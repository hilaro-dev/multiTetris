package com.multitetris.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multitetris.audio.RetroSoundEngine
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.network.GameSessionController
import com.multitetris.network.LocalSimulationSessionController
import com.multitetris.network.NetworkSessionState
import com.multitetris.network.social.FirebaseSocialManager
import com.multitetris.network.social.FriendRequest
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.social.RoomInvite
import com.multitetris.network.social.SavedFriend
import com.multitetris.network.social.SocialUiState
import com.multitetris.network.webrtc.MultiplayerSessionManager
import com.multitetris.network.webrtc.WebRtcSessionController
import com.multitetris.network.webrtc.WebRtcSignalState
import com.multitetris.ui.skin.ClassicSkinRenderer
import com.multitetris.ui.skin.NeonCyberSkinRenderer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MultiTetrisUiState(
    val selectedMode: GameMode = GameMode.CO_OP,
    val selectedSkins: Map<Int, String> = mapOf(
        1 to ClassicSkinRenderer.SKIN_ID,
        2 to NeonCyberSkinRenderer.SKIN_ID
    ),
    val gameState: GameState = GameState(),
    val networkState: NetworkSessionState = NetworkSessionState(),
    val socialState: SocialUiState = SocialUiState(),
    val webRtcSignalState: WebRtcSignalState = WebRtcSignalState.Idle,
    val isMultiplayerSession: Boolean = false,
    val isMuted: Boolean = false
) {
    val controlledPlayerId: Int get() = networkState.controlledPlayerId
}

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val localSessionController = LocalSimulationSessionController()
    private val webRtcSessionController: WebRtcSessionController =
        MultiplayerSessionManager.getInstance(application.applicationContext)
    private val socialManager = FirebaseSocialManager(application.applicationContext)
    private val soundEngine = RetroSoundEngine()

    private val _selectedMode = MutableStateFlow(GameMode.CO_OP)
    private val _selectedSkins = MutableStateFlow(
        mapOf(
            1 to ClassicSkinRenderer.SKIN_ID,
            2 to NeonCyberSkinRenderer.SKIN_ID
        )
    )
    private val _isMultiplayerSession = MutableStateFlow(false)
    private val _isMuted = MutableStateFlow(false)
    private var connectedMatchId: String? = null
    private var lastPlayedClearEventId = 0L
    private var wasGameOver = false
    private var lastSabotagedIds: Set<Int> = emptySet()

    private val activeGameStateFlow = _isMultiplayerSession.flatMapLatest { isMultiplayer ->
        if (isMultiplayer) webRtcSessionController.gameState else localSessionController.gameState
    }

    private val activeSessionStateFlow = _isMultiplayerSession.flatMapLatest { isMultiplayer ->
        if (isMultiplayer) webRtcSessionController.sessionState else localSessionController.sessionState
    }

    init {
        socialManager.initialize(defaultSkinId = ClassicSkinRenderer.SKIN_ID)

        // Automatically start WebRTC P2P signaling when Stage 2 matchmaking finds a match
        viewModelScope.launch {
            socialManager.socialState.collect { social ->
                val matchState = social.matchmakingState
                if (matchState is MatchmakingState.MatchFound) {
                    val room = matchState.room
                    if (connectedMatchId != room.matchId) {
                        connectedMatchId = room.matchId
                        localSessionController.stop()
                        _isMultiplayerSession.value = true
                        webRtcSessionController.connectRoom(room, social.userProfile)
                    } else {
                        webRtcSessionController.updateRoomMetadata(room)
                    }
                } else if (connectedMatchId != null) {
                    connectedMatchId = null
                }
            }
        }

        // Trigger retro synthesized SFX on line clears, sabotage bursts, and game over
        viewModelScope.launch {
            activeGameStateFlow.collect { game ->
                if (game.lineClearEventId > lastPlayedClearEventId) {
                    lastPlayedClearEventId = game.lineClearEventId
                    soundEngine.playLineClear(game.lastClearedRowIndices.size.coerceAtLeast(1))
                } else if (game.lineClearEventId == 0L) {
                    lastPlayedClearEventId = 0L
                }

                if (game.isGameOver && !wasGameOver) {
                    soundEngine.playGameOver()
                }
                wasGameOver = game.isGameOver
            }
        }

        viewModelScope.launch {
            activeSessionStateFlow.collect { session ->
                val currentSabotaged = session.activeSabotagedPlayerIds
                val newlySabotaged = currentSabotaged - lastSabotagedIds
                if (newlySabotaged.isNotEmpty()) {
                    soundEngine.playSabotageBurst()
                }
                lastSabotagedIds = currentSabotaged
            }
        }
    }

    val uiState: StateFlow<MultiTetrisUiState> = combine(
        _selectedMode,
        _selectedSkins,
        activeGameStateFlow,
        activeSessionStateFlow,
        socialManager.socialState,
        webRtcSessionController.signalState,
        _isMultiplayerSession,
        _isMuted
    ) { values ->
        val mode = values[0] as GameMode
        @Suppress("UNCHECKED_CAST")
        val skins = values[1] as Map<Int, String>
        val game = values[2] as GameState
        val network = values[3] as NetworkSessionState
        val social = values[4] as SocialUiState
        val signal = values[5] as WebRtcSignalState
        val isMultiplayer = values[6] as Boolean
        val isMuted = values[7] as Boolean

        val syncedSkins = if (!isMultiplayer &&
            social.userProfile.selectedSkinId.isNotBlank() &&
            skins[1] != social.userProfile.selectedSkinId &&
            game.activePieces.isEmpty()
        ) {
            skins.toMutableMap().apply { put(1, social.userProfile.selectedSkinId) }
        } else {
            skins
        }

        MultiTetrisUiState(
            selectedMode = mode,
            selectedSkins = syncedSkins,
            gameState = game,
            networkState = network,
            socialState = social,
            webRtcSignalState = signal,
            isMultiplayerSession = isMultiplayer,
            isMuted = isMuted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MultiTetrisUiState()
    )

    private fun currentController(): GameSessionController {
        return if (_isMultiplayerSession.value) webRtcSessionController else localSessionController
    }

    // --- Stage 1 Local Simulation & Shared Gameplay Actions ---

    fun setGameMode(mode: GameMode) {
        _selectedMode.value = mode
    }

    fun setPlayerSkin(playerId: Int, skinId: String) {
        _selectedSkins.update { current ->
            current.toMutableMap().apply { put(playerId, skinId) }
        }
        currentController().setPlayerSkin(playerId, skinId)
        if (playerId == 1) {
            socialManager.updateSelectedSkin(skinId)
        }
    }

    fun startLocal2PGame() {
        webRtcSessionController.stop()
        connectedMatchId = null
        _isMultiplayerSession.value = false
        localSessionController.startSession(
            mode = _selectedMode.value,
            playerSkins = uiState.value.selectedSkins,
            config = BoardConfig(width = 10, height = 20, playerCount = 2)
        )
    }

    fun restartGame() {
        if (_isMultiplayerSession.value) {
            webRtcSessionController.requestRematch()
        } else {
            startLocal2PGame()
        }
    }

    fun stopGameLoop() {
        localSessionController.stop()
        if (_isMultiplayerSession.value) {
            webRtcSessionController.stop()
            _isMultiplayerSession.value = false
            connectedMatchId = null
            socialManager.cancelSearch()
        }
    }

    fun switchControlFocus() {
        if (_isMultiplayerSession.value) return
        val currentId = uiState.value.controlledPlayerId
        val playerIds = uiState.value.gameState.config.spawnZones.map { it.playerId }
        if (playerIds.isEmpty()) return
        val currentIndex = playerIds.indexOf(currentId)
        val nextId = playerIds[(currentIndex + 1) % playerIds.size]
        localSessionController.switchControlledPlayer(nextId)
    }

    fun selectControlPlayer(playerId: Int) {
        if (_isMultiplayerSession.value) return
        localSessionController.switchControlledPlayer(playerId)
    }

    fun onPlayerAction(action: PlayerAction) {
        val currentGame = uiState.value.gameState
        if (!currentGame.isPaused && !currentGame.isGameOver) {
            when (action) {
                PlayerAction.MOVE_LEFT,
                PlayerAction.MOVE_RIGHT -> soundEngine.playMove()
                PlayerAction.ROTATE_CW -> soundEngine.playRotate()
                PlayerAction.SOFT_DROP -> soundEngine.playSoftDrop()
                PlayerAction.HARD_DROP -> soundEngine.playHardDrop()
            }
        }
        val targetPlayerId = uiState.value.controlledPlayerId
        currentController().dispatchAction(targetPlayerId, action)
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        soundEngine.isMuted = next
    }

    fun togglePause() {
        currentController().togglePause()
    }

    // --- Stage 2 & 3 Social, Matchmaking & WebRTC Lifecycle ---

    fun updateNickname(newNickname: String) {
        socialManager.updateNickname(newNickname)
    }

    fun addFriendByCode(codeInput: String) {
        socialManager.sendFriendRequestByCode(codeInput)
    }

    fun sendFriendRequest(codeInput: String) {
        socialManager.sendFriendRequestByCode(codeInput)
    }

    fun acceptFriendRequest(request: FriendRequest) {
        socialManager.acceptFriendRequest(request)
    }

    fun declineFriendRequest(fromUid: String) {
        socialManager.declineFriendRequest(fromUid)
    }

    fun removeFriend(friendUid: String) {
        socialManager.removeFriend(friendUid)
    }

    fun clearFriendFeedback() {
        socialManager.clearFeedback()
    }

    fun createRoom() {
        socialManager.createRoom(_selectedMode.value)
    }

    fun inviteFriendToRoom(friend: SavedFriend) {
        socialManager.inviteFriendToRoom(friend)
    }

    fun acceptRoomInvite(invite: RoomInvite) {
        socialManager.acceptRoomInvite(invite)
    }

    fun declineRoomInvite(invite: RoomInvite) {
        socialManager.declineRoomInvite(invite)
    }

    fun startPlayTogetherSearch() {
        socialManager.startSearch(_selectedMode.value)
    }

    fun cancelPlayTogetherSearch() {
        webRtcSessionController.stop()
        _isMultiplayerSession.value = false
        connectedMatchId = null
        socialManager.leaveRoom()
    }

    override fun onCleared() {
        super.onCleared()
        localSessionController.stop()
        webRtcSessionController.stop()
        socialManager.shutdown()
        soundEngine.shutdown()
    }
}
