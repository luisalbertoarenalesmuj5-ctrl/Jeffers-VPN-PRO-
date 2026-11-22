package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.BackgroundEffect
import com.example.data.model.ServerAnimationStyle
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CranberryRed
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.FrostIce
import com.example.ui.theme.MonoTypography
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun BackgroundEffectLayer(
    effect: BackgroundEffect,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    if (effect == BackgroundEffect.SOLID_BLACK) return

    if (effect == BackgroundEffect.IMAGE) {
        Image(
            painter = painterResource(id = R.drawable.img_vpn_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .fillMaxSize()
                .alpha(0.32f)
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bgEffect")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bgProgress"
    )

    val seeds = remember {
        List(42) { idx ->
            val r = Random(idx * 97 + 13)
            Triple(r.nextFloat(), r.nextFloat(), r.nextFloat() * 2.5f + 1f)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (effect == BackgroundEffect.CHRISTMAS_SNOW) {
            Image(
                painter = painterResource(id = R.drawable.img_christmas_tree_luxury),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.90f)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            when (effect) {
                BackgroundEffect.CHRISTMAS_SNOW -> {
                    val w = size.width
                    val h = size.height

                    // Light editorial vignette keeping the luxury Christmas tree & golden star crisp while grounding bottom Bento cards
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x18040A08),
                                Color(0x26050C09),
                                Color(0x4604090C),
                                Color(0x5E03060B)
                            )
                        ),
                        size = size
                    )

                    // Warm champagne gold & ruby crown aura behind top pine garlands
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ChampagneGold.copy(alpha = 0.18f),
                                CranberryRed.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(w * 0.50f, h * 0.16f),
                            radius = size.maxDimension * 0.52f
                        ),
                        size = size
                    )

                    // Delicate double-arc champagne fairy-light garland
                    val garlandYBase = 4.dp.toPx()
                    val drapeDepth = 15.dp.toPx()
                    val garlandPath = Path().apply {
                        moveTo(0f, garlandYBase)
                        quadraticTo(w * 0.25f, garlandYBase + drapeDepth, w * 0.5f, garlandYBase + 2.dp.toPx())
                        quadraticTo(w * 0.75f, garlandYBase + drapeDepth, w, garlandYBase)
                    }
                    drawPath(
                        path = garlandPath,
                        color = ChampagneGold.copy(alpha = 0.36f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    for (i in 1..11) {
                        val t = i / 12f
                        val bulbX = t * w
                        val localT = if (t <= 0.5f) t * 2f else (1f - t) * 2f
                        val sag = 4f * (t % 0.5f * 2f) * (1f - (t % 0.5f * 2f))
                        val bulbY = garlandYBase + sag * (drapeDepth * 0.52f) + localT * 1.4.dp.toPx()
                        val bulbAlpha = 0.44f + 0.52f * abs(sin((progress * PI * 2 + i * 0.85).toDouble()).toFloat())
                        val bulbTint = when (i % 3) {
                            0 -> ChampagneGold
                            1 -> accentColor
                            else -> CranberryRed
                        }
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(bulbTint.copy(alpha = bulbAlpha * 0.68f), Color.Transparent),
                                center = Offset(bulbX, bulbY),
                                radius = 9.dp.toPx()
                            ),
                            radius = 9.dp.toPx(),
                            center = Offset(bulbX, bulbY)
                        )
                        drawCircle(
                            color = FrostIce.copy(alpha = bulbAlpha),
                            radius = 1.6.dp.toPx(),
                            center = Offset(bulbX, bulbY)
                        )
                    }

                    // Gentle parallax crystalline snowflakes & champagne starlight glints
                    seeds.forEachIndexed { idx, (sx, sy, radiusSeed) ->
                        val speed = 0.24f + (idx % 4) * 0.14f
                        val y = ((sy + progress * speed) % 1f) * h
                        val swayX = sin((progress * PI * 2 + idx * 0.85).toDouble()).toFloat() * 12.dp.toPx()
                        val x = (sx * w + swayX).let { raw ->
                            when {
                                raw < 0f -> raw + w
                                raw > w -> raw - w
                                else -> raw
                            }
                        }

                        if (idx % 6 == 0) {
                            val arm = (radiusSeed * 1.9f + 2.2f).dp.toPx()
                            val flakeAlpha = 0.28f + 0.28f * abs(cos((progress * PI * 2 + idx).toDouble()).toFloat())
                            val flakeColor = FrostIce.copy(alpha = flakeAlpha)
                            val spinOffset = progress * 30.0 * if (idx % 2 == 0) 1.0 else -1.0
                            for (a in 0 until 6) {
                                val angleRad = (a * 60.0 + spinOffset) * PI / 180.0
                                val cosA = cos(angleRad).toFloat()
                                val sinA = sin(angleRad).toFloat()
                                drawLine(
                                    color = flakeColor,
                                    start = Offset(x, y),
                                    end = Offset(x + cosA * arm, y + sinA * arm),
                                    strokeWidth = 0.9.dp.toPx()
                                )
                                val branchOriginX = x + cosA * (arm * 0.62f)
                                val branchOriginY = y + sinA * (arm * 0.62f)
                                val branchLen = arm * 0.28f
                                val leftRad = angleRad + (42.0 * PI / 180.0)
                                val rightRad = angleRad - (42.0 * PI / 180.0)
                                drawLine(
                                    color = flakeColor.copy(alpha = flakeAlpha * 0.78f),
                                    start = Offset(branchOriginX, branchOriginY),
                                    end = Offset(
                                        branchOriginX + (cos(leftRad) * branchLen).toFloat(),
                                        branchOriginY + (sin(leftRad) * branchLen).toFloat()
                                    ),
                                    strokeWidth = 0.75.dp.toPx()
                                )
                                drawLine(
                                    color = flakeColor.copy(alpha = flakeAlpha * 0.78f),
                                    start = Offset(branchOriginX, branchOriginY),
                                    end = Offset(
                                        branchOriginX + (cos(rightRad) * branchLen).toFloat(),
                                        branchOriginY + (sin(rightRad) * branchLen).toFloat()
                                    ),
                                    strokeWidth = 0.75.dp.toPx()
                                )
                            }
                        } else if (idx % 7 == 0) {
                            val starAlpha = 0.24f + 0.40f * abs(sin((progress * PI * 4 + idx).toDouble()).toFloat())
                            val starRadius = (radiusSeed * 1.6f + 1.5f).dp.toPx()
                            val starColor = ChampagneGold.copy(alpha = starAlpha)
                            drawLine(
                                color = starColor,
                                start = Offset(x - starRadius, y),
                                end = Offset(x + starRadius, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawLine(
                                color = starColor,
                                start = Offset(x, y - starRadius),
                                end = Offset(x, y + starRadius),
                                strokeWidth = 1.dp.toPx()
                            )
                            drawCircle(
                                color = FrostIce.copy(alpha = starAlpha),
                                radius = 1.1.dp.toPx(),
                                center = Offset(x, y)
                            )
                        } else {
                            val snowAlpha = 0.14f + (idx % 3) * 0.08f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(FrostIce.copy(alpha = snowAlpha), Color.Transparent),
                                    center = Offset(x, y),
                                    radius = (radiusSeed * 1.35f).dp.toPx()
                                ),
                                radius = (radiusSeed * 1.35f).dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            BackgroundEffect.TECH_GRID -> {
                val step = 36.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        color = primaryColor.copy(alpha = 0.08f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f
                    )
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = primaryColor.copy(alpha = 0.08f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
                val scanY = progress * size.height
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        startY = (scanY - 60.dp.toPx()).coerceAtLeast(0f),
                        endY = scanY + 60.dp.toPx()
                    ),
                    size = size
                )
            }
            BackgroundEffect.PARTICLES -> {
                seeds.forEachIndexed { idx, (sx, sy, radius) ->
                    val y = ((sy + progress * (0.4f + (idx % 3) * 0.2f)) % 1f) * size.height
                    val x = (sx + sin((progress * 6.28f + idx).toDouble()).toFloat() * 0.03f) * size.width
                    val color = if (idx % 2 == 0) primaryColor else accentColor
                    drawCircle(
                        color = color.copy(alpha = 0.28f),
                        radius = radius * 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
            BackgroundEffect.GALAXY -> {
                seeds.forEachIndexed { idx, (sx, sy, radius) ->
                    val twinkle = 0.2f + 0.5f * kotlin.math.abs(
                        sin((progress * 6.28f + idx * 0.7f).toDouble()).toFloat()
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = twinkle),
                        radius = radius * 1.1.dp.toPx(),
                        center = Offset(sx * size.width, sy * size.height)
                    )
                }
            }
            BackgroundEffect.NEO_TEK -> {
                for (col in 0 until 10) {
                    val x = (col + 0.5f) * (size.width / 10f)
                    val dropY = ((progress * 1.5f + col * 0.17f) % 1f) * size.height
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.28f)),
                            startY = (dropY - 90.dp.toPx()).coerceAtLeast(0f),
                            endY = dropY
                        ),
                        start = Offset(x, (dropY - 90.dp.toPx()).coerceAtLeast(0f)),
                        end = Offset(x, dropY),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            else -> Unit
        }
    }
    }
}

@Composable
fun ServerCardAnimationStrip(
    style: ServerAnimationStyle,
    accentColor: Color = CyberEmerald,
    primaryColor: Color = ElectricCyan,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "serverStrip")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stripPhase"
    )

    if (style == ServerAnimationStyle.BITS) {
        val bitsLine1 = remember {
            "0 1 1 0 0 1 1 0 0 1 1 0 1 0 1 1 0 0 0 1 0 1 0 0 0 0 1 0 0 0 0 1 0"
        }
        val bitsLine2 = remember {
            "1 0 0 1 1 0 1 0 1 1 0 0 0 1 0 1 0 0 0 0 1 0 0 0 0 1 0 0 1 0 0 0 1"
        }
        val shift = ((phase * bitsLine1.length).toInt()) % bitsLine1.length
        val shifted1 = (bitsLine1.drop(shift) + " " + bitsLine1.take(shift))
        val shifted2 = (bitsLine2.drop(shift) + " " + bitsLine2.take(shift))

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(26.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.foundation.layout.Column {
                Text(
                    text = shifted1,
                    style = MonoTypography.terminal.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                    color = accentColor.copy(alpha = 0.34f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = shifted2,
                    style = MonoTypography.terminal.copy(fontSize = 9.5.sp, lineHeight = 12.sp),
                    color = accentColor.copy(alpha = 0.20f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
        return
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        when (style) {
            ServerAnimationStyle.CYBER -> {
                val barX = phase * size.width
                drawLine(
                    color = primaryColor.copy(alpha = 0.2f),
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width, size.height * 0.5f),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.7f),
                    radius = 4.dp.toPx(),
                    center = Offset(barX, size.height * 0.5f)
                )
            }
            ServerAnimationStyle.NETWORK -> {
                val nodes = 6
                for (i in 0 until nodes) {
                    val x = (i + 0.5f) * (size.width / nodes)
                    val y = size.height * (0.5f + 0.25f * sin((phase * 6.28f + i).toDouble()).toFloat())
                    drawCircle(
                        color = accentColor.copy(alpha = 0.45f),
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                    if (i > 0) {
                        val px = (i - 0.5f) * (size.width / nodes)
                        val py = size.height * (0.5f + 0.25f * sin((phase * 6.28f + i - 1).toDouble()).toFloat())
                        drawLine(
                            color = primaryColor.copy(alpha = 0.22f),
                            start = Offset(px, py),
                            end = Offset(x, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
            ServerAnimationStyle.LIGHTNING -> {
                val path = Path()
                val segs = 12
                path.moveTo(0f, size.height * 0.5f)
                for (i in 1..segs) {
                    val x = i * (size.width / segs)
                    val offset = sin((phase * 12.56f + i * 1.9f).toDouble()).toFloat() * (size.height * 0.3f)
                    path.lineTo(x, size.height * 0.5f + offset)
                }
                drawPath(path, color = primaryColor.copy(alpha = 0.45f), style = Stroke(width = 1.8.dp.toPx()))
            }
            ServerAnimationStyle.GALAXY, ServerAnimationStyle.PARTICLES -> {
                val auroraPath = Path().apply {
                    moveTo(0f, size.height * 0.65f)
                    for (i in 0..10) {
                        val x = i * (size.width / 10f)
                        val wave = sin((phase * 6.28f + i * 0.7f).toDouble()).toFloat() * (size.height * 0.22f)
                        lineTo(x, size.height * 0.55f + wave)
                    }
                }
                drawPath(
                    path = auroraPath,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            primaryColor.copy(alpha = 0.42f),
                            accentColor.copy(alpha = 0.48f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 1.4.dp.toPx())
                )
                for (i in 0 until 14) {
                    val x = ((i * 0.083f + phase * 0.28f) % 1f) * size.width
                    val y = size.height * (0.2f + 0.6f * abs(cos((i * 1.3f + phase * 3.14f).toDouble()).toFloat()))
                    val starColor = (if (i % 2 == 0) accentColor else primaryColor).copy(alpha = 0.62f)
                    drawCircle(
                        color = starColor,
                        radius = 2.dp.toPx(),
                        center = Offset(x, y)
                    )
                    if (i % 3 == 0) {
                        val arm = 4.dp.toPx()
                        drawLine(
                            color = FrostIce.copy(alpha = 0.45f),
                            start = Offset(x - arm, y),
                            end = Offset(x + arm, y),
                            strokeWidth = 0.8.dp.toPx()
                        )
                        drawLine(
                            color = FrostIce.copy(alpha = 0.45f),
                            start = Offset(x, y - arm),
                            end = Offset(x, y + arm),
                            strokeWidth = 0.8.dp.toPx()
                        )
                    }
                }
            }
            ServerAnimationStyle.BITS -> Unit
        }
    }
}

