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
import com.multitetris.ui.i18n.AppStrings
import com.multitetris.ui.i18n.EnStrings
import com.multitetris.ui.i18n.LocalStrings
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

    fun forGameMode(mode: GameMode, strings: AppStrings = EnStrings): InfoTooltipData = when (mode) {
        GameMode.CO_OP -> InfoTooltipData(
            title = strings.tooltipCoopTitle,
            badge = if (strings === EnStrings) "2–4 PLAYERS" else "2–4 OYUNCU",
            description = strings.tooltipCoopDesc,
            accentColor = CyberCyan
        )

        GameMode.VERSUS -> InfoTooltipData(
            title = strings.tooltipVersusTitle,
            badge = if (strings === EnStrings) "LAST STANDING" else "SON KALAN KAZANIR",
            description = strings.tooltipVersusDesc,
            accentColor = NeonMagenta
        )
    }

    fun forVersusSubMode(subMode: VersusSubMode, strings: AppStrings = EnStrings): InfoTooltipData = when (subMode) {
        VersusSubMode.FREE_FOR_ALL -> InfoTooltipData(
            title = strings.tooltipFfaTitle,
            badge = if (strings === EnStrings) "SOLO SURVIVAL" else "TEKLİ HAYATTA KALMA",
            description = strings.tooltipFfaDesc,
            accentColor = NeonMagenta
        )

        VersusSubMode.TEAMS_2V2 -> InfoTooltipData(
            title = strings.tooltipTeamsTitle,
            badge = if (strings === EnStrings) "CYAN VS MAGENTA" else "MAVİ VS PEMBE",
            description = strings.tooltipTeamsDesc,
            accentColor = CyberCyan
        )
    }

    fun sabotageMode(strings: AppStrings = EnStrings): InfoTooltipData = InfoTooltipData(
        title = strings.tooltipSabotageTitle,
        badge = if (strings === EnStrings) "VERSUS & CO-OP" else "KARŞILIKLI & İŞBİRLİĞİ",
        description = strings.tooltipSabotageDesc,
        accentColor = ElectricGold
    )

    fun mysteryMode(strings: AppStrings = EnStrings): InfoTooltipData = InfoTooltipData(
        title = strings.tooltipMysteryTitle,
        badge = if (strings === EnStrings) "+1.25X COINS" else "+%25 ALTIN",
        description = strings.tooltipMysteryDesc,
        accentColor = Color(0xFFA855F7)
    )

    fun forSabotage(type: SabotageType, strings: AppStrings = EnStrings): InfoTooltipData {
        val ms = SabotageConfig.durationMs(type)
        val unit = if (strings === EnStrings) "s" else " sn"
        val secondsLabel = if (ms % 1000L == 0L) "${ms / 1000L}$unit" else "${ms / 1000.0}$unit"
        val details = when (type) {
            SabotageType.BLINDFOLD_FOG -> strings.tooltipBlindfoldDesc
            SabotageType.CONTROLS_FLIP -> strings.tooltipControlsFlipDesc
            SabotageType.BUTTON_FREEZE -> strings.tooltipIceFreezeDesc
            SabotageType.TURBO_DROP -> strings.tooltipTurboDropDesc
            SabotageType.DROP_JAMMER -> strings.tooltipJammerDesc
        }
        return InfoTooltipData(
            title = strings.sabotageDisplayName(type),
            badge = "${strings.tooltipDurationPrefix}$secondsLabel".uppercase(),
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
                            contentDescription = LocalStrings.current.close,
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
