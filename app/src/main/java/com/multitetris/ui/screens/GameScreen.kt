package com.multitetris.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.economy.MatchRewardBreakdown
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.game.TeamColor
import com.multitetris.game.TetrominoType
import com.multitetris.game.VersusSubMode
import com.multitetris.network.ConnectionStatus
import com.multitetris.network.NetworkSessionState
import com.multitetris.network.SessionMode
import com.multitetris.ui.skin.PlayerPalette
import com.multitetris.ui.skin.SkinRegistry
import com.multitetris.ui.theme.CyberCyan
import com.multitetris.ui.theme.ElectricGold
import com.multitetris.ui.theme.ElevatedCard
import com.multitetris.ui.theme.NeonMagenta
import com.multitetris.ui.theme.ObsidianBg
import com.multitetris.ui.theme.SlateSurface
import com.multitetris.ui.theme.SubtleBorder
import com.multitetris.ui.theme.TextPrimary
import com.multitetris.ui.theme.TextSecondary

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GameScreen(
    gameState: GameState,
    networkState: NetworkSessionState,
    matchReward: MatchRewardBreakdown? = null,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onSwitchFocus: () -> Unit,
    onSelectFocusPlayer: (Int) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onExitToHome: () -> Unit
) {
    val playerCount = gameState.config.playerCount
    val context = LocalContext.current
    DisposableEffect(playerCount) {
        val activity = context.findActivity()
        if (playerCount >= 3) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val controlledPlayerId = networkState.controlledPlayerId
    val focusAccent = PlayerPalette.accentFor(controlledPlayerId)
    val controlsEnabled = !gameState.isPaused &&
        !gameState.isGameOver &&
        networkState.status != ConnectionStatus.DISCONNECTED &&
        !gameState.isPlayerEliminated(controlledPlayerId) &&
        !gameState.isPlayerBlocked(controlledPlayerId)

    if (playerCount >= 3) {
        LandscapeGameLayout(
            gameState = gameState,
            networkState = networkState,
            matchReward = matchReward,
            controlledPlayerId = controlledPlayerId,
            focusAccent = focusAccent,
            controlsEnabled = controlsEnabled,
            isMuted = isMuted,
            onToggleMute = onToggleMute,
            onSwitchFocus = onSwitchFocus,
            onSelectFocusPlayer = onSelectFocusPlayer,
            onAction = onAction,
            onTogglePause = onTogglePause,
            onRestart = onRestart,
            onExitToHome = onExitToHome
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopControlBar(
                controlledPlayerId = controlledPlayerId,
                sessionMode = networkState.sessionMode,
                focusAccent = focusAccent,
                isPaused = gameState.isPaused,
                isMuted = isMuted,
                onToggleMute = onToggleMute,
                onBack = onExitToHome,
                onSwitchFocus = onSwitchFocus,
                onTogglePause = onTogglePause,
                onRestart = onRestart
            )

            Spacer(modifier = Modifier.height(6.dp))

            DualPlayerHud(
                gameState = gameState,
                networkState = networkState,
                controlledPlayerId = controlledPlayerId,
                onSelectPlayer = onSelectFocusPlayer
            )

            AnimatedVisibility(visible = networkState.activeSabotagedPlayerIds.isNotEmpty()) {
                val targetId = networkState.activeSabotagedPlayerIds.firstOrNull()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = ElectricGold.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ElectricGold)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = ElectricGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SABOTAGE PUSH! P${targetId ?: ""} SPEED BURST (0.5s)",
                            color = ElectricGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BoardWithOverlays(
                gameState = gameState,
                networkState = networkState,
                matchReward = matchReward,
                controlledPlayerId = controlledPlayerId,
                onTogglePause = onTogglePause,
                onRestart = onRestart,
                onExitToHome = onExitToHome,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            TouchControlsDeck(
                controlledPlayerId = controlledPlayerId,
                focusAccent = focusAccent,
                enabled = controlsEnabled,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun LandscapeGameLayout(
    gameState: GameState,
    networkState: NetworkSessionState,
    matchReward: MatchRewardBreakdown?,
    controlledPlayerId: Int,
    focusAccent: Color,
    controlsEnabled: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onSwitchFocus: () -> Unit,
    onSelectFocusPlayer: (Int) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onExitToHome: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val playerCount = gameState.config.playerCount
    val isTwoVTwo = gameState.mode == GameMode.VERSUS && gameState.versusSubMode == VersusSubMode.TEAMS_2V2

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Mode/Team Scoreboard + P1 & P3 HUD + Left-Thumb Left/Right Controls
        Column(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (isTwoVTwo) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = SlateSurface,
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "CYAN ${gameState.teamScore(TeamColor.CYAN)}",
                                color = CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "2v2",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "MAG ${gameState.teamScore(TeamColor.MAGENTA)}",
                                color = NeonMagenta,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = SlateSurface,
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${gameState.mode.label} ${playerCount}P",
                                color = if (gameState.mode == GameMode.VERSUS) NeonMagenta else CyberCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "LVL ${gameState.level} • ${gameState.sharedLines}L",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                LandscapePlayerHudCard(
                    gameState = gameState,
                    networkState = networkState,
                    playerId = 1,
                    isFocused = controlledPlayerId == 1,
                    onClick = { onSelectFocusPlayer(1) }
                )
                LandscapePlayerHudCard(
                    gameState = gameState,
                    networkState = networkState,
                    playerId = 3,
                    isFocused = controlledPlayerId == 3,
                    onClick = { onSelectFocusPlayer(3) }
                )
            }

            // Left-Thumb Directional Pad (LEFT + RIGHT)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ControlPadButton(
                    modifier = Modifier.weight(1f),
                    label = "LEFT",
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    accent = focusAccent,
                    enabled = controlsEnabled,
                    onClick = { onAction(PlayerAction.MOVE_LEFT) }
                )
                ControlPadButton(
                    modifier = Modifier.weight(1f),
                    label = "RIGHT",
                    icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    accent = focusAccent,
                    enabled = controlsEnabled,
                    onClick = { onAction(PlayerAction.MOVE_RIGHT) }
                )
            }
        }

        // Center Column: Compact Control Header + Wide 13x20 or 16x20 Board
        Column(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopControlBar(
                controlledPlayerId = controlledPlayerId,
                sessionMode = networkState.sessionMode,
                focusAccent = focusAccent,
                isPaused = gameState.isPaused,
                isMuted = isMuted,
                onToggleMute = onToggleMute,
                onBack = onExitToHome,
                onSwitchFocus = onSwitchFocus,
                onTogglePause = onTogglePause,
                onRestart = onRestart
            )
            Spacer(modifier = Modifier.height(4.dp))
            BoardWithOverlays(
                gameState = gameState,
                networkState = networkState,
                matchReward = matchReward,
                controlledPlayerId = controlledPlayerId,
                onTogglePause = onTogglePause,
                onRestart = onRestart,
                onExitToHome = onExitToHome,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // Right Column: P2 (& P4) HUD + Right-Thumb Rotate & Drop Controls
        Column(
            modifier = Modifier
                .weight(0.28f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LandscapePlayerHudCard(
                    gameState = gameState,
                    networkState = networkState,
                    playerId = 2,
                    isFocused = controlledPlayerId == 2,
                    onClick = { onSelectFocusPlayer(2) }
                )
                if (playerCount >= 4) {
                    LandscapePlayerHudCard(
                        gameState = gameState,
                        networkState = networkState,
                        playerId = 4,
                        isFocused = controlledPlayerId == 4,
                        onClick = { onSelectFocusPlayer(4) }
                    )
                }
                if (networkState.activeSabotagedPlayerIds.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = ElectricGold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ElectricGold)
                    ) {
                        Text(
                            text = "SABOTAGE P${networkState.activeSabotagedPlayerIds.joinToString(",")}!",
                            color = ElectricGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 6.dp)
                        )
                    }
                }
            }

            // Right-Thumb Action Pad (SOFT DROP, ROTATE, HARD DROP)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ControlPadButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = "HARD DROP",
                    icon = Icons.Rounded.VerticalAlignBottom,
                    accent = focusAccent,
                    highlighted = true,
                    enabled = controlsEnabled,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAction(PlayerAction.HARD_DROP)
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlPadButton(
                        modifier = Modifier.weight(1f),
                        label = "SOFT",
                        icon = Icons.Rounded.KeyboardArrowDown,
                        accent = focusAccent,
                        enabled = controlsEnabled,
                        onClick = { onAction(PlayerAction.SOFT_DROP) }
                    )
                    ControlPadButton(
                        modifier = Modifier.weight(1f),
                        label = "ROTATE",
                        icon = Icons.AutoMirrored.Rounded.RotateRight,
                        accent = focusAccent,
                        enabled = controlsEnabled,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAction(PlayerAction.ROTATE_CW)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapePlayerHudCard(
    gameState: GameState,
    networkState: NetworkSessionState,
    playerId: Int,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val team = gameState.playerTeams[playerId] ?: TeamColor.NONE
    val showTeam = gameState.mode == GameMode.VERSUS &&
        gameState.versusSubMode == VersusSubMode.TEAMS_2V2 &&
        team != TeamColor.NONE
    val accent = when {
        showTeam && team == TeamColor.CYAN -> CyberCyan
        showTeam && team == TeamColor.MAGENTA -> NeonMagenta
        else -> PlayerPalette.accentFor(playerId)
    }
    val endpoint = networkState.players.firstOrNull { it.playerId == playerId }
    val endpointName = endpoint?.displayName ?: "Player $playerId"
    val isSabotaged = playerId in networkState.activeSabotagedPlayerIds
    val isDisconnected = (endpoint?.isDisconnected == true) || gameState.isPlayerDisconnected(playerId)
    val isEliminated = gameState.isPlayerEliminated(playerId) || isDisconnected
    val isBlocked = gameState.isPlayerBlocked(playerId)
    val placement = gameState.placementFor(playerId)
    val scoreText = if (gameState.mode == GameMode.CO_OP) {
        "Shared ${gameState.sharedScore}"
    } else {
        "${gameState.playerScores[playerId] ?: 0} pts"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = when {
            isEliminated -> SlateSurface.copy(alpha = 0.55f)
            isFocused -> accent.copy(alpha = 0.14f)
            else -> SlateSurface
        },
        border = BorderStroke(
            width = if (isFocused || isSabotaged || isEliminated) 1.5.dp else 1.dp,
            color = when {
                isEliminated -> NeonMagenta.copy(alpha = 0.6f)
                isSabotaged -> ElectricGold
                isFocused -> accent
                else -> SubtleBorder
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "P$playerId",
                        color = if (isEliminated) TextSecondary else accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = endpointName,
                        color = if (isEliminated) TextSecondary else TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isDisconnected) TextDecoration.LineThrough else null,
                        modifier = if (isDisconnected) Modifier.alpha(0.5f) else Modifier
                    )
                }
                Text(
                    text = when {
                        isDisconnected -> "Disconnected"
                        isEliminated -> if (placement > 0) "OUT • #$placement" else "ELIMINATED"
                        isBlocked -> "WAIT CLEAR"
                        isSabotaged -> "SPEED BURST!"
                        else -> scoreText
                    },
                    color = when {
                        isEliminated -> NeonMagenta
                        isBlocked -> ElectricGold
                        isSabotaged -> ElectricGold
                        else -> TextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            if (isDisconnected) {
                LeftBadgePill()
            } else {
                NextPiecePreviewCanvas(
                    tetrominoType = gameState.nextPieces[playerId],
                    playerId = playerId,
                    skinId = gameState.playerSkins[playerId] ?: GameState.defaultSkinForPlayer(playerId),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun BoardWithOverlays(
    gameState: GameState,
    networkState: NetworkSessionState,
    matchReward: MatchRewardBreakdown?,
    controlledPlayerId: Int,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onExitToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val boardRatio = gameState.config.width.toFloat() / gameState.config.height.toFloat()
            val sizeModifier = if (maxWidth / maxHeight > boardRatio) {
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(boardRatio)
            } else {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(boardRatio)
            }

            Surface(
                modifier = sizeModifier,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF060911),
                border = BorderStroke(1.5.dp, SubtleBorder)
            ) {
                TetrisBoardCanvas(
                    gameState = gameState,
                    controlledPlayerId = controlledPlayerId,
                    sabotagedPlayers = networkState.activeSabotagedPlayerIds,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        val isLocalEliminated = gameState.isPlayerEliminated(controlledPlayerId)
        val isLocalBlocked = gameState.isPlayerBlocked(controlledPlayerId)

        var visibleFeedToast by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(gameState.feedToastEventId) {
            val raw = gameState.feedToastMessage
            if (gameState.feedToastEventId > 0L && raw != null && raw.isNotBlank()) {
                var resolved: String = raw
                networkState.players.forEach { player ->
                    val name = player.displayName
                    if (!name.isNullOrBlank()) {
                        resolved = resolved.replace("P${player.playerId}", name)
                    }
                }
                visibleFeedToast = resolved
                delay(2600L)
                visibleFeedToast = null
            }
        }

        if (!gameState.isGameOver && !gameState.isPaused && networkState.status != ConnectionStatus.DISCONNECTED) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLocalEliminated) {
                    val placement = gameState.placementFor(controlledPlayerId)
                    val placementSuffix = if (placement > 0) " • #$placement" else ""
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ObsidianBg.copy(alpha = 0.88f),
                        border = BorderStroke(1.2.dp, NeonMagenta)
                    ) {
                        Text(
                            text = "ELIMINATED$placementSuffix • SPECTATING",
                            color = NeonMagenta,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.7.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                } else if (isLocalBlocked) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ObsidianBg.copy(alpha = 0.88f),
                        border = BorderStroke(1.2.dp, ElectricGold)
                    ) {
                        Text(
                            text = "BLOCKED — WAITING FOR CLEAR...",
                            color = ElectricGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = visibleFeedToast != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    visibleFeedToast?.let { toastText ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SlateSurface.copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = toastText,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        if (networkState.status == ConnectionStatus.DISCONNECTED) {
            DisconnectedOverlay(
                message = networkState.disconnectMessage ?: if (networkState.sessionMode == SessionMode.CLIENT) {
                    "Host left or disconnected."
                } else {
                    "All opponents left the match."
                },
                onReturnHome = onExitToHome
            )
        } else if (gameState.isPaused && !gameState.isGameOver) {
            PauseOverlay(
                onResume = onTogglePause,
                onRestart = onRestart,
                onExit = onExitToHome
            )
        } else if (gameState.isGameOver) {
            GameOverOverlay(
                gameState = gameState,
                networkState = networkState,
                matchReward = matchReward,
                onPlayAgain = onRestart,
                onExit = onExitToHome
            )
        }
    }
}

@Composable
private fun TopControlBar(
    controlledPlayerId: Int,
    sessionMode: SessionMode,
    focusAccent: Color,
    isPaused: Boolean,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onBack: () -> Unit,
    onSwitchFocus: () -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit
) {
    val isLocalMode = sessionMode == SessionMode.LOCAL_SIMULATION
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to Home",
                tint = TextPrimary
            )
        }

        // Control Focus Toggle (Local 2P) or P2P Live Role Badge
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (isLocalMode) Modifier.clickable(onClick = onSwitchFocus) else Modifier
                ),
            shape = RoundedCornerShape(50),
            color = focusAccent.copy(alpha = 0.18f),
            border = BorderStroke(1.5.dp, focusAccent)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isLocalMode) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Switch Player Focus",
                        tint = focusAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "CONTROL: P$controlledPlayerId",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "(TAP)",
                        color = focusAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    val roleTag = if (sessionMode == SessionMode.HOST) {
                        "P1 HOST"
                    } else {
                        "P$controlledPlayerId GUEST"
                    }
                    Text(
                        text = "P2P • $roleTag",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onToggleMute,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (isMuted) "Unmute Sound" else "Mute Sound",
                    tint = if (isMuted) TextSecondary else CyberCyan
                )
            }
            if (isLocalMode || sessionMode == SessionMode.HOST) {
                IconButton(
                    onClick = onTogglePause,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (isPaused) "Resume" else "Pause",
                        tint = TextPrimary
                    )
                }
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Restart",
                        tint = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun DualPlayerHud(
    gameState: GameState,
    networkState: NetworkSessionState,
    controlledPlayerId: Int,
    onSelectPlayer: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerPreviewCard(
            modifier = Modifier.weight(1f),
            playerId = 1,
            spawnColLabel = "Col 2 • Z[1-4]",
            nextType = gameState.nextPieces[1],
            skinId = gameState.playerSkins[1] ?: "classic",
            score = if (gameState.mode == GameMode.VERSUS) gameState.playerScores[1] ?: 0 else null,
            isFocused = controlledPlayerId == 1,
            isPausedByPiece = gameState.activePieces[1]?.isPausedByPiece == true,
            isSabotaged = 1 in networkState.activeSabotagedPlayerIds,
            isEliminated = gameState.isPlayerEliminated(1),
            isBlocked = gameState.isPlayerBlocked(1),
            isDisconnected = (networkState.players.firstOrNull { it.playerId == 1 }?.isDisconnected == true) ||
                gameState.isPlayerDisconnected(1),
            displayName = networkState.players.firstOrNull { it.playerId == 1 }?.displayName,
            placement = gameState.placementFor(1),
            onClick = { onSelectPlayer(1) }
        )

        // Center Match Scoreboard
        Surface(
            modifier = Modifier.width(86.dp),
            shape = RoundedCornerShape(12.dp),
            color = SlateSurface,
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = gameState.mode.label,
                    color = if (gameState.mode == GameMode.VERSUS) NeonMagenta else CyberCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (gameState.mode == GameMode.CO_OP) "${gameState.sharedScore}" else "LVL ${gameState.level}",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (gameState.mode == GameMode.CO_OP) {
                        "L${gameState.level} • ${gameState.sharedLines}L"
                    } else {
                        "${gameState.sharedLines} LINES"
                    },
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        PlayerPreviewCard(
            modifier = Modifier.weight(1f),
            playerId = 2,
            spawnColLabel = "Col 6 • Z[5-8]",
            nextType = gameState.nextPieces[2],
            skinId = gameState.playerSkins[2] ?: "neon_cyber",
            score = if (gameState.mode == GameMode.VERSUS) gameState.playerScores[2] ?: 0 else null,
            isFocused = controlledPlayerId == 2,
            isPausedByPiece = gameState.activePieces[2]?.isPausedByPiece == true,
            isSabotaged = 2 in networkState.activeSabotagedPlayerIds,
            isEliminated = gameState.isPlayerEliminated(2),
            isBlocked = gameState.isPlayerBlocked(2),
            isDisconnected = (networkState.players.firstOrNull { it.playerId == 2 }?.isDisconnected == true) ||
                gameState.isPlayerDisconnected(2),
            displayName = networkState.players.firstOrNull { it.playerId == 2 }?.displayName,
            placement = gameState.placementFor(2),
            onClick = { onSelectPlayer(2) }
        )
    }
}

@Composable
private fun PlayerPreviewCard(
    modifier: Modifier = Modifier,
    playerId: Int,
    spawnColLabel: String,
    nextType: TetrominoType?,
    skinId: String,
    score: Int?,
    isFocused: Boolean,
    isPausedByPiece: Boolean,
    isSabotaged: Boolean,
    isEliminated: Boolean = false,
    isBlocked: Boolean = false,
    isDisconnected: Boolean = false,
    displayName: String? = null,
    placement: Int = 0,
    onClick: () -> Unit
) {
    val accent = PlayerPalette.accentFor(playerId)
    val effectiveEliminated = isEliminated || isDisconnected
    val borderColor = when {
        effectiveEliminated -> NeonMagenta
        isBlocked -> ElectricGold
        isSabotaged -> ElectricGold
        isFocused -> accent
        else -> SubtleBorder
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused && !isDisconnected) accent.copy(alpha = 0.1f) else SlateSurface,
        border = BorderStroke(if (isFocused || isSabotaged || effectiveEliminated) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayName?.takeIf { it.isNotBlank() } ?: "P$playerId",
                        color = if (isDisconnected) TextSecondary else accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isDisconnected) TextDecoration.LineThrough else null,
                        modifier = if (isDisconnected) Modifier.alpha(0.5f) else Modifier
                    )
                    if (isFocused && !isDisconnected) {
                        Text(
                            text = "ACTIVE",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = when {
                        isDisconnected -> "Disconnected"
                        effectiveEliminated -> if (placement > 0) "OUT • #$placement" else "ELIMINATED"
                        isBlocked -> "WAIT CLEAR"
                        isSabotaged -> "SPEED BURST!"
                        isPausedByPiece -> "WAIT PIECE"
                        score != null -> "$score pts"
                        else -> spawnColLabel
                    },
                    color = when {
                        effectiveEliminated -> NeonMagenta
                        isBlocked -> ElectricGold
                        isSabotaged -> ElectricGold
                        isPausedByPiece -> ElectricGold
                        else -> TextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = if (isSabotaged || isPausedByPiece || effectiveEliminated || isBlocked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }

            if (isDisconnected) {
                LeftBadgePill()
            } else {
                NextPiecePreviewCanvas(
                    tetrominoType = nextType,
                    playerId = playerId,
                    skinId = skinId,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun LeftBadgePill() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = NeonMagenta.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.7f))
    ) {
        Text(
            text = "LEFT",
            color = NeonMagenta,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun NextPiecePreviewCanvas(
    tetrominoType: TetrominoType?,
    playerId: Int,
    skinId: String,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (tetrominoType == null) return@Canvas
        val cells = tetrominoType.offsetsFor(0)
        val minX = cells.minOf { it.x }
        val maxX = cells.maxOf { it.x }
        val minY = cells.minOf { it.y }
        val maxY = cells.maxOf { it.y }
        val spanX = (maxX - minX + 1).coerceAtLeast(1)
        val spanY = (maxY - minY + 1).coerceAtLeast(1)

        val cellSize = minOf(size.width / 4f, size.height / 3f)
        val totalW = spanX * cellSize
        val totalH = spanY * cellSize
        val startX = (size.width - totalW) / 2f
        val startY = (size.height - totalH) / 2f

        val renderer = SkinRegistry.resolve(skinId)
        for (cell in cells) {
            val drawX = startX + (cell.x - minX) * cellSize
            val drawY = startY + (cell.y - minY) * cellSize
            with(renderer) {
                drawBlock(
                    topLeft = Offset(drawX, drawY),
                    size = Size(cellSize, cellSize),
                    tetrominoType = tetrominoType,
                    playerId = playerId,
                    isGhost = false
                )
            }
        }
    }
}

@Composable
fun TetrisBoardCanvas(
    gameState: GameState,
    controlledPlayerId: Int,
    sabotagedPlayers: Set<Int>,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "board_fx")
    val animPhaseMs by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 60_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 60_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skin_anim_clock"
    )
    val animTimeMillis = animPhaseMs.toLong()
    val sabotagePulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sabotage_pulse"
    )

    val lineClearFlash = remember { Animatable(0f) }
    LaunchedEffect(gameState.lineClearEventId) {
        if (gameState.lineClearEventId > 0L && gameState.lastClearedRowIndices.isNotEmpty()) {
            lineClearFlash.snapTo(0f)
            lineClearFlash.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }
    }

    Canvas(modifier = modifier) {
        val cols = gameState.config.width
        val rows = gameState.config.height
        val cellW = size.width / cols.toFloat()
        val cellH = size.height / rows.toFloat()
        val cellSize = Size(cellW, cellH)

        // Subtle spawn zone background highlights (cols 1..4 for P1, 5..8 for P2, etc.)
        for (zone in gameState.config.spawnZones) {
            val isZoneSabotaged = zone.playerId in sabotagedPlayers
            val zoneColor = if (isZoneSabotaged) ElectricGold else PlayerPalette.accentFor(zone.playerId)
            val zoneLeft = zone.columnRange.first * cellW
            val zoneWidth = (zone.columnRange.last - zone.columnRange.first + 1) * cellW
            drawRect(
                color = zoneColor.copy(alpha = if (isZoneSabotaged) 0.12f * sabotagePulse else 0.04f),
                topLeft = Offset(zoneLeft, 0f),
                size = Size(zoneWidth, cellH * 2f)
            )
            // Top spawn zone danger threshold line & drop column tick
            drawLine(
                color = zoneColor.copy(alpha = if (isZoneSabotaged) 0.85f * sabotagePulse else 0.45f),
                start = Offset(zoneLeft + 2f, cellH),
                end = Offset(zoneLeft + zoneWidth - 2f, cellH),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            // Exact spawn drop-point marker bar at top of spawnX
            drawRect(
                color = zoneColor.copy(alpha = 0.85f),
                topLeft = Offset(zone.spawnX * cellW + cellW * 0.15f, 1f),
                size = Size(cellW * 0.7f, 4f)
            )
        }

        // Crisp grid lines
        val gridLineColor = Color(0xFF162138)
        for (c in 1 until cols) {
            val x = c * cellW
            drawLine(
                color = gridLineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
        for (r in 1 until rows) {
            val y = r * cellH
            drawLine(
                color = gridLineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }

        // 1. Draw locked blocks on board (preserving each player's skinId)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cell = gameState.grid[r][c] ?: continue
                val renderer = SkinRegistry.resolve(cell.skinId)
                with(renderer) {
                    drawBlockCell(
                        topLeft = Offset(c * cellW, r * cellH),
                        size = cellSize,
                        gridX = c,
                        gridY = r,
                        tetrominoType = cell.tetrominoType,
                        playerId = cell.playerId,
                        isGhost = false,
                        animTimeMillis = animTimeMillis
                    )
                }
            }
        }

        // 2. Draw ghost pieces for all active players
        for (ghost in gameState.ghostPieces.values) {
            val renderer = SkinRegistry.resolve(ghost.skinId)
            for (cell in ghost.occupiedCells()) {
                if (cell.x in 0 until cols && cell.y in 0 until rows) {
                    with(renderer) {
                        drawBlockCell(
                            topLeft = Offset(cell.x * cellW, cell.y * cellH),
                            size = cellSize,
                            gridX = cell.x,
                            gridY = cell.y,
                            tetrominoType = ghost.tetrominoType,
                            playerId = ghost.playerId,
                            isGhost = true,
                            animTimeMillis = animTimeMillis
                        )
                    }
                }
            }
        }

        // 3. Draw active falling pieces for all players
        for (piece in gameState.activePieces.values) {
            val renderer = SkinRegistry.resolve(piece.skinId)
            val isFocused = piece.playerId == controlledPlayerId
            val isSabotaged = piece.playerId in sabotagedPlayers

            for (cell in piece.occupiedCells()) {
                if (cell.x in 0 until cols && cell.y in 0 until rows) {
                    val topLeft = Offset(cell.x * cellW, cell.y * cellH)
                    with(renderer) {
                        drawBlockCell(
                            topLeft = topLeft,
                            size = cellSize,
                            gridX = cell.x,
                            gridY = cell.y,
                            tetrominoType = piece.tetrominoType,
                            playerId = piece.playerId,
                            isGhost = false,
                            isPausedOnPiece = piece.isPausedByPiece,
                            animTimeMillis = animTimeMillis
                        )
                    }
                    if (isFocused) {
                        drawRect(
                            color = Color.White.copy(alpha = 0.55f),
                            topLeft = Offset(topLeft.x + 1f, topLeft.y + 1f),
                            size = Size(cellW - 2f, cellH - 2f),
                            style = Stroke(width = 1.6f)
                        )
                    }
                    if (isSabotaged) {
                        drawRect(
                            color = ElectricGold.copy(alpha = 0.24f * sabotagePulse),
                            topLeft = Offset(topLeft.x - 2f, topLeft.y - 2f),
                            size = Size(cellW + 4f, cellH + 4f)
                        )
                        drawRect(
                            color = ElectricGold.copy(alpha = 0.92f * sabotagePulse),
                            topLeft = Offset(topLeft.x + 1f, topLeft.y + 1f),
                            size = Size(cellW - 2f, cellH - 2f),
                            style = Stroke(width = 2.6f)
                        )
                    }
                }
            }
        }

        // 4. Line-clear flash bar & particle burst animation
        val clearProgress = lineClearFlash.value
        if (gameState.lineClearEventId > 0L && clearProgress > 0f && clearProgress < 1f) {
            val fade = 1f - clearProgress
            for (rowIdx in gameState.lastClearedRowIndices) {
                if (rowIdx !in 0 until rows) continue
                val rowY = rowIdx * cellH
                drawRect(
                    color = Color.White.copy(alpha = 0.65f * fade),
                    topLeft = Offset(0f, rowY),
                    size = Size(size.width, cellH)
                )
                drawRect(
                    color = CyberCyan.copy(alpha = 0.85f * fade),
                    topLeft = Offset(0f, rowY + cellH * 0.25f),
                    size = Size(size.width, cellH * 0.5f)
                )

                // Burst particles radiating from each column in the cleared row
                for (c in 0 until cols) {
                    val baseX = (c + 0.5f) * cellW
                    val baseY = rowY + cellH * 0.5f
                    val dirX = if (c % 2 == 0) 1f else -1f
                    val dirY = if ((c + rowIdx) % 2 == 0) -1f else 1f
                    val spread = clearProgress * cellW * 1.4f
                    val pSize = (cellW * 0.22f * fade).coerceAtLeast(2f)

                    drawRect(
                        color = if (c % 2 == 0) CyberCyan.copy(alpha = fade) else ElectricGold.copy(alpha = fade),
                        topLeft = Offset(
                            baseX + dirX * spread * 0.65f - pSize / 2f,
                            baseY + dirY * spread - pSize / 2f
                        ),
                        size = Size(pSize, pSize)
                    )
                }
            }
        }
    }
}

@Composable
private fun TouchControlsDeck(
    controlledPlayerId: Int,
    focusAccent: Color,
    enabled: Boolean,
    onAction: (PlayerAction) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = "LEFT",
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            accent = focusAccent,
            enabled = enabled,
            onClick = { onAction(PlayerAction.MOVE_LEFT) }
        )
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = "SOFT",
            icon = Icons.Rounded.KeyboardArrowDown,
            accent = focusAccent,
            enabled = enabled,
            onClick = { onAction(PlayerAction.SOFT_DROP) }
        )
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = "RIGHT",
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            accent = focusAccent,
            enabled = enabled,
            onClick = { onAction(PlayerAction.MOVE_RIGHT) }
        )
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = "ROTATE",
            icon = Icons.AutoMirrored.Rounded.RotateRight,
            accent = focusAccent,
            enabled = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(PlayerAction.ROTATE_CW)
            }
        )
        ControlPadButton(
            modifier = Modifier.weight(1.05f),
            label = "DROP",
            icon = Icons.Rounded.VerticalAlignBottom,
            accent = focusAccent,
            highlighted = true,
            enabled = enabled,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAction(PlayerAction.HARD_DROP)
            }
        )
    }
}

@Composable
private fun ControlPadButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    accent: Color,
    highlighted: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (highlighted) accent.copy(alpha = 0.24f) else ElevatedCard,
            contentColor = TextPrimary,
            disabledContainerColor = SlateSurface,
            disabledContentColor = TextSecondary.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.2.dp, if (enabled) accent.copy(alpha = if (highlighted) 0.9f else 0.45f) else SubtleBorder)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) accent else TextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    OverlayCard(title = "PAUSED") {
        Button(
            onClick = onResume,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBg)
        ) {
            Text("Resume", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Text("Restart Match", color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Text("Home Screen", color = TextSecondary)
        }
    }
}

@Composable
private fun GameOverOverlay(
    gameState: GameState,
    networkState: NetworkSessionState,
    matchReward: MatchRewardBreakdown?,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    val winnerId = gameState.winnerPlayerId
    val winTeam = gameState.winningTeam
    val isTwoVTwo = gameState.mode == GameMode.VERSUS && gameState.versusSubMode == VersusSubMode.TEAMS_2V2
    val title = when {
        isTwoVTwo && winTeam != null -> "${winTeam.label.uppercase()} WINS!"
        gameState.mode == GameMode.VERSUS && winnerId != null -> "PLAYER $winnerId WINS!"
        else -> "GAME OVER"
    }
    val titleColor = when {
        isTwoVTwo && winTeam == TeamColor.CYAN -> CyberCyan
        isTwoVTwo && winTeam == TeamColor.MAGENTA -> NeonMagenta
        winnerId != null -> PlayerPalette.accentFor(winnerId)
        else -> NeonMagenta
    }

    OverlayCard(title = title, titleColor = titleColor) {
        gameState.statusMessage?.let { msg ->
            Text(
                text = msg,
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (gameState.mode == GameMode.CO_OP) {
            Text(
                text = "Score: ${gameState.sharedScore} • Lines: ${gameState.sharedLines} • Level ${gameState.level}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else if (isTwoVTwo) {
            Text(
                text = "Team Cyan: ${gameState.teamScore(TeamColor.CYAN)} pts  |  Team Magenta: ${gameState.teamScore(TeamColor.MAGENTA)} pts",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else {
            val rankedIds = (1..gameState.config.playerCount).sortedWith(
                compareBy<Int> {
                    val p = gameState.placementFor(it)
                    if (p > 0) p else Int.MAX_VALUE
                }.thenByDescending { gameState.playerScores[it] ?: 0 }
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rankedIds.forEach { pid ->
                    val endpoint = networkState.players.firstOrNull { it.playerId == pid }
                    val isDisconnected = (endpoint?.isDisconnected == true) || gameState.isPlayerDisconnected(pid)
                    val displayName = endpoint?.displayName?.takeIf { it.isNotBlank() } ?: "Player $pid"
                    val placement = gameState.placementFor(pid)
                    val rankPrefix = if (placement > 0) "#$placement  " else ""
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$rankPrefix$displayName",
                            color = if (isDisconnected) TextSecondary else PlayerPalette.accentFor(pid),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textDecoration = if (isDisconnected) TextDecoration.LineThrough else null,
                            modifier = if (isDisconnected) Modifier.alpha(0.5f) else Modifier
                        )
                        Text(
                            text = if (isDisconnected) "Disconnected" else "${gameState.playerScores[pid] ?: 0} pts",
                            color = if (isDisconnected) NeonMagenta else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (matchReward != null) {
            Spacer(modifier = Modifier.height(12.dp))
            MatchRewardsCard(reward = matchReward)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onPlayAgain,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = titleColor, contentColor = ObsidianBg)
        ) {
            Text("Play Again", fontWeight = FontWeight.ExtraBold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Text("Back to Home", color = TextPrimary)
        }
    }
}

@Composable
private fun MatchRewardsCard(reward: MatchRewardBreakdown) {
    var targetTotal by remember(reward) { mutableIntStateOf(0) }
    LaunchedEffect(reward) {
        targetTotal = reward.totalCoins
    }
    val animatedTotal by animateIntAsState(
        targetValue = targetTotal,
        animationSpec = tween(durationMillis = 750),
        label = "match_reward_counter"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MATCH REWARDS",
                    color = ElectricGold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                if (reward.isHalfRateLocal) {
                    Text(
                        text = "50% Local Rate",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (reward.lines.isEmpty()) {
                Text(
                    text = reward.eligibilityReason ?: "No coins earned this round",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            } else {
                reward.lines.forEach { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "+${line.amount} ${line.label}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "+${line.amount}",
                            color = ElectricGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Earned",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total: +$animatedTotal Coins",
                        color = ElectricGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DisconnectedOverlay(
    message: String,
    onReturnHome: () -> Unit
) {
    OverlayCard(title = "Match Disconnected", titleColor = ElectricGold) {
        Text(
            text = message,
            color = TextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onReturnHome,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberCyan,
                contentColor = ObsidianBg
            )
        ) {
            Text("Return to Home", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun OverlayCard(
    title: String,
    titleColor: Color = TextPrimary,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.84f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(1.5.dp, titleColor.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
    }
}
