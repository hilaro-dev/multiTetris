package com.multitetris.economy

import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.ui.skin.SkinCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyAndMissionsTest {

    @Test
    fun matchRewardCalculator_requiresLineOrMinimumDuration() {
        val state = GameState(mode = GameMode.VERSUS, winnerPlayerId = 1, playerLines = mapOf(1 to 0, 2 to 0))
        val ineligible = MatchRewardCalculator.calculate(
            gameState = state,
            localPlayerId = 1,
            isLocalTest = false,
            matchDurationMillis = 10_000L
        )
        assertEquals(0, ineligible.totalCoins)
        assertTrue(ineligible.lines.isEmpty())

        val eligibleByTime = MatchRewardCalculator.calculate(
            gameState = state,
            localPlayerId = 1,
            isLocalTest = false,
            matchDurationMillis = 15_000L
        )
        assertEquals(60, eligibleByTime.totalCoins)
    }

    @Test
    fun matchRewardCalculator_versusAndCoopRatesAndLocalHalfRate() {
        val versusState = GameState(
            mode = GameMode.VERSUS,
            winnerPlayerId = 1,
            playerLines = mapOf(1 to 3, 2 to 2)
        )
        val winnerReward = MatchRewardCalculator.calculate(versusState, localPlayerId = 1, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(75, winnerReward.totalCoins) // 60 + 3*5

        val runnerUpReward = MatchRewardCalculator.calculate(versusState, localPlayerId = 2, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(35, runnerUpReward.totalCoins) // 25 + 2*5

        val localWinnerReward = MatchRewardCalculator.calculate(versusState, localPlayerId = 1, isLocalTest = true, matchDurationMillis = 20_000L)
        assertEquals(37, localWinnerReward.totalCoins) // 30 + 7

        val coopState = GameState(mode = GameMode.CO_OP, sharedLines = 7)
        val coopReward = MatchRewardCalculator.calculate(coopState, localPlayerId = 1, isLocalTest = false, matchDurationMillis = 25_000L)
        assertEquals(40, coopReward.totalCoins) // 20 + (7/3)*10

        val localCoopReward = MatchRewardCalculator.calculate(coopState, localPlayerId = 1, isLocalTest = true, matchDurationMillis = 25_000L)
        assertEquals(20, localCoopReward.totalCoins) // 10 + 10
    }

    @Test
    fun dailyMissionManager_tracksProgressClaimsAndResetsAtMidnight() {
        var currentDay = "2026-11-15"
        val manager = DailyMissionManager(dateProvider = { currentDay })

        var snapshot = manager.ensureForToday(null)
        assertEquals(3, snapshot.missions.size)

        snapshot = manager.recordLinesCleared(snapshot, 10)
        snapshot = manager.recordLinesCleared(snapshot, 10)
        val clearMission = snapshot.missions.first { it.id == DailyMissionManager.MISSION_CLEAR_LINES }
        assertEquals(15, clearMission.progress)
        assertTrue(clearMission.isReadyToClaim)

        snapshot = manager.recordMatchCompleted(snapshot, GameMode.VERSUS, isMultiplayer = true, didLocalPlayerWin = true)
        snapshot = manager.recordMatchCompleted(snapshot, GameMode.VERSUS, isMultiplayer = true, didLocalPlayerWin = true)
        val versusMission = snapshot.missions.first { it.id == DailyMissionManager.MISSION_WIN_VERSUS }
        assertEquals(2, versusMission.progress)
        assertTrue(versusMission.isReadyToClaim)

        val (afterClaim, reward) = manager.claimMission(snapshot, DailyMissionManager.MISSION_CLEAR_LINES)
        assertEquals(80, reward)
        assertTrue(afterClaim.missions.first { it.id == DailyMissionManager.MISSION_CLEAR_LINES }.isClaimed)
        assertFalse(afterClaim.missions.first { it.id == DailyMissionManager.MISSION_CLEAR_LINES }.isReadyToClaim)

        val (_, duplicateClaimReward) = manager.claimMission(afterClaim, DailyMissionManager.MISSION_CLEAR_LINES)
        assertEquals(0, duplicateClaimReward)

        // Advance past midnight
        currentDay = "2026-11-16"
        val nextDaySnapshot = manager.ensureForToday(afterClaim)
        assertEquals("2026-11-16", nextDaySnapshot.dateKey)
        assertTrue(nextDaySnapshot.missions.all { it.progress == 0 && !it.isClaimed })
    }

    @Test
    fun skinCatalog_hasExpectedPricesForAllSixSkins() {
        assertEquals(6, SkinCatalog.items.size)
        assertEquals(0, SkinCatalog.priceFor("classic"))
        assertEquals(150, SkinCatalog.priceFor("neon_cyber"))
        assertEquals(300, SkinCatalog.priceFor("pixel_crystal"))
        assertEquals(500, SkinCatalog.priceFor("obsidian_magma"))
        assertEquals(750, SkinCatalog.priceFor("hologram_glitch"))
        assertEquals(1200, SkinCatalog.priceFor("golden_flex"))
    }
}
