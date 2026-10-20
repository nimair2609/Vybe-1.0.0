package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Post
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * 14.6 VYBE UNFILTERED
 * Product philosophy: "Back to Real. Back to Us."
 * - Dedicated UNFILTERED feed.
 * - Everyday raw authentic moments.
 * - Zero beauty filters, zero face reshaping, zero skin smoothing.
 * - NO Like button, NO Share button, NO engagement popularity rankings.
 * - Conversation-first comments and authentic connections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnfilteredScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val unfilteredPosts by viewModel.unfilteredPosts.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showCreateSheet by remember { mutableStateOf(false) }
  var postCaption by remember { mutableStateOf("") }
  var selectedRawPhoto by remember { mutableStateOf("https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600") }
  var activeCommentPostId by remember { mutableStateOf<String?>(null) }
  var commentText by remember { mutableStateOf("") }

  val rawPhotoOptions = listOf(
    "https://images.unsplash.com/photo-1517841905240-472988babdf9?w=600",
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=600",
    "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d?w=600",
    "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?w=600"
  )

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("unfiltered_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE UNFILTERED", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text("Back to Real. Back to Us.", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.Bold)
          }
        },
        actions = {
          FilledTonalButton(
            onClick = { showCreateSheet = true },
            modifier = Modifier.testTag("unfiltered_create_button")
          ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Raw Capture", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Philosophy Banner
      item {
        Spacer(modifier = Modifier.height(4.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(16.dp)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("🌿", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "No Likes. No Shares. No Filters.",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Text(
                text = "Pure authentic human moments. Face retouching and popularity rankings are completely disabled in this space.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      // Posts list
      items(unfilteredPosts, key = { it.id }) { post ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            // Author row
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                VybeAvatar(user = post.author, size = 40.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(post.author.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(post.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" • ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                      color = VybeOnlineGreen.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "Authentic Raw",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeOnlineGreen,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                      )
                    }
                  }
                }
              }

              Icon(
                imageVector = Icons.Default.FilterVintage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Caption
            if (post.caption.isNotBlank()) {
              Text(
                text = post.caption,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 19.sp
              )
              Spacer(modifier = Modifier.height(10.dp))
            }

            // Raw Photo (untouched)
            val firstMedia = post.mediaUrls.firstOrNull()
            if (firstMedia != null) {
              AsyncImage(
                model = firstMedia,
                contentDescription = "Unfiltered authentic moment",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(240.dp)
                  .clip(RoundedCornerShape(12.dp))
              )
              Spacer(modifier = Modifier.height(10.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            // Strictly NO Like button, NO Share button (Section 14.6 requirement)
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              TextButton(
                onClick = { activeCommentPostId = if (activeCommentPostId == post.id) null else post.id }
              ) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Comments", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = if (post.comments.isNotEmpty()) "${post.comments.size} Conversations" else "Start Conversation",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }

              Text(
                text = "Zero metric pressure",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            // Comments conversation thread
            if (post.comments.isNotEmpty()) {
              Spacer(modifier = Modifier.height(8.dp))
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                  .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                post.comments.forEach { c ->
                  Row {
                    Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(c.content, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                  }
                }
              }
            }

            // Comment input box
            if (activeCommentPostId == post.id) {
              Spacer(modifier = Modifier.height(8.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                OutlinedTextField(
                  value = commentText,
                  onValueChange = { commentText = it },
                  placeholder = { Text("Write a thoughtful response...", fontSize = 12.sp) },
                  singleLine = true,
                  modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                  onClick = {
                    if (commentText.isNotBlank()) {
                      viewModel.addUnfilteredComment(post.id, commentText)
                      commentText = ""
                    }
                  },
                  enabled = commentText.isNotBlank()
                ) {
                  Icon(Icons.Default.Send, contentDescription = "Send", tint = VybeViolet)
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }
  }

  // Raw Capture Publishing Sheet
  if (showCreateSheet) {
    AlertDialog(
      onDismissRequest = { showCreateSheet = false },
      title = { Text("Publish UNFILTERED Moment", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Camera snapshots in this mode are published without beauty effects, skin retouches, or filters.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          OutlinedTextField(
            value = postCaption,
            onValueChange = { postCaption = it },
            placeholder = { Text("What happened just now? No show-off, just life...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )

          Text("Select Raw In-App Snapshot:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            rawPhotoOptions.forEach { url ->
              val isSelected = selectedRawPhoto == url
              Box(
                modifier = Modifier
                  .size(56.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { selectedRawPhoto = url }
              ) {
                AsyncImage(
                  model = url,
                  contentDescription = null,
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                if (isSelected) {
                  Box(
                    modifier = Modifier
                      .fillMaxSize()
                      .background(VybeOnlineGreen.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (postCaption.isNotBlank() || selectedRawPhoto.isNotBlank()) {
              viewModel.createUnfilteredPost(postCaption, selectedRawPhoto)
              postCaption = ""
              showCreateSheet = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Publish Raw Moment")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateSheet = false }) { Text("Cancel") }
      }
    )
  }
}
