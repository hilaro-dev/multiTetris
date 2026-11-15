package com.multitetris.ui.skin

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.multitetris.game.TetrominoType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =============================================================================
// BASIC TIER SKINS (2..5 — Clean shaders, zero particles)
// =============================================================================

/**
 * 2. Minimal Matte: Clean modern flat matte tiles with crisp dark borders.
 */
class MinimalMatteSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Minimal Matte"
    override val description: String = "Clean modern flat matte tiles"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val base = when (tetrominoType) {
            TetrominoType.I -> Color(0xFF4CC9F0)
            TetrominoType.O -> Color(0xFFF9C74F)
            TetrominoType.T -> Color(0xFF9D4EDD)
            TetrominoType.S -> Color(0xFF43AA8B)
            TetrominoType.Z -> Color(0xFFF94144)
            TetrominoType.J -> Color(0xFF4361EE)
            TetrominoType.L -> Color(0xFFF8961E)
        }
        val inset = (size.minDimension * 0.07f).coerceAtLeast(1.2f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.14f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, base, corner)
            return
        }

        drawRoundRect(color = base, topLeft = bTopLeft, size = bSize, cornerRadius = corner)
        drawRoundRect(
            color = Color(0xFF0A0F1A),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner,
            style = Stroke(width = (bSize.minDimension * 0.09f).coerceAtLeast(1.5f))
        )
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "minimal_matte"
    }
}

/**
 * 3. Pastel Dream: Soft rounded pastel gradients with creamy inner highlights.
 */
class PastelDreamSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Pastel Dream"
    override val description: String = "Soft rounded pastel gradients"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val topColor = when (tetrominoType) {
            TetrominoType.I -> Color(0xFFA8E6CF)
            TetrominoType.O -> Color(0xFFFFF3B0)
            TetrominoType.T -> Color(0xFFDCC6FF)
            TetrominoType.S -> Color(0xFFB8F2E6)
            TetrominoType.Z -> Color(0xFFFFB5C2)
            TetrominoType.J -> Color(0xFFAEC6FF)
            TetrominoType.L -> Color(0xFFFFCFA8)
        }
        val bottomColor = Color(
            red = (topColor.red * 0.80f),
            green = (topColor.green * 0.78f),
            blue = (topColor.blue * 0.86f),
            alpha = 1f
        )
        val inset = (size.minDimension * 0.06f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.28f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, topColor, corner)
            return
        }

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(topColor, bottomColor),
                startY = bTopLeft.y,
                endY = bTopLeft.y + bSize.height
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )
        // Creamy inner highlight pill
        drawRoundRect(
            color = Color(0xFFFFFDF8).copy(alpha = 0.55f),
            topLeft = Offset(bTopLeft.x + bSize.width * 0.14f, bTopLeft.y + bSize.height * 0.12f),
            size = Size(bSize.width * 0.72f, bSize.height * 0.20f),
            cornerRadius = CornerRadius(bSize.minDimension * 0.14f)
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.45f),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner,
            style = Stroke(width = 1.2f)
        )
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "pastel_dream"
    }
}

/**
 * 4. Retro Handheld: Nostalgic 4-shade monochrome LCD olive-green pixel blocks.
 */
class RetroGameboySkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Retro Handheld"
    override val description: String = "4-shade olive LCD pixel blocks"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val lcdDarkest = Color(0xFF0F380F)
        val lcdDark = Color(0xFF306230)
        val lcdLight = Color(0xFF8BAC0F)
        val lcdLightest = Color(0xFF9BBC0F)

        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, lcdLight)
            return
        }

        drawRect(color = lcdDarkest, topLeft = bTopLeft, size = bSize)
        val pad1 = bSize.minDimension * 0.14f
        drawRect(
            color = lcdDark,
            topLeft = Offset(bTopLeft.x + pad1, bTopLeft.y + pad1),
            size = Size((bSize.width - pad1 * 2f).coerceAtLeast(1f), (bSize.height - pad1 * 2f).coerceAtLeast(1f))
        )
        val pad2 = bSize.minDimension * 0.28f
        drawRect(
            color = lcdLight,
            topLeft = Offset(bTopLeft.x + pad2, bTopLeft.y + pad2),
            size = Size((bSize.width - pad2 * 2f).coerceAtLeast(1f), (bSize.height - pad2 * 2f).coerceAtLeast(1f))
        )
        drawRect(
            color = lcdLightest,
            topLeft = Offset(bTopLeft.x + pad1, bTopLeft.y + pad1),
            size = Size(bSize.width * 0.16f, bSize.height * 0.16f)
        )
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "retro_gameboy"
    }
}

/**
 * 5. Blueprint CAD: Deep navy technical blueprint fill with white drafting crosshairs and corner ticks.
 */
class BlueprintGridSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Blueprint CAD"
    override val description: String = "Technical drafting lines & ticks"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val accent = Color(0xFF7AD7FF)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, accent)
            return
        }

        drawRect(color = Color(0xFF0A2547), topLeft = bTopLeft, size = bSize)
        drawRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = bTopLeft,
            size = bSize,
            style = Stroke(width = 1.2f)
        )
        val cx = bTopLeft.x + bSize.width / 2f
        val cy = bTopLeft.y + bSize.height / 2f
        val cross = bSize.minDimension * 0.22f
        drawLine(
            color = accent.copy(alpha = 0.65f),
            start = Offset(cx - cross, cy),
            end = Offset(cx + cross, cy),
            strokeWidth = 1f
        )
        drawLine(
            color = accent.copy(alpha = 0.65f),
            start = Offset(cx, cy - cross),
            end = Offset(cx, cy + cross),
            strokeWidth = 1f
        )
        // Corner tick marks
        val tick = bSize.minDimension * 0.18f
        drawLine(Color.White, bTopLeft, Offset(bTopLeft.x + tick, bTopLeft.y), strokeWidth = 1.6f)
        drawLine(Color.White, bTopLeft, Offset(bTopLeft.x, bTopLeft.y + tick), strokeWidth = 1.6f)
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "blueprint_grid"
    }
}

// =============================================================================
// RARE TIER SKINS (6..15 — Shaders + Subtle Deterministic Per-Cell Particles)
// =============================================================================

/**
 * 6. Neon Cyber: Dark core, glowing neon outline + 2 floating neon sparks per block.
 */
class NeonCyberSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Neon Cyber"
    override val description: String = "Glowing neon core + sparks"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val neon = when (tetrominoType) {
            TetrominoType.I -> Color(0xFF00F5FF)
            TetrominoType.O -> Color(0xFFFFE600)
            TetrominoType.T -> Color(0xFFD500F9)
            TetrominoType.S -> Color(0xFF00FF87)
            TetrominoType.Z -> Color(0xFFFF1744)
            TetrominoType.J -> Color(0xFF2979FF)
            TetrominoType.L -> Color(0xFFFF6D00)
        }
        val inset = (size.minDimension * 0.06f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.16f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, neon, corner)
            return
        }

        drawRoundRect(color = Color(0xFF0A1022).copy(alpha = 0.92f), topLeft = bTopLeft, size = bSize, cornerRadius = corner)
        drawRoundRect(
            color = neon.copy(alpha = 0.30f),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner,
            style = Stroke(width = (bSize.minDimension * 0.22f).coerceAtLeast(3f))
        )
        drawRoundRect(
            color = neon,
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner,
            style = Stroke(width = (bSize.minDimension * 0.09f).coerceAtLeast(1.5f))
        )

        // 2 deterministic floating neon sparks per block
        val t = animTimeMillis / 1000.0
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val px = bTopLeft.x + bSize.width * (0.5f + 0.34f * sin(t * 2.1 + phase).toFloat())
            val py = bTopLeft.y + bSize.height * (0.5f + 0.32f * cos(t * 1.7 + phase * 1.3).toFloat())
            val alpha = (0.55 + 0.45 * sin(t * 3.8 + phase)).toFloat()
            drawCircle(color = neon.copy(alpha = alpha), radius = (bSize.minDimension * 0.075f).coerceAtLeast(1.4f), center = Offset(px, py))
        }

        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "neon_cyber"
    }
}

/**
 * 7. Pixel Crystal: Faceted diamond inner polygons + periodic twinkling star glint.
 */
class PixelCrystalSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Pixel Crystal"
    override val description: String = "Faceted gem + twinkling glint"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val gemBase = when (tetrominoType) {
            TetrominoType.I -> Color(0xFF00E5FF)
            TetrominoType.O -> Color(0xFFFFD740)
            TetrominoType.T -> Color(0xFFE040FB)
            TetrominoType.S -> Color(0xFF69F0AE)
            TetrominoType.Z -> Color(0xFFFF5252)
            TetrominoType.J -> Color(0xFF448AFF)
            TetrominoType.L -> Color(0xFFFFAB40)
        }
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, gemBase)
            return
        }

        val dark = Color(gemBase.red * 0.38f, gemBase.green * 0.38f, gemBase.blue * 0.48f, 1f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(gemBase, dark),
                start = bTopLeft,
                end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
            ),
            topLeft = bTopLeft,
            size = bSize
        )

        val cx = bTopLeft.x + bSize.width / 2f
        val cy = bTopLeft.y + bSize.height / 2f
        val r = bSize.minDimension * 0.28f
        // Diamond facet lines (zero Path allocation)
        drawLine(Color.White.copy(alpha = 0.55f), Offset(cx, cy - r), Offset(cx + r, cy), strokeWidth = 1.2f)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(cx + r, cy), Offset(cx, cy + r), strokeWidth = 1.2f)
        drawLine(Color.White.copy(alpha = 0.45f), Offset(cx, cy + r), Offset(cx - r, cy), strokeWidth = 1.2f)
        drawLine(Color.White.copy(alpha = 0.65f), Offset(cx - r, cy), Offset(cx, cy - r), strokeWidth = 1.2f)

        // Periodic twinkling star glint
        val t = animTimeMillis / 1000.0
        val twinkle = ((sin(t * 2.8 + cellPhase(gridX, gridY, 0)) + 1.0) * 0.5).toFloat()
        if (twinkle > 0.65f) {
            val glintAlpha = ((twinkle - 0.65f) / 0.35f).coerceIn(0f, 1f)
            val gx = bTopLeft.x + bSize.width * 0.30f
            val gy = bTopLeft.y + bSize.height * 0.28f
            val arm = bSize.minDimension * 0.15f * glintAlpha
            drawLine(Color.White.copy(alpha = glintAlpha), Offset(gx - arm, gy), Offset(gx + arm, gy), strokeWidth = 1.4f)
            drawLine(Color.White.copy(alpha = glintAlpha), Offset(gx, gy - arm), Offset(gx, gy + arm), strokeWidth = 1.4f)
        }

        drawRect(color = Color.White.copy(alpha = 0.6f), topLeft = bTopLeft, size = bSize, style = Stroke(width = 1.2f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "pixel_crystal"
    }
}

/**
 * 8. Frosted Ice: Glacial cyan gradient, white frost cracks + tiny drifting snowflakes.
 */
class FrostedIceSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Frosted Ice"
    override val description: String = "Glacial cyan + drifting snow"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val iceLight = Color(0xFFBDF7FF)
        val iceDeep = Color(0xFF1A84B8)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.14f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, iceLight, corner)
            return
        }

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(iceLight, iceDeep),
                startY = bTopLeft.y,
                endY = bTopLeft.y + bSize.height
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )
        // Frost cracks
        drawLine(
            Color.White.copy(alpha = 0.65f),
            Offset(bTopLeft.x + bSize.width * 0.18f, bTopLeft.y + bSize.height * 0.2f),
            Offset(bTopLeft.x + bSize.width * 0.56f, bTopLeft.y + bSize.height * 0.54f),
            strokeWidth = 1.2f
        )
        drawLine(
            Color.White.copy(alpha = 0.55f),
            Offset(bTopLeft.x + bSize.width * 0.56f, bTopLeft.y + bSize.height * 0.54f),
            Offset(bTopLeft.x + bSize.width * 0.80f, bTopLeft.y + bSize.height * 0.35f),
            strokeWidth = 1.0f
        )

        // 2 tiny drifting snowflakes
        val t = animTimeMillis / 1000.0
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val fall = positiveMod(t * 0.45 + phase * 0.37, 1.0).toFloat()
            val px = bTopLeft.x + bSize.width * (0.25f + 0.5f * positiveMod(phase + sin(t + phase) * 0.2, 1.0).toFloat())
            val py = bTopLeft.y + bSize.height * fall
            drawCircle(
                color = Color.White.copy(alpha = 0.82f),
                radius = (bSize.minDimension * 0.06f).coerceAtLeast(1.2f),
                center = Offset(px, py)
            )
        }

        drawRoundRect(color = Color.White.copy(alpha = 0.65f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.3f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "frosted_ice"
    }
}

/**
 * 9. Toxic Slime: Radio-active lime gradient + slow rising translucent bubbles.
 */
class ToxicSlimeSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Toxic Slime"
    override val description: String = "Radioactive lime + rising bubbles"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val limeGlow = Color(0xFF9BFF00)
        val acidDark = Color(0xFF145214)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.22f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, limeGlow, corner)
            return
        }

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF56D918), acidDark),
                startY = bTopLeft.y,
                endY = bTopLeft.y + bSize.height
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )

        // 2 slow rising translucent bubbles
        val t = animTimeMillis / 1000.0
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val rise = 1f - positiveMod(t * 0.42 + phase * 0.51, 1.0).toFloat()
            val px = bTopLeft.x + bSize.width * (0.30f + 0.42f * ((sin(phase) + 1.0) * 0.5).toFloat())
            val py = bTopLeft.y + bSize.height * (0.15f + 0.72f * rise)
            val r = (bSize.minDimension * (0.09f + i * 0.03f)).coerceAtLeast(1.8f)
            drawCircle(color = limeGlow.copy(alpha = 0.28f), radius = r, center = Offset(px, py))
            drawCircle(color = Color.White.copy(alpha = 0.55f), radius = r, center = Offset(px, py), style = Stroke(1f))
        }

        drawRoundRect(color = limeGlow.copy(alpha = 0.85f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.5f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "toxic_slime"
    }
}

/**
 * 10. Sakura Blossom: Cherry blossom pink gradient + drifting pink petal particles.
 */
class SakuraPetalSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Sakura Blossom"
    override val description: String = "Cherry pink + drifting petals"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val petalLight = Color(0xFFFFD1E3)
        val petalDeep = Color(0xFFD84B84)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.22f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, petalLight, corner)
            return
        }

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(petalLight, petalDeep),
                start = bTopLeft,
                end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )

        // 2 drifting pink petal ovals
        val t = animTimeMillis / 1000.0
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val driftY = positiveMod(t * 0.35 + phase * 0.31, 1.0).toFloat()
            val driftX = (0.22f + 0.56f * ((sin(t * 1.4 + phase) + 1.0) * 0.5).toFloat())
            val px = bTopLeft.x + bSize.width * driftX
            val py = bTopLeft.y + bSize.height * driftY
            val pw = (bSize.width * 0.16f).coerceAtLeast(2.4f)
            val ph = (bSize.height * 0.10f).coerceAtLeast(1.6f)
            drawOval(
                color = Color(0xFFFFF0F6).copy(alpha = 0.85f),
                topLeft = Offset(px - pw / 2f, py - ph / 2f),
                size = Size(pw, ph)
            )
        }

        drawRoundRect(color = Color.White.copy(alpha = 0.45f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.2f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "sakura_petal"
    }
}

/**
 * 11. Obsidian Magma: Charred volcanic black rock with pulsing orange lava cracks + rising embers.
 */
class ObsidianMagmaSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Obsidian Magma"
    override val description: String = "Molten lava cracks + rising embers"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val lavaOrange = Color(0xFFFF5722)
        val lavaGold = Color(0xFFFFB300)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.14f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, lavaOrange, corner)
            return
        }

        val t = animTimeMillis / 1000.0
        val pulse = (0.60 + 0.40 * sin(t * 3.0 + cellPhase(gridX, gridY, 0))).toFloat()

        drawRoundRect(color = Color(0xFF141016), topLeft = bTopLeft, size = bSize, cornerRadius = corner)
        drawLine(
            color = lavaOrange.copy(alpha = pulse),
            start = Offset(bTopLeft.x + bSize.width * 0.15f, bTopLeft.y + bSize.height * 0.78f),
            end = Offset(bTopLeft.x + bSize.width * 0.55f, bTopLeft.y + bSize.height * 0.44f),
            strokeWidth = (bSize.minDimension * 0.09f).coerceAtLeast(1.6f)
        )
        drawLine(
            color = lavaGold.copy(alpha = pulse),
            start = Offset(bTopLeft.x + bSize.width * 0.55f, bTopLeft.y + bSize.height * 0.44f),
            end = Offset(bTopLeft.x + bSize.width * 0.85f, bTopLeft.y + bSize.height * 0.18f),
            strokeWidth = (bSize.minDimension * 0.07f).coerceAtLeast(1.4f)
        )

        // 2 rising embers
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val rise = 1f - positiveMod(t * 0.65 + phase * 0.43, 1.0).toFloat()
            val px = bTopLeft.x + bSize.width * (0.25f + 0.5f * ((cos(phase + t) + 1.0) * 0.5).toFloat())
            val py = bTopLeft.y + bSize.height * rise
            drawCircle(color = lavaGold.copy(alpha = 0.85f), radius = (bSize.minDimension * 0.065f).coerceAtLeast(1.3f), center = Offset(px, py))
        }

        drawRoundRect(color = lavaOrange.copy(alpha = 0.8f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.4f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "obsidian_magma"
    }
}

/**
 * 12. Cyber Matrix: Pitch-black core with glowing emerald border + falling digital rain motes.
 */
class CyberMatrixSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Cyber Matrix"
    override val description: String = "Emerald code + falling rain"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val emerald = Color(0xFF00FF66)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, emerald)
            return
        }

        drawRect(color = Color(0xFF040B06), topLeft = bTopLeft, size = bSize)

        // 2 falling digital rain motes
        val t = animTimeMillis / 1000.0
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val fall = positiveMod(t * 0.85 + phase * 0.41, 1.0).toFloat()
            val px = bTopLeft.x + bSize.width * (if (i == 0) 0.30f else 0.70f)
            val py = bTopLeft.y + bSize.height * (0.1f + 0.72f * fall)
            drawRect(
                color = emerald.copy(alpha = 0.85f),
                topLeft = Offset(px - 1.2f, py),
                size = Size((bSize.width * 0.09f).coerceAtLeast(1.8f), (bSize.height * 0.18f).coerceAtLeast(3f))
            )
        }

        drawRect(color = emerald, topLeft = bTopLeft, size = bSize, style = Stroke(width = 1.5f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "cyber_matrix"
    }
}

/**
 * 13. Synthwave 84: Magenta-to-amber retro sunset gradient, horizontal scanlines + floating sun dust.
 */
class SunsetSynthwaveSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Synthwave 84"
    override val description: String = "Retro sunset scanlines + dust"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val magenta = Color(0xFFFF2A85)
        val amber = Color(0xFFFFB800)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, magenta)
            return
        }

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(amber, magenta, Color(0xFF420A5E)),
                startY = bTopLeft.y,
                endY = bTopLeft.y + bSize.height
            ),
            topLeft = bTopLeft,
            size = bSize
        )
        // Retro scanlines
        for (lineIdx in 1..3) {
            val ly = bTopLeft.y + bSize.height * (lineIdx / 4f)
            drawLine(
                color = Color(0xFF0A0614).copy(alpha = 0.42f),
                start = Offset(bTopLeft.x, ly),
                end = Offset(bTopLeft.x + bSize.width, ly),
                strokeWidth = 1.4f
            )
        }
        // Floating sun dust
        val t = animTimeMillis / 1000.0
        val phase = cellPhase(gridX, gridY, 0)
        val px = bTopLeft.x + bSize.width * (0.5f + 0.30f * sin(t * 1.5 + phase).toFloat())
        val py = bTopLeft.y + bSize.height * (0.5f + 0.28f * cos(t * 1.2 + phase).toFloat())
        drawCircle(color = amber.copy(alpha = 0.85f), radius = (bSize.minDimension * 0.07f).coerceAtLeast(1.3f), center = Offset(px, py))

        drawRect(color = Color(0xFF00F0FF).copy(alpha = 0.65f), topLeft = bTopLeft, size = bSize, style = Stroke(1.3f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "sunset_synthwave"
    }
}

/**
 * 14. Royal Amethyst: Deep royal purple crystal facets + subtle violet stardust sparkles.
 */
class RoyalAmethystSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Royal Amethyst"
    override val description: String = "Purple facets + violet stardust"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val violet = Color(0xFFC77DFF)
        val royal = Color(0xFF3C096C)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, violet)
            return
        }

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF7B2CBF), royal),
                start = bTopLeft,
                end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
            ),
            topLeft = bTopLeft,
            size = bSize
        )
        drawLine(
            color = violet.copy(alpha = 0.55f),
            start = bTopLeft,
            end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height),
            strokeWidth = 1.2f
        )

        val t = animTimeMillis / 1000.0
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val px = bTopLeft.x + bSize.width * (0.25f + 0.5f * ((sin(phase * 1.9 + t * 1.3) + 1.0) * 0.5).toFloat())
            val py = bTopLeft.y + bSize.height * (0.25f + 0.5f * ((cos(phase * 1.3 + t * 1.6) + 1.0) * 0.5).toFloat())
            val alpha = (0.45 + 0.55 * sin(t * 3.4 + phase)).toFloat()
            drawCircle(color = Color(0xFFE0AAFF).copy(alpha = alpha), radius = (bSize.minDimension * 0.065f).coerceAtLeast(1.3f), center = Offset(px, py))
        }

        drawRect(color = violet.copy(alpha = 0.8f), topLeft = bTopLeft, size = bSize, style = Stroke(1.4f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "royal_amethyst"
    }
}

/**
 * 15. Electric Volt: Cobalt blue core with animated jagged lightning bolts + micro-sparks.
 */
class ElectricVoltSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Electric Volt"
    override val description: String = "Cobalt lightning bolts + sparks"

    override fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean,
        animTimeMillis: Long
    ) {
        val boltBlue = Color(0xFF40C4FF)
        val cobalt = Color(0xFF0A2472)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, boltBlue)
            return
        }

        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF1E56B0), cobalt),
                center = Offset(bTopLeft.x + bSize.width / 2f, bTopLeft.y + bSize.height / 2f),
                radius = bSize.maxDimension * 0.8f
            ),
            topLeft = bTopLeft,
            size = bSize
        )

        val t = animTimeMillis / 1000.0
        val jitter = (sin(t * 16.0 + cellPhase(gridX, gridY, 0)) * bSize.minDimension * 0.09).toFloat()
        // Animated jagged lightning arc across block
        val p1 = Offset(bTopLeft.x + bSize.width * 0.14f, bTopLeft.y + bSize.height * 0.22f)
        val p2 = Offset(bTopLeft.x + bSize.width * 0.48f + jitter, bTopLeft.y + bSize.height * 0.45f)
        val p3 = Offset(bTopLeft.x + bSize.width * 0.36f - jitter, bTopLeft.y + bSize.height * 0.62f)
        val p4 = Offset(bTopLeft.x + bSize.width * 0.84f, bTopLeft.y + bSize.height * 0.80f)
        drawLine(Color.White, p1, p2, strokeWidth = 1.6f)
        drawLine(boltBlue, p2, p3, strokeWidth = 1.8f)
        drawLine(Color.White, p3, p4, strokeWidth = 1.6f)

        // Micro-sparks
        for (i in 0 until 2) {
            val phase = cellPhase(gridX, gridY, i)
            val sx = bTopLeft.x + bSize.width * (0.5f + 0.36f * cos(t * 5.2 + phase).toFloat())
            val sy = bTopLeft.y + bSize.height * (0.5f + 0.36f * sin(t * 4.7 + phase).toFloat())
            drawCircle(color = Color(0xFFE1F5FE), radius = (bSize.minDimension * 0.055f).coerceAtLeast(1.2f), center = Offset(sx, sy))
        }

        drawRect(color = boltBlue, topLeft = bTopLeft, size = bSize, style = Stroke(1.5f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "electric_volt"
    }
}
