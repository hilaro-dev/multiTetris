package com.multitetris.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Send
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.game.BoardConfig
import com.multitetris.game.GameMode
import com.multitetris.game.TetrominoType
import com.multitetris.network.social.FriendLivePresence
import com.multitetris.network.social.MatchRole
import com.multitetris.network.social.MatchRoom
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.SavedFriend
import com.multitetris.network.social.SocialUiState
import com.multitetris.network.webrtc.WebRtcSignalState
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

private data class LobbySlotUi(
    val playerId: Int,
    val spawnHint: String,
    val roleLabel: String,
    val nickname: String?,
    val skinId: String
)

@Composable
fun MatchLobbyScreen(
    room: MatchRoom,
    signalState: WebRtcSignalState,
    socialState: SocialUiState,
    onInviteFriend: (SavedFriend) -> Unit,
    onCancelLobby: () -> Unit
) {
    val modeAccent = if (room.mode == GameMode.VERSUS) NeonMagenta else CyberCyan
    val isWaitingForGuest = room.myRole == MatchRole.HOST && room.guestUid.isBlank()

    // Expandable slot list driven dynamically by BoardConfig spawn zones (defaults to 2 slots)
    val boardConfig = BoardConfig(playerCount = 2)
    val playerSlots = boardConfig.spawnZones.map { zone ->
        when (zone.playerId) {
            1 -> LobbySlotUi(
                playerId = 1,
                spawnHint = "Col ${zone.spawnX} (Zone ${zone.columnRange.first}–${zone.columnRange.last})",
                roleLabel = if (room.myRole == MatchRole.HOST) "HOST (YOU)" else "HOST",
                nickname = room.hostNickname,
                skinId = room.hostSkinId
            )

            2 -> LobbySlotUi(
                playerId = 2,
                spawnHint = "Col ${zone.spawnX} (Zone ${zone.columnRange.first}–${zone.columnRange.last})",
                roleLabel = if (room.myRole == MatchRole.GUEST) "GUEST (YOU)" else "GUEST",
                nickname = if (room.guestUid.isNotBlank()) room.guestNickname else null,
                skinId = room.guestSkinId
            )

            else -> LobbySlotUi(
                playerId = zone.playerId,
                spawnHint = "Col ${zone.spawnX} (Zone ${zone.columnRange.first}–${zone.columnRange.last})",
                roleLabel = "PLAYER ${zone.playerId}",
                nickname = null,
                skinId = "classic"
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBg, Color(0xFF0D1529), ObsidianBg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Status Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = modeAccent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, modeAccent)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = modeAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${room.mode.label} ROOM • ${room.myRole.name}",
                            color = modeAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isWaitingForGuest) "Room Lobby" else "Connecting P2P...",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!isWaitingForGuest) {
                        CircularProgressIndicator(
                            color = modeAccent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = ElectricGold,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    val statusMessage = when {
                        isWaitingForGuest -> "Invite an online friend below to start"
                        signalState is WebRtcSignalState.Connecting -> signalState.statusText
                        signalState is WebRtcSignalState.Connected -> "WebRTC DataChannel OPEN! Launching..."
                        signalState is WebRtcSignalState.TimeoutError -> "Direct P2P connection failed"
                        signalState is WebRtcSignalState.Disconnected -> signalState.reason
                        else -> "Preparing WebRTC peer connection..."
                    }
                    Text(
                        text = statusMessage,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Player Slots (Expandable)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "ROOM PLAYERS (${playerSlots.count { it.nickname != null }}/${playerSlots.size})",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                playerSlots.forEach { slot ->
                    if (slot.nickname != null) {
                        LobbyPlayerCard(
                            playerId = slot.playerId,
                            roleLabel = slot.roleLabel,
                            nickname = slot.nickname,
                            skinId = slot.skinId,
                            spawnHint = slot.spawnHint
                        )
                    } else {
                        WaitingSlotCard(
                            playerId = slot.playerId,
                            roleLabel = slot.roleLabel,
                            spawnHint = slot.spawnHint
                        )
                    }
                }
            }

            // In-Lobby Friend Invite List for Host while waiting for a friend to join
            if (room.myRole == MatchRole.HOST && room.guestUid.isBlank()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 10.dp)
                ) {
                    val onlineCount = socialState.friends.count { it.state != PresenceState.OFFLINE }
                    Text(
                        text = "INVITE FRIENDS ($onlineCount ONLINE)",
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (socialState.friends.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, SubtleBorder)
                        ) {
                            Text(
                                text = "Add friends from the Friends drawer on the Home Screen to invite them directly into your room.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = socialState.friends,
                                key = { it.friend.uid }
                            ) { presence ->
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
                Spacer(modifier = Modifier.weight(1f))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "WEBRTC P2P SIGNALING",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Room ID: ${room.matchId}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Channel: tetris-sync • ICE: STUN + TURN Relay",
                            color = ElectricGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onCancelLobby,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
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
                    text = "Leave Room Lobby",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
                    text = "Connection Blocked",
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
                    Text("Back to Lobby", fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Canvas(modifier = Modifier.size(26.dp)) {
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
                        fontSize = 14.sp
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
                            fontSize = 11.sp,
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
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
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
                            text = "Invited...",
                            color = ElectricGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onInvite,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
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
                            text = "Invite",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingSlotCard(
    playerId: Int,
    roleLabel: String,
    spawnHint: String
) {
    val accent = PlayerPalette.accentFor(playerId)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateSurface.copy(alpha = 0.65f)),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = roleLabel,
                    color = accent.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Waiting for friend...",
                    color = TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Drop Zone: $spawnHint",
                    color = TextSecondary.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }
            Icon(
                imageVector = Icons.Rounded.HourglassEmpty,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun LobbyPlayerCard(
    playerId: Int,
    roleLabel: String,
    nickname: String,
    skinId: String,
    spawnHint: String
) {
    val accent = PlayerPalette.accentFor(playerId)
    val renderer = SkinRegistry.resolve(skinId)
    val previewType = if (playerId == 1) TetrominoType.I else TetrominoType.T

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = roleLabel,
                    color = accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = nickname,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${renderer.displayName} Skin • $spawnHint",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Canvas(modifier = Modifier.size(40.dp)) {
                with(renderer) {
                    drawBlock(
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        tetrominoType = previewType,
                        playerId = playerId,
                        isGhost = false
                    )
                }
            }
        }
    }
}
