package com.multitetris.game

import kotlin.math.abs
import kotlin.random.Random

enum class BotDifficulty(
    val stepDelayMs: Long,
    val randomSuboptimalChance: Double
) {
    EASY(stepDelayMs = 420L, randomSuboptimalChance = 0.30),
    NORMAL(stepDelayMs = 210L, randomSuboptimalChance = 0.0),
    HARD(stepDelayMs = 90L, randomSuboptimalChance = 0.0)
}

/**
 * Deterministic step-by-step AI controller for Singleplayer & Multiplayer Bot slots.
 * Evaluates all valid (rotation, targetX) placements on the shared board using
 * Pierre Dellacherie-inspired weights (with 1-piece lookahead on HARD) and emits
 * one human-paced PlayerAction per tick.
 */
class TetrisBotController(
    private val botPlayerId: Int = 2,
    private val difficulty: BotDifficulty = BotDifficulty.NORMAL,
    private val random: Random = Random.Default
) {
    private data class PlannedMove(
        val targetRotation: Int,
        val targetX: Int,
        val score: Int
    )

    private data class PlacementResult(
        val score: Int,
        val clearedGrid: Array<BooleanArray>
    )

    private var currentPlan: PlannedMove? = null
    private var lastPieceType: TetrominoType? = null
    private var lastPieceAliveMs: Long = Long.MAX_VALUE
    private var lastSabotageTriggerMs: Long = 0L
    private var lastPieceX: Int = Int.MIN_VALUE
    private var lastPieceY: Int = Int.MAX_VALUE
    private var lastPieceRot: Int = -1
    private var lastEmittedSteeringAction: PlayerAction? = null
    private var consecutiveStalls: Int = 0

    fun reset() {
        currentPlan = null
        lastPieceType = null
        lastPieceAliveMs = Long.MAX_VALUE
        lastSabotageTriggerMs = 0L
        lastPieceX = Int.MIN_VALUE
        lastPieceY = Int.MAX_VALUE
        lastPieceRot = -1
        lastEmittedSteeringAction = null
        consecutiveStalls = 0
    }

    fun nextAction(state: GameState): PlayerAction? {
        if (state.isPaused || state.isGameOver) return null
        if (state.isPlayerEliminated(botPlayerId) || state.isPlayerBlocked(botPlayerId)) return null
        if (state.activeSabotageFor(botPlayerId)?.type == SabotageType.BUTTON_FREEZE) return null

        val piece = state.activePieces[botPlayerId] ?: return null
        val isDropJammed = state.activeSabotageFor(botPlayerId)?.type == SabotageType.DROP_JAMMER

        // Detect newly spawned piece (alive timer reset, y jumped back up, or tetromino type changed)
        val isNewSpawn = currentPlan == null ||
            piece.tetrominoType != lastPieceType ||
            piece.pieceAliveMs < lastPieceAliveMs ||
            piece.y < lastPieceY

        lastPieceType = piece.tetrominoType
        lastPieceAliveMs = piece.pieceAliveMs

        if (isNewSpawn) {
            currentPlan = computeBestPlan(state, piece)
            lastEmittedSteeringAction = null
            consecutiveStalls = 0
        } else {
            val plan = currentPlan
            val stalled = when (lastEmittedSteeringAction) {
                PlayerAction.ROTATE_CW -> piece.rotation == lastPieceRot
                PlayerAction.MOVE_LEFT, PlayerAction.MOVE_RIGHT -> piece.x == lastPieceX
                PlayerAction.SOFT_DROP -> piece.y == lastPieceY
                else -> false
            }
            if (stalled && plan != null) {
                consecutiveStalls++
                lastPieceX = piece.x
                lastPieceY = piece.y
                lastPieceRot = piece.rotation

                // If rotation was blocked (e.g., near wall/ceiling), try shifting toward targetX first
                if (lastEmittedSteeringAction == PlayerAction.ROTATE_CW && consecutiveStalls <= 2) {
                    if (piece.x < plan.targetX) {
                        lastEmittedSteeringAction = PlayerAction.MOVE_RIGHT
                        return PlayerAction.MOVE_RIGHT
                    }
                    if (piece.x > plan.targetX) {
                        lastEmittedSteeringAction = PlayerAction.MOVE_LEFT
                        return PlayerAction.MOVE_LEFT
                    }
                }
                // If horizontal steering was blocked by another player's piece, try stepping down
                if ((lastEmittedSteeringAction == PlayerAction.MOVE_LEFT ||
                        lastEmittedSteeringAction == PlayerAction.MOVE_RIGHT) &&
                    consecutiveStalls <= 2
                ) {
                    lastEmittedSteeringAction = PlayerAction.SOFT_DROP
                    return PlayerAction.SOFT_DROP
                }
                lastEmittedSteeringAction = null
                return if (isDropJammed) PlayerAction.SOFT_DROP else PlayerAction.HARD_DROP
            } else {
                consecutiveStalls = 0
            }
        }

        lastPieceX = piece.x
        lastPieceY = piece.y
        lastPieceRot = piece.rotation

        val plan = currentPlan ?: return null

        if (piece.rotation != plan.targetRotation) {
            lastEmittedSteeringAction = PlayerAction.ROTATE_CW
            return PlayerAction.ROTATE_CW
        }
        if (piece.x < plan.targetX) {
            lastEmittedSteeringAction = PlayerAction.MOVE_RIGHT
            return PlayerAction.MOVE_RIGHT
        }
        if (piece.x > plan.targetX) {
            lastEmittedSteeringAction = PlayerAction.MOVE_LEFT
            return PlayerAction.MOVE_LEFT
        }

        lastEmittedSteeringAction = null
        return if (isDropJammed) PlayerAction.SOFT_DROP else PlayerAction.HARD_DROP
    }

    /**
     * In Versus + Sabotage Mode, triggers one of the Bot's drafted sabotages against
     * a valid opponent when the board stack rises above row 10.
     */
    fun maybeSelectSabotage(state: GameState, nowMs: Long): Pair<Int, SabotageType>? {
        if (state.isPaused || state.isGameOver) return null
        if (!state.sabotageEnabled || state.mode != GameMode.VERSUS) return null
        if (state.isPlayerEliminated(botPlayerId)) return null
        if (nowMs - lastSabotageTriggerMs < 8_000L) return null

        val targetPlayerId = state.validSabotageTargetsFor(botPlayerId)
            .firstOrNull { targetId ->
                !state.isPlayerEliminated(targetId) && state.activeSabotageFor(targetId) == null
            } ?: return null

        val unused = state.equippedSabotagesFor(botPlayerId)
            .filterNot { it in state.usedSabotagesFor(botPlayerId) }
        if (unused.isEmpty()) return null

        // Check if any locked block is above row 10 (row index < 10, where 0 is top)
        val stackAboveRow10 = (0 until 10.coerceAtMost(state.config.height)).any { r ->
            state.grid[r].any { it != null }
        }
        if (!stackAboveRow10) return null

        lastSabotageTriggerMs = nowMs
        return targetPlayerId to unused.first()
    }

    private fun computeBestPlan(state: GameState, piece: ActivePiece): PlannedMove? {
        val rows = state.config.height
        val cols = state.config.width
        val baseGrid = Array(rows) { r ->
            BooleanArray(cols) { c -> state.grid[r][c] != null }
        }

        val evaluated = ArrayList<Pair<PlannedMove, Array<BooleanArray>>>()

        for (rot in 0..3) {
            for (targetX in -2 until cols + 2) {
                if (!canOccupy(baseGrid, piece, targetX, piece.y, rot, rows, cols)) continue

                var dropY = piece.y
                while (canOccupy(baseGrid, piece, targetX, dropY + 1, rot, rows, cols)) {
                    dropY++
                }

                val result = evaluatePlacement(baseGrid, piece, targetX, dropY, rot, rows, cols)
                evaluated.add(PlannedMove(targetRotation = rot, targetX = targetX, score = result.score) to result.clearedGrid)
            }
        }

        if (evaluated.isEmpty()) return null
        evaluated.sortByDescending { it.first.score }

        val nextType = state.nextPieces[botPlayerId]
        if (difficulty == BotDifficulty.HARD && nextType != null) {
            val topK = evaluated.take(6)
            val lookaheadPiece = ActivePiece(
                playerId = botPlayerId,
                tetrominoType = nextType,
                x = piece.x,
                y = 0,
                rotation = 0,
                skinId = piece.skinId
            )
            var bestMove = topK.first().first
            var bestCombinedScore = Int.MIN_VALUE

            for ((move, afterGrid) in topK) {
                val nextBest = bestPlacementScore(afterGrid, lookaheadPiece, rows, cols)
                val combined = if (nextBest != Int.MIN_VALUE) move.score + nextBest else move.score - 500
                if (combined > bestCombinedScore) {
                    bestCombinedScore = combined
                    bestMove = move.copy(score = combined)
                }
            }
            return bestMove
        }

        val candidates = evaluated.map { it.first }
        if (difficulty.randomSuboptimalChance > 0.0 &&
            candidates.size > 1 &&
            random.nextDouble() < difficulty.randomSuboptimalChance
        ) {
            val poolSize = candidates.size.coerceAtMost(4)
            return candidates[random.nextInt(1, poolSize)]
        }
        return candidates.first()
    }

    private fun bestPlacementScore(
        grid: Array<BooleanArray>,
        piece: ActivePiece,
        rows: Int,
        cols: Int
    ): Int {
        var best = Int.MIN_VALUE
        for (rot in 0..3) {
            for (targetX in -2 until cols + 2) {
                if (!canOccupy(grid, piece, targetX, 0, rot, rows, cols)) continue
                var dropY = 0
                while (canOccupy(grid, piece, targetX, dropY + 1, rot, rows, cols)) {
                    dropY++
                }
                val score = evaluatePlacement(grid, piece, targetX, dropY, rot, rows, cols).score
                if (score > best) {
                    best = score
                }
            }
        }
        return best
    }

    private fun canOccupy(
        grid: Array<BooleanArray>,
        piece: ActivePiece,
        x: Int,
        y: Int,
        rot: Int,
        rows: Int,
        cols: Int
    ): Boolean {
        return piece.occupiedCells(atX = x, atY = y, atRotation = rot).all { cell ->
            cell.x in 0 until cols && cell.y in 0 until rows && !grid[cell.y][cell.x]
        }
    }

    private fun evaluatePlacement(
        baseGrid: Array<BooleanArray>,
        piece: ActivePiece,
        x: Int,
        y: Int,
        rot: Int,
        rows: Int,
        cols: Int
    ): PlacementResult {
        val sim = Array(rows) { r -> baseGrid[r].clone() }
        for (cell in piece.occupiedCells(atX = x, atY = y, atRotation = rot)) {
            if (cell.y in 0 until rows && cell.x in 0 until cols) {
                sim[cell.y][cell.x] = true
            }
        }

        // Count and remove cleared lines
        var linesCleared = 0
        val remainingRows = ArrayList<BooleanArray>(rows)
        for (r in 0 until rows) {
            if (sim[r].all { it }) {
                linesCleared++
            } else {
                remainingRows.add(sim[r])
            }
        }
        val clearedGrid = Array(rows) { r ->
            if (r < linesCleared) BooleanArray(cols) else remainingRows[r - linesCleared]
        }

        val heights = IntArray(cols)
        var holes = 0
        for (c in 0 until cols) {
            var seenBlock = false
            for (r in 0 until rows) {
                if (clearedGrid[r][c]) {
                    if (!seenBlock) {
                        heights[c] = rows - r
                        seenBlock = true
                    }
                } else if (seenBlock) {
                    holes++
                }
            }
        }

        val aggregateHeight = heights.sum()
        var bumpiness = 0
        for (c in 0 until cols - 1) {
            bumpiness += abs(heights[c] - heights[c + 1])
        }
        val maxHeight = heights.maxOrNull() ?: 0
        val dangerPenalty = if (maxHeight > 14) (maxHeight - 14) * 15 else 0

        val lineReward = when (linesCleared) {
            1 -> 120
            2 -> 300
            3 -> 600
            4 -> 1200
            else -> if (linesCleared > 4) linesCleared * 300 else 0
        }

        val score = lineReward -
            (holes * 35) -
            (aggregateHeight * 5) -
            (bumpiness * 4) -
            dangerPenalty

        return PlacementResult(score = score, clearedGrid = clearedGrid)
    }
}
