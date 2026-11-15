package com.multitetris.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiTetrisEngineTest {

    @Test
    fun simultaneousSpawn_placesPlayer1AtCol2AndPlayer2AtCol6() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.T })
        val state = engine.startNewGame(
            mode = GameMode.CO_OP,
            config = BoardConfig(width = 10, height = 20, playerCount = 2),
            playerSkins = mapOf(1 to "classic", 2 to "neon_cyber")
        )

        assertEquals(10, state.config.width)
        assertEquals(20, state.config.height)
        assertEquals(2, state.activePieces.size)

        val p1 = state.activePieces[1]!!
        val p2 = state.activePieces[2]!!
        assertEquals(2, p1.x)
        assertEquals(0, p1.y)
        assertEquals("classic", p1.skinId)
        assertEquals(6, p2.x)
        assertEquals(0, p2.y)
        assertEquals("neon_cyber", p2.skinId)

        assertNotNull(state.ghostPieces[1])
        assertNotNull(state.ghostPieces[2])
        assertEquals(TetrominoType.T, state.nextPieces[1])
        assertEquals(TetrominoType.T, state.nextPieces[2])
    }

    @Test
    fun dynamicBoardConfig_supportsThreePlayers() {
        val config = BoardConfig(width = 12, height = 20, playerCount = 3)
        assertEquals(3, config.spawnZones.size)
        assertEquals(0..3, config.spawnZoneFor(1).columnRange)
        assertEquals(4..7, config.spawnZoneFor(2).columnRange)
        assertEquals(8..11, config.spawnZoneFor(3).columnRange)
    }

    @Test
    fun horizontalCollision_blocksMoveAndRotationIntoOtherActivePiece() {
        val engine = MultiTetrisEngine()
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)
        val p1 = ActivePiece(
            playerId = 1,
            tetrominoType = TetrominoType.O,
            rotation = 0,
            x = 2,
            y = 5,
            skinId = "classic"
        )
        val p2 = ActivePiece(
            playerId = 2,
            tetrominoType = TetrominoType.O,
            rotation = 0,
            x = 4,
            y = 5,
            skinId = "neon_cyber"
        )

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.CO_OP,
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.I, 2 to TetrominoType.I)
            )
        )

        // P1 occupies x=2..3, P2 occupies x=4..5 -> moving P1 right must be blocked
        val afterBlockedMove = engine.applyAction(1, PlayerAction.MOVE_RIGHT)
        assertEquals(2, afterBlockedMove.activePieces[1]!!.x)

        // Also verify rotation is blocked when rotating into another active piece
        val p1Bar = ActivePiece(
            playerId = 1,
            tetrominoType = TetrominoType.I,
            rotation = 1, // vertical at x+1 = 3, y = 4..7
            x = 2,
            y = 5,
            skinId = "classic"
        )
        val p2Wall = ActivePiece(
            playerId = 2,
            tetrominoType = TetrominoType.O,
            rotation = 0,
            x = 4,
            y = 5,
            skinId = "neon_cyber"
        )
        val p1LeftWall = ActivePiece(
            playerId = 2,
            tetrominoType = TetrominoType.O,
            rotation = 0,
            x = 0,
            y = 5,
            skinId = "neon_cyber"
        )
        // Surround vertical I piece closely on both sides so rotating horizontal hits P2
        val gridWithLeftWall = List(20) { r ->
            List(10) { c ->
                if (c <= 1 && r in 4..7) LockedCell(1, TetrominoType.O, "classic") else null
            }
        }
        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.CO_OP,
                grid = gridWithLeftWall,
                activePieces = mapOf(1 to p1Bar, 2 to p2Wall),
                nextPieces = mapOf(1 to TetrominoType.T, 2 to TetrominoType.T)
            )
        )
        val afterBlockedRotate = engine.applyAction(1, PlayerAction.ROTATE_CW)
        assertEquals(1, afterBlockedRotate.activePieces[1]!!.rotation)
        assertEquals(2, afterBlockedRotate.activePieces[1]!!.x)
    }

    @Test
    fun verticalCollision_pieceLandingOnFallingPiecePausesInsteadOfLocking() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)

        // P1 O-piece at (4, 3) occupying rows 3..4; P2 O-piece directly below at (4, 5) occupying rows 5..6
        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 4, y = 3, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 4, y = 5, skinId = "neon_cyber")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.CO_OP,
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O)
            )
        )

        // Stepping P1 down collides with P2: P1 must pause at y=3 instead of locking into grid
        val pausedState = engine.stepPlayerDown(1)
        val updatedP1 = pausedState.activePieces[1]!!
        assertEquals(3, updatedP1.y)
        assertTrue(updatedP1.isPausedByPiece)
        // Grid must remain completely empty (P1 did NOT lock mid-air)
        assertTrue(pausedState.grid.flatten().all { it == null })

        // Move P2 two columns to the right so the 2-wide O-piece clears P1 completely
        engine.applyAction(2, PlayerAction.MOVE_RIGHT)
        val movedAwayState = engine.applyAction(2, PlayerAction.MOVE_RIGHT)
        val resumedP1 = movedAwayState.activePieces[1]!!
        assertFalse(resumedP1.isPausedByPiece)

        // P1 can now fall down freely
        val afterStep = engine.stepPlayerDown(1)
        assertEquals(4, afterStep.activePieces[1]!!.y)
    }

    @Test
    fun coopMode_sharesScoreAndLevelOnLineClear() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)

        // Fill bottom row except columns 2 and 3
        val grid = MutableList(20) { MutableList<LockedCell?>(10) { null } }
        for (c in 0 until 10) {
            if (c != 2 && c != 3) {
                grid[19][c] = LockedCell(2, TetrominoType.I, "neon_cyber")
            }
        }

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 2, y = 18, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 6, y = 0, skinId = "neon_cyber")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.CO_OP,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.T, 2 to TetrominoType.T)
            )
        )

        // P1 at y=18 (resting on bottom row 19) locks on next downward step, clearing row 19
        val afterLock = engine.stepPlayerDown(1)
        assertEquals(1, afterLock.sharedLines)
        assertEquals(100, afterLock.sharedScore)
        assertEquals(1, afterLock.level)
        assertFalse(afterLock.isGameOver)
    }

    @Test
    fun coopMode_endsWhenAnySpawnZoneOverflows() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)

        val grid = MutableList(20) { MutableList<LockedCell?>(10) { null } }
        for (r in 2 until 20) {
            grid[r][2] = LockedCell(1, TetrominoType.O, "classic")
            grid[r][3] = LockedCell(1, TetrominoType.O, "classic")
        }

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 2, y = 0, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 6, y = 4, skinId = "neon_cyber")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.CO_OP,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O)
            )
        )

        val endState = engine.stepPlayerDown(1)
        assertTrue(endState.isGameOver)
        assertNull(endState.winnerPlayerId)
        assertEquals(1, endState.topOutPlayerId)
    }

    @Test
    fun versusMode_sabotagePushAcceleratesOpponentForHalfSecond() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)

        val grid = MutableList(20) { MutableList<LockedCell?>(10) { null } }
        for (c in 0 until 10) {
            if (c != 2 && c != 3) {
                grid[19][c] = LockedCell(1, TetrominoType.I, "classic")
            }
        }

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 2, y = 18, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 6, y = 0, skinId = "neon_cyber")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.VERSUS,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.T, 2 to TetrominoType.T),
                playerScores = mapOf(1 to 0, 2 to 0),
                playerLines = mapOf(1 to 0, 2 to 0)
            )
        )

        val clearTime = 10_000L
        val afterClear = engine.stepPlayerDown(1, currentTimeMillis = clearTime)

        assertEquals(1, afterClear.playerLines[1])
        assertEquals(clearTime + 500L, afterClear.sabotageBurstUntilMillis[2])
        assertTrue(afterClear.isSabotaged(2, clearTime + 250L))
        assertFalse(afterClear.isSabotaged(2, clearTime + 500L))
        assertEquals(MultiTetrisEngine.SABOTAGE_DROP_INTERVAL_MS, engine.getDropIntervalMs(2, clearTime + 100L))
        assertEquals(MultiTetrisEngine.BASE_DROP_INTERVAL_MS, engine.getDropIntervalMs(1, clearTime + 100L))
    }

    @Test
    fun versusMode_overflowingPlayer1SpawnZoneDeclaresPlayer2Winner() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)

        // Stack locked blocks right up to row 1 in Player 1's spawn columns (1..4)
        val grid = MutableList(20) { MutableList<LockedCell?>(10) { null } }
        for (r in 2 until 20) {
            grid[r][2] = LockedCell(1, TetrominoType.O, "classic")
            grid[r][3] = LockedCell(1, TetrominoType.O, "classic")
        }

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 2, y = 0, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 6, y = 4, skinId = "neon_cyber")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.VERSUS,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O),
                playerScores = mapOf(1 to 0, 2 to 0),
                playerLines = mapOf(1 to 0, 2 to 0)
            )
        )

        // Stepping P1 down locks P1 into rows 0..1 of cols 2..3 (inside P1 spawn zone cols 1..4)
        val endState = engine.stepPlayerDown(1)
        assertTrue(endState.isGameOver)
        assertEquals(1, endState.topOutPlayerId)
        assertEquals(2, endState.winnerPlayerId)
        assertNull(endState.activePieces[1])
    }

    @Test
    fun getSpawnColumns_supportsTwoThreeAndFourPlayersDynamically() {
        assertEquals(listOf(2, 6), BoardConfig.getSpawnColumns(playerCount = 2, boardWidth = 10))
        assertEquals(listOf(1, 5, 9), BoardConfig.getSpawnColumns(playerCount = 3, boardWidth = 13))
        assertEquals(listOf(1, 5, 9, 13), BoardConfig.getSpawnColumns(playerCount = 4, boardWidth = 16))

        val threePlayerConfig = BoardConfig.forPlayerCount(3)
        assertEquals(13, threePlayerConfig.width)
        assertEquals(listOf(1, 5, 9), threePlayerConfig.spawnZones.map { it.spawnX })

        val fourPlayerConfig = BoardConfig.forPlayerCount(4)
        assertEquals(16, fourPlayerConfig.width)
        assertEquals(listOf(1, 5, 9, 13), fourPlayerConfig.spawnZones.map { it.spawnX })

        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val started = engine.startNewGame(
            mode = GameMode.CO_OP,
            config = fourPlayerConfig
        )
        assertEquals(4, started.activePieces.size)
        assertEquals(1, started.activePieces[1]?.x)
        assertEquals(5, started.activePieces[2]?.x)
        assertEquals(9, started.activePieces[3]?.x)
        assertEquals(13, started.activePieces[4]?.x)
    }

    @Test
    fun versus2v2_sabotageOnlyTargetsOpposingTeamAndResolvesWinningTeam() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig.forPlayerCount(4) // 16x20
        val teams = mapOf(
            1 to TeamColor.CYAN,
            2 to TeamColor.CYAN,
            3 to TeamColor.MAGENTA,
            4 to TeamColor.MAGENTA
        )

        val grid = MutableList(20) { MutableList<LockedCell?>(16) { null } }
        // Fill row 19 except columns 1..2 so P1 O-piece hard drop clears the row
        for (c in 0 until 16) {
            if (c !in 1..2) {
                grid[19][c] = LockedCell(3, TetrominoType.I, "classic")
            }
        }
        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 1, y = 16, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 5, y = 2, skinId = "neon_cyber")
        val p3 = ActivePiece(3, TetrominoType.O, 0, x = 9, y = 2, skinId = "golden_flex")
        val p4 = ActivePiece(4, TetrominoType.O, 0, x = 13, y = 2, skinId = "obsidian_magma")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.VERSUS,
                versusSubMode = VersusSubMode.TEAMS_2V2,
                playerTeams = teams,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2, 3 to p3, 4 to p4),
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O, 3 to TetrominoType.O, 4 to TetrominoType.O),
                playerScores = mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0),
                playerLines = mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0)
            )
        )

        val now = 10_000L
        val afterClear = engine.applyAction(1, PlayerAction.HARD_DROP, currentTimeMillis = now)
        // Teammate P2 must NOT be sabotaged; opponents P3 & P4 must be sabotaged!
        assertFalse(afterClear.isSabotaged(1, now + 100L))
        assertFalse(afterClear.isSabotaged(2, now + 100L))
        assertTrue(afterClear.isSabotaged(3, now + 100L))
        assertTrue(afterClear.isSabotaged(4, now + 100L))

        // Now stack P3 spawn zone to row 2 so P3 tops out and declares Team Cyan winner
        val topOutGrid = MutableList(20) { MutableList<LockedCell?>(16) { null } }
        for (r in 2 until 20) {
            topOutGrid[r][9] = LockedCell(3, TetrominoType.O, "golden_flex")
            topOutGrid[r][10] = LockedCell(3, TetrominoType.O, "golden_flex")
        }
        engine.setStateForTest(
            afterClear.copy(
                grid = topOutGrid.map { it.toList() },
                activePieces = mapOf(
                    1 to p1.copy(y = 4),
                    2 to p2,
                    3 to p3.copy(y = 0),
                    4 to p4
                )
            )
        )
        val endState = engine.stepPlayerDown(3, currentTimeMillis = now + 200L)
        assertTrue(endState.isGameOver)
        assertEquals(3, endState.topOutPlayerId)
        assertEquals(TeamColor.CYAN, endState.winningTeam)
    }

    @Test
    fun versusMode_overflowingPlayer2SpawnZoneDeclaresPlayer1Winner() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig(width = 10, height = 20, playerCount = 2)

        val grid = MutableList(20) { MutableList<LockedCell?>(10) { null } }
        for (r in 2 until 20) {
            grid[r][6] = LockedCell(2, TetrominoType.O, "neon_cyber")
            grid[r][7] = LockedCell(2, TetrominoType.O, "neon_cyber")
        }

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 2, y = 4, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = 6, y = 0, skinId = "neon_cyber")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.VERSUS,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2),
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O),
                playerScores = mapOf(1 to 0, 2 to 0),
                playerLines = mapOf(1 to 0, 2 to 0)
            )
        )

        val endState = engine.stepPlayerDown(2)
        assertTrue(endState.isGameOver)
        assertEquals(2, endState.topOutPlayerId)
        assertEquals(1, endState.winnerPlayerId)
    }
}
