package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

@Composable
fun ReelsViewerScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val posts by viewModel.posts.collectAsState()
  var isPlaying by remember { mutableStateOf(true) }
  var isMuted by remember { mutableStateOf(false) }

  val reelPost = posts.firstOrNull() ?: return

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    // Media content
    if (reelPost.mediaUrls.isNotEmpty()) {
      AsyncImage(
        model = reelPost.mediaUrls.first(),
        contentDescription = "Reel content",
        contentScale = ContentScale.Crop,
        modifier = Modifier
          .fillMaxSize()
          .clickable { isPlaying = !isPlaying }
      )
    }

    // Top subtle gradient & Back Button
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .align(Alignment.TopCenter)
        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = { viewModel.navigateBack() },
          modifier = Modifier.testTag("reels_back_button")
        ) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        Text(
          text = "VYBE Reels",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )

        IconButton(
          onClick = { isMuted = !isMuted },
          modifier = Modifier.testTag("reels_mute_button")
        ) {
          Icon(
            imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            contentDescription = "Mute audio",
            tint = Color.White
          )
        }
      }
    }

    // Right Side Action Bar (Likes, Comments, Remix, Share)
    Column(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .navigationBarsPadding()
        .padding(bottom = 60.dp, end = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Like
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = { viewModel.toggleLike(reelPost.id) },
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("reel_like_button")
        ) {
          Icon(
            imageVector = if (reelPost.isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = "Like",
            tint = if (reelPost.isLiked) VybeMagenta else Color.White
          )
        }
        Text(
          text = "${reelPost.likesCount}",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        )
      }

      // Comments
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = { /* Comments dialog */ },
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("reel_comment_button")
        ) {
          Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = Color.White)
        }
        Text(
          text = "${reelPost.commentsCount}",
          color = Color.White,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp
        )
      }

      // Remix Chain Action
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
          onClick = { /* Remix chain */ },
          modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .testTag("reel_remix_button")
        ) {
          Icon(Icons.Default.Transform, contentDescription = "Remix Chain", tint = VybeCyan)
        }
        Text(
          text = "Remix",
          color = VybeCyan,
          fontWeight = FontWeight.Bold,
          fontSize = 11.sp
        )
      }

      // Share
      IconButton(
        onClick = { /* Share */ },
        modifier = Modifier
          .size(46.dp)
          .clip(CircleShape)
          .background(Color.Black.copy(alpha = 0.5f))
          .testTag("reel_share_button")
      ) {
        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = Color.White)
      }
    }

    // Bottom info (Author, Caption, Sound, Mood)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomStart)
        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        .navigationBarsPadding()
        .padding(start = 16.dp, end = 80.dp, bottom = 20.dp, top = 40.dp)
    ) {
      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.clickable { viewModel.openUserProfile(reelPost.author) }
        ) {
          VybeAvatar(user = reelPost.author, size = 38.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = reelPost.author.displayName,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontSize = 15.sp
          )
          Spacer(modifier = Modifier.width(4.dp))
          VerificationBadge(user = reelPost.author)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = reelPost.caption,
          color = Color.White,
          fontSize = 13.sp,
          maxLines = 3,
          lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Sound Track Pill
        Surface(
          color = Color.White.copy(alpha = 0.2f),
          shape = RoundedCornerShape(12.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = reelPost.audioTrackTitle ?: "Original Audio - ${reelPost.author.displayName}",
              color = Color.White,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }
  }
}
