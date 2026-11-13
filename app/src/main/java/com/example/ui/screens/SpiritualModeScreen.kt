package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BreathingPhase
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import kotlinx.coroutines.delay

private val SereneSage = Color(0xFF4F8A6B)
private val SereneLavender = Color(0xFF8B7CE8)

@Composable
fun SpiritualModeScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val isSpiritualMode by viewModel.isSpiritualMode.collectAsState()
  val reflections by viewModel.spiritualReflections.collectAsState()
  val gratitudes by viewModel.spiritualGratitudes.collectAsState()
  val intentions by viewModel.spiritualIntentions.collectAsState()

  var gratitudeInput by remember { mutableStateOf("") }
  var intentionInput by remember { mutableStateOf("") }
  var currentReflectionIndex by remember { mutableIntStateOf(0) }
  var isBreathingActive by remember { mutableStateOf(false) }
  var currentPhase by remember { mutableStateOf(BreathingPhase.INHALE) }
  var secondsLeftInPhase by remember { mutableIntStateOf(4) }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = SereneSage,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  // Animated breathing circle scale
  val breathingScale by animateFloatAsState(
    targetValue = when (currentPhase) {
      BreathingPhase.INHALE -> 1.32f
      BreathingPhase.HOLD -> 1.32f
      BreathingPhase.EXHALE -> 0.86f
      BreathingPhase.REST -> 0.86f
    },
    animationSpec = tween(durationMillis = 3800, easing = LinearEasing),
    label = "BreathingScale"
  )

  LaunchedEffect(isBreathingActive, currentPhase) {
    if (!isBreathingActive) return@LaunchedEffect
    for (sec in currentPhase.seconds downTo 1) {
      secondsLeftInPhase = sec
      delay(1000)
    }
    currentPhase = when (currentPhase) {
      BreathingPhase.INHALE -> BreathingPhase.HOLD
      BreathingPhase.HOLD -> BreathingPhase.EXHALE
      BreathingPhase.EXHALE -> BreathingPhase.REST
      BreathingPhase.REST -> BreathingPhase.INHALE
    }
  }

  val activeReflection = if (reflections.isNotEmpty()) {
    reflections[currentReflectionIndex % reflections.size]
  } else null

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "Spiritual & Mindfulness",
        subtitle = "Peace, Gratitude & Reflection • Private by default",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "spiritual_back_button"
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding()
        .imePadding()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp)
    ) {
      // 1. Enable / Disable Spiritual Mode Card
      item {
        VybeSurfaceCard(contentPadding = PaddingValues(18.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Surface(
                shape = CircleShape,
                color = if (isSpiritualMode) SereneSage.copy(alpha = 0.2f) else palette.elevated,
                border = BorderStroke(1.dp, if (isSpiritualMode) SereneSage.copy(alpha = 0.4f) else palette.border),
                modifier = Modifier.size(44.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    imageVector = Icons.Outlined.SelfImprovement,
                    contentDescription = null,
                    tint = if (isSpiritualMode) SereneSage else palette.textSecondary,
                    modifier = Modifier.size(22.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Spiritual Mode",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = palette.textPrimary
                )
                Text(
                  text = if (isSpiritualMode) "Enabled — Mindful calming active" else "Disabled — Standard social feed",
                  fontSize = 12.sp,
                  color = if (isSpiritualMode) SereneSage else palette.textSecondary
                )
              }
            }
            Switch(
              checked = isSpiritualMode,
              onCheckedChange = { viewModel.setSpiritualMode(it) },
              colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SereneSage,
                uncheckedTrackColor = palette.elevated,
                uncheckedBorderColor = palette.border
              ),
              modifier = Modifier.testTag("spiritual_mode_toggle")
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
          Spacer(modifier = Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "100% Private. Your spiritual practices, gratitudes, and reflections are strictly private and never publicly shared or broadcasted to other profiles.",
              fontSize = 11.sp,
              color = palette.textSecondary,
              lineHeight = 16.sp
            )
          }
        }
      }

      // 2. Guided Breathing Exercise (Box Breathing 4-4-4-4)
      item {
        VybeSurfaceCard(contentPadding = PaddingValues(20.dp)) {
          Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Mindful Breath Pacer",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "4-4-4-4 Box Breathing for instant nervous system equilibrium",
              fontSize = 12.sp,
              color = palette.textSecondary,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Box(
              modifier = Modifier
                .size(154.dp)
                .scale(if (isBreathingActive) breathingScale else 1.0f)
                .clip(CircleShape)
                .background(
                  Brush.radialGradient(
                    colors = listOf(
                      SereneLavender.copy(alpha = 0.45f),
                      SereneSage.copy(alpha = 0.25f),
                      Color.Transparent
                    )
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Surface(
                modifier = Modifier.size(104.dp),
                shape = CircleShape,
                color = SereneSage.copy(alpha = 0.42f),
                border = BorderStroke(2.dp, SereneLavender)
              ) {
                Column(
                  modifier = Modifier.fillMaxSize(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Text(
                    text = if (isBreathingActive) currentPhase.label else "Breathe",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                  )
                  if (isBreathingActive) {
                    Text(
                      text = "${secondsLeftInPhase}s",
                      fontFamily = PlusJakartaSansFontFamily,
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 17.sp,
                      color = palette.textPrimary
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
              onClick = { isBreathingActive = !isBreathingActive },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isBreathingActive) SereneLavender else SereneSage,
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier
                .heightIn(min = VybeTokens.Size.buttonHeight)
                .testTag("spiritual_breathe_toggle")
            ) {
              Icon(
                imageVector = if (isBreathingActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isBreathingActive) "Pause Breathing" else "Begin Calming Breath",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }
      }

      // 3. Wisdom & Universal Reflections
      if (activeReflection != null) {
        item {
          VybeSurfaceCard(
            borderColor = SereneLavender.copy(alpha = 0.4f),
            contentPadding = PaddingValues(18.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                shape = RoundedCornerShape(VybeTokens.Radius.xs),
                color = SereneLavender.copy(alpha = 0.16f)
              ) {
                Text(
                  text = "Reflective Wisdom • ${activeReflection.theme}",
                  color = SereneLavender,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }

              TextButton(
                onClick = {
                  currentReflectionIndex = (currentReflectionIndex + 1) % reflections.size
                },
                modifier = Modifier.heightIn(min = 48.dp).testTag("spiritual_next_reflection")
              ) {
                Text("Next Quote", fontSize = 12.sp, color = SereneSage, fontWeight = FontWeight.SemiBold)
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "“${activeReflection.quote}”",
              fontSize = 15.sp,
              fontStyle = FontStyle.Italic,
              lineHeight = 22.sp,
              fontWeight = FontWeight.Medium,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "— ${activeReflection.traditionOrAuthor}",
              fontSize = 12.sp,
              color = palette.textSecondary,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      // 4. Daily Gratitude Prompt & Journal
      item {
        VybeSurfaceCard(contentPadding = PaddingValues(18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = SereneSage, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Daily Gratitude Journal",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = palette.textPrimary
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "What is a simple blessing or kindness you are thankful for today?",
            fontSize = 12.sp,
            color = palette.textSecondary
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = gratitudeInput,
            onValueChange = { gratitudeInput = it },
            placeholder = { Text("Write your gratitude entry here...", fontSize = 13.sp, color = palette.textMuted) },
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("add_gratitude_input"),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            minLines = 2,
            maxLines = 4
          )

          Spacer(modifier = Modifier.height(10.dp))

          Button(
            onClick = {
              if (gratitudeInput.isNotBlank()) {
                viewModel.addGratitudeEntry(gratitudeInput.trim())
                gratitudeInput = ""
              }
            },
            enabled = gratitudeInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(
              containerColor = SereneSage,
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            modifier = Modifier
              .align(Alignment.End)
              .heightIn(min = 44.dp)
              .testTag("add_gratitude_button")
          ) {
            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Gratitude", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }

          if (gratitudes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("Recent Gratitudes (${gratitudes.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            gratitudes.take(5).forEach { g ->
              Surface(
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                color = palette.elevated,
                border = BorderStroke(1.dp, palette.border),
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.Top
                ) {
                  Icon(Icons.Outlined.Spa, contentDescription = null, tint = SereneSage, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(g.entry, fontSize = 13.sp, lineHeight = 18.sp, color = palette.textPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(g.timestamp, fontSize = 10.sp, color = palette.textMuted)
                  }
                }
              }
            }
          }
        }
      }

      // 5. Mindful Intentions
      item {
        VybeSurfaceCard(contentPadding = PaddingValues(18.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.TrackChanges, contentDescription = null, tint = SereneLavender, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Mindful Daily Intentions",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = palette.textPrimary
            )
          }
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Set a conscious habit or intention for inner harmony today.",
            fontSize = 12.sp,
            color = palette.textSecondary
          )

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedTextField(
              value = intentionInput,
              onValueChange = { intentionInput = it },
              placeholder = { Text("e.g., Speak gently today...", fontSize = 13.sp, color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier
                .weight(1f)
                .testTag("add_intention_input"),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = {
                if (intentionInput.isNotBlank()) {
                  viewModel.addIntention(intentionInput.trim())
                  intentionInput = ""
                }
              },
              enabled = intentionInput.isNotBlank(),
              colors = ButtonDefaults.buttonColors(
                containerColor = SereneLavender,
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("add_intention_button")
            ) {
              Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }

          if (intentions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            intentions.forEach { intention ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                  .clickable { viewModel.toggleIntentionFulfilled(intention.id) }
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Checkbox(
                  checked = intention.isFulfilled,
                  onCheckedChange = { viewModel.toggleIntentionFulfilled(intention.id) },
                  colors = CheckboxDefaults.colors(checkedColor = SereneSage)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                  Text(
                    text = intention.text,
                    fontSize = 13.sp,
                    color = if (intention.isFulfilled) palette.textMuted else palette.textPrimary,
                    style = if (intention.isFulfilled) {
                      MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough)
                    } else {
                      MaterialTheme.typography.bodyMedium
                    }
                  )
                  Text(intention.timestamp, fontSize = 10.sp, color = palette.textMuted)
                }
              }
            }
          }
        }
      }
    }
  }
}
