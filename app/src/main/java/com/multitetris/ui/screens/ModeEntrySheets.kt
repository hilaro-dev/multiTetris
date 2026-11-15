package com.multitetris.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.game.BotDifficulty
import com.multitetris.game.GameMode
import com.multitetris.game.SabotageConfig
import com.multitetris.game.SabotageType
import com.multitetris.network.social.RoomInvite
import com.multitetris.network.social.SocialUiState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleplayerSetupSheet(
    initialMode: GameMode,
    onStartMatch: (
        mode: GameMode,
        sabotageEnabled: Boolean,
        mysteryEnabled: Boolean,
        botDifficulty: BotDifficulty,
        selectedSabotages: List<SabotageType>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMode by remember { mutableStateOf(initialMode) }
    var sabotageEnabled by remember { mutableStateOf(false) }
    var mysteryEnabled by remember { mutableStateOf(false) }
    var botDifficulty by remember { mutableStateOf(BotDifficulty.NORMAL) }
    var selectedSabotages by remember { mutableStateOf(SabotageConfig.defaultLoadout) }
    var activeTooltip by remember { mutableStateOf<InfoTooltipData?>(null) }

    val modeAccent = if (selectedMode == GameMode.VERSUS) NeonMagenta else CyberCyan

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SmartToy,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = strings.singleplayerSetupTitle,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.close,
                        tint = TextSecondary
                    )
                }
            }

            // Mode Toggle (Co-op vs Versus)
            SegmentedModeToggle(
                selectedMode = selectedMode,
                onSelectMode = { selectedMode = it },
                onShowInfo = { mode -> activeTooltip = GameInfoCatalog.forGameMode(mode, strings) }
            )
            Text(
                text = if (selectedMode == GameMode.CO_OP) strings.botCoopHint else strings.botVersusHint,
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Bot Difficulty Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = strings.botDifficultyTitle,
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val difficulties = listOf(
                        BotDifficulty.EASY to strings.botDifficultyEasy,
                        BotDifficulty.NORMAL to strings.botDifficultyNormal,
                        BotDifficulty.HARD to strings.botDifficultyHard
                    )
                    difficulties.forEach { (diff, label) ->
                        val selected = botDifficulty == diff
                        val accent = when (diff) {
                            BotDifficulty.EASY -> Color(0xFF2FE066)
                            BotDifficulty.NORMAL -> CyberCyan
                            BotDifficulty.HARD -> NeonMagenta
                        }
                        Surface(
                            onClick = { botDifficulty = diff },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) accent.copy(alpha = 0.18f) else ElevatedCard,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accent else SubtleBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) accent else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Sabotage Mode Switch
            SetupModifierSwitchRow(
                icon = Icons.Rounded.Bolt,
                title = strings.sabotageModeTitle,
                subtitle = strings.sabotageModeSubtitle,
                accent = ElectricGold,
                checked = sabotageEnabled,
                onCheckedChange = { sabotageEnabled = it },
                onShowInfo = { activeTooltip = GameInfoCatalog.sabotageMode(strings) }
            )

            // Mystery Mode Switch
            SetupModifierSwitchRow(
                icon = Icons.Rounded.VisibilityOff,
                title = "👻 ${strings.mysteryModeTitle}",
                subtitle = strings.mysteryModeSubtitle,
                accent = Color(0xFFA855F7),
                checked = mysteryEnabled,
                onCheckedChange = { mysteryEnabled = it },
                onShowInfo = { activeTooltip = GameInfoCatalog.mysteryMode(strings) }
            )

            // Versus Sabotage Loadout Picker
            AnimatedVisibility(visible = sabotageEnabled && selectedMode == GameMode.VERSUS) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = ElevatedCard,
                    border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = strings.pickSabotagesPrompt(selectedSabotages.size),
                            color = ElectricGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SabotageType.entries.forEach { sab ->
                                val isSelected = sab in selectedSabotages
                                val accent = when (sab) {
                                    SabotageType.BLINDFOLD_FOG -> Color(0xFFB388FF)
                                    SabotageType.CONTROLS_FLIP -> NeonMagenta
                                    SabotageType.BUTTON_FREEZE -> CyberCyan
                                    SabotageType.TURBO_DROP -> ElectricGold
                                    SabotageType.DROP_JAMMER -> Color(0xFFFF6D5A)
                                }
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedSabotages = SabotageConfig.toggleSelection(selectedSabotages, sab)
                                        },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) accent.copy(alpha = 0.22f) else SlateSurface,
                                    border = BorderStroke(
                                        width = if (isSelected) 1.4.dp else 1.dp,
                                        color = if (isSelected) accent else SubtleBorder
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 7.dp, horizontal = 3.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = when (sab) {
                                                    SabotageType.BLINDFOLD_FOG -> Icons.Rounded.VisibilityOff
                                                    SabotageType.CONTROLS_FLIP -> Icons.Rounded.Psychology
                                                    SabotageType.BUTTON_FREEZE -> Icons.Rounded.AutoAwesome
                                                    SabotageType.TURBO_DROP -> Icons.Rounded.Bolt
                                                    SabotageType.DROP_JAMMER -> Icons.Rounded.WarningAmber
                                                },
                                                contentDescription = strings.sabotageDisplayName(sab),
                                                tint = if (isSelected) accent else TextSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = strings.sabotageShortLabel(sab),
                                                color = if (isSelected) TextPrimary else TextSecondary,
                                                fontSize = 9.sp,
                                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(1.dp)
                                        ) {
                                            InfoIconButton(
                                                contentDescription = strings.moreInfoAbout(strings.sabotageDisplayName(sab)),
                                                tint = if (isSelected) accent else TextSecondary,
                                                iconSize = 11.dp,
                                                touchSize = 16.dp,
                                                onClick = { activeTooltip = GameInfoCatalog.forSabotage(sab, strings) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    onStartMatch(
                        selectedMode,
                        sabotageEnabled,
                        mysteryEnabled,
                        botDifficulty,
                        selectedSabotages
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = modeAccent,
                    contentColor = if (selectedMode == GameMode.VERSUS) Color.White else ObsidianBg
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = strings.startMatch,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    activeTooltip?.let { tooltip ->
        InfoTooltipDialog(
            info = tooltip,
            onDismiss = { activeTooltip = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerEntrySheet(
    selectedMode: GameMode,
    socialState: SocialUiState,
    onSelectMode: (GameMode) -> Unit,
    onCreateRoomLobby: () -> Unit,
    onAcceptRoomInvite: (RoomInvite) -> Unit,
    onDeclineRoomInvite: (RoomInvite) -> Unit,
    onOpenFriends: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTooltip by remember { mutableStateOf<InfoTooltipData?>(null) }
    val modeAccent = if (selectedMode == GameMode.VERSUS) NeonMagenta else CyberCyan

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = NeonMagenta,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = strings.multiplayerSetupTitle,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.close,
                        tint = TextSecondary
                    )
                }
            }

            SegmentedModeToggle(
                selectedMode = selectedMode,
                onSelectMode = onSelectMode,
                onShowInfo = { mode -> activeTooltip = GameInfoCatalog.forGameMode(mode, strings) }
            )

            Button(
                onClick = {
                    onDismiss()
                    onCreateRoomLobby()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = modeAccent,
                    contentColor = if (selectedMode == GameMode.VERSUS) Color.White else ObsidianBg
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = strings.createRoom,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            if (socialState.incomingRoomInvites.isNotEmpty()) {
                Text(
                    text = strings.activeRoomInvitesHeader,
                    color = CyberCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                socialState.incomingRoomInvites.forEach { invite ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = ElevatedCard,
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.roomInviteTitle(invite.hostNickname),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.roomInviteSubtitle(strings.gameModeLabel(invite.mode)),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onDeclineRoomInvite(invite) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(strings.decline, fontSize = 11.sp, color = TextSecondary)
                                }
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onAcceptRoomInvite(invite)
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberCyan,
                                        contentColor = ObsidianBg
                                    )
                                ) {
                                    Text(strings.join, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenFriends()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f))
            ) {
                Text(
                    text = strings.openFriendsAndInvites,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    activeTooltip?.let { tooltip ->
        InfoTooltipDialog(
            info = tooltip,
            onDismiss = { activeTooltip = null }
        )
    }
}

@Composable
private fun SetupModifierSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onShowInfo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCard,
        border = BorderStroke(
            width = if (checked) 1.2.dp else 1.dp,
            color = if (checked) accent.copy(alpha = 0.7f) else SubtleBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) accent else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        InfoIconButton(
                            contentDescription = LocalStrings.current.moreInfoAbout(title),
                            tint = if (checked) accent else TextSecondary,
                            iconSize = 14.dp,
                            touchSize = 18.dp,
                            onClick = onShowInfo
                        )
                    }
                    Text(
                        text = subtitle,
                        color = if (checked) accent else TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SlateSurface
                )
            )
        }
    }
}
