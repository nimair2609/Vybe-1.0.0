package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VybeReaction
import com.example.ui.theme.*

fun reactionColor(reaction: VybeReaction): Color = when (reaction) {
  VybeReaction.PULSE -> Color(0xFFF59E0B) // Amber Electric Pulse
  VybeReaction.SPARK -> Color(0xFFEC4899) // Neon Magenta Spark
  VybeReaction.WAVE -> Color(0xFF06B6D4)  // Vibrant Cyan Wave
  VybeReaction.FLAME -> Color(0xFFEF4444) // Vivid Flame Coral
}

fun reactionGradient(reaction: VybeReaction): Brush = when (reaction) {
  VybeReaction.PULSE -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
  VybeReaction.SPARK -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFFD946EF)))
  VybeReaction.WAVE -> Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6)))
  VybeReaction.FLAME -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
}

/**
 * Premium Animated Reaction Selector for VYBE:
 * - Displays all four reactions: PULSE ⚡, SPARK ✨, WAVE 💫, FLAME 🔥
 * - Single-tap selects a reaction; tapping an already selected reaction removes it.
 * - Selecting another switches the reaction.
 */
@Composable
fun VybeReactionSelector(
  selectedReaction: VybeReaction?,
  onSelectReaction: (VybeReaction) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    color = Color(0xFF141726).copy(alpha = 0.96f),
    shape = RoundedCornerShape(26.dp),
    border = BorderStroke(1.2.dp, Brush.horizontalGradient(listOf(VybeBrandCyan, VybeBrandViolet, VybeBrandMagenta))),
    shadowElevation = 12.dp,
    modifier = modifier
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .testTag("vybe_reaction_selector")
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VybeReaction.values().forEach { reaction ->
        val isSelected = selectedReaction == reaction
        val scale = remember { Animatable(1f) }

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
            .clip(CircleShape)
            .background(
              if (isSelected) reactionColor(reaction).copy(alpha = 0.22f) else Color.Transparent
            )
            .clickable {
              onSelectReaction(reaction)
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("reaction_item_${reaction.name}"),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = reaction.emoji,
              fontSize = 22.sp
            )
            Text(
              text = reaction.label,
              fontSize = 9.sp,
              fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
              color = if (isSelected) reactionColor(reaction) else Color.White.copy(alpha = 0.75f)
            )
          }
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
          Surface(
            color = if (isSelected) reactionColor(reaction).copy(alpha = 0.25f) else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) BorderStroke(1.dp, reactionColor(reaction)) else null,
            modifier = Modifier
              .clip(RoundedCornerShape(12.dp))
              .clickable { onReactionClick(reaction) }
              .testTag("sheet_reaction_pill_${reaction.name}")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(reaction.emoji, fontSize = 16.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                reaction.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) reactionColor(reaction) else Color.White
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
              Text(reaction.emoji, fontSize = 16.sp)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = reaction.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUserReaction) reactionColor(reaction) else Color.White
              )
              if (isUserReaction) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  color = reactionColor(reaction).copy(alpha = 0.2f),
                  shape = RoundedCornerShape(4.dp)
                ) {
                  Text(
                    "You",
                    color = reactionColor(reaction),
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
