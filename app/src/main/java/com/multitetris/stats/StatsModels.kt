package com.multitetris.stats

import com.multitetris.game.GameMode
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode
import kotlinx.serialization.Serializable

@Serializable
data class ModeCareerStats(
    val totalMatches: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    val totalLinesCleared: Int = 0,
    val bestScore: Int = 0,
    val bestLines: Int = 0
) {
    val winRatePercent: Int
        get() {
            val decided = wins + losses
            return if (decided <= 0) 0 else ((wins * 100f) / decided).toInt()
        }
}

@Serializable
data class FriendMatchStats(
    val versusWinsAgainst: Int = 0,
    val versusLossesAgainst: Int = 0,
    val coopMatchesTogether: Int = 0,
    val bestCoopLinesTogether: Int = 0
) {
    val hasAnyMatches: Boolean
        get() = versusWinsAgainst > 0 || versusLossesAgainst > 0 || coopMatchesTogether > 0

    fun formatRivalrySubtitle(): String {
        return if (hasAnyMatches) {
            "Versus: ${versusWinsAgainst}W - ${versusLossesAgainst}L  •  Best Co-op: $bestCoopLinesTogether Lines"
        } else {
            "No matches played yet"
        }
    }
}

@Serializable
data class CareerStatsSnapshot(
    val totalMatches: Int = 0,
    val versusWins: Int = 0,
    val versusLosses: Int = 0,
    val totalLinesCleared: Int = 0,
    val highestSoloOrMatchScore: Int = 0,
    val bestCoopLines: Int = 0,
    val bestCoopScore: Int = 0,
    val modeStats: Map<String, ModeCareerStats> = emptyMap(),
    val friendRivalry: Map<String, FriendMatchStats> = emptyMap()
) {
    val versusWinRatePercent: Int
        get() {
            val decided = versusWins + versusLosses
            return if (decided <= 0) 0 else ((versusWins * 100f) / decided).toInt()
        }
}

data class MatchParticipantStat(
    val playerId: Int,
    val uid: String,
    val team: TeamColor,
    val placement: Int,
    val score: Int,
    val linesCleared: Int,
    val isDisconnected: Boolean
)

data class MatchStatRecord(
    val mode: GameMode,
    val versusSubMode: VersusSubMode,
    val modeKey: String = defaultModeKey(mode, versusSubMode),
    val localPlayerId: Int,
    val localUid: String,
    val didLocalPlayerWin: Boolean,
    val winningTeam: TeamColor?,
    val sharedScore: Int,
    val sharedLines: Int,
    val matchDurationMs: Long,
    val participants: List<MatchParticipantStat>,
    val friendUids: Set<String>
) {
    companion object {
        fun defaultModeKey(mode: GameMode, versusSubMode: VersusSubMode): String = when (mode) {
            GameMode.CO_OP -> "CO_OP"
            GameMode.VERSUS -> if (versusSubMode == VersusSubMode.TEAMS_2V2) "VERSUS_TEAMS" else "VERSUS_FFA"
        }
    }
}

object StatsReducer {
    private const val MIN_DISCONNECT_RIVALRY_DURATION_MS = 10_000L

    fun recordMatch(
        current: CareerStatsSnapshot,
        record: MatchStatRecord
    ): Pair<CareerStatsSnapshot, Boolean> {
        val localParticipant = record.participants.firstOrNull { it.playerId == record.localPlayerId }
        val localScore = localParticipant?.score ?: 0
        val localLines = localParticipant?.linesCleared ?: 0
        val localTeam = localParticipant?.team ?: TeamColor.NONE

        val matchScoreForBest = if (record.mode == GameMode.CO_OP) record.sharedScore else localScore
        val isNewCoopRecord = record.mode == GameMode.CO_OP &&
            record.sharedScore > 0 &&
            record.sharedScore > current.bestCoopScore

        val updatedModeStats = current.modeStats.toMutableMap()
        val existingMode = updatedModeStats[record.modeKey] ?: ModeCareerStats()
        val modeLinesDelta = if (record.mode == GameMode.CO_OP) record.sharedLines else localLines
        updatedModeStats[record.modeKey] = existingMode.copy(
            totalMatches = existingMode.totalMatches + 1,
            wins = existingMode.wins + if (record.mode == GameMode.VERSUS && record.didLocalPlayerWin) 1 else 0,
            losses = existingMode.losses + if (record.mode == GameMode.VERSUS && !record.didLocalPlayerWin) 1 else 0,
            totalLinesCleared = existingMode.totalLinesCleared + localLines,
            bestScore = maxOf(existingMode.bestScore, matchScoreForBest),
            bestLines = maxOf(existingMode.bestLines, modeLinesDelta)
        )

        val updatedRivalry = current.friendRivalry.toMutableMap()
        for (participant in record.participants) {
            val friendUid = participant.uid
            if (friendUid.isBlank() ||
                friendUid == record.localUid ||
                friendUid !in record.friendUids
            ) {
                continue
            }
            val currentFriend = updatedRivalry[friendUid] ?: FriendMatchStats()
            if (record.mode == GameMode.CO_OP) {
                updatedRivalry[friendUid] = currentFriend.copy(
                    coopMatchesTogether = currentFriend.coopMatchesTogether + 1,
                    bestCoopLinesTogether = maxOf(currentFriend.bestCoopLinesTogether, record.sharedLines)
                )
            } else {
                // Ignore disconnected opponents for win/loss credit only if match lasted < 10 seconds
                if (participant.isDisconnected && record.matchDurationMs < MIN_DISCONNECT_RIVALRY_DURATION_MS) {
                    continue
                }
                if (record.versusSubMode == VersusSubMode.TEAMS_2V2 &&
                    localTeam != TeamColor.NONE &&
                    participant.team == localTeam
                ) {
                    continue
                }
                val wonAgainstFriend = didWinHeadToHead(
                    didLocalPlayerWin = record.didLocalPlayerWin,
                    localPlacement = localParticipant?.placement ?: 0,
                    opponentPlacement = participant.placement,
                    opponentDisconnected = participant.isDisconnected
                )
                updatedRivalry[friendUid] = if (wonAgainstFriend) {
                    currentFriend.copy(versusWinsAgainst = currentFriend.versusWinsAgainst + 1)
                } else {
                    currentFriend.copy(versusLossesAgainst = currentFriend.versusLossesAgainst + 1)
                }
            }
        }

        val updated = current.copy(
            totalMatches = current.totalMatches + 1,
            versusWins = current.versusWins + if (record.mode == GameMode.VERSUS && record.didLocalPlayerWin) 1 else 0,
            versusLosses = current.versusLosses + if (record.mode == GameMode.VERSUS && !record.didLocalPlayerWin) 1 else 0,
            totalLinesCleared = current.totalLinesCleared + localLines,
            highestSoloOrMatchScore = maxOf(current.highestSoloOrMatchScore, matchScoreForBest),
            bestCoopLines = if (record.mode == GameMode.CO_OP) maxOf(current.bestCoopLines, record.sharedLines) else current.bestCoopLines,
            bestCoopScore = if (record.mode == GameMode.CO_OP) maxOf(current.bestCoopScore, record.sharedScore) else current.bestCoopScore,
            modeStats = updatedModeStats,
            friendRivalry = updatedRivalry
        )
        return updated to isNewCoopRecord
    }

    private fun didWinHeadToHead(
        didLocalPlayerWin: Boolean,
        localPlacement: Int,
        opponentPlacement: Int,
        opponentDisconnected: Boolean
    ): Boolean {
        if (didLocalPlayerWin) return true
        if (opponentDisconnected && localPlacement != 1) {
            return localPlacement == 0 || opponentPlacement == 0 || localPlacement < opponentPlacement
        }
        if (localPlacement > 0 && opponentPlacement > 0) {
            return localPlacement < opponentPlacement
        }
        return false
    }
}
