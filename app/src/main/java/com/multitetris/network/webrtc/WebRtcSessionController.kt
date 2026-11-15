package com.multitetris.network.webrtc

import android.content.Context
import com.multitetris.game.BoardConfig
import com.multitetris.game.BotDifficulty
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.TeamColor
import com.multitetris.game.TetrisBotController
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
    private val syncedSabotages = ConcurrentHashMap<Int, List<com.multitetris.game.SabotageType>>()
    private val activeUidsBySlot = ConcurrentHashMap<Int, String>()
    private var botDifficulty: BotDifficulty = BotDifficulty.NORMAL
    private val botControllers = ConcurrentHashMap<Int, TetrisBotController>()
    private val lastBotActionTimeMs = ConcurrentHashMap<Int, Long>()

    fun setBotDifficulty(difficulty: BotDifficulty) {
        botDifficulty = difficulty
    }

    private var observerJob: Job? = null
    private var packetJob: Job? = null
    private var peerLeftJob: Job? = null
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
        activeUidsBySlot.clear()

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
            syncedSabotages[slot.slotIndex] = com.multitetris.game.SabotageConfig.resolveLoadout(slot.selectedSabotages)
            if (slot.uid.isNotBlank()) {
                activeUidsBySlot[slot.slotIndex] = slot.uid
            }
        }
    }

    private fun buildEndpointList(room: MatchRoom): List<PlayerEndpointState> {
        val myUid = myProfile.uid
        return room.resolvedPlayers().map { slot ->
            PlayerEndpointState(
                playerId = slot.slotIndex,
                displayName = syncedNames[slot.slotIndex] ?: slot.nickname.ifBlank { "Player ${slot.slotIndex}" },
                isLocalController = (myUid.isNotBlank() && slot.uid == myUid) ||
                    (slot.slotIndex == room.mySlotIndex),
                uid = slot.uid
            )
        }
    }

    override fun startSession(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        config: BoardConfig,
        versusSubMode: VersusSubMode,
        playerTeams: Map<Int, TeamColor>,
        sabotageEnabled: Boolean,
        equippedSabotages: Map<Int, List<com.multitetris.game.SabotageType>>,
        mysteryEnabled: Boolean
    ) {
        val room = currentRoom ?: return
        if (room.myRole != MatchRole.HOST) return

        val now = clockMillis()
        val resolvedEquipped = if (equippedSabotages.isNotEmpty()) {
            equippedSabotages
        } else {
            syncedSabotages.toMap()
        }
        val initial = synchronized(engineLock) {
            lastDropTimeMs.clear()
            botControllers.clear()
            lastBotActionTimeMs.clear()
            for (zone in config.spawnZones) {
                lastDropTimeMs[zone.playerId] = now
            }
            for (slot in room.resolvedPlayers()) {
                if (slot.isBot || slot.uid.startsWith("bot_p")) {
                    botControllers[slot.slotIndex] = TetrisBotController(
                        botPlayerId = slot.slotIndex,
                        difficulty = botDifficulty
                    )
                    lastBotActionTimeMs[slot.slotIndex] = now
                }
            }
            tickCounter.set(0L)
            hostEngine.startNewGame(
                mode = mode,
                config = config,
                playerSkins = playerSkins,
                currentTimeMillis = now,
                versusSubMode = versusSubMode,
                playerTeams = playerTeams,
                sabotageEnabled = sabotageEnabled,
                equippedSabotages = resolvedEquipped,
                mysteryEnabled = mysteryEnabled
            )
        }
        val nonHostPlayers = room.resolvedPlayers().filter { it.uid != room.hostUid && it.uid.isNotBlank() }
        if (nonHostPlayers.isNotEmpty() && nonHostPlayers.all { it.isBot || it.uid.startsWith("bot_p") }) {
            _sessionState.update { it.copy(status = ConnectionStatus.CONNECTED) }
        }
        publishHostStateAndBroadcast(initial, now, forceSend = true)
        startHostPhysicsLoop()
    }

    override fun triggerSabotage(
        attackerPlayerId: Int,
        targetPlayerId: Int,
        sabotageType: com.multitetris.game.SabotageType
    ) {
        val room = currentRoom ?: return
        if (_sessionState.value.status != ConnectionStatus.CONNECTED) return

        if (room.myRole == MatchRole.HOST) {
            val now = clockMillis()
            val updated = synchronized(engineLock) {
                hostEngine.useSabotage(
                    attackerPlayerId = 1,
                    targetPlayerId = targetPlayerId,
                    sabotageType = sabotageType,
                    attackerLabel = syncedNames[1]
                )
            }
            publishHostStateAndBroadcast(updated, now, forceSend = true)
        } else {
            val targetUid = room.resolvedPlayers()
                .firstOrNull { it.slotIndex == targetPlayerId }
                ?.uid
                .orEmpty()
            signalingClient.sendPacket(
                UseSabotagePacket(
                    attackerUid = myProfile.uid,
                    targetUid = targetUid,
                    sabotageType = sabotageType,
                    attackerPlayerId = room.mySlotIndex,
                    targetPlayerId = targetPlayerId
                )
            )
        }
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

        if (room.myRole == MatchRole.HOST) {
            startRematchFromConnectedPlayers()
        } else {
            lastReceivedTick = -1L
            signalingClient.sendPacket(RematchRequestPacket(requestedByPlayerId = room.mySlotIndex))
        }
    }

    private fun startRematchFromConnectedPlayers() {
        val room = currentRoom ?: return
        if (room.myRole != MatchRole.HOST) return

        val disconnectedSlots = hostEngine.state.disconnectedPlayerIds +
            _sessionState.value.players.filter { it.isDisconnected }.map { it.playerId }
        val connectedEndpoints = _sessionState.value.players.filter { it.playerId !in disconnectedSlots }
        val resolvedByUid = room.resolvedPlayers().associateBy { it.uid }
        val resolvedBySlot = room.resolvedPlayers().associateBy { it.slotIndex }

        val survivingSlots = if (connectedEndpoints.isNotEmpty()) {
            connectedEndpoints.sortedBy { it.playerId }.mapNotNull { ep ->
                resolvedByUid[ep.uid] ?: resolvedBySlot[ep.playerId]
            }
        } else {
            room.resolvedPlayers().filter { it.slotIndex !in disconnectedSlots }
        }

        if (survivingSlots.size < 2) return

        val remappedPlayers = survivingSlots.mapIndexed { index, slot ->
            slot.copy(slotIndex = index + 1)
        }
        val oldToNewSlot = survivingSlots.mapIndexed { index, slot ->
            slot.slotIndex to (index + 1)
        }.toMap()

        val newSkins = mutableMapOf<Int, String>()
        val newNames = mutableMapOf<Int, String>()
        val newSabotages = mutableMapOf<Int, List<com.multitetris.game.SabotageType>>()
        val newUids = mutableMapOf<Int, String>()
        val newTeams = mutableMapOf<Int, TeamColor>()

        for ((oldSlot, newSlot) in oldToNewSlot) {
            val playerObj = remappedPlayers.first { it.slotIndex == newSlot }
            newSkins[newSlot] = syncedSkins[oldSlot] ?: playerObj.skinId.ifBlank { "classic" }
            newNames[newSlot] = syncedNames[oldSlot] ?: playerObj.nickname.ifBlank { "Player $newSlot" }
            newSabotages[newSlot] = syncedSabotages[oldSlot]
                ?: com.multitetris.game.SabotageConfig.resolveLoadout(playerObj.selectedSabotages)
            if (playerObj.uid.isNotBlank()) {
                newUids[newSlot] = playerObj.uid
            }
            newTeams[newSlot] = playerObj.team
        }

        syncedSkins.clear()
        syncedSkins.putAll(newSkins)
        syncedNames.clear()
        syncedNames.putAll(newNames)
        syncedSabotages.clear()
        syncedSabotages.putAll(newSabotages)
        activeUidsBySlot.clear()
        activeUidsBySlot.putAll(newUids)

        val updatedRoom = room.copy(players = remappedPlayers)
        currentRoom = updatedRoom
        _sessionState.update { session ->
            session.copy(
                controlledPlayerId = 1,
                players = buildEndpointList(updatedRoom),
                activeSabotagedPlayerIds = emptySet()
            )
        }

        signalingClient.sendPacket(RematchRequestPacket(requestedByPlayerId = 1))
        startSession(
            mode = updatedRoom.mode,
            playerSkins = newSkins,
            config = BoardConfig.forPlayerCount(remappedPlayers.size.coerceIn(2, 4)),
            versusSubMode = updatedRoom.versusSubMode,
            playerTeams = newTeams,
            sabotageEnabled = updatedRoom.sabotageEnabled,
            equippedSabotages = newSabotages,
            mysteryEnabled = updatedRoom.mysteryEnabled
        )
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
        peerLeftJob?.cancel()
        peerLeftJob = null
        signalingClient.close()
        botControllers.clear()
        lastBotActionTimeMs.clear()
        currentRoom = null
        _gameState.value = GameState()
        _sessionState.value = NetworkSessionState()
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
                    is WebRtcSignalState.Disconnected -> onPeerDisconnected(signal.reason)
                    is WebRtcSignalState.TimeoutError -> {
                        hostLoopJob?.cancel()
                        hostLoopJob = null
                        _sessionState.update {
                            it.copy(
                                status = ConnectionStatus.DISCONNECTED,
                                disconnectMessage = signal.message
                            )
                        }
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

        peerLeftJob?.cancel()
        peerLeftJob = scope.launch {
            signalingClient.peerLeftEvents.collect { event ->
                val room = currentRoom ?: return@collect
                if (room.myRole != MatchRole.HOST) return@collect
                val slotIndex = room.resolvedPlayers()
                    .firstOrNull { it.uid == event.uid }
                    ?.slotIndex
                    ?: event.slotIndex
                val displayName = syncedNames[slotIndex]
                    ?: _sessionState.value.players.firstOrNull { it.playerId == slotIndex }?.displayName
                _sessionState.update { session ->
                    session.copy(
                        players = session.players.map { endpoint ->
                            if (endpoint.playerId == slotIndex) {
                                endpoint.copy(isDisconnected = true)
                            } else {
                                endpoint
                            }
                        }
                    )
                }
                val now = clockMillis()
                val updated = synchronized(engineLock) {
                    hostEngine.markPlayerDisconnected(slotIndex, displayName)
                }
                publishHostStateAndBroadcast(updated, now, forceSend = true)
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
                players = buildEndpointList(room),
                disconnectMessage = null
            )
        }

        val mySkin = syncedSkins[mySlot] ?: myProfile.selectedSkinId.ifBlank { "classic" }
        val myNick = syncedNames[mySlot] ?: myProfile.nickname.ifBlank { "Player $mySlot" }
        val mySabotages = room.resolvedPlayers()
            .firstOrNull { it.slotIndex == mySlot }
            ?.selectedSabotages
            ?.let { com.multitetris.game.SabotageConfig.resolveLoadout(it) }
            ?: com.multitetris.game.SabotageConfig.defaultLoadout
        syncedSabotages[mySlot] = mySabotages
        signalingClient.sendPacket(
            HandshakePacket(
                nickname = myNick,
                skinId = mySkin,
                uid = myProfile.uid,
                slotIndex = mySlot,
                selectedSabotages = mySabotages
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
                playerTeams = teamsMap,
                sabotageEnabled = room.sabotageEnabled,
                equippedSabotages = syncedSabotages.toMap(),
                mysteryEnabled = room.mysteryEnabled
            )
        }
    }

    private fun onPeerDisconnected(reason: String? = null) {
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

        _sessionState.update {
            it.copy(
                status = ConnectionStatus.DISCONNECTED,
                disconnectMessage = reason ?: if (currentRoom?.myRole == MatchRole.GUEST) {
                    "Host left or disconnected."
                } else {
                    "All opponents left the match."
                }
            )
        }
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
                if (packet.selectedSabotages.isNotEmpty()) {
                    syncedSabotages[resolvedSlot] = com.multitetris.game.SabotageConfig.resolveLoadout(packet.selectedSabotages)
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
                val targetSlot = activeUidsBySlot.entries.firstOrNull { it.value == incoming.fromUid }?.key
                    ?: room.resolvedPlayers()
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

            is UseSabotagePacket -> {
                if (room.myRole != MatchRole.HOST) return
                val attackerSlot = activeUidsBySlot.entries.firstOrNull {
                    it.value == packet.attackerUid || it.value == incoming.fromUid
                }?.key
                    ?: room.resolvedPlayers()
                        .firstOrNull { it.uid == packet.attackerUid || it.uid == incoming.fromUid }
                        ?.slotIndex
                    ?: incoming.slotIndex.takeIf { it in 2..4 }
                    ?: packet.attackerPlayerId.coerceIn(2, 4)
                val targetSlot = if (packet.targetUid.isNotBlank()) {
                    activeUidsBySlot.entries.firstOrNull { it.value == packet.targetUid }?.key
                        ?: room.resolvedPlayers().firstOrNull { it.uid == packet.targetUid }?.slotIndex
                        ?: packet.targetPlayerId
                } else {
                    packet.targetPlayerId
                }
                val now = clockMillis()
                val updated = synchronized(engineLock) {
                    hostEngine.useSabotage(
                        attackerPlayerId = attackerSlot,
                        targetPlayerId = targetSlot,
                        sabotageType = packet.sabotageType,
                        attackerLabel = syncedNames[attackerSlot]
                    )
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
                val mySlot = packet.playerUids.entries
                    .firstOrNull { it.value == myProfile.uid && myProfile.uid.isNotBlank() }
                    ?.key
                    ?: room.mySlotIndex.coerceIn(2, 4)
                val roomPlayers = room.resolvedPlayers()
                val refreshedEndpoints = (1..clientState.config.playerCount).map { pid ->
                    PlayerEndpointState(
                        playerId = pid,
                        displayName = syncedNames[pid] ?: "Player $pid",
                        isLocalController = pid == mySlot,
                        isDisconnected = pid in packet.disconnectedPlayerIds,
                        uid = packet.playerUids[pid]
                            ?: roomPlayers.firstOrNull { it.slotIndex == pid }?.uid.orEmpty()
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
                    startRematchFromConnectedPlayers()
                } else {
                    lastReceivedTick = -1L
                }
            }
        }
    }

    private fun startHostPhysicsLoop() {
        hostLoopJob?.cancel()
        hostLoopJob = scope.launch {
            var lastTickMs = clockMillis()
            while (isActive) {
                delay(HOST_FRAME_INTERVAL_MS)
                if (_sessionState.value.status != ConnectionStatus.CONNECTED) {
                    lastTickMs = clockMillis()
                    continue
                }

                val now = clockMillis()
                val deltaMs = (now - lastTickMs).coerceIn(0L, 250L)
                lastTickMs = now
                var stateMutated = false
                val latestState = synchronized(engineLock) {
                    val current = hostEngine.state
                    if (current.isPaused || current.isGameOver) {
                        current
                    } else {
                        var workingState = hostEngine.tickSabotageAndCoopGlitch(deltaMs)
                        workingState = hostEngine.tickMysteryPieceTimers(deltaMs)
                        if (workingState != current) {
                            stateMutated = true
                        }
                        val orderedPlayers = workingState.activePieces.values
                            .sortedByDescending { it.y }
                            .map { it.playerId }

                        for (playerId in orderedPlayers) {
                            val lastDrop = lastDropTimeMs[playerId] ?: now
                            val interval = hostEngine.getDropIntervalMs(playerId, now)
                            if (now - lastDrop >= interval) {
                                lastDropTimeMs[playerId] = now
                                workingState = hostEngine.stepPlayerDown(playerId, now)
                                stateMutated = true
                            }
                        }

                        // Host-driven AI ticks for any Bot slots in the room
                        for ((botPlayerId, botController) in botControllers) {
                            val lastBotAction = lastBotActionTimeMs[botPlayerId] ?: 0L
                            if (now - lastBotAction >= botDifficulty.stepDelayMs) {
                                lastBotActionTimeMs[botPlayerId] = now
                                val sabChoice = botController.maybeSelectSabotage(workingState, now)
                                if (sabChoice != null) {
                                    workingState = hostEngine.useSabotage(
                                        attackerPlayerId = botPlayerId,
                                        targetPlayerId = sabChoice.first,
                                        sabotageType = sabChoice.second,
                                        attackerLabel = syncedNames[botPlayerId]
                                    )
                                    stateMutated = true
                                }
                                val botAction = botController.nextAction(workingState)
                                if (botAction != null) {
                                    workingState = hostEngine.applyAction(botPlayerId, botAction, now)
                                    if (botAction == PlayerAction.SOFT_DROP || botAction == PlayerAction.HARD_DROP) {
                                        lastDropTimeMs[botPlayerId] = now
                                    }
                                    stateMutated = true
                                }
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

        val needsDisconnectedSync = _sessionState.value.players.any {
            (it.playerId in state.disconnectedPlayerIds) != it.isDisconnected
        }
        if (_sessionState.value.activeSabotagedPlayerIds != sabotaged || needsDisconnectedSync) {
            _sessionState.update { session ->
                session.copy(
                    activeSabotagedPlayerIds = sabotaged,
                    players = session.players.map { endpoint ->
                        endpoint.copy(isDisconnected = endpoint.playerId in state.disconnectedPlayerIds)
                    }
                )
            }
        }

        if (forceSend) {
            lastBroadcastTimeMs = now
            val tick = tickCounter.incrementAndGet()
            val packet = GameStatePacket.fromGameState(
                tick = tick,
                state = state,
                nowMillis = now,
                playerNames = syncedNames.toMap(),
                playerUids = activeUidsBySlot.toMap()
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
