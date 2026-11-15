package com.multitetris.network

import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.MultiTetrisEngine
import com.multitetris.game.PlayerAction
import com.multitetris.game.TeamColor
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

    private val _sessionState = MutableStateFlow(NetworkSessionState())
    override val sessionState: StateFlow<NetworkSessionState> = _sessionState.asStateFlow()

    private val _gameState = MutableStateFlow(GameState())
    override val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    override fun startSession(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        config: BoardConfig,
        versusSubMode: VersusSubMode,
        playerTeams: Map<Int, TeamColor>
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
                playerTeams = playerTeams
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

    override fun switchControlledPlayer(playerId: Int) {
        _sessionState.update { current ->
            if (current.players.any { it.playerId == playerId }) {
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
                        // Step lower pieces first (larger y) so stacked active pieces fall smoothly together.
                        val orderedPlayers = current.activePieces.values
                            .sortedByDescending { it.y }
                            .map { it.playerId }

                        var workingState = current
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
