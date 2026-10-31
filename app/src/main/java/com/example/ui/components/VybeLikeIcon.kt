package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/**
 * Unique, original VYBE Like / Resonance symbol (Section 16).
 *
 * Replaces conventional heart icons with VYBE's custom "Resonance Pulse-V Spark":
 * a crisp geometric V-prism crest crowned by an energy diamond spark that lights up
 * with VYBE's signature Electric Cyan -> Neon Violet -> Vivid Magenta gradient when active.
 */
@Composable
fun VybeLikeIcon(
  isLiked: Boolean,
  modifier: Modifier = Modifier,
  size: Dp = 24.dp,
  inactiveTint: Color = Color.Unspecified,
  activeTintOverride: Color? = null,
  contentDescriptionText: String? = if (isLiked) "Liked vibe" else "Like vibe"
) {
  val scale = remember { Animatable(1f) }

  LaunchedEffect(isLiked) {
    if (isLiked) {
      scale.snapTo(0.82f)
      scale.animateTo(
        targetValue = 1.18f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioMediumBouncy,
          stiffness = Spring.StiffnessMediumLow
        )
      )
      scale.animateTo(
        targetValue = 1f,
        animationSpec = spring(
          dampingRatio = Spring.DampingRatioNoBouncy,
          stiffness = Spring.StiffnessMedium
        )
      )
    } else {
      scale.snapTo(1f)
    }
  }

  val resolvedInactive = if (inactiveTint != Color.Unspecified) {
    inactiveTint
  } else {
    androidx.compose.material3.LocalContentColor.current
  }

  val semModifier = if (contentDescriptionText != null) {
    modifier.semantics { contentDescription = contentDescriptionText }
  } else {
    modifier
  }

  Canvas(
    modifier = semModifier
      .size(size)
      .graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
      }
  ) {
    val w = this.size.width
    val h = this.size.height
    val strokeW = (w * 0.082f).coerceIn(1.8f, 3.2f)

    // 1. Two-finger crossed hand outline (index finger & thumb forming the heart gesture)
    val handPath = Path().apply {
      // Start at lower left wrist
      moveTo(w * 0.32f, h * 0.92f)
      // Left edge of fist (curled pinky and ring fingers)
      cubicTo(w * 0.20f, h * 0.82f, w * 0.18f, h * 0.65f, w * 0.24f, h * 0.54f)
      // Curled middle finger knuckle
      cubicTo(w * 0.28f, h * 0.46f, w * 0.36f, h * 0.44f, w * 0.42f, h * 0.46f)
      // Thumb crossing over toward right
      cubicTo(w * 0.42f, h * 0.38f, w * 0.44f, h * 0.32f, w * 0.48f, h * 0.30f)
      // Index finger extending upwards to tip
      cubicTo(w * 0.52f, h * 0.24f, w * 0.58f, h * 0.24f, w * 0.62f, h * 0.30f)
      // Down index finger outer edge
      cubicTo(w * 0.68f, h * 0.40f, w * 0.76f, h * 0.46f, w * 0.80f, h * 0.56f)
      // Right edge of hand / wrist
      cubicTo(w * 0.84f, h * 0.68f, w * 0.80f, h * 0.84f, w * 0.68f, h * 0.92f)
      close()
    }

    // Folded finger crease lines
    val crease1 = Path().apply {
      moveTo(w * 0.28f, h * 0.64f)
      cubicTo(w * 0.36f, h * 0.66f, w * 0.44f, h * 0.66f, w * 0.50f, h * 0.62f)
    }
    val crease2 = Path().apply {
      moveTo(w * 0.24f, h * 0.74f)
      cubicTo(w * 0.34f, h * 0.76f, w * 0.42f, h * 0.75f, w * 0.48f, h * 0.71f)
    }

    // Thumb-index cross definition line
    val thumbCrossLine = Path().apply {
      moveTo(w * 0.46f, h * 0.34f)
      cubicTo(w * 0.54f, h * 0.36f, w * 0.58f, h * 0.42f, w * 0.58f, h * 0.50f)
    }

    // 2. Mini Heart formed at fingertips (hovering at top)
    val heartPath = Path().apply {
      // Bottom tip of heart
      moveTo(w * 0.55f, h * 0.24f)
      // Left lobe
      cubicTo(w * 0.45f, h * 0.17f, w * 0.40f, h * 0.08f, w * 0.48f, h * 0.05f)
      cubicTo(w * 0.52f, h * 0.03f, w * 0.55f, h * 0.09f, w * 0.55f, h * 0.12f)
      // Right lobe
      cubicTo(w * 0.55f, h * 0.09f, w * 0.58f, h * 0.03f, w * 0.62f, h * 0.05f)
      cubicTo(w * 0.70f, h * 0.08f, w * 0.65f, h * 0.17f, w * 0.55f, h * 0.24f)
      close()
    }

    val outlineColor = if (inactiveTint != Color.Unspecified) inactiveTint else Color.White

    if (isLiked) {
      val heartBrush = if (activeTintOverride != null) {
        Brush.linearGradient(listOf(activeTintOverride, activeTintOverride))
      } else {
        Brush.linearGradient(
          colors = listOf(
            Color(0xFFFF2A85), // Vivid VYBE Magenta
            Color(0xFF9D4EDD), // Neon Violet
            Color(0xFF00E5FF)  // Electric Cyan
          ),
          start = Offset(0f, 0f),
          end = Offset(w, h)
        )
      }

      // Sparkle rays around mini heart when liked
      drawLine(
        color = Color(0xFF00E5FF),
        start = Offset(w * 0.38f, h * 0.08f),
        end = Offset(w * 0.32f, h * 0.05f),
        strokeWidth = strokeW * 0.85f,
        cap = StrokeCap.Round
      )
      drawLine(
        color = Color(0xFFFF2A85),
        start = Offset(w * 0.72f, h * 0.08f),
        end = Offset(w * 0.78f, h * 0.05f),
        strokeWidth = strokeW * 0.85f,
        cap = StrokeCap.Round
      )
      drawLine(
        color = Color(0xFF9D4EDD),
        start = Offset(w * 0.55f, h * 0.01f),
        end = Offset(w * 0.55f, h * -0.04f),
        strokeWidth = strokeW * 0.85f,
        cap = StrokeCap.Round
      )

      // Filled luminous mini heart
      drawPath(path = heartPath, brush = heartBrush)
      drawPath(
        path = heartPath,
        color = Color.White.copy(alpha = 0.9f),
        style = Stroke(width = strokeW * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )

      // Hand gesture outline with vibrant highlight
      drawPath(
        path = handPath,
        color = Color(0xFFFF2A85),
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
      drawPath(
        path = crease1,
        color = Color(0xFFFF2A85).copy(alpha = 0.75f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
      )
      drawPath(
        path = crease2,
        color = Color(0xFFFF2A85).copy(alpha = 0.75f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
      )
      drawPath(
        path = thumbCrossLine,
        color = Color(0xFFFF2A85).copy(alpha = 0.75f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
      )
    } else {
      // Default / inactive: Clean white border outline with blank body fill
      drawPath(
        path = heartPath,
        color = outlineColor,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
      drawPath(
        path = handPath,
        color = outlineColor,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
      drawPath(
        path = crease1,
        color = outlineColor.copy(alpha = 0.85f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
      )
      drawPath(
        path = crease2,
        color = outlineColor.copy(alpha = 0.85f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
      )
      drawPath(
        path = thumbCrossLine,
        color = outlineColor.copy(alpha = 0.85f),
        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
      )
    }
  }
}
