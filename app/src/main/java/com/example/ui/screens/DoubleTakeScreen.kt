package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

/**
 * VYBE DOUBLE TAKE 2.0
 * Independent prompt answers • Blind synchronized reveals • Match-based conversation unlocks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoubleTakeScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val prompts by viewModel.doubleTakePrompts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var showCreateDialog by remember { mutableStateOf(false) }
  var newQuestion by remember { mutableStateOf("") }
  var newCategory by remember { mutableStateOf("Values & Lifestyle") }
  var newOptA by remember { mutableStateOf("Yes") }
  var newOptB by remember { mutableStateOf("No") }
  var selectedPeerIndex by remember { mutableIntStateOf(0) }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Double Take",
        subtitle = "Blind synchronized reveals • Match to chat",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "double_take_back_button",
        actions = {
          Button(
            onClick = {
              newQuestion = ""
              newCategory = "Values & Lifestyle"
              newOptA = "Yes"
              newOptB = "No"
              showCreateDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
            shape = RoundedCornerShape(VybeTokens.Radius.pill),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .padding(end = 4.dp)
              .heightIn(min = 40.dp)
              .testTag("create_double_take_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Prompt", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
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
        .padding(16.dp),
      contentPadding = PaddingValues(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        VybeSurfaceCard(elevated = true) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = palette.accent, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                "Blind Reveals & Instant Match Chat",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 13.sp,
                color = palette.textPrimary
              )
              Text(
                "Both participants answer the prompt without seeing each other's choice. When your answers match, a private conversation is instantly unlocked!",
                fontSize = 11.sp,
                color = palette.textSecondary,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      if (prompts.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.QuestionAnswer,
            title = "No Double Take prompts yet",
            subtitle = "Launch a blind reveal prompt to spark genuine conversations.",
            actionLabel = "New Prompt",
            onAction = { showCreateDialog = true }
          )
        }
      }

      items(prompts, key = { it.id }) { prompt ->
        val isRevealed = prompt.isRevealed
        val isMatched = isRevealed && (prompt.myAnswer.equals(prompt.peerAnswer, ignoreCase = true) || prompt.isCreatorAccepted || prompt.isChatUnlocked)
        val isCreator = prompt.creatorId == currentUser?.id

        Surface(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("double_take_prompt_${prompt.id}"),
          color = palette.card,
          shape = RoundedCornerShape(VybeTokens.Radius.lg),
          border = BorderStroke(
            1.dp,
            if (isMatched) palette.success.copy(alpha = 0.55f) else palette.border
          )
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            // Category & Type Header
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                  color = palette.accent.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(VybeTokens.Radius.xs)
                ) {
                  Text(
                    text = prompt.category.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFontFamily,
                    color = palette.accent,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  color = if (prompt.isPublic) palette.accentSecondary.copy(alpha = 0.16f) else palette.accent.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(VybeTokens.Radius.xs)
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                  ) {
                    Icon(
                      imageVector = if (prompt.isPublic) Icons.Outlined.Public else Icons.Outlined.Group,
                      contentDescription = null,
                      tint = if (prompt.isPublic) palette.accentSecondary else palette.accent,
                      modifier = Modifier.size(12.dp)
                    )
                    Text(
                      text = if (prompt.isPublic) "PUBLIC PROMPT" else "PRIVATE TAGGED",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (prompt.isPublic) palette.accentSecondary else palette.accent
                    )
                  }
                }
              }

              Row(verticalAlignment = Alignment.CenterVertically) {
                if (!prompt.isPublic) {
                  VybeAvatar(user = prompt.peerUser, size = 24.dp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("@${prompt.peerUser.username}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                }
                if (isCreator) {
                  Spacer(modifier = Modifier.width(6.dp))
                  IconButton(
                    onClick = { viewModel.deleteDoubleTakePrompt(prompt.id) },
                    modifier = Modifier.size(32.dp).testTag("delete_double_take_${prompt.id}")
                  ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = palette.danger, modifier = Modifier.size(16.dp))
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
              text = prompt.promptQuestion,
              fontWeight = FontWeight.Bold,
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 15.sp,
              lineHeight = 20.sp,
              color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (prompt.isPublic) {
              if (isCreator) {
                Text(
                  text = "Community Answers (${prompt.publicAnswers.size})",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  fontFamily = PlusJakartaSansFontFamily,
                  color = palette.accentSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (prompt.publicAnswers.isEmpty()) {
                  Surface(
                    color = palette.elevated,
                    border = BorderStroke(1.dp, palette.border),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = "No answers submitted yet. Your public prompt is live for anyone to answer!",
                      fontSize = 11.sp,
                      color = palette.textSecondary,
                      modifier = Modifier.padding(12.dp)
                    )
                  }
                } else {
                  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    prompt.publicAnswers.forEach { ans ->
                      Surface(
                        color = palette.elevated,
                        border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        modifier = Modifier.fillMaxWidth()
                      ) {
                        Row(
                          modifier = Modifier.padding(10.dp),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                          Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            VybeAvatar(user = ans.user, size = 32.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                              Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ans.user.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.textPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("@${ans.user.username}", fontSize = 10.sp, color = palette.textSecondary)
                              }
                              Text(
                                text = "Chose: \"${ans.answer}\" • ${ans.timestamp}",
                                fontSize = 11.sp,
                                color = palette.accent,
                                fontWeight = FontWeight.SemiBold
                              )
                            }
                          }

                          if (ans.isConnected) {
                            Surface(
                              color = palette.success.copy(alpha = 0.18f),
                              shape = RoundedCornerShape(VybeTokens.Radius.xs)
                            ) {
                              Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                              ) {
                                Icon(
                                  imageVector = Icons.Outlined.Check,
                                  contentDescription = null,
                                  tint = palette.success,
                                  modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                  text = "Connected",
                                  fontSize = 10.sp,
                                  color = palette.success,
                                  fontWeight = FontWeight.Bold
                                )
                              }
                            }
                          } else {
                            Button(
                              onClick = { viewModel.connectWithPublicDoubleTakeAnswer(prompt.id, ans.id) },
                              colors = ButtonDefaults.buttonColors(containerColor = palette.success, contentColor = Color.White),
                              shape = RoundedCornerShape(VybeTokens.Radius.pill),
                              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                              Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                          }
                        }
                      }
                    }
                  }
                }
              } else {
                if (!prompt.isMyAnswerSubmitted) {
                  Text("Submit your answer to this public prompt:", fontSize = 11.sp, color = palette.textSecondary)
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Button(
                      onClick = { viewModel.submitPublicDoubleTakeAnswer(prompt.id, prompt.optionA) },
                      colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("public_answer_optA_${prompt.id}")
                    ) {
                      Text(prompt.optionA, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = PlusJakartaSansFontFamily)
                    }
                    OutlinedButton(
                      onClick = { viewModel.submitPublicDoubleTakeAnswer(prompt.id, prompt.optionB) },
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      border = BorderStroke(1.dp, palette.border),
                      colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = palette.elevated,
                        contentColor = palette.textPrimary
                      ),
                      modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("public_answer_optB_${prompt.id}")
                    ) {
                      Text(prompt.optionB, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = PlusJakartaSansFontFamily)
                    }
                  }
                } else {
                  Surface(
                    color = palette.accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Row(
                      modifier = Modifier.padding(12.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                      Spacer(modifier = Modifier.width(8.dp))
                      Column {
                        Text("Your Answer: \"${prompt.myAnswer}\"", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.accent)
                        Text("Submitted to author. If they connect with your response, a chat will open!", fontSize = 10.sp, color = palette.textSecondary)
                      }
                    }
                  }
                }
              }
            } else {
              if (!prompt.isMyAnswerSubmitted) {
                Text("Choose your honest answer to reveal:", fontSize = 11.sp, color = palette.textSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Button(
                    onClick = { viewModel.submitDoubleTakeAnswer(prompt.id, prompt.optionA) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("answer_optA_${prompt.id}")
                  ) {
                    Text(prompt.optionA, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = PlusJakartaSansFontFamily)
                  }
                  OutlinedButton(
                    onClick = { viewModel.submitDoubleTakeAnswer(prompt.id, prompt.optionB) },
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    border = BorderStroke(1.dp, palette.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                      containerColor = palette.elevated,
                      contentColor = palette.textPrimary
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("answer_optB_${prompt.id}")
                  ) {
                    Text(prompt.optionB, fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = PlusJakartaSansFontFamily)
                  }
                }
              } else {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Surface(
                    color = palette.accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.weight(1f)
                  ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                      Text("Your Answer", fontSize = 10.sp, color = palette.textSecondary)
                      Spacer(modifier = Modifier.height(2.dp))
                      Text(prompt.myAnswer, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.accent)
                    }
                  }

                  Surface(
                    color = if (isRevealed) palette.accentSecondary.copy(alpha = 0.14f) else palette.elevated,
                    border = BorderStroke(1.dp, if (isRevealed) palette.accentSecondary.copy(alpha = 0.35f) else palette.border),
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.weight(1f)
                  ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                      Text("${prompt.peerUser.displayName}'s Answer", fontSize = 10.sp, color = palette.textSecondary)
                      Spacer(modifier = Modifier.height(2.dp))
                      if (isRevealed) {
                        Text(prompt.peerAnswer, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.accentSecondary)
                      } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                          Icon(Icons.Outlined.Lock, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(13.dp))
                          Text("Sealed until match", fontSize = 12.sp, color = palette.textSecondary)
                        }
                      }
                    }
                  }
                }
              }

              if (isMatched) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                  color = palette.success.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  border = BorderStroke(1.dp, palette.success.copy(alpha = 0.4f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = palette.success, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("It's a Match!", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.success)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Both agreed on \"${prompt.myAnswer}\"", fontSize = 11.sp, color = palette.textSecondary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                      onClick = {
                        viewModel.selectTab(com.example.ui.MainTab.INBOX)
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = palette.success, contentColor = Color.White),
                      shape = RoundedCornerShape(VybeTokens.Radius.pill),
                      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_chat_double_take_${prompt.id}")
                    ) {
                      Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Open Conversation with @${prompt.peerUser.username}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }
              } else if (isRevealed && isCreator && !prompt.isCreatorAccepted) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                  onClick = { viewModel.acceptDoubleTakeMatch(prompt.id) },
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  border = BorderStroke(1.dp, palette.accent),
                  colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.accent),
                  modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("accept_double_take_${prompt.id}")
                ) {
                  Text("Accept Answer as Compatible & Unlock Chat", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
              }
            }
          }
        }
      }
    }
  }

  // Create Double Take Dialog (Public vs Private Mode)
  if (showCreateDialog) {
    val peerOptions = remember(allUsers, currentUser?.id) {
      allUsers.filter { it.id != currentUser?.id }.take(12)
    }
    var isPublicMode by remember(peerOptions.isEmpty()) { mutableStateOf(peerOptions.isEmpty()) }

    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Create Double Take",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 16.sp,
          color = palette.textPrimary
        )
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text("Prompt Type", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FilterChip(
              selected = isPublicMode,
              onClick = { isPublicMode = true },
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.accent,
                selectedLabelColor = Color.White,
                containerColor = palette.elevated,
                labelColor = palette.textSecondary
              ),
              label = { Text("Public (Anyone)", fontSize = 11.sp) }
            )
            FilterChip(
              selected = !isPublicMode,
              onClick = { if (peerOptions.isNotEmpty()) isPublicMode = false },
              enabled = peerOptions.isNotEmpty(),
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.accent,
                selectedLabelColor = Color.White,
                containerColor = palette.elevated,
                labelColor = palette.textSecondary
              ),
              label = { Text("Private (Tag Friend)", fontSize = 11.sp) }
            )
          }

          OutlinedTextField(
            value = newQuestion,
            onValueChange = { newQuestion = it },
            label = { Text("Prompt Question") },
            placeholder = { Text("e.g. Filter coffee or Matcha Latte?", color = palette.textMuted) },
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth().testTag("double_take_question_input"),
            maxLines = 3,
            shape = RoundedCornerShape(VybeTokens.Radius.sm)
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            OutlinedTextField(
              value = newOptA,
              onValueChange = { newOptA = it },
              label = { Text("Option A") },
              colors = fieldColors,
              modifier = Modifier.weight(1f),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.sm)
            )
            OutlinedTextField(
              value = newOptB,
              onValueChange = { newOptB = it },
              label = { Text("Option B") },
              colors = fieldColors,
              modifier = Modifier.weight(1f),
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.sm)
            )
          }

          if (!isPublicMode && peerOptions.isNotEmpty()) {
            Text("Tag Friend to Answer:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(peerOptions.size) { idx ->
                val peer = peerOptions[idx]
                FilterChip(
                  selected = selectedPeerIndex == idx,
                  onClick = { selectedPeerIndex = idx },
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = palette.accent,
                    selectedLabelColor = Color.White,
                    containerColor = palette.elevated,
                    labelColor = palette.textSecondary
                  ),
                  label = { Text("@${peer.username}", fontSize = 11.sp) }
                )
              }
            }
          } else {
            Surface(
              color = palette.accentSecondary.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, palette.accentSecondary.copy(alpha = 0.35f)),
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Icon(Icons.Outlined.Public, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(16.dp))
                Text(
                  text = "Public mode: Any VYBE member across devices can answer your prompt and unlock a chat when compatible!",
                  fontSize = 11.sp,
                  color = palette.textPrimary,
                  lineHeight = 15.sp
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newQuestion.isNotBlank()) {
              val safeIdx = selectedPeerIndex.coerceIn(0, (peerOptions.size - 1).coerceAtLeast(0))
              val peer = if (!isPublicMode && peerOptions.isNotEmpty()) {
                peerOptions[safeIdx]
              } else {
                currentUser ?: com.example.data.model.User(id = "public", username = "community", displayName = "VYBE Community")
              }
              viewModel.createDoubleTakePrompt(
                question = newQuestion.trim(),
                category = newCategory,
                optA = newOptA.trim().ifBlank { "Yes" },
                optB = newOptB.trim().ifBlank { "No" },
                peer = peer,
                isPublic = isPublicMode || peerOptions.isEmpty()
              )
              showCreateDialog = false
            }
          },
          enabled = newQuestion.isNotBlank(),
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
          modifier = Modifier.testTag("submit_double_take_button")
        ) {
          Text(
            if (isPublicMode) "Launch Public Prompt" else "Launch Double Take",
            fontWeight = FontWeight.Bold,
            fontFamily = PlusJakartaSansFontFamily
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }
}
