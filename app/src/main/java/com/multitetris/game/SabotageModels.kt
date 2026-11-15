package com.multitetris.game

import kotlinx.serialization.Serializable

/**
 * Stage 10 Versus sabotage abilities.
 */
@Serializable
enum class SabotageType(
    val displayName: String,
    val shortLabel: String,
    val statusBadgeLabel: String,
    val description: String
) {
    BLINDFOLD_FOG(
        displayName = "Blindfold",
        shortLabel = "Blindfold",
        statusBadgeLabel = "🌫️ BLIND",
        description = "Covers bottom 10 rows in dark swirling fog."
    ),
    CONTROLS_FLIP(
        displayName = "Controls Flip",
        shortLabel = "Flip",
        statusBadgeLabel = "🔀 FLIPPED",
        description = "Swaps Left <-> Right movement controls."
    ),
    BUTTON_FREEZE(
        displayName = "Ice Freeze",
        shortLabel = "Freeze",
        statusBadgeLabel = "❄️ FROZEN",
        description = "Freezes all movement/rotation buttons in ice."
    ),
    TURBO_DROP(
        displayName = "Turbo Drop",
        shortLabel = "Turbo",
        statusBadgeLabel = "⚡ TURBO",
        description = "Triples piece fall speed."
    ),
    DROP_JAMMER(
        displayName = "Jammer",
        shortLabel = "Jammer",
        statusBadgeLabel = "🚫 JAMMED",
        description = "Scrambles Next Piece preview to ??? and disables Hard Drop."
    )
}

@Serializable
data class ActiveSabotageState(
    val type: SabotageType,
    val remainingMs: Long,
    val sourceLabel: String
) {
    val remainingSeconds: Int
        get() = ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(1)
}

/**
 * Centralized tunable durations and loadout rules for Versus sabotage abilities.
 */
object SabotageConfig {
    const val MAX_DRAFT_SLOTS: Int = 2
    const val COOP_GLITCH_INTERVAL_MS: Long = 22_000L
    const val COOP_GLITCH_WARNING_MS: Long = 3_000L

    val defaultLoadout: List<SabotageType> = listOf(
        SabotageType.BLINDFOLD_FOG,
        SabotageType.TURBO_DROP
    )

    fun durationMs(type: SabotageType): Long = when (type) {
        SabotageType.BLINDFOLD_FOG -> 6_000L
        SabotageType.CONTROLS_FLIP -> 5_500L
        SabotageType.BUTTON_FREEZE -> 3_500L
        SabotageType.TURBO_DROP -> 6_000L
        SabotageType.DROP_JAMMER -> 7_000L
    }

    fun resolveLoadout(raw: List<SabotageType>): List<SabotageType> {
        val unique = raw.distinct().take(MAX_DRAFT_SLOTS).toMutableList()
        for (fallback in defaultLoadout) {
            if (unique.size >= MAX_DRAFT_SLOTS) break
            if (fallback !in unique) {
                unique.add(fallback)
            }
        }
        return unique
    }

    fun toggleSelection(current: List<SabotageType>, tapped: SabotageType): List<SabotageType> {
        val clean = current.distinct().take(MAX_DRAFT_SLOTS)
        return when {
            tapped in clean -> clean.filterNot { it == tapped }
            clean.size < MAX_DRAFT_SLOTS -> clean + tapped
            else -> clean.drop(1) + tapped
        }
    }
}
