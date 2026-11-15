package com.multitetris.network

import com.multitetris.game.GameMode
import com.multitetris.network.social.FriendCodeGenerator
import com.multitetris.network.social.FriendLivePresence
import com.multitetris.network.social.MatchRole
import com.multitetris.network.social.MatchmakingTieBreaker
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.SavedFriend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MatchmakingAndFriendCodeTest {

    @Test
    fun friendCodeGenerator_formatAndAlphabetExclusion() {
        val rng = Random(42)
        val forbiddenChars = setOf('0', 'O', '1', 'I')

        repeat(200) {
            val code = FriendCodeGenerator.generate(rng)
            assertEquals(8, code.length)
            assertTrue(code.startsWith("MT-"))
            val suffix = code.removePrefix("MT-")
            assertEquals(5, suffix.length)
            assertTrue(suffix.none { it in forbiddenChars })
            assertNotNull(FriendCodeGenerator.normalizeAndValidate(code))
        }

        assertEquals("Player_7K9P", FriendCodeGenerator.defaultNicknameFor("MT-A7K9P"))
    }

    @Test
    fun friendCodeNormalizer_validatesAndNormalizesInput() {
        assertEquals("MT-7K9P2", FriendCodeGenerator.normalizeAndValidate("mt-7k9p2"))
        assertEquals("MT-7K9P2", FriendCodeGenerator.normalizeAndValidate("  7k9p2  "))
        assertNull(FriendCodeGenerator.normalizeAndValidate("MT-01IOA"))
        assertNull(FriendCodeGenerator.normalizeAndValidate("MT-123"))
        assertNull(FriendCodeGenerator.normalizeAndValidate("AB-7K9P2"))
    }

    @Test
    fun deterministicTieBreaking_assignsSmallerUidAsHostAndLargerUidAsGuest() {
        val uidAlpha = "aaa_user_1"
        val uidBeta = "zzz_user_2"

        assertEquals(MatchRole.HOST, MatchmakingTieBreaker.determineRole(uidAlpha, uidBeta))
        assertEquals(MatchRole.GUEST, MatchmakingTieBreaker.determineRole(uidBeta, uidAlpha))
    }

    @Test
    fun matchmakingPairing_requiresSameModeAndSearchingStatus() {
        val uidAlpha = "uid_a"
        val uidBeta = "uid_b"

        // Mismatched mode -> no match
        assertNull(
            MatchmakingTieBreaker.shouldPairWithFriend(
                myUid = uidAlpha,
                mySearchMode = GameMode.CO_OP,
                friendUid = uidBeta,
                friendPresenceState = PresenceState.SEARCHING,
                friendSearchMode = GameMode.VERSUS
            )
        )

        // Friend only online in menu -> no match
        assertNull(
            MatchmakingTieBreaker.shouldPairWithFriend(
                myUid = uidAlpha,
                mySearchMode = GameMode.CO_OP,
                friendUid = uidBeta,
                friendPresenceState = PresenceState.ONLINE,
                friendSearchMode = null
            )
        )

        // Both searching in VERSUS -> deterministic HOST/GUEST roles
        assertEquals(
            MatchRole.HOST,
            MatchmakingTieBreaker.shouldPairWithFriend(
                myUid = uidAlpha,
                mySearchMode = GameMode.VERSUS,
                friendUid = uidBeta,
                friendPresenceState = PresenceState.SEARCHING,
                friendSearchMode = GameMode.VERSUS
            )
        )
        assertEquals(
            MatchRole.GUEST,
            MatchmakingTieBreaker.shouldPairWithFriend(
                myUid = uidBeta,
                mySearchMode = GameMode.VERSUS,
                friendUid = uidAlpha,
                friendPresenceState = PresenceState.SEARCHING,
                friendSearchMode = GameMode.VERSUS
            )
        )

        // Invite acceptance checks saved friend list
        val savedFriends = setOf(uidAlpha)
        assertTrue(MatchmakingTieBreaker.shouldAcceptInvite(uidAlpha, savedFriends))
        assertFalse(MatchmakingTieBreaker.shouldAcceptInvite("stranger_uid", savedFriends))
    }

    @Test
    fun friendLivePresence_formatsStatusBadgesAccurately() {
        val friend = SavedFriend(uid = "u1", friendCode = "MT-23456", nickname = "Nova")

        assertEquals(
            "Offline",
            FriendLivePresence(friend, PresenceState.OFFLINE).statusBadgeText()
        )
        assertEquals(
            "Online (In Menu)",
            FriendLivePresence(friend, PresenceState.ONLINE).statusBadgeText()
        )
        assertEquals(
            "Searching (Co-op)",
            FriendLivePresence(friend, PresenceState.SEARCHING, GameMode.CO_OP).statusBadgeText()
        )
        assertEquals(
            "Searching (Versus)",
            FriendLivePresence(friend, PresenceState.SEARCHING, GameMode.VERSUS).statusBadgeText()
        )
    }
}
