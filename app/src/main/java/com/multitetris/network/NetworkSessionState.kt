package com.multitetris.network

import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.game.SabotageType
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Distinguishes Stage 1 Local Simulation from future Host/Client multiplayer stages.
 */
enum class SessionMode(val label: String) {
    LOCAL_SIMULATION("Local 2P Simulation"),
    HOST("Host Room"),
    CLIENT("Connected Client")
}

enum class ConnectionStatus {
    READY_LOCAL,
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

data class PlayerEndpointState(
    val playerId: Int,
    val displayName: String,
    val isLocalController: Boolean,
    val latencyMs: Int = 0,
    val isDisconnected: Boolean = false,
    val uid: String = ""
)

/**
 * Cleanly separates network/session routing and control focus from pure game physics.
 */
data class NetworkSessionState(
    val sessionMode: SessionMode = SessionMode.LOCAL_SIMULATION,
    val status: ConnectionStatus = ConnectionStatus.READY_LOCAL,
    val controlledPlayerId: Int = 1,
    val players: List<PlayerEndpointState> = listOf(
        PlayerEndpointState(playerId = 1, displayName = "Player 1", isLocalController = true),
        PlayerEndpointState(playerId = 2, displayName = "Player 2", isLocalController = true)
    ),
    val activeSabotagedPlayerIds: Set<Int> = emptySet(),
    val disconnectMessage: String? = null
)

interface GameSessionController {
    val sessionState: StateFlow<NetworkSessionState>
    val gameState: StateFlow<GameState>

    fun startSession(
        mode: GameMode,
        playerSkins: Map<Int, String>,
        config: BoardConfig = BoardConfig(),
        versusSubMode: VersusSubMode = VersusSubMode.FREE_FOR_ALL,
        playerTeams: Map<Int, TeamColor> = emptyMap(),
        sabotageEnabled: Boolean = false,
        equippedSabotages: Map<Int, List<SabotageType>> = emptyMap(),
        mysteryEnabled: Boolean = false
    )

    fun dispatchAction(playerId: Int, action: PlayerAction)

    fun triggerSabotage(attackerPlayerId: Int, targetPlayerId: Int, sabotageType: SabotageType) {}

    fun switchControlledPlayer(playerId: Int)

    fun togglePause()

    fun setPlayerSkin(playerId: Int, skinId: String)

    fun stop()
}
