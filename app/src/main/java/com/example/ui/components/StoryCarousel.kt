package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Story
import com.example.data.model.User
import com.example.ui.theme.VybeCyan
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

@Composable
fun StoryCarousel(
  stories: List<Story>,
  currentUser: User?,
  onStoryClick: (Story) -> Unit,
  onAddStoryClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  LazyRow(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 12.dp),
    contentPadding = PaddingValues(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Current user "Your Story" item with plus badge
    item {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .width(68.dp)
          .clickable { onAddStoryClick() }
      ) {
        Box(modifier = Modifier.size(62.dp)) {
          if (currentUser != null) {
            VybeAvatar(user = currentUser, size = 60.dp, showPulseDot = false)
          }
          Box(
            modifier = Modifier
              .size(22.dp)
              .align(Alignment.BottomEnd)
              .clip(CircleShape)
              .background(Brush.linearGradient(listOf(VybeViolet, VybeMagenta))),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Add story",
              tint = Color.White,
              modifier = Modifier.size(16.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Your Story",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    // Community stories
    items(stories, key = { it.id }) { story ->
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .width(68.dp)
          .clickable { onStoryClick(story) }
      ) {
        Box(
          modifier = Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(Brush.sweepGradient(listOf(VybeViolet, VybeMagenta, VybeCyan, VybeViolet)))
            .padding(2.5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
          contentAlignment = Alignment.Center
        ) {
          VybeAvatar(user = story.author, size = 56.dp, showPulseDot = false)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = story.author.displayName.split(" ").first(),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
