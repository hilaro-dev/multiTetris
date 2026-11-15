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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.social.RoomInvite
import com.multitetris.network.webrtc.WebRtcSignalState
import com.multitetris.ui.screens.GameScreen
import com.multitetris.ui.screens.HomeScreen
import com.multitetris.ui.screens.MatchLobbyScreen
import com.multitetris.ui.theme.CyberCyan
import com.multitetris.ui.theme.ElevatedCard
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
            MultiTetrisTheme {
                MultiTetrisApp()
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
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
            currentRoute == Routes.HOME && matchmakingState is MatchmakingState.MatchFound -> {
                navController.navigate(Routes.LOBBY) {
                    launchSingleTop = true
                }
            }

            currentRoute == Routes.LOBBY && signalState is WebRtcSignalState.Connected -> {
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
                    onSelectMode = gameViewModel::setGameMode,
                    onSelectSkin = gameViewModel::setPlayerSkin,
                    onPurchaseSkin = gameViewModel::purchaseAndEquipSkin,
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
                    onStartLocalTest = {
                        gameViewModel.startLocal2PGame()
                        navController.navigate(Routes.GAME)
                    }
                )
            }

            composable(Routes.LOBBY) {
                val foundRoom = (matchmakingState as? MatchmakingState.MatchFound)?.room
                if (foundRoom != null) {
                    MatchLobbyScreen(
                        room = foundRoom,
                        signalState = signalState,
                        socialState = uiState.socialState,
                        onInviteFriend = gameViewModel::inviteFriendToRoom,
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
                    isMuted = uiState.isMuted,
                    onToggleMute = gameViewModel::toggleMute,
                    onSwitchFocus = gameViewModel::switchControlFocus,
                    onSelectFocusPlayer = gameViewModel::selectControlPlayer,
                    onAction = gameViewModel::onPlayerAction,
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

@Composable
private fun IncomingRoomInviteBanner(
    invite: RoomInvite,
    onJoin: () -> Unit,
    onDecline: () -> Unit
) {
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
                text = "INCOMING GAME INVITE",
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${invite.hostNickname} invited you to ${invite.mode.label}!",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
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
                        text = "Decline",
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
                        text = "Join",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
