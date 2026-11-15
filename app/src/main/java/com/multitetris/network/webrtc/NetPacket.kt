package com.multitetris.network.webrtc

import com.multitetris.game.ActivePiece
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.LockedCell
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.TeamColor
import com.multitetris.game.TetrominoType
import com.multitetris.game.VersusSubMode
import com.multitetris.ui.skin.SkinCatalog
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed interface NetPacket

@Serializable
@SerialName("handshake")
data class HandshakePacket(
    val nickname: String,
    val skinId: String,
    val uid: String = "",
    val slotIndex: Int = 0
) : NetPacket

@Serializable
@SerialName("input")
data class InputPacket(
    val seq: Int,
    val action: PlayerAction,
    val playerId: Int = 2
) : NetPacket

@Serializable
data class PieceState(
    val playerId: Int,
    val tetrominoType: TetrominoType,
    val rotation: Int,
    val x: Int,
    val y: Int,
    val skinId: String,
    val isPausedByPiece: Boolean = false
) {
    fun toActivePiece(): ActivePiece = ActivePiece(
        playerId = playerId,
        tetrominoType = tetrominoType,
        rotation = rotation,
        x = x,
        y = y,
        skinId = skinId,
        isPausedByPiece = isPausedByPiece
    )

    companion object {
        fun fromActivePiece(piece: ActivePiece): PieceState = PieceState(
            playerId = piece.playerId,
            tetrominoType = piece.tetrominoType,
            rotation = piece.rotation,
            x = piece.x,
            y = piece.y,
            skinId = piece.skinId,
            isPausedByPiece = piece.isPausedByPiece
        )
    }
}

@Serializable
@SerialName("state")
data class GameStatePacket(
    val tick: Long,
    val grid: List<IntArray>,
    val activePieces: List<PieceState>,
    val nextPieces: Map<Int, TetrominoType>,
    val score: Int,
    val level: Int,
    val mode: GameMode,
    val gameOverWinnerId: Int?,
    val sharedLines: Int = 0,
    val playerScores: Map<Int, Int> = emptyMap(),
    val playerLines: Map<Int, Int> = emptyMap(),
    val sabotagedPlayerIds: Set<Int> = emptySet(),
    val isGameOver: Boolean = false,
    val topOutPlayerId: Int? = null,
    val statusMessage: String? = null,
    val isPaused: Boolean = false,
    val playerSkins: Map<Int, String> = emptyMap(),
    val lastClearedRowIndices: List<Int> = emptyList(),
    val lineClearEventId: Long = 0L,
    val playerCount: Int = 2,
    val versusSubMode: String = VersusSubMode.FREE_FOR_ALL.name,
    val playerTeams: Map<Int, String> = emptyMap(),
    val winningTeam: String? = null,
    val playerNames: Map<Int, String> = emptyMap(),
    val eliminatedPlayerIds: Set<Int> = emptySet(),
    val playerPlacements: Map<Int, Int> = emptyMap(),
    val blockedPlayerIds: Set<Int> = emptySet(),
    val disconnectedPlayerIds: Set<Int> = emptySet(),
    val feedToastMessage: String? = null,
    val feedToastEventId: Long = 0L
) : NetPacket {

    fun toClientGameState(
        ghostCalculator: MultiTetrisEngine,
        localClockMillis: Long = System.currentTimeMillis()
    ): GameState {
        val height = grid.size.coerceAtLeast(4)
        val width = grid.firstOrNull()?.size?.coerceAtLeast(4) ?: 10
        val inferredPlayers = maxOf(
            playerCount,
            when (width) {
                16 -> 4
                13, 12 -> 3
                else -> 2
            },
            activePieces.maxOfOrNull { it.playerId } ?: 2,
            playerTeams.keys.maxOrNull() ?: 2
        )
        val config = BoardConfig(width = width, height = height, playerCount = inferredPlayers)

        val decodedGrid = grid.map { row ->
            row.map { packed -> GridCellCodec.decode(packed) }
        }
        val activeMap = activePieces.associate { pieceState ->
            pieceState.playerId to pieceState.toActivePiece()
        }
        val ghostMap = if (isGameOver) {
            emptyMap()
        } else {
            activeMap.mapValues { (_, piece) ->
                ghostCalculator.computeGhostPiece(decodedGrid, activeMap, config, piece)
            }
        }
        val sabotageUntil = sabotagedPlayerIds.associateWith { localClockMillis + 500L }
        val resolvedSubMode = runCatching { VersusSubMode.valueOf(versusSubMode) }
            .getOrDefault(VersusSubMode.FREE_FOR_ALL)
        val resolvedTeams = playerTeams.mapValues { (_, raw) ->
            runCatching { TeamColor.valueOf(raw) }.getOrDefault(TeamColor.NONE)
        }
        val resolvedWinningTeam = winningTeam?.let { raw ->
            runCatching { TeamColor.valueOf(raw) }.getOrNull()
        }

        return GameState(
            config = config,
            mode = mode,
            versusSubMode = resolvedSubMode,
            playerTeams = resolvedTeams,
            grid = decodedGrid,
            activePieces = activeMap,
            ghostPieces = ghostMap,
            nextPieces = nextPieces,
            playerSkins = playerSkins,
            sharedScore = score,
            sharedLines = sharedLines,
            level = level,
            playerScores = playerScores,
            playerLines = playerLines,
            sabotageBurstUntilMillis = sabotageUntil,
            lastSabotageEvent = null,
            isPaused = isPaused,
            isGameOver = isGameOver,
            winnerPlayerId = gameOverWinnerId,
            winningTeam = resolvedWinningTeam,
            topOutPlayerId = topOutPlayerId,
            statusMessage = statusMessage,
            lastClearedRowIndices = lastClearedRowIndices,
            lineClearEventId = lineClearEventId,
            eliminatedPlayerIds = eliminatedPlayerIds,
            playerPlacements = playerPlacements,
            blockedPlayerIds = blockedPlayerIds,
            disconnectedPlayerIds = disconnectedPlayerIds,
            feedToastMessage = feedToastMessage,
            feedToastEventId = feedToastEventId
        )
    }

    companion object {
        fun fromGameState(
            tick: Long,
            state: GameState,
            nowMillis: Long,
            playerNames: Map<Int, String> = emptyMap()
        ): GameStatePacket {
            val encodedGrid = state.grid.map { row ->
                IntArray(row.size) { col -> GridCellCodec.encode(row[col]) }
            }
            val activeList = state.activePieces.values.map { PieceState.fromActivePiece(it) }
            val sabotagedIds = state.activePieces.keys
                .filter { playerId -> state.isSabotaged(playerId, nowMillis) }
                .toSet()

            return GameStatePacket(
                tick = tick,
                grid = encodedGrid,
                activePieces = activeList,
                nextPieces = state.nextPieces,
                score = state.sharedScore,
                level = state.level,
                mode = state.mode,
                gameOverWinnerId = state.winnerPlayerId,
                sharedLines = state.sharedLines,
                playerScores = state.playerScores,
                playerLines = state.playerLines,
                sabotagedPlayerIds = sabotagedIds,
                isGameOver = state.isGameOver,
                topOutPlayerId = state.topOutPlayerId,
                statusMessage = state.statusMessage,
                isPaused = state.isPaused,
                playerSkins = state.playerSkins,
                lastClearedRowIndices = state.lastClearedRowIndices,
                lineClearEventId = state.lineClearEventId,
                playerCount = state.config.playerCount,
                versusSubMode = state.versusSubMode.name,
                playerTeams = state.playerTeams.mapValues { it.value.name },
                winningTeam = state.winningTeam?.name,
                playerNames = playerNames,
                eliminatedPlayerIds = state.eliminatedPlayerIds,
                playerPlacements = state.playerPlacements,
                blockedPlayerIds = state.blockedPlayerIds,
                disconnectedPlayerIds = state.disconnectedPlayerIds,
                feedToastMessage = state.feedToastMessage,
                feedToastEventId = state.feedToastEventId
            )
        }
    }
}

@Serializable
@SerialName("rematch")
data class RematchRequestPacket(
    val requestedByPlayerId: Int,
    val timestamp: Long = System.currentTimeMillis()
) : NetPacket

/**
 * Compact integer packing for board cells sent inside GameStatePacket.grid:
 * 0 = empty cell; non-zero = (playerId shl 16) | ((tetrominoOrdinal + 1) shl 8) | skinCode
 * Supports up to 255 skins (all 20 SkinCatalog skins + future skins) and 255 players.
 */
object GridCellCodec {
    private val orderedSkinIds: List<String> = SkinCatalog.items.map { it.renderer.skinId }
    private val skinToCode: Map<String, Int> = orderedSkinIds
        .mapIndexed { index, skinId -> skinId to (index + 1) }
        .toMap()
    private val codeToSkin: Map<Int, String> = orderedSkinIds
        .mapIndexed { index, skinId -> (index + 1) to skinId }
        .toMap()

    fun encode(cell: LockedCell?): Int {
        if (cell == null) return 0
        val skinCode = (skinToCode[cell.skinId] ?: 1) and 0xFF
        val typeCode = (cell.tetrominoType.ordinal + 1) and 0xFF
        val playerCode = cell.playerId.coerceIn(1, 255)
        return (playerCode shl 16) or (typeCode shl 8) or skinCode
    }

    fun decode(packed: Int): LockedCell? {
        if (packed == 0) return null
        val playerId = (packed ushr 16) and 0xFF
        val typeOrdinal = ((packed ushr 8) and 0xFF) - 1
        val skinCode = packed and 0xFF
        val type = TetrominoType.entries.getOrNull(typeOrdinal) ?: TetrominoType.I
        val resolvedPlayerId = playerId.coerceAtLeast(1)
        val skinId = codeToSkin[skinCode] ?: GameState.defaultSkinForPlayer(resolvedPlayerId)
        return LockedCell(
            playerId = resolvedPlayerId,
            tetrominoType = type,
            skinId = skinId
        )
    }
}

object NetPacketCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "packetType"
    }

    fun encode(packet: NetPacket): String = json.encodeToString(packet)

    fun decode(raw: String): NetPacket? = runCatching {
        json.decodeFromString<NetPacket>(raw)
    }.getOrNull()
}
