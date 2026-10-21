package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BreathingPhase
import com.example.ui.VybeViewModel
import kotlinx.coroutines.delay

private val SereneSage = Color(0xFF6B8E78)
private val SereneLavender = Color(0xFF8E8CD8)
private val SereneSky = Color(0xFF5B92E5)
private val SereneBgDark = Color(0xFF0F1720)
private val SereneCardDark = Color(0xFF16222F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiritualModeScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

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

  // Animated breathing circle scale
  val breathingScale by animateFloatAsState(
    targetValue = when (currentPhase) {
      BreathingPhase.INHALE -> 1.35f
      BreathingPhase.HOLD -> 1.35f
      BreathingPhase.EXHALE -> 0.85f
      BreathingPhase.REST -> 0.85f
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
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("spiritual_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text(
              text = "Spiritual & Mindfulness",
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text(
              text = "Peace, Gratitude & Reflection • Private by default",
              fontSize = 11.sp,
              color = SereneSage
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Enable / Disable Spiritual Mode Card
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  shape = CircleShape,
                  color = if (isSpiritualMode) SereneSage.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                  modifier = Modifier.size(42.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text("🕊️", fontSize = 20.sp)
                  }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = "Spiritual Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                  )
                  Text(
                    text = if (isSpiritualMode) "Enabled — Mindful calming active" else "Disabled — Standard social feed",
                    fontSize = 12.sp,
                    color = if (isSpiritualMode) SereneSage else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Switch(
                checked = isSpiritualMode,
                onCheckedChange = { viewModel.setSpiritualMode(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SereneSage),
                modifier = Modifier.testTag("spiritual_mode_toggle")
              )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "100% Private. Your spiritual practices, gratitudes, and reflections are strictly private and never publicly shared or broadcasted to other profiles.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      // 2. Guided Breathing Exercise (Box Breathing 4-4-4-4)
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "Mindful Breath Pacer",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
            Text(
              text = "4-4-4-4 Box Breathing for instant nervous system equilibrium",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
              modifier = Modifier
                .size(150.dp)
                .scale(if (isBreathingActive) breathingScale else 1.0f)
                .clip(CircleShape)
                .background(
                  Brush.radialGradient(
                    colors = listOf(
                      SereneLavender.copy(alpha = 0.5f),
                      SereneSage.copy(alpha = 0.25f),
                      Color.Transparent
                    )
                  )
                ),
              contentAlignment = Alignment.Center
            ) {
              Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = SereneSage.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(2.dp, SereneLavender)
              ) {
                Column(
                  modifier = Modifier.fillMaxSize(),
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.Center
                ) {
                  Text(
                    text = if (isBreathingActive) currentPhase.label else "Breathe",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                  )
                  if (isBreathingActive) {
                    Text(
                      text = "${secondsLeftInPhase}s",
                      fontWeight = FontWeight.ExtraBold,
                      fontSize = 16.sp,
                      color = Color.White
                    )
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
              onClick = { isBreathingActive = !isBreathingActive },
              colors = ButtonDefaults.buttonColors(
                containerColor = if (isBreathingActive) SereneLavender else SereneSage
              ),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.testTag("spiritual_breathe_toggle")
            ) {
              Icon(
                imageVector = if (isBreathingActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (isBreathingActive) "Pause Breathing" else "Begin Calming Breath",
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
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = androidx.compose.foundation.BorderStroke(1.dp, SereneLavender.copy(alpha = 0.3f))
          ) {
            Column(modifier = Modifier.padding(18.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = SereneLavender.copy(alpha = 0.2f)
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
                  modifier = Modifier.testTag("spiritual_next_reflection")
                ) {
                  Text("Next Quote", fontSize = 11.sp, color = SereneSage)
                }
              }

              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "“${activeReflection.quote}”",
                fontSize = 15.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "— ${activeReflection.traditionOrAuthor}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      // 4. Daily Gratitude Prompt & Journal
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("✨", fontSize = 18.sp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Daily Gratitude Journal", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(
              text = "What is a simple blessing or kindness you are thankful for today?",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
              value = gratitudeInput,
              onValueChange = { gratitudeInput = it },
              placeholder = { Text("Write your gratitude entry here...", fontSize = 13.sp) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("add_gratitude_input"),
              shape = RoundedCornerShape(12.dp),
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
              colors = ButtonDefaults.buttonColors(containerColor = SereneSage),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .align(Alignment.End)
                .testTag("add_gratitude_button")
            ) {
              Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save Gratitude", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            if (gratitudes.isNotEmpty()) {
              Spacer(modifier = Modifier.height(14.dp))
              Text("Recent Gratitudes (${gratitudes.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
              Spacer(modifier = Modifier.height(6.dp))
              gratitudes.take(5).forEach { g ->
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.surface,
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                  ) {
                    Text("🌱", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                      Text(g.entry, fontSize = 13.sp, lineHeight = 18.sp)
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(g.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // 5. Mindful Intentions
      item {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("🎯", fontSize = 18.sp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Mindful Daily Intentions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(
              text = "Set a conscious habit or intention for inner harmony today.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = intentionInput,
                onValueChange = { intentionInput = it },
                placeholder = { Text("e.g., Speak gently today...", fontSize = 13.sp) },
                modifier = Modifier
                  .weight(1f)
                  .testTag("add_intention_input"),
                shape = RoundedCornerShape(12.dp),
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
                colors = ButtonDefaults.buttonColors(containerColor = SereneLavender),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("add_intention_button")
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
                      color = if (intention.isFulfilled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                      style = if (intention.isFulfilled) MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                      ) else MaterialTheme.typography.bodyMedium
                    )
                    Text(intention.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
