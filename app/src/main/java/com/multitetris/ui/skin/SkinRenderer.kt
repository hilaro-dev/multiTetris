package com.multitetris.ui.skin

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.multitetris.game.TetrominoType

/**
 * Renders individual Tetromino blocks and ghost previews based on a skinId.
 */
interface SkinRenderer {
    val skinId: String
    val displayName: String
    val description: String

    fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean = false
    )
}

object PlayerPalette {
    val Player1Accent = Color(0xFF00F0FF) // Electric Cyan
    val Player2Accent = Color(0xFFFF2A85) // Neon Magenta
    val SabotageGold = Color(0xFFFFD600)

    fun accentFor(playerId: Int): Color = when (playerId) {
        1 -> Player1Accent
        2 -> Player2Accent
        3 -> Color(0xFF39FF14)
        else -> Color(0xFFFFB800)
    }
}

/**
 * Classic arcade 3D beveled Tetromino blocks with crisp borders.
 */
class ClassicSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Classic"
    override val description: String = "3D beveled arcade blocks"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean
    ) {
        val baseColor = colorFor(tetrominoType)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )

        if (isGhost) {
            drawRect(
                color = baseColor.copy(alpha = 0.14f),
                topLeft = blockTopLeft,
                size = blockSize
            )
            drawRect(
                color = baseColor.copy(alpha = 0.65f),
                topLeft = blockTopLeft,
                size = blockSize,
                style = Stroke(width = (size.minDimension * 0.08f).coerceAtLeast(1.5f))
            )
            return
        }

        // Main fill
        drawRect(
            color = baseColor,
            topLeft = blockTopLeft,
            size = blockSize
        )

        val bevel = (blockSize.minDimension * 0.16f).coerceAtLeast(2f)

        // Top & left highlight bevel
        val lightPath = Path().apply {
            moveTo(blockTopLeft.x, blockTopLeft.y + blockSize.height)
            lineTo(blockTopLeft.x, blockTopLeft.y)
            lineTo(blockTopLeft.x + blockSize.width, blockTopLeft.y)
            lineTo(blockTopLeft.x + blockSize.width - bevel, blockTopLeft.y + bevel)
            lineTo(blockTopLeft.x + bevel, blockTopLeft.y + bevel)
            lineTo(blockTopLeft.x + bevel, blockTopLeft.y + blockSize.height - bevel)
            close()
        }
        drawPath(path = lightPath, color = Color.White.copy(alpha = 0.35f))

        // Bottom & right shadow bevel
        val darkPath = Path().apply {
            moveTo(blockTopLeft.x + blockSize.width, blockTopLeft.y)
            lineTo(blockTopLeft.x + blockSize.width, blockTopLeft.y + blockSize.height)
            lineTo(blockTopLeft.x, blockTopLeft.y + blockSize.height)
            lineTo(blockTopLeft.x + bevel, blockTopLeft.y + blockSize.height - bevel)
            lineTo(blockTopLeft.x + blockSize.width - bevel, blockTopLeft.y + blockSize.height - bevel)
            lineTo(blockTopLeft.x + blockSize.width - bevel, blockTopLeft.y + bevel)
            close()
        }
        drawPath(path = darkPath, color = Color.Black.copy(alpha = 0.35f))

        // Crisp outer border
        drawRect(
            color = Color(0xFF0A0E17).copy(alpha = 0.85f),
            topLeft = blockTopLeft,
            size = blockSize,
            style = Stroke(width = 1.2f)
        )

        // Subtle player ownership badge in top-right corner
        val dotRadius = (blockSize.minDimension * 0.11f).coerceAtLeast(2f)
        drawCircle(
            color = PlayerPalette.accentFor(playerId),
            radius = dotRadius,
            center = Offset(
                blockTopLeft.x + blockSize.width - bevel * 0.9f,
                blockTopLeft.y + bevel * 0.9f
            )
        )

        if (isPausedOnPiece) {
            drawRect(
                color = PlayerPalette.SabotageGold,
                topLeft = blockTopLeft,
                size = blockSize,
                style = Stroke(width = (size.minDimension * 0.1f).coerceAtLeast(2f))
            )
        }
    }

    private fun colorFor(type: TetrominoType): Color = when (type) {
        TetrominoType.I -> Color(0xFF00D8E8)
        TetrominoType.O -> Color(0xFFF5D000)
        TetrominoType.T -> Color(0xFF9C42F4)
        TetrominoType.S -> Color(0xFF2FE066)
        TetrominoType.Z -> Color(0xFFFF3B4E)
        TetrominoType.J -> Color(0xFF2E6BFF)
        TetrominoType.L -> Color(0xFFFF8C1A)
    }

    companion object {
        const val SKIN_ID = "classic"
    }
}

/**
 * Cyberpunk glowing neon outline skin with dark glass cores and glowing traces.
 */
class NeonCyberSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Neon Cyber"
    override val description: String = "Dark core with laser glow"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean
    ) {
        val neonColor = neonColorFor(tetrominoType)
        val playerAccent = PlayerPalette.accentFor(playerId)
        val inset = (size.minDimension * 0.06f).coerceAtLeast(1.2f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )
        val corner = CornerRadius(size.minDimension * 0.16f, size.minDimension * 0.16f)

        if (isGhost) {
            drawRoundRect(
                color = neonColor.copy(alpha = 0.08f),
                topLeft = blockTopLeft,
                size = blockSize,
                cornerRadius = corner
            )
            drawRoundRect(
                color = neonColor.copy(alpha = 0.72f),
                topLeft = blockTopLeft,
                size = blockSize,
                cornerRadius = corner,
                style = Stroke(width = (size.minDimension * 0.07f).coerceAtLeast(1.5f))
            )
            return
        }

        // Soft outer bloom
        drawRoundRect(
            color = neonColor.copy(alpha = 0.22f),
            topLeft = Offset(blockTopLeft.x - 1.5f, blockTopLeft.y - 1.5f),
            size = Size(blockSize.width + 3f, blockSize.height + 3f),
            cornerRadius = corner
        )

        // Dark cybernetic core
        drawRoundRect(
            color = Color(0xFF0B1221),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        // Tinted inner energy gradient fill
        drawRoundRect(
            color = neonColor.copy(alpha = 0.28f),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        // Neon border
        drawRoundRect(
            color = neonColor,
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = (size.minDimension * 0.09f).coerceAtLeast(1.8f))
        )

        // Inner cyber circuit box
        val innerInset = blockSize.minDimension * 0.26f
        if (blockSize.width > innerInset * 2f && blockSize.height > innerInset * 2f) {
            drawRoundRect(
                color = playerAccent.copy(alpha = 0.55f),
                topLeft = Offset(blockTopLeft.x + innerInset, blockTopLeft.y + innerInset),
                size = Size(blockSize.width - innerInset * 2f, blockSize.height - innerInset * 2f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = 1.2f)
            )
        }

        if (isPausedOnPiece) {
            drawRoundRect(
                color = PlayerPalette.SabotageGold,
                topLeft = blockTopLeft,
                size = blockSize,
                cornerRadius = corner,
                style = Stroke(width = (size.minDimension * 0.11f).coerceAtLeast(2.2f))
            )
        }
    }

    private fun neonColorFor(type: TetrominoType): Color = when (type) {
        TetrominoType.I -> Color(0xFF00F6FF)
        TetrominoType.O -> Color(0xFFFFEA00)
        TetrominoType.T -> Color(0xFFD500F9)
        TetrominoType.S -> Color(0xFF00FF87)
        TetrominoType.Z -> Color(0xFFFF1744)
        TetrominoType.J -> Color(0xFF2979FF)
        TetrominoType.L -> Color(0xFFFF6D00)
    }

    companion object {
        const val SKIN_ID = "neon_cyber"
    }
}

object SkinRegistry {
    private val classic = ClassicSkinRenderer()
    private val neonCyber = NeonCyberSkinRenderer()

    val availableSkins: List<SkinRenderer> = listOf(classic, neonCyber)

    fun resolve(skinId: String): SkinRenderer {
        return when (skinId) {
            NeonCyberSkinRenderer.SKIN_ID -> neonCyber
            ClassicSkinRenderer.SKIN_ID -> classic
            else -> classic
        }
    }
}
