package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val FingerHeartPinkStart = Color(0xFFFF2E93)
val FingerHeartMagentaMid = Color(0xFFEC4899)
val FingerHeartGlowPurple = Color(0xFFD946EF)
private val LightModeOutlineColor = Color(0xFF0F172A)
private val DarkModeOutlineColor = Color(0xFFFFFFFF)

private data class MiniFingerHeartParticle(
    val id: Int,
    val driftDp: Float,
    val riseDp: Float,
    val initialDelayMs: Long,
    val sizeDp: Float
)

/**
 * Draws the custom VYBE Finger Heart icon (crossed thumb & index finger + crown heart)
 * using smooth rounded strokes and Instagram-grade proportions.
 */
@Composable
fun FingerHeartIcon(
    isLiked: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    forceDarkSurface: Boolean = false,
    inactiveTintOverride: Color? = null,
    contentDescriptionText: String? = null
) {
    val isLightTheme = !forceDarkSurface && MaterialTheme.colorScheme.background.luminance() > 0.5f
    val inactiveStrokeColor = inactiveTintOverride ?: if (isLightTheme) {
        LightModeOutlineColor
    } else {
        DarkModeOutlineColor
    }

    val semanticsModifier = if (contentDescriptionText != null) {
        modifier.semantics { contentDescription = contentDescriptionText }
    } else {
        modifier
    }

    Canvas(modifier = semanticsModifier.size(size)) {
        drawFingerHeartVector(
            isLiked = isLiked,
            inactiveStrokeColor = inactiveStrokeColor
        )
    }
}

private fun DrawScope.drawFingerHeartVector(
    isLiked: Boolean,
    inactiveStrokeColor: Color
) {
    val w = size.width
    val h = size.height
    val strokePx = (w * 0.082f).coerceAtLeast(1.7f)

    // Subtle radial pink/magenta glow behind active Finger Heart
    if (isLiked) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    FingerHeartPinkStart.copy(alpha = 0.34f),
                    FingerHeartGlowPurple.copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.44f),
                radius = w * 0.58f
            ),
            radius = w * 0.58f,
            center = Offset(w * 0.50f, h * 0.44f)
        )
    }

    // 1. Top Heart nestled right above crossed fingertips
    val heartPath = Path().apply {
        moveTo(w * 0.50f, h * 0.40f)
        cubicTo(
            w * 0.30f, h * 0.28f,
            w * 0.22f, h * 0.14f,
            w * 0.35f, h * 0.08f
        )
        cubicTo(
            w * 0.43f, h * 0.04f,
            w * 0.48f, h * 0.10f,
            w * 0.50f, h * 0.15f
        )
        cubicTo(
            w * 0.52f, h * 0.10f,
            w * 0.57f, h * 0.04f,
            w * 0.65f, h * 0.08f
        )
        cubicTo(
            w * 0.78f, h * 0.14f,
            w * 0.70f, h * 0.28f,
            w * 0.50f, h * 0.40f
        )
        close()
    }

    // 2. Crossed Index Finger + Thumb + Sleek Hand Base silhouette
    val fingersPath = Path().apply {
        // Left-to-right slanted thumb crossing forward
        moveTo(w * 0.27f, h * 0.66f)
        cubicTo(
            w * 0.26f, h * 0.55f,
            w * 0.39f, h * 0.45f,
            w * 0.57f, h * 0.44f
        )
        cubicTo(
            w * 0.68f, h * 0.44f,
            w * 0.73f, h * 0.51f,
            w * 0.67f, h * 0.58f
        )
        // Right folded fingers & palm contour
        cubicTo(
            w * 0.75f, h * 0.61f,
            w * 0.77f, h * 0.72f,
            w * 0.71f, h * 0.82f
        )
        cubicTo(
            w * 0.66f, h * 0.90f,
            w * 0.57f, h * 0.93f,
            w * 0.47f, h * 0.93f
        )
        cubicTo(
            w * 0.35f, h * 0.93f,
            w * 0.26f, h * 0.85f,
            w * 0.26f, h * 0.73f
        )
        close()
    }

    // Index fingertip rising behind the thumb to form the V-cross
    val indexTipPath = Path().apply {
        moveTo(w * 0.36f, h * 0.56f)
        cubicTo(
            w * 0.35f, h * 0.45f,
            w * 0.41f, h * 0.39f,
            w * 0.49f, h * 0.42f
        )
        cubicTo(
            w * 0.53f, h * 0.44f,
            w * 0.55f, h * 0.48f,
            w * 0.53f, h * 0.53f
        )
    }

    // Subtle interior thumb-overlap crease line for crisp Finger Heart recognition
    val thumbCreasePath = Path().apply {
        moveTo(w * 0.37f, h * 0.63f)
        cubicTo(
            w * 0.46f, h * 0.58f,
            w * 0.56f, h * 0.57f,
            w * 0.66f, h * 0.58f
        )
    }

    val activeBrush = Brush.linearGradient(
        colors = listOf(FingerHeartPinkStart, FingerHeartMagentaMid, FingerHeartGlowPurple),
        start = Offset(w * 0.2f, h * 0.08f),
        end = Offset(w * 0.8f, h * 0.94f)
    )

    if (isLiked) {
        drawPath(path = indexTipPath, brush = activeBrush, style = Fill)
        drawPath(
            path = indexTipPath,
            brush = activeBrush,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(path = fingersPath, brush = activeBrush, style = Fill)
        drawPath(path = heartPath, brush = activeBrush, style = Fill)
        // Crisp luminous inner separation line so the crossed fingers stay recognizable when filled
        drawPath(
            path = thumbCreasePath,
            color = Color.White.copy(alpha = 0.68f),
            style = Stroke(width = strokePx * 0.68f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } else {
        val outlineStroke = Stroke(
            width = strokePx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawPath(path = heartPath, color = inactiveStrokeColor, style = outlineStroke)
        drawPath(path = indexTipPath, color = inactiveStrokeColor, style = outlineStroke)
        drawPath(path = fingersPath, color = inactiveStrokeColor, style = outlineStroke)
        drawPath(
            path = thumbCreasePath,
            color = inactiveStrokeColor.copy(alpha = 0.85f),
            style = Stroke(width = strokePx * 0.78f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Reusable Global VYBE Finger Heart Reaction button with:
 * - Scale 1.0 -> 1.35 + smooth spring bounce + slight rotation (350-450ms)
 * - Haptic feedback
 * - 4 upward-floating mini Finger Heart particles with horizontal drift & auto-cleanup
 * - Smooth animated count transition
 * - Theme-aware Light/Dark outline when inactive & pink/magenta glow when active
 */
@Composable
fun FingerHeartReaction(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    showZeroCount: Boolean = false,
    iconSize: Dp = 23.dp,
    forceDarkSurface: Boolean = false,
    inactiveTintOverride: Color? = null,
    countColorOverride: Color? = null,
    compactPadding: Boolean = false,
    testTag: String? = null,
    contentDescriptionText: String = if (isLiked) "Remove Finger Heart reaction" else "Send Finger Heart reaction"
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val particles = remember { mutableStateListOf<MiniFingerHeartParticle>() }
    var particleSeed by remember { mutableIntStateOf(0) }

    val isLightTheme = !forceDarkSurface && MaterialTheme.colorScheme.background.luminance() > 0.5f
    val defaultTextColor = if (forceDarkSurface || !isLightTheme) {
        Color(0xFFE2E8F0)
    } else {
        Color(0xFF0F172A)
    }
    val resolvedCountColor = when {
        isLiked -> FingerHeartPinkStart
        countColorOverride != null -> countColorOverride
        else -> defaultTextColor
    }

    val triggerTapReaction: () -> Unit = {
        val becomingLiked = !isLiked
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()

        scope.launch {
            if (becomingLiked) {
                val baseId = particleSeed + 1
                particleSeed += 4
                val burst = listOf(
                    MiniFingerHeartParticle(baseId, driftDp = -12f, riseDp = -30f, initialDelayMs = 0L, sizeDp = 11f),
                    MiniFingerHeartParticle(baseId + 1, driftDp = 11f, riseDp = -34f, initialDelayMs = 35L, sizeDp = 12f),
                    MiniFingerHeartParticle(baseId + 2, driftDp = -5f, riseDp = -40f, initialDelayMs = 70L, sizeDp = 9.5f),
                    MiniFingerHeartParticle(baseId + 3, driftDp = 15f, riseDp = -26f, initialDelayMs = 50L, sizeDp = 10f)
                )
                particles.addAll(burst)
                launch {
                    delay(460L)
                    particles.removeAll(burst.toSet())
                }

                // Scale 1.0 -> 1.35 + slight rotation + smooth settle to 1.0 (~400ms total)
                launch {
                    rotation.animateTo(-10f, animationSpec = tween(120, easing = FastOutSlowInEasing))
                    rotation.animateTo(5f, animationSpec = tween(120, easing = FastOutSlowInEasing))
                    rotation.animateTo(0f, animationSpec = tween(140, easing = FastOutSlowInEasing))
                }
                scale.animateTo(1.35f, animationSpec = tween(145, easing = FastOutSlowInEasing))
                scale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            } else {
                // Smooth reverse transition on unlike
                rotation.snapTo(0f)
                scale.animateTo(0.86f, animationSpec = tween(110, easing = FastOutSlowInEasing))
                scale.animateTo(1.0f, animationSpec = tween(160, easing = FastOutSlowInEasing))
            }
        }
    }

    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        contentAlignment = Alignment.Center,
        modifier = taggedModifier
    ) {
        // Floating mini Finger Heart particles on activation
        particles.forEach { particle ->
            FloatingMiniFingerHeart(particle = particle)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactPadding) 4.dp else 6.dp),
            modifier = Modifier
                .defaultMinSize(
                    minWidth = if (compactPadding) 36.dp else 48.dp,
                    minHeight = if (compactPadding) 36.dp else 48.dp
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = triggerTapReaction
                )
                .padding(
                    horizontal = if (compactPadding) 4.dp else 6.dp,
                    vertical = if (compactPadding) 4.dp else 6.dp
                )
        ) {
            Box(
                modifier = Modifier.graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = rotation.value
                },
                contentAlignment = Alignment.Center
            ) {
                FingerHeartIcon(
                    isLiked = isLiked,
                    size = iconSize,
                    forceDarkSurface = forceDarkSurface,
                    inactiveTintOverride = inactiveTintOverride,
                    contentDescriptionText = contentDescriptionText
                )
            }

            if (count != null && (count > 0 || showZeroCount)) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { height -> height / 2 } + fadeIn(tween(160)))
                                .togetherWith(slideOutVertically { height -> -height / 2 } + fadeOut(tween(120)))
                        } else {
                            (slideInVertically { height -> -height / 2 } + fadeIn(tween(160)))
                                .togetherWith(slideOutVertically { height -> height / 2 } + fadeOut(tween(120)))
                        }
                    },
                    label = "finger_heart_count"
                ) { animatedCount ->
                    Text(
                        text = formatCompactReactionCount(animatedCount),
                        color = resolvedCountColor,
                        fontSize = if (compactPadding) 12.sp else 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingMiniFingerHeart(particle: MiniFingerHeartParticle) {
    val progress = remember(particle.id) { Animatable(0f) }

    LaunchedEffect(particle.id) {
        if (particle.initialDelayMs > 0) {
            delay(particle.initialDelayMs)
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 390, easing = FastOutSlowInEasing)
        )
    }

    val p = progress.value
    if (p > 0f && p < 1f) {
        val alpha = if (p < 0.25f) (p / 0.25f) else (1f - ((p - 0.25f) / 0.75f)).coerceIn(0f, 1f)
        val scale = (0.55f + p * 0.65f).coerceAtMost(1.15f)
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (particle.driftDp * p * density).roundToInt(),
                        y = (particle.riseDp * p * density).roundToInt()
                    )
                }
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                }
        ) {
            FingerHeartIcon(
                isLiked = true,
                size = particle.sizeDp.dp,
                forceDarkSurface = true
            )
        }
    }
}

/**
 * Large central Finger Heart double-tap overlay for Home Feed and Reels.
 */
@Composable
fun FingerHeartDoubleTapOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    burstKey: Any = visible
) {
    if (!visible) return

    val mainScale = remember(burstKey) { Animatable(0.35f) }
    val mainRotation = remember(burstKey) { Animatable(-12f) }
    val mainAlpha = remember(burstKey) { Animatable(0f) }
    val particleProgress = remember(burstKey) { Animatable(0f) }

    LaunchedEffect(burstKey) {
        launch {
            mainAlpha.animateTo(1f, tween(90))
            delay(340)
            mainAlpha.animateTo(0f, tween(210))
        }
        launch {
            mainRotation.animateTo(6f, tween(160, easing = FastOutSlowInEasing))
            mainRotation.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
        }
        launch {
            particleProgress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        }
        mainScale.animateTo(
            targetValue = 1.18f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        mainScale.animateTo(1.0f, tween(140))
    }

    val burstOffsets = remember {
        listOf(
            Offset(-44f, -54f),
            Offset(46f, -50f),
            Offset(-56f, -12f),
            Offset(58f, -10f),
            Offset(-24f, -72f),
            Offset(28f, -68f)
        )
    }

    Box(
        modifier = modifier.testTag("finger_heart_double_tap_overlay"),
        contentAlignment = Alignment.Center
    ) {
        val p = particleProgress.value
        burstOffsets.forEachIndexed { idx, target ->
            val particleAlpha = (1f - p).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (target.x * p * density).roundToInt(),
                            y = (target.y * p * density).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        alpha = particleAlpha
                        val s = 0.55f + (0.55f * p)
                        scaleX = s
                        scaleY = s
                        rotationZ = if (idx % 2 == 0) -12f * p else 12f * p
                    }
            ) {
                FingerHeartIcon(
                    isLiked = true,
                    size = 20.dp,
                    forceDarkSurface = true
                )
            }
        }

        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = mainScale.value
                scaleY = mainScale.value
                rotationZ = mainRotation.value
                alpha = mainAlpha.value
            }
        ) {
            FingerHeartIcon(
                isLiked = true,
                size = 96.dp,
                forceDarkSurface = true,
                contentDescriptionText = "Finger Heart reaction burst"
            )
        }
    }
}

fun formatCompactReactionCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000f).replace(".0M", "M")
        count >= 1_000 -> String.format("%.1fK", count / 1_000f).replace(".0K", "K")
        else -> count.toString()
    }
}
