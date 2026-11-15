package com.multitetris.network.webrtc

import com.multitetris.game.ActivePiece
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.LockedCell
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.TetrominoType
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
    val skinId: String
) : NetPacket

@Serializable
@SerialName("input")
data class InputPacket(
    val seq: Int,
    val action: PlayerAction
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
    val playerSkins: Map<Int, String> = emptyMap()
) : NetPacket {

    fun toClientGameState(
        ghostCalculator: MultiTetrisEngine,
        localClockMillis: Long = System.currentTimeMillis()
    ): GameState {
        val height = grid.size.coerceAtLeast(4)
        val width = grid.firstOrNull()?.size?.coerceAtLeast(4) ?: 10
        val config = BoardConfig(width = width, height = height, playerCount = 2)

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

        return GameState(
            config = config,
            mode = mode,
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
            topOutPlayerId = topOutPlayerId,
            statusMessage = statusMessage
        )
    }

    companion object {
        fun fromGameState(
            tick: Long,
            state: GameState,
            nowMillis: Long
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
                playerSkins = state.playerSkins
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
 * 0 = empty cell; non-zero = (playerId shl 12) | (tetrominoOrdinal shl 4) | skinCode
 */
object GridCellCodec {
    private val skinToCode = mapOf(
        "classic" to 1,
        "neon_cyber" to 2
    )
    private val codeToSkin = mapOf(
        1 to "classic",
        2 to "neon_cyber"
    )

    fun encode(cell: LockedCell?): Int {
        if (cell == null) return 0
        val skinCode = skinToCode[cell.skinId] ?: 1
        val typeOrdinal = cell.tetrominoType.ordinal and 0xFF
        return (cell.playerId shl 12) or (typeOrdinal shl 4) or (skinCode and 0xF)
    }

    fun decode(packed: Int): LockedCell? {
        if (packed == 0) return null
        val playerId = packed ushr 12
        val typeOrdinal = (packed ushr 4) and 0xFF
        val skinCode = packed and 0xF
        val type = TetrominoType.entries.getOrNull(typeOrdinal) ?: TetrominoType.I
        val skinId = codeToSkin[skinCode] ?: "classic"
        return LockedCell(
            playerId = playerId.coerceAtLeast(1),
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
