package com.multitetris.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.multitetris.game.GameMode
import com.multitetris.game.SabotageConfig
import com.multitetris.game.SabotageType
import com.multitetris.game.VersusSubMode
import com.multitetris.ui.theme.CyberCyan
import com.multitetris.ui.theme.ElectricGold
import com.multitetris.ui.theme.NeonMagenta
import com.multitetris.ui.theme.SlateSurface
import com.multitetris.ui.theme.SubtleBorder
import com.multitetris.ui.theme.TextPrimary
import com.multitetris.ui.theme.TextSecondary

data class InfoTooltipData(
    val title: String,
    val badge: String? = null,
    val description: String,
    val accentColor: Color = CyberCyan
)

object GameInfoCatalog {

    fun forGameMode(mode: GameMode): InfoTooltipData = when (mode) {
        GameMode.CO_OP -> InfoTooltipData(
            title = "Co-op Mode",
            badge = "2–4 PLAYERS",
            description = "Share one arena with teammates and combine your score. If a player's spawn zone gets blocked, clearing any line frees them back into the match!",
            accentColor = CyberCyan
        )

        GameMode.VERSUS -> InfoTooltipData(
            title = "Versus Mode",
            badge = "LAST STANDING",
            description = "Compete to outlast every opponent! Clearing lines sends a brief speed burst to rivals. Top out and you're eliminated while survivors fight on.",
            accentColor = NeonMagenta
        )
    }

    fun forVersusSubMode(subMode: VersusSubMode): InfoTooltipData = when (subMode) {
        VersusSubMode.FREE_FOR_ALL -> InfoTooltipData(
            title = "Free-For-All (FFA)",
            badge = "SOLO SURVIVAL",
            description = "Every player fights for themselves across 2–4 spawn zones. Outlast every rival to claim 1st place and top coin rewards.",
            accentColor = NeonMagenta
        )

        VersusSubMode.TEAMS_2V2 -> InfoTooltipData(
            title = "2v2 / Teams Mode",
            badge = "CYAN VS MAGENTA",
            description = "Two teams battle on a 16×20 board. Line-clear speed bursts and sabotages only hit the opposing team—last team with a standing player wins!",
            accentColor = CyberCyan
        )
    }

    val sabotageMode: InfoTooltipData = InfoTooltipData(
        title = "Sabotage Mode",
        badge = "VERSUS & CO-OP",
        description = "In Versus, each player drafts 2 one-time sabotage abilities to disrupt rivals. In Co-op, a Rogue AI triggers random System Glitches every 22 seconds!",
        accentColor = ElectricGold
    )

    val mysteryMode: InfoTooltipData = InfoTooltipData(
        title = "Mystery Mode",
        badge = "+1.25X COINS",
        description = "Your falling piece turns invisible 1 second after spawning—steer by memory! Other players can still see your piece to spot or taunt you.",
        accentColor = Color(0xFFA855F7)
    )

    fun forSabotage(type: SabotageType): InfoTooltipData {
        val ms = SabotageConfig.durationMs(type)
        val secondsLabel = if (ms % 1000L == 0L) "${ms / 1000L}s" else "${ms / 1000.0}s"
        val details = when (type) {
            SabotageType.BLINDFOLD_FOG ->
                "Covers the target's entire board in opaque swirling storm fog so they must place pieces purely by spatial memory."
            SabotageType.CONTROLS_FLIP ->
                "Swaps the target's Left and Right movement buttons for the duration, throwing off muscle memory."
            SabotageType.BUTTON_FREEZE ->
                "Freezes all of the target's movement and rotation buttons in ice so they cannot steer until it thaws."
            SabotageType.TURBO_DROP ->
                "Triples the target's piece fall speed, forcing rapid reactions before blocks stack up."
            SabotageType.DROP_JAMMER ->
                "Scrambles the target's Next Piece preview to ??? and disables their Hard Drop button."
        }
        return InfoTooltipData(
            title = type.displayName,
            badge = "DURATION: $secondsLabel",
            description = details,
            accentColor = when (type) {
                SabotageType.BLINDFOLD_FOG -> Color(0xFFB388FF)
                SabotageType.CONTROLS_FLIP -> NeonMagenta
                SabotageType.BUTTON_FREEZE -> CyberCyan
                SabotageType.TURBO_DROP -> ElectricGold
                SabotageType.DROP_JAMMER -> Color(0xFFFF6D5A)
            }
        )
    }
}

@Composable
fun InfoIconButton(
    contentDescription: String,
    tint: Color = TextSecondary,
    iconSize: Dp = 15.dp,
    touchSize: Dp = 22.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun InfoTooltipDialog(
    info: InfoTooltipData,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = BorderStroke(1.2.dp, info.accentColor.copy(alpha = 0.75f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = info.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = info.title,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!info.badge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = info.accentColor.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, info.accentColor.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = info.badge,
                            color = info.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = info.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
