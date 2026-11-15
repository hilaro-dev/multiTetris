package com.multitetris.economy

import com.multitetris.game.GameMode
import com.multitetris.game.GameState

/**
 * Computes end-of-match coin payouts for Versus and Co-op modes.
 */
object MatchRewardCalculator {

    private const val MIN_MATCH_DURATION_MILLIS = 15_000L

    fun calculate(
        gameState: GameState,
        localPlayerId: Int,
        isLocalTest: Boolean,
        matchDurationMillis: Long
    ): MatchRewardBreakdown {
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

        return if (gameState.mode == GameMode.VERSUS) {
            val isWinner = gameState.winnerPlayerId == localPlayerId
            val rawPlacement = if (isWinner) 60 else 25
            val placementCoins = if (isLocalTest) rawPlacement / 2 else rawPlacement
            val placementLabel = if (isWinner) "Victory" else "Runner-up"

            val playerLines = gameState.playerLines[localPlayerId] ?: 0
            val rawLineBonus = playerLines * 5
            val lineBonusCoins = if (isLocalTest) rawLineBonus / 2 else rawLineBonus

            val breakdownLines = buildList {
                add(MatchRewardLine(label = placementLabel, amount = placementCoins))
                if (lineBonusCoins > 0) {
                    add(MatchRewardLine(label = "Line Bonus ($playerLines)", amount = lineBonusCoins))
                }
            }
            MatchRewardBreakdown(
                totalCoins = placementCoins + lineBonusCoins,
                isHalfRateLocal = isLocalTest,
                lines = breakdownLines
            )
        } else {
            val rawBase = 20
            val baseCoins = if (isLocalTest) rawBase / 2 else rawBase

            val bonusBundles = gameState.sharedLines / 3
            val rawLineBonus = bonusBundles * 10
            val lineBonusCoins = if (isLocalTest) rawLineBonus / 2 else rawLineBonus

            val breakdownLines = buildList {
                add(MatchRewardLine(label = "Co-op Base", amount = baseCoins))
                if (lineBonusCoins > 0) {
                    add(MatchRewardLine(label = "Line Bonus (${gameState.sharedLines}L)", amount = lineBonusCoins))
                }
            }
            MatchRewardBreakdown(
                totalCoins = baseCoins + lineBonusCoins,
                isHalfRateLocal = isLocalTest,
                lines = breakdownLines
            )
        }
    }
}
