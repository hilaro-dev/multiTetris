package com.multitetris.game

/**
 * Grid coordinate on the MultiTetris board where (0, 0) is the top-left cell.
 */
data class GridPos(
    val x: Int,
    val y: Int
)

/**
 * Standard 7 Tetromino shapes with 4 clockwise rotation states (0..3).
 * Offsets are defined so that at rotation = 0 and x = spawnX, all pieces
 * fit cleanly inside a 4-column spawn zone centered around x (e.g., cols 1..4 for x = 2).
 */
enum class TetrominoType(
    private val rotationOffsets: List<List<GridPos>>
) {
    I(
        listOf(
            listOf(GridPos(-1, 0), GridPos(0, 0), GridPos(1, 0), GridPos(2, 0)),
            listOf(GridPos(1, -1), GridPos(1, 0), GridPos(1, 1), GridPos(1, 2)),
            listOf(GridPos(-1, 1), GridPos(0, 1), GridPos(1, 1), GridPos(2, 1)),
            listOf(GridPos(0, -1), GridPos(0, 0), GridPos(0, 1), GridPos(0, 2))
        )
    ),
    O(
        List(4) {
            listOf(GridPos(0, 0), GridPos(1, 0), GridPos(0, 1), GridPos(1, 1))
        }
    ),
    T(
        listOf(
            listOf(GridPos(0, 0), GridPos(-1, 1), GridPos(0, 1), GridPos(1, 1)),
            listOf(GridPos(1, 1), GridPos(0, 0), GridPos(0, 1), GridPos(0, 2)),
            listOf(GridPos(0, 2), GridPos(1, 1), GridPos(0, 1), GridPos(-1, 1)),
            listOf(GridPos(-1, 1), GridPos(0, 2), GridPos(0, 1), GridPos(0, 0))
        )
    ),
    S(
        listOf(
            listOf(GridPos(0, 0), GridPos(1, 0), GridPos(-1, 1), GridPos(0, 1)),
            listOf(GridPos(1, 1), GridPos(1, 2), GridPos(0, 0), GridPos(0, 1)),
            listOf(GridPos(0, 2), GridPos(-1, 2), GridPos(1, 1), GridPos(0, 1)),
            listOf(GridPos(-1, 1), GridPos(-1, 0), GridPos(0, 2), GridPos(0, 1))
        )
    ),
    Z(
        listOf(
            listOf(GridPos(-1, 0), GridPos(0, 0), GridPos(0, 1), GridPos(1, 1)),
            listOf(GridPos(1, 0), GridPos(1, 1), GridPos(0, 1), GridPos(0, 2)),
            listOf(GridPos(1, 2), GridPos(0, 2), GridPos(0, 1), GridPos(-1, 1)),
            listOf(GridPos(-1, 2), GridPos(-1, 1), GridPos(0, 1), GridPos(0, 0))
        )
    ),
    J(
        listOf(
            listOf(GridPos(-1, 0), GridPos(-1, 1), GridPos(0, 1), GridPos(1, 1)),
            listOf(GridPos(1, 0), GridPos(0, 0), GridPos(0, 1), GridPos(0, 2)),
            listOf(GridPos(1, 2), GridPos(1, 1), GridPos(0, 1), GridPos(-1, 1)),
            listOf(GridPos(-1, 2), GridPos(0, 2), GridPos(0, 1), GridPos(0, 0))
        )
    ),
    L(
        listOf(
            listOf(GridPos(1, 0), GridPos(-1, 1), GridPos(0, 1), GridPos(1, 1)),
            listOf(GridPos(1, 2), GridPos(0, 0), GridPos(0, 1), GridPos(0, 2)),
            listOf(GridPos(-1, 2), GridPos(1, 1), GridPos(0, 1), GridPos(-1, 1)),
            listOf(GridPos(-1, 0), GridPos(0, 2), GridPos(0, 1), GridPos(0, 0))
        )
    );

    fun offsetsFor(rotation: Int): List<GridPos> {
        val normalized = ((rotation % 4) + 4) % 4
        return rotationOffsets[normalized]
    }
}

enum class GameMode(val label: String) {
    CO_OP("CO-OP"),
    VERSUS("VERSUS")
}

enum class PlayerAction {
    MOVE_LEFT,
    MOVE_RIGHT,
    ROTATE_CW,
    SOFT_DROP,
    HARD_DROP
}

/**
 * Represents a currently falling Tetromino controlled by a specific player.
 */
data class ActivePiece(
    val playerId: Int,
    val tetrominoType: TetrominoType,
    val rotation: Int,
    val x: Int,
    val y: Int,
    val skinId: String,
    val isPausedByPiece: Boolean = false
) {
    fun occupiedCells(
        atX: Int = x,
        atY: Int = y,
        atRotation: Int = rotation
    ): List<GridPos> {
        return tetrominoType.offsetsFor(atRotation).map { offset ->
            GridPos(atX + offset.x, atY + offset.y)
        }
    }
}

/**
 * A single locked cell on the shared board, preserving the owner player and skinId.
 */
data class LockedCell(
    val playerId: Int,
    val tetrominoType: TetrominoType,
    val skinId: String
)

/**
 * Defines a player's drop column and overflow zone for top-out detection.
 */
data class SpawnZone(
    val playerId: Int,
    val spawnX: Int,
    val spawnY: Int = 0,
    val columnRange: IntRange
)

/**
 * Dynamic board configuration parameterized by playerCount so Stage 1 defaults to 2 players
 * on a 10x20 grid while allowing 3+ drop points (e.g., 12 columns for 3 players at 1, 5, 9).
 */
data class BoardConfig(
    val width: Int = 10,
    val height: Int = 20,
    val playerCount: Int = 2
) {
    init {
        require(width >= 4) { "Board width must be at least 4" }
        require(height >= 4) { "Board height must be at least 4" }
        require(playerCount >= 1) { "Player count must be at least 1" }
    }

    val spawnZones: List<SpawnZone> = buildSpawnZones(width, playerCount)

    fun spawnZoneFor(playerId: Int): SpawnZone {
        return spawnZones.first { it.playerId == playerId }
    }

    companion object {
        fun forPlayerCount(playerCount: Int, height: Int = 20): BoardConfig {
            return BoardConfig(
                width = recommendedBoardWidth(playerCount),
                height = height,
                playerCount = playerCount
            )
        }

        fun recommendedBoardWidth(playerCount: Int): Int {
            return if (playerCount <= 2) 10 else playerCount * 4
        }

        /**
         * Computes horizontal spawn columns dynamically:
         * - 2 players on width 10 -> [2, 6]
         * - 3 players on width 12 -> [1, 5, 9]
         * - N players -> evenly spaced across zones of width (boardWidth / playerCount)
         */
        fun getSpawnColumns(playerCount: Int, boardWidth: Int): List<Int> {
            require(playerCount >= 1) { "Player count must be at least 1" }
            require(boardWidth >= 4) { "Board width must be at least 4" }

            if (playerCount == 2 && boardWidth == 10) {
                return listOf(2, 6)
            }

            val zoneWidth = (boardWidth / playerCount).coerceAtLeast(1)
            return (1..playerCount).map { index ->
                val startCol = ((index - 1) * zoneWidth).coerceIn(0, boardWidth - 1)
                val endCol = if (index == playerCount) {
                    boardWidth - 1
                } else {
                    (startCol + zoneWidth - 1).coerceIn(startCol, boardWidth - 1)
                }
                val candidate = if ((endCol - startCol + 1) >= 4) {
                    startCol + 1
                } else {
                    (startCol + endCol) / 2
                }
                candidate.coerceIn(1, (boardWidth - 2).coerceAtLeast(1))
            }
        }

        private fun buildSpawnZones(width: Int, playerCount: Int): List<SpawnZone> {
            if (playerCount == 2 && width == 10) {
                val cols = getSpawnColumns(playerCount, width)
                return listOf(
                    SpawnZone(playerId = 1, spawnX = cols[0], spawnY = 0, columnRange = 1..4),
                    SpawnZone(playerId = 2, spawnX = cols[1], spawnY = 0, columnRange = 5..8)
                )
            }

            val spawnCols = getSpawnColumns(playerCount, width)
            val zoneWidth = (width / playerCount).coerceAtLeast(1)
            return (1..playerCount).map { index ->
                val startCol = ((index - 1) * zoneWidth).coerceIn(0, width - 1)
                val endCol = if (index == playerCount) {
                    width - 1
                } else {
                    (startCol + zoneWidth - 1).coerceIn(startCol, width - 1)
                }
                SpawnZone(
                    playerId = index,
                    spawnX = spawnCols[index - 1],
                    spawnY = 0,
                    columnRange = startCol..endCol
                )
            }
        }
    }
}

data class SabotageEvent(
    val attackerPlayerId: Int,
    val targetPlayerId: Int,
    val linesCleared: Int,
    val expiresAtMillis: Long
)

data class GameState(
    val config: BoardConfig = BoardConfig(),
    val mode: GameMode = GameMode.CO_OP,
    val grid: List<List<LockedCell?>> = List(config.height) { List(config.width) { null } },
    val activePieces: Map<Int, ActivePiece> = emptyMap(),
    val ghostPieces: Map<Int, ActivePiece> = emptyMap(),
    val nextPieces: Map<Int, TetrominoType> = emptyMap(),
    val playerSkins: Map<Int, String> = mapOf(1 to "classic", 2 to "neon_cyber"),
    val sharedScore: Int = 0,
    val sharedLines: Int = 0,
    val level: Int = 1,
    val playerScores: Map<Int, Int> = emptyMap(),
    val playerLines: Map<Int, Int> = emptyMap(),
    val sabotageBurstUntilMillis: Map<Int, Long> = emptyMap(),
    val lastSabotageEvent: SabotageEvent? = null,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val winnerPlayerId: Int? = null,
    val topOutPlayerId: Int? = null,
    val statusMessage: String? = null,
    val lastClearedRowIndices: List<Int> = emptyList(),
    val lineClearEventId: Long = 0L
) {
    fun isSabotaged(playerId: Int, currentTimeMillis: Long): Boolean {
        val until = sabotageBurstUntilMillis[playerId] ?: 0L
        return mode == GameMode.VERSUS && currentTimeMillis < until
    }
}
