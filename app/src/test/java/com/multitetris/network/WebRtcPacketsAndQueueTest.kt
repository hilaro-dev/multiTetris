package com.multitetris.network

import com.multitetris.game.ActivePiece
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.LockedCell
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.TetrominoType
import com.multitetris.network.webrtc.GameStatePacket
import com.multitetris.network.webrtc.GridCellCodec
import com.multitetris.network.webrtc.HandshakePacket
import com.multitetris.network.webrtc.InputPacket
import com.multitetris.network.webrtc.NetPacketCodec
import com.multitetris.network.webrtc.PendingIceCandidateQueue
import com.multitetris.network.webrtc.PieceState
import com.multitetris.network.webrtc.RematchRequestPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcPacketsAndQueueTest {

    @Test
    fun netPacketCodec_serializesAndDeserializesAllPacketTypes() {
        val packets = listOf(
            HandshakePacket(nickname = "HostNova", skinId = "neon_cyber"),
            InputPacket(seq = 42, action = PlayerAction.ROTATE_CW),
            RematchRequestPacket(requestedByPlayerId = 2, timestamp = 123_456L),
            GameStatePacket(
                tick = 99L,
                grid = List(20) { IntArray(10) { 0 } },
                activePieces = listOf(
                    PieceState(1, TetrominoType.I, 0, 2, 4, "classic", isPausedByPiece = false),
                    PieceState(2, TetrominoType.T, 1, 6, 5, "neon_cyber", isPausedByPiece = true)
                ),
                nextPieces = mapOf(1 to TetrominoType.L, 2 to TetrominoType.S),
                score = 1500,
                level = 3,
                mode = GameMode.VERSUS,
                gameOverWinnerId = null,
                sabotagedPlayerIds = setOf(2)
            )
        )

        for (original in packets) {
            val encoded = NetPacketCodec.encode(original)
            val decoded = NetPacketCodec.decode(encoded)
            assertNotNull("Failed to decode $encoded", decoded)
            when (original) {
                is HandshakePacket -> assertEquals(original, decoded)
                is InputPacket -> assertEquals(original, decoded)
                is RematchRequestPacket -> assertEquals(original, decoded)
                is GameStatePacket -> {
                    val decodedState = decoded as GameStatePacket
                    assertEquals(original.tick, decodedState.tick)
                    assertEquals(original.activePieces, decodedState.activePieces)
                    assertEquals(original.nextPieces, decodedState.nextPieces)
                    assertEquals(original.score, decodedState.score)
                    assertEquals(original.level, decodedState.level)
                    assertEquals(original.mode, decodedState.mode)
                    assertEquals(original.sabotagedPlayerIds, decodedState.sabotagedPlayerIds)
                }
            }
        }
    }

    @Test
    fun gameStatePacket_preservesBoardLockedCellsSkinsAndGhosts() {
        assertNull(GridCellCodec.decode(GridCellCodec.encode(null)))

        val grid = MutableList(20) { MutableList<LockedCell?>(10) { null } }
        grid[19][1] = LockedCell(playerId = 1, tetrominoType = TetrominoType.J, skinId = "classic")
        grid[19][6] = LockedCell(playerId = 2, tetrominoType = TetrominoType.Z, skinId = "neon_cyber")

        val p1 = ActivePiece(1, TetrominoType.O, 0, x = 2, y = 2, skinId = "classic", isPausedByPiece = false)
        val p2 = ActivePiece(2, TetrominoType.T, 2, x = 6, y = 3, skinId = "neon_cyber", isPausedByPiece = true)

        val now = 50_000L
        val hostState = GameState(
            config = BoardConfig(10, 20, 2),
            mode = GameMode.VERSUS,
            grid = grid.map { it.toList() },
            activePieces = mapOf(1 to p1, 2 to p2),
            nextPieces = mapOf(1 to TetrominoType.I, 2 to TetrominoType.O),
            playerSkins = mapOf(1 to "classic", 2 to "neon_cyber"),
            sharedScore = 800,
            sharedLines = 4,
            level = 1,
            playerScores = mapOf(1 to 500, 2 to 300),
            playerLines = mapOf(1 to 3, 2 to 1),
            sabotageBurstUntilMillis = mapOf(2 to now + 500L)
        )

        val packet = GameStatePacket.fromGameState(tick = 15L, state = hostState, nowMillis = now)
        val decodedPacket = NetPacketCodec.decode(NetPacketCodec.encode(packet)) as GameStatePacket
        val clientState = decodedPacket.toClientGameState(MultiTetrisEngine(), localClockMillis = now)

        assertEquals(LockedCell(1, TetrominoType.J, "classic"), clientState.grid[19][1])
        assertEquals(LockedCell(2, TetrominoType.Z, "neon_cyber"), clientState.grid[19][6])
        assertEquals(p1, clientState.activePieces[1])
        assertEquals(p2, clientState.activePieces[2])
        assertEquals(TetrominoType.I, clientState.nextPieces[1])
        assertEquals(TetrominoType.O, clientState.nextPieces[2])
        assertNotNull(clientState.ghostPieces[1])
        assertNotNull(clientState.ghostPieces[2])
        assertTrue(clientState.isSabotaged(2, now + 100L))
    }

    @Test
    fun gridCellCodec_roundTripsAllSixStage4Skins() {
        val allSkins = listOf(
            "classic",
            "neon_cyber",
            "golden_flex",
            "obsidian_magma",
            "hologram_glitch",
            "pixel_crystal"
        )
        for ((index, skinId) in allSkins.withIndex()) {
            val cell = LockedCell(
                playerId = (index % 3) + 1,
                tetrominoType = TetrominoType.entries[index % TetrominoType.entries.size],
                skinId = skinId
            )
            val decoded = GridCellCodec.decode(GridCellCodec.encode(cell))
            assertEquals(cell, decoded)
        }
    }

    @Test
    fun pendingIceCandidateQueue_buffersUntilRemoteDescriptionSetAndDeduplicates() {
        val queue = PendingIceCandidateQueue<String>()
        val dispatched = mutableListOf<String>()

        // Candidates arriving before setRemoteDescription must be buffered
        queue.enqueueOrDispatch("0:0:candA", "candA") { dispatched.add(it) }
        queue.enqueueOrDispatch("0:0:candA", "candA") { dispatched.add(it) } // duplicate ignored
        queue.enqueueOrDispatch("0:0:candB", "candB") { dispatched.add(it) }

        assertTrue(dispatched.isEmpty())
        assertEquals(2, queue.pendingCount())

        // Once setRemoteDescription completes, buffered candidates drain in FIFO order
        queue.markRemoteDescriptionSetAndDrain { dispatched.add(it) }
        assertEquals(listOf("candA", "candB"), dispatched)
        assertEquals(0, queue.pendingCount())

        // Candidates arriving afterwards dispatch immediately (and duplicates still ignored)
        queue.enqueueOrDispatch("0:0:candB", "candB") { dispatched.add(it) }
        queue.enqueueOrDispatch("0:0:candC", "candC") { dispatched.add(it) }
        assertEquals(listOf("candA", "candB", "candC"), dispatched)
    }
}
