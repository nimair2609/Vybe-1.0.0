package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GrowJourney
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val growJourneys by viewModel.growJourneys.collectAsState()
  val growBadges by viewModel.growBadges.collectAsState()

  var showReflectionDialogForJourney by remember { mutableStateOf<GrowJourney?>(null) }
  var reflectionNote by remember { mutableStateOf("") }
  var isSharedPublicly by remember { mutableStateOf(false) }

  val activeStreak = remember(growJourneys) {
    growJourneys.filter { it.isJoined }.maxOfOrNull { it.streakDays } ?: 1
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    containerColor = palette.canvas,
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Grow",
        subtitle = "Self-Expansion • Learn & Collaborate",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "grow_back_button"
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Grow Momentum Dashboard Card
      item {
        VybeSurfaceCard(
          borderColor = palette.accent.copy(alpha = 0.35f),
          contentPadding = PaddingValues(18.dp),
          modifier = Modifier.fillMaxWidth().testTag("grow_momentum_card")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Daily Habit Momentum",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = palette.textPrimary
              )
              Text(
                text = "Consistency unlocks personal & professional mastery",
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
            Surface(
              color = palette.warning.copy(alpha = 0.16f),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.warning.copy(alpha = 0.45f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = palette.warning, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("$activeStreak Day Streak", color = palette.warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${growJourneys.count { it.isJoined }}",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = palette.accent
              )
              Text("In Progress", fontSize = 11.sp, color = palette.textSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "${growBadges.size}",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = palette.accentSecondary
              )
              Text("Badges Earned", fontSize = 11.sp, color = palette.textSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "100%",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Black,
                fontSize = 20.sp,
                color = palette.success
              )
              Text("Commitment", fontSize = 11.sp, color = palette.textSecondary)
            }
          }
        }
      }

      // 2. Active & Guided Journeys Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Guided Growth Journeys",
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = palette.textPrimary
          )
          Surface(
            color = palette.elevated,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            border = BorderStroke(1.dp, palette.border)
          ) {
            Text(
              text = "7 / 21 / 30 Days",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.textSecondary,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }
      }

      if (growJourneys.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.MenuBook,
            title = "No growth tracks active",
            subtitle = "Growth journeys will appear here as soon as cohorts sync."
          )
        }
      } else {
        // 3. Journeys Cards
        items(growJourneys, key = { it.id }) { journey ->
          VybeSurfaceCard(
            borderColor = if (journey.isJoined) palette.accent.copy(alpha = 0.5f) else palette.border,
            contentPadding = PaddingValues(18.dp),
            modifier = Modifier.fillMaxWidth().testTag("grow_journey_${journey.id}")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue))),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = when (journey.category) {
                      "Mindfulness" -> Icons.Outlined.Spa
                      "Productivity" -> Icons.Outlined.Bolt
                      else -> Icons.Outlined.FitnessCenter
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = journey.title,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${journey.durationDays}-Day Track • ${journey.participantsCount} peers",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                  )
                }
              }

              if (journey.isJoined) {
                Button(
                  onClick = { showReflectionDialogForJourney = journey },
                  colors = ButtonDefaults.buttonColors(containerColor = palette.accentSecondary),
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Reflect", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              } else {
                Button(
                  onClick = { viewModel.joinGrowJourney(journey.id) },
                  colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Start", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = journey.description,
              fontSize = 13.sp,
              color = palette.textSecondary,
              lineHeight = 18.sp
            )

            if (journey.isJoined) {
              Spacer(modifier = Modifier.height(12.dp))
              Surface(
                color = palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = "Day ${journey.currentDay} of ${journey.durationDays}",
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = palette.accent
                    )
                    Text(
                      text = "${((journey.currentDay.toFloat() / journey.durationDays.coerceAtLeast(1)) * 100).toInt()}% Complete",
                      fontSize = 11.sp,
                      color = palette.textSecondary
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  LinearProgressIndicator(
                    progress = { (journey.currentDay.toFloat() / journey.durationDays.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = palette.accent,
                    trackColor = palette.border
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = "Today's Prompt: \"${journey.dailyPrompt}\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary
                  )
                }
              }
            }
          }
        }
      }

      // 4. Achievement Badges Showcase
      item {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Achievement Badges",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = palette.textPrimary
        )
      }

      items(growBadges, key = { it.id }) { badge ->
        VybeSurfaceCard(
          borderColor = palette.warning.copy(alpha = 0.4f),
          contentPadding = PaddingValues(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.warning.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = palette.warning, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(badge.description, fontSize = 12.sp, color = palette.textSecondary)
            }
            Surface(
              color = palette.warning.copy(alpha = 0.15f),
              shape = RoundedCornerShape(VybeTokens.Radius.sm)
            ) {
              Text(
                text = badge.badgeTier,
                color = palette.warning,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }
        }
      }
    }
  }

  // Daily Reflection Modal
  if (showReflectionDialogForJourney != null) {
    val journey = showReflectionDialogForJourney!!
    AlertDialog(
      onDismissRequest = { showReflectionDialogForJourney = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = {
        Text("Day ${journey.currentDay} Reflection", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = journey.dailyPrompt,
            fontSize = 13.sp,
            color = palette.textSecondary
          )
          OutlinedTextField(
            value = reflectionNote,
            onValueChange = { reflectionNote = it },
            placeholder = { Text("Write your thoughts or milestone for today...") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
            maxLines = 4
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Share milestone to public feed", fontSize = 12.sp, color = palette.textPrimary)
            Switch(
              checked = isSharedPublicly,
              onCheckedChange = { isSharedPublicly = it }
            )
          }
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Complete Day",
          onClick = {
            viewModel.submitGrowReflection(
              journeyId = journey.id,
              dayNumber = journey.currentDay,
              text = reflectionNote.ifBlank { "Completed Day ${journey.currentDay} milestone" },
              isShared = isSharedPublicly
            )
            reflectionNote = ""
            showReflectionDialogForJourney = null
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showReflectionDialogForJourney = null }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }
}
