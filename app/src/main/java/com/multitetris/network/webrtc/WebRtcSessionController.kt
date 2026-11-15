package com.multitetris.network.webrtc

import android.content.Context
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.network.ConnectionStatus
import com.multitetris.network.GameSessionController
import com.multitetris.network.NetworkSessionState
import com.multitetris.network.PlayerEndpointState
import com.multitetris.network.SessionMode
import com.multitetris.network.social.MatchRole
import com.multitetris.network.social.MatchRoom
import com.multitetris.network.social.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Host-authoritative WebRTC P2P GameSessionController for Stage 3.
 *
 * - Host (Player 1, Col 2) runs the 60fps MultiTetrisEngine physics loop,
 *   applies local P1 inputs + incoming Guest InputPackets for P2, and broadcasts
 *   GameStatePacket updates at ~25Hz or immediately on state mutation.
 * - Guest (Player 2, Col 6) runs zero independent gravity/collision simulation,
 *   transmits InputPackets immediately, and renders incoming GameStatePackets.
 */
class WebRtcSessionController(
    context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : GameSessionController {

    val signalingClient = WebRtcSignalingClient(context = context.applicationContext)
    val signalState: StateFlow<WebRtcSignalState> = signalingClient.signalState

    private val hostEngine = MultiTetrisEngine()
    private val guestGhostEngine = MultiTetrisEngine()
    private val engineLock = Any()

    private val lastDropTimeMs = mutableMapOf<Int, Long>()
    private val tickCounter = AtomicLong(0L)
    private val inputSeqCounter = AtomicInteger(0)

    private var lastReceivedTick = -1L
    private var lastBroadcastTimeMs = 0L

    private var currentRoom: MatchRoom? = null
    private var myProfile: UserProfile = UserProfile()
    private val syncedSkins = mutableMapOf(1 to "classic", 2 to "neon_cyber")

    private var observerJob: Job? = null
    private var packetJob: Job? = null
    private var hostLoopJob: Job? = null

    private val _sessionState = MutableStateFlow(NetworkSessionState())
    override val sessionState: StateFlow<NetworkSessionState> = _sessionState.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    override val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    fun connectRoom(room: MatchRoom, localUserProfile: UserProfile) {
        val currentSignal = signalingClient.signalState.value
        if (currentRoom?.matchId == room.matchId &&
            (currentSignal is WebRtcSignalState.Connecting || currentSignal is WebRtcSignalState.Connected)
        ) {
            return
        }

        stop()
        currentRoom = room
        myProfile = localUserProfile
        lastReceivedTick = -1L
        tickCounter.set(0L)
        inputSeqCounter.set(0)

        syncedSkins[1] = if (room.myRole == MatchRole.HOST) {
            localUserProfile.selectedSkinId.ifBlank { room.hostSkinId }
        } else {
            room.hostSkinId
        }
        syncedSkins[2] = if (room.myRole == MatchRole.GUEST) {
            localUserProfile.selectedSkinId.ifBlank { room.guestSkinId }
        } else {
            room.guestSkinId
        }

        val myPlayerId = if (room.myRole == MatchRole.HOST) 1 else 2
        val sessionMode = if (room.myRole == MatchRole.HOST) SessionMode.HOST else SessionMode.CLIENT

        _sessionState.value = NetworkSessionState(
            sessionMode = sessionMode,
            status = ConnectionStatus.CONNECTING,
            controlledPlayerId = myPlayerId,
            players = listOf(
                PlayerEndpointState(
                    playerId = 1,
                    displayName = room.hostNickname,
                    isLocalController = room.myRole == MatchRole.HOST
                ),
                PlayerEndpointState(
                    playerId = 2,
                    displayName = room.guestNickname,
                    isLocalController = room.myRole == MatchRole.GUEST
                )
            ),
            activeSabotagedPlayerIds = emptySet()
        )

        startSignalAndPacketObservers()
        signalingClient.connect(room)
    }

    fun updateRoomMetadata(room: MatchRoom) {
        if (currentRoom?.matchId != room.matchId) return
        currentRoom = room
        if (room.hostSkinId.isNotBlank()) {
            syncedSkins[1] = room.hostSkinId
        }
        if (room.guestSkinId.isNotBlank()) {
            syncedSkins[2] = room.guestSkinId
        }
        _sessionState.update { state ->
            state.copy(
                players = state.players.map { endpoint ->
                    when (endpoint.playerId) {
                        1 -> endpoint.copy(displayName = room.hostNickname)
                        2 -> endpoint.copy(displayName = room.guestNickname)
                        else -> endpoint
                    }
                }
            )
        }
        signalingClient.updateRoom(room)
    }

    override fun startSession(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        config: BoardConfig
    ) {
        val room = currentRoom ?: return
        if (room.myRole != MatchRole.HOST) return

        val now = clockMillis()
        val initial = synchronized(engineLock) {
            lastDropTimeMs.clear()
            for (zone in config.spawnZones) {
                lastDropTimeMs[zone.playerId] = now
            }
            tickCounter.set(0L)
            hostEngine.startNewGame(
                mode = mode,
                config = config,
                playerSkins = playerSkins,
                currentTimeMillis = now
            )
        }
        publishHostStateAndBroadcast(initial, now, forceSend = true)
        startHostPhysicsLoop()
    }

    override fun dispatchAction(playerId: Int, action: PlayerAction) {
        val room = currentRoom ?: return
        if (_sessionState.value.status != ConnectionStatus.CONNECTED) return

        if (room.myRole == MatchRole.HOST) {
            val now = clockMillis()
            val updated = synchronized(engineLock) {
                val next = hostEngine.applyAction(1, action, now)
                if (action == PlayerAction.SOFT_DROP || action == PlayerAction.HARD_DROP) {
                    lastDropTimeMs[1] = now
                }
                next
            }
            publishHostStateAndBroadcast(updated, now, forceSend = true)
        } else {
            // Guest transmits inputs immediately without running local physics
            val seq = inputSeqCounter.incrementAndGet()
            signalingClient.sendPacket(InputPacket(seq = seq, action = action))
        }
    }

    fun requestRematch() {
        val room = currentRoom ?: return
        if (_sessionState.value.status != ConnectionStatus.CONNECTED) return

        if (room.myRole == MatchRole.HOST) {
            signalingClient.sendPacket(RematchRequestPacket(requestedByPlayerId = 1))
            startSession(
                mode = room.mode,
                playerSkins = syncedSkins.toMap(),
                config = BoardConfig(width = 10, height = 20, playerCount = 2)
            )
        } else {
            lastReceivedTick = -1L
            signalingClient.sendPacket(RematchRequestPacket(requestedByPlayerId = 2))
        }
    }

    override fun switchControlledPlayer(playerId: Int) {
        // Locked to local player's assigned role in multiplayer
    }

    override fun togglePause() {
        val room = currentRoom ?: return
        if (room.myRole != MatchRole.HOST) return
        val now = clockMillis()
        val updated = synchronized(engineLock) {
            val nextPaused = !hostEngine.state.isPaused
            if (!nextPaused) {
                hostEngine.state.activePieces.keys.forEach { id ->
                    lastDropTimeMs[id] = now
                }
            }
            hostEngine.setPaused(nextPaused)
        }
        publishHostStateAndBroadcast(updated, now, forceSend = true)
    }

    override fun setPlayerSkin(playerId: Int, skinId: String) {
        syncedSkins[playerId] = skinId
        if (currentRoom?.myRole == MatchRole.HOST) {
            val now = clockMillis()
            val updated = synchronized(engineLock) {
                hostEngine.updatePlayerSkin(playerId, skinId)
            }
            publishHostStateAndBroadcast(updated, now, forceSend = true)
        }
    }

    override fun stop() {
        hostLoopJob?.cancel()
        hostLoopJob = null
        observerJob?.cancel()
        observerJob = null
        packetJob?.cancel()
        packetJob = null
        signalingClient.close()
        currentRoom = null
    }

    fun dispose() {
        stop()
        signalingClient.dispose()
    }

    private fun startSignalAndPacketObservers() {
        observerJob?.cancel()
        observerJob = scope.launch {
            signalingClient.signalState.collect { signal ->
                when (signal) {
                    is WebRtcSignalState.Connected -> onPeerConnected(signal.room)
                    is WebRtcSignalState.Disconnected -> onPeerDisconnected()
                    is WebRtcSignalState.TimeoutError -> {
                        hostLoopJob?.cancel()
                        hostLoopJob = null
                        _sessionState.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
                    }

                    else -> Unit
                }
            }
        }

        packetJob?.cancel()
        packetJob = scope.launch {
            signalingClient.incomingPackets.collect { packet ->
                handleIncomingPacket(packet)
            }
        }
    }

    private fun onPeerConnected(room: MatchRoom) {
        _sessionState.update { it.copy(status = ConnectionStatus.CONNECTED) }

        val mySkin = if (room.myRole == MatchRole.HOST) {
            syncedSkins[1] ?: myProfile.selectedSkinId
        } else {
            syncedSkins[2] ?: myProfile.selectedSkinId
        }
        val myNick = myProfile.nickname.ifBlank {
            if (room.myRole == MatchRole.HOST) room.hostNickname else room.guestNickname
        }
        signalingClient.sendPacket(HandshakePacket(nickname = myNick, skinId = mySkin))

        if (room.myRole == MatchRole.HOST) {
            startSession(
                mode = room.mode,
                playerSkins = syncedSkins.toMap(),
                config = BoardConfig(width = 10, height = 20, playerCount = 2)
            )
        }
    }

    private fun onPeerDisconnected() {
        hostLoopJob?.cancel()
        hostLoopJob = null

        if (currentRoom?.myRole == MatchRole.HOST) {
            synchronized(engineLock) {
                hostEngine.setPaused(true)
            }
            _gameState.value = hostEngine.state
        } else {
            _gameState.update { it.copy(isPaused = true) }
        }

        _sessionState.update { it.copy(status = ConnectionStatus.DISCONNECTED) }
    }

    private fun handleIncomingPacket(packet: NetPacket) {
        val room = currentRoom ?: return
        when (packet) {
            is HandshakePacket -> {
                val opponentPlayerId = if (room.myRole == MatchRole.HOST) 2 else 1
                syncedSkins[opponentPlayerId] = packet.skinId
                _sessionState.update { state ->
                    state.copy(
                        players = state.players.map { endpoint ->
                            if (endpoint.playerId == opponentPlayerId) {
                                endpoint.copy(displayName = packet.nickname.ifBlank { endpoint.displayName })
                            } else {
                                endpoint
                            }
                        }
                    )
                }
                if (room.myRole == MatchRole.HOST) {
                    val now = clockMillis()
                    val updated = synchronized(engineLock) {
                        hostEngine.updatePlayerSkin(opponentPlayerId, packet.skinId)
                    }
                    publishHostStateAndBroadcast(updated, now, forceSend = true)
                }
            }

            is InputPacket -> {
                if (room.myRole != MatchRole.HOST) return
                val now = clockMillis()
                val updated = synchronized(engineLock) {
                    val next = hostEngine.applyAction(2, packet.action, now)
                    if (packet.action == PlayerAction.SOFT_DROP || packet.action == PlayerAction.HARD_DROP) {
                        lastDropTimeMs[2] = now
                    }
                    next
                }
                publishHostStateAndBroadcast(updated, now, forceSend = true)
            }

            is GameStatePacket -> {
                if (room.myRole != MatchRole.GUEST) return
                if (packet.tick <= lastReceivedTick && packet.tick > 2L) return
                lastReceivedTick = packet.tick

                val now = clockMillis()
                val clientState = packet.toClientGameState(
                    ghostCalculator = guestGhostEngine,
                    localClockMillis = now
                )
                _gameState.value = clientState
                _sessionState.update {
                    it.copy(activeSabotagedPlayerIds = packet.sabotagedPlayerIds)
                }
            }

            is RematchRequestPacket -> {
                if (room.myRole == MatchRole.HOST) {
                    startSession(
                        mode = room.mode,
                        playerSkins = syncedSkins.toMap(),
                        config = BoardConfig(width = 10, height = 20, playerCount = 2)
                    )
                } else {
                    lastReceivedTick = -1L
                }
            }
        }
    }

    private fun startHostPhysicsLoop() {
        hostLoopJob?.cancel()
        hostLoopJob = scope.launch {
            while (isActive) {
                delay(HOST_FRAME_INTERVAL_MS)
                if (_sessionState.value.status != ConnectionStatus.CONNECTED) continue

                val now = clockMillis()
                var stateMutated = false
                val latestState = synchronized(engineLock) {
                    val current = hostEngine.state
                    if (current.isPaused || current.isGameOver) {
                        current
                    } else {
                        val orderedPlayers = current.activePieces.values
                            .sortedByDescending { it.y }
                            .map { it.playerId }

                        var workingState = current
                        for (playerId in orderedPlayers) {
                            val lastDrop = lastDropTimeMs[playerId] ?: now
                            val interval = hostEngine.getDropIntervalMs(playerId, now)
                            if (now - lastDrop >= interval) {
                                lastDropTimeMs[playerId] = now
                                workingState = hostEngine.stepPlayerDown(playerId, now)
                                stateMutated = true
                            }
                        }
                        workingState
                    }
                }

                val shouldHeartbeatBroadcast = (now - lastBroadcastTimeMs) >= SYNC_BROADCAST_INTERVAL_MS
                if (stateMutated || shouldHeartbeatBroadcast) {
                    publishHostStateAndBroadcast(
                        state = latestState,
                        now = now,
                        forceSend = stateMutated || shouldHeartbeatBroadcast
                    )
                }
            }
        }
    }

    private fun publishHostStateAndBroadcast(
        state: GameState,
        now: Long,
        forceSend: Boolean
    ) {
        _gameState.value = state
        val sabotaged = state.activePieces.keys
            .filter { id -> state.isSabotaged(id, now) }
            .toSet()

        if (_sessionState.value.activeSabotagedPlayerIds != sabotaged) {
            _sessionState.update { it.copy(activeSabotagedPlayerIds = sabotaged) }
        }

        if (forceSend) {
            lastBroadcastTimeMs = now
            val tick = tickCounter.incrementAndGet()
            val packet = GameStatePacket.fromGameState(tick = tick, state = state, nowMillis = now)
            signalingClient.sendPacket(packet)
        }
    }

    companion object {
        private const val HOST_FRAME_INTERVAL_MS = 16L // 60fps physics loop
        private const val SYNC_BROADCAST_INTERVAL_MS = 40L // ~25Hz steady network sync
    }
}

/**
 * Process-wide singleton holder for WebRtcSessionController (and its WebRtcSignalingClient,
 * PeerConnectionFactory, PeerConnection, and DataChannel) so navigation transitions
 * never recreate or prematurely dispose an active P2P handshake.
 */
object MultiplayerSessionManager {
    @Volatile
    private var instance: WebRtcSessionController? = null

    fun getInstance(context: Context): WebRtcSessionController {
        return instance ?: synchronized(this) {
            instance ?: WebRtcSessionController(context.applicationContext).also { created ->
                instance = created
            }
        }
    }
}
