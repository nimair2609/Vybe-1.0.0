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
  contentDescriptionText: String? = if (isLiked) "Finger Heart active" else "Finger Heart reaction"
) {
  val scale = remember { Animatable(1f) }

  LaunchedEffect(isLiked) {
    if (isLiked) {
      scale.snapTo(0.86f)
      scale.animateTo(
        targetValue = 1.35f,
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

  val resolvedInactiveOverride = if (inactiveTint != Color.Unspecified) inactiveTint else null

  FingerHeartIcon(
    isLiked = isLiked,
    size = size,
    inactiveTintOverride = resolvedInactiveOverride,
    contentDescriptionText = contentDescriptionText,
    modifier = modifier.graphicsLayer {
      scaleX = scale.value
      scaleY = scale.value
    }
  )
}
