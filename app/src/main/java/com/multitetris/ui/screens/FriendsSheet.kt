package com.multitetris.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.game.TetrominoType
import com.multitetris.network.social.FriendLivePresence
import com.multitetris.network.social.FriendRequest
import com.multitetris.network.social.PresenceState
import com.multitetris.network.social.SocialUiState
import com.multitetris.stats.FriendMatchStats
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsSheet(
    socialState: SocialUiState,
    friendRivalry: Map<String, FriendMatchStats> = emptyMap(),
    onDismiss: () -> Unit,
    onUpdateNickname: (String) -> Unit,
    onAddFriendByCode: (String) -> Unit,
    onAcceptFriendRequest: (FriendRequest) -> Unit,
    onDeclineFriendRequest: (String) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onClearFeedback: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var friendCodeInput by remember { mutableStateOf("") }
    var isEditingNickname by remember { mutableStateOf(false) }
    var nicknameDraft by remember(socialState.userProfile.nickname) {
        mutableStateOf(socialState.userProfile.nickname)
    }
    var copiedJustNow by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = LocalStrings.current.friendsAndAccountTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    val connectionLabel = when {
                        !socialState.isFirebaseConfigured -> "Firebase not configured (see FIREBASE_SETUP.md)"
                        socialState.isFirebaseConnected -> "Connected • Live Presence Active"
                        socialState.isSigningIn -> "Signing in anonymously..."
                        else -> "Connecting to Firebase..."
                    }
                    Text(
                        text = connectionLabel,
                        fontSize = 11.sp,
                        color = if (socialState.isFirebaseConnected) Color(0xFF2FE066) else TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = LocalStrings.current.close,
                        tint = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Own Identity + Copy / Share Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Editable Nickname row
                    if (isEditingNickname) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = nicknameDraft,
                                onValueChange = { nicknameDraft = it.take(20) },
                                singleLine = true,
                                label = { Text("Nickname") },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = SubtleBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            IconButton(
                                onClick = {
                                    onUpdateNickname(nicknameDraft)
                                    isEditingNickname = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = LocalStrings.current.save,
                                    tint = CyberCyan
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (LocalStrings.current.gotIt == "Anladım") "PROFİLİN" else "YOUR PROFILE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = socialState.userProfile.nickname.ifBlank { "Player" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }
                            IconButton(onClick = { isEditingNickname = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = LocalStrings.current.editNickname,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Permanent Friend Code + Copy & Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (LocalStrings.current.gotIt == "Anladım") "ARKADAŞ KODUN" else "YOUR FRIEND CODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = socialState.userProfile.friendCode.ifBlank { "MT------" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CyberCyan,
                                letterSpacing = 1.4.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copyFriendCode(context, socialState.userProfile.friendCode)
                                    copiedJustNow = true
                                },
                                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (copiedJustNow) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (copiedJustNow) "Copied" else "Copy",
                                    color = CyberCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    shareFriendCode(
                                        context,
                                        socialState.userProfile.nickname,
                                        socialState.userProfile.friendCode
                                    )
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyberCyan,
                                    contentColor = ObsidianBg
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (LocalStrings.current.gotIt == "Anladım") "Paylaş" else "Share",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Add Friend by MT-XXXXX code
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = friendCodeInput,
                    onValueChange = {
                        friendCodeInput = it.uppercase().take(8)
                        onClearFeedback()
                    },
                    singleLine = true,
                    placeholder = { Text("MT-XXXXX", color = TextSecondary) },
                    label = { Text("Friend Code") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonMagenta,
                        unfocusedBorderColor = SubtleBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Button(
                    onClick = {
                        onAddFriendByCode(friendCodeInput)
                        friendCodeInput = ""
                    },
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonMagenta,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Send Friend Request",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Send Request", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            socialState.feedbackMessage?.let { feedback ->
                Text(
                    text = feedback,
                    color = ElectricGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (socialState.pendingFriendRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = LocalStrings.current.incomingRequestsHeader(socialState.pendingFriendRequests.size),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = ElectricGold,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    socialState.pendingFriendRequests.forEach { request ->
                        PendingRequestRow(
                            request = request,
                            onAccept = { onAcceptFriendRequest(request) },
                            onDecline = { onDeclineFriendRequest(request.fromUid) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = LocalStrings.current.friendsListHeader(socialState.friends.size),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (socialState.friends.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Text(
                        text = LocalStrings.current.noFriendsYet,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = socialState.friends,
                        key = { it.friend.uid }
                    ) { friendPresence ->
                        FriendRowItem(
                            presence = friendPresence,
                            rivalryStats = friendRivalry[friendPresence.friend.uid],
                            onRemove = { onRemoveFriend(friendPresence.friend.uid) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PendingRequestRow(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val renderer = SkinRegistry.resolve(request.fromSkinId)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElectricGold.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.5f))
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
                        text = request.fromNickname,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (LocalStrings.current.gotIt == "Anladım") "${request.fromCode} • Seni eklemek istiyor" else "${request.fromCode} • Wants to add you",
                        color = ElectricGold,
                        fontSize = 11.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onAccept,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text(LocalStrings.current.accept, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                OutlinedButton(
                    onClick = onDecline,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp),
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Text("Decline", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FriendRowItem(
    presence: FriendLivePresence,
    rivalryStats: FriendMatchStats? = null,
    onRemove: () -> Unit
) {
    val badgeColor = when (presence.state) {
        PresenceState.SEARCHING -> ElectricGold
        PresenceState.ONLINE -> Color(0xFF2FE066)
        PresenceState.OFFLINE -> TextSecondary
    }
    val renderer = SkinRegistry.resolve(presence.friend.selectedSkinId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.dp, SubtleBorder)
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
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${presence.friend.friendCode} • ${renderer.displayName}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    val hasRivalry = rivalryStats?.hasAnyMatches == true
                    Text(
                        text = rivalryStats?.formatRivalrySubtitle() ?: "No matches played yet",
                        color = if (hasRivalry) CyberCyan.copy(alpha = 0.88f) else TextSecondary.copy(alpha = 0.72f),
                        fontSize = 10.sp,
                        fontWeight = if (hasRivalry) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = badgeColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Text(
                            text = presence.statusBadgeText(),
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = LocalStrings.current.removeFriendContentDesc,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun copyFriendCode(context: Context, code: String) {
    if (code.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MultiTetris Friend Code", code))
}

private fun shareFriendCode(context: Context, nickname: String, code: String) {
    if (code.isBlank()) return
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Join me in MultiTetris! Add my Friend Code: $code ($nickname)"
        )
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share MultiTetris Friend Code"))
}
