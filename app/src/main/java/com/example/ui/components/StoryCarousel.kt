package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.LiveStreamSession
import com.example.data.model.Story
import com.example.data.model.StoryAudience
import com.example.data.model.UnloadRoom
import com.example.data.model.User
import com.example.ui.theme.*
import com.example.util.TimestampUtils

// VYBE signature story ring gradients
private val VybeStoryRingGradient = Brush.sweepGradient(
  listOf(
    VybeViolet,
    VybeMagenta,
    VybeBrandCyan,
    VybeViolet
  )
)

private val VybeMatchesRingGradient = Brush.sweepGradient(
  listOf(
    Color(0xFF10B981),
    Color(0xFF06B6D4),
    Color(0xFF34D399),
    Color(0xFF10B981)
  )
)

@Composable
fun StoryCarousel(
  stories: List<Story>,
  currentUser: User?,
  onStoryClick: (Story) -> Unit,
  onAddStoryClick: () -> Unit,
  onManageVybeMatchesClick: (() -> Unit)? = null,
  vybeMatchesCount: Int = 0,
  activeLiveRooms: List<UnloadRoom> = emptyList(),
  onJoinLiveRoom: ((UnloadRoom) -> Unit)? = null,
  activeLiveStreams: List<LiveStreamSession> = emptyList(),
  onJoinLiveStream: ((LiveStreamSession) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val nowMillis = System.currentTimeMillis()
  val activeStories = remember(stories) {
    stories.filter { s ->
      val expMillis = TimestampUtils.parseIsoMillis(s.expiresAtIso)
      if (expMillis != null) expMillis > nowMillis else s.expiresHoursRemaining > 0
    }
  }
  val ownStories = remember(activeStories, currentUser?.id) {
    activeStories
      .filter { currentUser != null && it.author.id == currentUser.id }
      .sortedBy { TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
  }
  val userHasStory = ownStories.isNotEmpty()
  val userOwnStory = ownStories.firstOrNull { !it.isViewed } ?: ownStories.firstOrNull()
  val hasUnviewedOwnStory = ownStories.any { !it.isViewed }

  // Group peer stories by author so each user has exactly ONE bubble in the carousel
  val peerStoryGroups = remember(activeStories, currentUser?.id) {
    activeStories
      .filter { currentUser == null || it.author.id != currentUser.id }
      .groupBy { it.author.id }
      .values
      .map { userStories ->
        val sorted = userStories.sortedBy { TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
        val firstUnviewed = sorted.firstOrNull { !it.isViewed }
        val representative = firstUnviewed ?: sorted.first()
        val hasUnviewed = sorted.any { !it.isViewed }
        val isMatches = sorted.any { it.audience == StoryAudience.VYBE_MATCHES || it.audience == StoryAudience.CLOSE_FRIENDS }
        Triple(representative, hasUnviewed, isMatches)
      }
      .sortedWith(compareByDescending<Triple<Story, Boolean, Boolean>> { it.second }.thenByDescending { TimestampUtils.parseIsoMillis(it.first.createdAtIso) ?: 0L })
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 10.dp, bottom = 10.dp)
          .testTag("story_carousel_row"),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. Current user "Your Story" bubble
        item(key = "current_user_story") {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(74.dp)
              .testTag("your_story_item")
          ) {
            Box(
              modifier = Modifier
                .size(68.dp)
                .clickable {
                  if (userHasStory && userOwnStory != null) {
                    onStoryClick(userOwnStory)
                  } else {
                    onAddStoryClick()
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              if (userHasStory) {
                if (hasUnviewedOwnStory) {
                  Box(
                    modifier = Modifier
                      .size(68.dp)
                      .clip(CircleShape)
                      .background(VybeStoryRingGradient)
                      .padding(2.5.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surface)
                      .padding(2.dp)
                      .clip(CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 58.dp)
                  }
                } else {
                  Box(
                    modifier = Modifier
                      .size(66.dp)
                      .clip(CircleShape)
                      .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                        shape = CircleShape
                      )
                      .padding(3.dp)
                      .clip(CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 58.dp)
                  }
                }

                Box(
                  modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(VybeViolet)
                    .clickable { onAddStoryClick() },
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add another story",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                  )
                }
              } else {
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(
                      width = 1.dp,
                      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                      shape = CircleShape
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 62.dp)
                }

                Box(
                  modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(VybeViolet),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add story",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(5.dp))
            Text(
              text = "Your Story",
              style = MaterialTheme.typography.labelSmall,
              color = if (userHasStory) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = if (userHasStory) FontWeight.SemiBold else FontWeight.Normal,
              fontSize = 11.sp,
              maxLines = 1,
              textAlign = TextAlign.Center,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // Visual separator between My Story and other users' stories / live items
        if (peerStoryGroups.isNotEmpty() || activeLiveStreams.any { it.isLive } || activeLiveRooms.any { it.isActive } || onManageVybeMatchesClick != null) {
          item(key = "my_story_divider") {
            Box(
              modifier = Modifier
                .height(48.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            )
          }
        }

        // 2. LIVE Video Streams Highlight bubbles
        val activeVideoStreams = activeLiveStreams.filter { it.isLive }
        items(activeVideoStreams, key = { "live_stream_carousel_${it.id}" }) { stream ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(74.dp)
              .clickable { onJoinLiveStream?.invoke(stream) }
              .testTag("carousel_live_stream_${stream.id}")
          ) {
            Box(
              modifier = Modifier.size(68.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(68.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.sweepGradient(
                      listOf(
                        Color(0xFFEF4444),
                        VybeMagenta,
                        Color(0xFFF59E0B),
                        Color(0xFFEF4444)
                      )
                    )
                  )
                  .padding(2.5.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface)
                  .padding(2.dp)
                  .clip(CircleShape),
                contentAlignment = Alignment.Center
              ) {
                AvatarContent(stream.host.avatarUrl, stream.host.displayName, 58.dp)
              }

              // Glowing Red "LIVE" Badge
              Surface(
                color = Color(0xFFEF4444),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .offset(y = 4.dp)
              ) {
                Text(
                  text = "LIVE",
                  color = Color.White,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = stream.host.displayName.ifBlank { stream.title },
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFEF4444),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              textAlign = TextAlign.Center
            )
          }
        }

        // 3. LIVE Highlight bubble for active voice rooms
        val firstActiveRoom = activeLiveRooms.firstOrNull { it.isActive }
        if (firstActiveRoom != null) {
          item(key = "live_highlight_room_${firstActiveRoom.id}") {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(74.dp)
                .clickable { onJoinLiveRoom?.invoke(firstActiveRoom) }
                .testTag("live_highlight_item")
            ) {
              Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
              ) {
                // Pulsing Gradient Ring for Active Live Session
                Box(
                  modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                      Brush.sweepGradient(
                        listOf(
                          VybeMagenta,
                          Color(0xFFEF4444),
                          VybeViolet,
                          Color(0xFF10B981),
                          VybeMagenta
                        )
                      )
                    )
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(VybeMagenta.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Active Live Room",
                    tint = VybeMagenta,
                    modifier = Modifier.size(28.dp)
                  )
                }

                // "LIVE" Badge
                Surface(
                  color = Color(0xFFEF4444),
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 4.dp)
                ) {
                  Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = firstActiveRoom.topicTag.ifBlank { "Live Room" },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VybeMagenta,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
              )
            }
          }
        }

        // 2. Quick VYBE Matches Circle Action
        if (onManageVybeMatchesClick != null) {
          item(key = "manage_vybe_matches_chip") {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(76.dp)
                .clickable { onManageVybeMatchesClick() }
                .testTag("story_tray_vybe_matches_button")
            ) {
              Box(
                modifier = Modifier
                  .size(64.dp)
                  .clip(CircleShape)
                  .border(1.5.dp, Color(0xFF10B981).copy(alpha = 0.7f), CircleShape)
                  .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Group,
                  contentDescription = "Manage VYBE Matches",
                  tint = Color(0xFF10B981),
                  modifier = Modifier.size(26.dp)
                )
                if (vybeMatchesCount > 0) {
                  Surface(
                    color = Color(0xFF10B981),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                  ) {
                    Text(
                      text = vybeMatchesCount.toString(),
                      color = Color.White,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(5.dp))
              Text(
                text = "VYBE Matches",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF10B981),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // 3. Active Peer Stories (one bubble per user, unviewed gradient ring vs viewed muted ring)
        items(peerStoryGroups, key = { "user_story_${it.first.author.id}" }) { (representativeStory, hasUnviewed, isMatchesStory) ->
          val mutedBrush = androidx.compose.ui.graphics.SolidColor(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
          )
          val ringBrush: androidx.compose.ui.graphics.Brush = when {
            !hasUnviewed -> mutedBrush
            isMatchesStory -> VybeMatchesRingGradient
            else -> VybeStoryRingGradient
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(74.dp)
              .clickable { onStoryClick(representativeStory) }
              .testTag("story_user_${representativeStory.author.id}")
          ) {
            Box(
              modifier = Modifier.size(68.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(68.dp)
                  .clip(CircleShape)
                  .background(ringBrush)
                  .padding(2.5.dp)
                  .clip(CircleShape)
                  .background(MaterialTheme.colorScheme.surface)
                  .padding(2.5.dp)
                  .clip(CircleShape),
                contentAlignment = Alignment.Center
              ) {
                AvatarContent(representativeStory.author.avatarUrl, representativeStory.author.displayName, 56.dp)
              }

              if (isMatchesStory) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "VYBE Matches",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(5.dp))
            Text(
              text = representativeStory.author.displayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: representativeStory.author.username,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurface,
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp,
              maxLines = 1,
              textAlign = TextAlign.Center,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
      )
    }
  }
}

@Composable
private fun AvatarContent(url: String?, name: String, size: Dp) {
  if (!url.isNullOrBlank()) {
    AsyncImage(
      model = url,
      contentDescription = "$name's avatar",
      contentScale = ContentScale.Crop,
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
    )
  } else {
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(listOf(VybeViolet, VybeMagenta))
        ),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = name.take(1).uppercase(),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp
      )
    }
  }
}
