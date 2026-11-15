package com.multitetris.ui.skin

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.multitetris.game.TetrominoType
import kotlin.math.PI
import kotlin.math.sin

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
        isPausedOnPiece: Boolean = false,
        animProgress: Float = 0f
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

private fun DrawScope.drawGhostOutline(
    topLeft: Offset,
    size: Size,
    color: Color,
    cornerRadius: CornerRadius = CornerRadius.Zero
) {
    if (cornerRadius == CornerRadius.Zero) {
        drawRect(
            color = color.copy(alpha = 0.12f),
            topLeft = topLeft,
            size = size
        )
        drawRect(
            color = color.copy(alpha = 0.68f),
            topLeft = topLeft,
            size = size,
            style = Stroke(width = (size.minDimension * 0.08f).coerceAtLeast(1.5f))
        )
    } else {
        drawRoundRect(
            color = color.copy(alpha = 0.10f),
            topLeft = topLeft,
            size = size,
            cornerRadius = cornerRadius
        )
        drawRoundRect(
            color = color.copy(alpha = 0.72f),
            topLeft = topLeft,
            size = size,
            cornerRadius = cornerRadius,
            style = Stroke(width = (size.minDimension * 0.08f).coerceAtLeast(1.5f))
        )
    }
}

private fun DrawScope.drawPausedAndOwnerMarkers(
    blockTopLeft: Offset,
    blockSize: Size,
    playerId: Int,
    isPausedOnPiece: Boolean,
    cornerRadius: CornerRadius = CornerRadius.Zero
) {
    val dotRadius = (blockSize.minDimension * 0.11f).coerceAtLeast(2f)
    val offset = (blockSize.minDimension * 0.18f).coerceAtLeast(3f)
    drawCircle(
        color = PlayerPalette.accentFor(playerId),
        radius = dotRadius,
        center = Offset(
            blockTopLeft.x + blockSize.width - offset,
            blockTopLeft.y + offset
        )
    )

    if (isPausedOnPiece) {
        if (cornerRadius == CornerRadius.Zero) {
            drawRect(
                color = PlayerPalette.SabotageGold,
                topLeft = blockTopLeft,
                size = blockSize,
                style = Stroke(width = (blockSize.minDimension * 0.11f).coerceAtLeast(2f))
            )
        } else {
            drawRoundRect(
                color = PlayerPalette.SabotageGold,
                topLeft = blockTopLeft,
                size = blockSize,
                cornerRadius = cornerRadius,
                style = Stroke(width = (blockSize.minDimension * 0.11f).coerceAtLeast(2f))
            )
        }
    }
}

private fun classicColorFor(type: TetrominoType): Color = when (type) {
    TetrominoType.I -> Color(0xFF00D8E8)
    TetrominoType.O -> Color(0xFFF5D000)
    TetrominoType.T -> Color(0xFF9C42F4)
    TetrominoType.S -> Color(0xFF2FE066)
    TetrominoType.Z -> Color(0xFFFF3B4E)
    TetrominoType.J -> Color(0xFF2E6BFF)
    TetrominoType.L -> Color(0xFFFF8C1A)
}

/**
 * 1. Classic: Retro arcade 3D beveled Tetromino blocks with crisp highlights.
 */
class ClassicSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Classic"
    override val description: String = "Retro arcade beveled blocks"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animProgress: Float
    ) {
        val baseColor = classicColorFor(tetrominoType)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )

        if (isGhost) {
            drawGhostOutline(blockTopLeft, blockSize, baseColor)
            return
        }

        drawRect(color = baseColor, topLeft = blockTopLeft, size = blockSize)

        val bevel = (blockSize.minDimension * 0.16f).coerceAtLeast(2f)
        val lightPath = Path().apply {
            moveTo(blockTopLeft.x, blockTopLeft.y + blockSize.height)
            lineTo(blockTopLeft.x, blockTopLeft.y)
            lineTo(blockTopLeft.x + blockSize.width, blockTopLeft.y)
            lineTo(blockTopLeft.x + blockSize.width - bevel, blockTopLeft.y + bevel)
            lineTo(blockTopLeft.x + bevel, blockTopLeft.y + bevel)
            lineTo(blockTopLeft.x + bevel, blockTopLeft.y + blockSize.height - bevel)
            close()
        }
        drawPath(path = lightPath, color = Color.White.copy(alpha = 0.36f))

        val darkPath = Path().apply {
            moveTo(blockTopLeft.x + blockSize.width, blockTopLeft.y)
            lineTo(blockTopLeft.x + blockSize.width, blockTopLeft.y + blockSize.height)
            lineTo(blockTopLeft.x, blockTopLeft.y + blockSize.height)
            lineTo(blockTopLeft.x + bevel, blockTopLeft.y + blockSize.height - bevel)
            lineTo(blockTopLeft.x + blockSize.width - bevel, blockTopLeft.y + blockSize.height - bevel)
            lineTo(blockTopLeft.x + blockSize.width - bevel, blockTopLeft.y + bevel)
            close()
        }
        drawPath(path = darkPath, color = Color.Black.copy(alpha = 0.36f))

        drawRect(
            color = Color(0xFF0A0E17).copy(alpha = 0.85f),
            topLeft = blockTopLeft,
            size = blockSize,
            style = Stroke(width = 1.2f)
        )

        drawPausedAndOwnerMarkers(blockTopLeft, blockSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "classic"
    }
}

/**
 * 2. Neon Cyber: Dark translucent cores with glowing cyan/magenta neon borders.
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
        isPausedOnPiece: Boolean,
        animProgress: Float
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
            drawGhostOutline(blockTopLeft, blockSize, neonColor, corner)
            return
        }

        val pulse = 0.18f + 0.08f * sin(animProgress * 2f * PI.toFloat())
        drawRoundRect(
            color = neonColor.copy(alpha = pulse),
            topLeft = Offset(blockTopLeft.x - 1.5f, blockTopLeft.y - 1.5f),
            size = Size(blockSize.width + 3f, blockSize.height + 3f),
            cornerRadius = corner
        )

        drawRoundRect(
            color = Color(0xFF0B1221).copy(alpha = 0.92f),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(neonColor.copy(alpha = 0.34f), Color.Transparent),
                center = Offset(
                    blockTopLeft.x + blockSize.width / 2f,
                    blockTopLeft.y + blockSize.height / 2f
                ),
                radius = blockSize.maxDimension * 0.75f
            ),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        drawRoundRect(
            color = neonColor,
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = (size.minDimension * 0.09f).coerceAtLeast(1.8f))
        )

        val innerInset = blockSize.minDimension * 0.26f
        if (blockSize.width > innerInset * 2f && blockSize.height > innerInset * 2f) {
            drawRoundRect(
                color = playerAccent.copy(alpha = 0.6f),
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

/**
 * 3. Golden Flex: Metallic gold gradient with diagonal specular shine animation.
 */
class GoldenFlexSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Golden Flex"
    override val description: String = "24K metallic shine sweep"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animProgress: Float
    ) {
        val goldPrimary = Color(0xFFFFD700)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )
        val corner = CornerRadius(size.minDimension * 0.12f, size.minDimension * 0.12f)

        if (isGhost) {
            drawGhostOutline(blockTopLeft, blockSize, goldPrimary, corner)
            return
        }

        // Metallic gold base gradient
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFF4B8),
                    Color(0xFFFFD54F),
                    Color(0xFFD49B00),
                    Color(0xFF8C5E00)
                ),
                start = blockTopLeft,
                end = Offset(blockTopLeft.x + blockSize.width, blockTopLeft.y + blockSize.height)
            ),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        // Animated diagonal specular shine band clipped inside block
        clipRect(
            left = blockTopLeft.x,
            top = blockTopLeft.y,
            right = blockTopLeft.x + blockSize.width,
            bottom = blockTopLeft.y + blockSize.height
        ) {
            val sweepSpan = blockSize.width * 2.4f
            val bandCenter = -blockSize.width * 0.6f + animProgress * sweepSpan
            val bandWidth = (blockSize.width * 0.42f).coerceAtLeast(4f)
            val shinePath = Path().apply {
                moveTo(blockTopLeft.x + bandCenter, blockTopLeft.y)
                lineTo(blockTopLeft.x + bandCenter + bandWidth, blockTopLeft.y)
                lineTo(blockTopLeft.x + bandCenter + bandWidth - blockSize.width * 0.55f, blockTopLeft.y + blockSize.height)
                lineTo(blockTopLeft.x + bandCenter - blockSize.width * 0.55f, blockTopLeft.y + blockSize.height)
                close()
            }
            drawPath(
                path = shinePath,
                color = Color.White.copy(alpha = 0.45f)
            )
        }

        // Inner engraved border
        val inner = (blockSize.minDimension * 0.18f).coerceAtLeast(2f)
        if (blockSize.width > inner * 2f && blockSize.height > inner * 2f) {
            drawRoundRect(
                color = Color(0xFFFFF8D6).copy(alpha = 0.55f),
                topLeft = Offset(blockTopLeft.x + inner, blockTopLeft.y + inner),
                size = Size(blockSize.width - inner * 2f, blockSize.height - inner * 2f),
                cornerRadius = CornerRadius(2f, 2f),
                style = Stroke(width = 1.1f)
            )
        }

        // Crisp metallic gold rim
        drawRoundRect(
            color = Color(0xFFFFF3B0),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = (size.minDimension * 0.08f).coerceAtLeast(1.4f))
        )

        drawPausedAndOwnerMarkers(blockTopLeft, blockSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "golden_flex"
    }
}

/**
 * 4. Obsidian Magma: Deep volcanic black tiles with glowing molten orange cracks.
 */
class ObsidianMagmaSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Obsidian Magma"
    override val description: String = "Volcanic rock & molten cracks"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animProgress: Float
    ) {
        val magmaOrange = Color(0xFFFF5500)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )
        val corner = CornerRadius(size.minDimension * 0.10f, size.minDimension * 0.10f)

        if (isGhost) {
            drawGhostOutline(blockTopLeft, blockSize, magmaOrange, corner)
            return
        }

        val emberPulse = 0.72f + 0.28f * sin(animProgress * 2f * PI.toFloat())

        // Outer ember glow
        drawRoundRect(
            color = Color(0xFFFF3D00).copy(alpha = 0.22f * emberPulse),
            topLeft = Offset(blockTopLeft.x - 1f, blockTopLeft.y - 1f),
            size = Size(blockSize.width + 2f, blockSize.height + 2f),
            cornerRadius = corner
        )

        // Volcanic obsidian core
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF23191D), Color(0xFF110B0E)),
                start = blockTopLeft,
                end = Offset(blockTopLeft.x + blockSize.width, blockTopLeft.y + blockSize.height)
            ),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        // Molten crack lines
        val w = blockSize.width
        val h = blockSize.height
        val crackPath = Path().apply {
            moveTo(blockTopLeft.x + w * 0.15f, blockTopLeft.y + h * 0.22f)
            lineTo(blockTopLeft.x + w * 0.48f, blockTopLeft.y + h * 0.45f)
            lineTo(blockTopLeft.x + w * 0.35f, blockTopLeft.y + h * 0.78f)
            moveTo(blockTopLeft.x + w * 0.48f, blockTopLeft.y + h * 0.45f)
            lineTo(blockTopLeft.x + w * 0.84f, blockTopLeft.y + h * 0.62f)
            moveTo(blockTopLeft.x + w * 0.68f, blockTopLeft.y + h * 0.16f)
            lineTo(blockTopLeft.x + w * 0.48f, blockTopLeft.y + h * 0.45f)
        }
        drawPath(
            path = crackPath,
            color = Color(0xFFFF3D00).copy(alpha = 0.65f * emberPulse),
            style = Stroke(width = (blockSize.minDimension * 0.14f).coerceAtLeast(2.2f))
        )
        drawPath(
            path = crackPath,
            color = Color(0xFFFFD180).copy(alpha = 0.92f * emberPulse),
            style = Stroke(width = (blockSize.minDimension * 0.06f).coerceAtLeast(1.1f))
        )

        // Scorched lava rim
        drawRoundRect(
            color = magmaOrange.copy(alpha = 0.85f),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = (size.minDimension * 0.07f).coerceAtLeast(1.3f))
        )

        drawPausedAndOwnerMarkers(blockTopLeft, blockSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "obsidian_magma"
    }
}

/**
 * 5. Hologram Glitch: Iridescent prismatic gradient with subtle scanline stripes.
 */
class HologramGlitchSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Hologram Glitch"
    override val description: String = "Prismatic scanline projection"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animProgress: Float
    ) {
        val holoCyan = Color(0xFF40F9FF)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )
        val corner = CornerRadius(size.minDimension * 0.14f, size.minDimension * 0.14f)

        if (isGhost) {
            drawGhostOutline(blockTopLeft, blockSize, holoCyan, corner)
            return
        }

        val shift = sin(animProgress * 2f * PI.toFloat()) * 0.25f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF00F5D4).copy(alpha = 0.88f),
                    Color(0xFF00BBF9).copy(alpha = 0.88f),
                    Color(0xFF9B5DE5).copy(alpha = 0.88f),
                    Color(0xFFF15BB5).copy(alpha = 0.88f)
                ),
                start = Offset(blockTopLeft.x + blockSize.width * shift, blockTopLeft.y),
                end = Offset(blockTopLeft.x + blockSize.width, blockTopLeft.y + blockSize.height)
            ),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner
        )

        // Subtle horizontal scanlines
        clipRect(
            left = blockTopLeft.x,
            top = blockTopLeft.y,
            right = blockTopLeft.x + blockSize.width,
            bottom = blockTopLeft.y + blockSize.height
        ) {
            val step = (blockSize.height / 5f).coerceAtLeast(2.5f)
            val yOffset = (animProgress * step) % step
            var y = blockTopLeft.y + yOffset
            while (y < blockTopLeft.y + blockSize.height) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.28f),
                    start = Offset(blockTopLeft.x, y),
                    end = Offset(blockTopLeft.x + blockSize.width, y),
                    strokeWidth = 1.2f
                )
                y += step
            }
        }

        // Glitch accent edge
        drawRoundRect(
            color = Color(0xFFE0FFFF).copy(alpha = 0.88f),
            topLeft = blockTopLeft,
            size = blockSize,
            cornerRadius = corner,
            style = Stroke(width = (size.minDimension * 0.08f).coerceAtLeast(1.4f))
        )

        drawPausedAndOwnerMarkers(blockTopLeft, blockSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "hologram_glitch"
    }
}

/**
 * 6. Pixel Crystal: Sharp faceted gem/diamond inner geometry.
 */
class PixelCrystalSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Pixel Crystal"
    override val description: String = "Sharp faceted gem geometry"

    override fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animProgress: Float
    ) {
        val gemBase = crystalColorFor(tetrominoType)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val blockTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val blockSize = Size(
            (size.width - inset * 2f).coerceAtLeast(1f),
            (size.height - inset * 2f).coerceAtLeast(1f)
        )

        if (isGhost) {
            drawGhostOutline(blockTopLeft, blockSize, gemBase)
            return
        }

        val x0 = blockTopLeft.x
        val y0 = blockTopLeft.y
        val x1 = x0 + blockSize.width
        val y1 = y0 + blockSize.height
        val cx = x0 + blockSize.width * 0.5f
        val cy = y0 + blockSize.height * 0.5f

        // Base jewel fill
        drawRect(color = gemBase, topLeft = blockTopLeft, size = blockSize)

        // Top facet (bright specular)
        val topFacet = Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y0)
            lineTo(cx, cy)
            close()
        }
        drawPath(topFacet, color = Color.White.copy(alpha = 0.42f))

        // Left facet (medium highlight)
        val leftFacet = Path().apply {
            moveTo(x0, y0)
            lineTo(cx, cy)
            lineTo(x0, y1)
            close()
        }
        drawPath(leftFacet, color = Color.White.copy(alpha = 0.18f))

        // Right facet (medium shadow)
        val rightFacet = Path().apply {
            moveTo(x1, y0)
            lineTo(x1, y1)
            lineTo(cx, cy)
            close()
        }
        drawPath(rightFacet, color = Color.Black.copy(alpha = 0.22f))

        // Bottom facet (deep shadow)
        val bottomFacet = Path().apply {
            moveTo(x0, y1)
            lineTo(cx, cy)
            lineTo(x1, y1)
            close()
        }
        drawPath(bottomFacet, color = Color.Black.copy(alpha = 0.40f))

        // Inner diamond table
        val tableW = blockSize.width * 0.46f
        val tableH = blockSize.height * 0.46f
        val diamondPath = Path().apply {
            moveTo(cx, cy - tableH * 0.5f)
            lineTo(cx + tableW * 0.5f, cy)
            lineTo(cx, cy + tableH * 0.5f)
            lineTo(cx - tableW * 0.5f, cy)
            close()
        }
        drawPath(diamondPath, color = Color.White.copy(alpha = 0.30f))
        drawPath(
            diamondPath,
            color = Color.White.copy(alpha = 0.72f),
            style = Stroke(width = 1.0f)
        )

        // Crisp crystal edge
        drawRect(
            color = Color.White.copy(alpha = 0.65f),
            topLeft = blockTopLeft,
            size = blockSize,
            style = Stroke(width = 1.2f)
        )

        drawPausedAndOwnerMarkers(blockTopLeft, blockSize, playerId, isPausedOnPiece)
    }

    private fun crystalColorFor(type: TetrominoType): Color = when (type) {
        TetrominoType.I -> Color(0xFF00E5FF)
        TetrominoType.O -> Color(0xFFFFD740)
        TetrominoType.T -> Color(0xFFB388FF)
        TetrominoType.S -> Color(0xFF69F0AE)
        TetrominoType.Z -> Color(0xFFFF5252)
        TetrominoType.J -> Color(0xFF448AFF)
        TetrominoType.L -> Color(0xFFFFAB40)
    }

    companion object {
        const val SKIN_ID = "pixel_crystal"
    }
}

object SkinRegistry {
    private val classic = ClassicSkinRenderer()
    private val neonCyber = NeonCyberSkinRenderer()
    private val goldenFlex = GoldenFlexSkinRenderer()
    private val obsidianMagma = ObsidianMagmaSkinRenderer()
    private val hologramGlitch = HologramGlitchSkinRenderer()
    private val pixelCrystal = PixelCrystalSkinRenderer()

    val availableSkins: List<SkinRenderer> = listOf(
        classic,
        neonCyber,
        goldenFlex,
        obsidianMagma,
        hologramGlitch,
        pixelCrystal
    )

    fun resolve(skinId: String): SkinRenderer {
        return when (skinId) {
            NeonCyberSkinRenderer.SKIN_ID -> neonCyber
            GoldenFlexSkinRenderer.SKIN_ID -> goldenFlex
            ObsidianMagmaSkinRenderer.SKIN_ID -> obsidianMagma
            HologramGlitchSkinRenderer.SKIN_ID -> hologramGlitch
            PixelCrystalSkinRenderer.SKIN_ID -> pixelCrystal
            ClassicSkinRenderer.SKIN_ID -> classic
            else -> classic
        }
    }
}
