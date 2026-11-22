package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldBright
import com.example.ui.theme.FrostIce

/**
 * Custom vector-rendered luxury Christmas Santa hat with 3D crimson velvet shading,
 * champagne-gold trim, layered plush fur brim, and dimensional pom-pom.
 */
@Composable
fun LuxuryChristmasHat(
    modifier: Modifier = Modifier,
    baseTiltDegrees: Float = -12f,
    animated: Boolean = true
) {
    val swayDegrees = if (animated) {
        val transition = rememberInfiniteTransition(label = "santaHatSway")
        val sway by transition.animateFloat(
            initialValue = -2.2f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hatSwayAngle"
        )
        sway
    } else {
        0f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pivot = Offset(w * 0.45f, h * 0.72f)

        rotate(degrees = baseTiltDegrees + swayDegrees, pivot = pivot) {
            drawSantaHatArtwork(w, h)
        }
    }
}

private fun DrawScope.drawSantaHatArtwork(w: Float, h: Float) {
    // Key anchor proportions in normalized hat coordinates
    val brimLeft = w * 0.12f
    val brimRight = w * 0.76f
    val brimTop = h * 0.65f
    val brimBottom = h * 0.85f
    val brimWidth = brimRight - brimLeft
    val brimHeight = brimBottom - brimTop
    val brimMidX = (brimLeft + brimRight) * 0.5f

    val tipX = w * 0.87f
    val tipY = h * 0.48f

    // 1. Soft ambient drop shadow beneath the crown & brim
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.46f),
                Color.Black.copy(alpha = 0.14f),
                Color.Transparent
            ),
            center = Offset(brimMidX, brimBottom + h * 0.02f),
            radius = brimWidth * 0.62f
        ),
        topLeft = Offset(brimLeft - w * 0.03f, brimBottom - brimHeight * 0.35f),
        size = Size(brimWidth + w * 0.06f, brimHeight * 0.95f)
    )

    // 2. Crimson velvet cone body curving gracefully into a folded tip
    val hatBodyPath = Path().apply {
        moveTo(brimLeft + brimWidth * 0.06f, brimTop + brimHeight * 0.18f)
        cubicTo(
            w * 0.21f, h * 0.24f,
            w * 0.46f, h * 0.07f,
            w * 0.69f, h * 0.18f
        )
        cubicTo(
            w * 0.84f, h * 0.25f,
            w * 0.89f, h * 0.36f,
            tipX, tipY
        )
        cubicTo(
            w * 0.76f, h * 0.43f,
            w * 0.71f, h * 0.34f,
            w * 0.63f, h * 0.38f
        )
        cubicTo(
            w * 0.67f, h * 0.48f,
            brimRight - brimWidth * 0.05f, brimTop + brimHeight * 0.12f,
            brimRight - brimWidth * 0.05f, brimTop + brimHeight * 0.22f
        )
        close()
    }

    // Rich crimson velvet fill
    drawPath(
        path = hatBodyPath,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFF465E),
                Color(0xFFD61F38),
                Color(0xFFA01125),
                Color(0xFF660714)
            ),
            start = Offset(w * 0.26f, h * 0.14f),
            end = Offset(w * 0.82f, brimTop)
        )
    )

    // Velvet sheen highlight along upper-left arch
    val highlightSheen = Path().apply {
        moveTo(w * 0.22f, brimTop - h * 0.02f)
        cubicTo(
            w * 0.28f, h * 0.29f,
            w * 0.45f, h * 0.15f,
            w * 0.64f, h * 0.21f
        )
        cubicTo(
            w * 0.50f, h * 0.22f,
            w * 0.36f, h * 0.35f,
            w * 0.33f, brimTop - h * 0.01f
        )
        close()
    }
    drawPath(
        path = highlightSheen,
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0x66FFD0D8),
                Color(0x28FF758A),
                Color.Transparent
            ),
            start = Offset(w * 0.32f, h * 0.18f),
            end = Offset(w * 0.34f, brimTop)
        )
    )

    // Velvet fold shadow crease under drooping bend
    val foldShadow = Path().apply {
        moveTo(w * 0.48f, h * 0.35f)
        quadraticTo(w * 0.59f, h * 0.32f, w * 0.66f, h * 0.42f)
    }
    drawPath(
        path = foldShadow,
        color = Color(0x7043030B),
        style = Stroke(width = w * 0.032f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Champagne-gold stitched piping border just above the fur cuff
    val goldTrimArc = Path().apply {
        moveTo(brimLeft + brimWidth * 0.06f, brimTop + brimHeight * 0.04f)
        quadraticTo(
            brimMidX, brimTop - brimHeight * 0.18f,
            brimRight - brimWidth * 0.05f, brimTop + brimHeight * 0.05f
        )
    }
    drawPath(
        path = goldTrimArc,
        brush = Brush.horizontalGradient(
            listOf(ChampagneGold, ChampagneGoldBright, ChampagneGold)
        ),
        style = Stroke(width = (w * 0.024f).coerceAtLeast(1.4f), cap = StrokeCap.Round)
    )

    // 3. Hanging Tip & 3D Plush Pom-Pom
    val pomRadius = (w * 0.105f).coerceAtLeast(3f)
    val pomCenter = Offset(tipX - pomRadius * 0.15f, tipY + pomRadius * 0.25f)

    // Pom-pom soft shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.28f),
        radius = pomRadius * 1.12f,
        center = Offset(pomCenter.x + pomRadius * 0.12f, pomCenter.y + pomRadius * 0.18f)
    )

    // Fluff puffs around the pom-pom perimeter for realistic fur silhouette
    val fluffOffsets = listOf(
        Offset(-0.48f, -0.22f) to 0.62f,
        Offset(0.05f, -0.52f) to 0.64f,
        Offset(0.50f, -0.18f) to 0.60f,
        Offset(0.45f, 0.38f) to 0.58f,
        Offset(-0.08f, 0.50f) to 0.62f,
        Offset(-0.50f, 0.30f) to 0.58f
    )
    fluffOffsets.forEach { (rel, rScale) ->
        drawCircle(
            color = Color(0xFFE7EFFA),
            radius = pomRadius * rScale,
            center = Offset(
                pomCenter.x + rel.x * pomRadius,
                pomCenter.y + rel.y * pomRadius
            )
        )
    }

    // Core 3D pom-pom shading
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                Color(0xFFF3F7FD),
                Color(0xFFC8D7EA)
            ),
            center = Offset(pomCenter.x - pomRadius * 0.28f, pomCenter.y - pomRadius * 0.30f),
            radius = pomRadius * 1.25f
        ),
        radius = pomRadius,
        center = pomCenter
    )

    // 4. Layered Plush Ermine White Fur Brim
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White,
                Color(0xFFEEF4FC),
                Color(0xFFC2D1E5)
            ),
            startY = brimTop,
            endY = brimBottom
        ),
        topLeft = Offset(brimLeft, brimTop),
        size = Size(brimWidth, brimHeight),
        cornerRadius = CornerRadius(brimHeight * 0.5f, brimHeight * 0.5f)
    )

    // Scalloped fur puffs along top & bottom edges of the cuff
    val puffCount = 7
    for (i in 0 until puffCount) {
        val frac = i.toFloat() / (puffCount - 1).toFloat()
        val px = brimLeft + brimWidth * (0.08f + frac * 0.84f)
        val archOffset = kotlin.math.sin(frac * Math.PI).toFloat() * (brimHeight * 0.16f)
        val r = brimHeight * (if (i % 2 == 0) 0.44f else 0.38f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFEDF3FB), Color(0xFFCFDCEE)),
                center = Offset(px - r * 0.22f, brimTop + r * 0.32f - archOffset),
                radius = r * 1.2f
            ),
            radius = r,
            center = Offset(px, brimTop + r * 0.48f - archOffset)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF6FAFF), Color(0xFFD5E1F0)),
                center = Offset(px, brimBottom - r * 0.45f),
                radius = r * 1.15f
            ),
            radius = r * 0.86f,
            center = Offset(px, brimBottom - r * 0.35f)
        )
    }

    // Top specular highlight along the fur cuff
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.85f),
                FrostIce.copy(alpha = 0.65f),
                Color.Transparent
            )
        ),
        topLeft = Offset(brimLeft + brimWidth * 0.12f, brimTop + brimHeight * 0.10f),
        size = Size(brimWidth * 0.76f, brimHeight * 0.22f),
        cornerRadius = CornerRadius(brimHeight * 0.15f, brimHeight * 0.15f)
    )

    // Subtle luxury golden sparkle glint on the crown
    val glintCenter = Offset(w * 0.45f, h * 0.24f)
    val glintArm = (w * 0.048f).coerceAtLeast(1.8f)
    val glintCore = glintArm * 0.28f
    val glintPath = Path().apply {
        moveTo(glintCenter.x, glintCenter.y - glintArm)
        lineTo(glintCenter.x + glintCore, glintCenter.y - glintCore)
        lineTo(glintCenter.x + glintArm, glintCenter.y)
        lineTo(glintCenter.x + glintCore, glintCenter.y + glintCore)
        lineTo(glintCenter.x, glintCenter.y + glintArm)
        lineTo(glintCenter.x - glintCore, glintCenter.y + glintCore)
        lineTo(glintCenter.x - glintArm, glintCenter.y)
        lineTo(glintCenter.x - glintCore, glintCenter.y - glintCore)
        close()
    }
    drawPath(path = glintPath, color = ChampagneGoldBright.copy(alpha = 0.92f))
}
