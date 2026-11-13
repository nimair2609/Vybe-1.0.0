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
import androidx.compose.ui.graphics.SolidColor
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

private val VybeStoryRingGradient = Brush.sweepGradient(
  listOf(
    VybeSocialVioletBright,
    VybeGrowBlueBright,
    VybeBrandMagenta,
    VybeSocialVioletBright
  )
)

private val VybeMatchesRingGradient = Brush.sweepGradient(
  listOf(
    Color(0xFF10B981),
    Color(0xFF38BDF8),
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
  val palette = LocalVybePalette.current
  val nowMillis = System.currentTimeMillis()
  val activeStories = remember(stories) {
    stories.filter { s ->
      val expMillis = TimestampUtils.parseIsoMillis(s.expiresAtIso)
      if (expMillis != null) expMillis > nowMillis else s.expiresHoursRemaining > 0
    }
  }
  val ownStories = remember(activeStories, currentUser?.id) {
    activeStories
      .filter { currentUser != null && it.author.id.equals(currentUser.id, ignoreCase = true) }
      .sortedBy { TimestampUtils.parseIsoMillis(it.createdAtIso) ?: 0L }
  }
  val userHasStory = ownStories.isNotEmpty()
  val userOwnStory = ownStories.firstOrNull { !it.isViewed } ?: ownStories.firstOrNull()
  val hasUnviewedOwnStory = ownStories.any { !it.isViewed }

  val peerStoryGroups = remember(activeStories, currentUser?.id) {
    activeStories
      .filter { currentUser == null || !it.author.id.equals(currentUser.id, ignoreCase = true) }
      .groupBy { it.author.id.lowercase() }
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
    color = palette.canvas,
    modifier = modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.fillMaxWidth()) {
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 4.dp, bottom = 6.dp)
          .testTag("story_carousel_row"),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // 1. Current user "Your Story" bubble
        item(key = "current_user_story") {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(62.dp)
              .testTag("your_story_item")
          ) {
            Box(
              modifier = Modifier
                .size(56.dp)
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
                      .size(56.dp)
                      .clip(CircleShape)
                      .background(VybeStoryRingGradient)
                      .padding(2.dp)
                      .clip(CircleShape)
                      .background(palette.canvas)
                      .padding(1.5.dp)
                      .clip(CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 48.dp)
                  }
                } else {
                  Box(
                    modifier = Modifier
                      .size(54.dp)
                      .clip(CircleShape)
                      .border(1.2.dp, palette.border, CircleShape)
                      .padding(2.dp)
                      .clip(CircleShape),
                    contentAlignment = Alignment.Center
                  ) {
                    AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 48.dp)
                  }
                }

                Box(
                  modifier = Modifier
                    .size(19.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(palette.canvas)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .clickable { onAddStoryClick() },
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add another story",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                  )
                }
              } else {
                Box(
                  modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(1.dp, palette.border, CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 48.dp)
                }

                Box(
                  modifier = Modifier
                    .size(19.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(palette.canvas)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add story",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Your Story",
              fontFamily = InterFontFamily,
              color = if (userHasStory) palette.textPrimary else palette.textSecondary,
              fontWeight = if (userHasStory) FontWeight.SemiBold else FontWeight.Medium,
              fontSize = 11.sp,
              maxLines = 1,
              textAlign = TextAlign.Center,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        if (peerStoryGroups.isNotEmpty() || activeLiveStreams.any { it.isLive } || activeLiveRooms.any { it.isActive } || onManageVybeMatchesClick != null) {
          item(key = "my_story_divider") {
            Box(
              modifier = Modifier
                .height(46.dp)
                .width(1.dp)
                .background(palette.border)
            )
          }
        }

        // 2. LIVE Video Streams Highlight bubbles
        val activeVideoStreams = activeLiveStreams.filter { it.isLive }
        items(activeVideoStreams, key = { "live_stream_carousel_${it.id}" }) { stream ->
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(62.dp)
              .clickable { onJoinLiveStream?.invoke(stream) }
              .testTag("carousel_live_stream_${stream.id}")
          ) {
            Box(
              modifier = Modifier.size(56.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.sweepGradient(
                      listOf(palette.danger, VybeBrandMagenta, palette.warning, palette.danger)
                    )
                  )
                  .padding(2.dp)
                  .clip(CircleShape)
                  .background(palette.canvas)
                  .padding(1.5.dp)
                  .clip(CircleShape),
                contentAlignment = Alignment.Center
              ) {
                AvatarContent(stream.host.avatarUrl, stream.host.displayName, 48.dp)
              }

              Surface(
                color = palette.danger,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                  .align(Alignment.BottomCenter)
                  .offset(y = 3.dp)
              ) {
                Text(
                  text = "LIVE",
                  color = Color.White,
                  fontSize = 8.sp,
                  fontWeight = FontWeight.ExtraBold,
                  modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = stream.host.displayName.ifBlank { stream.title },
              fontFamily = InterFontFamily,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = palette.danger,
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
                .width(62.dp)
                .clickable { onJoinLiveRoom?.invoke(firstActiveRoom) }
                .testTag("live_highlight_item")
            ) {
              Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center
              ) {
                Box(
                  modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(VybeStoryRingGradient)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(palette.canvas)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Active Live Room",
                    tint = palette.accent,
                    modifier = Modifier.size(24.dp)
                  )
                }

                Surface(
                  color = palette.danger,
                  shape = RoundedCornerShape(4.dp),
                  modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 3.dp)
                ) {
                  Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = firstActiveRoom.topicTag.ifBlank { "Live Room" },
                fontFamily = InterFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
              )
            }
          }
        }

        // 4. Quick VYBE Matches Circle Action
        if (onManageVybeMatchesClick != null) {
          item(key = "manage_vybe_matches_chip") {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .width(62.dp)
                .clickable { onManageVybeMatchesClick() }
                .testTag("story_tray_vybe_matches_button")
            ) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .border(1.2.dp, palette.success.copy(alpha = 0.7f), CircleShape)
                  .background(palette.success.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Default.Group,
                  contentDescription = "Manage VYBE Matches",
                  tint = palette.success,
                  modifier = Modifier.size(22.dp)
                )
                if (vybeMatchesCount > 0) {
                  Surface(
                    color = palette.success,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.BottomEnd)
                  ) {
                    Text(
                      text = vybeMatchesCount.toString(),
                      color = Color.White,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Matches",
                fontFamily = InterFontFamily,
                color = palette.success,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        // 5. Active Peer Stories
        items(peerStoryGroups, key = { "user_story_${it.first.author.id}" }) { (representativeStory, hasUnviewed, isMatchesStory) ->
          val mutedBrush = SolidColor(palette.border)
          val ringBrush = when {
            !hasUnviewed -> mutedBrush
            isMatchesStory -> VybeMatchesRingGradient
            else -> VybeStoryRingGradient
          }

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(62.dp)
              .clickable { onStoryClick(representativeStory) }
              .testTag("story_user_${representativeStory.author.id}")
          ) {
            Box(
              modifier = Modifier.size(56.dp),
              contentAlignment = Alignment.Center
            ) {
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(CircleShape)
                  .background(ringBrush)
                  .padding(2.dp)
                  .clip(CircleShape)
                  .background(palette.canvas)
                  .padding(1.5.dp)
                  .clip(CircleShape),
                contentAlignment = Alignment.Center
              ) {
                AvatarContent(representativeStory.author.avatarUrl, representativeStory.author.displayName, 48.dp)
              }

              if (isMatchesStory) {
                Box(
                  modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(palette.canvas)
                    .padding(1.2.dp)
                    .clip(CircleShape)
                    .background(palette.success),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "VYBE Matches",
                    tint = Color.White,
                    modifier = Modifier.size(9.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = representativeStory.author.displayName.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: representativeStory.author.username,
              fontFamily = InterFontFamily,
              color = palette.textPrimary,
              fontWeight = if (hasUnviewed) FontWeight.SemiBold else FontWeight.Normal,
              fontSize = 11.sp,
              maxLines = 1,
              textAlign = TextAlign.Center,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      HorizontalDivider(
        thickness = 0.8.dp,
        color = palette.border
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
        .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue))),
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
