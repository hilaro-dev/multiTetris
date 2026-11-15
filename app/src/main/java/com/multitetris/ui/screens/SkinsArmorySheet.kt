package com.multitetris.ui.screens

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.game.TetrominoType
import com.multitetris.ui.skin.PlayerPalette
import com.multitetris.ui.skin.SkinCatalog
import com.multitetris.ui.skin.SkinCatalogItem
import com.multitetris.ui.skin.SkinRenderer
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
fun SkinsArmorySheet(
    selectedSkins: Map<Int, String>,
    coinBalance: Int,
    unlockedSkins: Set<String>,
    onEquipSkin: (playerId: Int, skinId: String) -> Unit,
    onPurchaseSkin: (playerId: Int, skinId: String, price: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var targetPlayerId by remember { mutableIntStateOf(1) }
    var pendingPurchaseItem by remember { mutableStateOf<SkinCatalogItem?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "armory_anim")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "armory_skin_progress"
    )

    val equippedSkinId = selectedSkins[targetPlayerId]
        ?: if (targetPlayerId == 1) "classic" else "neon_cyber"
    val targetAccent = PlayerPalette.accentFor(targetPlayerId)

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
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Skins Armory & Shop",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Syncs live to Firebase & P2P matches",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = ElectricGold.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MonetizationOn,
                                contentDescription = "Coins",
                                tint = ElectricGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = coinBalance.toString(),
                                color = ElectricGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Armory",
                            tint = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Player target selector: Online/P1 vs Local P2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArmoryTargetChip(
                    modifier = Modifier.weight(1f),
                    label = "Your Skin (P1 & Online)",
                    selected = targetPlayerId == 1,
                    accent = CyberCyan,
                    onClick = { targetPlayerId = 1 }
                )
                ArmoryTargetChip(
                    modifier = Modifier.weight(1f),
                    label = "Local P2 Skin",
                    selected = targetPlayerId == 2,
                    accent = NeonMagenta,
                    onClick = { targetPlayerId = 2 }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = SkinCatalog.items,
                    key = { it.renderer.skinId }
                ) { item ->
                    val isUnlocked = item.renderer.skinId in unlockedSkins || item.priceCoins == 0
                    SkinArmoryCard(
                        renderer = item.renderer,
                        priceCoins = item.priceCoins,
                        playerId = targetPlayerId,
                        isUnlocked = isUnlocked,
                        isEquipped = isUnlocked && item.renderer.skinId == equippedSkinId,
                        canAfford = coinBalance >= item.priceCoins,
                        accent = targetAccent,
                        animProgress = animProgress,
                        onEquip = { onEquipSkin(targetPlayerId, item.renderer.skinId) },
                        onRequestPurchase = { pendingPurchaseItem = item }
                    )
                }
            }
        }
    }

    pendingPurchaseItem?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingPurchaseItem = null },
            containerColor = SlateSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    text = "Unlock ${item.renderer.displayName}?",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Text(
                    text = "Spend ${item.priceCoins} Coins to permanently unlock this skin for all matches?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPurchaseSkin(targetPlayerId, item.renderer.skinId, item.priceCoins)
                        pendingPurchaseItem = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricGold,
                        contentColor = ObsidianBg
                    )
                ) {
                    Text(
                        text = "Buy (${item.priceCoins})",
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurchaseItem = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ArmoryTargetChip(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.16f) else ElevatedCard,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else SubtleBorder)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SkinArmoryCard(
    renderer: SkinRenderer,
    priceCoins: Int,
    playerId: Int,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    accent: Color,
    animProgress: Float,
    onEquip: () -> Unit,
    onRequestPurchase: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEquipped) accent.copy(alpha = 0.12f) else ElevatedCard
        ),
        border = BorderStroke(
            width = if (isEquipped) 2.dp else 1.dp,
            color = when {
                isEquipped -> accent
                !isUnlocked && canAfford -> ElectricGold.copy(alpha = 0.5f)
                else -> SubtleBorder
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live animated Tetromino preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF070B14), Color(0xFF0E1629))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(width = 76.dp, height = 52.dp)
                        .alpha(if (isUnlocked) 1f else 0.72f)
                ) {
                    val cells = TetrominoType.T.offsetsFor(0)
                    val cellSize = minOf(size.width / 3.4f, size.height / 2.2f)
                    val totalW = 3f * cellSize
                    val totalH = 2f * cellSize
                    val startX = (size.width - totalW) / 2f
                    val startY = (size.height - totalH) / 2f
                    val minX = cells.minOf { it.x }
                    val minY = cells.minOf { it.y }

                    for (cell in cells) {
                        val drawX = startX + (cell.x - minX) * cellSize
                        val drawY = startY + (cell.y - minY) * cellSize
                        with(renderer) {
                            drawBlock(
                                topLeft = Offset(drawX, drawY),
                                size = Size(cellSize, cellSize),
                                tetrominoType = TetrominoType.T,
                                playerId = playerId,
                                isGhost = false,
                                animProgress = animProgress
                            )
                        }
                    }
                }

                if (!isUnlocked) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBg.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, ElectricGold.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = ElectricGold,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = renderer.displayName,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = renderer.description,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            when {
                !isUnlocked -> {
                    Button(
                        onClick = onRequestPurchase,
                        enabled = canAfford,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricGold,
                            contentColor = ObsidianBg,
                            disabledContainerColor = SubtleBorder.copy(alpha = 0.55f),
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Buy ($priceCoins Coins)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                isEquipped -> {
                    Button(
                        onClick = onEquip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = if (playerId == 2) Color.White else ObsidianBg
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Equipped",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                else -> {
                    OutlinedButton(
                        onClick = onEquip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        border = BorderStroke(1.dp, SubtleBorder)
                    ) {
                        Text(
                            text = "Equip",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
