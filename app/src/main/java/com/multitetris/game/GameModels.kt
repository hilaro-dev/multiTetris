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

enum class VersusSubMode(val label: String) {
    FREE_FOR_ALL("Free-For-All"),
    TEAMS_2V2("2v2 Teams")
}

enum class TeamColor(val label: String) {
    NONE("Solo"),
    CYAN("Team Cyan"),
    MAGENTA("Team Magenta")
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
            return when {
                playerCount <= 2 -> 10
                playerCount == 3 -> 13
                else -> 16
            }
        }

        /**
         * Computes horizontal spawn columns dynamically:
         * - 2 players on width 10 -> [2, 6]
         * - 3 players on width 13 (or 12) -> [1, 5, 9]
         * - 4 players on width 16 -> [1, 5, 9, 13]
         * - N players -> evenly spaced across zones of width (boardWidth / playerCount)
         */
        fun getSpawnColumns(playerCount: Int, boardWidth: Int): List<Int> {
            require(playerCount >= 1) { "Player count must be at least 1" }
            require(boardWidth >= 4) { "Board width must be at least 4" }

            if (playerCount == 2 && boardWidth == 10) {
                return listOf(2, 6)
            }
            if (playerCount == 3 && (boardWidth == 13 || boardWidth == 12)) {
                return listOf(1, 5, 9)
            }
            if (playerCount == 4 && boardWidth == 16) {
                return listOf(1, 5, 9, 13)
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
            if (playerCount == 3 && (width == 13 || width == 12)) {
                return listOf(
                    SpawnZone(playerId = 1, spawnX = 1, spawnY = 0, columnRange = 0..3),
                    SpawnZone(playerId = 2, spawnX = 5, spawnY = 0, columnRange = 4..7),
                    SpawnZone(playerId = 3, spawnX = 9, spawnY = 0, columnRange = 8..(width - 1))
                )
            }
            if (playerCount == 4 && width == 16) {
                return listOf(
                    SpawnZone(playerId = 1, spawnX = 1, spawnY = 0, columnRange = 0..3),
                    SpawnZone(playerId = 2, spawnX = 5, spawnY = 0, columnRange = 4..7),
                    SpawnZone(playerId = 3, spawnX = 9, spawnY = 0, columnRange = 8..11),
                    SpawnZone(playerId = 4, spawnX = 13, spawnY = 0, columnRange = 12..15)
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
    val versusSubMode: VersusSubMode = VersusSubMode.FREE_FOR_ALL,
    val playerTeams: Map<Int, TeamColor> = emptyMap(),
    val grid: List<List<LockedCell?>> = List(config.height) { List(config.width) { null } },
    val activePieces: Map<Int, ActivePiece> = emptyMap(),
    val ghostPieces: Map<Int, ActivePiece> = emptyMap(),
    val nextPieces: Map<Int, TetrominoType> = emptyMap(),
    val playerSkins: Map<Int, String> = defaultPlayerSkins(config.playerCount),
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
    val winningTeam: TeamColor? = null,
    val topOutPlayerId: Int? = null,
    val eliminatedPlayerIds: Set<Int> = emptySet(),
    val playerPlacements: Map<Int, Int> = emptyMap(),
    val blockedPlayerIds: Set<Int> = emptySet(),
    val disconnectedPlayerIds: Set<Int> = emptySet(),
    val feedToastMessage: String? = null,
    val feedToastEventId: Long = 0L,
    val statusMessage: String? = null,
    val lastClearedRowIndices: List<Int> = emptyList(),
    val lineClearEventId: Long = 0L
) {
    fun isPlayerEliminated(playerId: Int): Boolean = playerId in eliminatedPlayerIds

    fun isPlayerBlocked(playerId: Int): Boolean = playerId in blockedPlayerIds

    fun isPlayerDisconnected(playerId: Int): Boolean = playerId in disconnectedPlayerIds

    fun placementFor(playerId: Int): Int = playerPlacements[playerId] ?: 0

    fun isSabotaged(playerId: Int, currentTimeMillis: Long): Boolean {
        val until = sabotageBurstUntilMillis[playerId] ?: 0L
        return mode == GameMode.VERSUS && currentTimeMillis < until
    }

    fun teamScore(team: TeamColor): Int {
        return playerScores.entries.sumOf { (pid, score) ->
            if (playerTeams[pid] == team) score else 0
        }
    }

    fun teamLines(team: TeamColor): Int {
        return playerLines.entries.sumOf { (pid, lines) ->
            if (playerTeams[pid] == team) lines else 0
        }
    }

    companion object {
        private val DEFAULT_PLAYER_SKINS = listOf(
            "classic",
            "neon_cyber",
            "golden_flex",
            "obsidian_magma"
        )

        fun defaultSkinForPlayer(playerId: Int): String {
            return DEFAULT_PLAYER_SKINS[(playerId - 1).coerceAtLeast(0) % DEFAULT_PLAYER_SKINS.size]
        }

        fun defaultPlayerSkins(playerCount: Int): Map<Int, String> {
            return (1..playerCount.coerceAtLeast(1)).associateWith { pid ->
                defaultSkinForPlayer(pid)
            }
        }
    }
}
