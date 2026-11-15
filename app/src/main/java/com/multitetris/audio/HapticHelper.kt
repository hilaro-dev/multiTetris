package com.multitetris.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Tactile vibration helper for line-clear haptic pulses with graceful API-level fallbacks.
 */
object HapticHelper {

    fun vibrateLineClear(
        context: Context,
        linesCleared: Int,
        vibrationEnabled: Boolean
    ) {
        if (!vibrationEnabled || linesCleared <= 0) return
        val (durationMs, amplitude) = when {
            linesCleared >= 4 -> 95L to 255
            linesCleared >= 2 -> 55L to 185
            else -> 35L to 130
        }
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        }
    }
}
