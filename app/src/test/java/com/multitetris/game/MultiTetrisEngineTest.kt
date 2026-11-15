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
    fun coopMode_endsOnlyWhenAllSpawnZonesAreBlocked() {
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

        val p1BlockedState = engine.stepPlayerDown(1)
        assertFalse(p1BlockedState.isGameOver)
        assertTrue(1 in p1BlockedState.blockedPlayerIds)

        // Now block P2 as well -> Co-op ends!
        val gridBothBlocked = p1BlockedState.grid.map { it.toMutableList() }.toMutableList()
        for (r in 2 until 20) {
            gridBothBlocked[r][6] = LockedCell(2, TetrominoType.O, "neon_cyber")
            gridBothBlocked[r][7] = LockedCell(2, TetrominoType.O, "neon_cyber")
        }
        engine.setStateForTest(
            p1BlockedState.copy(
                grid = gridBothBlocked.map { it.toList() },
                activePieces = mapOf(2 to p2.copy(y = 0))
            )
        )

        val endState = engine.stepPlayerDown(2)
        assertTrue(endState.isGameOver)
        assertNull(endState.winnerPlayerId)
        assertEquals(2, endState.topOutPlayerId)
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
        val midState = engine.stepPlayerDown(3, currentTimeMillis = now + 200L)
        assertFalse(midState.isGameOver)
        assertTrue(3 in midState.eliminatedPlayerIds)
        assertEquals(4, midState.placementFor(3))
        assertNull(midState.activePieces[3])
        assertNotNull(midState.activePieces[4])

        // Now eliminate P4 (the last remaining Magenta player) -> Team Cyan wins!
        val gridWithP4Overflow = midState.grid.map { it.toMutableList() }.toMutableList()
        val p4Zone = config.spawnZoneFor(4)
        for (r in 2 until 20) {
            gridWithP4Overflow[r][p4Zone.columnRange.first] = LockedCell(4, TetrominoType.O, "golden_flex")
            gridWithP4Overflow[r][p4Zone.columnRange.first + 1] = LockedCell(4, TetrominoType.O, "golden_flex")
        }
        engine.setStateForTest(
            midState.copy(
                grid = gridWithP4Overflow.map { it.toList() },
                activePieces = midState.activePieces.toMutableMap().apply {
                    put(4, p4.copy(y = 0))
                }
            )
        )
        val endState = engine.stepPlayerDown(4, currentTimeMillis = now + 400L)
        assertTrue(endState.isGameOver)
        assertEquals(4, endState.topOutPlayerId)
        assertEquals(TeamColor.CYAN, endState.winningTeam)
        assertEquals(1, endState.placementFor(1))
        assertEquals(1, endState.placementFor(2))
    }

    @Test
    fun versusFfa3P_eliminatesPlayersSequentiallyUntilLastPlayerStands() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig.forPlayerCount(3)
        val grid = MutableList(20) { MutableList<LockedCell?>(config.width) { null } }
        val p2Zone = config.spawnZoneFor(2)
        for (r in 2 until 20) {
            grid[r][p2Zone.columnRange.first] = LockedCell(2, TetrominoType.O, "neon_cyber")
            grid[r][p2Zone.columnRange.first + 1] = LockedCell(2, TetrominoType.O, "neon_cyber")
        }

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = config.spawnZoneFor(1).spawnX, y = 4, skinId = "classic")
        val p2 = ActivePiece(2, TetrominoType.O, 0, x = p2Zone.spawnX, y = 0, skinId = "neon_cyber")
        val p3 = ActivePiece(3, TetrominoType.O, 0, x = config.spawnZoneFor(3).spawnX, y = 4, skinId = "pixel_crystal")

        engine.setStateForTest(
            GameState(
                config = config,
                mode = GameMode.VERSUS,
                versusSubMode = VersusSubMode.FREE_FOR_ALL,
                grid = grid.map { it.toList() },
                activePieces = mapOf(1 to p1, 2 to p2, 3 to p3),
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O, 3 to TetrominoType.O),
                playerScores = mapOf(1 to 100, 2 to 50, 3 to 80),
                playerLines = mapOf(1 to 1, 2 to 0, 3 to 1)
            )
        )

        val afterP2Out = engine.stepPlayerDown(2)
        assertFalse(afterP2Out.isGameOver)
        assertTrue(2 in afterP2Out.eliminatedPlayerIds)
        assertEquals(3, afterP2Out.placementFor(2))
        assertNull(afterP2Out.activePieces[2])

        // Eliminate P3 -> P1 is the last survivor and wins with 1st place
        val p3Zone = config.spawnZoneFor(3)
        val grid2 = afterP2Out.grid.map { it.toMutableList() }.toMutableList()
        for (r in 2 until 20) {
            grid2[r][p3Zone.columnRange.first] = LockedCell(3, TetrominoType.O, "pixel_crystal")
            grid2[r][p3Zone.columnRange.first + 1] = LockedCell(3, TetrominoType.O, "pixel_crystal")
        }
        engine.setStateForTest(
            afterP2Out.copy(
                grid = grid2.map { it.toList() },
                activePieces = afterP2Out.activePieces.toMutableMap().apply {
                    put(3, p3.copy(y = 0))
                }
            )
        )
        val endState = engine.stepPlayerDown(3)
        assertTrue(endState.isGameOver)
        assertEquals(1, endState.winnerPlayerId)
        assertEquals(1, endState.placementFor(1))
        assertEquals(2, endState.placementFor(3))
        assertEquals(3, endState.placementFor(2))
    }

    @Test
    fun coopMode_blockedPlayerRespawnsWhenTeammateClearsLineAndEndsOnlyWhenAllBlocked() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val config = BoardConfig.forPlayerCount(2)
        val grid = MutableList(20) { MutableList<LockedCell?>(config.width) { null } }
        // Fill P1's spawn column so P1 gets blocked on lock
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
                nextPieces = mapOf(1 to TetrominoType.O, 2 to TetrominoType.O),
                playerScores = mapOf(1 to 0, 2 to 0),
                playerLines = mapOf(1 to 0, 2 to 0)
            )
        )

        val blockedState = engine.stepPlayerDown(1)
        assertFalse(blockedState.isGameOver)
        assertTrue(1 in blockedState.blockedPlayerIds)
        assertNull(blockedState.activePieces[1])
        assertNotNull(blockedState.activePieces[2])

        // Prepare a board where P2 clears row 19 and row 0..1 of P1's spawn zone is now free
        val respawnGrid = MutableList(20) { MutableList<LockedCell?>(config.width) { null } }
        for (col in 0 until config.width) {
            if (col !in 6..7) {
                respawnGrid[19][col] = LockedCell(2, TetrominoType.I, "neon_cyber")
            }
        }
        engine.setStateForTest(
            blockedState.copy(
                grid = respawnGrid.map { it.toList() },
                activePieces = mapOf(2 to p2.copy(x = 6, y = 18))
            )
        )

        val respawnedState = engine.stepPlayerDown(2)
        assertFalse(respawnedState.isGameOver)
        assertFalse(1 in respawnedState.blockedPlayerIds)
        assertNotNull(respawnedState.activePieces[1])
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

    @Test
    fun versusFfa3P_guestDisconnectEliminatesPlayerAndEndsWhenLastOpponentLeaves() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        engine.startNewGame(
            mode = GameMode.VERSUS,
            config = BoardConfig.forPlayerCount(3),
            versusSubMode = VersusSubMode.FREE_FOR_ALL
        )

        val afterP2Left = engine.markPlayerDisconnected(2, "GuestTwo")
        assertFalse(afterP2Left.isGameOver)
        assertTrue(2 in afterP2Left.disconnectedPlayerIds)
        assertTrue(2 in afterP2Left.eliminatedPlayerIds)
        assertEquals(3, afterP2Left.placementFor(2))
        assertNull(afterP2Left.activePieces[2])
        assertEquals("GuestTwo left the match", afterP2Left.feedToastMessage)

        val afterP3Left = engine.markPlayerDisconnected(3, "GuestThree")
        assertTrue(afterP3Left.isGameOver)
        assertTrue(3 in afterP3Left.disconnectedPlayerIds)
        assertEquals(1, afterP3Left.winnerPlayerId)
        assertEquals(1, afterP3Left.placementFor(1))
    }

    @Test
    fun mysteryMode_pieceAliveMsAdvancesAndHidesAfterOneSecondAndResetsOnNextSpawn() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        val started = engine.startNewGame(
            mode = GameMode.CO_OP,
            config = BoardConfig.forPlayerCount(2),
            mysteryEnabled = true
        )
        assertTrue(started.mysteryEnabled)
        val initialP1 = started.activePieces[1]!!
        assertEquals(0L, initialP1.pieceAliveMs)
        assertFalse(started.isPieceHiddenByMystery(initialP1))

        val halfSecond = engine.tickMysteryPieceTimers(deltaMs = 600L)
        val halfP1 = halfSecond.activePieces[1]!!
        assertEquals(600L, halfP1.pieceAliveMs)
        assertFalse(halfSecond.isPieceHiddenByMystery(halfP1))

        val fullSecond = engine.tickMysteryPieceTimers(deltaMs = 500L)
        val hiddenP1 = fullSecond.activePieces[1]!!
        assertEquals(GameState.MYSTERY_VISIBLE_WINDOW_MS, hiddenP1.pieceAliveMs)
        assertTrue(fullSecond.isPieceHiddenByMystery(hiddenP1))

        // Hard drop locks current piece and spawns a fresh piece with pieceAliveMs = 0L
        val afterDrop = engine.applyAction(1, PlayerAction.HARD_DROP)
        val nextP1 = afterDrop.activePieces[1]!!
        assertEquals(0L, nextP1.pieceAliveMs)
        assertFalse(afterDrop.isPieceHiddenByMystery(nextP1))
    }

    @Test
    fun markPlayerDisconnected_duringGameOver_recordsDisconnectedPlayerWithoutClearingWinner() {
        val engine = MultiTetrisEngine(pieceBagOverride = { TetrominoType.O })
        engine.setStateForTest(
            GameState(
                config = BoardConfig.forPlayerCount(3),
                mode = GameMode.VERSUS,
                isGameOver = true,
                winnerPlayerId = 1,
                playerPlacements = mapOf(1 to 1, 2 to 2, 3 to 3)
            )
        )

        val updated = engine.markPlayerDisconnected(2, "GuestTwo")
        assertTrue(updated.isGameOver)
        assertEquals(1, updated.winnerPlayerId)
        assertEquals(mapOf(1 to 1, 2 to 2, 3 to 3), updated.playerPlacements)
        assertTrue(2 in updated.disconnectedPlayerIds)
        assertEquals("GuestTwo left the match", updated.feedToastMessage)
    }
}
