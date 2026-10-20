package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTakeScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val prompts by viewModel.doubleTakePrompts.collectAsState()
  var activePromptToAnswer by remember { mutableStateOf<String?>(null) }
  var answerText by remember { mutableStateOf("") }

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
            Text("VYBE Double Take", fontWeight = FontWeight.Bold)
            Text("Independent prompt answers • Blind synchronized reveals", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
              Text("Zero Spoilers & No Peeking", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Text(
                "Both participants answer the prompt without seeing each other's choice. Answers are revealed simultaneously only after both submit.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      items(prompts, key = { it.id }) { prompt ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("double_take_prompt_${prompt.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (prompt.isRevealed) VybeCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = VybeMagenta.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = prompt.category,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeMagenta,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
              }
              Surface(
                color = if (prompt.isRevealed) VybeOnlineGreen.copy(alpha = 0.15f) else VybeViolet.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = if (prompt.isRevealed) "REVEALED ✨" else "IN PROGRESS",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = if (prompt.isRevealed) VybeOnlineGreen else VybeViolet,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = prompt.promptQuestion,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Two participants boxes
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Me box
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text("You", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Spacer(modifier = Modifier.height(4.dp))
                  if (prompt.isMyAnswerSubmitted) {
                    Text(prompt.myAnswer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                  } else {
                    Text("Answer pending", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }

              // Peer box
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    VybeAvatar(user = prompt.peerUser, size = 18.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(prompt.peerUser.displayName.split(" ").first(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  if (prompt.isRevealed) {
                    Text(prompt.peerAnswer, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                  } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Default.Lock, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(12.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Hidden until you submit", fontSize = 11.sp, color = VybeViolet)
                    }
                  }
                }
              }
            }

            if (!prompt.isMyAnswerSubmitted) {
              Spacer(modifier = Modifier.height(12.dp))
              Button(
                onClick = { activePromptToAnswer = prompt.id },
                colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
                modifier = Modifier.fillMaxWidth().testTag("answer_prompt_${prompt.id}")
              ) {
                Text("Answer to Unlock Reveal", fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }
  }

  // Answer Dialog
  if (activePromptToAnswer != null) {
    AlertDialog(
      onDismissRequest = { activePromptToAnswer = null },
      title = { Text("Submit Your Answer", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Your response is securely stored and revealed only when both of you have submitted.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = answerText,
            onValueChange = { answerText = it },
            label = { Text("Your Answer") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth().testTag("double_take_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (answerText.isNotBlank()) {
              viewModel.submitDoubleTakeAnswer(activePromptToAnswer!!, answerText)
              activePromptToAnswer = null
              answerText = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta)
        ) {
          Text("Submit & Reveal")
        }
      },
      dismissButton = {
        TextButton(onClick = { activePromptToAnswer = null }) { Text("Cancel") }
      }
    )
  }
}
