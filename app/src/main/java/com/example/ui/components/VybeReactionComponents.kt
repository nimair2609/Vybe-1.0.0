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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.withTimeoutOrNull
import com.example.data.model.MoodType
import com.example.data.model.VybeReaction
import com.example.ui.theme.*

fun reactionColor(reaction: VybeReaction): Color = when (reaction) {
  VybeReaction.LOVE -> Color(0xFFEC4899)  // Signature Finger Heart Magenta
  VybeReaction.FLAME -> Color(0xFFEF4444) // Vivid Flame Coral
  VybeReaction.LAUGH -> Color(0xFFF59E0B) // Amber / Gold Laugh
  VybeReaction.WOW -> Color(0xFF00F0FF)   // Neon Wow Cyan
  VybeReaction.SAD -> Color(0xFF3B82F6)   // Ocean Sad Blue
}

fun reactionGradient(reaction: VybeReaction): Brush = when (reaction) {
  VybeReaction.LOVE -> Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))
  VybeReaction.FLAME -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
  VybeReaction.LAUGH -> Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
  VybeReaction.WOW -> Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFF38BDF8)))
  VybeReaction.SAD -> Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF6366F1)))
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
 * Custom VYBE-branded reaction icon for the 5 official reactions:
 * 1. Finger Heart (🫰) [Default]
 * 2. Fire (🔥)
 * 3. Laugh (😂)
 * 4. Wow (😮)
 * 5. Sad (😢)
 */
@Composable
fun VybeReactionOutlineIcon(
  reaction: VybeReaction,
  size: Dp = 20.dp,
  tint: Color = reactionColor(reaction),
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = reaction.emoji,
      fontSize = (size.value * 0.88f).sp,
      lineHeight = (size.value * 0.88f).sp,
      textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
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
 * Upgraded Interactive Reaction Button with Press-and-Hold + Drag-to-Highlight Gesture.
 * Supports:
 * - Single tap: Applies default 🫰 (Finger Heart) or toggles off active reaction.
 * - Press & hold: Displays horizontal floating bar with 5 reactions (LOVE 🫰, FLAME 🔥, LAUGH 😂, WOW 😮, SAD 😢).
 * - Drag across bar: Highlights reaction with 1.35x scale and haptic feedback.
 * - Release: Commits selected reaction immediately.
 * - Dragged away & released outside: Cancels without reacting.
 */
@Composable
fun ReelReactionGestureButton(
  userReaction: VybeReaction?,
  onReact: (VybeReaction) -> Unit,
  onToggleDefault: () -> Unit,
  modifier: Modifier = Modifier
) {
  VybeReactionGestureButtonInternal(
    userReaction = userReaction,
    onReact = onReact,
    onToggleDefault = onToggleDefault,
    isSideBar = true,
    testTagPrefix = "reel",
    modifier = modifier
  )
}

@Composable
fun PostReactionGestureButton(
  userReaction: VybeReaction?,
  onReact: (VybeReaction) -> Unit,
  onToggleDefault: () -> Unit,
  modifier: Modifier = Modifier
) {
  VybeReactionGestureButtonInternal(
    userReaction = userReaction,
    onReact = onReact,
    onToggleDefault = onToggleDefault,
    isSideBar = false,
    testTagPrefix = "post",
    modifier = modifier
  )
}

@Composable
private fun VybeReactionGestureButtonInternal(
  userReaction: VybeReaction?,
  onReact: (VybeReaction) -> Unit,
  onToggleDefault: () -> Unit,
  isSideBar: Boolean,
  testTagPrefix: String,
  modifier: Modifier = Modifier
) {
  val haptic = LocalHapticFeedback.current
  val densityObj = androidx.compose.ui.platform.LocalDensity.current
  val currentUserReaction by rememberUpdatedState(userReaction)
  val currentOnReact by rememberUpdatedState(onReact)
  val currentOnToggleDefault by rememberUpdatedState(onToggleDefault)

  var isBarVisible by remember { mutableStateOf(false) }
  var hoveredIndex by remember { mutableStateOf<Int?>(null) }
  val reactions = remember { VybeReaction.values() }
  val buttonSize = if (isSideBar) 48.dp else 40.dp

  Box(
    modifier = modifier.size(buttonSize),
    contentAlignment = Alignment.Center
  ) {
    // Non-focusable Popup prevents any parent Box/Column expansion (zero layout shift on Reel rail)
    if (isBarVisible) {
      val popupOffset = with(densityObj) {
        if (isSideBar) {
          androidx.compose.ui.unit.IntOffset(x = (-238).dp.roundToPx(), y = (-1).dp.roundToPx())
        } else {
          androidx.compose.ui.unit.IntOffset(x = (-4).dp.roundToPx(), y = (-56).dp.roundToPx())
        }
      }
      androidx.compose.ui.window.Popup(
        alignment = Alignment.TopStart,
        offset = popupOffset,
        properties = androidx.compose.ui.window.PopupProperties(
          focusable = false,
          clippingEnabled = false
        )
      ) {
        Surface(
          color = Color(0xFF0C1020).copy(alpha = 0.97f),
          shape = RoundedCornerShape(28.dp),
          border = BorderStroke(1.dp, Color(0xFF334155)),
          shadowElevation = 18.dp,
          modifier = Modifier.testTag("${testTagPrefix}_reaction_gesture_bar")
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            reactions.forEachIndexed { index, rx ->
              val isHovered = hoveredIndex == index
              val isCurrent = currentUserReaction == rx
              val rColor = reactionColor(rx)
              val scaleAnim by animateFloatAsState(
                targetValue = if (isHovered) 1.38f else if (isCurrent) 1.12f else 1f,
                animationSpec = spring(
                  dampingRatio = Spring.DampingRatioMediumBouncy,
                  stiffness = Spring.StiffnessMedium
                ),
                label = "reaction_scale_${rx.name}"
              )

              Box(
                modifier = Modifier
                  .scale(scaleAnim)
                  .size(38.dp)
                  .clip(CircleShape)
                  .background(
                    if (isHovered) rColor.copy(alpha = 0.38f)
                    else if (isCurrent) rColor.copy(alpha = 0.22f)
                    else Color.Transparent
                  )
                  .testTag("${testTagPrefix}_reaction_option_${rx.name}"),
                contentAlignment = Alignment.Center
              ) {
                VybeReactionOutlineIcon(
                  reaction = rx,
                  size = 24.dp,
                  tint = if (isHovered || isCurrent) rColor else Color.White
                )
              }
            }
          }
        }
      }
    }

    // Fixed-size reaction trigger button with tap + press-hold-drag-release gesture detector
    Box(
      modifier = Modifier
        .fillMaxSize()
        .clip(CircleShape)
        .background(
          if (isSideBar) Color.Black.copy(alpha = 0.5f)
          else Color.White.copy(alpha = 0.08f)
        )
        .pointerInput(isSideBar) {
          awaitEachGesture {
            try {
              awaitFirstDown(requireUnconsumed = false)
              hoveredIndex = null

              val upOrCancel = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation()
              }

              if (upOrCancel != null) {
                // Quick tap: toggle current/default 🫰 reaction
                currentOnToggleDefault()
              } else {
                // Press & hold: open floating 5-icon bar
                isBarVisible = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.firstOrNull() ?: break
                  if (!change.pressed) {
                    val selectedIndex = hoveredIndex
                    if (selectedIndex != null && selectedIndex in reactions.indices) {
                      currentOnReact(reactions[selectedIndex])
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    isBarVisible = false
                    hoveredIndex = null
                    change.consume()
                    break
                  }

                  val currentX = change.position.x
                  val currentY = change.position.y

                  if (isSideBar) {
                    // Reel floating bar spans [-238dp, -8dp] to the left of the 48dp button
                    val barStartX = -238f * density
                    val barEndX = -8f * density
                    val barMinY = -28f * density
                    val barMaxY = 76f * density

                    if (currentX in barStartX..barEndX && currentY in barMinY..barMaxY) {
                      val progress = ((currentX - barStartX) / (barEndX - barStartX)).coerceIn(0f, 0.999f)
                      val newIndex = (progress * reactions.size).toInt()
                      if (newIndex != hoveredIndex) {
                        hoveredIndex = newIndex
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      }
                    } else {
                      hoveredIndex = null
                    }
                  } else {
                    // Feed Post floating bar spans [-4dp, 226dp] above the 40dp button
                    val barStartX = -8f * density
                    val barEndX = 230f * density
                    val barMinY = -78f * density
                    val barMaxY = -2f * density

                    if (currentX in barStartX..barEndX && currentY in barMinY..barMaxY) {
                      val progress = ((currentX - barStartX) / (barEndX - barStartX)).coerceIn(0f, 0.999f)
                      val newIndex = (progress * reactions.size).toInt()
                      if (newIndex != hoveredIndex) {
                        hoveredIndex = newIndex
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                      }
                    } else {
                      hoveredIndex = null
                    }
                  }
                  change.consume()
                }
              }
            } finally {
              isBarVisible = false
              hoveredIndex = null
            }
          }
        }
        .testTag("${testTagPrefix}_like_button"),
      contentAlignment = Alignment.Center
    ) {
      val displayReaction = userReaction ?: VybeReaction.LOVE
      val isReacted = userReaction != null
      VybeReactionOutlineIcon(
        reaction = displayReaction,
        size = if (isSideBar) 24.dp else 22.dp,
        tint = if (isReacted) reactionColor(displayReaction) else Color.White
      )
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
