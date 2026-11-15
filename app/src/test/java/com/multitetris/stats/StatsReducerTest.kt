package com.multitetris.stats

import com.multitetris.game.GameMode
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsReducerTest {

    @Test
    fun recordMatch_tracksVersusCareerTotalsAndFriendRivalry() {
        val initial = CareerStatsSnapshot()
        val record1 = MatchStatRecord(
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            localPlayerId = 1,
            localUid = "me",
            didLocalPlayerWin = true,
            winningTeam = null,
            sharedScore = 0,
            sharedLines = 0,
            matchDurationMs = 25_000L,
            participants = listOf(
                MatchParticipantStat(1, "me", TeamColor.NONE, placement = 1, score = 1400, linesCleared = 10, isDisconnected = false),
                MatchParticipantStat(2, "friend_a", TeamColor.NONE, placement = 2, score = 900, linesCleared = 6, isDisconnected = false),
                MatchParticipantStat(3, "stranger", TeamColor.NONE, placement = 3, score = 400, linesCleared = 2, isDisconnected = false)
            ),
            friendUids = setOf("friend_a")
        )

        val (after1, isNewCoop1) = StatsReducer.recordMatch(initial, record1)
        assertFalse(isNewCoop1)
        assertEquals(1, after1.totalMatches)
        assertEquals(1, after1.versusWins)
        assertEquals(0, after1.versusLosses)
        assertEquals(100, after1.versusWinRatePercent)
        assertEquals(10, after1.totalLinesCleared)
        assertEquals(1400, after1.highestSoloOrMatchScore)
        assertEquals(1, after1.modeStats["VERSUS_FFA"]?.wins)
        assertEquals(1, after1.friendRivalry["friend_a"]?.versusWinsAgainst)
        assertEquals(0, after1.friendRivalry["friend_a"]?.versusLossesAgainst)
        assertFalse("stranger" in after1.friendRivalry)

        // Second match: loss against friend_a
        val record2 = MatchStatRecord(
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            localPlayerId = 1,
            localUid = "me",
            didLocalPlayerWin = false,
            winningTeam = null,
            sharedScore = 0,
            sharedLines = 0,
            matchDurationMs = 30_000L,
            participants = listOf(
                MatchParticipantStat(1, "me", TeamColor.NONE, placement = 2, score = 800, linesCleared = 4, isDisconnected = false),
                MatchParticipantStat(2, "friend_a", TeamColor.NONE, placement = 1, score = 1200, linesCleared = 8, isDisconnected = false)
            ),
            friendUids = setOf("friend_a")
        )
        val (after2, _) = StatsReducer.recordMatch(after1, record2)
        assertEquals(2, after2.totalMatches)
        assertEquals(1, after2.versusWins)
        assertEquals(1, after2.versusLosses)
        assertEquals(50, after2.versusWinRatePercent)
        assertEquals(14, after2.totalLinesCleared)
        assertEquals(1400, after2.highestSoloOrMatchScore)
        assertEquals(1, after2.friendRivalry["friend_a"]?.versusWinsAgainst)
        assertEquals(1, after2.friendRivalry["friend_a"]?.versusLossesAgainst)
    }

    @Test
    fun recordMatch_ignoresDisconnectedOpponentUnderTenSecondsOnly() {
        val initial = CareerStatsSnapshot()
        val shortMatch = MatchStatRecord(
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            localPlayerId = 1,
            localUid = "me",
            didLocalPlayerWin = true,
            winningTeam = null,
            sharedScore = 0,
            sharedLines = 0,
            matchDurationMs = 8_000L,
            participants = listOf(
                MatchParticipantStat(1, "me", TeamColor.NONE, placement = 1, score = 100, linesCleared = 1, isDisconnected = false),
                MatchParticipantStat(2, "friend_dc", TeamColor.NONE, placement = 2, score = 0, linesCleared = 0, isDisconnected = true)
            ),
            friendUids = setOf("friend_dc")
        )
        val (afterShort, _) = StatsReducer.recordMatch(initial, shortMatch)
        assertFalse("friend_dc" in afterShort.friendRivalry)

        val longMatch = shortMatch.copy(matchDurationMs = 15_000L)
        val (afterLong, _) = StatsReducer.recordMatch(afterShort, longMatch)
        assertEquals(1, afterLong.friendRivalry["friend_dc"]?.versusWinsAgainst)
    }

    @Test
    fun recordMatch_tracksCoopRecordsAndNewBestBadgeAndTeamsRivalry() {
        val coop1 = MatchStatRecord(
            mode = GameMode.CO_OP,
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            localPlayerId = 1,
            localUid = "me",
            didLocalPlayerWin = false,
            winningTeam = null,
            sharedScore = 2200,
            sharedLines = 18,
            matchDurationMs = 60_000L,
            participants = listOf(
                MatchParticipantStat(1, "me", TeamColor.NONE, placement = 0, score = 1200, linesCleared = 10, isDisconnected = false),
                MatchParticipantStat(2, "friend_b", TeamColor.NONE, placement = 0, score = 1000, linesCleared = 8, isDisconnected = false)
            ),
            friendUids = setOf("friend_b")
        )

        val (afterCoop1, newRecord1) = StatsReducer.recordMatch(CareerStatsSnapshot(), coop1)
        assertTrue(newRecord1)
        assertEquals(2200, afterCoop1.bestCoopScore)
        assertEquals(18, afterCoop1.bestCoopLines)
        assertEquals(1, afterCoop1.friendRivalry["friend_b"]?.coopMatchesTogether)
        assertEquals(18, afterCoop1.friendRivalry["friend_b"]?.bestCoopLinesTogether)

        // Lower Co-op score does not trigger NEW BEST RECORD!
        val coop2 = coop1.copy(sharedScore = 1900, sharedLines = 15)
        val (afterCoop2, newRecord2) = StatsReducer.recordMatch(afterCoop1, coop2)
        assertFalse(newRecord2)
        assertEquals(2200, afterCoop2.bestCoopScore)
        assertEquals(2, afterCoop2.friendRivalry["friend_b"]?.coopMatchesTogether)

        // 2v2 Teams: teammate excluded from rivalry W/L, opponent counted
        val teamsMatch = MatchStatRecord(
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.TEAMS_2V2,
            localPlayerId = 1,
            localUid = "me",
            didLocalPlayerWin = true,
            winningTeam = TeamColor.CYAN,
            sharedScore = 0,
            sharedLines = 0,
            matchDurationMs = 45_000L,
            participants = listOf(
                MatchParticipantStat(1, "me", TeamColor.CYAN, placement = 0, score = 1000, linesCleared = 7, isDisconnected = false),
                MatchParticipantStat(2, "friend_b", TeamColor.CYAN, placement = 0, score = 900, linesCleared = 6, isDisconnected = false),
                MatchParticipantStat(3, "friend_c", TeamColor.MAGENTA, placement = 2, score = 500, linesCleared = 3, isDisconnected = false)
            ),
            friendUids = setOf("friend_b", "friend_c")
        )
        val (afterTeams, _) = StatsReducer.recordMatch(afterCoop2, teamsMatch)
        assertEquals(0, afterTeams.friendRivalry["friend_b"]?.versusWinsAgainst ?: 0)
        assertEquals(1, afterTeams.friendRivalry["friend_c"]?.versusWinsAgainst)
        assertEquals(1, afterTeams.modeStats["VERSUS_TEAMS"]?.wins)
    }

    @Test
    fun friendMatchStats_formatsSubtitleCleanly() {
        assertEquals("No matches played yet", FriendMatchStats().formatRivalrySubtitle())
        assertEquals(
            "Versus: 5W - 3L  •  Best Co-op: 42 Lines",
            FriendMatchStats(
                versusWinsAgainst = 5,
                versusLossesAgainst = 3,
                coopMatchesTogether = 2,
                bestCoopLinesTogether = 42
            ).formatRivalrySubtitle()
        )
    }
}
