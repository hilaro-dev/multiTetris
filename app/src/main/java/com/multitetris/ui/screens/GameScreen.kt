package com.multitetris.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.economy.MatchRewardBreakdown
import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.PlayerAction
import com.multitetris.game.TetrominoType
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
    val controlledPlayerId = networkState.controlledPlayerId
    val focusAccent = PlayerPalette.accentFor(controlledPlayerId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: Back, Focus Toggle / P2P Role Badge, Mute, Pause, Restart
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

        // Dual HUD + Next Piece previews
        DualPlayerHud(
            gameState = gameState,
            networkState = networkState,
            controlledPlayerId = controlledPlayerId,
            onSelectPlayer = onSelectFocusPlayer
        )

        // Sabotage Push Alert banner when active in Versus
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

        // Main 10x20 Jetpack Compose Canvas Board
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
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

            if (networkState.status == ConnectionStatus.DISCONNECTED) {
                DisconnectedOverlay(onReturnHome = onExitToHome)
            } else if (gameState.isPaused && !gameState.isGameOver) {
                PauseOverlay(
                    onResume = onTogglePause,
                    onRestart = onRestart,
                    onExit = onExitToHome
                )
            } else if (gameState.isGameOver) {
                GameOverOverlay(
                    gameState = gameState,
                    matchReward = matchReward,
                    onPlayAgain = onRestart,
                    onExit = onExitToHome
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Responsive Touch Controls for the focused player
        TouchControlsDeck(
            controlledPlayerId = controlledPlayerId,
            focusAccent = focusAccent,
            enabled = !gameState.isPaused &&
                !gameState.isGameOver &&
                networkState.status != ConnectionStatus.DISCONNECTED,
            onAction = onAction
        )
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
                    val roleTag = if (sessionMode == SessionMode.HOST) "P1 HOST" else "P2 GUEST"
                    Text(
                        text = "P2P LIVE • $roleTag",
                        color = TextPrimary,
                        fontSize = 12.sp,
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
    onClick: () -> Unit
) {
    val accent = PlayerPalette.accentFor(playerId)
    val borderColor = when {
        isSabotaged -> ElectricGold
        isFocused -> accent
        else -> SubtleBorder
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isFocused) accent.copy(alpha = 0.1f) else SlateSurface,
        border = BorderStroke(if (isFocused || isSabotaged) 1.5.dp else 1.dp, borderColor)
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
                        text = "P$playerId",
                        color = accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (isFocused) {
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
                        isSabotaged -> "SPEED BURST!"
                        isPausedByPiece -> "WAIT PIECE"
                        score != null -> "$score pts"
                        else -> spawnColLabel
                    },
                    color = when {
                        isSabotaged -> ElectricGold
                        isPausedByPiece -> ElectricGold
                        else -> TextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = if (isSabotaged || isPausedByPiece) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }

            NextPiecePreviewCanvas(
                tetrominoType = nextType,
                playerId = playerId,
                skinId = skinId,
                modifier = Modifier.size(36.dp)
            )
        }
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
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skin_shimmer"
    )
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
                    drawBlock(
                        topLeft = Offset(c * cellW, r * cellH),
                        size = cellSize,
                        tetrominoType = cell.tetrominoType,
                        playerId = cell.playerId,
                        isGhost = false,
                        animProgress = animProgress
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
                        drawBlock(
                            topLeft = Offset(cell.x * cellW, cell.y * cellH),
                            size = cellSize,
                            tetrominoType = ghost.tetrominoType,
                            playerId = ghost.playerId,
                            isGhost = true,
                            animProgress = animProgress
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
                        drawBlock(
                            topLeft = topLeft,
                            size = cellSize,
                            tetrominoType = piece.tetrominoType,
                            playerId = piece.playerId,
                            isGhost = false,
                            isPausedOnPiece = piece.isPausedByPiece,
                            animProgress = animProgress
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
    matchReward: MatchRewardBreakdown?,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit
) {
    val winnerId = gameState.winnerPlayerId
    val title = if (gameState.mode == GameMode.VERSUS && winnerId != null) {
        "PLAYER $winnerId WINS!"
    } else {
        "GAME OVER"
    }
    val titleColor = if (winnerId != null) PlayerPalette.accentFor(winnerId) else NeonMagenta

    OverlayCard(title = title, titleColor = titleColor) {
        gameState.statusMessage?.let { msg ->
            Text(
                text = msg,
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (gameState.mode == GameMode.CO_OP) {
            Text(
                text = "Score: ${gameState.sharedScore} • Lines: ${gameState.sharedLines} • Level ${gameState.level}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = "P1: ${gameState.playerScores[1] ?: 0} pts (${gameState.playerLines[1] ?: 0}L)  |  P2: ${gameState.playerScores[2] ?: 0} pts (${gameState.playerLines[2] ?: 0}L)",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
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
    onReturnHome: () -> Unit
) {
    OverlayCard(title = "Opponent Disconnected", titleColor = ElectricGold) {
        Text(
            text = "The WebRTC peer connection was closed or your opponent left the match.",
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
