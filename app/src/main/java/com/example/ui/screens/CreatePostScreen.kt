package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.MoodType
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeGradientButton
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val currentUser by viewModel.currentUser.collectAsState()
  var caption by remember { mutableStateOf("") }
  var selectedMood by remember { mutableStateOf<MoodType?>(null) }
  var selectedImage by remember { mutableStateOf<String?>(null) }

  val samplePhotos = listOf(
    "https://images.unsplash.com/photo-1518770660439-4636190af475?w=600",
    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=600",
    "https://images.unsplash.com/photo-1498050108023-c5249f4df085?w=600",
    "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"
  )

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("create_post_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text("Create Post", fontWeight = FontWeight.Bold)
        },
        actions = {
          TextButton(
            onClick = {
              if (caption.isNotBlank() || selectedImage != null) {
                viewModel.createPost(
                  caption = caption,
                  mood = selectedMood,
                  imageUrl = selectedImage
                )
              }
            },
            enabled = caption.isNotBlank() || selectedImage != null,
            modifier = Modifier.testTag("publish_post_button")
          ) {
            Text("Publish", fontWeight = FontWeight.Bold, color = VybeViolet)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
    ) {
      if (currentUser != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          VybeAvatar(user = currentUser!!, size = 40.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(currentUser!!.displayName, fontWeight = FontWeight.Bold)
            Text("Public to VYBE Community", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = caption,
        onValueChange = { caption = it },
        placeholder = { Text("What's on your mind? Share your energy...") },
        minLines = 4,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("create_post_caption_input")
      )

      Spacer(modifier = Modifier.height(14.dp))

      // Mood Zone Tagging
      Text(
        text = "Tag a Mood Zone (Optional):",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MoodType.values()) { mood ->
          val isSelected = selectedMood == mood
          Surface(
            color = if (isSelected) mood.accentColor.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, mood.accentColor) else null,
            modifier = Modifier.clickable {
              selectedMood = if (isSelected) null else mood
            }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(mood.emoji, fontSize = 14.sp)
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = mood.title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) mood.accentColor else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Attach Photo
      Text(
        text = "Add Photo Asset:",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
      )
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(samplePhotos) { photoUrl ->
          val isSelected = selectedImage == photoUrl
          Box(
            modifier = Modifier
              .size(80.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable { selectedImage = if (isSelected) null else photoUrl }
          ) {
            AsyncImage(
              model = photoUrl,
              contentDescription = "Sample photo",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            if (isSelected) {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(VybeViolet.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      VybeGradientButton(
        text = "Post to VYBE Feed",
        onClick = {
          if (caption.isNotBlank() || selectedImage != null) {
            viewModel.createPost(
              caption = caption,
              mood = selectedMood,
              imageUrl = selectedImage
            )
          }
        },
        enabled = caption.isNotBlank() || selectedImage != null,
        modifier = Modifier.testTag("publish_bottom_button")
      )
    }
  }
}
