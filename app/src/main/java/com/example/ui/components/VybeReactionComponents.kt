package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MoodType
import com.example.data.model.VybeReaction
import com.example.ui.theme.*

fun reactionColor(reaction: VybeReaction): Color = when (reaction) {
  VybeReaction.LOVE -> Color(0xFFEC4899)  // Signature Finger Heart Magenta
  VybeReaction.PULSE -> Color(0xFFF59E0B) // Amber Electric Pulse
  VybeReaction.SPARK -> Color(0xFFA855F7) // Neon Violet Spark
  VybeReaction.WAVE -> Color(0xFF06B6D4)  // Vibrant Cyan Wave
  VybeReaction.FLAME -> Color(0xFFEF4444) // Vivid Flame Coral
}

fun reactionGradient(reaction: VybeReaction): Brush = when (reaction) {
  VybeReaction.LOVE -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
  VybeReaction.PULSE -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
  VybeReaction.SPARK -> Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFFD946EF)))
  VybeReaction.WAVE -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
  VybeReaction.FLAME -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
}

fun moodOutlineVector(mood: MoodType): ImageVector = when (mood) {
  MoodType.GENERAL -> Icons.Outlined.Language
  MoodType.HAPPY -> Icons.Outlined.AutoAwesome
  MoodType.HEARTBREAK -> Icons.Outlined.HeartBroken
  MoodType.CHILL -> Icons.Outlined.LocalCafe
  MoodType.HEALING -> Icons.Outlined.Spa
  MoodType.FUN -> Icons.Outlined.Whatshot
}

/**
 * Single-colour outline icon for VYBE Mood Zones (replaces inconsistent OS emojis).
 */
@Composable
fun VybeMoodOutlineIcon(
  mood: MoodType,
  size: Dp = 15.dp,
  tint: Color = mood.accentColor,
  modifier: Modifier = Modifier
) {
  Icon(
    imageVector = moodOutlineVector(mood),
    contentDescription = mood.title,
    tint = tint,
    modifier = modifier.size(size)
  )
}

/**
 * Custom VYBE-branded single-colour outline vector icon drawn via Compose Canvas
 * for PULSE, SPARK, WAVE, and FLAME.
 */
@Composable
fun VybeReactionOutlineIcon(
  reaction: VybeReaction,
  size: Dp = 20.dp,
  tint: Color = reactionColor(reaction),
  modifier: Modifier = Modifier
) {
  if (reaction == VybeReaction.LOVE) {
    Box(
      modifier = modifier.size(size),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = "🫰",
        fontSize = (size.value * 0.86f).sp,
        lineHeight = (size.value * 0.86f).sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
    }
  } else {
    Canvas(modifier = modifier.size(size)) {
      val w = this.size.width
      val h = this.size.height
      val stroke = Stroke(
        width = (w * 0.095f).coerceAtLeast(1.8f),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
      )
      val path = Path()
      when (reaction) {
        VybeReaction.LOVE -> { /* handled above */ }
        VybeReaction.PULSE -> {
          // Crisp single-line electric lightning bolt outline
          path.moveTo(w * 0.56f, h * 0.08f)
          path.lineTo(w * 0.22f, h * 0.54f)
          path.lineTo(w * 0.48f, h * 0.54f)
          path.lineTo(w * 0.42f, h * 0.92f)
          path.lineTo(w * 0.78f, h * 0.44f)
          path.lineTo(w * 0.52f, h * 0.44f)
          path.close()
        }
        VybeReaction.SPARK -> {
          // 4-point star spark outline
          path.moveTo(w * 0.50f, h * 0.08f)
          path.quadraticTo(w * 0.54f, h * 0.44f, w * 0.92f, h * 0.50f)
          path.quadraticTo(w * 0.54f, h * 0.56f, w * 0.50f, h * 0.92f)
          path.quadraticTo(w * 0.46f, h * 0.56f, w * 0.08f, h * 0.50f)
          path.quadraticTo(w * 0.46f, h * 0.44f, w * 0.50f, h * 0.08f)
          path.close()
        }
        VybeReaction.WAVE -> {
          // Twin harmonic resonance wave curves
          path.moveTo(w * 0.10f, h * 0.38f)
          path.cubicTo(w * 0.30f, h * 0.14f, w * 0.46f, h * 0.62f, w * 0.66f, h * 0.38f)
          path.quadraticTo(w * 0.80f, h * 0.22f, w * 0.90f, h * 0.38f)

          path.moveTo(w * 0.10f, h * 0.66f)
          path.cubicTo(w * 0.30f, h * 0.42f, w * 0.46f, h * 0.90f, w * 0.66f, h * 0.66f)
          path.quadraticTo(w * 0.80f, h * 0.50f, w * 0.90f, h * 0.66f)
        }
        VybeReaction.FLAME -> {
          // Clean modern single-stroke flame silhouette
          path.moveTo(w * 0.52f, h * 0.10f)
          path.cubicTo(w * 0.78f, h * 0.30f, w * 0.84f, h * 0.58f, w * 0.72f, h * 0.78f)
          path.cubicTo(w * 0.62f, h * 0.92f, w * 0.38f, h * 0.92f, w * 0.28f, h * 0.78f)
          path.cubicTo(w * 0.16f, h * 0.60f, w * 0.24f, h * 0.40f, w * 0.38f, h * 0.30f)
          path.quadraticTo(w * 0.36f, h * 0.48f, w * 0.48f, h * 0.44f)
          path.quadraticTo(w * 0.44f, h * 0.26f, w * 0.52f, h * 0.10f)
          path.close()
        }
      }
      drawPath(path = path, color = tint, style = stroke)
    }
  }
}

/**
 * Minimalist Instagram-Style Reaction Bar for VYBE:
 * - Displays all 5 reactions (LOVE 🫰, PULSE ⚡, SPARK ✨, WAVE 💫, FLAME 🔥) in a single,
 *   thin, continuous outlined floating bar.
 * - Icons only; clean and distraction-free without text labels.
 * - Single-tap selects a reaction or toggles off if already active.
 * - Smooth spring scale animation on selection.
 */
@Composable
fun VybeReactionSelector(
  selectedReaction: VybeReaction?,
  onSelectReaction: (VybeReaction) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color(0xFF0C1020).copy(alpha = 0.97f),
    shape = RoundedCornerShape(32.dp),
    border = BorderStroke(1.dp, Color(0xFF232D48)),
    shadowElevation = 16.dp,
    modifier = modifier
      .padding(horizontal = 4.dp, vertical = 2.dp)
      .testTag("vybe_reaction_selector")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VybeReaction.values().forEach { reaction ->
        val isSelected = selectedReaction == reaction
        val scale = remember { Animatable(1f) }
        val rColor = reactionColor(reaction)

        LaunchedEffect(isSelected) {
          if (isSelected) {
            scale.snapTo(0.85f)
            scale.animateTo(
              targetValue = 1.25f,
              animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
            scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy))
          } else {
            scale.snapTo(1f)
          }
        }

        Box(
          modifier = Modifier
            .scale(scale.value)
            .size(38.dp)
            .clip(CircleShape)
            .background(
              if (isSelected) rColor.copy(alpha = 0.26f) else Color.Transparent
            )
            .clickable {
              onSelectReaction(reaction)
            }
            .testTag("reaction_item_${reaction.name}"),
          contentAlignment = Alignment.Center
        ) {
          VybeReactionOutlineIcon(
            reaction = reaction,
            size = 23.dp,
            tint = if (isSelected) rColor else Color.White.copy(alpha = 0.88f)
          )
        }
      }
    }
  }
}

/**
 * Bottom Sheet displaying total reactions and individual reaction breakdowns for Reels
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelReactionBreakdownSheet(
  totalCount: Int,
  breakdown: Map<VybeReaction, Int>,
  userReaction: VybeReaction?,
  onReactionClick: (VybeReaction) -> Unit,
  onDismiss: () -> Unit
) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = Color(0xFF0F121E),
    contentColor = Color.White,
    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .navigationBarsPadding()
        .testTag("reel_reaction_breakdown_sheet")
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Reel Reactions",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
          )
          Text(
            text = "$totalCount total reactions",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
          )
        }
        IconButton(onClick = onDismiss) {
          Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Quick tap bar to toggle or switch user's reaction
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White.copy(alpha = 0.06f))
          .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        VybeReaction.values().forEach { reaction ->
          val isSelected = userReaction == reaction
          val rColor = reactionColor(reaction)
          Surface(
            color = if (isSelected) rColor.copy(alpha = 0.25f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) BorderStroke(1.dp, rColor) else null,
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .clickable { onReactionClick(reaction) }
              .testTag("sheet_reaction_pill_${reaction.name}")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              VybeReactionOutlineIcon(
                reaction = reaction,
                size = 16.dp,
                tint = rColor
              )
              Spacer(modifier = Modifier.width(5.dp))
              Text(
                reaction.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) rColor else Color.White
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      // Individual Breakdown Bars
      val maxCount = maxOf(1, VybeReaction.values().maxOfOrNull { breakdown[it] ?: 0 } ?: 1)

      VybeReaction.values().forEach { reaction ->
        val count = breakdown[reaction] ?: 0
        val fraction = (count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
        val isUserReaction = userReaction == reaction
        val rColor = reactionColor(reaction)

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onReactionClick(reaction) }
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              VybeReactionOutlineIcon(
                reaction = reaction,
                size = 17.dp,
                tint = rColor
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = reaction.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUserReaction) rColor else Color.White
              )
              if (isUserReaction) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  color = rColor.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    "You",
                    color = rColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                  )
                }
              }
            }

            Text(
              text = "$count",
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(Color.White.copy(alpha = 0.1f))
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(reactionGradient(reaction))
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
