package com.multitetris.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.game.BotDifficulty
import com.multitetris.game.GameMode
import com.multitetris.game.SabotageConfig
import com.multitetris.game.SabotageType
import com.multitetris.game.TeamColor
import com.multitetris.game.TetrominoType
import com.multitetris.game.VersusSubMode
import com.multitetris.network.social.FriendLivePresence
import com.multitetris.network.social.MatchRole
import com.multitetris.network.social.MatchRoom
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.RoomPlayerSlot
import com.multitetris.network.social.SavedFriend
import com.multitetris.network.social.SocialUiState
import com.multitetris.network.webrtc.WebRtcSignalState
import com.multitetris.ui.i18n.LocalStrings
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
fun MatchLobbyScreen(
    room: MatchRoom,
    signalState: WebRtcSignalState,
    socialState: SocialUiState,
    connectedPeerUids: Set<String> = emptySet(),
    onInviteFriend: (SavedFriend) -> Unit,
    onSetRoomMode: (GameMode) -> Unit = {},
    onSetVersusSubMode: (VersusSubMode) -> Unit = {},
    onSetSabotageEnabled: (Boolean) -> Unit = {},
    onSetMysteryEnabled: (Boolean) -> Unit = {},
    onToggleSabotage: (SabotageType) -> Unit = {},
    onSwapPlayerTeam: (String) -> Unit = {},
    botDifficulty: BotDifficulty = BotDifficulty.NORMAL,
    onAddBot: (Int) -> Unit = {},
    onRemoveBot: (Int) -> Unit = {},
    onSelectBotDifficulty: (BotDifficulty) -> Unit = {},
    onStartMatch: () -> Unit = {},
    onCancelLobby: () -> Unit
) {
    val modeAccent = when (room.mode) {
        GameMode.CO_OP -> CyberCyan
        GameMode.VERSUS -> NeonMagenta
        GameMode.SEPARATE -> ElectricGold
    }
    val isHost = room.myRole == MatchRole.HOST
    val resolvedPlayers = room.resolvedPlayers()
    val playerBySlot = resolvedPlayers.associateBy { it.slotIndex }
    val occupiedUids = resolvedPlayers.map { it.uid }.toSet()
    val guestCount = (resolvedPlayers.size - 1).coerceAtLeast(0)
    val isTwoVTwoActive = room.mode != GameMode.CO_OP && room.versusSubMode == VersusSubMode.TEAMS_2V2
    val showSabotageDraft = room.mode != GameMode.CO_OP && room.sabotageEnabled
    val myUid = socialState.userProfile.uid
    val mySelectedSabotages = resolvedPlayers.firstOrNull { it.uid == myUid }?.selectedSabotages
        ?: SabotageConfig.defaultLoadout
    var activeTooltip by remember { mutableStateOf<InfoTooltipData?>(null) }
    val strings = LocalStrings.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBg,
                        if (com.multitetris.ui.theme.MultiTetrisPalette.isDark) Color(0xFF0D1529) else Color(0xFFE4ECF8),
                        ObsidianBg
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header
            Surface(
                shape = RoundedCornerShape(50),
                color = modeAccent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, modeAccent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = modeAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = strings.lobbyModePlayersHeader(strings.gameModeLabel(room.mode), resolvedPlayers.size),
                        color = modeAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    InfoIconButton(
                        contentDescription = strings.moreInfoAbout(strings.gameModeLabel(room.mode)),
                        tint = modeAccent,
                        iconSize = 14.dp,
                        touchSize = 18.dp,
                        onClick = { activeTooltip = GameInfoCatalog.forGameMode(room.mode, strings) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.roomLobbyTitle,
                fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (signalState is WebRtcSignalState.Connecting) {
                    CircularProgressIndicator(
                        color = modeAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                }
                val statusMessage = when {
                    isHost && guestCount == 0 -> "Invite 1 to 3 online friends below"
                    isHost -> "P2P Ready: ${connectedPeerUids.size}/$guestCount • Ready to start (${resolvedPlayers.size}P)"
                    signalState is WebRtcSignalState.Connecting -> signalState.statusText
                    signalState is WebRtcSignalState.Connected -> "Launching match..."
                    else -> "Preparing WebRTC link..."
                }
                Text(
                    text = statusMessage,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isHost) {
                SegmentedModeToggle(
                    selectedMode = room.mode,
                    onSelectMode = onSetRoomMode,
                    onShowInfo = { mode -> activeTooltip = GameInfoCatalog.forGameMode(mode, strings) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 4-Player 2x2 Slot Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LobbySlotGridItem(
                        slotIndex = 1,
                        player = playerBySlot[1],
                        isMe = room.mySlotIndex == 1,
                        isHostViewing = isHost,
                        isPeerConnected = true,
                        showTeams = isTwoVTwoActive,
                        showSabotages = showSabotageDraft,
                        onSwapTeam = { playerBySlot[1]?.let { onSwapPlayerTeam(it.uid) } },
                        modifier = Modifier.weight(1f)
                    )
                    LobbySlotGridItem(
                        slotIndex = 2,
                        player = playerBySlot[2],
                        isMe = room.mySlotIndex == 2,
                        isHostViewing = isHost,
                        isPeerConnected = isSlotP2pReady(2, playerBySlot[2], room, connectedPeerUids),
                        showTeams = isTwoVTwoActive,
                        showSabotages = showSabotageDraft,
                        onSwapTeam = { playerBySlot[2]?.let { onSwapPlayerTeam(it.uid) } },
                        onAddBot = { onAddBot(2) },
                        onRemoveBot = { onRemoveBot(2) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LobbySlotGridItem(
                        slotIndex = 3,
                        player = playerBySlot[3],
                        isMe = room.mySlotIndex == 3,
                        isHostViewing = isHost,
                        isPeerConnected = isSlotP2pReady(3, playerBySlot[3], room, connectedPeerUids),
                        showTeams = isTwoVTwoActive,
                        showSabotages = showSabotageDraft,
                        onSwapTeam = { playerBySlot[3]?.let { onSwapPlayerTeam(it.uid) } },
                        onAddBot = { onAddBot(3) },
                        onRemoveBot = { onRemoveBot(3) },
                        modifier = Modifier.weight(1f)
                    )
                    LobbySlotGridItem(
                        slotIndex = 4,
                        player = playerBySlot[4],
                        isMe = room.mySlotIndex == 4,
                        isHostViewing = isHost,
                        isPeerConnected = isSlotP2pReady(4, playerBySlot[4], room, connectedPeerUids),
                        showTeams = isTwoVTwoActive,
                        showSabotages = showSabotageDraft,
                        onSwapTeam = { playerBySlot[4]?.let { onSwapPlayerTeam(it.uid) } },
                        onAddBot = { onAddBot(4) },
                        onRemoveBot = { onRemoveBot(4) },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isHost && resolvedPlayers.any { it.isBot || it.uid.startsWith("bot_p") }) {
                    val strings = LocalStrings.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.botDifficultyTitle,
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                BotDifficulty.EASY to strings.botDifficultyEasy,
                                BotDifficulty.NORMAL to strings.botDifficultyNormal,
                                BotDifficulty.HARD to strings.botDifficultyHard
                            ).forEach { (diff, label) ->
                                val selected = botDifficulty == diff
                                Surface(
                                    onClick = { onSelectBotDifficulty(diff) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) modeAccent.copy(alpha = 0.2f) else SlateSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (selected) modeAccent else SubtleBorder
                                    )
                                ) {
                                    Text(
                                        text = label,
                                        color = if (selected) modeAccent else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Host-only Teams vs Free-For-All toggle when Versus or Separate mode has 3-4 players
            if (room.mode != GameMode.CO_OP && resolvedPlayers.size >= 3) {
                Spacer(modifier = Modifier.height(10.dp))
                if (isHost) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = SlateSurface,
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                VersusSubMode.entries.forEach { subMode ->
                                    val selected = room.versusSubMode == subMode
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onSetVersusSubMode(subMode) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) modeAccent else Color.Transparent,
                                        border = if (selected) null else BorderStroke(1.dp, SubtleBorder)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = strings.versusSubModeLabel(subMode),
                                                    color = if (selected) Color.White else TextSecondary,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 12.sp
                                                )
                                                InfoIconButton(
                                                    contentDescription = strings.moreInfoAbout(strings.versusSubModeLabel(subMode)),
                                                    tint = if (selected) Color.White else TextSecondary,
                                                    iconSize = 14.dp,
                                                    touchSize = 18.dp,
                                                    onClick = { activeTooltip = GameInfoCatalog.forVersusSubMode(subMode, strings) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (room.versusSubMode == VersusSubMode.TEAMS_2V2) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = null,
                                        tint = ElectricGold,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Tap any player card above to swap Team Cyan / Magenta",
                                        color = ElectricGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SlateSurface,
                        border = BorderStroke(1.dp, modeAccent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Groups,
                                contentDescription = null,
                                tint = modeAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.versusSubModeLabel(room.versusSubMode),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            InfoIconButton(
                                contentDescription = strings.moreInfoAbout(strings.versusSubModeLabel(room.versusSubMode)),
                                tint = modeAccent,
                                iconSize = 14.dp,
                                touchSize = 20.dp,
                                onClick = { activeTooltip = GameInfoCatalog.forVersusSubMode(room.versusSubMode, strings) }
                            )
                        }
                    }
                }
            }

            // Sabotage Mode Modifier Switch (visible to everyone, interactive for Host)
            Spacer(modifier = Modifier.height(10.dp))
            SabotageModeToggleCard(
                sabotageEnabled = room.sabotageEnabled,
                isHost = isHost,
                isVersus = room.mode != GameMode.CO_OP,
                onToggle = onSetSabotageEnabled,
                onShowInfo = { activeTooltip = GameInfoCatalog.sabotageMode(strings) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            MysteryModeToggleCard(
                mysteryEnabled = room.mysteryEnabled,
                isHost = isHost,
                onToggle = onSetMysteryEnabled,
                onShowInfo = { activeTooltip = GameInfoCatalog.mysteryMode(strings) }
            )

            // Competitive Loadout Drafting (Pick 2 Sabotages, visible to all when Sabotage Mode is ON)
            androidx.compose.animation.AnimatedVisibility(
                visible = room.sabotageEnabled && room.mode != GameMode.CO_OP
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    SabotageDraftChipBar(
                        selectedSabotages = mySelectedSabotages,
                        onToggleSabotage = onToggleSabotage,
                        onShowSabotageInfo = { sab -> activeTooltip = GameInfoCatalog.forSabotage(sab, strings) }
                    )
                }
            }

            // Host Friend Invite List (available while fewer than 4 players in room)
            if (isHost && resolvedPlayers.size < 4) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 8.dp)
                ) {
                    val availableFriends = socialState.friends.filter { it.friend.uid !in occupiedUids }
                    val onlineCount = availableFriends.count { it.state != PresenceState.OFFLINE }
                    Text(
                        text = "${strings.inviteFriendsTitle} ($onlineCount ${strings.onlineLabel.uppercase()} • ${4 - resolvedPlayers.size})",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (availableFriends.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Text(
                                text = "Add friends in the Friends drawer on the Home Screen to invite up to 3 players.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableFriends.forEach { presence ->
                                InLobbyFriendInviteRow(
                                    presence = presence,
                                    isInvited = presence.friend.uid in socialState.invitedFriendUids,
                                    accent = modeAccent,
                                    onInvite = { onInviteFriend(presence.friend) }
                                )
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = when {
                                resolvedPlayers.size >= 3 && room.mode != GameMode.SEPARATE ->
                                    "LANDSCAPE MODE AUTO-ENABLED (${resolvedPlayers.size}P • ${if (resolvedPlayers.size == 3) "13×20" else "16×20"} BOARD)"
                                room.mode == GameMode.SEPARATE ->
                                    "PORTRAIT CORNER MINI-BOARDS (${resolvedPlayers.size}P • 10×20 EACH)"
                                else ->
                                    "STAR-TOPOLOGY WEBRTC • TURN RELAY ACTIVE"
                            },
                            color = ElectricGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (isHost) {
                                strings.readyStatus
                            } else {
                                strings.waitingForHostToStart
                            },
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            }

            // Bottom Actions (pinned): Start Match (Host with >= 2 players) + Leave Lobby
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isHost) {
                    val nonHostHumanGuests = resolvedPlayers.filter {
                        it.uid != room.hostUid && !it.isBot && !it.uid.startsWith("bot_p")
                    }
                    val allHumanGuestsConnected = nonHostHumanGuests.all { it.uid in connectedPeerUids }
                    val canStart = resolvedPlayers.size >= 2 && allHumanGuestsConnected
                    Button(
                        onClick = onStartMatch,
                        enabled = canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = modeAccent,
                            contentColor = if (modeAccent == NeonMagenta) Color.White else ObsidianBg,
                            disabledContainerColor = SlateSurface,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                resolvedPlayers.size < 2 -> strings.needMorePlayersToStart
                                !allHumanGuestsConnected -> strings.connectingToHost
                                else -> "${strings.startMatch} (${resolvedPlayers.size})"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCancelLobby,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, SubtleBorder)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.leaveRoomLobby,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    activeTooltip?.let { tooltip ->
        InfoTooltipDialog(
            info = tooltip,
            onDismiss = { activeTooltip = null }
        )
    }

    if (signalState is WebRtcSignalState.TimeoutError) {
        AlertDialog(
            onDismissRequest = onCancelLobby,
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = ElectricGold,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = strings.connectionTimedOutTitle,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = signalState.message,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = onCancelLobby,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text(strings.backToHome, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }
}

private fun isSlotP2pReady(
    slotIndex: Int,
    slot: RoomPlayerSlot?,
    room: MatchRoom,
    connectedPeerUids: Set<String>
): Boolean {
    if (slot == null) return false
    if (slot.isBot || slot.uid.startsWith("bot_p")) return true
    if (room.myRole == MatchRole.HOST) {
        return slotIndex == 1 || slot.uid in connectedPeerUids
    }
    return slotIndex == room.mySlotIndex || slot.uid in connectedPeerUids || connectedPeerUids.isNotEmpty()
}

@Composable
private fun LobbySlotGridItem(
    slotIndex: Int,
    player: RoomPlayerSlot?,
    isMe: Boolean,
    isHostViewing: Boolean,
    isPeerConnected: Boolean,
    showTeams: Boolean,
    showSabotages: Boolean = false,
    onSwapTeam: () -> Unit,
    onAddBot: () -> Unit = {},
    onRemoveBot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalStrings.current
    val slotAccent = if (showTeams && player != null) {
        if (player.team == TeamColor.MAGENTA) NeonMagenta else CyberCyan
    } else {
        PlayerPalette.accentFor(slotIndex)
    }

    if (player == null) {
        Card(
            modifier = modifier.height(92.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface.copy(alpha = 0.55f)),
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SLOT #$slotIndex",
                        color = slotAccent.copy(alpha = 0.65f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Icon(
                        imageVector = Icons.Rounded.HourglassEmpty,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Open Slot",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isHostViewing && slotIndex in 2..4) {
                    Surface(
                        onClick = onAddBot,
                        shape = RoundedCornerShape(8.dp),
                        color = slotAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, slotAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = strings.addBotLabel,
                            color = slotAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Text(
                        text = strings.waitingForPlayer,
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                }
            }
        }
        return
    }

    val isBotPlayer = player.isBot || player.uid.startsWith("bot_p")
    val renderer = SkinRegistry.resolve(player.skinId)
    val previewType = when (slotIndex) {
        1 -> TetrominoType.I
        2 -> TetrominoType.T
        3 -> TetrominoType.L
        else -> TetrominoType.S
    }
    val canSwap = showTeams && isHostViewing

    Card(
        modifier = modifier
            .height(if (showSabotages) 108.dp else 92.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (canSwap) Modifier.clickable(onClick = onSwapTeam) else Modifier
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.5.dp, slotAccent.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildString {
                            append(if (slotIndex == 1) strings.hostTag else "P$slotIndex")
                            if (isMe) append(" • ${strings.youTag}")
                        },
                        color = slotAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (isBotPlayer) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = slotAccent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = strings.botBadge,
                                color = slotAccent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isBotPlayer && isHostViewing) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = strings.removeBotContentDesc,
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(15.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onRemoveBot)
                        )
                    }
                    val readyColor = if (isPeerConnected) Color(0xFF2FE066) else ElectricGold
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(readyColor)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = player.nickname,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showTeams) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = slotAccent.copy(alpha = 0.2f),
                            border = BorderStroke(0.8.dp, slotAccent)
                        ) {
                            Text(
                                text = if (player.team == TeamColor.MAGENTA) "TEAM MAGENTA" else "TEAM CYAN",
                                color = slotAccent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            text = if (isPeerConnected) "P2P Ready" else "Linking TURN...",
                            color = if (isPeerConnected) Color(0xFF2FE066) else ElectricGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (showSabotages) {
                        Spacer(modifier = Modifier.height(3.dp))
                        val drafted = SabotageConfig.resolveLoadout(player.selectedSabotages)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            drafted.forEach { sab ->
                                val sabColor = sabotageAccentColor(sab)
                                Surface(
                                    shape = RoundedCornerShape(5.dp),
                                    color = sabColor.copy(alpha = 0.16f),
                                    border = BorderStroke(0.7.dp, sabColor.copy(alpha = 0.75f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = sabotageIcon(sab),
                                            contentDescription = sab.displayName,
                                            tint = sabColor,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = LocalStrings.current.sabotageShortLabel(sab),
                                            color = sabColor,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Canvas(modifier = Modifier.size(26.dp)) {
                    with(renderer) {
                        drawBlock(
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            tetrominoType = previewType,
                            playerId = slotIndex,
                            isGhost = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SabotageModeToggleCard(
    sabotageEnabled: Boolean,
    isHost: Boolean,
    isVersus: Boolean,
    onToggle: (Boolean) -> Unit,
    onShowInfo: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SlateSurface,
        border = BorderStroke(
            width = if (sabotageEnabled) 1.2.dp else 1.dp,
            color = if (sabotageEnabled) ElectricGold.copy(alpha = 0.8f) else SubtleBorder
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
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = if (sabotageEnabled) ElectricGold else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    val strings = LocalStrings.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = strings.sabotageModeTitle,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        InfoIconButton(
                            contentDescription = strings.moreInfoAbout(strings.sabotageModeTitle),
                            tint = if (sabotageEnabled) ElectricGold else TextSecondary,
                            iconSize = 14.dp,
                            touchSize = 18.dp,
                            onClick = onShowInfo
                        )
                    }
                    Text(
                        text = strings.sabotageModeSubtitle,
                        color = if (sabotageEnabled) ElectricGold else TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            Switch(
                checked = sabotageEnabled,
                onCheckedChange = { if (isHost) onToggle(it) },
                enabled = isHost,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = ElectricGold,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ElevatedCard
                )
            )
        }
    }
}

@Composable
private fun MysteryModeToggleCard(
    mysteryEnabled: Boolean,
    isHost: Boolean,
    onToggle: (Boolean) -> Unit,
    onShowInfo: () -> Unit
) {
    val mysteryViolet = Color(0xFFA855F7)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SlateSurface,
        border = BorderStroke(
            width = if (mysteryEnabled) 1.2.dp else 1.dp,
            color = if (mysteryEnabled) mysteryViolet.copy(alpha = 0.85f) else SubtleBorder
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
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.VisibilityOff,
                    contentDescription = null,
                    tint = if (mysteryEnabled) mysteryViolet else TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    val strings = LocalStrings.current
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "👻 ${strings.mysteryModeTitle}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        InfoIconButton(
                            contentDescription = strings.moreInfoAbout(strings.mysteryModeTitle),
                            tint = if (mysteryEnabled) mysteryViolet else TextSecondary,
                            iconSize = 14.dp,
                            touchSize = 18.dp,
                            onClick = onShowInfo
                        )
                    }
                    Text(
                        text = strings.mysteryModeSubtitle,
                        color = if (mysteryEnabled) mysteryViolet else TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
            Switch(
                checked = mysteryEnabled,
                onCheckedChange = { if (isHost) onToggle(it) },
                enabled = isHost,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = mysteryViolet,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = ElevatedCard
                )
            )
        }
    }
}

@Composable
private fun SabotageDraftChipBar(
    selectedSabotages: List<SabotageType>,
    onToggleSabotage: (SabotageType) -> Unit,
    onShowSabotageInfo: (SabotageType) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val strings = LocalStrings.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.pickSabotagesPrompt(selectedSabotages.size),
                    color = ElectricGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SabotageType.entries.forEach { sab ->
                    val isSelected = sab in selectedSabotages
                    val accent = sabotageAccentColor(sab)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleSabotage(sab) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) accent.copy(alpha = 0.22f) else ElevatedCard,
                        border = BorderStroke(
                            width = if (isSelected) 1.4.dp else 1.dp,
                            color = if (isSelected) accent else SubtleBorder
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 3.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = sabotageIcon(sab),
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
                                    onClick = { onShowSabotageInfo(sab) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sabotageIcon(type: SabotageType) = when (type) {
    SabotageType.BLINDFOLD_FOG -> Icons.Rounded.VisibilityOff
    SabotageType.CONTROLS_FLIP -> Icons.Rounded.SwapHoriz
    SabotageType.BUTTON_FREEZE -> Icons.Rounded.AcUnit
    SabotageType.TURBO_DROP -> Icons.Rounded.Bolt
    SabotageType.DROP_JAMMER -> Icons.Rounded.WarningAmber
}

private fun sabotageAccentColor(type: SabotageType): Color = when (type) {
    SabotageType.BLINDFOLD_FOG -> Color(0xFFB388FF)
    SabotageType.CONTROLS_FLIP -> NeonMagenta
    SabotageType.BUTTON_FREEZE -> CyberCyan
    SabotageType.TURBO_DROP -> ElectricGold
    SabotageType.DROP_JAMMER -> Color(0xFFFF6D5A)
}

@Composable
private fun InLobbyFriendInviteRow(
    presence: FriendLivePresence,
    isInvited: Boolean,
    accent: Color,
    onInvite: () -> Unit
) {
    val isOnline = presence.state != PresenceState.OFFLINE
    val statusColor = if (isOnline) Color(0xFF2FE066) else TextSecondary
    val renderer = SkinRegistry.resolve(presence.friend.selectedSkinId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, if (isOnline) SubtleBorder else SubtleBorder.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    with(renderer) {
                        drawBlock(
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height),
                            tetrominoType = TetrominoType.T,
                            playerId = 2,
                            isGhost = false
                        )
                    }
                }
                Column {
                    Text(
                        text = presence.friend.nickname,
                        color = if (isOnline) TextPrimary else TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = if (isOnline) "Online • ${renderer.displayName}" else "Offline",
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isOnline) {
                if (isInvited) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.65f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = ElectricGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = LocalStrings.current.invitedLabel,
                            color = ElectricGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onInvite,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = if (accent == NeonMagenta) Color.White else ObsidianBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = LocalStrings.current.inviteButton,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
