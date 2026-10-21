package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Story
import com.example.data.model.StoryAudience
import com.example.data.model.User
import com.example.ui.theme.*

// Instagram story gradient ring colors
private val InstaStoryGradient = Brush.sweepGradient(
  listOf(
    Color(0xFFFBAA47), // warm orange
    Color(0xFFD91A46), // vibrant magenta-red
    Color(0xFFA60F93), // deep purple
    Color(0xFF833AB4), // royal violet
    Color(0xFFFBAA47)  // wrap back to orange
  )
)

// Instagram Close Friends neon green ring
private val InstaCloseFriendsGradient = Brush.sweepGradient(
  listOf(
    Color(0xFF2ECC71),
    Color(0xFF1ABC9C),
    Color(0xFF27AE60),
    Color(0xFF2ECC71)
  )
)

/**
 * INSTAGRAM-STYLE SEPARATE STORIES TRAY
 * Visually demarcated horizontal section above the feed with:
 * - Distinct surface container separated from feed posts with hairline divider
 * - Separated multi-layered ring (outer gradient -> gap spacing -> avatar)
 * - Close Friends green ring support with star badge
 * - "Your story" avatar with Instagram-style '+' action badge
 * - Seen/Unseen visual state distinction (colorful gradient vs muted gray)
 */
@Composable
fun StoryCarousel(
  stories: List<Story>,
  currentUser: User?,
  onStoryClick: (Story) -> Unit,
  onAddStoryClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val userHasStory = stories.any { it.author.id == currentUser?.id }
  val userOwnStory = stories.firstOrNull { it.author.id == currentUser?.id }

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
        horizontalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // 1. Current user "Your story" bubble
        item(key = "current_user_story") {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(72.dp)
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
                // If user has active story, show colorful outer ring + gap
                Box(
                  modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(InstaStoryGradient)
                    .padding(2.5.dp) // outer ring thickness
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface) // separator gap
                    .padding(2.dp) // white/dark gap between ring and image
                    .clip(CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 58.dp)
                }

                // Mini '+' badge on top of user story to add another
                Box(
                  modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(VybeBrandCyan)
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
                // Standard "Your story" without active story: avatar + blue '+' icon
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .border(
                      width = 1.dp,
                      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                      shape = CircleShape
                    ),
                  contentAlignment = Alignment.Center
                ) {
                  AvatarContent(currentUser?.avatarUrl, currentUser?.displayName ?: "Me", 62.dp)
                }

                // Signature Instagram '+' badge
                Box(
                  modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0095F6)), // Instagram blue
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
              text = "Your story",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              maxLines = 1,
              textAlign = TextAlign.Center,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        // 2. Peer stories (grouped by distinct authors or individual stories)
        items(stories.filter { it.author.id != currentUser?.id }, key = { it.id }) { story ->
          val isCloseFriends = story.audience == StoryAudience.CLOSE_FRIENDS
          val isViewed = story.isViewed

          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .width(72.dp)
              .clickable { onStoryClick(story) }
              .testTag("story_item_${story.id}")
          ) {
            Box(
              modifier = Modifier.size(68.dp),
              contentAlignment = Alignment.Center
            ) {
              if (!isViewed) {
                // UNVIEWED STORY: High-contrast ring (Sunset Gradient or Close Friends Green)
                val ringGradient = if (isCloseFriends) InstaCloseFriendsGradient else InstaStoryGradient
                Box(
                  modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(ringGradient)
                    .padding(2.5.dp) // Ring thickness
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface) // Iconic Insta gap
                    .padding(2.5.dp)
                    .clip(CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  AvatarContent(story.author.avatarUrl, story.author.displayName, 56.dp)
                }
              } else {
                // VIEWED STORY: Subtle muted hairline border
                Box(
                  modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .border(
                      width = 1.dp,
                      color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                      shape = CircleShape
                    )
                    .padding(3.dp)
                    .clip(CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  AvatarContent(story.author.avatarUrl, story.author.displayName, 58.dp)
                }
              }

              // Close Friends star badge overlay
              if (isCloseFriends) {
                Box(
                  modifier = Modifier
                    .size(18.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2ECC71)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Close Friends",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(5.dp))
            Text(
              text = story.author.displayName.split(" ").firstOrNull() ?: story.author.username,
              style = MaterialTheme.typography.labelSmall,
              color = if (isViewed) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
              fontWeight = if (!isViewed) FontWeight.SemiBold else FontWeight.Normal,
              fontSize = 11.sp,
              maxLines = 1,
              textAlign = TextAlign.Center,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }

      // Hairline bottom divider cleanly separating the stories field from feed content
      HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
      )
    }
  }
}

@Composable
private fun AvatarContent(url: String?, name: String, size: androidx.compose.ui.unit.Dp) {
  if (!url.isNullOrBlank()) {
    AsyncImage(
      model = url,
      contentDescription = "$name's avatar",
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
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
