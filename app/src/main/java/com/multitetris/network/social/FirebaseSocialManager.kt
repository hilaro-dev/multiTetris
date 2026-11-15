package com.multitetris.network.social

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.multitetris.game.GameMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

data class SocialUiState(
    val userProfile: UserProfile = UserProfile(),
    val isFirebaseConfigured: Boolean = false,
    val isFirebaseConnected: Boolean = false,
    val isSigningIn: Boolean = false,
    val friends: List<FriendLivePresence> = emptyList(),
    val matchmakingState: MatchmakingState = MatchmakingState.Idle,
    val feedbackMessage: String? = null
)

/**
 * Coordinates zero-friction Firebase Anonymous Auth, permanent MT-XXXXX Friend Codes,
 * real-time friend presence (.info/connected + onDisconnect), and deterministic
 * automatic matchmaking without room codes.
 */
class FirebaseSocialManager(
    private val context: Context,
    private val localStore: LocalProfileDataStore = LocalProfileDataStore(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val _socialState = MutableStateFlow(SocialUiState())
    val socialState: StateFlow<SocialUiState> = _socialState.asStateFlow()

    private val initialized = AtomicBoolean(false)
    private val pairingLock = Any()

    private var auth: FirebaseAuth? = null
    private var database: FirebaseDatabase? = null

    private var connectedListener: ValueEventListener? = null
    private var invitesListener: ValueEventListener? = null

    private val friendPresenceListeners = mutableMapOf<String, ValueEventListener>()
    private val friendProfileListeners = mutableMapOf<String, ValueEventListener>()
    private val livePresenceByUid = mutableMapOf<String, Pair<PresenceState, GameMode?>>()
    private val lastUpdatedByUid = mutableMapOf<String, Long>()
    private var savedFriendsCache: List<SavedFriend> = emptyList()

    private var activeMatchIdAsHost: String? = null

    fun initialize(defaultSkinId: String = "classic") {
        if (!initialized.compareAndSet(false, true)) return

        scope.launch {
            val initialProfile = localStore.ensureInitializedProfile(defaultSkinId)
            _socialState.update { it.copy(userProfile = initialProfile) }

            // Collect local DataStore updates (profile + saved friends)
            launch {
                localStore.snapshotFlow.collect { snapshot ->
                    savedFriendsCache = snapshot.friends
                    _socialState.update { state ->
                        state.copy(
                            userProfile = snapshot.profile,
                            friends = buildFriendPresenceList(snapshot.friends)
                        )
                    }
                    syncFriendListeners(snapshot.friends)
                    evaluateFriendsForMatch()
                }
            }

            val firebaseReady = ensureFirebaseInitialized()
            _socialState.update { it.copy(isFirebaseConfigured = firebaseReady) }
            if (!firebaseReady) {
                return@launch
            }

            authenticateAndSyncProfile()
        }
    }

    private fun ensureFirebaseInitialized(): Boolean {
        return runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                true
            } else {
                FirebaseApp.initializeApp(context) != null
            }
        }.getOrDefault(false)
    }

    private suspend fun authenticateAndSyncProfile() {
        _socialState.update { it.copy(isSigningIn = true) }
        runCatching {
            val firebaseAuth = FirebaseAuth.getInstance()
            val firebaseDb = FirebaseDatabase.getInstance()
            auth = firebaseAuth
            database = firebaseDb

            val currentUser = firebaseAuth.currentUser
                ?: firebaseAuth.signInAnonymously().await().user
                ?: error("Anonymous Firebase sign-in returned null user")

            val uid = currentUser.uid
            localStore.saveUid(uid)

            var snapshot = localStore.getSnapshot()
            var friendCode = snapshot.profile.friendCode
            var nickname = snapshot.profile.nickname
            val skinId = snapshot.profile.selectedSkinId

            // Claim or verify permanent friend code mapping: /friendCodes/{code} -> uid
            friendCode = claimUniqueFriendCode(firebaseDb, uid, friendCode)
            if (nickname.isBlank()) {
                nickname = FriendCodeGenerator.defaultNicknameFor(friendCode)
                localStore.saveNickname(nickname)
            }

            // Save user profile: /users/{uid}
            writeUserProfileToFirebase(firebaseDb, uid, friendCode, nickname, skinId)

            // Ensure /userFriends/{uid} mirrors local saved friends for security rules
            for (friend in snapshot.friends) {
                firebaseDb.getReference("userFriends").child(uid).child(friend.uid).setValue(true)
            }

            snapshot = localStore.getSnapshot()
            _socialState.update {
                it.copy(
                    userProfile = snapshot.profile.copy(uid = uid, friendCode = friendCode),
                    isSigningIn = false
                )
            }

            attachConnectedPresenceMonitor(firebaseDb, uid)
            attachInvitesListener(firebaseDb, uid)
            syncFriendListeners(snapshot.friends)
        }.onFailure { error ->
            _socialState.update {
                it.copy(
                    isSigningIn = false,
                    feedbackMessage = "Firebase auth failed: ${error.localizedMessage ?: "Check setup"}"
                )
            }
        }
    }

    private suspend fun claimUniqueFriendCode(
        db: FirebaseDatabase,
        uid: String,
        preferredCode: String
    ): String {
        var candidate = preferredCode.ifBlank { FriendCodeGenerator.generate() }
        repeat(5) {
            val codeRef = db.getReference("friendCodes").child(candidate)
            val existing = codeRef.get().await().getValue(String::class.java)
            if (existing == null || existing == uid) {
                codeRef.setValue(uid).await()
                localStore.saveFriendCode(candidate)
                return candidate
            }
            candidate = FriendCodeGenerator.generate()
        }
        return candidate
    }

    private suspend fun writeUserProfileToFirebase(
        db: FirebaseDatabase,
        uid: String,
        friendCode: String,
        nickname: String,
        skinId: String
    ) {
        val payload = mapOf(
            "friendCode" to friendCode,
            "nickname" to nickname,
            "selectedSkinId" to skinId
        )
        db.getReference("users").child(uid).setValue(payload).await()
    }

    private fun attachConnectedPresenceMonitor(db: FirebaseDatabase, uid: String) {
        val connectedRef = db.getReference(".info/connected")
        connectedListener?.let { connectedRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                _socialState.update { it.copy(isFirebaseConnected = connected) }
                if (connected) {
                    val presenceRef = db.getReference("presence").child(uid)
                    presenceRef.onDisconnect().setValue(
                        mapOf(
                            "status" to "offline",
                            "mode" to null,
                            "timestamp" to ServerValue.TIMESTAMP
                        )
                    )
                    db.getReference("invites").child(uid).onDisconnect().removeValue()

                    val currentMatchmaking = _socialState.value.matchmakingState
                    if (currentMatchmaking is MatchmakingState.Searching) {
                        writePresence(presenceRef, "searching", currentMatchmaking.mode)
                    } else {
                        writePresence(presenceRef, "online", null)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                _socialState.update { it.copy(isFirebaseConnected = false) }
            }
        }

        connectedListener = listener
        connectedRef.addValueEventListener(listener)
    }

    private fun attachInvitesListener(db: FirebaseDatabase, myUid: String) {
        val invitesRef = db.getReference("invites").child(myUid)
        invitesListener?.let { invitesRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                val matchId = snapshot.child("matchId").getValue(String::class.java) ?: return
                val hostUid = snapshot.child("hostUid").getValue(String::class.java) ?: return

                val currentSearch = _socialState.value.matchmakingState as? MatchmakingState.Searching
                    ?: return

                val savedFriendUids = savedFriendsCache.map { it.uid }.toSet()
                if (!MatchmakingTieBreaker.shouldAcceptInvite(hostUid, savedFriendUids)) {
                    return
                }

                scope.launch {
                    acceptMatchAsGuest(db, myUid, matchId, currentSearch.mode)
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }

        invitesListener = listener
        invitesRef.addValueEventListener(listener)
    }

    private suspend fun acceptMatchAsGuest(
        db: FirebaseDatabase,
        myUid: String,
        matchId: String,
        expectedMode: GameMode
    ) {
        val matchSnap = runCatching {
            db.getReference("matches").child(matchId).get().await()
        }.getOrNull() ?: return

        if (!matchSnap.exists()) return
        val hostUid = matchSnap.child("hostUid").getValue(String::class.java) ?: return
        val guestUid = matchSnap.child("guestUid").getValue(String::class.java) ?: return
        if (guestUid != myUid) return

        val modeRaw = matchSnap.child("mode").getValue(String::class.java)
        val mode = runCatching { GameMode.valueOf(modeRaw.orEmpty()) }.getOrNull() ?: return
        if (mode != expectedMode) return

        val hostNickname = matchSnap.child("hostNickname").getValue(String::class.java)
            ?: savedFriendsCache.firstOrNull { it.uid == hostUid }?.nickname
            ?: "Host"
        val hostSkinId = matchSnap.child("hostSkinId").getValue(String::class.java) ?: "classic"
        val myProfile = _socialState.value.userProfile
        val guestNickname = matchSnap.child("guestNickname").getValue(String::class.java)
            ?: myProfile.nickname
        val guestSkinId = matchSnap.child("guestSkinId").getValue(String::class.java)
            ?: myProfile.selectedSkinId
        val matchState = matchSnap.child("state").getValue(String::class.java) ?: "signaling"
        val createdAt = matchSnap.child("createdAt").getValue(Long::class.java) ?: System.currentTimeMillis()

        // Acknowledge invite and transition presence back to online/in-lobby
        runCatching {
            db.getReference("invites").child(myUid).removeValue().await()
            writePresence(db.getReference("presence").child(myUid), "online", null)
        }

        val room = MatchRoom(
            matchId = matchId,
            hostUid = hostUid,
            hostNickname = hostNickname,
            hostSkinId = hostSkinId,
            guestUid = myUid,
            guestNickname = guestNickname,
            guestSkinId = guestSkinId,
            mode = mode,
            state = matchState,
            createdAtMillis = createdAt,
            myRole = MatchRole.GUEST
        )

        synchronized(pairingLock) {
            if (_socialState.value.matchmakingState is MatchmakingState.Searching) {
                _socialState.update { it.copy(matchmakingState = MatchmakingState.MatchFound(room)) }
            }
        }
    }

    private fun syncFriendListeners(friends: List<SavedFriend>) {
        val db = database ?: return
        val currentUids = friends.map { it.uid }.toSet()

        // Detach removed friends
        val removedUids = friendPresenceListeners.keys.filterNot { it in currentUids }
        for (removedUid in removedUids) {
            friendPresenceListeners.remove(removedUid)?.let {
                db.getReference("presence").child(removedUid).removeEventListener(it)
            }
            friendProfileListeners.remove(removedUid)?.let {
                db.getReference("users").child(removedUid).removeEventListener(it)
            }
            livePresenceByUid.remove(removedUid)
            lastUpdatedByUid.remove(removedUid)
        }

        // Attach new friends
        for (friend in friends) {
            if (friend.uid !in friendPresenceListeners) {
                val presenceRef = db.getReference("presence").child(friend.uid)
                val presenceListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val statusStr = snapshot.child("status").getValue(String::class.java) ?: "offline"
                        val modeStr = snapshot.child("mode").getValue(String::class.java)
                        val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                        val state = when (statusStr) {
                            "searching" -> PresenceState.SEARCHING
                            "online" -> PresenceState.ONLINE
                            else -> PresenceState.OFFLINE
                        }
                        val mode = modeStr?.let { raw ->
                            runCatching { GameMode.valueOf(raw) }.getOrNull()
                        }

                        livePresenceByUid[friend.uid] = state to mode
                        lastUpdatedByUid[friend.uid] = timestamp
                        publishFriendsPresence()
                        evaluateFriendsForMatch()
                    }

                    override fun onCancelled(error: DatabaseError) = Unit
                }
                friendPresenceListeners[friend.uid] = presenceListener
                presenceRef.addValueEventListener(presenceListener)
            }

            if (friend.uid !in friendProfileListeners) {
                val userRef = db.getReference("users").child(friend.uid)
                val profileListener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!snapshot.exists()) return
                        val nickname = snapshot.child("nickname").getValue(String::class.java)
                            ?: friend.nickname
                        val skinId = snapshot.child("selectedSkinId").getValue(String::class.java)
                            ?: friend.selectedSkinId
                        val code = snapshot.child("friendCode").getValue(String::class.java)
                            ?: friend.friendCode

                        if (nickname != friend.nickname || skinId != friend.selectedSkinId || code != friend.friendCode) {
                            scope.launch {
                                localStore.addOrUpdateFriend(
                                    friend.copy(
                                        friendCode = code,
                                        nickname = nickname,
                                        selectedSkinId = skinId
                                    )
                                )
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) = Unit
                }
                friendProfileListeners[friend.uid] = profileListener
                userRef.addValueEventListener(profileListener)
            }
        }
    }

    private fun publishFriendsPresence() {
        _socialState.update { state ->
            state.copy(friends = buildFriendPresenceList(savedFriendsCache))
        }
    }

    private fun buildFriendPresenceList(friends: List<SavedFriend>): List<FriendLivePresence> {
        return friends.map { friend ->
            val (state, mode) = livePresenceByUid[friend.uid] ?: (PresenceState.OFFLINE to null)
            FriendLivePresence(
                friend = friend,
                state = state,
                searchingMode = mode,
                lastUpdatedMillis = lastUpdatedByUid[friend.uid] ?: 0L
            )
        }.sortedByDescending {
            when (it.state) {
                PresenceState.SEARCHING -> 2
                PresenceState.ONLINE -> 1
                PresenceState.OFFLINE -> 0
            }
        }
    }

    fun updateNickname(newNickname: String) {
        val sanitized = newNickname.trim().take(20)
        if (sanitized.isEmpty()) return

        scope.launch {
            localStore.saveNickname(sanitized)
            val profile = _socialState.value.userProfile.copy(nickname = sanitized)
            val db = database
            val uid = profile.uid
            if (db != null && uid.isNotBlank()) {
                runCatching {
                    writeUserProfileToFirebase(
                        db = db,
                        uid = uid,
                        friendCode = profile.friendCode,
                        nickname = sanitized,
                        skinId = profile.selectedSkinId
                    )
                }
            }
        }
    }

    fun updateSelectedSkin(skinId: String) {
        scope.launch {
            localStore.saveSelectedSkin(skinId)
            val profile = _socialState.value.userProfile.copy(selectedSkinId = skinId)
            val db = database
            val uid = profile.uid
            if (db != null && uid.isNotBlank()) {
                runCatching {
                    writeUserProfileToFirebase(
                        db = db,
                        uid = uid,
                        friendCode = profile.friendCode,
                        nickname = profile.nickname,
                        skinId = skinId
                    )
                }
            }
        }
    }

    fun addFriendByCode(rawInput: String) {
        val normalizedCode = FriendCodeGenerator.normalizeAndValidate(rawInput)
        if (normalizedCode == null) {
            _socialState.update {
                it.copy(feedbackMessage = "Enter a valid Friend Code (e.g. MT-7K9P2)")
            }
            return
        }

        val myProfile = _socialState.value.userProfile
        if (normalizedCode == myProfile.friendCode) {
            _socialState.update {
                it.copy(feedbackMessage = "You cannot add your own Friend Code")
            }
            return
        }

        val db = database
        val myUid = myProfile.uid
        if (db == null || myUid.isBlank()) {
            _socialState.update {
                it.copy(feedbackMessage = "Firebase is not connected yet. Check FIREBASE_SETUP.md")
            }
            return
        }

        scope.launch {
            runCatching {
                val friendUid = db.getReference("friendCodes")
                    .child(normalizedCode)
                    .get()
                    .await()
                    .getValue(String::class.java)

                if (friendUid.isNullOrBlank()) {
                    _socialState.update {
                        it.copy(feedbackMessage = "No player found for code $normalizedCode")
                    }
                    return@launch
                }

                if (friendUid == myUid) {
                    _socialState.update {
                        it.copy(feedbackMessage = "You cannot add yourself as a friend")
                    }
                    return@launch
                }

                // Register friend link first so Realtime Database rules permit reading their profile/presence
                db.getReference("userFriends").child(myUid).child(friendUid).setValue(true).await()

                val userSnap = db.getReference("users").child(friendUid).get().await()
                val nickname = userSnap.child("nickname").getValue(String::class.java)
                    ?: FriendCodeGenerator.defaultNicknameFor(normalizedCode)
                val skinId = userSnap.child("selectedSkinId").getValue(String::class.java)
                    ?: "classic"

                val savedFriend = SavedFriend(
                    uid = friendUid,
                    friendCode = normalizedCode,
                    nickname = nickname,
                    selectedSkinId = skinId,
                    addedAtMillis = System.currentTimeMillis()
                )
                localStore.addOrUpdateFriend(savedFriend)
                _socialState.update {
                    it.copy(feedbackMessage = "Added $nickname ($normalizedCode)")
                }
            }.onFailure { error ->
                _socialState.update {
                    it.copy(feedbackMessage = "Could not add friend: ${error.localizedMessage ?: "Network error"}")
                }
            }
        }
    }

    fun removeFriend(friendUid: String) {
        scope.launch {
            localStore.removeFriend(friendUid)
            val myUid = _socialState.value.userProfile.uid
            val db = database
            if (db != null && myUid.isNotBlank()) {
                runCatching {
                    db.getReference("userFriends").child(myUid).child(friendUid).removeValue().await()
                }
            }
        }
    }

    fun clearFeedback() {
        _socialState.update { it.copy(feedbackMessage = null) }
    }

    fun startSearch(mode: GameMode) {
        val db = database
        val myUid = _socialState.value.userProfile.uid
        if (db == null || myUid.isBlank()) {
            _socialState.update {
                it.copy(
                    matchmakingState = MatchmakingState.Error(
                        "Firebase is not configured or connected yet. See FIREBASE_SETUP.md or use Local 2P Test."
                    )
                )
            }
            return
        }

        if (savedFriendsCache.isEmpty()) {
            _socialState.update {
                it.copy(
                    matchmakingState = MatchmakingState.Error(
                        "Add at least one friend in the Friends drawer before searching!"
                    )
                )
            }
            return
        }

        _socialState.update {
            it.copy(matchmakingState = MatchmakingState.Searching(mode = mode))
        }

        writePresence(db.getReference("presence").child(myUid), "searching", mode)
        evaluateFriendsForMatch()
    }

    fun cancelSearch() {
        val db = database
        val myUid = _socialState.value.userProfile.uid

        synchronized(pairingLock) {
            _socialState.update { it.copy(matchmakingState = MatchmakingState.Idle) }
        }

        if (db != null && myUid.isNotBlank()) {
            writePresence(db.getReference("presence").child(myUid), "online", null)
            db.getReference("invites").child(myUid).removeValue()
            activeMatchIdAsHost?.let { matchId ->
                db.getReference("matches").child(matchId).removeValue()
                activeMatchIdAsHost = null
            }
        }
    }

    private fun evaluateFriendsForMatch() {
        val currentSearch = _socialState.value.matchmakingState as? MatchmakingState.Searching
            ?: return
        val myProfile = _socialState.value.userProfile
        val myUid = myProfile.uid
        val db = database ?: return
        if (myUid.isBlank()) return

        val candidate = _socialState.value.friends.firstOrNull { friendPresence ->
            MatchmakingTieBreaker.shouldPairWithFriend(
                myUid = myUid,
                mySearchMode = currentSearch.mode,
                friendUid = friendPresence.friend.uid,
                friendPresenceState = friendPresence.state,
                friendSearchMode = friendPresence.searchingMode
            ) == MatchRole.HOST
        } ?: return

        synchronized(pairingLock) {
            if (_socialState.value.matchmakingState !is MatchmakingState.Searching) return
            val matchId = "m_${myUid.take(8)}_${candidate.friend.uid.take(8)}_${System.currentTimeMillis()}"
            activeMatchIdAsHost = matchId

            val room = MatchRoom(
                matchId = matchId,
                hostUid = myUid,
                hostNickname = myProfile.nickname,
                hostSkinId = myProfile.selectedSkinId,
                guestUid = candidate.friend.uid,
                guestNickname = candidate.friend.nickname,
                guestSkinId = candidate.friend.selectedSkinId,
                mode = currentSearch.mode,
                state = "signaling",
                createdAtMillis = System.currentTimeMillis(),
                myRole = MatchRole.HOST
            )

            // Transition optimistically under lock to prevent creating duplicate rooms
            _socialState.update { it.copy(matchmakingState = MatchmakingState.MatchFound(room)) }

            scope.launch {
                publishHostMatchAndInvite(db, room)
            }
        }
    }

    private suspend fun publishHostMatchAndInvite(db: FirebaseDatabase, room: MatchRoom) {
        runCatching {
            val matchRef = db.getReference("matches").child(room.matchId)
            matchRef.onDisconnect().removeValue()

            val matchPayload = mapOf(
                "matchId" to room.matchId,
                "hostUid" to room.hostUid,
                "hostNickname" to room.hostNickname,
                "hostSkinId" to room.hostSkinId,
                "guestUid" to room.guestUid,
                "guestNickname" to room.guestNickname,
                "guestSkinId" to room.guestSkinId,
                "mode" to room.mode.name,
                "state" to "signaling",
                "createdAt" to ServerValue.TIMESTAMP
            )
            matchRef.setValue(matchPayload).await()

            val invitePayload = mapOf(
                "matchId" to room.matchId,
                "hostUid" to room.hostUid,
                "mode" to room.mode.name,
                "timestamp" to ServerValue.TIMESTAMP
            )
            db.getReference("invites").child(room.guestUid).setValue(invitePayload).await()

            writePresence(db.getReference("presence").child(room.hostUid), "online", null)
        }.onFailure { error ->
            activeMatchIdAsHost = null
            _socialState.update {
                it.copy(
                    matchmakingState = MatchmakingState.Error(
                        "Failed to create match room: ${error.localizedMessage ?: "Network error"}"
                    )
                )
            }
        }
    }

    private fun writePresence(
        presenceRef: DatabaseReference,
        status: String,
        mode: GameMode?
    ) {
        val payload = mapOf(
            "status" to status,
            "mode" to mode?.name,
            "timestamp" to ServerValue.TIMESTAMP
        )
        presenceRef.setValue(payload)
    }

    fun shutdown() {
        val db = database
        val myUid = _socialState.value.userProfile.uid
        if (db != null && myUid.isNotBlank()) {
            writePresence(db.getReference("presence").child(myUid), "offline", null)
            connectedListener?.let { db.getReference(".info/connected").removeEventListener(it) }
            invitesListener?.let { db.getReference("invites").child(myUid).removeEventListener(it) }
            friendPresenceListeners.forEach { (friendUid, listener) ->
                db.getReference("presence").child(friendUid).removeEventListener(listener)
            }
            friendProfileListeners.forEach { (friendUid, listener) ->
                db.getReference("users").child(friendUid).removeEventListener(listener)
            }
        }
        friendPresenceListeners.clear()
        friendProfileListeners.clear()
        scope.cancel()
    }
}
