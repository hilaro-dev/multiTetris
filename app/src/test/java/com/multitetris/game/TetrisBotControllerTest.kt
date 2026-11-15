package com.multitetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetrisBotControllerTest {

    @Test
    fun botPlansAndSteersToLandingTarget() {
        val engine = MultiTetrisEngine()
        val state = engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            playerSkins = mapOf(1 to "classic", 2 to "neon_cyber"),
            currentTimeMillis = 1000L,
            sabotageEnabled = false
        )

        val bot = TetrisBotController(botPlayerId = 2, difficulty = BotDifficulty.HARD)
        var working = state
        var steps = 0
        var hardDropped = false

        while (steps < 20) {
            val action = bot.nextAction(working) ?: break
            if (action == PlayerAction.HARD_DROP) {
                hardDropped = true
                working = engine.applyAction(2, action, 1000L + steps * 100L)
                break
            }
            assertTrue(
                action == PlayerAction.ROTATE_CW ||
                    action == PlayerAction.MOVE_LEFT ||
                    action == PlayerAction.MOVE_RIGHT
            )
            working = engine.applyAction(2, action, 1000L + steps * 100L)
            steps++
        }

        assertTrue("Bot should finish steering with a hard drop", hardDropped)
    }

    @Test
    fun botRespectsButtonFreezeAndTriggersSabotageAboveRow10() {
        val engine = MultiTetrisEngine()
        var state = engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            playerSkins = mapOf(1 to "classic", 2 to "neon_cyber"),
            currentTimeMillis = 1000L,
            sabotageEnabled = true,
            equippedSabotages = mapOf(
                1 to listOf(SabotageType.BUTTON_FREEZE, SabotageType.TURBO_DROP),
                2 to listOf(SabotageType.CONTROLS_FLIP, SabotageType.BUTTON_FREEZE)
            )
        )

        val bot = TetrisBotController(botPlayerId = 2, difficulty = BotDifficulty.NORMAL)
        assertNull("No sabotage when human stack is empty", bot.maybeSelectSabotage(state, 10_000L))

        // Place a locked block at row 8 (above row 10)
        val newGrid = state.grid.mapIndexed { r, row ->
            if (r == 8) {
                row.mapIndexed { c, cell ->
                    if (c == 1) LockedCell(playerId = 1, tetrominoType = TetrominoType.O, skinId = "classic") else cell
                }
            } else {
                row
            }
        }
        state = state.copy(grid = newGrid)

        val sabotage = bot.maybeSelectSabotage(state, 10_000L)
        assertNotNull("Bot should trigger sabotage when stack is above row 10", sabotage)
        assertEquals(1, sabotage?.first)
        assertEquals(SabotageType.CONTROLS_FLIP, sabotage?.second)

        // Freeze Bot controls -> nextAction should return null
        val frozenState = engine.useSabotage(
            attackerPlayerId = 1,
            targetPlayerId = 2,
            sabotageType = SabotageType.BUTTON_FREEZE,
            attackerLabel = "P1"
        )
        assertNull(bot.nextAction(frozenState))
    }

    @Test
    fun botCompletesBottomLineClearInsteadOfStackingRandomly() {
        val engine = MultiTetrisEngine()
        var state = engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(2),
            playerSkins = mapOf(1 to "classic", 2 to "neon_cyber"),
            currentTimeMillis = 1000L,
            sabotageEnabled = false
        )

        val rows = state.config.height
        val cols = state.config.width
        val bottomRow = rows - 1
        // Fill bottom row except columns 0..3 so a horizontal I-piece at x=0 clears the line
        val filledGrid = state.grid.mapIndexed { r, row ->
            if (r == bottomRow) {
                row.mapIndexed { c, _ ->
                    if (c >= 4) LockedCell(playerId = 1, tetrominoType = TetrominoType.O, skinId = "classic") else null
                }
            } else {
                row
            }
        }
        val botPiece = ActivePiece(
            playerId = 2,
            tetrominoType = TetrominoType.I,
            x = cols - 5,
            y = 1,
            rotation = 1,
            skinId = "neon_cyber"
        )
        state = state.copy(
            grid = filledGrid,
            activePieces = state.activePieces + (2 to botPiece)
        )

        val bot = TetrisBotController(botPlayerId = 2, difficulty = BotDifficulty.NORMAL)
        var working = state
        var steps = 0
        while (steps < 25) {
            val action = bot.nextAction(working) ?: break
            working = applyActionToState(working, 2, action)
            if (action == PlayerAction.HARD_DROP) break
            steps++
        }

        // After locking the horizontal I-piece into columns 0..3 of bottomRow, the full row clears
        val bottomRemainingFilled = working.grid[bottomRow].count { it != null }
        assertEquals(0, bottomRemainingFilled)
    }

    @Test
    fun botTargetsOpposingTeamInTeams2v2() {
        val engine = MultiTetrisEngine()
        var state = engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(4),
            playerSkins = mapOf(1 to "classic", 2 to "neon_cyber", 3 to "gold", 4 to "pixel"),
            currentTimeMillis = 1000L,
            versusSubMode = VersusSubMode.TEAMS_2V2,
            playerTeams = mapOf(
                1 to TeamColor.CYAN,
                2 to TeamColor.CYAN,
                3 to TeamColor.MAGENTA,
                4 to TeamColor.MAGENTA
            ),
            sabotageEnabled = true,
            equippedSabotages = mapOf(
                1 to SabotageConfig.defaultLoadout,
                2 to listOf(SabotageType.TURBO_DROP, SabotageType.DROP_JAMMER),
                3 to SabotageConfig.defaultLoadout,
                4 to SabotageConfig.defaultLoadout
            )
        )
        val highStackGrid = state.grid.mapIndexed { r, row ->
            if (r == 6) {
                row.mapIndexed { c, cell ->
                    if (c == 0) LockedCell(playerId = 3, tetrominoType = TetrominoType.O, skinId = "gold") else cell
                }
            } else {
                row
            }
        }
        state = state.copy(grid = highStackGrid)

        val bot = TetrisBotController(botPlayerId = 2, difficulty = BotDifficulty.NORMAL)
        val sabotage = bot.maybeSelectSabotage(state, 10_000L)
        assertNotNull(sabotage)
        assertTrue("Bot on CYAN team must target MAGENTA (3 or 4)", sabotage?.first == 3 || sabotage?.first == 4)
    }

    private fun applyActionToState(state: GameState, playerId: Int, action: PlayerAction): GameState {
        val piece = state.activePieces[playerId] ?: return state
        val rows = state.config.height
        val cols = state.config.width
        fun fits(p: ActivePiece): Boolean = p.occupiedCells().all { c ->
            c.x in 0 until cols && c.y in 0 until rows && state.grid[c.y][c.x] == null
        }
        return when (action) {
            PlayerAction.MOVE_LEFT -> {
                val moved = piece.copy(x = piece.x - 1)
                if (fits(moved)) state.copy(activePieces = state.activePieces + (playerId to moved)) else state
            }
            PlayerAction.MOVE_RIGHT -> {
                val moved = piece.copy(x = piece.x + 1)
                if (fits(moved)) state.copy(activePieces = state.activePieces + (playerId to moved)) else state
            }
            PlayerAction.ROTATE_CW -> {
                val rotated = piece.copy(rotation = (piece.rotation + 1) % 4)
                if (fits(rotated)) state.copy(activePieces = state.activePieces + (playerId to rotated)) else state
            }
            PlayerAction.SOFT_DROP -> {
                val down = piece.copy(y = piece.y + 1)
                if (fits(down)) state.copy(activePieces = state.activePieces + (playerId to down)) else state
            }
            PlayerAction.HARD_DROP -> {
                var dropY = piece.y
                while (fits(piece.copy(y = dropY + 1))) dropY++
                val locked = piece.copy(y = dropY)
                val mutableGrid = state.grid.map { it.toMutableList() }.toMutableList()
                for (c in locked.occupiedCells()) {
                    mutableGrid[c.y][c.x] = LockedCell(playerId, locked.tetrominoType, "neon_cyber")
                }
                val remaining = mutableGrid.filterNot { r -> r.all { it != null } }
                val cleared = rows - remaining.size
                val finalGrid = List(cleared) { List<LockedCell?>(cols) { null } } + remaining
                state.copy(grid = finalGrid)
            }
        }
    }
}
