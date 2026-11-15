package com.multitetris.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.game.GameMode
import com.multitetris.game.TetrominoType
import com.multitetris.network.social.FriendRequest
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.SocialUiState
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
fun HomeScreen(
    selectedMode: GameMode,
    selectedSkins: Map<Int, String>,
    socialState: SocialUiState,
    onSelectMode: (GameMode) -> Unit,
    onSelectSkin: (playerId: Int, skinId: String) -> Unit,
    onUpdateNickname: (String) -> Unit,
    onAddFriendByCode: (String) -> Unit,
    onAcceptFriendRequest: (FriendRequest) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onClearFriendFeedback: () -> Unit,
    onCreateRoom: () -> Unit,
    onCancelPlayTogetherSearch: () -> Unit,
    onStartLocalTest: () -> Unit
) {
    var showFriendsSheet by remember { mutableStateOf(false) }
    var showArmorySheet by remember { mutableStateOf(false) }

    val onlineFriendsCount = socialState.friends.count { it.state != PresenceState.OFFLINE }
    val searchingFriendsCount = socialState.friends.count { it.state == PresenceState.SEARCHING }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianBg, Color(0xFF0C1324), ObsidianBg)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Identity & Friends Drawer trigger bar
            TopSocialBar(
                nickname = socialState.userProfile.nickname,
                friendCode = socialState.userProfile.friendCode,
                isConnected = socialState.isFirebaseConnected,
                friendCount = socialState.friends.size,
                onlineFriendsCount = onlineFriendsCount,
                pendingRequestCount = socialState.pendingFriendRequests.size,
                onOpenFriends = { showFriendsSheet = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Title
            Text(
                text = "MultiTetris",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = "Shared 10×20 Board • Live Friend Matchmaking",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Mode Selector Section
            SectionLabel(text = "GAME MODE")
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeSelectorCard(
                    modifier = Modifier.weight(1f),
                    title = GameMode.CO_OP.label,
                    subtitle = "Shared score & level",
                    selected = selectedMode == GameMode.CO_OP,
                    accent = CyberCyan,
                    icon = Icons.Rounded.Groups,
                    onClick = { onSelectMode(GameMode.CO_OP) }
                )
                ModeSelectorCard(
                    modifier = Modifier.weight(1f),
                    title = GameMode.VERSUS.label,
                    subtitle = "Sabotage Push + Top-Out",
                    selected = selectedMode == GameMode.VERSUS,
                    accent = NeonMagenta,
                    icon = Icons.Rounded.Bolt,
                    onClick = { onSelectMode(GameMode.VERSUS) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode rules card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.dp, SubtleBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val isVersus = selectedMode == GameMode.VERSUS
                    Text(
                        text = if (isVersus) "Versus Rules" else "Co-op Rules",
                        color = if (isVersus) NeonMagenta else CyberCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isVersus) {
                            "Player 1 drops at Col 2 (zone 1–4) and Player 2 drops at Col 6 (zone 5–8). Clearing a line triggers a 0.5s Sabotage Push speed burst on your opponent. First spawn zone to overflow loses!"
                        } else {
                            "Both players share one score, line counter, and escalating level speed on the same 10×20 grid. Colliding mid-air pauses the upper piece until the lower piece moves or locks."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Block Skins Armory Section
            SectionLabel(text = "BLOCK SKINS ARMORY")
            Spacer(modifier = Modifier.height(8.dp))

            val p1Skin = SkinRegistry.resolve(selectedSkins[1] ?: "classic")
            val p2Skin = SkinRegistry.resolve(selectedSkins[2] ?: "neon_cyber")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showArmorySheet = true },
                colors = CardDefaults.cardColors(containerColor = SlateSurface),
                border = BorderStroke(1.2.dp, CyberCyan.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        SkinMiniPreview(
                            skinId = p1Skin.skinId,
                            playerId = 1,
                            modifier = Modifier.size(34.dp)
                        )
                        Column {
                            Text(
                                text = "Equipped: ${p1Skin.displayName}",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Local P2: ${p2Skin.displayName} • 6 Skins",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberCyan.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Skins Armory",
                                color = CyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Automatic "Play Together" Matchmaking Section
            val modeAccent = if (selectedMode == GameMode.VERSUS) NeonMagenta else CyberCyan
            when (val matchState = socialState.matchmakingState) {
                is MatchmakingState.Searching -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = modeAccent.copy(alpha = 0.14f)),
                        border = BorderStroke(1.5.dp, modeAccent)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = modeAccent,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Searching (${matchState.mode.label})...",
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Waiting for a saved friend to press PLAY in ${matchState.mode.label} • $searchingFriendsCount searching now",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onCancelPlayTogetherSearch,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, modeAccent)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = null,
                                    tint = modeAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Cancel Search",
                                    color = modeAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (matchState is MatchmakingState.Error) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = ElectricGold.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = matchState.message,
                                color = ElectricGold,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onCreateRoom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = modeAccent,
                            contentColor = if (selectedMode == GameMode.VERSUS) Color.White else ObsidianBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "CREATE ${selectedMode.label} ROOM",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (onlineFriendsCount > 0) {
                                    "Invite from $onlineFriendsCount online friend(s)"
                                } else {
                                    "Open Room Lobby & invite saved friends"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Stage 1 Local 2P Test Button
            OutlinedButton(
                onClick = onStartLocalTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.2.dp, SubtleBorder)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Local 2P Test",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showFriendsSheet) {
        FriendsSheet(
            socialState = socialState,
            onDismiss = { showFriendsSheet = false },
            onUpdateNickname = onUpdateNickname,
            onAddFriendByCode = onAddFriendByCode,
            onAcceptFriendRequest = onAcceptFriendRequest,
            onDeclineFriendRequest = onDeclineFriendRequest,
            onRemoveFriend = onRemoveFriend,
            onClearFeedback = onClearFriendFeedback
        )
    }

    if (showArmorySheet) {
        SkinsArmorySheet(
            selectedSkins = selectedSkins,
            onEquipSkin = onSelectSkin,
            onDismiss = { showArmorySheet = false }
        )
    }
}

@Composable
private fun TopSocialBar(
    nickname: String,
    friendCode: String,
    isConnected: Boolean,
    friendCount: Int,
    onlineFriendsCount: Int,
    pendingRequestCount: Int,
    onOpenFriends: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenFriends),
            shape = RoundedCornerShape(50),
            color = ElevatedCard,
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) Color(0xFF2FE066) else ElectricGold)
                )
                Column {
                    Text(
                        text = nickname.ifBlank { "Player" },
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = friendCode.ifBlank { "MT------" },
                        color = CyberCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenFriends),
            shape = RoundedCornerShape(50),
            color = CyberCyan.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.People,
                    contentDescription = "Friends",
                    tint = CyberCyan,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (onlineFriendsCount > 0) {
                        "Friends ($onlineFriendsCount/$friendCount)"
                    } else {
                        "Friends ($friendCount)"
                    },
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (pendingRequestCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(NeonMagenta),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pendingRequestCount.coerceAtMost(9).toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp
    )
}

@Composable
private fun ModeSelectorCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accent.copy(alpha = 0.14f) else SlateSurface
        ),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) accent else SubtleBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                color = if (selected) TextPrimary else TextSecondary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun SkinMiniPreview(
    skinId: String,
    playerId: Int,
    modifier: Modifier = Modifier
) {
    val renderer = SkinRegistry.resolve(skinId)
    val sampleType = if (playerId == 1) TetrominoType.I else TetrominoType.T
    Canvas(modifier = modifier) {
        with(renderer) {
            drawBlock(
                topLeft = Offset.Zero,
                size = Size(size.width, size.height),
                tetrominoType = sampleType,
                playerId = playerId,
                isGhost = false
            )
        }
    }
}
