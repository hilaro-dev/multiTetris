package com.multitetris.economy

import com.multitetris.game.GameMode
import java.time.LocalDate

/**
 * Generates and updates the 3 daily missions keyed by [LocalDate.now().toString()].
 */
class DailyMissionManager(
    private val dateProvider: () -> String = { LocalDate.now().toString() }
) {
    fun ensureForToday(existing: DailyMissionsSnapshot?): DailyMissionsSnapshot {
        val today = dateProvider()
        if (existing != null && existing.dateKey == today && existing.missions.size == 3) {
            return existing
        }
        return DailyMissionsSnapshot(
            dateKey = today,
            missions = defaultMissions()
        )
    }

    fun recordLinesCleared(
        existing: DailyMissionsSnapshot?,
        linesCleared: Int
    ): DailyMissionsSnapshot {
        val current = ensureForToday(existing)
        if (linesCleared <= 0) return current
        val updated = current.missions.map { mission ->
            if (mission.id == MISSION_CLEAR_LINES && !mission.isClaimed) {
                mission.copy(progress = (mission.progress + linesCleared).coerceAtMost(mission.target))
            } else {
                mission
            }
        }
        return current.copy(missions = updated)
    }

    fun recordMatchCompleted(
        existing: DailyMissionsSnapshot?,
        mode: GameMode,
        isMultiplayer: Boolean,
        didLocalPlayerWin: Boolean
    ): DailyMissionsSnapshot {
        val current = ensureForToday(existing)
        val updated = current.missions.map { mission ->
            when {
                mission.id == MISSION_WIN_VERSUS &&
                    !mission.isClaimed &&
                    mode != GameMode.CO_OP &&
                    didLocalPlayerWin -> {
                    mission.copy(progress = (mission.progress + 1).coerceAtMost(mission.target))
                }

                mission.id == MISSION_COMPLETE_MULTIPLAYER &&
                    !mission.isClaimed &&
                    isMultiplayer -> {
                    mission.copy(progress = (mission.progress + 1).coerceAtMost(mission.target))
                }

                else -> mission
            }
        }
        return current.copy(missions = updated)
    }

    fun claimMission(
        existing: DailyMissionsSnapshot?,
        missionId: String
    ): Pair<DailyMissionsSnapshot, Int> {
        val current = ensureForToday(existing)
        var reward = 0
        val updated = current.missions.map { mission ->
            if (mission.id == missionId && mission.isReadyToClaim) {
                reward = mission.rewardCoins
                mission.copy(isClaimed = true)
            } else {
                mission
            }
        }
        return current.copy(missions = updated) to reward
    }

    companion object {
        const val MISSION_CLEAR_LINES = "clear_15_lines"
        const val MISSION_WIN_VERSUS = "win_2_versus"
        const val MISSION_COMPLETE_MULTIPLAYER = "complete_3_multiplayer"

        fun defaultMissions(): List<DailyMission> = listOf(
            DailyMission(
                id = MISSION_CLEAR_LINES,
                title = "Clear 15 total lines",
                target = 15,
                rewardCoins = 80
            ),
            DailyMission(
                id = MISSION_WIN_VERSUS,
                title = "Win 2 Versus matches",
                target = 2,
                rewardCoins = 100
            ),
            DailyMission(
                id = MISSION_COMPLETE_MULTIPLAYER,
                title = "Complete 3 multiplayer matches",
                target = 3,
                rewardCoins = 75
            )
        )
    }
}
