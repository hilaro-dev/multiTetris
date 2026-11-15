package com.multitetris.network.webrtc

import android.content.Context
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Host-authoritative Star-Topology WebRTC P2P GameSessionController (Stage 8: 2-4 Players).
 *
 * - Host (Slot 1) runs the 60fps MultiTetrisEngine physics loop, applies local P1 inputs +
 *   incoming Guest InputPackets (Slots 2..4), and broadcasts GameStatePacket to all Guests at ~25Hz.
 * - Guests (Slots 2..4) transmit InputPackets directly to the Host and render incoming GameStatePackets.
 */
class WebRtcSessionController(
    context: Context,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : GameSessionController {

    val signalingClient = WebRtcSignalingClient(context = context.applicationContext)
    val signalState: StateFlow<WebRtcSignalState> = signalingClient.signalState
    val connectedPeerUids: StateFlow<Set<String>> = signalingClient.connectedPeerUids

    private val hostEngine = MultiTetrisEngine()
    private val guestGhostEngine = MultiTetrisEngine()
    private val engineLock = Any()

    private val lastDropTimeMs = ConcurrentHashMap<Int, Long>()
    private val tickCounter = AtomicLong(0L)
    private val inputSeqCounter = AtomicInteger(0)

    private var lastReceivedTick = -1L
    private var lastBroadcastTimeMs = 0L

    private var currentRoom: MatchRoom? = null
    private var myProfile: UserProfile = UserProfile()
    private val syncedSkins = ConcurrentHashMap<Int, String>()
    private val syncedNames = ConcurrentHashMap<Int, String>()

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
            updateRoomMetadata(room)
            return
        }

        stop()
        currentRoom = room
        myProfile = localUserProfile
        lastReceivedTick = -1L
        tickCounter.set(0L)
        inputSeqCounter.set(0)
        syncedSkins.clear()
        syncedNames.clear()

        syncMapsFromRoom(room)

        val myPlayerId = room.mySlotIndex.coerceIn(1, 4)
        val sessionMode = if (room.myRole == MatchRole.HOST) SessionMode.HOST else SessionMode.CLIENT

        _sessionState.value = NetworkSessionState(
            sessionMode = sessionMode,
            status = ConnectionStatus.CONNECTING,
            controlledPlayerId = myPlayerId,
            players = buildEndpointList(room),
            activeSabotagedPlayerIds = emptySet()
        )

        startSignalAndPacketObservers()
        signalingClient.connect(room)
    }

    fun updateRoomMetadata(room: MatchRoom) {
        if (currentRoom?.matchId != room.matchId) return
        currentRoom = room
        syncMapsFromRoom(room)

        val myPlayerId = room.mySlotIndex.coerceIn(1, 4)
        _sessionState.update { state ->
            state.copy(
                controlledPlayerId = myPlayerId,
                players = buildEndpointList(room)
            )
        }
        signalingClient.updateRoom(room)
    }

    private fun syncMapsFromRoom(room: MatchRoom) {
        val players = room.resolvedPlayers()
        for (slot in players) {
            val existingSkin = if (slot.uid == myProfile.uid && myProfile.selectedSkinId.isNotBlank()) {
                myProfile.selectedSkinId
            } else {
                slot.skinId.ifBlank { GameState.defaultSkinForPlayer(slot.slotIndex) }
            }
            syncedSkins[slot.slotIndex] = existingSkin
            val displayName = if (slot.uid == myProfile.uid && myProfile.nickname.isNotBlank()) {
                myProfile.nickname
            } else {
                slot.nickname.ifBlank { "Player ${slot.slotIndex}" }
            }
            syncedNames[slot.slotIndex] = displayName
        }
    }

    private fun buildEndpointList(room: MatchRoom): List<PlayerEndpointState> {
        val myUid = myProfile.uid
        return room.resolvedPlayers().map { slot ->
            PlayerEndpointState(
                playerId = slot.slotIndex,
                displayName = syncedNames[slot.slotIndex] ?: slot.nickname.ifBlank { "Player ${slot.slotIndex}" },
                isLocalController = (myUid.isNotBlank() && slot.uid == myUid) ||
                    (slot.slotIndex == room.mySlotIndex)
            )
        }
    }

    override fun startSession(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        config: BoardConfig,
        versusSubMode: VersusSubMode,
        playerTeams: Map<Int, TeamColor>
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
                currentTimeMillis = now,
                versusSubMode = versusSubMode,
                playerTeams = playerTeams
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
            val seq = inputSeqCounter.incrementAndGet()
            val mySlot = room.mySlotIndex.coerceIn(2, 4)
            signalingClient.sendPacket(InputPacket(seq = seq, action = action, playerId = mySlot))
        }
    }

    fun requestRematch() {
        val room = currentRoom ?: return
        if (_sessionState.value.status != ConnectionStatus.CONNECTED) return

        val players = room.resolvedPlayers()
        val playerCount = players.size.coerceIn(2, 4)
        if (room.myRole == MatchRole.HOST) {
            val teamsMap = players.associate { it.slotIndex to it.team }
            signalingClient.sendPacket(RematchRequestPacket(requestedByPlayerId = 1))
            startSession(
                mode = room.mode,
                playerSkins = syncedSkins.toMap(),
                config = BoardConfig.forPlayerCount(playerCount),
                versusSubMode = room.versusSubMode,
                playerTeams = teamsMap
            )
        } else {
            lastReceivedTick = -1L
            signalingClient.sendPacket(RematchRequestPacket(requestedByPlayerId = room.mySlotIndex))
        }
    }

    override fun switchControlledPlayer(playerId: Int) {
        // Locked to local player's assigned slot in multiplayer
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
            signalingClient.peerIncomingPackets.collect { incoming ->
                handleIncomingPeerPacket(incoming)
            }
        }
    }

    private fun onPeerConnected(room: MatchRoom) {
        currentRoom = room
        syncMapsFromRoom(room)
        val mySlot = room.mySlotIndex.coerceIn(1, 4)
        _sessionState.update {
            it.copy(
                status = ConnectionStatus.CONNECTED,
                controlledPlayerId = mySlot,
                players = buildEndpointList(room)
            )
        }

        val mySkin = syncedSkins[mySlot] ?: myProfile.selectedSkinId.ifBlank { "classic" }
        val myNick = syncedNames[mySlot] ?: myProfile.nickname.ifBlank { "Player $mySlot" }
        signalingClient.sendPacket(
            HandshakePacket(
                nickname = myNick,
                skinId = mySkin,
                uid = myProfile.uid,
                slotIndex = mySlot
            )
        )

        if (room.myRole == MatchRole.HOST) {
            val players = room.resolvedPlayers()
            val playerCount = players.size.coerceIn(2, 4)
            val teamsMap = players.associate { it.slotIndex to it.team }
            startSession(
                mode = room.mode,
                playerSkins = syncedSkins.toMap(),
                config = BoardConfig.forPlayerCount(playerCount),
                versusSubMode = room.versusSubMode,
                playerTeams = teamsMap
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

    private fun handleIncomingPeerPacket(incoming: IncomingPeerPacket) {
        val room = currentRoom ?: return
        val packet = incoming.packet
        when (packet) {
            is HandshakePacket -> {
                val resolvedSlot = if (room.myRole == MatchRole.HOST) {
                    room.resolvedPlayers().firstOrNull { it.uid == incoming.fromUid }?.slotIndex
                        ?: packet.slotIndex.takeIf { it in 2..4 }
                        ?: incoming.slotIndex.coerceIn(2, 4)
                } else {
                    1
                }
                syncedSkins[resolvedSlot] = packet.skinId
                if (packet.nickname.isNotBlank()) {
                    syncedNames[resolvedSlot] = packet.nickname
                }
                _sessionState.update { state ->
                    state.copy(
                        players = state.players.map { endpoint ->
                            if (endpoint.playerId == resolvedSlot) {
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
                        hostEngine.updatePlayerSkin(resolvedSlot, packet.skinId)
                    }
                    publishHostStateAndBroadcast(updated, now, forceSend = true)
                }
            }

            is InputPacket -> {
                if (room.myRole != MatchRole.HOST) return
                val targetSlot = room.resolvedPlayers()
                    .firstOrNull { it.uid == incoming.fromUid }
                    ?.slotIndex
                    ?: incoming.slotIndex.takeIf { it in 2..4 }
                    ?: packet.playerId.coerceIn(2, 4)
                val now = clockMillis()
                val updated = synchronized(engineLock) {
                    val next = hostEngine.applyAction(targetSlot, packet.action, now)
                    if (packet.action == PlayerAction.SOFT_DROP || packet.action == PlayerAction.HARD_DROP) {
                        lastDropTimeMs[targetSlot] = now
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

                if (packet.playerNames.isNotEmpty()) {
                    packet.playerNames.forEach { (pid, name) -> syncedNames[pid] = name }
                }
                val mySlot = room.mySlotIndex.coerceIn(2, 4)
                val refreshedEndpoints = (1..clientState.config.playerCount).map { pid ->
                    PlayerEndpointState(
                        playerId = pid,
                        displayName = syncedNames[pid] ?: "Player $pid",
                        isLocalController = pid == mySlot
                    )
                }
                _sessionState.update {
                    it.copy(
                        controlledPlayerId = mySlot,
                        players = refreshedEndpoints,
                        activeSabotagedPlayerIds = packet.sabotagedPlayerIds
                    )
                }
            }

            is RematchRequestPacket -> {
                if (room.myRole == MatchRole.HOST) {
                    val players = room.resolvedPlayers()
                    val playerCount = players.size.coerceIn(2, 4)
                    val teamsMap = players.associate { it.slotIndex to it.team }
                    startSession(
                        mode = room.mode,
                        playerSkins = syncedSkins.toMap(),
                        config = BoardConfig.forPlayerCount(playerCount),
                        versusSubMode = room.versusSubMode,
                        playerTeams = teamsMap
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
            val packet = GameStatePacket.fromGameState(
                tick = tick,
                state = state,
                nowMillis = now,
                playerNames = syncedNames.toMap()
            )
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
