package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.model.VybeReaction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

// Signature VYBE Universal Reaction Blue-Purple Palette
val VybePulseBlueStart = Color(0xFF00C6FF)
val VybePulseIndigoMid = Color(0xFF4F46E5)
val VybePulseVioletGlow = Color(0xFF8B5CF6)
val VybePulsePurpleEnd = Color(0xFFA855F7)

// Preserved color aliases mapped to the VYBE Blue-Purple signature palette for full compatibility
val FingerHeartPinkStart = VybePulseIndigoMid
val FingerHeartMagentaMid = VybePulseVioletGlow
val FingerHeartGlowPurple = VybePulsePurpleEnd

private val LightModeOutlineColor = Color(0xFF0F172A)
private val DarkModeOutlineColor = Color(0xFFFFFFFF)

internal enum class ReactionGestureOutcome {
    TAP,
    LONG_PRESS,
    CANCELLED_BY_SCROLL
}

/**
 * Scroll-safe pointer detector:
 * - Returns [ReactionGestureOutcome.CANCELLED_BY_SCROLL] without consuming events as soon as the
 *   pointer moves beyond `touchSlop` or is consumed by a parent scrollable (`LazyColumn` / `Pager`).
 * - Returns [ReactionGestureOutcome.TAP] on a clean press-and-release before `longPressTimeoutMillis`.
 * - Returns [ReactionGestureOutcome.LONG_PRESS] only when held steady for `longPressTimeoutMillis`.
 */
internal suspend fun AwaitPointerEventScope.awaitScrollSafeTapOrLongPress(
    startPosition: Offset,
    pointerId: androidx.compose.ui.input.pointer.PointerId
): ReactionGestureOutcome {
    val slop = viewConfiguration.touchSlop
    val result = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                ?: return@withTimeoutOrNull ReactionGestureOutcome.CANCELLED_BY_SCROLL

            if (change.isConsumed || (change.position - startPosition).getDistance() > slop) {
                return@withTimeoutOrNull ReactionGestureOutcome.CANCELLED_BY_SCROLL
            }
            if (!change.pressed) {
                change.consume()
                return@withTimeoutOrNull ReactionGestureOutcome.TAP
            }
        }
        @Suppress("UNREACHABLE_CODE")
        ReactionGestureOutcome.CANCELLED_BY_SCROLL
    }
    return result ?: ReactionGestureOutcome.LONG_PRESS
}

private data class MiniVybePulseParticle(
    val id: Int,
    val driftDp: Float,
    val riseDp: Float,
    val initialDelayMs: Long,
    val sizeDp: Float
)

/**
 * Proprietary VYBE Universal Reaction Icon:
 * Sculpted geometric V-shaped heart crest converging into a sharp VYBE "V" apex,
 * fused with an integrated horizontal-to-spike electric energy-pulse waveform.
 * Supports small (15dp) and large (24dp–96dp) sizes in both Light and Dark themes.
 */
@Composable
fun VybeUniversalReactionIcon(
    isActive: Boolean,
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
        drawVybeUniversalReactionVector(
            isActive = isActive,
            inactiveStrokeColor = inactiveStrokeColor
        )
    }
}

/**
 * Global icon entry-point delegating directly to [VybeUniversalReactionIcon]
 * so every screen in VYBE renders the proprietary V-Heart + Energy Pulse vector.
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
    VybeUniversalReactionIcon(
        isActive = isLiked,
        modifier = modifier,
        size = size,
        forceDarkSurface = forceDarkSurface,
        inactiveTintOverride = inactiveTintOverride,
        contentDescriptionText = contentDescriptionText
    )
}

private fun DrawScope.drawVybeUniversalReactionVector(
    isActive: Boolean,
    inactiveStrokeColor: Color
) {
    val w = size.width
    val h = size.height
    val strokePx = (w * 0.082f).coerceAtLeast(1.65f)
    val pulseStrokePx = (w * 0.074f).coerceAtLeast(1.45f)

    // Subtle blue-purple radial aura glow when active
    if (isActive) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    VybePulseBlueStart.copy(alpha = 0.28f),
                    VybePulseVioletGlow.copy(alpha = 0.26f),
                    VybePulsePurpleEnd.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = Offset(w * 0.50f, h * 0.48f),
                radius = w * 0.62f
            ),
            radius = w * 0.62f,
            center = Offset(w * 0.50f, h * 0.48f)
        )
    }

    // 1. Proprietary Sculpted V-Shaped Heart Crest:
    // Sharp VYBE central V-dip at top + aerodynamic upper wings + crisp straight V-blade lower edges
    val vHeartCrestPath = Path().apply {
        // Bottom sharp "V" apex
        moveTo(w * 0.50f, h * 0.90f)
        // Crisp lower-left "V" blade up to outer left wing
        lineTo(w * 0.12f, h * 0.43f)
        // Sculpted upper-left crest curve
        cubicTo(
            w * 0.06f, h * 0.29f,
            w * 0.14f, h * 0.12f,
            w * 0.30f, h * 0.12f
        )
        // Angled V-shoulder into central V-notch
        cubicTo(
            w * 0.39f, h * 0.12f,
            w * 0.45f, h * 0.18f,
            w * 0.50f, h * 0.28f
        )
        // Central V-notch rising to right V-shoulder
        cubicTo(
            w * 0.55f, h * 0.18f,
            w * 0.61f, h * 0.12f,
            w * 0.70f, h * 0.12f
        )
        // Sculpted upper-right crest curve
        cubicTo(
            w * 0.86f, h * 0.12f,
            w * 0.94f, h * 0.29f,
            w * 0.88f, h * 0.43f
        )
        // Crisp lower-right "V" blade back to apex
        close()
    }

    // 2. Signature VYBE Energy-Pulse Waveform spanning across the V-Heart core
    val energyPulsePath = Path().apply {
        moveTo(w * 0.20f, h * 0.48f)
        lineTo(w * 0.35f, h * 0.48f)
        lineTo(w * 0.43f, h * 0.31f)
        lineTo(w * 0.52f, h * 0.64f)
        lineTo(w * 0.60f, h * 0.39f)
        lineTo(w * 0.66f, h * 0.48f)
        lineTo(w * 0.80f, h * 0.48f)
    }

    val activeFillBrush = Brush.linearGradient(
        colors = listOf(
            VybePulseBlueStart,
            VybePulseIndigoMid,
            VybePulseVioletGlow,
            VybePulsePurpleEnd
        ),
        start = Offset(w * 0.12f, h * 0.10f),
        end = Offset(w * 0.88f, h * 0.90f)
    )

    val activeBorderBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF7DD3FC),
            VybePulseVioletGlow,
            Color(0xFFD8B4FE)
        ),
        start = Offset(0f, 0f),
        end = Offset(w, h)
    )

    if (isActive) {
        drawPath(path = vHeartCrestPath, brush = activeFillBrush, style = Fill)
        drawPath(
            path = vHeartCrestPath,
            brush = activeBorderBrush,
            style = Stroke(width = strokePx * 0.55f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Subtle blue-cyan inner glow behind the energy pulse trace
        drawPath(
            path = energyPulsePath,
            color = VybePulseBlueStart.copy(alpha = 0.45f),
            style = Stroke(width = pulseStrokePx * 1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // Crisp luminous white-cyan energy-pulse waveform inside the active V-Heart
        drawPath(
            path = energyPulsePath,
            color = Color(0xFFF8FAFC),
            style = Stroke(width = pulseStrokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    } else {
        drawPath(
            path = vHeartCrestPath,
            color = inactiveStrokeColor,
            style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = energyPulsePath,
            color = inactiveStrokeColor.copy(alpha = 0.92f),
            style = Stroke(width = pulseStrokePx * 0.90f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * Alias for VYBE Universal Reaction with full single-tap, press-hold-drag-release selector,
 * scroll-cancellation protection, haptic feedback, and blue-purple energy pulse animation.
 */
@Composable
fun VybeUniversalReaction(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    userReaction: VybeReaction? = null,
    onReact: ((VybeReaction) -> Unit)? = null,
    count: Int? = null,
    showZeroCount: Boolean = false,
    iconSize: Dp = 23.dp,
    forceDarkSurface: Boolean = false,
    inactiveTintOverride: Color? = null,
    countColorOverride: Color? = null,
    compactPadding: Boolean = false,
    testTag: String? = null,
    contentDescriptionText: String = if (isLiked) "Remove VYBE reaction" else "Send VYBE reaction"
) {
    FingerHeartReaction(
        isLiked = isLiked,
        onClick = onClick,
        modifier = modifier,
        userReaction = userReaction,
        onReact = onReact,
        count = count,
        showZeroCount = showZeroCount,
        iconSize = iconSize,
        forceDarkSurface = forceDarkSurface,
        inactiveTintOverride = inactiveTintOverride,
        countColorOverride = countColorOverride,
        compactPadding = compactPadding,
        testTag = testTag,
        contentDescriptionText = contentDescriptionText
    )
}

/**
 * Reusable Global VYBE Universal Reaction component:
 * - Single tap applies/toggles default VYBE reaction (V-Heart + Energy Pulse)
 * - Press & hold opens floating 5-reaction selector; drag horizontally & release to apply
 * - Scroll-safe: immediately aborts without triggering if finger scrolls beyond `touchSlop`
 * - Blue-purple energy pulse ring + upward-floating mini V-Pulse sparks + haptic feedback
 * - High-contrast visibility in Dark and Light modes
 */
@Composable
fun FingerHeartReaction(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    userReaction: VybeReaction? = null,
    onReact: ((VybeReaction) -> Unit)? = null,
    count: Int? = null,
    showZeroCount: Boolean = false,
    iconSize: Dp = 23.dp,
    forceDarkSurface: Boolean = false,
    inactiveTintOverride: Color? = null,
    countColorOverride: Color? = null,
    compactPadding: Boolean = false,
    testTag: String? = null,
    contentDescriptionText: String = if (isLiked) "Remove VYBE reaction" else "Send VYBE reaction"
) {
    val haptic = LocalHapticFeedback.current
    val densityObj = LocalDensity.current
    val scope = rememberCoroutineScope()

    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val pulseRing = remember { Animatable(0f) }
    val particles = remember { mutableStateListOf<MiniVybePulseParticle>() }
    var particleSeed by remember { mutableIntStateOf(0) }

    var isSelectorVisible by remember { mutableStateOf(false) }
    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    val reactions = remember { VybeReaction.values() }

    val effectiveReaction = userReaction ?: if (isLiked) VybeReaction.LOVE else null
    val isActive = effectiveReaction != null

    val currentIsActive by rememberUpdatedState(isActive)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnReact by rememberUpdatedState(onReact)

    val isLightTheme = !forceDarkSurface && MaterialTheme.colorScheme.background.luminance() > 0.5f
    val defaultTextColor = if (forceDarkSurface || !isLightTheme) {
        Color(0xFFE2E8F0)
    } else {
        Color(0xFF0F172A)
    }
    val activeAccentColor = if (effectiveReaction != null) {
        reactionColor(effectiveReaction)
    } else {
        VybePulseVioletGlow
    }
    val resolvedCountColor = when {
        isActive -> activeAccentColor
        countColorOverride != null -> countColorOverride
        else -> defaultTextColor
    }

    val playReactionAnimation: (Boolean) -> Unit = { becomingActive ->
        scope.launch {
            if (becomingActive) {
                val baseId = particleSeed + 1
                particleSeed += 4
                val burst = listOf(
                    MiniVybePulseParticle(baseId, driftDp = -12f, riseDp = -30f, initialDelayMs = 0L, sizeDp = 11f),
                    MiniVybePulseParticle(baseId + 1, driftDp = 11f, riseDp = -34f, initialDelayMs = 35L, sizeDp = 12f),
                    MiniVybePulseParticle(baseId + 2, driftDp = -5f, riseDp = -40f, initialDelayMs = 70L, sizeDp = 9.5f),
                    MiniVybePulseParticle(baseId + 3, driftDp = 15f, riseDp = -26f, initialDelayMs = 50L, sizeDp = 10f)
                )
                particles.addAll(burst)
                launch {
                    delay(460L)
                    particles.removeAll(burst.toSet())
                }
                launch {
                    pulseRing.snapTo(0.05f)
                    pulseRing.animateTo(1f, animationSpec = tween(420, easing = FastOutSlowInEasing))
                    pulseRing.snapTo(0f)
                }
                launch {
                    rotation.animateTo(-9f, animationSpec = tween(115, easing = FastOutSlowInEasing))
                    rotation.animateTo(5f, animationSpec = tween(115, easing = FastOutSlowInEasing))
                    rotation.animateTo(0f, animationSpec = tween(130, easing = FastOutSlowInEasing))
                }
                scale.animateTo(1.34f, animationSpec = tween(140, easing = FastOutSlowInEasing))
                scale.animateTo(
                    targetValue = 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            } else {
                rotation.snapTo(0f)
                scale.animateTo(0.86f, animationSpec = tween(110, easing = FastOutSlowInEasing))
                scale.animateTo(1.0f, animationSpec = tween(160, easing = FastOutSlowInEasing))
            }
        }
    }

    val triggerSingleTap: () -> Unit = {
        val becomingActive = !currentIsActive
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnClick()
        playReactionAnimation(becomingActive)
    }

    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        contentAlignment = Alignment.Center,
        modifier = taggedModifier
    ) {
        // Floating Press-Hold-Drag-Release Reaction Selector
        if (isSelectorVisible) {
            val popupOffset = with(densityObj) {
                IntOffset(x = (-12).dp.roundToPx(), y = (-58).dp.roundToPx())
            }
            Popup(
                alignment = Alignment.TopStart,
                offset = popupOffset,
                properties = PopupProperties(
                    focusable = false,
                    clippingEnabled = false
                )
            ) {
                Surface(
                    color = if (isLightTheme) Color(0xFFF8FAFC).copy(alpha = 0.98f) else Color(0xFF0B1022).copy(alpha = 0.98f),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                VybePulseBlueStart.copy(alpha = 0.65f),
                                VybePulseVioletGlow.copy(alpha = 0.65f)
                            )
                        )
                    ),
                    shadowElevation = 18.dp,
                    modifier = Modifier.testTag(
                        if (testTag != null) "${testTag}_selector" else "vybe_universal_reaction_selector"
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        reactions.forEachIndexed { index, rx ->
                            val isHovered = hoveredIndex == index
                            val isCurrent = effectiveReaction == rx
                            val rColor = reactionColor(rx)
                            val itemScale by animateFloatAsState(
                                targetValue = when {
                                    isHovered -> 1.36f
                                    isCurrent -> 1.12f
                                    else -> 1f
                                },
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "universal_rx_scale_${rx.name}"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .scale(itemScale)
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isHovered -> rColor.copy(alpha = 0.28f)
                                                isCurrent -> rColor.copy(alpha = 0.16f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .testTag("universal_reaction_option_${rx.name}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (rx == VybeReaction.LOVE) {
                                        VybeUniversalReactionIcon(
                                            isActive = true,
                                            size = 23.dp,
                                            forceDarkSurface = !isLightTheme
                                        )
                                    } else {
                                        Text(
                                            text = rx.emoji,
                                            fontSize = 20.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Expanding blue-purple energy pulse ring on tap
        val ringVal = pulseRing.value
        if (ringVal > 0f && ringVal < 1f) {
            Canvas(
                modifier = Modifier.size(iconSize * 2.1f)
            ) {
                val ringRadius = (size.minDimension * 0.24f) + (size.minDimension * 0.26f * ringVal)
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(
                            VybePulseBlueStart.copy(alpha = (1f - ringVal) * 0.7f),
                            VybePulseVioletGlow.copy(alpha = (1f - ringVal) * 0.7f),
                            VybePulsePurpleEnd.copy(alpha = (1f - ringVal) * 0.7f),
                            VybePulseBlueStart.copy(alpha = (1f - ringVal) * 0.7f)
                        )
                    ),
                    radius = ringRadius,
                    style = Stroke(width = (2.2f * (1f - ringVal * 0.5f)).coerceAtLeast(0.8f))
                )
            }
        }

        // Floating mini V-Pulse particles on activation
        particles.forEach { particle ->
            FloatingMiniVybePulse(particle = particle)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compactPadding) 4.dp else 6.dp),
            modifier = Modifier
                .defaultMinSize(
                    minWidth = if (compactPadding) 36.dp else 48.dp,
                    minHeight = if (compactPadding) 36.dp else 48.dp
                )
                .semantics {
                    role = Role.Button
                    contentDescription = contentDescriptionText
                    onClick {
                        triggerSingleTap()
                        true
                    }
                    if (currentOnReact != null) {
                        onLongClick {
                            isSelectorVisible = true
                            hoveredIndex = 0
                            true
                        }
                    }
                }
                .pointerInput(compactPadding) {
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            hoveredIndex = null

                            when (awaitScrollSafeTapOrLongPress(down.position, down.id)) {
                                ReactionGestureOutcome.CANCELLED_BY_SCROLL -> {
                                    // Cleanly abort so scrolling Feed/Reels/Comments never fires accidental reactions
                                    isSelectorVisible = false
                                    hoveredIndex = null
                                }

                                ReactionGestureOutcome.TAP -> {
                                    triggerSingleTap()
                                }

                                ReactionGestureOutcome.LONG_PRESS -> {
                                    val reactCallback = currentOnReact
                                    if (reactCallback == null) {
                                        // Wait for finger release if no multi-reaction callback was supplied
                                        val slop = viewConfiguration.touchSlop
                                        var scrolled = false
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: ev.changes.firstOrNull() ?: break
                                            if ((ch.position - down.position).getDistance() > slop || ch.isConsumed) {
                                                scrolled = true
                                                break
                                            }
                                            if (!ch.pressed) {
                                                ch.consume()
                                                break
                                            }
                                        }
                                        if (!scrolled) {
                                            triggerSingleTap()
                                        }
                                    } else {
                                        isSelectorVisible = true
                                        hoveredIndex = 0
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                                        val barStartX = -16f * density
                                        val barEndX = 224f * density
                                        val cancelMinY = -105f * density
                                        val cancelMaxY = 72f * density

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: event.changes.firstOrNull() ?: break

                                            if (!change.pressed) {
                                                val chosenIdx = hoveredIndex
                                                if (chosenIdx != null && chosenIdx in reactions.indices) {
                                                    val chosenRx = reactions[chosenIdx]
                                                    reactCallback(chosenRx)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    playReactionAnimation(effectiveReaction != chosenRx)
                                                }
                                                isSelectorVisible = false
                                                hoveredIndex = null
                                                change.consume()
                                                break
                                            }

                                            val cx = change.position.x
                                            val cy = change.position.y
                                            if (cy in cancelMinY..cancelMaxY && cx in (barStartX - 24f * density)..(barEndX + 24f * density)) {
                                                val progress = ((cx - barStartX) / (barEndX - barStartX)).coerceIn(0f, 0.999f)
                                                val nextIdx = (progress * reactions.size).toInt().coerceIn(reactions.indices)
                                                if (nextIdx != hoveredIndex) {
                                                    hoveredIndex = nextIdx
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            } else {
                                                hoveredIndex = null
                                            }
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        } finally {
                            isSelectorVisible = false
                            hoveredIndex = null
                        }
                    }
                }
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
                if (effectiveReaction != null && effectiveReaction != VybeReaction.LOVE) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .semantics { contentDescription = "${effectiveReaction.label} reaction" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = effectiveReaction.emoji,
                            fontSize = (iconSize.value * 0.84f).sp,
                            lineHeight = (iconSize.value * 0.84f).sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    VybeUniversalReactionIcon(
                        isActive = isActive,
                        size = iconSize,
                        forceDarkSurface = forceDarkSurface,
                        inactiveTintOverride = inactiveTintOverride,
                        contentDescriptionText = contentDescriptionText
                    )
                }
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
                    label = "vybe_universal_reaction_count"
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
private fun FloatingMiniVybePulse(particle: MiniVybePulseParticle) {
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
            VybeUniversalReactionIcon(
                isActive = true,
                size = particle.sizeDp.dp,
                forceDarkSurface = true
            )
        }
    }
}

/**
 * Large central VYBE Universal Reaction double-tap overlay for Home Feed and Reels.
 */
@Composable
fun FingerHeartDoubleTapOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    burstKey: Any = visible
) {
    if (!visible) return

    val mainScale = remember(burstKey) { Animatable(0.35f) }
    val mainRotation = remember(burstKey) { Animatable(-10f) }
    val mainAlpha = remember(burstKey) { Animatable(0f) }
    val particleProgress = remember(burstKey) { Animatable(0f) }

    LaunchedEffect(burstKey) {
        launch {
            mainAlpha.animateTo(1f, tween(90))
            delay(340)
            mainAlpha.animateTo(0f, tween(210))
        }
        launch {
            mainRotation.animateTo(5f, tween(160, easing = FastOutSlowInEasing))
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
        // Expanding blue-purple pulse halo
        Canvas(modifier = Modifier.size(156.dp)) {
            val haloAlpha = (1f - p).coerceIn(0f, 1f) * 0.6f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VybePulseBlueStart.copy(alpha = haloAlpha),
                        VybePulseVioletGlow.copy(alpha = haloAlpha * 0.65f),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension * (0.28f + 0.22f * p)
            )
        }

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
                VybeUniversalReactionIcon(
                    isActive = true,
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
            VybeUniversalReactionIcon(
                isActive = true,
                size = 96.dp,
                forceDarkSurface = true,
                contentDescriptionText = "VYBE Universal Reaction burst"
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
