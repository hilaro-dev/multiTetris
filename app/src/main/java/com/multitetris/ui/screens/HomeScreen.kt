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
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
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
import com.multitetris.game.BotDifficulty
import com.multitetris.game.GameMode
import com.multitetris.game.SabotageType
import com.multitetris.game.TetrominoType
import com.multitetris.game.VersusSubMode
import com.multitetris.network.social.FriendRequest
import com.multitetris.network.social.MatchmakingState
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.RoomInvite
import com.multitetris.network.social.SocialUiState
import com.multitetris.stats.CareerStatsSnapshot
import com.multitetris.ui.i18n.LocalStrings
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
    careerStats: CareerStatsSnapshot = CareerStatsSnapshot(),
    isDarkMode: Boolean = true,
    soundEnabled: Boolean = true,
    musicEnabled: Boolean = true,
    vibrationEnabled: Boolean = true,
    onSelectMode: (GameMode) -> Unit,
    onSetDarkMode: (Boolean) -> Unit = {},
    onSetSoundEnabled: (Boolean) -> Unit = {},
    onSetMusicEnabled: (Boolean) -> Unit = {},
    onSetVibrationEnabled: (Boolean) -> Unit = {},
    languageCode: String = "en",
    onSetLanguage: (String) -> Unit = {},
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
    onStartLocalTest: (playerCount: Int, subMode: VersusSubMode) -> Unit = { _, _ -> },
    onStartSingleplayerBot: (
        playWithBot: Boolean,
        mode: GameMode,
        sabotageEnabled: Boolean,
        mysteryEnabled: Boolean,
        botDifficulty: BotDifficulty,
        selectedSabotages: List<SabotageType>
    ) -> Unit = { _, _, _, _, _, _ -> },
    onAcceptRoomInvite: (RoomInvite) -> Unit = {},
    onDeclineRoomInvite: (RoomInvite) -> Unit = {}
) {
    var showFriendsSheet by remember { mutableStateOf(false) }
    var showMissionsSheet by remember { mutableStateOf(false) }
    var showStatsSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSingleplayerSheet by remember { mutableStateOf(false) }
    var showMultiplayerSheet by remember { mutableStateOf(false) }
    var activeTooltip by remember { mutableStateOf<InfoTooltipData?>(null) }
    val strings = LocalStrings.current

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
                    colors = listOf(
                        ObsidianBg,
                        if (isDarkMode) Color(0xFF0C1324) else Color(0xFFE4ECF8),
                        ObsidianBg
                    )
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
                onOpenStats = { showStatsSheet = true },
                onOpenFriends = { showFriendsSheet = true },
                onOpenSettings = { showSettingsSheet = true }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Center Hero: Bold MultiTetris title
            Text(
                text = "MultiTetris",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                letterSpacing = (-0.8).sp
            )
            Text(
                text = strings.appSubtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyberCyan,
                letterSpacing = 1.4.sp,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.weight(1.1f))

            // Primary Action Area: Create Room, Secondary Shop, Subtle Local 2P Test
            val modeAccent = when (selectedMode) {
                GameMode.CO_OP -> CyberCyan
                GameMode.VERSUS -> NeonMagenta
                GameMode.SEPARATE -> ElectricGold
            }
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
                        val strings = LocalStrings.current
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

                    // Two prominent, balanced primary buttons: Singleplayer & Multiplayer
                    Button(
                        onClick = { showSingleplayerSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = ObsidianBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartToy,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = strings.singleplayerTitle.uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = strings.singleplayerSubtitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showMultiplayerSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonMagenta,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = strings.multiplayerTitle.uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (onlineFriendsCount > 0) {
                                    "${strings.multiplayerSubtitle} ($onlineFriendsCount ${strings.onlineLabel.lowercase()})"
                                } else {
                                    strings.multiplayerSubtitle
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                    text = strings.skinShopButton(unlockedCount, SkinCatalog.items.size),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showSingleplayerSheet) {
        SingleplayerSetupSheet(
            initialMode = selectedMode,
            onStartMatch = onStartSingleplayerBot,
            onDismiss = { showSingleplayerSheet = false }
        )
    }

    if (showMultiplayerSheet) {
        MultiplayerEntrySheet(
            selectedMode = selectedMode,
            socialState = socialState,
            onSelectMode = onSelectMode,
            onCreateRoomLobby = onCreateRoom,
            onAcceptRoomInvite = onAcceptRoomInvite,
            onDeclineRoomInvite = onDeclineRoomInvite,
            onOpenFriends = { showFriendsSheet = true },
            onDismiss = { showMultiplayerSheet = false }
        )
    }

    if (showFriendsSheet) {
        FriendsSheet(
            socialState = socialState,
            friendRivalry = careerStats.friendRivalry,
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

    if (showStatsSheet) {
        CareerStatsSheet(
            stats = careerStats,
            onDismiss = { showStatsSheet = false }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            isDarkMode = isDarkMode,
            soundEnabled = soundEnabled,
            musicEnabled = musicEnabled,
            vibrationEnabled = vibrationEnabled,
            languageCode = languageCode,
            onSetDarkMode = onSetDarkMode,
            onSetSoundEnabled = onSetSoundEnabled,
            onSetMusicEnabled = onSetMusicEnabled,
            onSetVibrationEnabled = onSetVibrationEnabled,
            onSetLanguage = onSetLanguage,
            onDismiss = { showSettingsSheet = false }
        )
    }

    activeTooltip?.let { tooltip ->
        InfoTooltipDialog(
            info = tooltip,
            onDismiss = { activeTooltip = null }
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
    onOpenStats: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Compact Player Profile Pill
        val strings = LocalStrings.current
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
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Clickable Coin Balance Badge
            val strings = LocalStrings.current
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
                contentDescription = strings.dailyMissionsContentDesc,
                tint = ElectricGold,
                showDot = hasClaimableMissions,
                badgeCount = 0,
                onClick = onOpenMissions
            )

            // Career Stats Icon Button
            TopIconBadgeButton(
                icon = Icons.Rounded.BarChart,
                contentDescription = strings.careerStatsContentDesc,
                tint = NeonMagenta,
                showDot = false,
                badgeCount = 0,
                onClick = onOpenStats
            )

            // Friends Icon Button with pending request/invite badge
            TopIconBadgeButton(
                icon = Icons.Rounded.People,
                contentDescription = strings.friendsContentDesc,
                tint = CyberCyan,
                showDot = false,
                badgeCount = friendsBadgeCount,
                onClick = onOpenFriends
            )

            // Settings Icon Button
            TopIconBadgeButton(
                icon = Icons.Rounded.Settings,
                contentDescription = strings.settingsContentDesc,
                tint = TextPrimary,
                showDot = false,
                badgeCount = 0,
                onClick = onOpenSettings
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
        val strings = LocalStrings.current
    Surface(
            modifier = Modifier
                .size(34.dp)
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
internal fun SegmentedModeToggle(
    selectedMode: GameMode,
    onSelectMode: (GameMode) -> Unit,
    onShowInfo: (GameMode) -> Unit
) {
    val strings = LocalStrings.current
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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegmentedModeOption(
                modifier = Modifier.weight(1f),
                title = strings.gameModeLabel(GameMode.CO_OP),
                icon = Icons.Rounded.Groups,
                selected = selectedMode == GameMode.CO_OP,
                accent = CyberCyan,
                onClick = { onSelectMode(GameMode.CO_OP) },
                onInfoClick = { onShowInfo(GameMode.CO_OP) }
            )
            SegmentedModeOption(
                modifier = Modifier.weight(1f),
                title = strings.gameModeLabel(GameMode.VERSUS),
                icon = Icons.Rounded.Bolt,
                selected = selectedMode == GameMode.VERSUS,
                accent = NeonMagenta,
                onClick = { onSelectMode(GameMode.VERSUS) },
                onInfoClick = { onShowInfo(GameMode.VERSUS) }
            )
            SegmentedModeOption(
                modifier = Modifier.weight(1f),
                title = strings.gameModeLabel(GameMode.SEPARATE),
                icon = Icons.Rounded.GridView,
                selected = selectedMode == GameMode.SEPARATE,
                accent = ElectricGold,
                onClick = { onSelectMode(GameMode.SEPARATE) },
                onInfoClick = { onShowInfo(GameMode.SEPARATE) }
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
    onClick: () -> Unit,
    onInfoClick: () -> Unit
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
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) accent else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                color = if (selected) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(2.dp))
            InfoIconButton(
                contentDescription = "$title Info",
                tint = if (selected) accent else TextSecondary,
                iconSize = 12.dp,
                touchSize = 16.dp,
                onClick = onInfoClick
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
