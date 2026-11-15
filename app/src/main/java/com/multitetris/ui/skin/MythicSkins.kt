package com.multitetris.ui.skin

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import com.multitetris.game.TetrominoType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// =============================================================================
// MYTHIC TIER SKINS (16..20 — Heavy particles, multi-layer glows & shaders)
// =============================================================================

/**
 * 16. Hologram Glitch: Shifting prismatic rainbow gradient, animated RGB chromatic offset slices
 * + flying glitch pixel shards.
 */
class HologramGlitchSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Hologram Glitch"
    override val description: String = "Prismatic RGB slices + glitch shards"

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
        val t = animTimeMillis / 1000.0
        val hueShift = ((t * 140.0 + (gridX + gridY) * 18.0) % 360.0).toFloat()
        val c1 = Color.hsv(hueShift, 0.78f, 0.96f)
        val c2 = Color.hsv((hueShift + 95f) % 360f, 0.82f, 0.92f)
        val c3 = Color.hsv((hueShift + 190f) % 360f, 0.78f, 0.96f)

        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, c1)
            return
        }

        // Chromatic RGB offset slices
        val sliceOffset = (sin(t * 9.0 + gridY * 1.7) * bSize.width * 0.09).toFloat()
        drawRect(
            color = Color(0xFFFF006E).copy(alpha = 0.45f),
            topLeft = Offset(bTopLeft.x - sliceOffset, bTopLeft.y + bSize.height * 0.22f),
            size = Size(bSize.width, bSize.height * 0.22f)
        )
        drawRect(
            color = Color(0xFF00F5FF).copy(alpha = 0.45f),
            topLeft = Offset(bTopLeft.x + sliceOffset, bTopLeft.y + bSize.height * 0.56f),
            size = Size(bSize.width, bSize.height * 0.22f)
        )

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(c1.copy(alpha = 0.85f), c2.copy(alpha = 0.85f), c3.copy(alpha = 0.85f)),
                start = bTopLeft,
                end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
            ),
            topLeft = bTopLeft,
            size = bSize
        )

        // Scanline shimmer
        val scanY = bTopLeft.y + (positiveMod(t * 1.4, 1.0).toFloat() * bSize.height)
        drawLine(
            color = Color.White.copy(alpha = 0.6f),
            start = Offset(bTopLeft.x, scanY),
            end = Offset(bTopLeft.x + bSize.width, scanY),
            strokeWidth = 1.6f
        )

        // 4 flying glitch pixel shards
        for (i in 0 until 4) {
            val phase = cellPhase(gridX, gridY, i)
            val sx = bTopLeft.x + bSize.width * positiveMod(phase + t * (0.9 + i * 0.25), 1.0).toFloat()
            val sy = bTopLeft.y + bSize.height * positiveMod(phase * 1.7 + sin(t * 4.0 + phase) * 0.2, 1.0).toFloat()
            val shardSize = (bSize.minDimension * 0.11f).coerceAtLeast(1.8f)
            drawRect(
                color = if (i % 2 == 0) Color(0xFF00F5FF) else Color(0xFFFF2A85),
                topLeft = Offset(sx, sy),
                size = Size(shardSize, shardSize * 0.7f)
            )
        }

        drawRect(color = Color.White.copy(alpha = 0.75f), topLeft = bTopLeft, size = bSize, style = Stroke(1.4f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "hologram_glitch"
    }
}

/**
 * 17. Golden Flex: Rich 24K liquid gold metallic gradient, diagonal animated specular shine sweep
 * + 5 orbiting golden star sparkles.
 */
class GoldenFlexSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Golden Flex"
    override val description: String = "24K liquid gold + 5 star sparkles"

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
        val goldBright = Color(0xFFFFF3A1)
        val goldMid = Color(0xFFFFC107)
        val goldDeep = Color(0xFF9C6600)
        val inset = (size.minDimension * 0.05f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.16f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, goldMid, corner)
            return
        }

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(goldBright, goldMid, goldDeep, goldMid),
                start = bTopLeft,
                end = Offset(bTopLeft.x + bSize.width, bTopLeft.y + bSize.height)
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )

        // Diagonal animated specular shine sweep
        val t = animTimeMillis / 1000.0
        val sweep = positiveMod(t * 0.55, 1.0).toFloat()
        clipRect(
            left = bTopLeft.x,
            top = bTopLeft.y,
            right = bTopLeft.x + bSize.width,
            bottom = bTopLeft.y + bSize.height
        ) {
            val sweepX = bTopLeft.x - bSize.width + sweep * bSize.width * 2.8f
            drawLine(
                color = Color.White.copy(alpha = 0.65f),
                start = Offset(sweepX, bTopLeft.y + bSize.height),
                end = Offset(sweepX + bSize.width * 0.7f, bTopLeft.y),
                strokeWidth = (bSize.minDimension * 0.24f).coerceAtLeast(3f)
            )
        }

        // 5 orbiting golden star sparkles
        val cx = bTopLeft.x + bSize.width / 2f
        val cy = bTopLeft.y + bSize.height / 2f
        for (i in 0 until 5) {
            val phase = cellPhase(gridX, gridY, i)
            val angle = t * (1.8 + i * 0.25) + phase
            val px = cx + cos(angle).toFloat() * (bSize.width * 0.36f)
            val py = cy + sin(angle).toFloat() * (bSize.height * 0.36f)
            val arm = (bSize.minDimension * 0.11f).coerceAtLeast(1.8f)
            val alpha = (0.55 + 0.45 * sin(t * 5.0 + phase)).toFloat()
            drawLine(Color.White.copy(alpha = alpha), Offset(px - arm, py), Offset(px + arm, py), strokeWidth = 1.3f)
            drawLine(Color.White.copy(alpha = alpha), Offset(px, py - arm), Offset(px, py + arm), strokeWidth = 1.3f)
        }

        drawRoundRect(color = goldBright.copy(alpha = 0.9f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.6f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "golden_flex"
    }
}

/**
 * 18. Cosmic Nebula: Deep indigo-to-magenta swirling nebula core, pulsing inner stars
 * + multi-colored shooting stardust particles.
 */
class CosmicNebulaSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Cosmic Nebula"
    override val description: String = "Swirling galaxy + shooting stardust"

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
        val nebulaMagenta = Color(0xFFFF2E93)
        val nebulaCyan = Color(0xFF38BDF8)
        val deepSpace = Color(0xFF0D0628)
        val inset = (size.minDimension * 0.04f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.18f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, nebulaMagenta, corner)
            return
        }

        val t = animTimeMillis / 1000.0
        val cx = bTopLeft.x + bSize.width / 2f
        val cy = bTopLeft.y + bSize.height / 2f
        val swirlX = cx + cos(t * 1.6 + gridX).toFloat() * (bSize.width * 0.16f)
        val swirlY = cy + sin(t * 1.4 + gridY).toFloat() * (bSize.height * 0.16f)

        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(nebulaMagenta.copy(alpha = 0.85f), Color(0xFF4C1D95), deepSpace),
                center = Offset(swirlX, swirlY),
                radius = bSize.maxDimension * 0.9f
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )

        // Multi-colored shooting stardust particles
        for (i in 0 until 5) {
            val phase = cellPhase(gridX, gridY, i)
            val progress = positiveMod(t * 0.75 + phase * 0.29, 1.0).toFloat()
            val px = bTopLeft.x + bSize.width * progress
            val py = bTopLeft.y + bSize.height * positiveMod(phase * 0.7 + progress * 0.45, 1.0).toFloat()
            val pColor = when (i % 3) {
                0 -> Color.White
                1 -> nebulaCyan
                else -> nebulaMagenta
            }
            val r = (bSize.minDimension * 0.065f).coerceAtLeast(1.3f)
            drawCircle(color = pColor.copy(alpha = 0.9f), radius = r, center = Offset(px, py))
        }

        drawRoundRect(color = nebulaCyan.copy(alpha = 0.7f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.5f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "cosmic_nebula"
    }
}

/**
 * 19. Dragon Inferno: White-hot yellow-orange molten core, pulsing outer fire aura
 * + 6 rising/swirling flame ember particles.
 */
class DragonInfernoSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Dragon Inferno"
    override val description: String = "Molten fire aura + 6 flame embers"

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
        val whiteHot = Color(0xFFFFF59D)
        val flameOrange = Color(0xFFFF6D00)
        val crimson = Color(0xFFB71C1C)
        val inset = (size.minDimension * 0.04f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.16f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, flameOrange, corner)
            return
        }

        val t = animTimeMillis / 1000.0
        val pulse = (0.65 + 0.35 * sin(t * 5.0 + cellPhase(gridX, gridY, 0))).toFloat()

        // Outer fire aura glow
        drawRoundRect(
            color = flameOrange.copy(alpha = 0.34f * pulse),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner,
            style = Stroke(width = (bSize.minDimension * 0.22f).coerceAtLeast(3.5f))
        )

        // White-hot molten core
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(whiteHot, flameOrange, crimson),
                center = Offset(bTopLeft.x + bSize.width * 0.5f, bTopLeft.y + bSize.height * 0.55f),
                radius = bSize.maxDimension * 0.8f
            ),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner
        )

        // 6 rising/swirling flame ember particles
        for (i in 0 until 6) {
            val phase = cellPhase(gridX, gridY, i)
            val rise = 1f - positiveMod(t * (0.75 + i * 0.08) + phase * 0.33, 1.0).toFloat()
            val swirl = sin(t * 4.5 + phase).toFloat() * (bSize.width * 0.24f)
            val px = bTopLeft.x + bSize.width * (0.20f + (i / 5f) * 0.60f) + swirl * 0.5f
            val py = bTopLeft.y + bSize.height * rise
            val r = (bSize.minDimension * 0.065f).coerceAtLeast(1.3f)
            drawCircle(
                color = if (i % 2 == 0) whiteHot else flameOrange,
                radius = r,
                center = Offset(px.coerceIn(bTopLeft.x, bTopLeft.x + bSize.width), py)
            )
        }

        drawRoundRect(color = whiteHot.copy(alpha = 0.85f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.4f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "dragon_inferno"
    }
}

/**
 * 20. Quantum Void: Pitch-black event horizon core, glowing cyan/violet accretion ring,
 * pulsing gravitational distortion stroke + 6 orbiting quantum light particles.
 */
class QuantumSingularitySkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Quantum Void"
    override val description: String = "Event horizon + 6 orbiting quanta"

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
        val quantumCyan = Color(0xFF00F0FF)
        val quantumViolet = Color(0xFF9D4EDD)
        val inset = (size.minDimension * 0.04f).coerceAtLeast(1f)
        val bTopLeft = Offset(topLeft.x + inset, topLeft.y + inset)
        val bSize = Size((size.width - inset * 2f).coerceAtLeast(1f), (size.height - inset * 2f).coerceAtLeast(1f))
        val corner = CornerRadius(bSize.minDimension * 0.22f)

        if (isGhost) {
            drawGhostOutline(bTopLeft, bSize, quantumCyan, corner)
            return
        }

        val t = animTimeMillis / 1000.0
        val pulse = (0.55 + 0.45 * sin(t * 3.5 + cellPhase(gridX, gridY, 0))).toFloat()
        val cx = bTopLeft.x + bSize.width / 2f
        val cy = bTopLeft.y + bSize.height / 2f

        // Gravitational distortion outer ring
        drawRoundRect(
            color = quantumViolet.copy(alpha = 0.45f * pulse),
            topLeft = bTopLeft,
            size = bSize,
            cornerRadius = corner,
            style = Stroke(width = (bSize.minDimension * 0.18f).coerceAtLeast(2.8f))
        )
        drawRoundRect(color = Color(0xFF030308), topLeft = bTopLeft, size = bSize, cornerRadius = corner)

        // Glowing cyan/violet accretion ring
        val ringR = bSize.minDimension * 0.28f
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(quantumCyan, quantumViolet, quantumCyan),
                center = Offset(cx, cy)
            ),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = (bSize.minDimension * 0.08f).coerceAtLeast(1.6f))
        )
        // Event horizon pitch-black center
        drawCircle(color = Color.Black, radius = ringR * 0.68f, center = Offset(cx, cy))

        // 6 orbiting quantum light particles
        for (i in 0 until 6) {
            val angle = t * 2.6 + (i * PI * 2.0 / 6.0) + cellPhase(gridX, gridY, 0) * 0.25
            val rx = bSize.width * 0.38f
            val ry = bSize.height * 0.38f
            val px = cx + cos(angle).toFloat() * rx
            val py = cy + sin(angle).toFloat() * ry
            drawCircle(
                color = if (i % 2 == 0) quantumCyan else quantumViolet,
                radius = (bSize.minDimension * 0.065f).coerceAtLeast(1.3f),
                center = Offset(px, py)
            )
        }

        drawRoundRect(color = quantumCyan.copy(alpha = 0.8f), topLeft = bTopLeft, size = bSize, cornerRadius = corner, style = Stroke(1.3f))
        drawPausedAndOwnerMarkers(bTopLeft, bSize, playerId, isPausedOnPiece, corner)
    }

    companion object {
        const val SKIN_ID = "quantum_singularity"
    }
}
