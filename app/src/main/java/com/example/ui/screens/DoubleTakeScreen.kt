package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import com.example.data.model.DoubleTakePrompt
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * VYBE DOUBLE TAKE 2.0
 * Independent prompt answers • Blind synchronized reveals • Match-based conversation unlocks.
 * - Create, edit, and delete Double Takes.
 * - Quick Yes/No or custom two-choice answers.
 * - Direct conversation unlock when answers match or creator accepts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTakeScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val prompts by viewModel.doubleTakePrompts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreateDialog by remember { mutableStateOf(false) }
  var newQuestion by remember { mutableStateOf("") }
  var newCategory by remember { mutableStateOf("Values & Lifestyle") }
  var newOptA by remember { mutableStateOf("Yes") }
  var newOptB by remember { mutableStateOf("No") }
  var selectedPeerIndex by remember { mutableIntStateOf(0) }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("double_take_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE Double Take", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Blind synchronized reveals • Match to chat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        actions = {
          Button(
            onClick = {
              newQuestion = ""
              newCategory = "Values & Lifestyle"
              newOptA = "Yes"
              newOptB = "No"
              showCreateDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("create_double_take_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text("Blind Reveals & Instant Match Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text(
                "Both participants answer the prompt without seeing each other's choice. When your answers match, a private conversation is instantly unlocked!",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      items(prompts, key = { it.id }) { prompt ->
        val isRevealed = prompt.isRevealed
        val isMatched = isRevealed && (prompt.myAnswer.equals(prompt.peerAnswer, ignoreCase = true) || prompt.isCreatorAccepted || prompt.isChatUnlocked)
        val isCreator = prompt.creatorId == currentUser?.id

        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("double_take_prompt_${prompt.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMatched) VybeOnlineGreen.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Category & Peer header
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = VybeMagenta.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = prompt.category.uppercase(),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeMagenta,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                VybeAvatar(user = prompt.peerUser, size = 24.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("@${prompt.peerUser.username}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                if (isCreator) {
                  Spacer(modifier = Modifier.width(6.dp))
                  IconButton(
                    onClick = { viewModel.deleteDoubleTakePrompt(prompt.id) },
                    modifier = Modifier.size(24.dp).testTag("delete_double_take_${prompt.id}")
                  ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = VybeDanger, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = prompt.promptQuestion,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two-Choice Yes / No buttons
            if (!prompt.isMyAnswerSubmitted) {
              Text("Choose your honest answer to reveal:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = { viewModel.submitDoubleTakeAnswer(prompt.id, prompt.optionA) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f).testTag("answer_optA_${prompt.id}")
                ) {
                  Text(prompt.optionA, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Button(
                  onClick = { viewModel.submitDoubleTakeAnswer(prompt.id, prompt.optionB) },
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f).testTag("answer_optB_${prompt.id}")
                ) {
                  Text(prompt.optionB, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
              }
            } else {
              // Answer Submitted State
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                // My Answer Card
                Surface(
                  color = VybeViolet.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text("Your Answer", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(prompt.myAnswer, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VybeViolet)
                  }
                }

                // Peer Answer Card (Hidden or Revealed)
                Surface(
                  color = if (isRevealed) VybeMagenta.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Column(modifier = Modifier.padding(10.dp)) {
                    Text("${prompt.peerUser.displayName}'s Answer", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    if (isRevealed) {
                      Text(prompt.peerAnswer, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VybeMagenta)
                    } else {
                      Text("🔒 Sealed until match", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }
            }

            // Match Unlocked Celebration & Conversation Link
            if (isMatched) {
              Spacer(modifier = Modifier.height(12.dp))
              Surface(
                color = VybeOnlineGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VybeOnlineGreen.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨ It's a Match!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = VybeOnlineGreen)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Both agreed on \"${prompt.myAnswer}\"", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Button(
                    onClick = {
                      viewModel.selectTab(com.example.ui.MainTab.INBOX)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("open_chat_double_take_${prompt.id}")
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Conversation with @${prompt.peerUser.username}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            } else if (isRevealed && isCreator && !prompt.isCreatorAccepted) {
              // Creator option to accept answer as match anyway
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedButton(
                onClick = { viewModel.acceptDoubleTakeMatch(prompt.id) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("accept_double_take_${prompt.id}")
              ) {
                Text("Accept Answer as Compatible & Unlock Chat", fontSize = 11.sp)
              }
            }
          }
        }
      }
    }
  }

  // Create Double Take Dialog
  if (showCreateDialog) {
    val peerOptions = remember {
      listOf(
        com.example.data.model.User(
          id = "u_priya",
          username = "priya_design",
          displayName = "Priya Krishnamurthy",
          avatarUrl = "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=400",
          bio = "UX Designer"
        ),
        com.example.data.model.User(
          id = "u_aarav",
          username = "aarav_beats",
          displayName = "Aarav Mehta",
          avatarUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400",
          bio = "Music Producer"
        )
      )
    }

    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create Double Take", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = newQuestion,
            onValueChange = { newQuestion = it },
            label = { Text("Prompt Question") },
            placeholder = { Text("e.g. Filter coffee or Matcha Latte?") },
            modifier = Modifier.fillMaxWidth().testTag("double_take_question_input"),
            maxLines = 3
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = newOptA,
              onValueChange = { newOptA = it },
              label = { Text("Option A") },
              modifier = Modifier.weight(1f),
              singleLine = true
            )
            OutlinedTextField(
              value = newOptB,
              onValueChange = { newOptB = it },
              label = { Text("Option B") },
              modifier = Modifier.weight(1f),
              singleLine = true
            )
          }

          Text("Send Prompt To:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            peerOptions.forEachIndexed { idx, peer ->
              FilterChip(
                selected = selectedPeerIndex == idx,
                onClick = { selectedPeerIndex = idx },
                label = { Text("@${peer.username}", fontSize = 11.sp) }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newQuestion.isNotBlank()) {
              val peer = peerOptions[selectedPeerIndex]
              viewModel.createDoubleTakePrompt(
                question = newQuestion.trim(),
                category = newCategory,
                optA = newOptA.trim().ifBlank { "Yes" },
                optB = newOptB.trim().ifBlank { "No" },
                peer = peer
              )
              showCreateDialog = false
            }
          },
          enabled = newQuestion.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
          modifier = Modifier.testTag("submit_double_take_button")
        ) {
          Text("Launch Double Take", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
      }
    )
  }
}
