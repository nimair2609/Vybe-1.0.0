package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.Conversation
import com.example.data.model.Post
import com.example.data.model.User
import com.example.data.repository.MessagingEligibility
import com.example.ui.VybeViewModel
import com.example.ui.theme.*
import com.example.util.QrCodeGenerator
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostShareBottomSheet(
  post: Post,
  viewModel: VybeViewModel,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val currentUser by viewModel.currentUser.collectAsState()
  val conversations by viewModel.conversations.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val blockedIds by viewModel.blockedUserIds.collectAsState()

  var searchQuery by remember { mutableStateOf("") }
  var noteInput by remember { mutableStateOf("") }
  var sendingTargetIds by remember { mutableStateOf(setOf<String>()) }
  var sentTargetIds by remember { mutableStateOf(setOf<String>()) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val isArchived = post.isArchived
  val isAuthor = currentUser?.id != null && post.author.id.equals(currentUser?.id, ignoreCase = true)
  val isRestrictedPrivate = post.author.isPrivate && !isAuthor
  val canShareExternally = !isArchived && !isRestrictedPrivate
  val canonicalPostUrl = remember(post.id) { QrCodeGenerator.buildCanonicalPostUrl(post.id) }

  val activeConversations = remember(conversations, searchQuery, blockedIds) {
    val q = searchQuery.trim().lowercase()
    conversations.filter { conv ->
      !conv.isBlocked &&
        !blockedIds.contains(conv.peerUser.id) &&
        (
          q.isEmpty() ||
            (conv.groupName?.lowercase()?.contains(q) == true) ||
            conv.peerUser.displayName.lowercase().contains(q) ||
            conv.peerUser.username.lowercase().contains(q)
          )
    }
  }

  val additionalUsers = remember(allUsers, conversations, currentUser?.id, searchQuery, blockedIds) {
    val q = searchQuery.trim().lowercase()
    val existingPeerIds = conversations.filter { !it.isGroup }.map { it.peerUser.id.lowercase() }.toSet()
    allUsers.filter { u ->
      !u.id.equals(currentUser?.id, ignoreCase = true) &&
        !blockedIds.contains(u.id) &&
        !existingPeerIds.contains(u.id.lowercase()) &&
        (
          q.isEmpty() ||
            u.displayName.lowercase().contains(q) ||
            u.username.lowercase().contains(q)
          )
    }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = MaterialTheme.colorScheme.surface,
    dragHandle = { BottomSheetDefaults.DragHandle() }
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .navigationBarsPadding()
        .imePadding()
        .testTag("post_share_bottom_sheet")
    ) {
      // Sheet Header + Post Preview
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (post.mediaUrls.isNotEmpty()) {
          AsyncImage(
            model = post.mediaUrls.first(),
            contentDescription = "Shared post thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(46.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
          )
          Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Share Post",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
          Text(
            text = "@${post.author.username}" + if (post.caption.isNotBlank()) " • ${post.caption}" else "",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      if (isArchived) {
        Surface(
          color = VybeDanger.copy(alpha = 0.12f),
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.35f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Archived posts are private to you and cannot be shared.",
              fontSize = 12.sp,
              color = VybeDanger
            )
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      } else {
        if (isRestrictedPrivate) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Private account post: external links are disabled to protect creator privacy.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Quick External Actions Row: Copy Link + Share via Android Chooser
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              clipboard?.setPrimaryClip(ClipData.newPlainText("VYBE Post Link", canonicalPostUrl))
              viewModel.incrementLocalPostShares(post.id)
              viewModel.postBannerMessage("Post link copied to clipboard")
              onDismiss()
            },
            enabled = canShareExternally,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("post_share_copy_link_btn")
          ) {
            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Link", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }

          Button(
            onClick = {
              val shareBody = buildString {
                append("${post.author.displayName} (@${post.author.username}) on VYBE")
                if (post.caption.isNotBlank()) {
                  append(":\n")
                  append(post.caption.take(200))
                }
                append("\n\n")
                append(canonicalPostUrl)
              }
              val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Post by @${post.author.username} on VYBE")
                putExtra(Intent.EXTRA_TEXT, shareBody)
              }
              val chooser = Intent.createChooser(sendIntent, "Share VYBE Post").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              context.startActivity(chooser)
              viewModel.incrementLocalPostShares(post.id)
              onDismiss()
            },
            enabled = canShareExternally,
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("post_share_external_btn")
          ) {
            Icon(Icons.Outlined.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Share via...", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Optional personal message when sending via VYBE DM
        OutlinedTextField(
          value = noteInput,
          onValueChange = {
            noteInput = it
            errorMessage = null
          },
          placeholder = { Text("Write a message (optional)...", fontSize = 12.sp) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("post_share_note_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Recipient search box
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search people or conversations...", fontSize = 12.sp) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("post_share_search_input")
        )

        if (!errorMessage.isNullOrBlank()) {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            color = VybeDanger.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = errorMessage!!,
              color = VybeDanger,
              fontSize = 12.sp,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 300.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (activeConversations.isEmpty() && additionalUsers.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = if (currentUser == null) "Sign in to share posts via Direct Message." else "No matching contacts found.",
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (activeConversations.isNotEmpty()) {
            item {
              Text(
                text = "Recent Chats",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            items(activeConversations, key = { "share_conv_${it.id}" }) { conv ->
              val targetKey = "conv:${conv.id}"
              val isSending = sendingTargetIds.contains(targetKey)
              val isSent = sentTargetIds.contains(targetKey)
              ShareRecipientRow(
                avatarUser = conv.peerUser,
                title = conv.groupName ?: (conv.customNickname ?: conv.peerUser.displayName),
                subtitle = if (conv.isGroup) "${conv.groupMembers.size} members" else "@${conv.peerUser.username}",
                isSending = isSending,
                isSent = isSent,
                enabled = !isRestrictedPrivate || conv.peerUser.id == post.author.id,
                testTag = "share_send_conv_${conv.id}",
                onSend = {
                  if (isSending || isSent) return@ShareRecipientRow
                  errorMessage = null
                  sendingTargetIds = sendingTargetIds + targetKey
                  scope.launch {
                    val result = viewModel.sharePostToConversationAwait(
                      post = post,
                      targetUser = if (conv.isGroup) null else conv.peerUser,
                      existingConversationId = conv.id,
                      optionalNote = noteInput
                    )
                    sendingTargetIds = sendingTargetIds - targetKey
                    result.onSuccess {
                      sentTargetIds = sentTargetIds + targetKey
                      viewModel.postBannerMessage("Sent to ${conv.groupName ?: conv.peerUser.displayName}")
                    }.onFailure { err ->
                      errorMessage = err.message ?: "Failed to send post. Please try again."
                    }
                  }
                }
              )
            }
          }

          if (additionalUsers.isNotEmpty()) {
            item {
              Text(
                text = "Suggested People",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
              )
            }
            items(additionalUsers, key = { "share_user_${it.id}" }) { user ->
              val eligibility = viewModel.checkMessagingEligibility(user)
              val canMsg = eligibility is MessagingEligibility.Allowed && (!isRestrictedPrivate || user.id == post.author.id)
              val targetKey = "user:${user.id}"
              val isSending = sendingTargetIds.contains(targetKey)
              val isSent = sentTargetIds.contains(targetKey)
              ShareRecipientRow(
                avatarUser = user,
                title = user.displayName,
                subtitle = when {
                  eligibility is MessagingEligibility.Denied -> eligibility.reason
                  else -> "@${user.username}"
                },
                isSending = isSending,
                isSent = isSent,
                enabled = canMsg,
                testTag = "share_send_user_${user.id}",
                onSend = {
                  if (!canMsg || isSending || isSent) return@ShareRecipientRow
                  errorMessage = null
                  sendingTargetIds = sendingTargetIds + targetKey
                  scope.launch {
                    val result = viewModel.sharePostToConversationAwait(
                      post = post,
                      targetUser = user,
                      existingConversationId = null,
                      optionalNote = noteInput
                    )
                    sendingTargetIds = sendingTargetIds - targetKey
                    result.onSuccess {
                      sentTargetIds = sentTargetIds + targetKey
                      viewModel.postBannerMessage("Sent to ${user.displayName}")
                    }.onFailure { err ->
                      errorMessage = err.message ?: "Failed to send post. Please try again."
                    }
                  }
                }
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))
    }
  }
}

@Composable
private fun ShareRecipientRow(
  avatarUser: User,
  title: String,
  subtitle: String,
  isSending: Boolean,
  isSent: Boolean,
  enabled: Boolean,
  testTag: String,
  onSend: () -> Unit
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    shape = RoundedCornerShape(14.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VybeAvatar(user = avatarUser, size = 38.dp, showPulseDot = false)
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          VerificationBadge(user = avatarUser, iconSize = 14.dp)
        }
        Text(
          text = subtitle,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Button(
        onClick = onSend,
        enabled = enabled && !isSending && !isSent,
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isSent) VybeOnlineGreen else VybeViolet,
          disabledContainerColor = if (isSent) VybeOnlineGreen.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = Modifier
          .defaultMinSize(minWidth = 74.dp, minHeight = 40.dp)
          .testTag(testTag)
      ) {
        when {
          isSending -> {
            CircularProgressIndicator(
              modifier = Modifier.size(15.dp),
              strokeWidth = 2.dp,
              color = Color.White
            )
          }
          isSent -> {
            Text("Sent", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
          }
          else -> {
            Text(
              text = "Send",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
fun SharedPostDetailDialog(viewModel: VybeViewModel) {
  val openedPost by viewModel.openedSharedPost.collectAsState()
  val lookupError by viewModel.sharedPostLookupError.collectAsState()
  val isResolving by viewModel.isResolvingSharedPost.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val followingIds by viewModel.followingUserIds.collectAsState()
  val requestedIds by viewModel.requestedFollowUserIds.collectAsState()
  val isFeedAudioEnabled by viewModel.isFeedAudioEnabled.collectAsState()

  if (!isResolving && openedPost == null && lookupError == null) return

  Dialog(
    onDismissRequest = { viewModel.dismissSharedPostModal() },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      color = MaterialTheme.colorScheme.background,
      modifier = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .navigationBarsPadding()
        .testTag("shared_post_detail_dialog")
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { viewModel.dismissSharedPostModal() },
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close shared post",
                tint = MaterialTheme.colorScheme.onBackground
              )
            }
            Text(
              text = "Shared Post",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

        when {
          isResolving -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = VybeViolet)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Loading shared post...",
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 13.sp
                )
              }
            }
          }
          lookupError != null -> {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(24.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = VybeDanger,
                    modifier = Modifier.size(40.dp)
                  )
                  Spacer(modifier = Modifier.height(12.dp))
                  Text(
                    text = "Post Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = lookupError!!,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Spacer(modifier = Modifier.height(16.dp))
                  Button(
                    onClick = { viewModel.dismissSharedPostModal() },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                  ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
          openedPost != null -> {
            val post = openedPost!!
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
            ) {
              PostCard(
                post = post,
                onLikeClick = { viewModel.toggleLike(post.id) },
                onReact = { rx -> viewModel.reactToPost(post.id, rx) },
                onSaveClick = { viewModel.toggleSave(post.id) },
                onCommentClick = { viewModel.loadCommentsForPost(post.id) },
                onAddComment = { text -> viewModel.addComment(post.id, text) },
                onLoadComments = { viewModel.loadCommentsForPost(post.id) },
                onShareClick = { viewModel.openPostShareSheet(post) },
                onUserClick = { user ->
                  viewModel.dismissSharedPostModal()
                  viewModel.openUserProfile(user)
                },
                onDeleteClick = {
                  viewModel.deletePost(post.id)
                  viewModel.dismissSharedPostModal()
                },
                onPostViewed = { viewModel.recordPostView(it) },
                currentUserId = currentUser?.id,
                shouldAutoPlay = true,
                isAudioEnabled = isFeedAudioEnabled,
                isHighlighted = true,
                isFollowing = followingIds.contains(post.author.id),
                isFollowRequested = requestedIds.contains(post.author.id) || post.author.isFollowRequestedByMe,
                followsMe = post.author.followsMe,
                onFollowClick = if (post.author.id != currentUser?.id) {
                  { viewModel.toggleFollowUser(post.author.id) }
                } else {
                  null
                },
                onToggleAudio = { enabled -> viewModel.setFeedAudioEnabled(enabled) }
              )
            }
          }
        }
      }
    }
  }
}
