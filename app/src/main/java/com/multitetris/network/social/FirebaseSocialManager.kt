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
import com.multitetris.game.SabotageConfig
import com.multitetris.game.SabotageType
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
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
    val pendingFriendRequests: List<FriendRequest> = emptyList(),
    val incomingRoomInvites: List<RoomInvite> = emptyList(),
    val invitedFriendUids: Set<String> = emptySet(),
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
    private var friendRequestsListener: ValueEventListener? = null
    private var acceptedRequestsListener: ValueEventListener? = null
    private var roomInvitesListener: ValueEventListener? = null
    private var activeRoomListener: ValueEventListener? = null
    private val sentRoomInviteRefs = mutableListOf<DatabaseReference>()

    private val friendPresenceListeners = mutableMapOf<String, ValueEventListener>()
    private val friendProfileListeners = mutableMapOf<String, ValueEventListener>()
    private val livePresenceByUid = mutableMapOf<String, Pair<PresenceState, GameMode?>>()
    private val lastUpdatedByUid = mutableMapOf<String, Long>()
    private var savedFriendsCache: List<SavedFriend> = emptyList()

    private var activeMatchIdAsHost: String? = null
    private var activeObservedRoomId: String? = null
    private var activeObservedRole: MatchRole? = null
    private var isCreatingMatchAsHost = false
    private var isAcceptingInviteAsGuest = false

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
            attachFriendRequestsListener(firebaseDb, uid)
            attachAcceptedRequestsListener(firebaseDb, uid)
            attachRoomInvitesListener(firebaseDb, uid)
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
        synchronized(pairingLock) {
            if (isAcceptingInviteAsGuest || _socialState.value.matchmakingState !is MatchmakingState.Searching) {
                return
            }
            isAcceptingInviteAsGuest = true
        }

        try {
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
        } finally {
            synchronized(pairingLock) {
                isAcceptingInviteAsGuest = false
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
        sendFriendRequestByCode(rawInput)
    }

    fun sendFriendRequestByCode(rawInput: String) {
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
                val targetUid = db.getReference("friendCodes")
                    .child(normalizedCode)
                    .get()
                    .await()
                    .getValue(String::class.java)

                if (targetUid.isNullOrBlank()) {
                    _socialState.update {
                        it.copy(feedbackMessage = "No player found for code $normalizedCode")
                    }
                    return@launch
                }

                if (targetUid == myUid) {
                    _socialState.update {
                        it.copy(feedbackMessage = "You cannot send a request to yourself")
                    }
                    return@launch
                }

                if (savedFriendsCache.any { it.uid == targetUid }) {
                    _socialState.update {
                        it.copy(feedbackMessage = "Already friends with $normalizedCode")
                    }
                    return@launch
                }

                val payload = mapOf(
                    "fromUid" to myUid,
                    "fromNickname" to myProfile.nickname.ifBlank { "Player" },
                    "fromCode" to myProfile.friendCode,
                    "fromSkinId" to myProfile.selectedSkinId,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                db.getReference("friendRequests").child(targetUid).child(myUid)
                    .setValue(payload)
                    .await()

                _socialState.update {
                    it.copy(feedbackMessage = "Friend request sent to $normalizedCode!")
                }
            }.onFailure { error ->
                _socialState.update {
                    it.copy(feedbackMessage = "Could not send request: ${error.localizedMessage ?: "Network error"}")
                }
            }
        }
    }

    fun acceptFriendRequest(request: FriendRequest) {
        val db = database ?: return
        val myProfile = _socialState.value.userProfile
        val myUid = myProfile.uid
        if (myUid.isBlank()) return

        scope.launch {
            runCatching {
                // 1. Register mutual friendship links
                db.getReference("userFriends").child(myUid).child(request.fromUid).setValue(true).await()
                runCatching {
                    db.getReference("userFriends").child(request.fromUid).child(myUid).setValue(true).await()
                }

                // 2. Notify requester via /acceptedRequests so their client also saves us locally
                val ackPayload = mapOf(
                    "friendUid" to myUid,
                    "friendNickname" to myProfile.nickname.ifBlank { "Player" },
                    "friendCode" to myProfile.friendCode,
                    "friendSkinId" to myProfile.selectedSkinId,
                    "timestamp" to ServerValue.TIMESTAMP
                )
                db.getReference("acceptedRequests").child(request.fromUid).child(myUid)
                    .setValue(ackPayload)
                    .await()

                // 3. Save locally and remove the pending request
                val savedFriend = SavedFriend(
                    uid = request.fromUid,
                    friendCode = request.fromCode,
                    nickname = request.fromNickname,
                    selectedSkinId = request.fromSkinId,
                    addedAtMillis = System.currentTimeMillis()
                )
                localStore.addOrUpdateFriend(savedFriend)
                db.getReference("friendRequests").child(myUid).child(request.fromUid).removeValue().await()

                _socialState.update {
                    it.copy(feedbackMessage = "Accepted ${request.fromNickname} (${request.fromCode})!")
                }
            }.onFailure { error ->
                _socialState.update {
                    it.copy(feedbackMessage = "Failed accepting request: ${error.localizedMessage ?: "Network error"}")
                }
            }
        }
    }

    fun declineFriendRequest(fromUid: String) {
        val db = database ?: return
        val myUid = _socialState.value.userProfile.uid
        if (myUid.isBlank()) return

        scope.launch {
            runCatching {
                db.getReference("friendRequests").child(myUid).child(fromUid).removeValue().await()
            }
        }
    }

    private fun attachFriendRequestsListener(db: FirebaseDatabase, myUid: String) {
        val reqRef = db.getReference("friendRequests").child(myUid)
        friendRequestsListener?.let { reqRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = snapshot.children.mapNotNull { child ->
                    val fromUid = child.child("fromUid").getValue(String::class.java)
                        ?: child.key
                        ?: return@mapNotNull null
                    val fromNickname = child.child("fromNickname").getValue(String::class.java) ?: "Player"
                    val fromCode = child.child("fromCode").getValue(String::class.java) ?: ""
                    val fromSkinId = child.child("fromSkinId").getValue(String::class.java) ?: "classic"
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    FriendRequest(
                        fromUid = fromUid,
                        fromNickname = fromNickname,
                        fromCode = fromCode,
                        fromSkinId = fromSkinId,
                        timestamp = timestamp
                    )
                }.sortedByDescending { it.timestamp }

                _socialState.update { it.copy(pendingFriendRequests = requests) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        friendRequestsListener = listener
        reqRef.addValueEventListener(listener)
    }

    private fun attachAcceptedRequestsListener(db: FirebaseDatabase, myUid: String) {
        val acceptedRef = db.getReference("acceptedRequests").child(myUid)
        acceptedRequestsListener?.let { acceptedRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) return
                scope.launch {
                    for (child in snapshot.children) {
                        val friendUid = child.child("friendUid").getValue(String::class.java)
                            ?: child.key
                            ?: continue
                        val friendNickname = child.child("friendNickname").getValue(String::class.java) ?: "Player"
                        val friendCode = child.child("friendCode").getValue(String::class.java) ?: ""
                        val friendSkinId = child.child("friendSkinId").getValue(String::class.java) ?: "classic"

                        runCatching {
                            db.getReference("userFriends").child(myUid).child(friendUid).setValue(true).await()
                            localStore.addOrUpdateFriend(
                                SavedFriend(
                                    uid = friendUid,
                                    friendCode = friendCode,
                                    nickname = friendNickname,
                                    selectedSkinId = friendSkinId,
                                    addedAtMillis = System.currentTimeMillis()
                                )
                            )
                            acceptedRef.child(friendUid).removeValue().await()
                            _socialState.update {
                                it.copy(feedbackMessage = "$friendNickname accepted your friend request!")
                            }
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        acceptedRequestsListener = listener
        acceptedRef.addValueEventListener(listener)
    }

    private fun attachRoomInvitesListener(db: FirebaseDatabase, myUid: String) {
        val invitesRef = db.getReference("roomInvites").child(myUid)
        roomInvitesListener?.let { invitesRef.removeEventListener(it) }

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val invites = snapshot.children.mapNotNull { child ->
                    val matchId = child.child("matchId").getValue(String::class.java)
                        ?: child.key
                        ?: return@mapNotNull null
                    val hostUid = child.child("hostUid").getValue(String::class.java)
                        ?: return@mapNotNull null
                    val hostNickname = child.child("hostNickname").getValue(String::class.java)
                        ?: savedFriendsCache.firstOrNull { it.uid == hostUid }?.nickname
                        ?: "Friend"
                    val hostSkinId = child.child("hostSkinId").getValue(String::class.java)
                        ?: savedFriendsCache.firstOrNull { it.uid == hostUid }?.selectedSkinId
                        ?: "classic"
                    val modeRaw = child.child("mode").getValue(String::class.java)
                    val mode = runCatching { GameMode.valueOf(modeRaw.orEmpty()) }
                        .getOrDefault(GameMode.CO_OP)
                    val timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L

                    RoomInvite(
                        matchId = matchId,
                        hostUid = hostUid,
                        hostNickname = hostNickname,
                        hostSkinId = hostSkinId,
                        mode = mode,
                        timestamp = timestamp
                    )
                }.sortedByDescending { it.timestamp }

                _socialState.update { it.copy(incomingRoomInvites = invites) }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        roomInvitesListener = listener
        invitesRef.addValueEventListener(listener)
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

    fun createRoom(mode: GameMode) {
        val db = database
        val myProfile = _socialState.value.userProfile
        val myUid = myProfile.uid
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

        val matchId = "m_${myUid.take(8)}_${System.currentTimeMillis()}"
        val myNickname = myProfile.nickname.ifBlank { "Host" }
        val mySkinId = myProfile.selectedSkinId.ifBlank { "classic" }
        val hostSlot = RoomPlayerSlot(
            uid = myUid,
            nickname = myNickname,
            skinId = mySkinId,
            slotIndex = 1,
            team = TeamColor.CYAN,
            ready = true
        )
        val room = MatchRoom(
            matchId = matchId,
            hostUid = myUid,
            hostNickname = myNickname,
            hostSkinId = mySkinId,
            guestUid = "",
            guestNickname = "Waiting for friend...",
            guestSkinId = "neon_cyber",
            mode = mode,
            state = "lobby",
            createdAtMillis = System.currentTimeMillis(),
            myRole = MatchRole.HOST,
            players = listOf(hostSlot),
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            mySlotIndex = 1
        )

        synchronized(pairingLock) {
            isCreatingMatchAsHost = false
            isAcceptingInviteAsGuest = false
            activeMatchIdAsHost = matchId
            activeObservedRole = MatchRole.HOST
        }
        clearSentRoomInvites()

        scope.launch {
            runCatching {
                val matchRef = db.getReference("matches").child(matchId)
                matchRef.onDisconnect().removeValue()

                val matchPayload = mapOf(
                    "matchId" to room.matchId,
                    "hostUid" to room.hostUid,
                    "hostNickname" to room.hostNickname,
                    "hostSkinId" to room.hostSkinId,
                    "mode" to room.mode.name,
                    "versusSubMode" to VersusSubMode.FREE_FOR_ALL.name,
                    "sabotageEnabled" to false,
                    "mysteryEnabled" to false,
                    "state" to "lobby",
                    "createdAt" to ServerValue.TIMESTAMP,
                    "players" to mapOf(
                        myUid to mapOf(
                            "uid" to myUid,
                            "nickname" to myNickname,
                            "skinId" to mySkinId,
                            "slotIndex" to 1,
                            "team" to TeamColor.CYAN.name,
                            "ready" to true,
                            "sabotages" to SabotageConfig.defaultLoadout.map { it.name }
                        )
                    )
                )
                matchRef.updateChildren(matchPayload).await()

                synchronized(pairingLock) {
                    if (activeMatchIdAsHost == matchId) {
                        _socialState.update {
                            it.copy(
                                matchmakingState = MatchmakingState.MatchFound(room),
                                invitedFriendUids = emptySet()
                            )
                        }
                    }
                }
                attachRoomObserver(matchRef, room)
            }.onFailure { error ->
                _socialState.update {
                    it.copy(
                        matchmakingState = MatchmakingState.Error(
                            "Could not create room: ${error.localizedMessage ?: "Network error"}"
                        )
                    )
                }
            }
        }
    }

    private fun attachRoomObserver(matchRef: DatabaseReference, initialRoom: MatchRoom) {
        val db = database ?: return
        activeRoomListener?.let { prev ->
            activeObservedRoomId?.let { prevId ->
                db.getReference("matches").child(prevId).removeEventListener(prev)
            }
        }
        activeObservedRoomId = initialRoom.matchId
        activeObservedRole = initialRoom.myRole

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val myUid = _socialState.value.userProfile.uid
                if (!snapshot.exists()) {
                    if (initialRoom.myRole == MatchRole.GUEST && activeObservedRoomId == initialRoom.matchId) {
                        activeObservedRoomId = null
                        activeObservedRole = null
                        activeRoomListener = null
                        matchRef.removeEventListener(this)
                        _socialState.update {
                            it.copy(
                                matchmakingState = MatchmakingState.Idle,
                                feedbackMessage = "Host left or disconnected."
                            )
                        }
                    }
                    return
                }

                val currentFound = _socialState.value.matchmakingState as? MatchmakingState.MatchFound
                val baseRoom = if (currentFound?.room?.matchId == initialRoom.matchId) {
                    currentFound.room
                } else {
                    initialRoom
                }

                val hostUid = snapshot.child("hostUid").getValue(String::class.java) ?: baseRoom.hostUid
                val hostNickname = snapshot.child("hostNickname").getValue(String::class.java)
                    ?: baseRoom.hostNickname
                val hostSkinId = snapshot.child("hostSkinId").getValue(String::class.java)
                    ?: baseRoom.hostSkinId
                val modeRaw = snapshot.child("mode").getValue(String::class.java)
                val mode = runCatching { GameMode.valueOf(modeRaw.orEmpty()) }.getOrDefault(baseRoom.mode)
                val subModeRaw = snapshot.child("versusSubMode").getValue(String::class.java)
                val subMode = runCatching { VersusSubMode.valueOf(subModeRaw.orEmpty()) }
                    .getOrDefault(baseRoom.versusSubMode)
                val sabotageEnabled = snapshot.child("sabotageEnabled").getValue(Boolean::class.java)
                    ?: baseRoom.sabotageEnabled
                val mysteryEnabled = snapshot.child("mysteryEnabled").getValue(Boolean::class.java)
                    ?: baseRoom.mysteryEnabled
                val stateStr = snapshot.child("state").getValue(String::class.java) ?: baseRoom.state

                val parsedPlayers = snapshot.child("players").children.mapNotNull { playerSnap ->
                    val pUid = playerSnap.child("uid").getValue(String::class.java)
                        ?: playerSnap.key.orEmpty()
                    if (pUid.isBlank()) return@mapNotNull null
                    val slotIndex = playerSnap.child("slotIndex").getValue(Int::class.java)
                        ?: if (pUid == hostUid) 1 else 2
                    val nickname = playerSnap.child("nickname").getValue(String::class.java)
                        ?: savedFriendsCache.firstOrNull { it.uid == pUid }?.nickname
                        ?: "Player $slotIndex"
                    val skinId = playerSnap.child("skinId").getValue(String::class.java) ?: "classic"
                    val teamRaw = playerSnap.child("team").getValue(String::class.java)
                    val team = runCatching { TeamColor.valueOf(teamRaw.orEmpty()) }
                        .getOrDefault(if (slotIndex <= 2) TeamColor.CYAN else TeamColor.MAGENTA)
                    val ready = playerSnap.child("ready").getValue(Boolean::class.java) ?: true
                    val parsedSabotages = playerSnap.child("sabotages").children.mapNotNull { sabSnap ->
                        runCatching {
                            SabotageType.valueOf(sabSnap.getValue(String::class.java).orEmpty())
                        }.getOrNull()
                    }.distinct().take(SabotageConfig.MAX_DRAFT_SLOTS)
                    val selectedSabotages = if (playerSnap.hasChild("sabotages")) {
                        parsedSabotages
                    } else {
                        SabotageConfig.defaultLoadout
                    }
                    RoomPlayerSlot(
                        uid = pUid,
                        nickname = nickname,
                        skinId = skinId,
                        slotIndex = slotIndex,
                        team = team,
                        ready = ready,
                        selectedSabotages = selectedSabotages
                    )
                }.sortedBy { it.slotIndex }

                val legacyGuestUid = snapshot.child("guestUid").getValue(String::class.java).orEmpty()
                val legacyGuestName = snapshot.child("guestNickname").getValue(String::class.java)
                    ?: savedFriendsCache.firstOrNull { it.uid == legacyGuestUid }?.nickname
                    ?: "Guest"
                val legacyGuestSkin = snapshot.child("guestSkinId").getValue(String::class.java)
                    ?: "neon_cyber"

                val primaryGuest = parsedPlayers.firstOrNull { it.uid != hostUid }
                val effectivePlayers = if (parsedPlayers.isNotEmpty()) {
                    parsedPlayers
                } else {
                    buildList {
                        add(
                            RoomPlayerSlot(
                                uid = hostUid,
                                nickname = hostNickname,
                                skinId = hostSkinId,
                                slotIndex = 1,
                                team = TeamColor.CYAN,
                                ready = true
                            )
                        )
                        if (legacyGuestUid.isNotBlank()) {
                            add(
                                RoomPlayerSlot(
                                    uid = legacyGuestUid,
                                    nickname = legacyGuestName,
                                    skinId = legacyGuestSkin,
                                    slotIndex = 2,
                                    team = TeamColor.MAGENTA,
                                    ready = true
                                )
                            )
                        }
                    }
                }

                val mySlot = effectivePlayers.firstOrNull { it.uid == myUid }?.slotIndex
                    ?: if (initialRoom.myRole == MatchRole.HOST) 1 else 2

                val updatedRoom = baseRoom.copy(
                    hostUid = hostUid,
                    hostNickname = hostNickname,
                    hostSkinId = hostSkinId,
                    guestUid = primaryGuest?.uid ?: legacyGuestUid,
                    guestNickname = primaryGuest?.nickname ?: legacyGuestName,
                    guestSkinId = primaryGuest?.skinId ?: legacyGuestSkin,
                    mode = mode,
                    state = stateStr,
                    players = effectivePlayers,
                    versusSubMode = subMode,
                    mySlotIndex = mySlot,
                    sabotageEnabled = sabotageEnabled,
                    mysteryEnabled = mysteryEnabled
                )
                if (updatedRoom != currentFound?.room) {
                    _socialState.update { it.copy(matchmakingState = MatchmakingState.MatchFound(updatedRoom)) }
                }
            }

            override fun onCancelled(error: DatabaseError) = Unit
        }
        activeRoomListener = listener
        matchRef.addValueEventListener(listener)
    }

    fun inviteFriendToRoom(friend: SavedFriend) {
        val db = database ?: return
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (room.myRole != MatchRole.HOST) return

        val matchRef = db.getReference("matches").child(room.matchId)
        matchRef.onDisconnect().removeValue()
        matchRef.updateChildren(
            mapOf(
                "matchId" to room.matchId,
                "hostUid" to room.hostUid,
                "hostNickname" to room.hostNickname,
                "hostSkinId" to room.hostSkinId,
                "mode" to room.mode.name
            )
        )

        val inviteRef = db.getReference("roomInvites").child(friend.uid).child(room.matchId)
        inviteRef.onDisconnect().removeValue()
        val invitePayload = mapOf(
            "matchId" to room.matchId,
            "hostUid" to room.hostUid,
            "hostNickname" to room.hostNickname,
            "hostSkinId" to room.hostSkinId,
            "mode" to room.mode.name,
            "timestamp" to ServerValue.TIMESTAMP
        )
        inviteRef.setValue(invitePayload)
        synchronized(sentRoomInviteRefs) {
            sentRoomInviteRefs.add(inviteRef)
        }
        _socialState.update {
            it.copy(invitedFriendUids = it.invitedFriendUids + friend.uid)
        }
    }

    fun setRoomVersusSubMode(subMode: VersusSubMode) {
        val db = database ?: return
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (room.myRole != MatchRole.HOST) return

        val matchRef = db.getReference("matches").child(room.matchId)
        val updates = mutableMapOf<String, Any>(
            "versusSubMode" to subMode.name
        )
        if (subMode == VersusSubMode.TEAMS_2V2) {
            room.resolvedPlayers().forEachIndexed { idx, slot ->
                val targetTeam = if (slot.team == TeamColor.NONE) {
                    if (idx < 2) TeamColor.CYAN else TeamColor.MAGENTA
                } else {
                    slot.team
                }
                updates["players/${slot.uid}/team"] = targetTeam.name
            }
        }
        matchRef.updateChildren(updates)
    }

    fun swapPlayerTeam(targetUid: String) {
        val db = database ?: return
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (room.myRole != MatchRole.HOST || targetUid.isBlank()) return

        val player = room.resolvedPlayers().firstOrNull { it.uid == targetUid } ?: return
        val nextTeam = if (player.team == TeamColor.CYAN) TeamColor.MAGENTA else TeamColor.CYAN
        db.getReference("matches")
            .child(room.matchId)
            .child("players")
            .child(targetUid)
            .child("team")
            .setValue(nextTeam.name)
    }

    fun startRoomMatch() {
        val db = database ?: return
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (room.myRole != MatchRole.HOST) return
        if (room.resolvedPlayers().size < 2) {
            _socialState.update {
                it.copy(feedbackMessage = "Need at least 2 players to start!")
            }
            return
        }

        clearSentRoomInvites()
        val matchRef = db.getReference("matches").child(room.matchId)
        if (room.sabotageEnabled && room.mode == GameMode.VERSUS) {
            val updates = mutableMapOf<String, Any?>()
            for (slot in room.resolvedPlayers()) {
                if (slot.selectedSabotages.size < SabotageConfig.MAX_DRAFT_SLOTS) {
                    val resolved = SabotageConfig.resolveLoadout(slot.selectedSabotages)
                    updates["players/${slot.uid}/sabotages"] = resolved.map { it.name }
                }
            }
            updates["state"] = "playing"
            matchRef.updateChildren(updates)
        } else {
            matchRef.child("state").setValue("playing")
        }
    }

    fun setRoomSabotageEnabled(enabled: Boolean) {
        val db = database ?: return
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (room.myRole != MatchRole.HOST) return
        db.getReference("matches")
            .child(room.matchId)
            .child("sabotageEnabled")
            .setValue(enabled)
    }

    fun setRoomMysteryEnabled(enabled: Boolean) {
        val db = database ?: return
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (room.myRole != MatchRole.HOST) return
        db.getReference("matches")
            .child(room.matchId)
            .child("mysteryEnabled")
            .setValue(enabled)
    }

    fun toggleMySabotageSelection(sabotage: SabotageType) {
        val db = database ?: return
        val myUid = _socialState.value.userProfile.uid
        val room = (_socialState.value.matchmakingState as? MatchmakingState.MatchFound)?.room
            ?: return
        if (myUid.isBlank()) return

        val current = room.resolvedPlayers()
            .firstOrNull { it.uid == myUid }
            ?.selectedSabotages
            ?: emptyList()
        val updated = SabotageConfig.toggleSelection(current, sabotage)

        db.getReference("matches")
            .child(room.matchId)
            .child("players")
            .child(myUid)
            .child("sabotages")
            .setValue(updated.map { it.name })
    }

    fun acceptRoomInvite(invite: RoomInvite) {
        val db = database ?: return
        val myProfile = _socialState.value.userProfile
        val myUid = myProfile.uid
        if (myUid.isBlank()) return

        synchronized(pairingLock) {
            if (isAcceptingInviteAsGuest) return
            isAcceptingInviteAsGuest = true
        }

        scope.launch {
            try {
                // Delete invite from /roomInvites/{myUid}/{matchId}
                runCatching {
                    db.getReference("roomInvites").child(myUid).child(invite.matchId).removeValue().await()
                }
                _socialState.update { state ->
                    state.copy(
                        incomingRoomInvites = state.incomingRoomInvites.filterNot { it.matchId == invite.matchId }
                    )
                }

                val matchRef = db.getReference("matches").child(invite.matchId)
                val matchSnap = runCatching { matchRef.get().await() }.getOrNull()
                if (matchSnap == null || !matchSnap.exists()) {
                    _socialState.update {
                        it.copy(feedbackMessage = "Room closed by host before joining")
                    }
                    return@launch
                }

                val currentStateStr = matchSnap.child("state").getValue(String::class.java) ?: "lobby"
                if (currentStateStr == "playing") {
                    _socialState.update {
                        it.copy(feedbackMessage = "Match has already started!")
                    }
                    return@launch
                }

                val hostUid = matchSnap.child("hostUid").getValue(String::class.java) ?: invite.hostUid
                val hostNickname = matchSnap.child("hostNickname").getValue(String::class.java)
                    ?: invite.hostNickname
                val hostSkinId = matchSnap.child("hostSkinId").getValue(String::class.java)
                    ?: invite.hostSkinId
                val modeRaw = matchSnap.child("mode").getValue(String::class.java)
                val mode = runCatching { GameMode.valueOf(modeRaw.orEmpty()) }.getOrDefault(invite.mode)
                val subModeRaw = matchSnap.child("versusSubMode").getValue(String::class.java)
                val subMode = runCatching { VersusSubMode.valueOf(subModeRaw.orEmpty()) }
                    .getOrDefault(VersusSubMode.FREE_FOR_ALL)

                val existingPlayersSnap = matchSnap.child("players")
                val existingSlotForMe = existingPlayersSnap.child(myUid)
                    .child("slotIndex").getValue(Int::class.java)
                val takenSlots = existingPlayersSnap.children.mapNotNull { snap ->
                    snap.child("slotIndex").getValue(Int::class.java)
                }.toMutableSet()
                takenSlots.add(1) // Slot 1 always reserved for Host

                val mySlotIndex = existingSlotForMe ?: (2..4).firstOrNull { it !in takenSlots }
                if (mySlotIndex == null) {
                    _socialState.update {
                        it.copy(feedbackMessage = "Room is already full (4/4 players).")
                    }
                    return@launch
                }

                val myNickname = myProfile.nickname.ifBlank { "Player $mySlotIndex" }
                val mySkinId = myProfile.selectedSkinId.ifBlank { "classic" }
                val defaultTeam = if (mySlotIndex <= 2) TeamColor.CYAN else TeamColor.MAGENTA

                val playerRef = matchRef.child("players").child(myUid)
                playerRef.onDisconnect().removeValue()
                matchRef.child("peers").child(myUid).onDisconnect().removeValue()

                val playerPayload = mapOf(
                    "uid" to myUid,
                    "nickname" to myNickname,
                    "skinId" to mySkinId,
                    "slotIndex" to mySlotIndex,
                    "team" to defaultTeam.name,
                    "ready" to true,
                    "sabotages" to SabotageConfig.defaultLoadout.map { it.name }
                )
                playerRef.setValue(playerPayload).await()

                val guestSlot = RoomPlayerSlot(
                    uid = myUid,
                    nickname = myNickname,
                    skinId = mySkinId,
                    slotIndex = mySlotIndex,
                    team = defaultTeam,
                    ready = true
                )
                val guestRoom = MatchRoom(
                    matchId = invite.matchId,
                    hostUid = hostUid,
                    hostNickname = hostNickname,
                    hostSkinId = hostSkinId,
                    guestUid = myUid,
                    guestNickname = myNickname,
                    guestSkinId = mySkinId,
                    mode = mode,
                    state = currentStateStr,
                    createdAtMillis = System.currentTimeMillis(),
                    myRole = MatchRole.GUEST,
                    players = listOf(
                        RoomPlayerSlot(
                            uid = hostUid,
                            nickname = hostNickname,
                            skinId = hostSkinId,
                            slotIndex = 1,
                            team = TeamColor.CYAN,
                            ready = true
                        ),
                        guestSlot
                    ),
                    versusSubMode = subMode,
                    mySlotIndex = mySlotIndex
                )

                synchronized(pairingLock) {
                    activeObservedRole = MatchRole.GUEST
                }
                _socialState.update {
                    it.copy(matchmakingState = MatchmakingState.MatchFound(guestRoom))
                }
                attachRoomObserver(matchRef, guestRoom)
            } catch (err: Exception) {
                _socialState.update {
                    it.copy(feedbackMessage = "Failed to join room: ${err.localizedMessage ?: "Network error"}")
                }
            } finally {
                synchronized(pairingLock) {
                    isAcceptingInviteAsGuest = false
                }
            }
        }
    }

    fun declineRoomInvite(invite: RoomInvite) {
        val db = database ?: return
        val myUid = _socialState.value.userProfile.uid
        _socialState.update { state ->
            state.copy(incomingRoomInvites = state.incomingRoomInvites.filterNot { it.matchId == invite.matchId })
        }
        if (myUid.isNotBlank()) {
            db.getReference("roomInvites").child(myUid).child(invite.matchId).removeValue()
        }
    }

    fun leaveRoom() = cancelSearch()

    private fun clearSentRoomInvites() {
        val toRemove = synchronized(sentRoomInviteRefs) {
            val copy = sentRoomInviteRefs.toList()
            sentRoomInviteRefs.clear()
            copy
        }
        toRemove.forEach { ref -> runCatching { ref.removeValue() } }
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

        synchronized(pairingLock) {
            isCreatingMatchAsHost = false
            isAcceptingInviteAsGuest = false
            _socialState.update {
                it.copy(matchmakingState = MatchmakingState.Searching(mode = mode))
            }
        }

        writePresence(db.getReference("presence").child(myUid), "searching", mode)
        evaluateFriendsForMatch()
    }

    fun cancelSearch() {
        val db = database
        val myUid = _socialState.value.userProfile.uid
        val prevRoomId: String?
        val prevHostMatchId: String?
        val prevRole: MatchRole?

        synchronized(pairingLock) {
            isCreatingMatchAsHost = false
            isAcceptingInviteAsGuest = false
            prevRoomId = activeObservedRoomId
            prevHostMatchId = activeMatchIdAsHost
            prevRole = activeObservedRole
            activeObservedRoomId = null
            activeMatchIdAsHost = null
            activeObservedRole = null
            _socialState.update {
                it.copy(
                    matchmakingState = MatchmakingState.Idle,
                    invitedFriendUids = emptySet()
                )
            }
        }

        clearSentRoomInvites()
        if (db != null && myUid.isNotBlank()) {
            writePresence(db.getReference("presence").child(myUid), "online", null)
            db.getReference("invites").child(myUid).removeValue()

            val targetMatchId = prevHostMatchId ?: prevRoomId
            if (!targetMatchId.isNullOrBlank()) {
                val matchRef = db.getReference("matches").child(targetMatchId)
                activeRoomListener?.let { listener ->
                    matchRef.removeEventListener(listener)
                }
                activeRoomListener = null

                if (prevRole == MatchRole.HOST || prevHostMatchId != null) {
                    matchRef.removeValue()
                } else if (prevRole == MatchRole.GUEST) {
                    matchRef.child("players").child(myUid).removeValue()
                    matchRef.child("peers").child(myUid).removeValue()
                }
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
            if (isCreatingMatchAsHost || _socialState.value.matchmakingState !is MatchmakingState.Searching) {
                return
            }
            val matchId = "m_${myUid.take(8)}_${candidate.friend.uid.take(8)}_${System.currentTimeMillis()}"
            isCreatingMatchAsHost = true
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

            scope.launch {
                publishHostMatchAndInvite(db, room)
            }
        }
    }

    private suspend fun publishHostMatchAndInvite(db: FirebaseDatabase, room: MatchRoom) {
        runCatching {
            val matchRef = db.getReference("matches").child(room.matchId)
            matchRef.onDisconnect().removeValue()

            // Write hostUid & guestUid BEFORE signaling starts and BEFORE emitting MatchFound
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
            matchRef.updateChildren(matchPayload).await()

            val invitePayload = mapOf(
                "matchId" to room.matchId,
                "hostUid" to room.hostUid,
                "mode" to room.mode.name,
                "timestamp" to ServerValue.TIMESTAMP
            )
            db.getReference("invites").child(room.guestUid).setValue(invitePayload).await()

            writePresence(db.getReference("presence").child(room.hostUid), "online", null)

            synchronized(pairingLock) {
                isCreatingMatchAsHost = false
                if (_socialState.value.matchmakingState is MatchmakingState.Searching &&
                    activeMatchIdAsHost == room.matchId
                ) {
                    _socialState.update { it.copy(matchmakingState = MatchmakingState.MatchFound(room)) }
                }
            }
        }.onFailure { error ->
            synchronized(pairingLock) {
                isCreatingMatchAsHost = false
                activeMatchIdAsHost = null
            }
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
        clearSentRoomInvites()
        if (db != null && myUid.isNotBlank()) {
            writePresence(db.getReference("presence").child(myUid), "offline", null)
            connectedListener?.let { db.getReference(".info/connected").removeEventListener(it) }
            invitesListener?.let { db.getReference("invites").child(myUid).removeEventListener(it) }
            friendRequestsListener?.let { db.getReference("friendRequests").child(myUid).removeEventListener(it) }
            acceptedRequestsListener?.let { db.getReference("acceptedRequests").child(myUid).removeEventListener(it) }
            roomInvitesListener?.let { db.getReference("roomInvites").child(myUid).removeEventListener(it) }
            (activeObservedRoomId ?: activeMatchIdAsHost)?.let { matchId ->
                activeRoomListener?.let { listener ->
                    db.getReference("matches").child(matchId).removeEventListener(listener)
                }
            }
            activeRoomListener = null
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
