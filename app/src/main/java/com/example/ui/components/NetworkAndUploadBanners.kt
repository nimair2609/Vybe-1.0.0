package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CallSession
import com.example.data.model.CallState
import com.example.data.model.User
import com.example.data.repository.UploadStatus
import com.example.ui.theme.VybeOnlineGreen
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Persistent online-first indicator shown whenever internet connectivity drops.
 */
@Composable
fun OfflineStatusBanner(isOnline: Boolean) {
  AnimatedVisibility(visible = !isOnline) {
    Surface(
      color = Color(0xFFB3261E),
      contentColor = Color.White,
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .testTag("offline_status_banner")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          Icons.Default.WifiOff,
          contentDescription = "Offline",
          modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = "No internet connection. Uploads and sync will resume when you're back online.",
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

/**
 * Displays real upload states (Uploading %, Processing, Published, or Failed + Retry)
 * for photos, videos, reels, and stories.
 *
 * - Positioned slightly lower below top app bar via [modifier].
 * - Auto-hides cleanly after completion (`Published`).
 * - Supports vertical pull/slide gesture to hide the banner visually without cancelling the active upload.
 */
@Composable
fun UploadStatusBanner(
  uploadStatus: UploadStatus,
  onRetry: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  var hiddenBySwipe by remember { mutableStateOf(false) }
  var dragOffsetY by remember { mutableFloatStateOf(0f) }

  // Reset swipe-hide state whenever upload transitions to a new major state (e.g. new upload starts, finishes, or fails)
  val statusTypeKey = when (uploadStatus) {
    is UploadStatus.Idle -> "IDLE"
    is UploadStatus.Uploading -> "UPLOADING_${uploadStatus.label}"
    is UploadStatus.Processing -> "PROCESSING"
    is UploadStatus.Published -> "PUBLISHED_${uploadStatus.message}"
    is UploadStatus.Failed -> "FAILED_${uploadStatus.errorMessage}"
  }

  LaunchedEffect(statusTypeKey) {
    hiddenBySwipe = false
    dragOffsetY = 0f
  }

  // Auto-dismiss Published state after 3.2 seconds
  LaunchedEffect(uploadStatus) {
    if (uploadStatus is UploadStatus.Published) {
      delay(3200)
      onDismiss()
    }
  }

  AnimatedVisibility(
    visible = uploadStatus !is UploadStatus.Idle && !hiddenBySwipe,
    enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    modifier = modifier
  ) {
    val bgColor = when (uploadStatus) {
      is UploadStatus.Failed -> Color(0xFF3B1219)
      is UploadStatus.Published -> Color(0xFF0E3324)
      else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.96f)
    }

    Surface(
      color = bgColor,
      shape = RoundedCornerShape(16.dp),
      tonalElevation = 6.dp,
      shadowElevation = 8.dp,
      modifier = Modifier
        .widthIn(max = 560.dp)
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .offset { IntOffset(0, dragOffsetY.roundToInt()) }
        .pointerInput(statusTypeKey) {
          detectVerticalDragGestures(
            onDragEnd = {
              if (dragOffsetY < -36f || dragOffsetY > 56f) {
                // Hide banner visually without cancelling any active upload
                hiddenBySwipe = true
                if (uploadStatus is UploadStatus.Published || uploadStatus is UploadStatus.Failed) {
                  onDismiss()
                }
              }
              dragOffsetY = 0f
            },
            onDragCancel = {
              dragOffsetY = 0f
            },
            onVerticalDrag = { change, dragAmount ->
              change.consume()
              dragOffsetY = (dragOffsetY + dragAmount).coerceIn(-120f, 90f)
            }
          )
        }
        .testTag("upload_status_banner")
    ) {
      Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
        // Subtle gesture handle pill
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(bottom = 6.dp)
            .width(30.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(
              if (uploadStatus is UploadStatus.Published || uploadStatus is UploadStatus.Failed)
                Color.White.copy(alpha = 0.35f)
              else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
            )
        )

        when (uploadStatus) {
          is UploadStatus.Uploading -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp
                )
                Spacer(Modifier.width(10.dp))
                Text(
                  text = uploadStatus.label,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Text(
                text = "${uploadStatus.progressPercent}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              Spacer(Modifier.width(6.dp))
              IconButton(
                onClick = { hiddenBySwipe = true },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  Icons.Default.KeyboardArrowUp,
                  contentDescription = "Hide upload banner",
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
              progress = { (uploadStatus.progressPercent / 100f).coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
            )
          }

          is UploadStatus.Processing -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                  text = uploadStatus.label,
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.Medium
                )
              }
              IconButton(
                onClick = { hiddenBySwipe = true },
                modifier = Modifier.size(32.dp)
              ) {
                Icon(
                  Icons.Default.KeyboardArrowUp,
                  contentDescription = "Hide upload banner",
                  modifier = Modifier.size(18.dp),
                  tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          is UploadStatus.Published -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  Icons.Default.CheckCircle,
                  contentDescription = "Published",
                  tint = Color(0xFF34D399),
                  modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = uploadStatus.message,
                  color = Color.White,
                  fontSize = 13.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
              IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
              }
            }
          }

          is UploadStatus.Failed -> {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Upload failed",
                tint = Color(0xFFFF8A80),
                modifier = Modifier.size(20.dp)
              )
              Spacer(Modifier.width(8.dp))
              Text(
                text = uploadStatus.errorMessage,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
              )
              if (uploadStatus.canRetry) {
                Spacer(Modifier.width(8.dp))
                TextButton(
                  onClick = onRetry,
                  modifier = Modifier
                    .heightIn(min = 40.dp)
                    .testTag("retry_upload_button")
                ) {
                  Text("Retry", color = Color(0xFF82B1FF), fontWeight = FontWeight.Bold)
                }
              }
              IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss error", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
              }
            }
          }

          UploadStatus.Idle -> {}
        }
      }
    }
  }
}

/**
 * Floating real-time Incoming Voice / Video Call banner shown across screens when a peer rings.
 */
@Composable
fun IncomingCallBanner(
  callSession: CallSession?,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
  onOpenCallScreen: () -> Unit,
  modifier: Modifier = Modifier
) {
  val isIncoming = callSession != null && callSession.state == CallState.INCOMING
  AnimatedVisibility(
    visible = isIncoming,
    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    modifier = modifier
  ) {
    if (callSession != null) {
      Surface(
        color = Color(0xFF141224),
        contentColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 12.dp,
        tonalElevation = 8.dp,
        modifier = Modifier
          .widthIn(max = 560.dp)
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 6.dp)
          .clickable { onOpenCallScreen() }
          .testTag("incoming_call_banner")
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          VybeAvatar(user = callSession.peerUser, size = 44.dp)
          Spacer(Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = callSession.peerUser.displayName,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = Color.White,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = if (callSession.isVideo) "Incoming VYBE Video Call..." else "Incoming VYBE Voice Call...",
              fontSize = 12.sp,
              color = Color.White.copy(alpha = 0.78f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Spacer(Modifier.width(8.dp))
          IconButton(
            onClick = onDecline,
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(Color(0xFFEF4444))
              .testTag("incoming_call_decline_button")
          ) {
            Icon(Icons.Default.CallEnd, contentDescription = "Decline call", tint = Color.White)
          }
          Spacer(Modifier.width(10.dp))
          IconButton(
            onClick = onAccept,
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(VybeOnlineGreen)
              .testTag("incoming_call_accept_button")
          ) {
            Icon(
              imageVector = if (callSession.isVideo) Icons.Default.Videocam else Icons.Default.Call,
              contentDescription = "Accept call",
              tint = Color.White
            )
          }
        }
      }
    }
  }
}

/**
 * Dedicated VYBE Matches audience manager with explicit Add and Remove controls,
 * searchable community members, and real-time Supabase persistence.
 */
@Composable
fun VybeMatchesManagerDialog(
  currentUserId: String,
  allUsers: List<User>,
  selectedMatches: List<User>,
  onAddMatch: (User) -> Unit,
  onRemoveMatch: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var filterQuery by remember { mutableStateOf("") }
  val selectedIds = remember(selectedMatches) { selectedMatches.map { it.id }.toSet() }

  val candidatePool = remember(allUsers, currentUserId, filterQuery) {
    val base = allUsers.filter { it.id != currentUserId }
    val q = filterQuery.trim().lowercase().removePrefix("@")
    if (q.isBlank()) base
    else base.filter {
      it.displayName.lowercase().contains(q) || it.username.lowercase().contains(q)
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .widthIn(max = 560.dp)
        .fillMaxWidth(0.94f)
        .heightIn(max = 600.dp),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "VYBE Matches",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${selectedMatches.size} selected • Only these people can view VYBE Matches stories",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close VYBE Matches")
          }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
          value = filterQuery,
          onValueChange = { filterQuery = it },
          placeholder = { Text("Search people by name or @username...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("vybe_matches_search_input")
        )

        Spacer(Modifier.height(12.dp))

        if (candidatePool.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (filterQuery.isBlank()) "LINK UP or search people on VYBE to add them to your VYBE Matches."
              else "No matching accounts found.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(candidatePool, key = { it.id }) { person ->
              val isAdded = selectedIds.contains(person.id)
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  if (person.avatarUrl.isNotBlank()) {
                    AsyncImage(
                      model = person.avatarUrl,
                      contentDescription = person.displayName,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                    )
                  } else {
                    Box(
                      modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = person.displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                      )
                    }
                  }

                  Spacer(Modifier.width(12.dp))

                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = person.displayName,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "@${person.username}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }

                  if (isAdded) {
                    OutlinedButton(
                      onClick = { onRemoveMatch(person.id) },
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("remove_vybe_match_${person.id}")
                    ) {
                      Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Remove")
                    }
                  } else {
                    Button(
                      onClick = { onAddMatch(person) },
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_vybe_match_${person.id}")
                    ) {
                      Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Add")
                    }
                  }
                }
              }
            }
          }
        }

        Spacer(Modifier.height(14.dp))

        Button(
          onClick = onDismiss,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("done_vybe_matches_button")
        ) {
          Text("Done (${selectedMatches.size} Selected)", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

/**
 * Instagram-style foreground real-time notification pill banner for DMs, reactions, comments,
 * follows, badge approvals, and official announcements.
 */
@Composable
fun RealtimeNotificationBanner(
  event: com.example.notifications.RealtimeInAppBannerEvent?,
  onOpen: (com.example.notifications.RealtimeInAppBannerEvent) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = event != null,
    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
    modifier = modifier
  ) {
    val active = event ?: return@AnimatedVisibility
    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
      contentColor = MaterialTheme.colorScheme.onSurface,
      shape = RoundedCornerShape(20.dp),
      tonalElevation = 8.dp,
      shadowElevation = 10.dp,
      modifier = Modifier
        .widthIn(max = 520.dp)
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 4.dp)
        .clickable {
          onOpen(active)
          onDismiss()
        }
        .testTag("realtime_notification_banner")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (!active.actorAvatarUrl.isNullOrBlank()) {
          AsyncImage(
            model = active.actorAvatarUrl,
            contentDescription = active.actorDisplayName ?: active.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
          )
        } else {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.NotificationsActive,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = active.title.ifBlank { "VYBE" },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = active.body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss notification banner",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }
  }
}

