package com.multitetris.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multitetris.audio.RetroSoundEngine
import com.multitetris.economy.EconomyRepository
import com.multitetris.economy.EconomyUiState
import com.multitetris.economy.MatchRewardBreakdown
import com.multitetris.economy.MatchRewardCalculator
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.game.SabotageConfig
import com.multitetris.game.SabotageType
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
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
import com.multitetris.stats.CareerStatsSnapshot
import com.multitetris.stats.MatchParticipantStat
import com.multitetris.stats.MatchStatRecord
import com.multitetris.stats.StatsRepository
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
    val economyState: EconomyUiState = EconomyUiState(),
    val lastMatchReward: MatchRewardBreakdown? = null,
    val careerStats: CareerStatsSnapshot = CareerStatsSnapshot(),
    val isNewCoopRecord: Boolean = false,
    val webRtcSignalState: WebRtcSignalState = WebRtcSignalState.Idle,
    val connectedPeerUids: Set<String> = emptySet(),
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
    private val economyRepository = EconomyRepository(application.applicationContext)
    private val statsRepository = StatsRepository(application.applicationContext)
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
    private val _lastMatchReward = MutableStateFlow<MatchRewardBreakdown?>(null)
    private val _isNewCoopRecord = MutableStateFlow(false)
    private var connectedMatchId: String? = null
    private var lastPlayedClearEventId = 0L
    private var wasGameOver = false
    private var lastSabotagedIds: Set<Int> = emptySet()
    private var matchStartedAtMillis: Long = System.currentTimeMillis()
    private var rewardGrantedForCurrentMatch = false
    private var lastRecordedLinesForMissions = 0

    private var lastLocalPlayerCount: Int = 2
    private var lastLocalVersusSubMode: VersusSubMode = VersusSubMode.FREE_FOR_ALL

    private val activeGameStateFlow = _isMultiplayerSession.flatMapLatest { isMultiplayer ->
        if (isMultiplayer) webRtcSessionController.gameState else localSessionController.gameState
    }

    private val activeSessionStateFlow = _isMultiplayerSession.flatMapLatest { isMultiplayer ->
        if (isMultiplayer) webRtcSessionController.sessionState else localSessionController.sessionState
    }

    private val webRtcCombinedFlow = combine(
        webRtcSessionController.signalState,
        webRtcSessionController.connectedPeerUids
    ) { signal, peers -> signal to peers }

    private val statsCombinedFlow = combine(
        statsRepository.stats,
        _isNewCoopRecord
    ) { stats, isNewCoop -> stats to isNewCoop }

    private val economyStateFlow = combine(
        economyRepository.coinBalance,
        economyRepository.unlockedSkins,
        economyRepository.dailyMissions
    ) { balance, unlocked, missions ->
        EconomyUiState(
            coinBalance = balance,
            unlockedSkins = unlocked,
            dailyMissions = missions
        )
    }

    init {
        socialManager.initialize(defaultSkinId = ClassicSkinRenderer.SKIN_ID)

        // Automatically start WebRTC P2P signaling when Stage 2/8 matchmaking creates/joins a room
        viewModelScope.launch {
            socialManager.socialState.collect { social ->
                val matchState = social.matchmakingState
                if (matchState is MatchmakingState.MatchFound) {
                    val room = matchState.room
                    if (connectedMatchId != room.matchId) {
                        connectedMatchId = room.matchId
                        localSessionController.stop()
                        _isMultiplayerSession.value = true
                        resetMatchTracking()
                        webRtcSessionController.connectRoom(room, social.userProfile)
                    } else {
                        webRtcSessionController.updateRoomMetadata(room)
                    }
                } else if (connectedMatchId != null) {
                    connectedMatchId = null
                }
            }
        }

        // Trigger retro synthesized SFX, match rewards, and daily mission progress
        viewModelScope.launch {
            activeGameStateFlow.collect { game ->
                if (!game.isGameOver && wasGameOver) {
                    resetMatchTracking()
                }

                if (game.lineClearEventId > lastPlayedClearEventId) {
                    lastPlayedClearEventId = game.lineClearEventId
                    soundEngine.playLineClear(game.lastClearedRowIndices.size.coerceAtLeast(1))
                } else if (game.lineClearEventId == 0L) {
                    lastPlayedClearEventId = 0L
                }

                val localPlayerId = currentController().sessionState.value.controlledPlayerId
                val trackedLines = if (game.mode == GameMode.CO_OP) {
                    game.sharedLines
                } else {
                    game.playerLines[localPlayerId] ?: 0
                }
                if (trackedLines > lastRecordedLinesForMissions) {
                    val delta = trackedLines - lastRecordedLinesForMissions
                    lastRecordedLinesForMissions = trackedLines
                    economyRepository.recordLinesCleared(delta)
                } else if (trackedLines < lastRecordedLinesForMissions) {
                    lastRecordedLinesForMissions = trackedLines
                }

                if (game.isGameOver && !wasGameOver) {
                    soundEngine.playGameOver()
                    if (!rewardGrantedForCurrentMatch) {
                        rewardGrantedForCurrentMatch = true
                        val isMultiplayer = _isMultiplayerSession.value
                        val durationMs = (System.currentTimeMillis() - matchStartedAtMillis).coerceAtLeast(0L)
                        val breakdown = MatchRewardCalculator.calculate(
                            gameState = game,
                            localPlayerId = localPlayerId,
                            isLocalTest = !isMultiplayer,
                            matchDurationMillis = durationMs
                        )
                        _lastMatchReward.value = breakdown
                        if (breakdown.totalCoins > 0) {
                            economyRepository.addCoins(breakdown.totalCoins)
                        }
                        val didWin = if (game.mode == GameMode.VERSUS) {
                            if (game.versusSubMode == VersusSubMode.TEAMS_2V2) {
                                game.winningTeam != null && game.playerTeams[localPlayerId] == game.winningTeam
                            } else {
                                game.winnerPlayerId == localPlayerId
                            }
                        } else {
                            false
                        }
                        economyRepository.recordMatchCompleted(
                            mode = game.mode,
                            isMultiplayer = isMultiplayer,
                            didLocalPlayerWin = didWin
                        )

                        val socialSnapshot = socialManager.socialState.value
                        val localUid = socialSnapshot.userProfile.uid
                        val friendUids = socialSnapshot.friends
                            .map { it.friend.uid }
                            .filter { it.isNotBlank() }
                            .toSet()
                        val sessionPlayers = currentController().sessionState.value.players
                        val roomPlayers = (socialSnapshot.matchmakingState as? MatchmakingState.MatchFound)
                            ?.room
                            ?.resolvedPlayers()
                            .orEmpty()
                        val participants = (1..game.config.playerCount).map { pid ->
                            val endpoint = sessionPlayers.firstOrNull { it.playerId == pid }
                            val uid = endpoint?.uid?.takeIf { it.isNotBlank() }
                                ?: roomPlayers.firstOrNull { it.slotIndex == pid }?.uid.orEmpty()
                            val isDisconnected = (endpoint?.isDisconnected == true) || game.isPlayerDisconnected(pid)
                            val rawPlacement = game.placementFor(pid)
                            val resolvedPlacement = if (rawPlacement == 0 && pid == game.winnerPlayerId) 1 else rawPlacement
                            MatchParticipantStat(
                                playerId = pid,
                                uid = uid,
                                team = game.playerTeams[pid] ?: TeamColor.NONE,
                                placement = resolvedPlacement,
                                score = game.playerScores[pid] ?: 0,
                                linesCleared = game.playerLines[pid] ?: 0,
                                isDisconnected = isDisconnected
                            )
                        }
                        _isNewCoopRecord.value = statsRepository.recordCompletedMatch(
                            MatchStatRecord(
                                mode = game.mode,
                                versusSubMode = game.versusSubMode,
                                localPlayerId = localPlayerId,
                                localUid = localUid,
                                didLocalPlayerWin = didWin,
                                winningTeam = game.winningTeam,
                                sharedScore = game.sharedScore,
                                sharedLines = game.sharedLines,
                                matchDurationMs = durationMs,
                                participants = participants,
                                friendUids = friendUids
                            )
                        )
                    }
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
        economyStateFlow,
        _lastMatchReward,
        webRtcCombinedFlow,
        _isMultiplayerSession,
        _isMuted,
        statsCombinedFlow
    ) { values ->
        val mode = values[0] as GameMode
        @Suppress("UNCHECKED_CAST")
        val skins = values[1] as Map<Int, String>
        val game = values[2] as GameState
        val network = values[3] as NetworkSessionState
        val social = values[4] as SocialUiState
        val economy = values[5] as EconomyUiState
        val matchReward = values[6] as MatchRewardBreakdown?
        @Suppress("UNCHECKED_CAST")
        val rtcPair = values[7] as Pair<WebRtcSignalState, Set<String>>
        val isMultiplayer = values[8] as Boolean
        val isMuted = values[9] as Boolean
        @Suppress("UNCHECKED_CAST")
        val statsPair = values[10] as Pair<CareerStatsSnapshot, Boolean>

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
            economyState = economy,
            lastMatchReward = matchReward,
            careerStats = statsPair.first,
            isNewCoopRecord = statsPair.second,
            webRtcSignalState = rtcPair.first,
            connectedPeerUids = rtcPair.second,
            isMultiplayerSession = isMultiplayer,
            isMuted = isMuted
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MultiTetrisUiState()
    )

    private fun resetMatchTracking() {
        matchStartedAtMillis = System.currentTimeMillis()
        rewardGrantedForCurrentMatch = false
        lastRecordedLinesForMissions = 0
        _lastMatchReward.value = null
        _isNewCoopRecord.value = false
    }

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

    fun purchaseAndEquipSkin(playerId: Int, skinId: String, price: Int) {
        viewModelScope.launch {
            val purchased = economyRepository.purchaseSkin(skinId, price)
            if (purchased) {
                soundEngine.playCoinChime()
                setPlayerSkin(playerId, skinId)
            }
        }
    }

    fun claimDailyMission(missionId: String) {
        viewModelScope.launch {
            val reward = economyRepository.claimMissionReward(missionId)
            if (reward > 0) {
                soundEngine.playCoinChime()
            }
        }
    }

    fun refreshDailyMissions() {
        viewModelScope.launch {
            economyRepository.refreshDailyMissionsIfNeeded()
        }
    }

    fun startLocal2PGame() {
        startLocalTestGame(playerCount = 2, versusSubMode = VersusSubMode.FREE_FOR_ALL)
    }

    fun startLocalTestGame(
        playerCount: Int = 2,
        versusSubMode: VersusSubMode = VersusSubMode.FREE_FOR_ALL
    ) {
        val clampedCount = playerCount.coerceIn(2, 4)
        val resolvedSubMode = if (clampedCount == 4 && _selectedMode.value == GameMode.VERSUS) {
            versusSubMode
        } else {
            VersusSubMode.FREE_FOR_ALL
        }
        lastLocalPlayerCount = clampedCount
        lastLocalVersusSubMode = resolvedSubMode

        webRtcSessionController.stop()
        connectedMatchId = null
        _isMultiplayerSession.value = false
        resetMatchTracking()

        val baseSkins = uiState.value.selectedSkins
        val mergedSkins = (1..clampedCount).associateWith { pid ->
            baseSkins[pid] ?: GameState.defaultSkinForPlayer(pid)
        }
        val defaultTeams = if (resolvedSubMode == VersusSubMode.TEAMS_2V2) {
            mapOf(
                1 to TeamColor.CYAN,
                2 to TeamColor.CYAN,
                3 to TeamColor.MAGENTA,
                4 to TeamColor.MAGENTA
            )
        } else {
            emptyMap()
        }

        localSessionController.startSession(
            mode = _selectedMode.value,
            playerSkins = mergedSkins,
            config = BoardConfig.forPlayerCount(clampedCount),
            versusSubMode = resolvedSubMode,
            playerTeams = defaultTeams,
            sabotageEnabled = true,
            equippedSabotages = (1..clampedCount).associateWith { SabotageConfig.defaultLoadout }
        )
    }

    fun restartGame() {
        if (_isMultiplayerSession.value) {
            webRtcSessionController.requestRematch()
        } else {
            startLocalTestGame(lastLocalPlayerCount, lastLocalVersusSubMode)
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
        val targetPlayerId = uiState.value.controlledPlayerId
        if (targetPlayerId in currentGame.eliminatedPlayerIds ||
            targetPlayerId in currentGame.blockedPlayerIds
        ) {
            return
        }
        val activeSabotage = currentGame.activeSabotageFor(targetPlayerId)
        if (activeSabotage?.type == SabotageType.BUTTON_FREEZE) {
            return
        }
        if (activeSabotage?.type == SabotageType.DROP_JAMMER && action == PlayerAction.HARD_DROP) {
            return
        }
        val effectiveAction = if (activeSabotage?.type == SabotageType.CONTROLS_FLIP) {
            when (action) {
                PlayerAction.MOVE_LEFT -> PlayerAction.MOVE_RIGHT
                PlayerAction.MOVE_RIGHT -> PlayerAction.MOVE_LEFT
                else -> action
            }
        } else {
            action
        }
        if (!currentGame.isPaused && !currentGame.isGameOver) {
            when (effectiveAction) {
                PlayerAction.MOVE_LEFT,
                PlayerAction.MOVE_RIGHT -> soundEngine.playMove()
                PlayerAction.ROTATE_CW -> soundEngine.playRotate()
                PlayerAction.SOFT_DROP -> soundEngine.playSoftDrop()
                PlayerAction.HARD_DROP -> soundEngine.playHardDrop()
            }
        }
        currentController().dispatchAction(targetPlayerId, effectiveAction)
    }

    fun onUseSabotage(targetPlayerId: Int, sabotageType: SabotageType) {
        val currentGame = uiState.value.gameState
        val attackerPlayerId = uiState.value.controlledPlayerId
        if (!currentGame.sabotageEnabled ||
            currentGame.isPaused ||
            currentGame.isGameOver ||
            attackerPlayerId in currentGame.eliminatedPlayerIds ||
            attackerPlayerId in currentGame.blockedPlayerIds
        ) {
            return
        }
        currentController().triggerSabotage(attackerPlayerId, targetPlayerId, sabotageType)
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

    fun setRoomVersusSubMode(subMode: VersusSubMode) {
        socialManager.setRoomVersusSubMode(subMode)
    }

    fun setRoomSabotageEnabled(enabled: Boolean) {
        socialManager.setRoomSabotageEnabled(enabled)
    }

    fun toggleMySabotageSelection(sabotage: com.multitetris.game.SabotageType) {
        socialManager.toggleMySabotageSelection(sabotage)
    }

    fun swapPlayerTeam(targetUid: String) {
        socialManager.swapPlayerTeam(targetUid)
    }

    fun startRoomMatch() {
        socialManager.startRoomMatch()
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
