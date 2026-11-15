package com.multitetris.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.multitetris.stats.CareerStatsSnapshot
import com.multitetris.stats.ModeCareerStats
import com.multitetris.ui.i18n.LocalStrings
import com.multitetris.ui.theme.CyberCyan
import com.multitetris.ui.theme.ElectricGold
import com.multitetris.ui.theme.ElevatedCard
import com.multitetris.ui.theme.NeonMagenta
import com.multitetris.ui.theme.SlateSurface
import com.multitetris.ui.theme.SubtleBorder
import com.multitetris.ui.theme.TextPrimary
import com.multitetris.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareerStatsSheet(
    stats: CareerStatsSnapshot,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .verticalScroll(rememberScrollState())
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
                        imageVector = Icons.Rounded.BarChart,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = LocalStrings.current.careerStatsTitle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = LocalStrings.current.careerStatsSubtitle,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = LocalStrings.current.close,
                        tint = TextSecondary
                    )
                }
            }

            // 2x2 Career Totals Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CareerStatTile(
                    modifier = Modifier.weight(1f),
                    label = LocalStrings.current.statMatchesPlayed.uppercase(),
                    value = stats.totalMatches.toString(),
                    subtitle = LocalStrings.current.linesShort(stats.totalLinesCleared),
                    accent = CyberCyan
                )
                CareerStatTile(
                    modifier = Modifier.weight(1f),
                    label = LocalStrings.current.statWinRate.uppercase(),
                    value = "${stats.versusWinRatePercent}%",
                    subtitle = "${stats.versusWins}W - ${stats.versusLosses}L",
                    accent = NeonMagenta
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CareerStatTile(
                    modifier = Modifier.weight(1f),
                    label = LocalStrings.current.statBestScore.uppercase(),
                    value = stats.highestSoloOrMatchScore.toString(),
                    subtitle = if (LocalStrings.current.gotIt == "Anladım") "En iyi tek maç" else "Best single run",
                    accent = ElectricGold
                )
                CareerStatTile(
                    modifier = Modifier.weight(1f),
                    label = LocalStrings.current.statVersusWins.uppercase(),
                    value = stats.versusWins.toString(),
                    subtitle = if (LocalStrings.current.gotIt == "Anladım") "${stats.versusLosses} mağlubiyet" else "${stats.versusLosses} losses",
                    accent = Color(0xFF2FE066)
                )
            }

            // Best Co-op Record Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = if (LocalStrings.current.gotIt == "Anladım") "EN İYİ İŞBİRLİĞİ REKORU" else "BEST CO-OP RECORD",
                                color = CyberCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "${stats.bestCoopScore} pts",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = CyberCyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = "${stats.bestCoopLines} ${LocalStrings.current.linesLabel}",
                            color = CyberCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Per-Mode Breakdown
            Text(
                text = if (LocalStrings.current.gotIt == "Anladım") "MOD BAZLI DAĞILIM" else "PER-MODE BREAKDOWN",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.9.sp
            )

            if (stats.modeStats.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = ElevatedCard,
                    border = BorderStroke(1.dp, SubtleBorder)
                ) {
                    Text(
                        text = if (LocalStrings.current.gotIt == "Anladım") "Henüz tamamlanmış maç yok. Kariyer kaydını başlatmak için İşbirliği veya Karşılıklı oyna!" else "No completed matches yet. Play Co-op or Versus to start building your career record!",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                stats.modeStats.entries.forEach { (modeKey, modeStat) ->
                    ModeBreakdownCard(modeKey = modeKey, stats = modeStat)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun CareerStatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    subtitle: String,
    accent: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.7.sp
            )
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ModeBreakdownCard(
    modeKey: String,
    stats: ModeCareerStats
) {
    val label = when (modeKey) {
        "CO_OP" -> "Co-op"
        "VERSUS_FFA" -> "Versus (FFA)"
        "VERSUS_TEAMS" -> "Versus (Teams)"
        else -> modeKey.replace('_', ' ')
    }
    val isCoop = modeKey == "CO_OP"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
        border = BorderStroke(1.dp, SubtleBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.EmojiEvents,
                    contentDescription = null,
                    tint = if (isCoop) CyberCyan else NeonMagenta,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = label,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (LocalStrings.current.gotIt == "Anladım") "${stats.totalMatches} maç • ${stats.totalLinesCleared} satır" else "${stats.totalMatches} matches • ${stats.totalLinesCleared} lines",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isCoop) "${stats.bestScore} best pts" else "${stats.winRatePercent}% Win",
                    color = if (isCoop) CyberCyan else ElectricGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = if (isCoop) "${stats.bestLines} best lines" else "${stats.wins}W - ${stats.losses}L",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}
