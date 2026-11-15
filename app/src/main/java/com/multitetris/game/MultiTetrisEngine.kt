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
    private var coopGlitchElapsedMs: Long = 0L

    var state: GameState = GameState()
        private set

    fun startNewGame(
        mode: GameMode,
        config: BoardConfig = BoardConfig(),
        playerSkins: Map<Int, String> = GameState.defaultPlayerSkins(config.playerCount),
        currentTimeMillis: Long = 0L,
        versusSubMode: VersusSubMode = VersusSubMode.FREE_FOR_ALL,
        playerTeams: Map<Int, TeamColor> = emptyMap(),
        sabotageEnabled: Boolean = false,
        equippedSabotages: Map<Int, List<SabotageType>> = emptyMap()
    ): GameState {
        playerBags.clear()
        coopGlitchElapsedMs = 0L
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

        val resolvedEquipped = config.spawnZones.associate { zone ->
            zone.playerId to SabotageConfig.resolveLoadout(
                equippedSabotages[zone.playerId] ?: SabotageConfig.defaultLoadout
            )
        }
        val initialUsed = config.spawnZones.associate { it.playerId to emptySet<SabotageType>() }

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
            statusMessage = null,
            sabotageEnabled = sabotageEnabled,
            equippedSabotages = resolvedEquipped,
            usedSabotages = initialUsed,
            activeSabotages = emptyMap(),
            systemGlitchWarningActive = false
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
        return when {
            state.activeSabotageFor(playerId)?.type == SabotageType.TURBO_DROP ->
                (baseInterval / 3L).coerceAtLeast(40L)
            !state.sabotageEnabled && state.isSabotaged(playerId, currentTimeMillis) ->
                SABOTAGE_DROP_INTERVAL_MS
            else -> baseInterval
        }
    }

    fun applyAction(
        playerId: Int,
        action: PlayerAction,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): GameState {
        if (state.isPaused || state.isGameOver) return state
        val piece = state.activePieces[playerId] ?: return state
        val activeType = state.activeSabotageFor(playerId)?.type
        if (activeType == SabotageType.BUTTON_FREEZE) return state
        if (activeType == SabotageType.DROP_JAMMER && action == PlayerAction.HARD_DROP) return state

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
                    if (zone.playerId in state.eliminatedPlayerIds ||
                        zone.playerId in state.blockedPlayerIds ||
                        zone.playerId in state.disconnectedPlayerIds
                    ) continue
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

        val updatedActive = state.activePieces.toMutableMap()
        val updatedNext = state.nextPieces.toMutableMap()
        if (spawnBlockedByLocked) {
            updatedActive.remove(piece.playerId)
        } else {
            updatedActive[piece.playerId] = spawnedPiece
            updatedNext[piece.playerId] = upcomingType
        }

        val isRow0Blocked: (SpawnZone) -> Boolean = { zone ->
            zone.columnRange.any { col ->
                col in 0 until state.config.width && clearedGrid[0][col] != null
            }
        }

        if (state.mode == GameMode.CO_OP) {
            val newBlockedIds = state.blockedPlayerIds.toMutableSet()
            var feedToast: String? = null

            // Respawn blocked Co-op teammates if a line clear freed their spawn zone (never respawn disconnected players)
            if (clearedCount > 0 && newBlockedIds.isNotEmpty()) {
                for (blockedId in state.blockedPlayerIds) {
                    if (blockedId in state.disconnectedPlayerIds) continue
                    val zone = state.config.spawnZoneFor(blockedId)
                    if (!isRow0Blocked(zone)) {
                        val respawnType = updatedNext[blockedId] ?: drawFromBag(blockedId)
                        val respawnPiece = ActivePiece(
                            playerId = blockedId,
                            tetrominoType = respawnType,
                            rotation = 0,
                            x = zone.spawnX,
                            y = zone.spawnY,
                            skinId = state.playerSkins[blockedId] ?: "classic"
                        )
                        if (!hasLockedOrBoundsCollision(clearedGrid, state.config, respawnPiece)) {
                            newBlockedIds.remove(blockedId)
                            updatedActive[blockedId] = respawnPiece
                            updatedNext[blockedId] = drawFromBag(blockedId)
                            feedToast = "P$blockedId is back in!"
                        }
                    }
                }
            }

            for (zone in state.config.spawnZones) {
                if (zone.playerId in newBlockedIds) continue
                val pieceBlocked = zone.playerId == piece.playerId && spawnBlockedByLocked
                if (isRow0Blocked(zone) || pieceBlocked) {
                    newBlockedIds.add(zone.playerId)
                    updatedActive.remove(zone.playerId)
                    feedToast = "P${zone.playerId} blocked — Waiting for clear..."
                }
            }

            val allBlocked = newBlockedIds.size >= state.config.playerCount || updatedActive.isEmpty()
            val nextFeedEventId = if (feedToast != null) state.feedToastEventId + 1L else state.feedToastEventId
            if (allBlocked) {
                state = state.copy(
                    grid = clearedGrid,
                    activePieces = emptyMap(),
                    ghostPieces = emptyMap(),
                    nextPieces = updatedNext,
                    sharedScore = newSharedScore,
                    sharedLines = newSharedLines,
                    level = newLevel,
                    playerScores = newPlayerScores,
                    playerLines = newPlayerLines,
                    sabotageBurstUntilMillis = newSabotageUntil,
                    lastSabotageEvent = newSabotageEvent,
                    blockedPlayerIds = newBlockedIds,
                    feedToastMessage = feedToast ?: state.feedToastMessage,
                    feedToastEventId = nextFeedEventId,
                    isGameOver = true,
                    winnerPlayerId = null,
                    winningTeam = null,
                    topOutPlayerId = piece.playerId,
                    statusMessage = "All players blocked! Co-op run ended at Level $newLevel.",
                    lastClearedRowIndices = nextClearedRows,
                    lineClearEventId = nextClearEventId
                )
                return state
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
                blockedPlayerIds = newBlockedIds,
                feedToastMessage = feedToast ?: state.feedToastMessage,
                feedToastEventId = nextFeedEventId,
                lastClearedRowIndices = nextClearedRows,
                lineClearEventId = nextClearEventId
            )
            state = refreshPauseFlagsAndGhosts(updatedState)
            return state
        }

        // VERSUS MODE (2P, 3P, 4P FFA & 4P 2v2 Teams)
        val newEliminatedIds = state.eliminatedPlayerIds.toMutableSet()
        val newPlacements = state.playerPlacements.toMutableMap()
        val newlyEliminated = mutableListOf<Int>()
        var feedToast: String? = null

        for (zone in state.config.spawnZones) {
            if (zone.playerId in newEliminatedIds) continue
            val pieceBlocked = zone.playerId == piece.playerId && spawnBlockedByLocked
            if (isRow0Blocked(zone) || pieceBlocked) {
                newlyEliminated.add(zone.playerId)
            }
        }

        if (newlyEliminated.isNotEmpty()) {
            val updatedSabotages = state.activeSabotages.toMutableMap()
            for (elimId in newlyEliminated) {
                newEliminatedIds.add(elimId)
                updatedActive.remove(elimId)
                updatedSabotages.remove(elimId)
                val aliveAfter = state.config.spawnZones.count { it.playerId !in newEliminatedIds }
                newPlacements[elimId] = aliveAfter + 1
                feedToast = "P$elimId was eliminated!"
            }

            val aliveZones = state.config.spawnZones.filter { it.playerId !in newEliminatedIds }
            val nextFeedEventId = state.feedToastEventId + 1L

            if (state.versusSubMode == VersusSubMode.TEAMS_2V2) {
                val cyanAlive = aliveZones.any { state.playerTeams[it.playerId] == TeamColor.CYAN }
                val magentaAlive = aliveZones.any { state.playerTeams[it.playerId] == TeamColor.MAGENTA }
                if (!cyanAlive || !magentaAlive) {
                    val resolvedTeam = when {
                        cyanAlive && !magentaAlive -> TeamColor.CYAN
                        magentaAlive && !cyanAlive -> TeamColor.MAGENTA
                        else -> {
                            val cyanScore = newPlayerScores.entries.sumOf { (pid, s) ->
                                if (state.playerTeams[pid] == TeamColor.CYAN) s else 0
                            }
                            val magentaScore = newPlayerScores.entries.sumOf { (pid, s) ->
                                if (state.playerTeams[pid] == TeamColor.MAGENTA) s else 0
                            }
                            if (cyanScore >= magentaScore) TeamColor.CYAN else TeamColor.MAGENTA
                        }
                    }
                    for (zone in state.config.spawnZones) {
                        if (state.playerTeams[zone.playerId] == resolvedTeam) {
                            newPlacements[zone.playerId] = 1
                        }
                    }
                    val winnerId = state.config.spawnZones
                        .filter { state.playerTeams[it.playerId] == resolvedTeam }
                        .maxWithOrNull(
                            compareBy<SpawnZone> { newPlayerScores[it.playerId] ?: 0 }
                                .thenByDescending { it.playerId }
                        )
                        ?.playerId
                    val lastElim = newlyEliminated.last()
                    state = state.copy(
                        grid = clearedGrid,
                        activePieces = emptyMap(),
                        ghostPieces = emptyMap(),
                        nextPieces = updatedNext,
                        sharedScore = newSharedScore,
                        sharedLines = newSharedLines,
                        level = newLevel,
                        playerScores = newPlayerScores,
                        playerLines = newPlayerLines,
                        sabotageBurstUntilMillis = newSabotageUntil,
                        lastSabotageEvent = newSabotageEvent,
                        activeSabotages = emptyMap(),
                        systemGlitchWarningActive = false,
                        eliminatedPlayerIds = newEliminatedIds,
                        playerPlacements = newPlacements,
                        feedToastMessage = feedToast,
                        feedToastEventId = nextFeedEventId,
                        isGameOver = true,
                        winnerPlayerId = winnerId,
                        winningTeam = resolvedTeam,
                        topOutPlayerId = lastElim,
                        statusMessage = "${resolvedTeam.label} wins!",
                        lastClearedRowIndices = nextClearedRows,
                        lineClearEventId = nextClearEventId
                    )
                    return state
                }
            } else if (aliveZones.size <= 1) {
                val lastSurvivor = aliveZones.firstOrNull()
                if (lastSurvivor != null) {
                    newPlacements[lastSurvivor.playerId] = 1
                }
                val winnerId = lastSurvivor?.playerId ?: state.config.spawnZones
                    .filter { it.playerId != newlyEliminated.first() }
                    .maxWithOrNull(
                        compareBy<SpawnZone> { newPlayerScores[it.playerId] ?: 0 }
                            .thenByDescending { it.playerId }
                    )
                    ?.playerId
                val lastElim = newlyEliminated.last()
                state = state.copy(
                    grid = clearedGrid,
                    activePieces = emptyMap(),
                    ghostPieces = emptyMap(),
                    nextPieces = updatedNext,
                    sharedScore = newSharedScore,
                    sharedLines = newSharedLines,
                    level = newLevel,
                    playerScores = newPlayerScores,
                    playerLines = newPlayerLines,
                    sabotageBurstUntilMillis = newSabotageUntil,
                    lastSabotageEvent = newSabotageEvent,
                    activeSabotages = emptyMap(),
                    systemGlitchWarningActive = false,
                    eliminatedPlayerIds = newEliminatedIds,
                    playerPlacements = newPlacements,
                    feedToastMessage = feedToast,
                    feedToastEventId = nextFeedEventId,
                    isGameOver = true,
                    winnerPlayerId = winnerId,
                    winningTeam = null,
                    topOutPlayerId = lastElim,
                    statusMessage = if (winnerId != null) {
                        "Player $winnerId wins!"
                    } else {
                        "Match over!"
                    },
                    lastClearedRowIndices = nextClearedRows,
                    lineClearEventId = nextClearEventId
                )
                return state
            }

            // Match continues with remaining alive players/teams!
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
                activeSabotages = updatedSabotages,
                eliminatedPlayerIds = newEliminatedIds,
                playerPlacements = newPlacements,
                feedToastMessage = feedToast,
                feedToastEventId = nextFeedEventId,
                lastClearedRowIndices = nextClearedRows,
                lineClearEventId = nextClearEventId
            )
            state = refreshPauseFlagsAndGhosts(updatedState)
            return state
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

    /**
     * Marks a non-host player as disconnected mid-match while preserving their roster slot.
     * Clears their active falling piece, sets eliminated + disconnected flags, emits a feed toast,
     * and re-evaluates Last-Standing / 2v2 / Co-op match-end conditions immediately.
     */
    fun markPlayerDisconnected(playerId: Int, displayName: String? = null): GameState {
        if (state.isGameOver || playerId in state.disconnectedPlayerIds) {
            return state
        }
        if (state.config.spawnZones.none { it.playerId == playerId }) {
            return state
        }

        val newDisconnectedIds = state.disconnectedPlayerIds + playerId
        val newEliminatedIds = state.eliminatedPlayerIds + playerId
        val newBlockedIds = if (state.mode == GameMode.CO_OP) {
            state.blockedPlayerIds + playerId
        } else {
            state.blockedPlayerIds
        }
        val updatedActive = state.activePieces.toMutableMap().apply { remove(playerId) }
        val updatedGhosts = state.ghostPieces.toMutableMap().apply { remove(playerId) }
        val updatedSabotages = state.activeSabotages.toMutableMap().apply { remove(playerId) }
        val label = displayName?.takeIf { it.isNotBlank() } ?: "P$playerId"
        val feedToast = "$label left the match"
        val nextFeedEventId = state.feedToastEventId + 1L

        if (state.mode == GameMode.CO_OP) {
            val allBlockedOrLeft = newBlockedIds.size >= state.config.playerCount || updatedActive.isEmpty()
            state = if (allBlockedOrLeft) {
                state.copy(
                    activePieces = emptyMap(),
                    ghostPieces = emptyMap(),
                    activeSabotages = emptyMap(),
                    systemGlitchWarningActive = false,
                    blockedPlayerIds = newBlockedIds,
                    eliminatedPlayerIds = newEliminatedIds,
                    disconnectedPlayerIds = newDisconnectedIds,
                    feedToastMessage = feedToast,
                    feedToastEventId = nextFeedEventId,
                    isGameOver = true,
                    winnerPlayerId = null,
                    winningTeam = null,
                    topOutPlayerId = playerId,
                    statusMessage = "All teammates left or blocked! Co-op run ended at Level ${state.level}."
                )
            } else {
                refreshPauseFlagsAndGhosts(
                    state.copy(
                        activePieces = updatedActive,
                        ghostPieces = updatedGhosts,
                        activeSabotages = updatedSabotages,
                        blockedPlayerIds = newBlockedIds,
                        eliminatedPlayerIds = newEliminatedIds,
                        disconnectedPlayerIds = newDisconnectedIds,
                        feedToastMessage = feedToast,
                        feedToastEventId = nextFeedEventId
                    )
                )
            }
            return state
        }

        // VERSUS MODE (2P, 3P, 4P FFA & 4P 2v2 Teams)
        val newPlacements = state.playerPlacements.toMutableMap()
        if (playerId !in newPlacements) {
            val aliveAfter = state.config.spawnZones.count { it.playerId !in newEliminatedIds }
            newPlacements[playerId] = aliveAfter + 1
        }
        val aliveZones = state.config.spawnZones.filter { it.playerId !in newEliminatedIds }

        if (state.versusSubMode == VersusSubMode.TEAMS_2V2) {
            val cyanAlive = aliveZones.any { state.playerTeams[it.playerId] == TeamColor.CYAN }
            val magentaAlive = aliveZones.any { state.playerTeams[it.playerId] == TeamColor.MAGENTA }
            if (!cyanAlive || !magentaAlive) {
                val resolvedTeam = when {
                    cyanAlive && !magentaAlive -> TeamColor.CYAN
                    magentaAlive && !cyanAlive -> TeamColor.MAGENTA
                    else -> TeamColor.CYAN
                }
                for (zone in state.config.spawnZones) {
                    if (state.playerTeams[zone.playerId] == resolvedTeam) {
                        newPlacements[zone.playerId] = 1
                    }
                }
                val winnerId = state.config.spawnZones
                    .filter { state.playerTeams[it.playerId] == resolvedTeam }
                    .maxWithOrNull(
                        compareBy<SpawnZone> { state.playerScores[it.playerId] ?: 0 }
                            .thenByDescending { it.playerId }
                    )
                    ?.playerId
                state = state.copy(
                    activePieces = emptyMap(),
                    ghostPieces = emptyMap(),
                    activeSabotages = emptyMap(),
                    systemGlitchWarningActive = false,
                    eliminatedPlayerIds = newEliminatedIds,
                    disconnectedPlayerIds = newDisconnectedIds,
                    playerPlacements = newPlacements,
                    feedToastMessage = feedToast,
                    feedToastEventId = nextFeedEventId,
                    isGameOver = true,
                    winnerPlayerId = winnerId,
                    winningTeam = resolvedTeam,
                    topOutPlayerId = playerId,
                    statusMessage = "${resolvedTeam.label} wins!"
                )
                return state
            }
        } else if (aliveZones.size <= 1) {
            val lastSurvivor = aliveZones.firstOrNull()
            if (lastSurvivor != null) {
                newPlacements[lastSurvivor.playerId] = 1
            }
            val winnerId = lastSurvivor?.playerId
            state = state.copy(
                activePieces = emptyMap(),
                ghostPieces = emptyMap(),
                activeSabotages = emptyMap(),
                systemGlitchWarningActive = false,
                eliminatedPlayerIds = newEliminatedIds,
                disconnectedPlayerIds = newDisconnectedIds,
                playerPlacements = newPlacements,
                feedToastMessage = feedToast,
                feedToastEventId = nextFeedEventId,
                isGameOver = true,
                winnerPlayerId = winnerId,
                winningTeam = null,
                topOutPlayerId = playerId,
                statusMessage = if (winnerId != null) {
                    "Player $winnerId wins!"
                } else {
                    "Match over!"
                }
            )
            return state
        }

        state = refreshPauseFlagsAndGhosts(
            state.copy(
                activePieces = updatedActive,
                ghostPieces = updatedGhosts,
                activeSabotages = updatedSabotages,
                eliminatedPlayerIds = newEliminatedIds,
                disconnectedPlayerIds = newDisconnectedIds,
                playerPlacements = newPlacements,
                feedToastMessage = feedToast,
                feedToastEventId = nextFeedEventId
            )
        )
        return state
    }

    /**
     * Validates and applies a drafted Versus sabotage from [attackerPlayerId] to [targetPlayerId].
     */
    fun useSabotage(
        attackerPlayerId: Int,
        targetPlayerId: Int,
        sabotageType: SabotageType,
        attackerLabel: String? = null
    ): GameState {
        if (state.isPaused || state.isGameOver || !state.sabotageEnabled || state.mode != GameMode.VERSUS) {
            return state
        }
        if (state.isPlayerEliminated(attackerPlayerId) || state.isPlayerDisconnected(attackerPlayerId)) {
            return state
        }
        if (sabotageType !in state.equippedSabotagesFor(attackerPlayerId)) {
            return state
        }
        if (sabotageType in state.usedSabotagesFor(attackerPlayerId)) {
            return state
        }
        if (targetPlayerId !in state.validSabotageTargetsFor(attackerPlayerId)) {
            return state
        }

        val updatedUsed = state.usedSabotages.toMutableMap()
        updatedUsed[attackerPlayerId] = state.usedSabotagesFor(attackerPlayerId) + sabotageType

        val updatedActiveSabotages = state.activeSabotages.toMutableMap()
        updatedActiveSabotages[targetPlayerId] = ActiveSabotageState(
            type = sabotageType,
            remainingMs = SabotageConfig.durationMs(sabotageType),
            sourceLabel = attackerLabel?.takeIf { it.isNotBlank() } ?: "P$attackerPlayerId"
        )

        val attackerText = attackerLabel?.takeIf { it.isNotBlank() } ?: "P$attackerPlayerId"
        state = state.copy(
            usedSabotages = updatedUsed,
            activeSabotages = updatedActiveSabotages,
            feedToastMessage = "$attackerText used ${sabotageType.displayName} on P$targetPlayerId!",
            feedToastEventId = state.feedToastEventId + 1L
        )
        return state
    }

    /**
     * Advances active sabotage timers and Co-op Rogue AI System Glitch countdown on each tick.
     */
    fun tickSabotageAndCoopGlitch(deltaMs: Long): GameState {
        if (state.isPaused || state.isGameOver || deltaMs <= 0L) {
            return state
        }

        val updatedActiveSabotages = mutableMapOf<Int, ActiveSabotageState>()
        var changed = false
        for ((playerId, debuff) in state.activeSabotages) {
            val nextRemaining = debuff.remainingMs - deltaMs
            if (nextRemaining > 0L) {
                updatedActiveSabotages[playerId] = debuff.copy(remainingMs = nextRemaining)
            }
            changed = true
        }

        var warningActive = false
        var feedToast: String? = null
        if (state.sabotageEnabled && state.mode == GameMode.CO_OP) {
            coopGlitchElapsedMs += deltaMs
            if (coopGlitchElapsedMs >= SabotageConfig.COOP_GLITCH_INTERVAL_MS) {
                coopGlitchElapsedMs -= SabotageConfig.COOP_GLITCH_INTERVAL_MS
                val aliveTargets = state.config.spawnZones
                    .map { it.playerId }
                    .filter { pid ->
                        !state.isPlayerEliminated(pid) &&
                            !state.isPlayerDisconnected(pid) &&
                            !state.isPlayerBlocked(pid)
                    }
                if (aliveTargets.isNotEmpty()) {
                    val targetId = aliveTargets[random.nextInt(aliveTargets.size)]
                    val glitchType = SabotageType.entries[random.nextInt(SabotageType.entries.size)]
                    updatedActiveSabotages[targetId] = ActiveSabotageState(
                        type = glitchType,
                        remainingMs = SabotageConfig.durationMs(glitchType),
                        sourceLabel = "SYSTEM"
                    )
                    feedToast = "⚠️ SYSTEM GLITCH: ${glitchType.displayName} on P$targetId!"
                    changed = true
                }
            }
            warningActive = coopGlitchElapsedMs >=
                (SabotageConfig.COOP_GLITCH_INTERVAL_MS - SabotageConfig.COOP_GLITCH_WARNING_MS)
        }

        if (!changed && warningActive == state.systemGlitchWarningActive) {
            return state
        }

        state = state.copy(
            activeSabotages = updatedActiveSabotages,
            systemGlitchWarningActive = warningActive,
            feedToastMessage = feedToast ?: state.feedToastMessage,
            feedToastEventId = if (feedToast != null) state.feedToastEventId + 1L else state.feedToastEventId
        )
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
