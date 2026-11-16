package com.multitetris.economy

import com.multitetris.game.GameMode
import com.multitetris.game.GameState
import com.multitetris.game.TeamColor
import com.multitetris.game.VersusSubMode

/**
 * Computes end-of-match coin payouts for Co-op, Versus FFA (2-4P), and 2v2 Teams.
 */
object MatchRewardCalculator {

    private const val MIN_MATCH_DURATION_MILLIS = 15_000L

    fun calculate(
        gameState: GameState,
        localPlayerId: Int,
        isLocalTest: Boolean,
        matchDurationMillis: Long
    ): MatchRewardBreakdown {
        if (gameState.isPlayerDisconnected(localPlayerId)) {
            return MatchRewardBreakdown(
                totalCoins = 0,
                isHalfRateLocal = isLocalTest,
                lines = emptyList(),
                eligibilityReason = "Disconnected"
            )
        }

        val totalLinesInMatch = if (gameState.mode == GameMode.CO_OP) {
            gameState.sharedLines
        } else {
            maxOf(gameState.sharedLines, gameState.playerLines.values.sum())
        }

        val isEligible = totalLinesInMatch >= 1 || matchDurationMillis >= MIN_MATCH_DURATION_MILLIS
        if (!isEligible) {
            return MatchRewardBreakdown(
                totalCoins = 0,
                isHalfRateLocal = isLocalTest,
                lines = emptyList(),
                eligibilityReason = "Clear 1+ line or play 15s+ to earn coins"
            )
        }

        return if (gameState.mode != GameMode.CO_OP) {
            val (rawPlacement, placementLabel) = when (gameState.versusSubMode) {
                VersusSubMode.TEAMS_2V2 -> {
                    val myTeam = gameState.playerTeams[localPlayerId] ?: TeamColor.NONE
                    val isWinningTeam = gameState.winningTeam != null && myTeam == gameState.winningTeam
                    if (isWinningTeam) {
                        70 to "2v2 Team Victory"
                    } else {
                        30 to "2v2 Match Completed"
                    }
                }
                VersusSubMode.FREE_FOR_ALL -> {
                    val isWinner = gameState.winnerPlayerId == localPlayerId
                    if (gameState.config.playerCount >= 3) {
                        val placement = gameState.playerPlacements[localPlayerId]
                            ?: if (isWinner) 1 else 2
                        when (placement) {
                            1 -> 75 to "1st Place Victory (${gameState.config.playerCount}P)"
                            2 -> 40 to "2nd Place (${gameState.config.playerCount}P)"
                            3 -> 20 to "3rd Place (${gameState.config.playerCount}P)"
                            else -> 20 to "${placement}th Place (${gameState.config.playerCount}P)"
                        }
                    } else {
                        if (isWinner) 60 to "Victory" else 25 to "Runner-up"
                    }
                }
            }
            val placementCoins = if (isLocalTest) rawPlacement / 2 else rawPlacement

            val playerLines = gameState.playerLines[localPlayerId] ?: 0
            val rawLineBonus = playerLines * 5
            val lineBonusCoins = if (isLocalTest) rawLineBonus / 2 else rawLineBonus

            val baseTotal = placementCoins + lineBonusCoins
            val mysteryBonus = if (gameState.mysteryEnabled && baseTotal > 0) {
                kotlin.math.round(baseTotal * 0.25).toInt().coerceAtLeast(1)
            } else {
                0
            }

            val breakdownLines = buildList {
                add(MatchRewardLine(label = placementLabel, amount = placementCoins))
                if (lineBonusCoins > 0) {
                    add(MatchRewardLine(label = "Line Bonus ($playerLines)", amount = lineBonusCoins))
                }
                if (mysteryBonus > 0) {
                    add(MatchRewardLine(label = "Mystery Bonus (1.25x)", amount = mysteryBonus))
                }
            }
            MatchRewardBreakdown(
                totalCoins = baseTotal + mysteryBonus,
                isHalfRateLocal = isLocalTest,
                lines = breakdownLines
            )
        } else {
            val rawBase = 20
            val baseCoins = if (isLocalTest) rawBase / 2 else rawBase

            val bonusBundles = gameState.sharedLines / 3
            val rawLineBonus = bonusBundles * 10
            val lineBonusCoins = if (isLocalTest) rawLineBonus / 2 else rawLineBonus
            val baseTotal = baseCoins + lineBonusCoins
            val mysteryBonus = if (gameState.mysteryEnabled && baseTotal > 0) {
                kotlin.math.round(baseTotal * 0.25).toInt().coerceAtLeast(1)
            } else {
                0
            }

            val breakdownLines = buildList {
                add(MatchRewardLine(label = "Co-op Base", amount = baseCoins))
                if (lineBonusCoins > 0) {
                    add(MatchRewardLine(label = "Line Bonus (${gameState.sharedLines}L)", amount = lineBonusCoins))
                }
                if (mysteryBonus > 0) {
                    add(MatchRewardLine(label = "Mystery Bonus (1.25x)", amount = mysteryBonus))
                }
            }
            MatchRewardBreakdown(
                totalCoins = baseTotal + mysteryBonus,
                isHalfRateLocal = isLocalTest,
                lines = breakdownLines
            )
        }
    }
}
