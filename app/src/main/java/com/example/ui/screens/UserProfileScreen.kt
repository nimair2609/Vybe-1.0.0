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
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val profileUser by viewModel.selectedUserProfile.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()

  var selectedTab by remember { mutableStateOf("Posts") }
  var isFollowed by remember { mutableStateOf(false) }

  if (profileUser == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("User not found")
    }
    return
  }

  val user = profileUser!!
  val isMe = currentUser?.id == user.id

  val userPosts = remember(posts, user) {
    posts.filter { it.author.id == user.id }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("profile_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(user.username, fontWeight = FontWeight.Bold)
        },
        actions = {
          IconButton(
            onClick = { viewModel.navigateTo(Screen.SETTINGS) },
            modifier = Modifier.testTag("profile_settings_button")
          ) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options")
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
    ) {
      // Cover & Avatar
      item {
        Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
          if (user.coverUrl.isNotEmpty()) {
            AsyncImage(
              model = user.coverUrl,
              contentDescription = "Cover",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
            )
          }

          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .offset(x = 16.dp, y = 40.dp)
          ) {
            VybeAvatar(user = user, size = 80.dp)
          }
        }

        Spacer(modifier = Modifier.height(44.dp))

        // Name, Bio, Badges
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = user.displayName,
              style = MaterialTheme.typography.headlineMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            VerificationBadge(user = user)
          }

          Text(
            text = "@${user.username} • Joined ${user.joinedDate}",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          if (user.bio.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = user.bio,
              style = MaterialTheme.typography.bodyMedium,
              lineHeight = 20.sp
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Stats Bar (Posts, Followers, Following)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("${user.postsCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Posts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("${user.followersCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Followers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text("${user.followingCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Following", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Actions: Follow & Message
          if (!isMe) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { isFollowed = !isFollowed },
                colors = ButtonDefaults.buttonColors(
                  containerColor = if (isFollowed) MaterialTheme.colorScheme.surfaceVariant else VybeViolet
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("follow_user_button")
              ) {
                Text(
                  text = if (isFollowed) "Following" else "Follow",
                  color = if (isFollowed) MaterialTheme.colorScheme.onSurface else Color.White,
                  fontWeight = FontWeight.Bold
                )
              }

              OutlinedButton(
                onClick = {
                  val convs = viewModel.conversations.value
                  val found = convs.firstOrNull { it.peerUser.id == user.id }
                  if (found != null) {
                    viewModel.openConversation(found)
                  }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("message_user_button")
              ) {
                Text("Message", fontWeight = FontWeight.Bold)
              }
            }
          } else {
            Button(
              onClick = { viewModel.navigateTo(Screen.SETTINGS) },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth().testTag("edit_profile_button")
            ) {
              Text("Edit Profile & Privacy", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Profile tabs
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            listOf("Posts", "Reels", "Saved").forEach { tab ->
              val isSelected = selectedTab == tab
              Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).clickable { selectedTab = tab }
              ) {
                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                  Text(
                    text = tab,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }
      }

      // Tab Content: User Posts
      if (userPosts.isEmpty()) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(40.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("No posts shared yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      } else {
        items(userPosts, key = { it.id }) { post ->
          PostCard(
            post = post,
            onLikeClick = { viewModel.toggleLike(post.id) },
            onSaveClick = { viewModel.toggleSave(post.id) },
            onCommentClick = {},
            onShareClick = {},
            onUserClick = {}
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }
}
