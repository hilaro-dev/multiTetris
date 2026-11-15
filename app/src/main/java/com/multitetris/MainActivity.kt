package com.multitetris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.webrtc.WebRtcSignalState
import com.multitetris.ui.screens.GameScreen
import com.multitetris.ui.screens.HomeScreen
import com.multitetris.ui.screens.MatchLobbyScreen
import com.multitetris.ui.theme.MultiTetrisTheme
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

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                selectedMode = uiState.selectedMode,
                selectedSkins = uiState.selectedSkins,
                socialState = uiState.socialState,
                onSelectMode = gameViewModel::setGameMode,
                onSelectSkin = gameViewModel::setPlayerSkin,
                onUpdateNickname = gameViewModel::updateNickname,
                onAddFriendByCode = gameViewModel::addFriendByCode,
                onRemoveFriend = gameViewModel::removeFriend,
                onClearFriendFeedback = gameViewModel::clearFriendFeedback,
                onStartPlayTogetherSearch = gameViewModel::startPlayTogetherSearch,
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
}
