package com.multitetris.network

import com.multitetris.game.BoardConfig
import com.multitetris.game.BotDifficulty
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.SabotageType
import com.multitetris.game.TeamColor
import com.multitetris.game.TetrisBotController
import com.multitetris.game.VersusSubMode
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

/**
 * Local 2-Player Simulation controller for Stage 1.
 * Drives independent gravity timers per player (supporting per-player Sabotage Push bursts)
 * while allowing the local user to switch control focus between Player 1 and Player 2.
 */
class LocalSimulationSessionController(
    private val engine: MultiTetrisEngine = MultiTetrisEngine(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : GameSessionController {

    private val engineLock = Any()
    private val lastDropTimeMs = mutableMapOf<Int, Long>()
    private var tickerJob: Job? = null
    private var botJob: Job? = null
    private var botController: TetrisBotController? = null

    private val _sessionState = MutableStateFlow(NetworkSessionState())
    override val sessionState: StateFlow<NetworkSessionState> = _sessionState.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    override val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    override fun startSession(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        config: BoardConfig,
        versusSubMode: VersusSubMode,
        playerTeams: Map<Int, TeamColor>,
        sabotageEnabled: Boolean,
        equippedSabotages: Map<Int, List<SabotageType>>,
        mysteryEnabled: Boolean
    ) {
        stop()
        val now = clockMillis()
        val initialState = synchronized(engineLock) {
            lastDropTimeMs.clear()
            for (zone in config.spawnZones) {
                lastDropTimeMs[zone.playerId] = now
            }
            engine.startNewGame(
                mode = mode,
                config = config,
                playerSkins = playerSkins,
                currentTimeMillis = now,
                versusSubMode = versusSubMode,
                playerTeams = playerTeams,
                sabotageEnabled = sabotageEnabled,
                equippedSabotages = equippedSabotages,
                mysteryEnabled = mysteryEnabled
            )
        }

        val playerEndpoints = config.spawnZones.map { zone ->
            PlayerEndpointState(
                playerId = zone.playerId,
                displayName = "Player ${zone.playerId}",
                isLocalController = true,
                latencyMs = 0
            )
        }

        _sessionState.update { current ->
            val focusedId = if (config.spawnZones.any { it.playerId == current.controlledPlayerId }) {
                current.controlledPlayerId
            } else {
                config.spawnZones.first().playerId
            }
            current.copy(
                sessionMode = SessionMode.LOCAL_SIMULATION,
                status = ConnectionStatus.READY_LOCAL,
                controlledPlayerId = focusedId,
                players = playerEndpoints,
                activeSabotagedPlayerIds = emptySet()
            )
        }
        _gameState.value = initialState
        startGravityTicker()
    }

    fun startSingleplayerVsBot(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        sabotageEnabled: Boolean,
        equippedSabotages: Map<Int, List<SabotageType>>,
        mysteryEnabled: Boolean,
        botDifficulty: BotDifficulty,
        humanNickname: String
    ) {
        stop()
        val config = if (mode == GameMode.SEPARATE) {
            BoardConfig.forSeparateBoards(2)
        } else {
            BoardConfig.forPlayerCount(2)
        }
        val now = clockMillis()
        val initialState = synchronized(engineLock) {
            lastDropTimeMs.clear()
            for (zone in config.spawnZones) {
                lastDropTimeMs[zone.playerId] = now
            }
            engine.startNewGame(
                mode = mode,
                config = config,
                playerSkins = playerSkins,
                currentTimeMillis = now,
                versusSubMode = VersusSubMode.FREE_FOR_ALL,
                playerTeams = emptyMap(),
                sabotageEnabled = sabotageEnabled,
                equippedSabotages = equippedSabotages,
                mysteryEnabled = mysteryEnabled
            )
        }

        val playerEndpoints = listOf(
            PlayerEndpointState(
                playerId = 1,
                displayName = humanNickname.ifBlank { "You" },
                isLocalController = true,
                latencyMs = 0
            ),
            PlayerEndpointState(
                playerId = 2,
                displayName = "Bot",
                isLocalController = false,
                latencyMs = 0
            )
        )

        _sessionState.update {
            NetworkSessionState(
                sessionMode = SessionMode.LOCAL_SIMULATION,
                status = ConnectionStatus.READY_LOCAL,
                controlledPlayerId = 1,
                players = playerEndpoints,
                activeSabotagedPlayerIds = emptySet()
            )
        }
        _gameState.value = initialState
        val controller = TetrisBotController(botPlayerId = 2, difficulty = botDifficulty)
        botController = controller
        startGravityTicker()
        startBotTicker(controller, botDifficulty)
    }

    fun startSingleplayerSolo(
        playerSkinId: String,
        sabotageEnabled: Boolean,
        mysteryEnabled: Boolean,
        humanNickname: String
    ) {
        stop()
        val config = BoardConfig.forPlayerCount(1)
        val now = clockMillis()
        val initialState = synchronized(engineLock) {
            lastDropTimeMs.clear()
            for (zone in config.spawnZones) {
                lastDropTimeMs[zone.playerId] = now
            }
            engine.startNewGame(
                mode = GameMode.CO_OP,
                config = config,
                playerSkins = mapOf(1 to playerSkinId),
                currentTimeMillis = now,
                versusSubMode = VersusSubMode.FREE_FOR_ALL,
                playerTeams = emptyMap(),
                sabotageEnabled = sabotageEnabled,
                equippedSabotages = emptyMap(),
                mysteryEnabled = mysteryEnabled
            )
        }

        val playerEndpoints = listOf(
            PlayerEndpointState(
                playerId = 1,
                displayName = humanNickname.ifBlank { "You" },
                isLocalController = true,
                latencyMs = 0
            )
        )

        _sessionState.update {
            NetworkSessionState(
                sessionMode = SessionMode.LOCAL_SIMULATION,
                status = ConnectionStatus.READY_LOCAL,
                controlledPlayerId = 1,
                players = playerEndpoints,
                activeSabotagedPlayerIds = emptySet()
            )
        }
        _gameState.value = initialState
        botController = null
        startGravityTicker()
    }

    override fun dispatchAction(playerId: Int, action: PlayerAction) {
        val now = clockMillis()
        val updated = synchronized(engineLock) {
            val next = engine.applyAction(playerId, action, now)
            if (action == PlayerAction.SOFT_DROP || action == PlayerAction.HARD_DROP) {
                lastDropTimeMs[playerId] = now
            }
            next
        }
        publishState(updated, now)
    }

    override fun triggerSabotage(attackerPlayerId: Int, targetPlayerId: Int, sabotageType: SabotageType) {
        val now = clockMillis()
        val updated = synchronized(engineLock) {
            engine.useSabotage(
                attackerPlayerId = attackerPlayerId,
                targetPlayerId = targetPlayerId,
                sabotageType = sabotageType,
                attackerLabel = "Player $attackerPlayerId"
            )
        }
        publishState(updated, now)
    }

    override fun switchControlledPlayer(playerId: Int) {
        _sessionState.update { current ->
            val target = current.players.firstOrNull { it.playerId == playerId }
            if (target != null && target.isLocalController) {
                current.copy(controlledPlayerId = playerId)
            } else {
                current
            }
        }
    }

    override fun togglePause() {
        val now = clockMillis()
        val updated = synchronized(engineLock) {
            val nextPaused = !engine.state.isPaused
            if (!nextPaused) {
                // Reset drop timers on resume so pieces don't jump immediately.
                engine.state.activePieces.keys.forEach { playerId ->
                    lastDropTimeMs[playerId] = now
                }
            }
            engine.setPaused(nextPaused)
        }
        publishState(updated, now)
    }

    override fun setPlayerSkin(playerId: Int, skinId: String) {
        val now = clockMillis()
        val updated = synchronized(engineLock) {
            engine.updatePlayerSkin(playerId, skinId)
        }
        publishState(updated, now)
    }

    override fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        botJob?.cancel()
        botJob = null
        botController = null
        _gameState.value = GameState()
        _sessionState.value = NetworkSessionState()
    }

    private fun startBotTicker(controller: TetrisBotController, difficulty: BotDifficulty) {
        botJob?.cancel()
        botJob = scope.launch {
            while (isActive) {
                delay(difficulty.stepDelayMs)
                val now = clockMillis()
                val snapshot = synchronized(engineLock) { engine.state }
                if (snapshot.isPaused || snapshot.isGameOver) continue

                if (snapshot.sabotageEnabled && snapshot.mode != GameMode.CO_OP) {
                    controller.maybeSelectSabotage(snapshot, now)?.let { (targetId, sab) ->
                        val updated = synchronized(engineLock) {
                            engine.useSabotage(
                                attackerPlayerId = 2,
                                targetPlayerId = targetId,
                                sabotageType = sab,
                                attackerLabel = "Bot"
                            )
                        }
                        publishState(updated, now)
                    }
                }

                val latestSnapshot = synchronized(engineLock) { engine.state }
                controller.nextAction(latestSnapshot)?.let { action ->
                    dispatchAction(2, action)
                }
            }
        }
    }

    private fun startGravityTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                val now = clockMillis()
                var changed = false
                val latestState = synchronized(engineLock) {
                    val current = engine.state
                    if (current.isPaused || current.isGameOver) {
                        current
                    } else {
                        var workingState = engine.tickSabotageAndCoopGlitch(TICK_INTERVAL_MS)
                        workingState = engine.tickMysteryPieceTimers(TICK_INTERVAL_MS)
                        if (workingState != current) {
                            changed = true
                        }
                        // Step lower pieces first (larger y) so stacked active pieces fall smoothly together.
                        val orderedPlayers = workingState.activePieces.values
                            .sortedByDescending { it.y }
                            .map { it.playerId }

                        for (playerId in orderedPlayers) {
                            val lastDrop = lastDropTimeMs[playerId] ?: now
                            val interval = engine.getDropIntervalMs(playerId, now)
                            if (now - lastDrop >= interval) {
                                lastDropTimeMs[playerId] = now
                                workingState = engine.stepPlayerDown(playerId, now)
                                changed = true
                            }
                        }
                        workingState
                    }
                }

                if (changed) {
                    publishState(latestState, now)
                } else {
                    refreshSabotageIndicators(latestState, now)
                }
            }
        }
    }

    private fun publishState(state: GameState, now: Long) {
        _gameState.value = state
        refreshSabotageIndicators(state, now)
    }

    private fun refreshSabotageIndicators(state: GameState, now: Long) {
        val activeSabotaged = state.activePieces.keys
            .filter { playerId -> state.isSabotaged(playerId, now) }
            .toSet()

        if (_sessionState.value.activeSabotagedPlayerIds != activeSabotaged) {
            _sessionState.update { it.copy(activeSabotagedPlayerIds = activeSabotaged) }
        }
    }

    companion object {
        private const val TICK_INTERVAL_MS = 16L
    }
}
