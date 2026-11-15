package com.multitetris.ui.skin

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.multitetris.game.TetrominoType

enum class SkinTier(val label: String) {
    BASIC("BASIC"),
    RARE("RARE"),
    MYTHIC("MYTHIC")
}

/**
 * Renders individual Tetromino blocks and ghost previews based on a skinId.
 */
interface SkinRenderer {
    val skinId: String
    val displayName: String
    val description: String

    fun DrawScope.drawBlockCell(
        topLeft: Offset,
        size: Size,
        gridX: Int,
        gridY: Int,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean = false,
        animTimeMillis: Long = 0L
    )

    fun DrawScope.drawBlock(
        topLeft: Offset,
        size: Size,
        tetrominoType: TetrominoType,
        playerId: Int,
        isGhost: Boolean,
        isPausedOnPiece: Boolean = false,
        animProgress: Float = 0f
    ) {
        drawBlockCell(
            topLeft = topLeft,
            size = size,
            gridX = 0,
            gridY = 0,
            tetrominoType = tetrominoType,
            playerId = playerId,
            isGhost = isGhost,
            isPausedOnPiece = isPausedOnPiece,
            animTimeMillis = (animProgress * 2200f).toLong()
        )
    }
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

internal fun cellPhase(gridX: Int, gridY: Int, particleIndex: Int): Double {
    return gridX * 1.731 + gridY * 2.417 + particleIndex * 1.913
}

internal fun positiveMod(value: Double, modulus: Double): Double {
    val rem = value % modulus
    return if (rem < 0.0) rem + modulus else rem
}

internal fun DrawScope.drawGhostOutline(
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

internal fun DrawScope.drawPausedAndOwnerMarkers(
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
 * 1. Classic Arcade: Retro arcade beveled Tetromino blocks with crisp highlights.
 */
class ClassicSkinRenderer : SkinRenderer {
    override val skinId: String = SKIN_ID
    override val displayName: String = "Classic Arcade"
    override val description: String = "Retro arcade beveled blocks"

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
        drawPath(path = darkPath, color = Color.Black.copy(alpha = 0.34f))

        drawRect(
            color = Color.Black.copy(alpha = 0.45f),
            topLeft = blockTopLeft,
            size = blockSize,
            style = Stroke(width = 1f)
        )

        drawPausedAndOwnerMarkers(blockTopLeft, blockSize, playerId, isPausedOnPiece)
    }

    companion object {
        const val SKIN_ID = "classic"
    }
}

data class SkinCatalogItem(
    val renderer: SkinRenderer,
    val priceCoins: Int,
    val tier: SkinTier
)

object SkinCatalog {
    val items: List<SkinCatalogItem> = listOf(
        // BASIC (5 skins — No particles)
        SkinCatalogItem(ClassicSkinRenderer(), priceCoins = 0, tier = SkinTier.BASIC),
        SkinCatalogItem(MinimalMatteSkinRenderer(), priceCoins = 100, tier = SkinTier.BASIC),
        SkinCatalogItem(PastelDreamSkinRenderer(), priceCoins = 150, tier = SkinTier.BASIC),
        SkinCatalogItem(RetroGameboySkinRenderer(), priceCoins = 200, tier = SkinTier.BASIC),
        SkinCatalogItem(BlueprintGridSkinRenderer(), priceCoins = 250, tier = SkinTier.BASIC),

        // RARE (10 skins — Shaders + subtle particles)
        SkinCatalogItem(NeonCyberSkinRenderer(), priceCoins = 350, tier = SkinTier.RARE),
        SkinCatalogItem(PixelCrystalSkinRenderer(), priceCoins = 400, tier = SkinTier.RARE),
        SkinCatalogItem(FrostedIceSkinRenderer(), priceCoins = 450, tier = SkinTier.RARE),
        SkinCatalogItem(ToxicSlimeSkinRenderer(), priceCoins = 500, tier = SkinTier.RARE),
        SkinCatalogItem(SakuraPetalSkinRenderer(), priceCoins = 550, tier = SkinTier.RARE),
        SkinCatalogItem(ObsidianMagmaSkinRenderer(), priceCoins = 600, tier = SkinTier.RARE),
        SkinCatalogItem(CyberMatrixSkinRenderer(), priceCoins = 650, tier = SkinTier.RARE),
        SkinCatalogItem(SunsetSynthwaveSkinRenderer(), priceCoins = 700, tier = SkinTier.RARE),
        SkinCatalogItem(RoyalAmethystSkinRenderer(), priceCoins = 750, tier = SkinTier.RARE),
        SkinCatalogItem(ElectricVoltSkinRenderer(), priceCoins = 800, tier = SkinTier.RARE),

        // MYTHIC (5 skins — Heavy particles, multi-layer glows & shaders)
        SkinCatalogItem(HologramGlitchSkinRenderer(), priceCoins = 1200, tier = SkinTier.MYTHIC),
        SkinCatalogItem(GoldenFlexSkinRenderer(), priceCoins = 1500, tier = SkinTier.MYTHIC),
        SkinCatalogItem(CosmicNebulaSkinRenderer(), priceCoins = 1800, tier = SkinTier.MYTHIC),
        SkinCatalogItem(DragonInfernoSkinRenderer(), priceCoins = 2200, tier = SkinTier.MYTHIC),
        SkinCatalogItem(QuantumSingularitySkinRenderer(), priceCoins = 3000, tier = SkinTier.MYTHIC)
    )

    fun priceFor(skinId: String): Int {
        return items.firstOrNull { it.renderer.skinId == skinId }?.priceCoins ?: 0
    }

    fun itemFor(skinId: String): SkinCatalogItem? {
        return items.firstOrNull { it.renderer.skinId == skinId }
    }
}

object SkinRegistry {
    private val defaultRenderer = SkinCatalog.items.first().renderer
    private val byId: Map<String, SkinRenderer> = SkinCatalog.items.associate { it.renderer.skinId to it.renderer }

    val availableSkins: List<SkinRenderer> = SkinCatalog.items.map { it.renderer }

    fun resolve(skinId: String): SkinRenderer {
        return byId[skinId] ?: defaultRenderer
    }
}
