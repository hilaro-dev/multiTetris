package com.multitetris.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.network.GameSessionController
import com.multitetris.network.LocalSimulationSessionController
import com.multitetris.network.NetworkSessionState
import com.multitetris.network.social.FirebaseSocialManager
import com.multitetris.network.social.SocialUiState
import com.multitetris.ui.skin.ClassicSkinRenderer
import com.multitetris.ui.skin.NeonCyberSkinRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class MultiTetrisUiState(
    val selectedMode: GameMode = GameMode.CO_OP,
    val selectedSkins: Map<Int, String> = mapOf(
        1 to ClassicSkinRenderer.SKIN_ID,
        2 to NeonCyberSkinRenderer.SKIN_ID
    ),
    val gameState: GameState = GameState(),
    val networkState: NetworkSessionState = NetworkSessionState(),
    val socialState: SocialUiState = SocialUiState()
) {
    val controlledPlayerId: Int get() = networkState.controlledPlayerId
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val sessionController: GameSessionController = LocalSimulationSessionController()
    private val socialManager = FirebaseSocialManager(application.applicationContext)

    private val _selectedMode = MutableStateFlow(GameMode.CO_OP)
    private val _selectedSkins = MutableStateFlow(
        mapOf(
            1 to ClassicSkinRenderer.SKIN_ID,
            2 to NeonCyberSkinRenderer.SKIN_ID
        )
    )

    init {
        socialManager.initialize(defaultSkinId = ClassicSkinRenderer.SKIN_ID)
    }

    val uiState: StateFlow<MultiTetrisUiState> = combine(
        _selectedMode,
        _selectedSkins,
        sessionController.gameState,
        sessionController.sessionState,
        socialManager.socialState
    ) { mode, skins, game, network, social ->
        val syncedSkins = if (social.userProfile.selectedSkinId.isNotBlank() &&
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
            socialState = social
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MultiTetrisUiState()
    )

    // --- Stage 1 Local Simulation & Game Actions ---

    fun setGameMode(mode: GameMode) {
        _selectedMode.value = mode
    }

    fun setPlayerSkin(playerId: Int, skinId: String) {
        _selectedSkins.update { current ->
            current.toMutableMap().apply { put(playerId, skinId) }
        }
        sessionController.setPlayerSkin(playerId, skinId)
        if (playerId == 1) {
            socialManager.updateSelectedSkin(skinId)
        }
    }

    fun startLocal2PGame() {
        sessionController.startSession(
            mode = _selectedMode.value,
            playerSkins = uiState.value.selectedSkins,
            config = BoardConfig(width = 10, height = 20, playerCount = 2)
        )
    }

    fun restartGame() {
        startLocal2PGame()
    }

    fun stopGameLoop() {
        sessionController.stop()
    }

    fun switchControlFocus() {
        val currentId = uiState.value.controlledPlayerId
        val playerIds = uiState.value.gameState.config.spawnZones.map { it.playerId }
        if (playerIds.isEmpty()) return
        val currentIndex = playerIds.indexOf(currentId)
        val nextId = playerIds[(currentIndex + 1) % playerIds.size]
        sessionController.switchControlledPlayer(nextId)
    }

    fun selectControlPlayer(playerId: Int) {
        sessionController.switchControlledPlayer(playerId)
    }

    fun onPlayerAction(action: PlayerAction) {
        val targetPlayerId = uiState.value.controlledPlayerId
        sessionController.dispatchAction(targetPlayerId, action)
    }

    fun togglePause() {
        sessionController.togglePause()
    }

    // --- Stage 2 Friends, Identity & Automatic Matchmaking ---

    fun updateNickname(newNickname: String) {
        socialManager.updateNickname(newNickname)
    }

    fun addFriendByCode(codeInput: String) {
        socialManager.addFriendByCode(codeInput)
    }

    fun removeFriend(friendUid: String) {
        socialManager.removeFriend(friendUid)
    }

    fun clearFriendFeedback() {
        socialManager.clearFeedback()
    }

    fun startPlayTogetherSearch() {
        socialManager.startSearch(_selectedMode.value)
    }

    fun cancelPlayTogetherSearch() {
        socialManager.cancelSearch()
    }

    override fun onCleared() {
        super.onCleared()
        sessionController.stop()
        socialManager.shutdown()
    }
}
