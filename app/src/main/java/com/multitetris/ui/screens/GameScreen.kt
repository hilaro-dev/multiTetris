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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.audio.HapticHelper
import com.multitetris.economy.MatchRewardBreakdown
import com.multitetris.economy.MatchRewardCalculator
import com.multitetris.game.ActiveSabotageState
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.game.SabotageType
import com.multitetris.game.TeamColor
import com.multitetris.game.TetrominoType
import com.multitetris.game.VersusSubMode
import com.multitetris.network.ConnectionStatus
import com.multitetris.network.NetworkSessionState
import com.multitetris.network.SessionMode
import com.multitetris.ui.skin.PlayerPalette
import com.multitetris.ui.skin.SkinRegistry
import com.multitetris.ui.i18n.LocalStrings
import com.multitetris.ui.theme.CyberCyan
import com.multitetris.ui.theme.ElectricGold
import com.multitetris.ui.theme.ElevatedCard
import com.multitetris.ui.theme.NeonMagenta
import com.multitetris.ui.theme.ObsidianBg
import com.multitetris.ui.theme.SlateSurface
import com.multitetris.ui.theme.SubtleBorder
import com.multitetris.ui.theme.TextPrimary
import com.multitetris.ui.theme.TextSecondary
import kotlin.math.cos
import kotlin.math.sin

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
    isNewCoopRecord: Boolean = false,
    isMuted: Boolean = false,
    vibrationEnabled: Boolean = true,
    onToggleMute: () -> Unit = {},
    onSwitchFocus: () -> Unit,
    onSelectFocusPlayer: (Int) -> Unit,
    onAction: (PlayerAction) -> Unit,
    onUseSabotage: (Int, SabotageType) -> Unit = { _, _ -> },
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
    onExitToHome: () -> Unit
) {
    val playerCount = gameState.config.playerCount
    val context = LocalContext.current
    var lastHapticClearEventId by remember { mutableStateOf(gameState.lineClearEventId) }
    LaunchedEffect(gameState.lineClearEventId, vibrationEnabled) {
        if (gameState.lineClearEventId > lastHapticClearEventId) {
            lastHapticClearEventId = gameState.lineClearEventId
            HapticHelper.vibrateLineClear(
                context = context,
                linesCleared = gameState.lastClearedRowIndices.size.coerceAtLeast(1),
                vibrationEnabled = vibrationEnabled
            )
        } else if (gameState.lineClearEventId == 0L) {
            lastHapticClearEventId = 0L
        }
    }
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

    val localActiveSabotage = gameState.activeSabotageFor(controlledPlayerId)
    val isFrozenDebuff = localActiveSabotage?.type == SabotageType.BUTTON_FREEZE
    val isFlippedDebuff = localActiveSabotage?.type == SabotageType.CONTROLS_FLIP
    val isJammedDebuff = localActiveSabotage?.type == SabotageType.DROP_JAMMER
    val equippedSabotages = gameState.equippedSabotagesFor(controlledPlayerId)
    val usedSabotages = gameState.usedSabotagesFor(controlledPlayerId)
    val validSabotageTargets = gameState.validSabotageTargetsFor(controlledPlayerId)
    val showVersusSabotageDeck = gameState.sabotageEnabled &&
        gameState.mode == GameMode.VERSUS &&
        equippedSabotages.isNotEmpty() &&
        !gameState.isPlayerEliminated(controlledPlayerId) &&
        !gameState.isPlayerDisconnected(controlledPlayerId)

    var pendingSabotage by remember { mutableStateOf<SabotageType?>(null) }
    LaunchedEffect(
        controlledPlayerId,
        controlsEnabled,
        validSabotageTargets,
        usedSabotages,
        showVersusSabotageDeck
    ) {
        if (!controlsEnabled ||
            !showVersusSabotageDeck ||
            validSabotageTargets.isEmpty() ||
            pendingSabotage in usedSabotages
        ) {
            pendingSabotage = null
        }
    }

    val handleTapSabotage: (SabotageType) -> Unit = { sabotageType ->
        if (controlsEnabled && showVersusSabotageDeck && sabotageType !in usedSabotages) {
            when {
                validSabotageTargets.size == 1 -> {
                    pendingSabotage = null
                    onUseSabotage(validSabotageTargets.first(), sabotageType)
                }
                validSabotageTargets.size > 1 -> {
                    pendingSabotage = if (pendingSabotage == sabotageType) null else sabotageType
                }
            }
        }
    }

    val handlePlayerCardClick: (Int) -> Unit = { targetPlayerId ->
        val pending = pendingSabotage
        if (pending != null &&
            controlsEnabled &&
            targetPlayerId in validSabotageTargets
        ) {
            pendingSabotage = null
            onUseSabotage(targetPlayerId, pending)
        } else {
            onSelectFocusPlayer(targetPlayerId)
        }
    }

    if (playerCount >= 3) {
        LandscapeGameLayout(
            gameState = gameState,
            networkState = networkState,
            matchReward = matchReward,
            isNewCoopRecord = isNewCoopRecord,
            controlledPlayerId = controlledPlayerId,
            focusAccent = focusAccent,
            controlsEnabled = controlsEnabled,
            showVersusSabotageDeck = showVersusSabotageDeck,
            equippedSabotages = equippedSabotages,
            usedSabotages = usedSabotages,
            validSabotageTargets = validSabotageTargets,
            pendingSabotage = pendingSabotage,
            isFrozenDebuff = isFrozenDebuff,
            isFlippedDebuff = isFlippedDebuff,
            isJammedDebuff = isJammedDebuff,
            isMuted = isMuted,
            vibrationEnabled = vibrationEnabled,
            onToggleMute = onToggleMute,
            onSwitchFocus = onSwitchFocus,
            onSelectPlayerCard = handlePlayerCardClick,
            onTapSabotage = handleTapSabotage,
            onCancelSabotageTarget = { pendingSabotage = null },
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
                mysteryEnabled = gameState.mysteryEnabled,
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
                pendingSabotage = pendingSabotage,
                validSabotageTargets = validSabotageTargets,
                onSelectPlayer = handlePlayerCardClick
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
                isNewCoopRecord = isNewCoopRecord,
                controlledPlayerId = controlledPlayerId,
                onTogglePause = onTogglePause,
                onRestart = onRestart,
                onExitToHome = onExitToHome,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (showVersusSabotageDeck) {
                Spacer(modifier = Modifier.height(6.dp))
                SabotageHudDeck(
                    equippedSabotages = equippedSabotages,
                    usedSabotages = usedSabotages,
                    pendingSabotage = pendingSabotage,
                    validTargets = validSabotageTargets,
                    targetLabels = validSabotageTargets.associateWith { id ->
                        networkState.players.firstOrNull { it.playerId == id }
                            ?.displayName
                            ?.takeIf { it.isNotBlank() }
                            ?: "P$id"
                    },
                    enabled = controlsEnabled,
                    compact = false,
                    onTapSabotage = handleTapSabotage,
                    onPickTarget = handlePlayerCardClick,
                    onCancelTarget = { pendingSabotage = null }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TouchControlsDeck(
                controlledPlayerId = controlledPlayerId,
                focusAccent = focusAccent,
                enabled = controlsEnabled,
                isFrozen = isFrozenDebuff,
                isFlipped = isFlippedDebuff,
                isDropJammed = isJammedDebuff,
                vibrationEnabled = vibrationEnabled,
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
    isNewCoopRecord: Boolean = false,
    controlledPlayerId: Int,
    focusAccent: Color,
    controlsEnabled: Boolean,
    showVersusSabotageDeck: Boolean,
    equippedSabotages: List<SabotageType>,
    usedSabotages: Set<SabotageType>,
    validSabotageTargets: List<Int>,
    pendingSabotage: SabotageType?,
    isFrozenDebuff: Boolean,
    isFlippedDebuff: Boolean,
    isJammedDebuff: Boolean,
    isMuted: Boolean,
    vibrationEnabled: Boolean,
    onToggleMute: () -> Unit,
    onSwitchFocus: () -> Unit,
    onSelectPlayerCard: (Int) -> Unit,
    onTapSabotage: (SabotageType) -> Unit,
    onCancelSabotageTarget: () -> Unit,
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
                                text = if (playerCount == 4) "2v2" else "TEAMS",
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
                    activeSabotage = gameState.activeSabotageFor(1),
                    pendingSabotageType = pendingSabotage,
                    isTargetable = pendingSabotage != null && 1 in validSabotageTargets,
                    onClick = { onSelectPlayerCard(1) }
                )
                LandscapePlayerHudCard(
                    gameState = gameState,
                    networkState = networkState,
                    playerId = 3,
                    isFocused = controlledPlayerId == 3,
                    activeSabotage = gameState.activeSabotageFor(3),
                    pendingSabotageType = pendingSabotage,
                    isTargetable = pendingSabotage != null && 3 in validSabotageTargets,
                    onClick = { onSelectPlayerCard(3) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showVersusSabotageDeck) {
                    SabotageHudDeck(
                        equippedSabotages = equippedSabotages,
                        usedSabotages = usedSabotages,
                        pendingSabotage = pendingSabotage,
                        validTargets = validSabotageTargets,
                        targetLabels = validSabotageTargets.associateWith { id ->
                            networkState.players.firstOrNull { it.playerId == id }
                                ?.displayName
                                ?.takeIf { it.isNotBlank() }
                                ?: "P$id"
                        },
                        enabled = controlsEnabled,
                        compact = true,
                        onTapSabotage = onTapSabotage,
                        onPickTarget = onSelectPlayerCard,
                        onCancelTarget = onCancelSabotageTarget
                    )
                }

                // Left-Thumb Directional Pad (LEFT + RIGHT)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ControlPadButton(
                        modifier = Modifier.weight(1f),
                        label = LocalStrings.current.leftBtn,
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        accent = focusAccent,
                        enabled = controlsEnabled,
                        isFrozen = isFrozenDebuff,
                        isFlipped = isFlippedDebuff,
                        onClick = { onAction(PlayerAction.MOVE_LEFT) }
                    )
                    ControlPadButton(
                        modifier = Modifier.weight(1f),
                        label = LocalStrings.current.rightBtn,
                        icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        accent = focusAccent,
                        enabled = controlsEnabled,
                        isFrozen = isFrozenDebuff,
                        isFlipped = isFlippedDebuff,
                        onClick = { onAction(PlayerAction.MOVE_RIGHT) }
                    )
                }
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
                mysteryEnabled = gameState.mysteryEnabled,
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
                isNewCoopRecord = isNewCoopRecord,
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
                    activeSabotage = gameState.activeSabotageFor(2),
                    pendingSabotageType = pendingSabotage,
                    isTargetable = pendingSabotage != null && 2 in validSabotageTargets,
                    onClick = { onSelectPlayerCard(2) }
                )
                if (playerCount >= 4) {
                    LandscapePlayerHudCard(
                        gameState = gameState,
                        networkState = networkState,
                        playerId = 4,
                        isFocused = controlledPlayerId == 4,
                        activeSabotage = gameState.activeSabotageFor(4),
                        pendingSabotageType = pendingSabotage,
                        isTargetable = pendingSabotage != null && 4 in validSabotageTargets,
                        onClick = { onSelectPlayerCard(4) }
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
                    label = if (isJammedDebuff) LocalStrings.current.statusJammed else LocalStrings.current.dropBtn,
                    icon = Icons.Rounded.VerticalAlignBottom,
                    accent = focusAccent,
                    highlighted = true,
                    enabled = controlsEnabled && !isJammedDebuff,
                    isFrozen = isFrozenDebuff,
                    onClick = {
                        if (vibrationEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                        isFrozen = isFrozenDebuff,
                        onClick = { onAction(PlayerAction.SOFT_DROP) }
                    )
                    ControlPadButton(
                        modifier = Modifier.weight(1f),
                        label = LocalStrings.current.rotateBtn,
                        icon = Icons.AutoMirrored.Rounded.RotateRight,
                        accent = focusAccent,
                        enabled = controlsEnabled,
                        isFrozen = isFrozenDebuff,
                        onClick = {
                            if (vibrationEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
    activeSabotage: ActiveSabotageState? = null,
    pendingSabotageType: SabotageType? = null,
    isTargetable: Boolean = false,
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
    val targetHighlightColor = pendingSabotageType?.let { sabotageAccentColor(it) } ?: NeonMagenta
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
            isTargetable -> targetHighlightColor.copy(alpha = 0.22f)
            isEliminated -> SlateSurface.copy(alpha = 0.55f)
            isFocused -> accent.copy(alpha = 0.14f)
            else -> SlateSurface
        },
        border = BorderStroke(
            width = if (isTargetable || isFocused || isSabotaged || activeSabotage != null || isEliminated) 1.5.dp else 1.dp,
            color = when {
                isTargetable -> targetHighlightColor
                isEliminated -> NeonMagenta.copy(alpha = 0.6f)
                activeSabotage != null -> sabotageAccentColor(activeSabotage.type)
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
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(if (isDisconnected) Modifier.alpha(0.5f) else Modifier)
                    )
                    if (activeSabotage != null) {
                        ActiveSabotageStatusPill(activeSabotage = activeSabotage)
                    }
                }
                Text(
                    text = when {
                        isTargetable -> "TAP TO TARGET!"
                        isDisconnected -> "Disconnected"
                        isEliminated -> if (placement > 0) "OUT • #$placement" else "ELIMINATED"
                        isBlocked -> "WAIT CLEAR"
                        isSabotaged -> "SPEED BURST!"
                        else -> scoreText
                    },
                    color = when {
                        isTargetable -> targetHighlightColor
                        isEliminated -> NeonMagenta
                        isBlocked -> ElectricGold
                        isSabotaged -> ElectricGold
                        else -> TextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = if (isTargetable) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
            if (isDisconnected) {
                LeftBadgePill()
            } else if (activeSabotage?.type == SabotageType.DROP_JAMMER) {
                JammedNextPieceBox(modifier = Modifier.size(28.dp))
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
    isNewCoopRecord: Boolean = false,
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

            val localBlindfold = gameState.activeSabotageFor(controlledPlayerId)
                ?.takeIf { it.type == SabotageType.BLINDFOLD_FOG }

            Surface(
                modifier = sizeModifier,
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF060911),
                border = BorderStroke(1.5.dp, SubtleBorder)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    TetrisBoardCanvas(
                        gameState = gameState,
                        controlledPlayerId = controlledPlayerId,
                        sabotagedPlayers = networkState.activeSabotagedPlayerIds,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (localBlindfold != null &&
                        !gameState.isPaused &&
                        !gameState.isGameOver &&
                        networkState.status != ConnectionStatus.DISCONNECTED
                    ) {
                        Surface(
                            modifier = Modifier.align(Alignment.Center),
                            shape = RoundedCornerShape(999.dp),
                            color = Color(0xCC0B1026),
                            border = BorderStroke(1.2.dp, Color(0xFF94A3B8).copy(alpha = 0.75f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VisibilityOff,
                                    contentDescription = "Blinded",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${LocalStrings.current.statusBlinded} • ${localBlindfold.remainingSeconds}s",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
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
                            text = if (LocalStrings.current.gotIt == "Anladım") "ELENDİN$placementSuffix • İZLENİYOR" else "ELIMINATED$placementSuffix • SPECTATING",
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
                            text = if (LocalStrings.current.gotIt == "Anladım") "ENGELLENDİ — SATIR TEMİZLENMESİ BEKLENİYOR..." else "BLOCKED — WAITING FOR CLEAR...",
                            color = ElectricGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = gameState.sabotageEnabled &&
                        gameState.mode == GameMode.CO_OP &&
                        gameState.systemGlitchWarningActive,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFF2B0B1E).copy(alpha = 0.94f),
                        border = BorderStroke(1.4.dp, NeonMagenta)
                    ) {
                        Text(
                            text = "⚠️ ${LocalStrings.current.systemGlitchIncoming}",
                            color = NeonMagenta,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
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
                isNewCoopRecord = isNewCoopRecord,
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
    onRestart: () -> Unit,
    mysteryEnabled: Boolean = false
) {
    val isLocalMode = sessionMode == SessionMode.LOCAL_SIMULATION
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
        if (mysteryEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFA855F7).copy(alpha = 0.24f),
                border = BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.75f))
            ) {
                Text(
                    text = "👻 ${LocalStrings.current.mysteryModeTitle.uppercase()}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DualPlayerHud(
    gameState: GameState,
    networkState: NetworkSessionState,
    controlledPlayerId: Int,
    pendingSabotage: SabotageType? = null,
    validSabotageTargets: List<Int> = emptyList(),
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
            activeSabotage = gameState.activeSabotageFor(1),
            pendingSabotageType = pendingSabotage,
            isTargetable = pendingSabotage != null && 1 in validSabotageTargets,
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
            activeSabotage = gameState.activeSabotageFor(2),
            pendingSabotageType = pendingSabotage,
            isTargetable = pendingSabotage != null && 2 in validSabotageTargets,
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
    activeSabotage: ActiveSabotageState? = null,
    pendingSabotageType: SabotageType? = null,
    isTargetable: Boolean = false,
    isEliminated: Boolean = false,
    isBlocked: Boolean = false,
    isDisconnected: Boolean = false,
    displayName: String? = null,
    placement: Int = 0,
    onClick: () -> Unit
) {
    val accent = PlayerPalette.accentFor(playerId)
    val effectiveEliminated = isEliminated || isDisconnected
    val targetHighlightColor = pendingSabotageType?.let { sabotageAccentColor(it) } ?: NeonMagenta
    val borderColor = when {
        isTargetable -> targetHighlightColor
        effectiveEliminated -> NeonMagenta
        activeSabotage != null -> sabotageAccentColor(activeSabotage.type)
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
        color = when {
            isTargetable -> targetHighlightColor.copy(alpha = 0.22f)
            isFocused && !isDisconnected -> accent.copy(alpha = 0.1f)
            else -> SlateSurface
        },
        border = BorderStroke(
            if (isTargetable || isFocused || isSabotaged || activeSabotage != null || effectiveEliminated) 1.5.dp else 1.dp,
            borderColor
        )
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
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(if (isDisconnected) Modifier.alpha(0.5f) else Modifier)
                    )
                    if (activeSabotage != null) {
                        ActiveSabotageStatusPill(activeSabotage = activeSabotage)
                    } else if (isFocused && !isDisconnected) {
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
                        isTargetable -> "TAP TO TARGET!"
                        isDisconnected -> "Disconnected"
                        effectiveEliminated -> if (placement > 0) "OUT • #$placement" else "ELIMINATED"
                        isBlocked -> "WAIT CLEAR"
                        isSabotaged -> "SPEED BURST!"
                        isPausedByPiece -> "WAIT PIECE"
                        score != null -> "$score pts"
                        else -> spawnColLabel
                    },
                    color = when {
                        isTargetable -> targetHighlightColor
                        effectiveEliminated -> NeonMagenta
                        isBlocked -> ElectricGold
                        isSabotaged -> ElectricGold
                        isPausedByPiece -> ElectricGold
                        else -> TextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = if (isTargetable || isSabotaged || isPausedByPiece || effectiveEliminated || isBlocked) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }

            if (isDisconnected) {
                LeftBadgePill()
            } else if (activeSabotage?.type == SabotageType.DROP_JAMMER) {
                JammedNextPieceBox(modifier = Modifier.size(36.dp))
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

    val textMeasurer = rememberTextMeasurer()

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
            if (gameState.mysteryEnabled && ghost.playerId == controlledPlayerId) {
                val ownerPiece = gameState.activePieces[ghost.playerId]
                if (ownerPiece != null && gameState.isPieceHiddenByMystery(ownerPiece)) {
                    continue
                }
            }
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
            val isHiddenMystery = gameState.isPieceHiddenByMystery(piece)
            if (piece.playerId == controlledPlayerId && isHiddenMystery) {
                continue
            }
            val renderer = SkinRegistry.resolve(piece.skinId)
            val isFocused = piece.playerId == controlledPlayerId
            val isSabotaged = piece.playerId in sabotagedPlayers

            val occupied = piece.occupiedCells()
            for (cell in occupied) {
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
            if (piece.playerId != controlledPlayerId && isHiddenMystery && occupied.isNotEmpty()) {
                val minX = occupied.minOf { it.x }
                val maxX = occupied.maxOf { it.x }
                val minY = occupied.minOf { it.y }
                val centerX = ((minX + maxX + 1) / 2f) * cellW
                val badgeY = (minY * cellH - cellH * 0.72f).coerceAtLeast(2f)
                val badgeLayout = textMeasurer.measure(
                    text = "👻",
                    style = TextStyle(fontSize = 11.sp)
                )
                val pillW = badgeLayout.size.width + 6f
                val pillH = badgeLayout.size.height + 2f
                val pillLeft = (centerX - pillW / 2f).coerceIn(2f, size.width - pillW - 2f)
                drawRoundRect(
                    color = Color(0xCC121426),
                    topLeft = Offset(pillLeft, badgeY),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawText(
                    textLayoutResult = badgeLayout,
                    topLeft = Offset(pillLeft + 3f, badgeY + 1f)
                )
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

        // 5. Blindfold fog debuff: 100% opaque base + animated swirling fog waves on top of all blocks
        if (gameState.activeSabotageFor(controlledPlayerId)?.type == SabotageType.BLINDFOLD_FOG) {
            val phaseRad = ((animTimeMillis % 4000L) / 4000f) * (2f * Math.PI.toFloat())
            clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
                drawRect(
                    color = Color(0xFF0A0A12),
                    topLeft = Offset.Zero,
                    size = size,
                    alpha = 1.0f
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF04060E).copy(alpha = 0.45f),
                            Color(0xFF0B1026).copy(alpha = 0.55f),
                            Color(0xFF050814).copy(alpha = 0.45f)
                        ),
                        startY = 0f,
                        endY = size.height
                    ),
                    topLeft = Offset.Zero,
                    size = size
                )
                for (band in 0 until 7) {
                    val bandY = size.height * ((band + 0.5f) / 7f)
                    val driftX = sin(phaseRad + band * 1.15f) * size.width * 0.2f
                    val driftY = cos(phaseRad * 1.35f + band * 0.85f) * cellH * 0.6f
                    val plumeCenter = Offset(size.width * 0.5f + driftX, bandY + driftY)
                    val radius = (size.width * 0.46f).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF38BDF8).copy(alpha = 0.16f),
                                Color(0xFF6366F1).copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = plumeCenter,
                            radius = radius
                        ),
                        radius = radius,
                        center = plumeCenter
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
    isFrozen: Boolean = false,
    isFlipped: Boolean = false,
    isDropJammed: Boolean = false,
    vibrationEnabled: Boolean = true,
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
            label = LocalStrings.current.leftBtn,
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
            accent = focusAccent,
            enabled = enabled,
            isFrozen = isFrozen,
            isFlipped = isFlipped,
            onClick = { onAction(PlayerAction.MOVE_LEFT) }
        )
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = "SOFT",
            icon = Icons.Rounded.KeyboardArrowDown,
            accent = focusAccent,
            enabled = enabled,
            isFrozen = isFrozen,
            onClick = { onAction(PlayerAction.SOFT_DROP) }
        )
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = LocalStrings.current.rightBtn,
            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            accent = focusAccent,
            enabled = enabled,
            isFrozen = isFrozen,
            isFlipped = isFlipped,
            onClick = { onAction(PlayerAction.MOVE_RIGHT) }
        )
        ControlPadButton(
            modifier = Modifier.weight(1f),
            label = LocalStrings.current.rotateBtn,
            icon = Icons.AutoMirrored.Rounded.RotateRight,
            accent = focusAccent,
            enabled = enabled,
            isFrozen = isFrozen,
            onClick = {
                if (vibrationEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction(PlayerAction.ROTATE_CW)
            }
        )
        ControlPadButton(
            modifier = Modifier.weight(1.05f),
            label = if (isDropJammed) LocalStrings.current.statusJammed else LocalStrings.current.dropBtn,
            icon = Icons.Rounded.VerticalAlignBottom,
            accent = focusAccent,
            highlighted = true,
            enabled = enabled && !isDropJammed,
            isFrozen = isFrozen,
            onClick = {
                if (vibrationEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
    isFrozen: Boolean = false,
    isFlipped: Boolean = false,
    onClick: () -> Unit
) {
    val effectiveEnabled = enabled && !isFrozen
    val flipPurple = Color(0xFFA855F7)
    val iceCyan = Color(0xFF7DD3FC)
    val effectiveAccent = when {
        isFrozen -> iceCyan
        isFlipped -> flipPurple
        else -> accent
    }
    val containerColor = when {
        isFrozen -> Color(0xFF0D283D)
        isFlipped && effectiveEnabled -> flipPurple.copy(alpha = 0.24f)
        highlighted -> effectiveAccent.copy(alpha = 0.24f)
        else -> ElevatedCard
    }
    val borderColor = when {
        isFrozen -> iceCyan.copy(alpha = 0.85f)
        isFlipped && effectiveEnabled -> flipPurple
        effectiveEnabled -> effectiveAccent.copy(alpha = if (highlighted) 0.9f else 0.45f)
        else -> SubtleBorder
    }

    Box(modifier = modifier) {
        Button(
            onClick = onClick,
            enabled = effectiveEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = TextPrimary,
                disabledContainerColor = if (isFrozen) Color(0xFF0D283D) else SlateSurface,
                disabledContentColor = if (isFrozen) iceCyan.copy(alpha = 0.7f) else TextSecondary.copy(alpha = 0.4f)
            ),
            border = BorderStroke(if (isFrozen || isFlipped) 1.4.dp else 1.2.dp, borderColor)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = when {
                        isFrozen -> iceCyan.copy(alpha = 0.8f)
                        effectiveEnabled -> effectiveAccent
                        else -> TextSecondary.copy(alpha = 0.4f)
                    },
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

        if (isFrozen) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                iceCyan.copy(alpha = 0.22f),
                                Color(0xFF38BDF8).copy(alpha = 0.08f)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Rounded.AcUnit,
                    contentDescription = "Frozen",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 5.dp)
                        .size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun SabotageHudDeck(
    equippedSabotages: List<SabotageType>,
    usedSabotages: Set<SabotageType>,
    pendingSabotage: SabotageType?,
    validTargets: List<Int>,
    targetLabels: Map<Int, String>,
    enabled: Boolean,
    compact: Boolean,
    onTapSabotage: (SabotageType) -> Unit,
    onPickTarget: (Int) -> Unit,
    onCancelTarget: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (pendingSabotage != null && validTargets.size > 1) {
            val pendingColor = sabotageAccentColor(pendingSabotage)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = pendingColor.copy(alpha = 0.16f),
                border = BorderStroke(1.2.dp, pendingColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (compact) "HIT:" else "PICK TARGET:",
                        color = pendingColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    validTargets.forEach { targetId ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(7.dp))
                                .clickable(enabled = enabled) { onPickTarget(targetId) },
                            shape = RoundedCornerShape(7.dp),
                            color = SlateSurface,
                            border = BorderStroke(1.dp, pendingColor.copy(alpha = 0.85f))
                        ) {
                            Text(
                                text = targetLabels[targetId] ?: "P$targetId",
                                color = TextPrimary,
                                fontSize = if (compact) 9.sp else 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = onCancelTarget,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel target",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            equippedSabotages.forEach { sabotageType ->
                val isUsed = sabotageType in usedSabotages
                val isPending = pendingSabotage == sabotageType
                val abilityColor = sabotageAccentColor(sabotageType)
                val isClickable = enabled && !isUsed && validTargets.isNotEmpty()
                val cardShape = RoundedCornerShape(10.dp)

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(cardShape)
                        .clickable(enabled = isClickable) { onTapSabotage(sabotageType) },
                    shape = cardShape,
                    color = when {
                        isUsed -> SlateSurface.copy(alpha = 0.45f)
                        isPending -> abilityColor.copy(alpha = 0.28f)
                        isClickable -> abilityColor.copy(alpha = 0.14f)
                        else -> SlateSurface
                    },
                    border = BorderStroke(
                        width = if (isPending) 1.5.dp else 1.dp,
                        color = when {
                            isPending -> abilityColor
                            isClickable -> abilityColor.copy(alpha = 0.7f)
                            else -> SubtleBorder
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (compact) 6.dp else 10.dp,
                            vertical = if (compact) 6.dp else 7.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = sabotageIcon(sabotageType),
                            contentDescription = sabotageType.displayName,
                            tint = if (isUsed || !isClickable) {
                                TextSecondary.copy(alpha = 0.45f)
                            } else {
                                abilityColor
                            },
                            modifier = Modifier.size(if (compact) 15.dp else 18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (compact) sabotageType.shortLabel else sabotageType.displayName,
                                color = if (isUsed) TextSecondary else TextPrimary,
                                fontSize = if (compact) 10.sp else 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when {
                                    isUsed -> "USED"
                                    isPending -> "PICK TARGET"
                                    validTargets.size == 1 -> "TAP TO FIRE"
                                    validTargets.size > 1 -> "TAP -> TARGET"
                                    else -> "READY"
                                },
                                color = when {
                                    isUsed -> TextSecondary.copy(alpha = 0.65f)
                                    isPending -> abilityColor
                                    else -> abilityColor.copy(alpha = 0.9f)
                                },
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveSabotageStatusPill(
    activeSabotage: ActiveSabotageState
) {
    val pillColor = sabotageAccentColor(activeSabotage.type)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = pillColor.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, pillColor)
    ) {
        Text(
            text = "${LocalStrings.current.sabotageStatusBadge(activeSabotage.type)} ${activeSabotage.remainingSeconds}s",
            color = pillColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun JammedNextPieceBox(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = NeonMagenta.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, NeonMagenta.copy(alpha = 0.85f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "???",
                color = NeonMagenta,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

private fun sabotageIcon(type: SabotageType): ImageVector = when (type) {
    SabotageType.BLINDFOLD_FOG -> Icons.Rounded.VisibilityOff
    SabotageType.CONTROLS_FLIP -> Icons.Rounded.SwapHoriz
    SabotageType.BUTTON_FREEZE -> Icons.Rounded.AcUnit
    SabotageType.TURBO_DROP -> Icons.Rounded.Bolt
    SabotageType.DROP_JAMMER -> Icons.Rounded.WarningAmber
}

private fun sabotageAccentColor(type: SabotageType): Color = when (type) {
    SabotageType.BLINDFOLD_FOG -> Color(0xFF94A3B8)
    SabotageType.CONTROLS_FLIP -> Color(0xFFA855F7)
    SabotageType.BUTTON_FREEZE -> Color(0xFF38BDF8)
    SabotageType.TURBO_DROP -> ElectricGold
    SabotageType.DROP_JAMMER -> NeonMagenta
}

@Composable
private fun PauseOverlay(
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    OverlayCard(title = LocalStrings.current.pausedTitle) {
        Button(
            onClick = onResume,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBg)
        ) {
            Text(LocalStrings.current.resumeGame, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Text(LocalStrings.current.playAgain, color = TextPrimary)
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Text(LocalStrings.current.backToHome, color = TextSecondary)
        }
    }
}

@Composable
private fun GameOverOverlay(
    gameState: GameState,
    networkState: NetworkSessionState,
    matchReward: MatchRewardBreakdown?,
    isNewCoopRecord: Boolean = false,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    val winnerId = gameState.winnerPlayerId
    val winTeam = gameState.winningTeam
    val isTeamsMode = gameState.mode == GameMode.VERSUS && gameState.versusSubMode == VersusSubMode.TEAMS_2V2
    val localPlayerId = networkState.controlledPlayerId
    val isLocalTest = networkState.sessionMode == SessionMode.LOCAL_SIMULATION
    val matchEligibleForCoins = matchReward == null || matchReward.lines.isNotEmpty()

    val title = when {
        isTeamsMode && winTeam != null -> "${winTeam.label.uppercase()} WINS!"
        gameState.mode == GameMode.VERSUS && winnerId != null -> {
            val winnerName = networkState.players.firstOrNull { it.playerId == winnerId }
                ?.displayName
                ?.takeIf { it.isNotBlank() }
                ?: "PLAYER $winnerId"
            "${winnerName.uppercase()} WINS!"
        }
        else -> "GAME OVER"
    }
    val titleColor = when {
        isTeamsMode && winTeam == TeamColor.CYAN -> CyberCyan
        isTeamsMode && winTeam == TeamColor.MAGENTA -> NeonMagenta
        winnerId != null -> PlayerPalette.accentFor(winnerId)
        else -> NeonMagenta
    }

    val rankedIds = (1..gameState.config.playerCount).sortedWith(
        compareBy<Int> { pid ->
            val endpoint = networkState.players.firstOrNull { it.playerId == pid }
            val isDisc = (endpoint?.isDisconnected == true) || gameState.isPlayerDisconnected(pid)
            if (isDisc) 1 else 0
        }.thenBy { pid ->
            if (isTeamsMode && winTeam != null) {
                if (gameState.playerTeams[pid] == winTeam) 0 else 1
            } else {
                0
            }
        }.thenBy { pid ->
            if (gameState.mode == GameMode.VERSUS) {
                val p = gameState.placementFor(pid)
                when {
                    p > 0 -> p
                    pid == winnerId -> 1
                    else -> Int.MAX_VALUE
                }
            } else {
                0
            }
        }.thenByDescending { pid ->
            if (gameState.mode == GameMode.CO_OP) gameState.playerLines[pid] ?: 0 else 0
        }.thenByDescending { pid ->
            gameState.playerScores[pid] ?: 0
        }
    )

    OverlayCard(title = title, titleColor = titleColor) {
        gameState.statusMessage?.let { msg ->
            Text(
                text = msg,
                color = TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (gameState.mode == GameMode.CO_OP) {
            // Prominent Co-op Team Summary Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isNewCoopRecord) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = ElectricGold.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, ElectricGold)
                        ) {
                            Text(
                                text = if (LocalStrings.current.gotIt == "Anladım") "★ YENİ REKOR!" else "★ NEW BEST RECORD!",
                                color = ElectricGold,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.7.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = "${gameState.sharedScore} PTS",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "${gameState.sharedLines} Lines Cleared • Level ${gameState.level}",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        } else if (isTeamsMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "CYAN: ${gameState.teamScore(TeamColor.CYAN)} pts",
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "MAGENTA: ${gameState.teamScore(TeamColor.MAGENTA)} pts",
                    color = NeonMagenta,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Structured Round Leaderboard Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ElevatedCard),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = LocalStrings.current.roundLeaderboardTitle,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.9.sp
                )

                rankedIds.forEachIndexed { index, pid ->
                    val endpoint = networkState.players.firstOrNull { it.playerId == pid }
                    val isDisconnected = (endpoint?.isDisconnected == true) || gameState.isPlayerDisconnected(pid)
                    val displayName = endpoint?.displayName?.takeIf { it.isNotBlank() } ?: "Player $pid"
                    val skinId = gameState.playerSkins[pid] ?: GameState.defaultSkinForPlayer(pid)
                    val score = gameState.playerScores[pid] ?: 0
                    val lines = gameState.playerLines[pid] ?: 0
                    val team = gameState.playerTeams[pid] ?: TeamColor.NONE
                    val explicitPlacement = gameState.placementFor(pid)
                    val displayRank = when {
                        isDisconnected -> 0
                        gameState.mode == GameMode.CO_OP -> index + 1
                        explicitPlacement > 0 -> explicitPlacement
                        pid == winnerId -> 1
                        else -> index + 1
                    }
                    val coinsEarned = if (pid == localPlayerId && matchReward != null) {
                        matchReward.totalCoins
                    } else {
                        MatchRewardCalculator.calculate(
                            gameState = gameState,
                            localPlayerId = pid,
                            isLocalTest = isLocalTest,
                            matchDurationMillis = if (matchEligibleForCoins) 15_000L else 0L
                        ).totalCoins
                    }

                    RoundLeaderboardRow(
                        playerId = pid,
                        rank = displayRank,
                        isCrown = !isDisconnected && (
                            (gameState.mode == GameMode.VERSUS && (
                                (isTeamsMode && winTeam != null && team == winTeam) ||
                                    (!isTeamsMode && displayRank == 1)
                                )) ||
                                (gameState.mode == GameMode.CO_OP && index == 0)
                            ),
                        skinId = skinId,
                        nickname = displayName,
                        teamColor = if (isTeamsMode) team else TeamColor.NONE,
                        score = score,
                        linesCleared = lines,
                        coinsEarned = if (gameState.mode == GameMode.VERSUS) coinsEarned else null,
                        isLocalPlayer = pid == localPlayerId,
                        isDisconnected = isDisconnected
                    )
                }
            }
        }

        if (matchReward != null) {
            Spacer(modifier = Modifier.height(10.dp))
            MatchRewardsCard(reward = matchReward)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val activeConnectedCount = if (networkState.players.isNotEmpty()) {
            networkState.players.count { !it.isDisconnected && !gameState.isPlayerDisconnected(it.playerId) }
        } else {
            (1..gameState.config.playerCount).count { !gameState.isPlayerDisconnected(it) }
        }
        val canPlayAgain = isLocalTest || (
            networkState.status == ConnectionStatus.CONNECTED && activeConnectedCount >= 2
        )

        Button(
            onClick = onPlayAgain,
            enabled = canPlayAgain,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = titleColor,
                contentColor = ObsidianBg,
                disabledContainerColor = SubtleBorder,
                disabledContentColor = TextSecondary
            )
        ) {
            Text(
                text = if (canPlayAgain) LocalStrings.current.playAgain else LocalStrings.current.playersLeftReturnHome,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Text(LocalStrings.current.backToHome, color = TextPrimary)
        }
    }
}

@Composable
private fun RoundLeaderboardRow(
    playerId: Int,
    rank: Int,
    isCrown: Boolean,
    skinId: String,
    nickname: String,
    teamColor: TeamColor,
    score: Int,
    linesCleared: Int,
    coinsEarned: Int?,
    isLocalPlayer: Boolean,
    isDisconnected: Boolean
) {
    val accent = PlayerPalette.accentFor(playerId)
    val renderer = SkinRegistry.resolve(skinId)
    val rowBg = when {
        isLocalPlayer -> accent.copy(alpha = 0.14f)
        isDisconnected -> SlateSurface.copy(alpha = 0.45f)
        else -> SlateSurface.copy(alpha = 0.85f)
    }
    val rowBorder = when {
        isLocalPlayer -> BorderStroke(1.2.dp, accent.copy(alpha = 0.8f))
        else -> BorderStroke(1.dp, SubtleBorder.copy(alpha = 0.65f))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = rowBg,
        border = rowBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Rank / Crown Badge
            Box(
                modifier = Modifier.width(26.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCrown) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = "Rank 1",
                        tint = ElectricGold,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = if (isDisconnected) "OUT" else "#$rank",
                        color = if (isDisconnected) NeonMagenta else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Equipped Skin Mini Block
            Canvas(
                modifier = Modifier
                    .size(18.dp)
                    .alpha(if (isDisconnected) 0.45f else 1f)
            ) {
                with(renderer) {
                    drawBlock(
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        tetrominoType = TetrominoType.T,
                        playerId = playerId,
                        isGhost = false
                    )
                }
            }

            // Nickname + Optional Team Tag
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = nickname,
                        color = if (isDisconnected) TextSecondary else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isDisconnected) TextDecoration.LineThrough else null,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .then(if (isDisconnected) Modifier.alpha(0.55f) else Modifier)
                    )
                    if (teamColor != TeamColor.NONE) {
                        val tagColor = if (teamColor == TeamColor.CYAN) CyberCyan else NeonMagenta
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = tagColor.copy(alpha = 0.18f),
                            border = BorderStroke(0.8.dp, tagColor.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = if (teamColor == TeamColor.CYAN) "CYAN" else "MAG",
                                color = tagColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (isDisconnected) LocalStrings.current.disconnectedLabel else "${LocalStrings.current.linesShort(linesCleared)} • $score",
                    color = if (isDisconnected) NeonMagenta else TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            // Coins Earned (Versus) or Score Highlight (Co-op)
            if (coinsEarned != null) {
                Text(
                    text = "+$coinsEarned",
                    color = if (coinsEarned > 0) ElectricGold else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            } else {
                Text(
                    text = "${score}p",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
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
                    text = if (LocalStrings.current.gotIt == "Anladım") "MAÇ ÖDÜLLERİ" else "MATCH REWARDS",
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
                        text = if (LocalStrings.current.gotIt == "Anladım") "Toplam Kazanılan" else "Total Earned",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (LocalStrings.current.gotIt == "Anladım") "Toplam: +$animatedTotal Altın" else "Total: +$animatedTotal Coins",
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
            Text(LocalStrings.current.backToHome, fontWeight = FontWeight.ExtraBold)
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
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(1.5.dp, titleColor.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
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
