package com.multitetris.economy

import kotlinx.serialization.Serializable

@Serializable
data class DailyMission(
    val id: String,
    val title: String,
    val target: Int,
    val progress: Int = 0,
    val rewardCoins: Int,
    val isClaimed: Boolean = false
) {
    val isCompleted: Boolean
        get() = progress >= target

    val isReadyToClaim: Boolean
        get() = isCompleted && !isClaimed
}

@Serializable
data class DailyMissionsSnapshot(
    val dateKey: String,
    val missions: List<DailyMission>
)

data class MatchRewardLine(
    val label: String,
    val amount: Int
)

data class MatchRewardBreakdown(
    val totalCoins: Int,
    val isHalfRateLocal: Boolean,
    val lines: List<MatchRewardLine> = emptyList(),
    val eligibilityReason: String? = null
)

data class EconomyUiState(
    val coinBalance: Int = DEFAULT_STARTING_COINS,
    val unlockedSkins: Set<String> = DEFAULT_UNLOCKED_SKINS,
    val dailyMissions: List<DailyMission> = emptyList()
) {
    val claimableMissionCount: Int
        get() = dailyMissions.count { it.isReadyToClaim }

    companion object {
        const val DEFAULT_STARTING_COINS = 150
        val DEFAULT_UNLOCKED_SKINS = setOf("classic")
    }
}
