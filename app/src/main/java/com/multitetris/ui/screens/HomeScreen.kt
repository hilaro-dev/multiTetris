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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.economy.EconomyUiState
import com.multitetris.game.GameMode
import com.multitetris.game.TetrominoType
import com.multitetris.network.social.FriendRequest
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.SocialUiState
import com.multitetris.ui.skin.SkinCatalog
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
    economyState: EconomyUiState,
    onSelectMode: (GameMode) -> Unit,
    onOpenShop: () -> Unit,
    onClaimMission: (String) -> Unit,
    onRefreshMissions: () -> Unit,
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
    var showMissionsSheet by remember { mutableStateOf(false) }

    val onlineFriendsCount = socialState.friends.count { it.state != PresenceState.OFFLINE }
    val searchingFriendsCount = socialState.friends.count { it.state == PresenceState.SEARCHING }
    val friendsBadgeCount = socialState.pendingFriendRequests.size + socialState.incomingRoomInvites.size
    val equippedP1SkinId = selectedSkins[1] ?: "classic"
    val unlockedCount = economyState.unlockedSkins.size.coerceAtMost(SkinCatalog.items.size)

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
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Minimalist Top Bar: Profile pill + Coin Balance + Missions + Friends
            HomeTopBar(
                nickname = socialState.userProfile.nickname,
                equippedSkinId = equippedP1SkinId,
                isConnected = socialState.isFirebaseConnected,
                coinBalance = economyState.coinBalance,
                hasClaimableMissions = economyState.claimableMissionCount > 0,
                friendsBadgeCount = friendsBadgeCount,
                onOpenProfile = { showFriendsSheet = true },
                onOpenShop = onOpenShop,
                onOpenMissions = {
                    onRefreshMissions()
                    showMissionsSheet = true
                },
                onOpenFriends = { showFriendsSheet = true }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Center Hero: Bold MultiTetris title + compact CO-OP / VERSUS segmented toggle
            Text(
                text = "MultiTetris",
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = (-0.8).sp
            )
            Text(
                text = "Shared 10×20 Arena",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(22.dp))

            SegmentedModeToggle(
                selectedMode = selectedMode,
                onSelectMode = onSelectMode
            )

            Spacer(modifier = Modifier.weight(1.1f))

            // Primary Action Area: Create Room, Secondary Shop, Subtle Local 2P Test
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
                                text = "Waiting for a saved friend • $searchingFriendsCount searching now",
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
                            .height(60.dp),
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
                                    "Open Room Lobby & invite friends"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Shop button
            OutlinedButton(
                onClick = onOpenShop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.2.dp, CyberCyan.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storefront,
                    contentDescription = null,
                    tint = CyberCyan,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Shop • $unlockedCount/${SkinCatalog.items.size} Unlocked",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtle Local 2P Test button at bottom
            TextButton(onClick = onStartLocalTest) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Local 2P Test",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
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

    if (showMissionsSheet) {
        DailyMissionsSheet(
            coinBalance = economyState.coinBalance,
            missions = economyState.dailyMissions,
            onClaimMission = onClaimMission,
            onDismiss = { showMissionsSheet = false }
        )
    }
}

@Composable
private fun HomeTopBar(
    nickname: String,
    equippedSkinId: String,
    isConnected: Boolean,
    coinBalance: Int,
    hasClaimableMissions: Boolean,
    friendsBadgeCount: Int,
    onOpenProfile: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenMissions: () -> Unit,
    onOpenFriends: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact Player Profile Pill
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onOpenProfile),
            shape = RoundedCornerShape(50),
            color = ElevatedCard,
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    SkinMiniPreview(
                        skinId = equippedSkinId,
                        playerId = 1,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) Color(0xFF2FE066) else ElectricGold)
                    )
                }
                Text(
                    text = nickname.ifBlank { "Player" },
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Clickable Coin Balance Badge
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onOpenShop),
                shape = RoundedCornerShape(50),
                color = ElectricGold.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.65f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MonetizationOn,
                        contentDescription = "Coins",
                        tint = ElectricGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = coinBalance.toString(),
                        color = ElectricGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Missions Icon Button with red notification dot when claimable
            TopIconBadgeButton(
                icon = Icons.Rounded.EmojiEvents,
                contentDescription = "Daily Missions",
                tint = ElectricGold,
                showDot = hasClaimableMissions,
                badgeCount = 0,
                onClick = onOpenMissions
            )

            // Friends Icon Button with pending request/invite badge
            TopIconBadgeButton(
                icon = Icons.Rounded.People,
                contentDescription = "Friends",
                tint = CyberCyan,
                showDot = false,
                badgeCount = friendsBadgeCount,
                onClick = onOpenFriends
            )
        }
    }
}

@Composable
private fun TopIconBadgeButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    showDot: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    Box {
        Surface(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            shape = CircleShape,
            color = ElevatedCard,
            border = BorderStroke(1.dp, SubtleBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(NeonMagenta),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.coerceAtMost(9).toString(),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 1.dp, y = (-1).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B5C))
            )
        }
    }
}

@Composable
private fun SegmentedModeToggle(
    selectedMode: GameMode,
    onSelectMode: (GameMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SegmentedModeOption(
                modifier = Modifier.weight(1f),
                title = GameMode.CO_OP.label,
                icon = Icons.Rounded.Groups,
                selected = selectedMode == GameMode.CO_OP,
                accent = CyberCyan,
                onClick = { onSelectMode(GameMode.CO_OP) }
            )
            SegmentedModeOption(
                modifier = Modifier.weight(1f),
                title = GameMode.VERSUS.label,
                icon = Icons.Rounded.Bolt,
                selected = selectedMode == GameMode.VERSUS,
                accent = NeonMagenta,
                onClick = { onSelectMode(GameMode.VERSUS) }
            )
        }
    }
}

@Composable
private fun SegmentedModeOption(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.18f) else Color.Transparent,
        border = if (selected) BorderStroke(1.2.dp, accent) else null
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (selected) TextPrimary else TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
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
