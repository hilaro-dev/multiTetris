package com.multitetris.game

import kotlin.math.max
import kotlin.random.Random

/**
 * Result of checking whether a piece can occupy a target board position and rotation.
 */
enum class CollisionResult {
    NONE,
    OUT_OF_BOUNDS_OR_LOCKED,
    ACTIVE_PIECE
}

/**
 * Core MultiTetris dual-drop (and N-player extensible) engine.
 *
 * Pure game logic with zero Android/Compose dependencies so it can be unit-tested
 * deterministically and shared across local simulation or networked host authority.
 */
class MultiTetrisEngine(
    private val random: Random = Random.Default,
    private val pieceBagOverride: ((playerId: Int) -> TetrominoType)? = null
) {
    private val playerBags = mutableMapOf<Int, MutableList<TetrominoType>>()

    var state: GameState = GameState()
        private set

    fun startNewGame(
        mode: GameMode,
        config: BoardConfig = BoardConfig(),
        playerSkins: Map<Int, String> = GameState.defaultPlayerSkins(config.playerCount),
        currentTimeMillis: Long = 0L,
        versusSubMode: VersusSubMode = VersusSubMode.FREE_FOR_ALL,
        playerTeams: Map<Int, TeamColor> = emptyMap()
    ): GameState {
        playerBags.clear()
        val emptyGrid = List(config.height) { List<LockedCell?>(config.width) { null } }
        val activePieces = mutableMapOf<Int, ActivePiece>()
        val nextPieces = mutableMapOf<Int, TetrominoType>()
        val scores = mutableMapOf<Int, Int>()
        val lines = mutableMapOf<Int, Int>()

        val resolvedTeams: Map<Int, TeamColor> = if (mode == GameMode.VERSUS && versusSubMode == VersusSubMode.TEAMS_2V2) {
            if (playerTeams.isNotEmpty()) {
                config.spawnZones.associate { zone ->
                    zone.playerId to (playerTeams[zone.playerId] ?: if (zone.playerId <= 2) TeamColor.CYAN else TeamColor.MAGENTA)
                }
            } else {
                config.spawnZones.associate { zone ->
                    zone.playerId to if (zone.playerId <= 2) TeamColor.CYAN else TeamColor.MAGENTA
                }
            }
        } else {
            config.spawnZones.associate { it.playerId to TeamColor.NONE }
        }

        val resolvedSkins = config.spawnZones.associate { zone ->
            zone.playerId to (playerSkins[zone.playerId] ?: GameState.defaultSkinForPlayer(zone.playerId))
        }

        for (zone in config.spawnZones) {
            val playerId = zone.playerId
            val firstType = drawFromBag(playerId)
            val nextType = drawFromBag(playerId)
            val skinId = resolvedSkins[playerId] ?: GameState.defaultSkinForPlayer(playerId)
            activePieces[playerId] = ActivePiece(
                playerId = playerId,
                tetrominoType = firstType,
                rotation = 0,
                x = zone.spawnX,
                y = zone.spawnY,
                skinId = skinId
            )
            nextPieces[playerId] = nextType
            scores[playerId] = 0
            lines[playerId] = 0
        }

        val initialState = GameState(
            config = config,
            mode = mode,
            versusSubMode = if (mode == GameMode.VERSUS) versusSubMode else VersusSubMode.FREE_FOR_ALL,
            playerTeams = resolvedTeams,
            grid = emptyGrid,
            activePieces = activePieces,
            nextPieces = nextPieces,
            playerSkins = resolvedSkins,
            sharedScore = 0,
            sharedLines = 0,
            level = 1,
            playerScores = scores,
            playerLines = lines,
            sabotageBurstUntilMillis = emptyMap(),
            lastSabotageEvent = null,
            isPaused = false,
            isGameOver = false,
            winnerPlayerId = null,
            winningTeam = null,
            topOutPlayerId = null,
            statusMessage = null
        )

        state = refreshPauseFlagsAndGhosts(initialState)
        return state
    }

    /**
     * Allows deterministic unit tests to inject a specific board and piece layout.
     */
    internal fun setStateForTest(testState: GameState): GameState {
        state = refreshPauseFlagsAndGhosts(testState)
        return state
    }

    fun setPaused(paused: Boolean): GameState {
        if (state.isGameOver) return state
        state = state.copy(isPaused = paused)
        return state
    }

    fun updatePlayerSkin(playerId: Int, skinId: String): GameState {
        val updatedSkins = state.playerSkins.toMutableMap().apply { put(playerId, skinId) }
        val updatedActive = state.activePieces.mapValues { (id, piece) ->
            if (id == playerId) piece.copy(skinId = skinId) else piece
        }
        state = refreshPauseFlagsAndGhosts(
            state.copy(
                playerSkins = updatedSkins,
                activePieces = updatedActive
            )
        )
        return state
    }

    fun getDropIntervalMs(playerId: Int, currentTimeMillis: Long): Long {
        val baseInterval = max(MIN_DROP_INTERVAL_MS, BASE_DROP_INTERVAL_MS - (state.level - 1) * LEVEL_SPEED_STEP_MS)
        return if (state.isSabotaged(playerId, currentTimeMillis)) {
            SABOTAGE_DROP_INTERVAL_MS
        } else {
            baseInterval
        }
    }

    fun applyAction(
        playerId: Int,
        action: PlayerAction,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): GameState {
        if (state.isPaused || state.isGameOver) return state
        val piece = state.activePieces[playerId] ?: return state

        return when (action) {
            PlayerAction.MOVE_LEFT -> tryMoveHorizontal(piece, dx = -1)
            PlayerAction.MOVE_RIGHT -> tryMoveHorizontal(piece, dx = 1)
            PlayerAction.ROTATE_CW -> tryRotateClockwise(piece)
            PlayerAction.SOFT_DROP -> stepPlayerDown(playerId, currentTimeMillis, awardSoftDropPoint = true)
            PlayerAction.HARD_DROP -> hardDropPlayer(playerId, currentTimeMillis)
        }
    }

    fun stepPlayerDown(
        playerId: Int,
        currentTimeMillis: Long = System.currentTimeMillis(),
        awardSoftDropPoint: Boolean = false
    ): GameState {
        if (state.isPaused || state.isGameOver) return state
        val piece = state.activePieces[playerId] ?: return state

        val collision = canOccupy(
            grid = state.grid,
            activePieces = state.activePieces,
            config = state.config,
            piece = piece,
            candidateX = piece.x,
            candidateY = piece.y + 1,
            candidateRotation = piece.rotation
        )

        return when (collision) {
            CollisionResult.NONE -> {
                val movedPiece = piece.copy(y = piece.y + 1, isPausedByPiece = false)
                val updatedActive = state.activePieces.toMutableMap().apply {
                    put(playerId, movedPiece)
                }
                var updatedState = state.copy(activePieces = updatedActive)
                if (awardSoftDropPoint) {
                    updatedState = addDropBonus(updatedState, playerId, 1)
                }
                state = refreshPauseFlagsAndGhosts(updatedState)
                state
            }

            CollisionResult.ACTIVE_PIECE -> {
                // Pause downward movement on top of the falling piece below; do NOT lock into board.
                val pausedPiece = piece.copy(isPausedByPiece = true)
                val updatedActive = state.activePieces.toMutableMap().apply {
                    put(playerId, pausedPiece)
                }
                state = refreshPauseFlagsAndGhosts(state.copy(activePieces = updatedActive))
                state
            }

            CollisionResult.OUT_OF_BOUNDS_OR_LOCKED -> {
                lockPieceAndAdvance(piece, currentTimeMillis, dropBonus = 0)
            }
        }
    }

    private fun tryMoveHorizontal(piece: ActivePiece, dx: Int): GameState {
        val candidateX = piece.x + dx
        val collision = canOccupy(
            grid = state.grid,
            activePieces = state.activePieces,
            config = state.config,
            piece = piece,
            candidateX = candidateX,
            candidateY = piece.y,
            candidateRotation = piece.rotation
        )
        if (collision != CollisionResult.NONE) {
            return state
        }

        val moved = piece.copy(x = candidateX)
        val updatedActive = state.activePieces.toMutableMap().apply {
            put(piece.playerId, moved)
        }
        state = refreshPauseFlagsAndGhosts(state.copy(activePieces = updatedActive))
        return state
    }

    private fun tryRotateClockwise(piece: ActivePiece): GameState {
        val targetRotation = (piece.rotation + 1) % 4
        for ((kickX, kickY) in WALL_KICK_OFFSETS) {
            val candidateX = piece.x + kickX
            val candidateY = piece.y + kickY
            val collision = canOccupy(
                grid = state.grid,
                activePieces = state.activePieces,
                config = state.config,
                piece = piece,
                candidateX = candidateX,
                candidateY = candidateY,
                candidateRotation = targetRotation
            )
            if (collision == CollisionResult.NONE) {
                val rotated = piece.copy(
                    x = candidateX,
                    y = candidateY,
                    rotation = targetRotation
                )
                val updatedActive = state.activePieces.toMutableMap().apply {
                    put(piece.playerId, rotated)
                }
                state = refreshPauseFlagsAndGhosts(state.copy(activePieces = updatedActive))
                return state
            }
        }
        return state
    }

    private fun hardDropPlayer(playerId: Int, currentTimeMillis: Long): GameState {
        val piece = state.activePieces[playerId] ?: return state
        var dropY = piece.y
        var rowsDropped = 0

        while (true) {
            val nextCollision = canOccupy(
                grid = state.grid,
                activePieces = state.activePieces,
                config = state.config,
                piece = piece,
                candidateX = piece.x,
                candidateY = dropY + 1,
                candidateRotation = piece.rotation
            )
            if (nextCollision == CollisionResult.NONE) {
                dropY++
                rowsDropped++
            } else {
                break
            }
        }

        val stopReason = canOccupy(
            grid = state.grid,
            activePieces = state.activePieces,
            config = state.config,
            piece = piece,
            candidateX = piece.x,
            candidateY = dropY + 1,
            candidateRotation = piece.rotation
        )

        return if (stopReason == CollisionResult.ACTIVE_PIECE) {
            // Landed on another falling piece mid-air: pause above it without locking.
            val pausedPiece = piece.copy(y = dropY, isPausedByPiece = true)
            val updatedActive = state.activePieces.toMutableMap().apply {
                put(playerId, pausedPiece)
            }
            state = refreshPauseFlagsAndGhosts(state.copy(activePieces = updatedActive))
            state
        } else {
            val landedPiece = piece.copy(y = dropY, isPausedByPiece = false)
            lockPieceAndAdvance(landedPiece, currentTimeMillis, dropBonus = rowsDropped * 2)
        }
    }

    private fun lockPieceAndAdvance(
        piece: ActivePiece,
        currentTimeMillis: Long,
        dropBonus: Int
    ): GameState {
        val mutableGrid = state.grid.map { it.toMutableList() }.toMutableList()
        for (cell in piece.occupiedCells()) {
            if (cell.y in 0 until state.config.height && cell.x in 0 until state.config.width) {
                mutableGrid[cell.y][cell.x] = LockedCell(
                    playerId = piece.playerId,
                    tetrominoType = piece.tetrominoType,
                    skinId = piece.skinId
                )
            }
        }

        // Detect and remove completed horizontal rows
        val clearedRowIndices = mutableGrid.indices.filter { r ->
            mutableGrid[r].none { it == null }
        }
        val remainingRows = mutableGrid.filter { row -> row.any { it == null } }
        val clearedCount = state.config.height - remainingRows.size
        val clearedGrid = if (clearedCount > 0) {
            val emptyTopRows = List(clearedCount) {
                List<LockedCell?>(state.config.width) { null }
            }
            emptyTopRows + remainingRows.map { it.toList() }
        } else {
            mutableGrid.map { it.toList() }
        }
        val nextClearEventId = if (clearedCount > 0) state.lineClearEventId + 1L else state.lineClearEventId
        val nextClearedRows = if (clearedCount > 0) clearedRowIndices else state.lastClearedRowIndices

        val lineScore = lineClearPoints(clearedCount, state.level)
        val totalAwarded = lineScore + dropBonus

        var newSharedScore = state.sharedScore
        var newSharedLines = state.sharedLines
        var newLevel = state.level
        val newPlayerScores = state.playerScores.toMutableMap()
        val newPlayerLines = state.playerLines.toMutableMap()
        val newSabotageUntil = state.sabotageBurstUntilMillis.toMutableMap()
        var newSabotageEvent = state.lastSabotageEvent

        if (state.mode == GameMode.CO_OP) {
            newSharedScore += totalAwarded
            newSharedLines += clearedCount
            newLevel = 1 + (newSharedLines / LINES_PER_LEVEL)
        } else {
            newPlayerScores[piece.playerId] = (newPlayerScores[piece.playerId] ?: 0) + totalAwarded
            newPlayerLines[piece.playerId] = (newPlayerLines[piece.playerId] ?: 0) + clearedCount
            newSharedScore = newPlayerScores.values.sum()
            newSharedLines = newPlayerLines.values.sum()
            newLevel = 1 + (newSharedLines / LINES_PER_LEVEL)

            if (clearedCount > 0) {
                val expiresAt = currentTimeMillis + SABOTAGE_DURATION_MS
                val attackerTeam = state.playerTeams[piece.playerId] ?: TeamColor.NONE
                for (zone in state.config.spawnZones) {
                    if (zone.playerId == piece.playerId) continue
                    val targetTeam = state.playerTeams[zone.playerId] ?: TeamColor.NONE
                    val isTeammate = state.versusSubMode == VersusSubMode.TEAMS_2V2 &&
                        attackerTeam != TeamColor.NONE &&
                        targetTeam == attackerTeam
                    if (!isTeammate) {
                        newSabotageUntil[zone.playerId] = expiresAt
                        newSabotageEvent = SabotageEvent(
                            attackerPlayerId = piece.playerId,
                            targetPlayerId = zone.playerId,
                            linesCleared = clearedCount,
                            expiresAtMillis = expiresAt
                        )
                    }
                }
            }
        }

        // Check spawn zone overflow on row 0 first (catches both self-overflow and opponent-caused overflow)
        val overflowedZone = state.config.spawnZones.firstOrNull { zone ->
            zone.columnRange.any { col ->
                col in 0 until state.config.width && clearedGrid[0][col] != null
            }
        }

        // Prepare next piece for this player
        val nextType = state.nextPieces[piece.playerId] ?: drawFromBag(piece.playerId)
        val upcomingType = drawFromBag(piece.playerId)
        val spawnZone = state.config.spawnZoneFor(piece.playerId)
        val spawnedPiece = ActivePiece(
            playerId = piece.playerId,
            tetrominoType = nextType,
            rotation = 0,
            x = spawnZone.spawnX,
            y = spawnZone.spawnY,
            skinId = state.playerSkins[piece.playerId] ?: piece.skinId
        )

        val spawnBlockedByLocked = hasLockedOrBoundsCollision(clearedGrid, state.config, spawnedPiece)

        val topOutPlayerId: Int? = when {
            overflowedZone != null -> overflowedZone.playerId
            spawnBlockedByLocked -> piece.playerId
            else -> null
        }

        if (topOutPlayerId != null) {
            val updatedActive = state.activePieces.toMutableMap().apply { remove(piece.playerId) }
            var winningTeam: TeamColor? = null
            val winnerId: Int? = if (state.mode == GameMode.VERSUS) {
                if (state.versusSubMode == VersusSubMode.TEAMS_2V2) {
                    val losingTeam = state.playerTeams[topOutPlayerId] ?: TeamColor.NONE
                    val resolvedTeam = when (losingTeam) {
                        TeamColor.CYAN -> TeamColor.MAGENTA
                        TeamColor.MAGENTA -> TeamColor.CYAN
                        TeamColor.NONE -> {
                            val cyanScore = newPlayerScores.entries.sumOf { (pid, s) ->
                                if (state.playerTeams[pid] == TeamColor.CYAN) s else 0
                            }
                            val magentaScore = newPlayerScores.entries.sumOf { (pid, s) ->
                                if (state.playerTeams[pid] == TeamColor.MAGENTA) s else 0
                            }
                            if (cyanScore >= magentaScore) TeamColor.CYAN else TeamColor.MAGENTA
                        }
                    }
                    winningTeam = resolvedTeam
                    state.config.spawnZones
                        .filter { state.playerTeams[it.playerId] == resolvedTeam }
                        .maxWithOrNull(
                            compareBy<SpawnZone> { newPlayerScores[it.playerId] ?: 0 }
                                .thenByDescending { it.playerId }
                        )
                        ?.playerId
                } else {
                    state.config.spawnZones
                        .filter { it.playerId != topOutPlayerId }
                        .maxWithOrNull(
                            compareBy<SpawnZone> { newPlayerScores[it.playerId] ?: 0 }
                                .thenByDescending { it.playerId }
                        )
                        ?.playerId
                }
            } else {
                null
            }
            val message = if (state.mode == GameMode.VERSUS && winnerId != null) {
                if (winningTeam != null) {
                    "P$topOutPlayerId overflowed! ${winningTeam.label} wins!"
                } else {
                    "Player $topOutPlayerId spawn zone (${state.config.spawnZoneFor(topOutPlayerId).columnRange.first}-${state.config.spawnZoneFor(topOutPlayerId).columnRange.last}) overflowed! Player $winnerId wins!"
                }
            } else {
                "Spawn zone blocked! Co-op run ended at Level $newLevel."
            }

            state = state.copy(
                grid = clearedGrid,
                activePieces = updatedActive,
                ghostPieces = emptyMap(),
                sharedScore = newSharedScore,
                sharedLines = newSharedLines,
                level = newLevel,
                playerScores = newPlayerScores,
                playerLines = newPlayerLines,
                sabotageBurstUntilMillis = newSabotageUntil,
                lastSabotageEvent = newSabotageEvent,
                isGameOver = true,
                winnerPlayerId = winnerId,
                winningTeam = winningTeam,
                topOutPlayerId = topOutPlayerId,
                statusMessage = message,
                lastClearedRowIndices = nextClearedRows,
                lineClearEventId = nextClearEventId
            )
            return state
        }

        val updatedActive = state.activePieces.toMutableMap().apply {
            put(piece.playerId, spawnedPiece)
        }
        val updatedNext = state.nextPieces.toMutableMap().apply {
            put(piece.playerId, upcomingType)
        }

        val updatedState = state.copy(
            grid = clearedGrid,
            activePieces = updatedActive,
            nextPieces = updatedNext,
            sharedScore = newSharedScore,
            sharedLines = newSharedLines,
            level = newLevel,
            playerScores = newPlayerScores,
            playerLines = newPlayerLines,
            sabotageBurstUntilMillis = newSabotageUntil,
            lastSabotageEvent = newSabotageEvent,
            lastClearedRowIndices = nextClearedRows,
            lineClearEventId = nextClearEventId
        )

        state = refreshPauseFlagsAndGhosts(updatedState)
        return state
    }

    private fun addDropBonus(currentState: GameState, playerId: Int, points: Int): GameState {
        return if (currentState.mode == GameMode.CO_OP) {
            currentState.copy(sharedScore = currentState.sharedScore + points)
        } else {
            val updatedScores = currentState.playerScores.toMutableMap()
            updatedScores[playerId] = (updatedScores[playerId] ?: 0) + points
            currentState.copy(
                playerScores = updatedScores,
                sharedScore = updatedScores.values.sum()
            )
        }
    }

    private fun refreshPauseFlagsAndGhosts(currentState: GameState): GameState {
        val refreshedActive = currentState.activePieces.mapValues { (_, piece) ->
            val belowCollision = canOccupy(
                grid = currentState.grid,
                activePieces = currentState.activePieces,
                config = currentState.config,
                piece = piece,
                candidateX = piece.x,
                candidateY = piece.y + 1,
                candidateRotation = piece.rotation
            )
            piece.copy(isPausedByPiece = belowCollision == CollisionResult.ACTIVE_PIECE)
        }

        val ghosts = refreshedActive.mapValues { (_, piece) ->
            computeGhostPiece(currentState.grid, refreshedActive, currentState.config, piece)
        }

        return currentState.copy(
            activePieces = refreshedActive,
            ghostPieces = ghosts
        )
    }

    fun computeGhostPiece(
        grid: List<List<LockedCell?>>,
        activePieces: Map<Int, ActivePiece>,
        config: BoardConfig,
        piece: ActivePiece
    ): ActivePiece {
        var ghostY = piece.y
        while (
            canOccupy(
                grid = grid,
                activePieces = activePieces,
                config = config,
                piece = piece,
                candidateX = piece.x,
                candidateY = ghostY + 1,
                candidateRotation = piece.rotation
            ) == CollisionResult.NONE
        ) {
            ghostY++
        }
        return piece.copy(y = ghostY, isPausedByPiece = false)
    }

    fun canOccupy(
        grid: List<List<LockedCell?>>,
        activePieces: Map<Int, ActivePiece>,
        config: BoardConfig,
        piece: ActivePiece,
        candidateX: Int,
        candidateY: Int,
        candidateRotation: Int
    ): CollisionResult {
        val candidateCells = piece.occupiedCells(candidateX, candidateY, candidateRotation)
        for (cell in candidateCells) {
            if (cell.x !in 0 until config.width || cell.y !in 0 until config.height) {
                return CollisionResult.OUT_OF_BOUNDS_OR_LOCKED
            }
            if (grid[cell.y][cell.x] != null) {
                return CollisionResult.OUT_OF_BOUNDS_OR_LOCKED
            }
        }

        for ((otherPlayerId, otherPiece) in activePieces) {
            if (otherPlayerId == piece.playerId) continue
            val otherCells = otherPiece.occupiedCells()
            if (candidateCells.any { cell -> cell in otherCells }) {
                return CollisionResult.ACTIVE_PIECE
            }
        }

        return CollisionResult.NONE
    }

    private fun hasLockedOrBoundsCollision(
        grid: List<List<LockedCell?>>,
        config: BoardConfig,
        piece: ActivePiece
    ): Boolean {
        return piece.occupiedCells().any { cell ->
            cell.x !in 0 until config.width ||
                cell.y !in 0 until config.height ||
                grid[cell.y][cell.x] != null
        }
    }

    private fun drawFromBag(playerId: Int): TetrominoType {
        pieceBagOverride?.let { return it(playerId) }
        val bag = playerBags.getOrPut(playerId) { mutableListOf() }
        if (bag.isEmpty()) {
            bag.addAll(TetrominoType.entries.shuffled(random))
        }
        return bag.removeAt(0)
    }

    private fun lineClearPoints(linesCleared: Int, level: Int): Int {
        val base = when (linesCleared) {
            0 -> 0
            1 -> 100
            2 -> 300
            3 -> 500
            4 -> 800
            else -> linesCleared * 250
        }
        return base * level
    }

    private fun defaultSkinFor(playerId: Int): String {
        val skins = listOf(
            "classic",
            "neon_cyber",
            "golden_flex",
            "obsidian_magma",
            "hologram_glitch",
            "pixel_crystal"
        )
        return skins[(playerId - 1).coerceAtLeast(0) % skins.size]
    }

    companion object {
        const val BASE_DROP_INTERVAL_MS = 800L
        const val MIN_DROP_INTERVAL_MS = 120L
        const val LEVEL_SPEED_STEP_MS = 65L
        const val LINES_PER_LEVEL = 10
        const val SABOTAGE_DURATION_MS = 500L
        const val SABOTAGE_DROP_INTERVAL_MS = 75L

        private val WALL_KICK_OFFSETS = listOf(
            0 to 0,
            -1 to 0,
            1 to 0,
            0 to 1,
            0 to -1,
            -2 to 0,
            2 to 0
        )
    }
}
