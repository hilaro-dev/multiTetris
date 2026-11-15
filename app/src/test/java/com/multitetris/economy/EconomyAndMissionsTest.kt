package com.multitetris.economy

import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
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
    fun skinCatalog_hasExpectedTwentySkinsTiersAndPrices() {
        assertEquals(20, SkinCatalog.items.size)
        assertEquals(5, SkinCatalog.items.count { it.tier == com.multitetris.ui.skin.SkinTier.BASIC })
        assertEquals(10, SkinCatalog.items.count { it.tier == com.multitetris.ui.skin.SkinTier.RARE })
        assertEquals(5, SkinCatalog.items.count { it.tier == com.multitetris.ui.skin.SkinTier.MYTHIC })

        assertEquals(0, SkinCatalog.priceFor("classic"))
        assertEquals(100, SkinCatalog.priceFor("minimal_matte"))
        assertEquals(150, SkinCatalog.priceFor("pastel_dream"))
        assertEquals(200, SkinCatalog.priceFor("retro_gameboy"))
        assertEquals(250, SkinCatalog.priceFor("blueprint_grid"))

        assertEquals(350, SkinCatalog.priceFor("neon_cyber"))
        assertEquals(400, SkinCatalog.priceFor("pixel_crystal"))
        assertEquals(450, SkinCatalog.priceFor("frosted_ice"))
        assertEquals(500, SkinCatalog.priceFor("toxic_slime"))
        assertEquals(550, SkinCatalog.priceFor("sakura_petal"))
        assertEquals(600, SkinCatalog.priceFor("obsidian_magma"))
        assertEquals(650, SkinCatalog.priceFor("cyber_matrix"))
        assertEquals(700, SkinCatalog.priceFor("sunset_synthwave"))
        assertEquals(750, SkinCatalog.priceFor("royal_amethyst"))
        assertEquals(800, SkinCatalog.priceFor("electric_volt"))

        assertEquals(1200, SkinCatalog.priceFor("hologram_glitch"))
        assertEquals(1500, SkinCatalog.priceFor("golden_flex"))
        assertEquals(1800, SkinCatalog.priceFor("cosmic_nebula"))
        assertEquals(2200, SkinCatalog.priceFor("dragon_inferno"))
        assertEquals(3000, SkinCatalog.priceFor("quantum_singularity"))
    }

    @Test
    fun matchRewardCalculator_threeAndFourPlayerFfaAndTwoVTwoTeamsPayouts() {
        val ffa3State = GameState(
            config = BoardConfig.forPlayerCount(3),
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            winnerPlayerId = 2,
            playerPlacements = mapOf(2 to 1, 1 to 2, 3 to 3),
            playerLines = mapOf(1 to 1, 2 to 2, 3 to 0)
        )
        val ffaWinner = MatchRewardCalculator.calculate(ffa3State, localPlayerId = 2, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(85, ffaWinner.totalCoins) // 75 + 2*5

        val ffaSecond = MatchRewardCalculator.calculate(ffa3State, localPlayerId = 1, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(45, ffaSecond.totalCoins) // 40 + 1*5

        val ffaThird = MatchRewardCalculator.calculate(ffa3State, localPlayerId = 3, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(20, ffaThird.totalCoins) // 20 + 0*5

        val disconnectedState = ffa3State.copy(disconnectedPlayerIds = setOf(3))
        val ffaDisconnected = MatchRewardCalculator.calculate(disconnectedState, localPlayerId = 3, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(0, ffaDisconnected.totalCoins)

        val twoVTwoState = GameState(
            config = BoardConfig.forPlayerCount(4),
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.TEAMS_2V2,
            playerTeams = mapOf(
                1 to TeamColor.CYAN,
                2 to TeamColor.CYAN,
                3 to TeamColor.MAGENTA,
                4 to TeamColor.MAGENTA
            ),
            winningTeam = TeamColor.CYAN,
            winnerPlayerId = 1,
            playerLines = mapOf(1 to 2, 2 to 1, 3 to 1, 4 to 0)
        )
        val teamWinP2 = MatchRewardCalculator.calculate(twoVTwoState, localPlayerId = 2, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(75, teamWinP2.totalCoins) // 70 + 1*5

        val teamLoseP3 = MatchRewardCalculator.calculate(twoVTwoState, localPlayerId = 3, isLocalTest = false, matchDurationMillis = 20_000L)
        assertEquals(35, teamLoseP3.totalCoins) // 30 + 1*5
    }
}
