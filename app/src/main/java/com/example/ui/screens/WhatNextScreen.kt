package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

/**
 * VYBE WHAT NEXT 2.0
 * Community predictions & audience consensus polls.
 * - Create, edit, delete, save, and publish audience polls.
 * - Configure questions, custom options, and duration (24h, 3 days, 7 days).
 * - Collect votes securely and display real-time percentage results.
 * - Enforce poll author permissions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatNextScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val predictions by viewModel.predictions.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreatePollDialog by remember { mutableStateOf(false) }
  var pollTitle by remember { mutableStateOf("") }
  var pollCategory by remember { mutableStateOf("Culture & Music") }
  var pollDurationHours by remember { mutableIntStateOf(24) }
  var pollOptions by remember { mutableStateOf(listOf("Yes, absolutely", "No, unlikely")) }

  val categoryChoices = listOf("Culture & Music", "Cinema & OTT", "Technology & AI", "City Life", "Cricket & Sports")

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("what_next_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE WHAT NEXT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Community Culture & Discovery Consensus", fontSize = 11.sp, color = VybeBrandCyan)
          }
        },
        actions = {
          Button(
            onClick = {
              pollTitle = ""
              pollCategory = "Culture & Music"
              pollDurationHours = 24
              pollOptions = listOf("Yes, absolutely", "No, unlikely")
              showCreatePollDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("create_poll_button")
          ) {
            Icon(Icons.Default.AddChart, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Poll", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = VybeViolet.copy(alpha = 0.15f)),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("🔮", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "What Next Consensus",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Predict and discover where music, cinema, technology, and city vibes are heading next. Vote with your intuition or launch your own poll.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
              )
            }
          }
        }
      }

      item {
        Text("Active Cultural Predictions (${predictions.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      }

      items(predictions, key = { it.id }) { pred ->
        val isAuthor = pred.authorId == currentUser?.id || pred.authorUsername == currentUser?.username

        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("poll_card_${pred.id}")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = VybeViolet.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = pred.category.uppercase(),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeViolet,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = pred.closingTimeText,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isAuthor) {
                  Spacer(modifier = Modifier.width(6.dp))
                  IconButton(
                    onClick = { viewModel.deletePredictionPoll(pred.id) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Poll", tint = VybeDanger, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = pred.title,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Voting Options
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              pred.options.forEach { opt ->
                val isSelected = pred.userSelectedOptionId == opt.id
                val hasVoted = pred.userSelectedOptionId != null

                Surface(
                  color = if (isSelected) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp),
                  border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, VybeViolet) else null,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.votePrediction(pred.id, opt.id) }
                    .testTag("poll_option_${pred.id}_${opt.id}")
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                          text = opt.text,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                          fontSize = 13.sp
                        )
                      }
                      if (hasVoted) {
                        Text(
                          text = "${opt.percentage}%",
                          fontWeight = FontWeight.ExtraBold,
                          fontSize = 13.sp,
                          color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                      }
                    }

                    // Progress bar once voted
                    if (hasVoted) {
                      Spacer(modifier = Modifier.height(6.dp))
                      LinearProgressIndicator(
                        progress = { opt.percentage / 100f },
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(6.dp)
                          .clip(RoundedCornerShape(3.dp)),
                        color = if (isSelected) VybeViolet else VybeBrandCyan,
                        trackColor = MaterialTheme.colorScheme.surface
                      )
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "${pred.totalVotes} total votes collected",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              if (pred.userSelectedOptionId != null) {
                Surface(
                  color = VybeOnlineGreen.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "Vote Recorded ✓",
                    fontSize = 10.sp,
                    color = VybeOnlineGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Create Audience Poll Full-Page Dialog
  if (showCreatePollDialog) {
    Dialog(
      onDismissRequest = { showCreatePollDialog = false },
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true
      )
    ) {
      Scaffold(
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { showCreatePollDialog = false }) {
                Icon(Icons.Default.Close, contentDescription = "Close")
              }
            },
            title = {
              Column {
                Text("New Audience Poll", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("VYBE WHAT NEXT • Community Consensus", fontSize = 11.sp, color = VybeBrandCyan)
              }
            },
            actions = {
              Button(
                onClick = {
                  if (pollTitle.isNotBlank() && pollOptions.all { it.isNotBlank() }) {
                    viewModel.createPredictionPoll(
                      title = pollTitle.trim(),
                      category = pollCategory,
                      durationHours = pollDurationHours,
                      options = pollOptions.map { it.trim() }
                    )
                    showCreatePollDialog = false
                  }
                },
                enabled = pollTitle.isNotBlank() && pollOptions.all { it.isNotBlank() },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .testTag("publish_poll_button")
              ) {
                Text("Publish Poll", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            )
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
              value = pollTitle,
              onValueChange = { pollTitle = it },
              label = { Text("Poll Question") },
              placeholder = { Text("e.g., Will Mumbai underground indie beats dominate 2027?") },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("poll_question_input"),
              minLines = 3,
              shape = RoundedCornerShape(14.dp)
            )
          }

          item {
            Text("Category", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              items(categoryChoices) { cat ->
                FilterChip(
                  selected = pollCategory == cat,
                  onClick = { pollCategory = cat },
                  label = { Text(cat, fontSize = 11.sp) }
                )
              }
            }
          }

          item {
            Text("Poll Duration", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(Pair("24 Hours", 24), Pair("3 Days", 72), Pair("7 Days", 168)).forEach { (lbl, hrs) ->
                FilterChip(
                  selected = pollDurationHours == hrs,
                  onClick = { pollDurationHours = hrs },
                  label = { Text(lbl, fontSize = 11.sp) }
                )
              }
            }
          }

          item {
            Text("Poll Options", fontSize = 13.sp, fontWeight = FontWeight.Bold)
          }

          items(pollOptions.indices.toList()) { index ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              OutlinedTextField(
                value = pollOptions[index],
                onValueChange = { newText ->
                  val updated = pollOptions.toMutableList()
                  updated[index] = newText
                  pollOptions = updated
                },
                label = { Text("Option ${index + 1}") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
              )
              if (pollOptions.size > 2) {
                IconButton(
                  onClick = {
                    val updated = pollOptions.toMutableList()
                    updated.removeAt(index)
                    pollOptions = updated
                  }
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove option", tint = VybeDanger)
                }
              }
            }
          }

          if (pollOptions.size < 4) {
            item {
              OutlinedButton(
                onClick = {
                  pollOptions = pollOptions + "New Option ${pollOptions.size + 1}"
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Another Option", fontSize = 12.sp)
              }
            }
          }

          item {
            Button(
              onClick = {
                if (pollTitle.isNotBlank() && pollOptions.all { it.isNotBlank() }) {
                  viewModel.createPredictionPoll(
                    title = pollTitle.trim(),
                    category = pollCategory,
                    durationHours = pollDurationHours,
                    options = pollOptions.map { it.trim() }
                  )
                  showCreatePollDialog = false
                }
              },
              enabled = pollTitle.isNotBlank() && pollOptions.all { it.isNotBlank() },
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("publish_poll_large_button")
            ) {
              Text("Publish Audience Poll", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }

          item {
            Spacer(modifier = Modifier.height(30.dp))
          }
        }
      }
    }
  }
}
