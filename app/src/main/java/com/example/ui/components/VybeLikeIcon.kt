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
    val strokeW = (w * 0.088f).coerceAtLeast(1.8f)

    // Outer V-Prism Resonance Wings
    val wingPath = Path().apply {
      moveTo(w * 0.14f, h * 0.28f)
      lineTo(w * 0.34f, h * 0.22f)
      lineTo(w * 0.50f, h * 0.60f)
      lineTo(w * 0.66f, h * 0.22f)
      lineTo(w * 0.86f, h * 0.28f)
      lineTo(w * 0.50f, h * 0.90f)
      close()
    }

    // Crown Resonance Spark Diamond nestled above the V valley
    val sparkPath = Path().apply {
      moveTo(w * 0.50f, h * 0.07f)
      lineTo(w * 0.59f, h * 0.22f)
      lineTo(w * 0.50f, h * 0.37f)
      lineTo(w * 0.41f, h * 0.22f)
      close()
    }

    if (isLiked) {
      val activeBrush = if (activeTintOverride != null) {
        Brush.linearGradient(listOf(activeTintOverride, activeTintOverride))
      } else {
        Brush.linearGradient(
          colors = listOf(
            Color(0xFF00E5FF), // Electric Cyan
            Color(0xFF9D4EDD), // Neon Violet
            Color(0xFFFF2A85)  // Vivid VYBE Magenta
          ),
          start = Offset(0f, 0f),
          end = Offset(w, h)
        )
      }

      // Subtle radiant pulse arcs on active state
      drawLine(
        color = Color(0xFF00E5FF).copy(alpha = 0.85f),
        start = Offset(w * 0.10f, h * 0.54f),
        end = Offset(w * 0.21f, h * 0.59f),
        strokeWidth = strokeW * 0.72f,
        cap = StrokeCap.Round
      )
      drawLine(
        color = Color(0xFFFF2A85).copy(alpha = 0.85f),
        start = Offset(w * 0.90f, h * 0.54f),
        end = Offset(w * 0.79f, h * 0.59f),
        strokeWidth = strokeW * 0.72f,
        cap = StrokeCap.Round
      )

      drawPath(path = wingPath, brush = activeBrush)
      drawPath(path = sparkPath, color = Color(0xFF00E5FF))
    } else {
      drawPath(
        path = wingPath,
        color = resolvedInactive,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
      drawPath(
        path = sparkPath,
        color = resolvedInactive,
        style = Stroke(width = strokeW * 0.82f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      )
    }
  }
}
