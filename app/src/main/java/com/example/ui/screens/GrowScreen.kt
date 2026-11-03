package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrowScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val growJourneys by viewModel.growJourneys.collectAsState()
  val growBadges by viewModel.growBadges.collectAsState()

  var showReflectionDialogForJourney by remember { mutableStateOf<GrowJourney?>(null) }
  var reflectionNote by remember { mutableStateOf("") }
  var isSharedPublicly by remember { mutableStateOf(false) }

  val activeStreak = remember(growJourneys) {
    growJourneys.filter { it.isJoined }.maxOfOrNull { it.streakDays } ?: 1
  }

  Scaffold(
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets.statusBars,
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.size(48.dp).testTag("grow_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "VYBE Grow",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
              )
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                color = VybeBrandCyan.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "SELF-EXPANSION",
                  color = VybeBrandCyan,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
              }
            }
            Text(
              text = "CONNECT. EXPRESS. GROW. • AN NR NEXT PRODUCT",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Grow Momentum Dashboard Card
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(22.dp),
          border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth().testTag("grow_momentum_card")
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .background(
                Brush.verticalGradient(
                  listOf(
                    VybeVioletDark.copy(alpha = 0.35f),
                    MaterialTheme.colorScheme.surface
                  )
                )
              )
              .padding(18.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text(
                  text = "Daily Habit Momentum",
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Consistency unlocks personal & professional mastery",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Surface(
                color = Color(0xFFF59E0B).copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f))
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("$activeStreak Day Streak", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                  fontWeight = FontWeight.Black,
                  fontSize = 20.sp,
                  color = VybeBrandCyan
                )
                Text("In Progress", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "${growBadges.size}",
                  fontWeight = FontWeight.Black,
                  fontSize = 20.sp,
                  color = VybeBrandMagenta
                )
                Text("Badges Earned", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                  text = "100%",
                  fontWeight = FontWeight.Black,
                  fontSize = 20.sp,
                  color = Color(0xFF10B981)
                )
                Text("Commitment", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
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
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = "7 / 21 / 30 Days",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }
        }
      }

      // 3. Journeys Cards
      items(growJourneys, key = { it.id }) { journey ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          border = BorderStroke(1.dp, if (journey.isJoined) VybeBrandCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
          modifier = Modifier.fillMaxWidth().testTag("grow_journey_${journey.id}")
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
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
                    .background(
                      Brush.linearGradient(
                        listOf(VybeBrandViolet, VybeBrandMagenta)
                      )
                    ),
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    text = "${journey.durationDays}-Day Track • ${journey.participantsCount} peers",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              if (journey.isJoined) {
                Button(
                  onClick = { showReflectionDialogForJourney = journey },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Reflect", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              } else {
                Button(
                  onClick = { viewModel.joinGrowJourney(journey.id) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  shape = RoundedCornerShape(12.dp),
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
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 18.sp
            )

            if (journey.isJoined) {
              Spacer(modifier = Modifier.height(12.dp))
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
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
                      color = VybeBrandCyan
                    )
                    Text(
                      text = "${((journey.currentDay.toFloat() / journey.durationDays) * 100).toInt()}% Complete",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Spacer(modifier = Modifier.height(6.dp))
                  LinearProgressIndicator(
                    progress = { (journey.currentDay.toFloat() / journey.durationDays).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = VybeBrandCyan,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  Text(
                    text = "Today's Prompt: \"${journey.dailyPrompt}\"",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
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
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      items(growBadges, key = { it.id }) { badge ->
        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Spacer(modifier = Modifier.height(2.dp))
              Text(badge.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
              color = Color(0xFFF59E0B).copy(alpha = 0.15f),
              shape = RoundedCornerShape(8.dp)
            ) {
              Text(
                text = badge.badgeTier,
                color = Color(0xFFF59E0B),
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
      title = { Text("Day ${journey.currentDay} Reflection", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = journey.dailyPrompt,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
            Text("Share milestone to public feed", fontSize = 12.sp)
            Switch(
              checked = isSharedPublicly,
              onCheckedChange = { isSharedPublicly = it }
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.submitGrowReflection(
              journeyId = journey.id,
              dayNumber = journey.currentDay,
              text = reflectionNote.ifBlank { "Completed Day ${journey.currentDay} milestone" },
              isShared = isSharedPublicly
            )
            reflectionNote = ""
            showReflectionDialogForJourney = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan)
        ) {
          Text("Complete Day", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReflectionDialogForJourney = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
