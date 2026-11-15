package com.multitetris.network.social

import com.multitetris.game.GameMode
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * Generates and validates permanent 8-character Friend Codes formatted as MT-XXXXX
 * using unambiguous uppercase alphanumeric characters (excluding 0, O, 1, I).
 */
object FriendCodeGenerator {
    const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    const val PREFIX = "MT-"
    const val SUFFIX_LENGTH = 5
    private val VALID_REGEX = Regex("^MT-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}$")

    fun generate(random: Random = Random.Default): String {
        val suffix = buildString(SUFFIX_LENGTH) {
            repeat(SUFFIX_LENGTH) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
        return "$PREFIX$suffix"
    }

    fun defaultNicknameFor(friendCode: String): String {
        val suffix = friendCode.removePrefix(PREFIX).takeLast(4).padStart(4, '2')
        return "Player_$suffix"
    }

    fun normalizeAndValidate(rawInput: String): String? {
        val cleaned = rawInput.trim().uppercase().replace(" ", "")
        val candidate = when {
            cleaned.startsWith(PREFIX) -> cleaned
            cleaned.length == SUFFIX_LENGTH -> "$PREFIX$cleaned"
            else -> cleaned
        }
        return if (VALID_REGEX.matches(candidate)) candidate else null
    }
}

@Serializable
data class UserProfile(
    val uid: String = "",
    val friendCode: String = "",
    val nickname: String = "",
    val selectedSkinId: String = "classic"
)

@Serializable
data class SavedFriend(
    val uid: String,
    val friendCode: String,
    val nickname: String,
    val selectedSkinId: String = "classic",
    val addedAtMillis: Long = 0L
)

enum class PresenceState {
    OFFLINE,
    ONLINE,
    SEARCHING
}

data class FriendLivePresence(
    val friend: SavedFriend,
    val state: PresenceState = PresenceState.OFFLINE,
    val searchingMode: GameMode? = null,
    val lastUpdatedMillis: Long = 0L
) {
    fun statusBadgeText(): String = when (state) {
        PresenceState.OFFLINE -> "Offline"
        PresenceState.ONLINE -> "Online (In Menu)"
        PresenceState.SEARCHING -> {
            val modeLabel = when (searchingMode) {
                GameMode.CO_OP -> "Co-op"
                GameMode.VERSUS -> "Versus"
                null -> "Match"
            }
            "Searching ($modeLabel)"
        }
    }
}

enum class MatchRole {
    HOST,
    GUEST
}

data class MatchRoom(
    val matchId: String,
    val hostUid: String,
    val hostNickname: String,
    val hostSkinId: String,
    val guestUid: String,
    val guestNickname: String,
    val guestSkinId: String,
    val mode: GameMode,
    val state: String = "signaling",
    val createdAtMillis: Long = 0L,
    val myRole: MatchRole
)

sealed interface MatchmakingState {
    data object Idle : MatchmakingState
    data class Searching(
        val mode: GameMode,
        val startedAtMillis: Long = System.currentTimeMillis()
    ) : MatchmakingState

    data class MatchFound(val room: MatchRoom) : MatchmakingState
    data class Error(val message: String) : MatchmakingState
}

/**
 * Pure deterministic matchmaking tie-breaker preventing dual-host race conditions
 * when two friends press PLAY simultaneously.
 */
object MatchmakingTieBreaker {

    fun determineRole(myUid: String, friendUid: String): MatchRole {
        require(myUid.isNotBlank() && friendUid.isNotBlank()) { "UIDs must not be blank" }
        require(myUid != friendUid) { "Cannot match against self" }
        return if (myUid < friendUid) MatchRole.HOST else MatchRole.GUEST
    }

    fun shouldPairWithFriend(
        myUid: String,
        mySearchMode: GameMode,
        friendUid: String,
        friendPresenceState: PresenceState,
        friendSearchMode: GameMode?
    ): MatchRole? {
        if (myUid.isBlank() || friendUid.isBlank() || myUid == friendUid) return null
        if (friendPresenceState != PresenceState.SEARCHING) return null
        if (friendSearchMode != mySearchMode) return null
        return determineRole(myUid, friendUid)
    }

    fun shouldAcceptInvite(hostUid: String, savedFriendUids: Set<String>): Boolean {
        return hostUid.isNotBlank() && hostUid in savedFriendUids
    }
}
