package com.multitetris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.multitetris.audio.MusicTrack
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.social.RoomInvite
import com.multitetris.network.webrtc.WebRtcSignalState
import com.multitetris.ui.screens.GameScreen
import com.multitetris.ui.screens.HomeScreen
import com.multitetris.ui.screens.MatchLobbyScreen
import com.multitetris.ui.screens.ShopScreen
import com.multitetris.ui.theme.CyberCyan
import com.multitetris.ui.theme.ElevatedCard
import com.multitetris.ui.i18n.EnStrings
import com.multitetris.ui.i18n.LocalStrings
import com.multitetris.ui.i18n.TrStrings
import com.multitetris.ui.theme.MultiTetrisTheme
import com.multitetris.ui.theme.ObsidianBg
import com.multitetris.ui.theme.TextPrimary
import com.multitetris.ui.theme.TextSecondary
import com.multitetris.ui.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MultiTetrisApp()
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SHOP = "shop"
    const val GAME = "game"
    const val LOBBY = "lobby"
}

@Composable
fun MultiTetrisApp(
    gameViewModel: GameViewModel = viewModel()
) {
    val navController = rememberNavController()
    val uiState by gameViewModel.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, gameViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> gameViewModel.setMusicLifecyclePaused(true)
                Lifecycle.Event.ON_RESUME,
                Lifecycle.Event.ON_START -> gameViewModel.setMusicLifecyclePaused(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(currentRoute) {
        val track = when (currentRoute) {
            Routes.GAME -> MusicTrack.GAMEPLAY_KOROBEINIKI
            Routes.HOME, Routes.SHOP, Routes.LOBBY, null -> MusicTrack.LOBBY_CHILL
            else -> MusicTrack.LOBBY_CHILL
        }
        gameViewModel.setMusicTrack(track)
    }
    val matchmakingState = uiState.socialState.matchmakingState
    val signalState = uiState.webRtcSignalState
    val pendingInvite = uiState.socialState.incomingRoomInvites.firstOrNull()
    var lastDisplayedInvite by remember { mutableStateOf<RoomInvite?>(null) }
    LaunchedEffect(pendingInvite) {
        if (pendingInvite != null) {
            lastDisplayedInvite = pendingInvite
        }
    }
    val showInviteBanner = pendingInvite != null &&
        currentRoute != Routes.GAME &&
        currentRoute != Routes.LOBBY

    LaunchedEffect(matchmakingState, signalState, currentRoute) {
        when {
            (currentRoute == Routes.HOME || currentRoute == Routes.SHOP) &&
                matchmakingState is MatchmakingState.MatchFound -> {
                navController.navigate(Routes.LOBBY) {
                    launchSingleTop = true
                }
            }

            currentRoute == Routes.LOBBY && signalState is WebRtcSignalState.Connected -> {
                gameViewModel.playMatchStartSound()
                navController.navigate(Routes.GAME) {
                    popUpTo(Routes.HOME) { inclusive = false }
                    launchSingleTop = true
                }
            }

            currentRoute == Routes.LOBBY && matchmakingState !is MatchmakingState.MatchFound -> {
                navController.popBackStack(Routes.HOME, inclusive = false)
            }
        }
    }

    val strings = if (uiState.languageCode == "tr") TrStrings else EnStrings
    CompositionLocalProvider(LocalStrings provides strings) {
        MultiTetrisTheme(isDarkMode = uiState.isDarkMode) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    selectedMode = uiState.selectedMode,
                    selectedSkins = uiState.selectedSkins,
                    socialState = uiState.socialState,
                    economyState = uiState.economyState,
                    careerStats = uiState.careerStats,
                    isDarkMode = uiState.isDarkMode,
                    soundEnabled = uiState.soundEnabled,
                    musicEnabled = uiState.musicEnabled,
                    vibrationEnabled = uiState.vibrationEnabled,
                    onSelectMode = gameViewModel::setGameMode,
                    onSetDarkMode = gameViewModel::setDarkMode,
                    onSetSoundEnabled = gameViewModel::setSoundEnabled,
                    onSetMusicEnabled = gameViewModel::setMusicEnabled,
                    onSetVibrationEnabled = gameViewModel::setVibrationEnabled,
                    languageCode = uiState.languageCode,
                    onSetLanguage = gameViewModel::setLanguage,
                    onOpenShop = {
                        navController.navigate(Routes.SHOP) {
                            launchSingleTop = true
                        }
                    },
                    onClaimMission = gameViewModel::claimDailyMission,
                    onRefreshMissions = gameViewModel::refreshDailyMissions,
                    onUpdateNickname = gameViewModel::updateNickname,
                    onAddFriendByCode = gameViewModel::addFriendByCode,
                    onAcceptFriendRequest = gameViewModel::acceptFriendRequest,
                    onDeclineFriendRequest = gameViewModel::declineFriendRequest,
                    onRemoveFriend = gameViewModel::removeFriend,
                    onClearFriendFeedback = gameViewModel::clearFriendFeedback,
                    onCreateRoom = gameViewModel::createRoom,
                    onCancelPlayTogetherSearch = gameViewModel::cancelPlayTogetherSearch,
                    onStartLocalTest = { playerCount, subMode ->
                        gameViewModel.playMatchStartSound()
                        gameViewModel.startLocalTestGame(playerCount, subMode)
                        navController.navigate(Routes.GAME)
                    },
                    onStartSingleplayerBot = { playWithBot, mode, sabotageEnabled, mysteryEnabled, difficulty, sabotages ->
                        gameViewModel.playMatchStartSound()
                        gameViewModel.startSingleplayerBotGame(
                            playWithBot = playWithBot,
                            mode = mode,
                            sabotageEnabled = sabotageEnabled,
                            mysteryEnabled = mysteryEnabled,
                            botDifficulty = difficulty,
                            selectedSabotages = sabotages
                        )
                        navController.navigate(Routes.GAME)
                    },
                    onAcceptRoomInvite = gameViewModel::acceptRoomInvite,
                    onDeclineRoomInvite = gameViewModel::declineRoomInvite
                )
            }

            composable(Routes.SHOP) {
                ShopScreen(
                    selectedSkins = uiState.selectedSkins,
                    coinBalance = uiState.economyState.coinBalance,
                    unlockedSkins = uiState.economyState.unlockedSkins,
                    onEquipSkin = gameViewModel::setPlayerSkin,
                    onPurchaseSkin = gameViewModel::purchaseAndEquipSkin,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.LOBBY) {
                val foundRoom = (matchmakingState as? MatchmakingState.MatchFound)?.room
                if (foundRoom != null) {
                    MatchLobbyScreen(
                        room = foundRoom,
                        signalState = signalState,
                        socialState = uiState.socialState,
                        connectedPeerUids = uiState.connectedPeerUids,
                        onInviteFriend = gameViewModel::inviteFriendToRoom,
                        onSetRoomMode = gameViewModel::setRoomMode,
                        onSetVersusSubMode = gameViewModel::setRoomVersusSubMode,
                        onSetSabotageEnabled = gameViewModel::setRoomSabotageEnabled,
                        onSetMysteryEnabled = gameViewModel::setRoomMysteryEnabled,
                        onToggleSabotage = gameViewModel::toggleMySabotageSelection,
                        onSwapPlayerTeam = gameViewModel::swapPlayerTeam,
                        botDifficulty = uiState.lobbyBotDifficulty,
                        onAddBot = gameViewModel::addBotToLobbySlot,
                        onRemoveBot = gameViewModel::removeBotFromLobbySlot,
                        onSelectBotDifficulty = gameViewModel::setLobbyBotDifficulty,
                        onStartMatch = gameViewModel::startRoomMatch,
                        onCancelLobby = {
                            gameViewModel.cancelPlayTogetherSearch()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        }
                    )
                }
            }

            composable(Routes.GAME) {
                GameScreen(
                    gameState = uiState.gameState,
                    networkState = uiState.networkState,
                    matchReward = uiState.lastMatchReward,
                    isNewCoopRecord = uiState.isNewCoopRecord,
                    isMuted = uiState.isMuted,
                    vibrationEnabled = uiState.vibrationEnabled,
                    onToggleMute = gameViewModel::toggleMute,
                    onSwitchFocus = gameViewModel::switchControlFocus,
                    onSelectFocusPlayer = gameViewModel::selectControlPlayer,
                    onAction = gameViewModel::onPlayerAction,
                    onUseSabotage = gameViewModel::onUseSabotage,
                    onTogglePause = gameViewModel::togglePause,
                    onRestart = gameViewModel::restartGame,
                    onExitToHome = {
                        gameViewModel.stopGameLoop()
                        navController.popBackStack(Routes.HOME, inclusive = false)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = showInviteBanner,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val invite = pendingInvite ?: lastDisplayedInvite
            if (invite != null) {
                IncomingRoomInviteBanner(
                    invite = invite,
                    onJoin = { gameViewModel.acceptRoomInvite(invite) },
                    onDecline = { gameViewModel.declineRoomInvite(invite) }
                )
            }
        }
    }
    }
    }
}

@Composable
private fun IncomingRoomInviteBanner(
    invite: RoomInvite,
    onJoin: () -> Unit,
    onDecline: () -> Unit
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = ElevatedCard,
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = strings.roomInviteHeader,
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = strings.roomInviteTitle(invite.hostNickname),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = strings.roomInviteSubtitle(strings.gameModeLabel(invite.mode)),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.45f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = strings.decline,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = onJoin,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = ObsidianBg
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = strings.join,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
