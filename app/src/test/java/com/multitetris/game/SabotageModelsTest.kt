package com.multitetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SabotageModelsTest {

    @Test
    fun sabotageConfig_hasExpectedDurationsAndResolvesDefaultLoadout() {
        assertEquals(6_000L, SabotageConfig.durationMs(SabotageType.BLINDFOLD_FOG))
        assertEquals(5_500L, SabotageConfig.durationMs(SabotageType.CONTROLS_FLIP))
        assertEquals(3_500L, SabotageConfig.durationMs(SabotageType.BUTTON_FREEZE))
        assertEquals(6_000L, SabotageConfig.durationMs(SabotageType.TURBO_DROP))
        assertEquals(7_000L, SabotageConfig.durationMs(SabotageType.DROP_JAMMER))

        assertEquals(
            listOf(SabotageType.BLINDFOLD_FOG, SabotageType.TURBO_DROP),
            SabotageConfig.resolveLoadout(emptyList())
        )
        assertEquals(
            listOf(SabotageType.BUTTON_FREEZE, SabotageType.BLINDFOLD_FOG),
            SabotageConfig.resolveLoadout(listOf(SabotageType.BUTTON_FREEZE))
        )
    }

    @Test
    fun sabotageConfig_toggleSelectionCapsAtTwoAndReplacesOldest() {
        val step1 = SabotageConfig.toggleSelection(emptyList(), SabotageType.CONTROLS_FLIP)
        assertEquals(listOf(SabotageType.CONTROLS_FLIP), step1)

        val step2 = SabotageConfig.toggleSelection(step1, SabotageType.DROP_JAMMER)
        assertEquals(listOf(SabotageType.CONTROLS_FLIP, SabotageType.DROP_JAMMER), step2)

        val step3 = SabotageConfig.toggleSelection(step2, SabotageType.BUTTON_FREEZE)
        assertEquals(listOf(SabotageType.DROP_JAMMER, SabotageType.BUTTON_FREEZE), step3)

        val step4 = SabotageConfig.toggleSelection(step3, SabotageType.DROP_JAMMER)
        assertEquals(listOf(SabotageType.BUTTON_FREEZE), step4)
    }

    @Test
    fun engine_useSabotage_enforcesEquipValidTargetAndOncePerRound() {
        val engine = MultiTetrisEngine(random = Random(7))
        engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            sabotageEnabled = true,
            equippedSabotages = mapOf(
                1 to listOf(SabotageType.BLINDFOLD_FOG, SabotageType.TURBO_DROP),
                2 to listOf(SabotageType.BUTTON_FREEZE, SabotageType.DROP_JAMMER)
            )
        )

        // Cannot self-target or use unequipped ability
        val afterSelf = engine.useSabotage(1, 1, SabotageType.BLINDFOLD_FOG)
        assertNull(afterSelf.activeSabotageFor(1))
        val afterUnequipped = engine.useSabotage(1, 2, SabotageType.BUTTON_FREEZE)
        assertNull(afterUnequipped.activeSabotageFor(2))

        // Valid equipped sabotage applies once
        val afterValid = engine.useSabotage(1, 2, SabotageType.BLINDFOLD_FOG, attackerLabel = "HostNova")
        val debuff = afterValid.activeSabotageFor(2)
        assertNotNull(debuff)
        assertEquals(SabotageType.BLINDFOLD_FOG, debuff?.type)
        assertEquals(SabotageConfig.durationMs(SabotageType.BLINDFOLD_FOG), debuff?.remainingMs)
        assertEquals("HostNova", debuff?.sourceLabel)
        assertTrue(SabotageType.BLINDFOLD_FOG in afterValid.usedSabotagesFor(1))

        // Once spent, cannot reuse BLINDFOLD_FOG in the same round
        engine.tickSabotageAndCoopGlitch(SabotageConfig.durationMs(SabotageType.BLINDFOLD_FOG))
        assertNull(engine.state.activeSabotageFor(2))
        val afterReuse = engine.useSabotage(1, 2, SabotageType.BLINDFOLD_FOG)
        assertNull(afterReuse.activeSabotageFor(2))
    }

    @Test
    fun validSabotageTargetsFor_respectsFfaTwoVTwoAndOneVThreeTeams() {
        val ffaState = GameState(
            config = BoardConfig.forPlayerCount(4),
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.FREE_FOR_ALL,
            sabotageEnabled = true,
            eliminatedPlayerIds = setOf(3),
            disconnectedPlayerIds = setOf(4)
        )
        assertEquals(listOf(2), ffaState.validSabotageTargetsFor(1))

        val twoVTwoState = GameState(
            config = BoardConfig.forPlayerCount(4),
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.TEAMS_2V2,
            sabotageEnabled = true,
            playerTeams = mapOf(
                1 to TeamColor.CYAN,
                2 to TeamColor.CYAN,
                3 to TeamColor.MAGENTA,
                4 to TeamColor.MAGENTA
            )
        )
        assertEquals(listOf(3, 4), twoVTwoState.validSabotageTargetsFor(1))
        assertEquals(listOf(1, 2), twoVTwoState.validSabotageTargetsFor(3))

        val oneVThreeState = GameState(
            config = BoardConfig.forPlayerCount(4),
            mode = GameMode.VERSUS,
            versusSubMode = VersusSubMode.TEAMS_2V2,
            sabotageEnabled = true,
            playerTeams = mapOf(
                1 to TeamColor.CYAN,
                2 to TeamColor.MAGENTA,
                3 to TeamColor.MAGENTA,
                4 to TeamColor.MAGENTA
            )
        )
        assertEquals(listOf(2, 3, 4), oneVThreeState.validSabotageTargetsFor(1))
        assertEquals(listOf(1), oneVThreeState.validSabotageTargetsFor(2))
        assertEquals(listOf(1), oneVThreeState.validSabotageTargetsFor(4))

        val engine = MultiTetrisEngine(random = Random(3))
        engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(4),
            versusSubMode = VersusSubMode.TEAMS_2V2,
            playerTeams = twoVTwoState.playerTeams,
            sabotageEnabled = true,
            equippedSabotages = (1..4).associateWith { listOf(SabotageType.TURBO_DROP, SabotageType.DROP_JAMMER) }
        )
        // Friendly fire is rejected
        val afterFriendly = engine.useSabotage(1, 2, SabotageType.TURBO_DROP)
        assertNull(afterFriendly.activeSabotageFor(2))
        assertFalse(SabotageType.TURBO_DROP in afterFriendly.usedSabotagesFor(1))
    }

    @Test
    fun engine_turboDropTriplesGravitySpeed() {
        val engine = MultiTetrisEngine(random = Random(11))
        engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            sabotageEnabled = true,
            equippedSabotages = mapOf(
                1 to listOf(SabotageType.TURBO_DROP, SabotageType.BLINDFOLD_FOG),
                2 to listOf(SabotageType.TURBO_DROP, SabotageType.BLINDFOLD_FOG)
            )
        )
        val normalInterval = engine.getDropIntervalMs(playerId = 2, currentTimeMillis = 1_000L)
        engine.useSabotage(1, 2, SabotageType.TURBO_DROP)
        val turboInterval = engine.getDropIntervalMs(playerId = 2, currentTimeMillis = 1_000L)
        assertEquals((normalInterval / 3L).coerceAtLeast(40L), turboInterval)
    }

    @Test
    fun engine_buttonFreezeBlocksAllActions_andDropJammerBlocksOnlyHardDrop() {
        val freezeEngine = MultiTetrisEngine(random = Random(5), pieceBagOverride = { TetrominoType.T })
        freezeEngine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            sabotageEnabled = true,
            equippedSabotages = mapOf(
                1 to listOf(SabotageType.BUTTON_FREEZE, SabotageType.DROP_JAMMER),
                2 to listOf(SabotageType.BUTTON_FREEZE, SabotageType.DROP_JAMMER)
            )
        )
        freezeEngine.useSabotage(1, 2, SabotageType.BUTTON_FREEZE)
        val initialP2 = freezeEngine.state.activePieces[2]!!

        freezeEngine.applyAction(2, PlayerAction.MOVE_LEFT)
        freezeEngine.applyAction(2, PlayerAction.MOVE_RIGHT)
        freezeEngine.applyAction(2, PlayerAction.ROTATE_CW)
        freezeEngine.applyAction(2, PlayerAction.SOFT_DROP)
        freezeEngine.applyAction(2, PlayerAction.HARD_DROP)
        assertEquals(initialP2, freezeEngine.state.activePieces[2])

        val jamEngine = MultiTetrisEngine(random = Random(5), pieceBagOverride = { TetrominoType.T })
        jamEngine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            sabotageEnabled = true,
            equippedSabotages = mapOf(
                1 to listOf(SabotageType.DROP_JAMMER, SabotageType.BLINDFOLD_FOG),
                2 to listOf(SabotageType.DROP_JAMMER, SabotageType.BLINDFOLD_FOG)
            )
        )
        jamEngine.useSabotage(1, 2, SabotageType.DROP_JAMMER)
        val startScore = jamEngine.state.playerScores[2] ?: 0
        jamEngine.applyAction(2, PlayerAction.HARD_DROP)
        // Hard drop blocked: score and piece unchanged
        assertEquals(startScore, jamEngine.state.playerScores[2] ?: 0)
        // Movement/rotation still permitted while jammed
        jamEngine.applyAction(2, PlayerAction.ROTATE_CW)
        assertEquals(1, jamEngine.state.activePieces[2]?.rotation)
    }

    @Test
    fun engine_tickSabotageAndCoopGlitch_expiresDebuffsAndTriggersSystemGlitch() {
        val engine = MultiTetrisEngine(random = Random(99))
        engine.startNewGame(
            mode = GameMode.CO_OP,
            config = BoardConfig.forPlayerCount(2),
            sabotageEnabled = true
        )

        // Before 19s: no warning and no active glitches
        engine.tickSabotageAndCoopGlitch(18_900L)
        assertFalse(engine.state.systemGlitchWarningActive)
        assertTrue(engine.state.activeSabotages.isEmpty())

        // At 19s: 3s warning activates before the 22s glitch strike
        engine.tickSabotageAndCoopGlitch(200L)
        assertTrue(engine.state.systemGlitchWarningActive)
        assertTrue(engine.state.activeSabotages.isEmpty())

        // Crossing 22s: clears warning and applies a SYSTEM sabotage to a living player
        engine.tickSabotageAndCoopGlitch(3_000L)
        assertFalse(engine.state.systemGlitchWarningActive)
        assertEquals(1, engine.state.activeSabotages.size)
        val glitchEntry = engine.state.activeSabotages.values.first()
        assertEquals("SYSTEM", glitchEntry.sourceLabel)

        // Advancing past max duration removes expired debuff cleanly
        engine.tickSabotageAndCoopGlitch(SabotageConfig.durationMs(glitchEntry.type))
        assertTrue(engine.state.activeSabotages.isEmpty())
    }
}
