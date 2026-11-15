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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun SettingsSheet(
    isDarkMode: Boolean,
    soundEnabled: Boolean,
    musicEnabled: Boolean,
    vibrationEnabled: Boolean,
    languageCode: String = "en",
    onSetDarkMode: (Boolean) -> Unit,
    onSetSoundEnabled: (Boolean) -> Unit,
    onSetMusicEnabled: (Boolean) -> Unit,
    onSetVibrationEnabled: (Boolean) -> Unit,
    onSetLanguage: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
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
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = strings.settingsTitle,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = strings.close,
                        tint = TextSecondary
                    )
                }
            }

            LanguageSelectorRow(
                selectedCode = languageCode,
                title = strings.languageTitle,
                subtitle = strings.languageSubtitle,
                englishLabel = strings.languageEnglish,
                turkishLabel = strings.languageTurkish,
                onSelect = onSetLanguage
            )

            SettingsToggleRow(
                icon = Icons.Rounded.DarkMode,
                title = strings.darkModeTitle,
                subtitle = if (isDarkMode) strings.darkModeSubtitleOn else strings.darkModeSubtitleOff,
                accent = CyberCyan,
                checked = isDarkMode,
                onCheckedChange = onSetDarkMode
            )

            SettingsToggleRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                title = strings.soundEffectsTitle,
                subtitle = if (soundEnabled) strings.soundEffectsSubtitleOn else strings.soundEffectsSubtitleOff,
                accent = ElectricGold,
                checked = soundEnabled,
                onCheckedChange = onSetSoundEnabled
            )

            SettingsToggleRow(
                icon = Icons.Rounded.MusicNote,
                title = strings.backgroundMusicTitle,
                subtitle = if (musicEnabled) strings.backgroundMusicSubtitleOn else strings.backgroundMusicSubtitleOff,
                accent = Color(0xFFA855F7),
                checked = musicEnabled,
                onCheckedChange = onSetMusicEnabled
            )

            SettingsToggleRow(
                icon = Icons.Rounded.Vibration,
                title = strings.vibrationTitle,
                subtitle = if (vibrationEnabled) strings.vibrationSubtitleOn else strings.vibrationSubtitleOff,
                accent = NeonMagenta,
                checked = vibrationEnabled,
                onCheckedChange = onSetVibrationEnabled
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun LanguageSelectorRow(
    selectedCode: String,
    title: String,
    subtitle: String,
    englishLabel: String,
    turkishLabel: String,
    onSelect: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCard,
        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val options = listOf("en" to englishLabel, "tr" to turkishLabel)
                options.forEach { (code, label) ->
                    val selected = selectedCode == code
                    Surface(
                        onClick = { onSelect(code) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) CyberCyan.copy(alpha = 0.18f) else SlateSurface,
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) CyberCyan else SubtleBorder
                        )
                    ) {
                        Text(
                            text = label,
                            color = if (selected) CyberCyan else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = ElevatedCard,
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) accent.copy(alpha = 0.55f) else SubtleBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) accent else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianBg,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SlateSurface
                )
            )
        }
    }
}
