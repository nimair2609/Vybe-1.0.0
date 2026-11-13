package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

/**
 * VYBE WHAT NEXT 2.0
 * Community predictions & audience consensus polls.
 * - Create, edit, delete, save, and publish audience polls.
 * - Configure questions, custom options, and duration (24h, 3 days, 7 days).
 * - Collect votes securely and display real-time percentage results.
 * - Enforce poll author permissions.
 */
@Composable
fun WhatNextScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val predictions by viewModel.predictions.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreatePollDialog by remember { mutableStateOf(false) }
  var pollTitle by remember { mutableStateOf("") }
  var pollCategory by remember { mutableStateOf("Culture & Music") }
  var pollDurationHours by remember { mutableIntStateOf(24) }
  var pollOptions by remember { mutableStateOf(listOf("Yes, absolutely", "No, unlikely")) }

  val categoryChoices = listOf("Culture & Music", "Cinema & OTT", "Technology & AI", "City Life", "Cricket & Sports")
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE What Next",
        subtitle = "Community Culture & Discovery Consensus",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "what_next_back_button",
        actions = {
          Button(
            onClick = {
              pollTitle = ""
              pollCategory = "Culture & Music"
              pollDurationHours = 24
              pollOptions = listOf("Yes, absolutely", "No, unlikely")
              showCreatePollDialog = true
            },
            colors = ButtonDefaults.buttonColors(
              containerColor = palette.accent,
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .heightIn(min = 40.dp)
              .testTag("create_poll_button")
          ) {
            Icon(Icons.Outlined.AddChart, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "New Poll",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold
            )
          }
        }
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
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(top = 14.dp, bottom = 72.dp)
    ) {
      item {
        VybeSurfaceCard(
          containerColor = palette.accent.copy(alpha = 0.10f),
          borderColor = palette.accent.copy(alpha = 0.35f),
          contentPadding = PaddingValues(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .background(palette.accent.copy(alpha = 0.16f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = null,
                tint = palette.accentSecondary,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "What Next Consensus",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "Predict and discover where music, cinema, technology, and city vibes are heading next. Vote with your intuition or launch your own poll.",
                fontSize = 12.sp,
                color = palette.textSecondary,
                lineHeight = 16.sp
              )
            }
          }
        }
      }

      item {
        Text(
          text = "Active Cultural Predictions (${predictions.size})",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = palette.textPrimary
        )
      }

      if (predictions.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.Poll,
            title = "No Active Predictions Yet",
            subtitle = "Launch a poll on music, cinema, tech, or city trends to gauge community consensus.",
            actionLabel = "Create First Poll",
            onAction = { showCreatePollDialog = true }
          )
        }
      }

      items(predictions, key = { it.id }) { pred ->
        val isAuthor = pred.authorId == currentUser?.id || pred.authorUsername == currentUser?.username

        VybeSurfaceCard(
          contentPadding = PaddingValues(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("poll_card_${pred.id}")
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              color = palette.accent.copy(alpha = 0.15f),
              shape = RoundedCornerShape(VybeTokens.Radius.xs)
            ) {
              Text(
                text = pred.category.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = pred.closingTimeText,
                fontSize = 11.sp,
                color = palette.textSecondary
              )
              if (isAuthor) {
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                  onClick = { viewModel.deletePredictionPoll(pred.id) },
                  modifier = Modifier.size(40.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete Poll",
                    tint = palette.danger,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = pred.title,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = palette.textPrimary
          )

          Spacer(modifier = Modifier.height(14.dp))

          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            pred.options.forEach { opt ->
              val isSelected = pred.userSelectedOptionId == opt.id
              val hasVoted = pred.userSelectedOptionId != null

              Surface(
                color = if (isSelected) palette.accent.copy(alpha = 0.16f) else palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                border = BorderStroke(
                  width = if (isSelected) 1.5.dp else 1.dp,
                  color = if (isSelected) palette.accent else palette.border
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                  .clickable { viewModel.votePrediction(pred.id, opt.id) }
                  .testTag("poll_option_${pred.id}_${opt.id}")
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                      if (isSelected) {
                        Icon(
                          imageVector = Icons.Default.CheckCircle,
                          contentDescription = null,
                          tint = palette.accent,
                          modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                      }
                      Text(
                        text = opt.text,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = palette.textPrimary
                      )
                    }
                    if (hasVoted) {
                      Spacer(modifier = Modifier.width(8.dp))
                      Text(
                        text = "${opt.percentage}%",
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = if (isSelected) palette.accent else palette.textSecondary
                      )
                    }
                  }

                  if (hasVoted) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                      progress = { opt.percentage / 100f },
                      modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                      color = if (isSelected) palette.accent else palette.accentSecondary,
                      trackColor = palette.card
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
              color = palette.textMuted
            )
            if (pred.userSelectedOptionId != null) {
              Surface(
                color = palette.success.copy(alpha = 0.14f),
                shape = RoundedCornerShape(VybeTokens.Radius.xs)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = palette.success,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "Vote Recorded",
                    fontSize = 10.sp,
                    color = palette.success,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Create Audience Poll Full-Page Screen
  if (showCreatePollDialog) {
    BackHandler { showCreatePollDialog = false }
    Dialog(
      onDismissRequest = { showCreatePollDialog = false },
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true
      )
    ) {
      Scaffold(
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier
          .fillMaxSize()
          .imePadding(),
        topBar = {
          VybeSubScreenTopBar(
            title = "New Audience Poll",
            subtitle = "VYBE What Next • Community Consensus",
            onBackClick = { showCreatePollDialog = false },
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
                colors = ButtonDefaults.buttonColors(
                  containerColor = palette.accent,
                  contentColor = Color.White
                ),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .heightIn(min = 40.dp)
                  .testTag("publish_poll_button")
              ) {
                Text(
                  text = "Publish Poll",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
              }
            }
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
        ) {
          item {
            OutlinedTextField(
              value = pollTitle,
              onValueChange = { pollTitle = it },
              label = { Text("Poll Question") },
              placeholder = { Text("e.g., Will Mumbai underground indie beats dominate 2027?", color = palette.textMuted) },
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("poll_question_input"),
              minLines = 3,
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            Text(
              text = "Category",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              items(categoryChoices) { cat ->
                val isSelected = pollCategory == cat
                FilterChip(
                  selected = isSelected,
                  onClick = { pollCategory = cat },
                  label = {
                    Text(
                      cat,
                      fontSize = 12.sp,
                      color = if (isSelected) palette.textPrimary else palette.textSecondary
                    )
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                    containerColor = palette.elevated
                  )
                )
              }
            }
          }

          item {
            Text(
              text = "Poll Duration",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              listOf(Pair("24 Hours", 24), Pair("3 Days", 72), Pair("7 Days", 168)).forEach { (lbl, hrs) ->
                val isSelected = pollDurationHours == hrs
                FilterChip(
                  selected = isSelected,
                  onClick = { pollDurationHours = hrs },
                  label = {
                    Text(
                      lbl,
                      fontSize = 12.sp,
                      color = if (isSelected) palette.textPrimary else palette.textSecondary
                    )
                  },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                    containerColor = palette.elevated
                  )
                )
              }
            }
          }

          item {
            Text(
              text = "Poll Options",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 13.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
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
                colors = fieldColors,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(VybeTokens.Radius.md)
              )
              if (pollOptions.size > 2) {
                IconButton(
                  onClick = {
                    val updated = pollOptions.toMutableList()
                    updated.removeAt(index)
                    pollOptions = updated
                  },
                  modifier = Modifier.size(48.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove option", tint = palette.danger)
                }
              }
            }
          }

          if (pollOptions.size < 4) {
            item {
              VybeSecondaryButton(
                text = "Add Another Option",
                onClick = {
                  pollOptions = pollOptions + "New Option ${pollOptions.size + 1}"
                },
                icon = Icons.Default.Add,
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          item {
            VybePrimaryButton(
              text = "Publish Audience Poll",
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
              testTag = "publish_poll_large_button",
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }
    }
  }
}
