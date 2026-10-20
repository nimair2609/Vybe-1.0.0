package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.data.model.User
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
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

  // 5-second video player modal state (Section 12)
  var showVideoPlayerModal by remember { mutableStateOf(false) }
  var showEditProfileModal by remember { mutableStateOf(false) }

  // Edit profile form state
  var editDisplayName by remember { mutableStateOf("") }
  var editFirstName by remember { mutableStateOf("") }
  var editLastName by remember { mutableStateOf("") }
  var editUsername by remember { mutableStateOf("") }
  var editBio by remember { mutableStateOf("") }
  var editAvatarUrl by remember { mutableStateOf("") }
  var editVideoUrl by remember { mutableStateOf<String?>("") }
  var availabilityMessage by remember { mutableStateOf<String?>(null) }
  var isAvailable by remember { mutableStateOf(true) }

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
            Icon(Icons.Default.Settings, contentDescription = "Settings")
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

          // Avatar with 5-second video tap action
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .offset(x = 16.dp, y = 40.dp)
          ) {
            VybeAvatar(
              user = user,
              size = 80.dp,
              onClick = {
                if (!user.profileVideoUrl.isNullOrEmpty()) {
                  showVideoPlayerModal = true
                }
              }
            )
          }

          // 5-Second Video Badge button
          if (!user.profileVideoUrl.isNullOrEmpty()) {
            Surface(
              color = VybeMagenta,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clickable { showVideoPlayerModal = true }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("▶ 5s Video", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
              }
            }
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

          // Actions: Follow / Message or Edit Profile
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
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = {
                  editDisplayName = user.displayName
                  editFirstName = user.firstName
                  editLastName = user.lastName
                  editUsername = user.username
                  editBio = user.bio
                  editAvatarUrl = user.avatarUrl
                  editVideoUrl = user.profileVideoUrl
                  showEditProfileModal = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).testTag("edit_profile_button")
              ) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Profile", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
              }

              OutlinedButton(
                onClick = {
                  if (!user.profileVideoUrl.isNullOrEmpty()) {
                    showVideoPlayerModal = true
                  } else {
                    editDisplayName = user.displayName
                    editFirstName = user.firstName
                    editLastName = user.lastName
                    editUsername = user.username
                    editBio = user.bio
                    editAvatarUrl = user.avatarUrl
                    editVideoUrl = "https://assets.mixkit.co/videos/preview/mixkit-urban-city-traffic-at-night-42861-large.mp4"
                    showEditProfileModal = true
                  }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (user.profileVideoUrl.isNullOrEmpty()) "+ 5s Video" else "View 5s Video", fontWeight = FontWeight.Bold)
              }
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

  // 5-Second Profile Video Player Preview Modal (Section 12)
  if (showVideoPlayerModal) {
    AlertDialog(
      onDismissRequest = { showVideoPlayerModal = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text("5-Second Profile Video", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Spacer(modifier = Modifier.width(8.dp))
          Surface(color = VybeMagenta.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
            Text("5s Max", color = VybeMagenta, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
          }
        }
      },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Box(
            modifier = Modifier
              .size(200.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
          ) {
            AsyncImage(
              model = user.avatarUrl,
              contentDescription = "Profile Video Still",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)),
              contentAlignment = Alignment.Center
            ) {
              Text("▶", fontSize = 24.sp, color = Color.White)
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Living avatar video for @${user.username} plays automatically on profile visits without sound.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showVideoPlayerModal = false }) { Text("Close") }
      }
    )
  }

  // Edit Profile Modal (Section 11 & 12)
  if (showEditProfileModal) {
    AlertDialog(
      onDismissRequest = { showEditProfileModal = false },
      title = { Text("Edit Profile & Media", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
              value = editFirstName,
              onValueChange = { editFirstName = it },
              label = { Text("First Name") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
              value = editLastName,
              onValueChange = { editLastName = it },
              label = { Text("Last Name") },
              singleLine = true,
              modifier = Modifier.weight(1f)
            )
          }

          OutlinedTextField(
            value = editUsername,
            onValueChange = {
              editUsername = it
              val (avail, msg) = viewModel.checkUserIdAvailability(it)
              isAvailable = avail
              availabilityMessage = msg
            },
            label = { Text("Rename User ID") },
            leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = VybeViolet) },
            supportingText = {
              if (availabilityMessage != null) {
                Text(
                  text = availabilityMessage ?: "",
                  color = if (isAvailable) VybeOnlineGreen else VybeDanger,
                  fontWeight = FontWeight.SemiBold
                )
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = editBio,
            onValueChange = { editBio = it },
            label = { Text("Bio") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
          )

          // 5-Second Video selector
          Text("5-Second Profile Video:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (editVideoUrl.isNullOrEmpty()) "No video attached" else "Video clip linked (5s max)",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
              TextButton(onClick = {
                editVideoUrl = "https://assets.mixkit.co/videos/preview/mixkit-urban-city-traffic-at-night-42861-large.mp4"
              }) {
                Text("Select Sample")
              }
              if (!editVideoUrl.isNullOrEmpty()) {
                TextButton(onClick = { editVideoUrl = null }) {
                  Text("Remove", color = VybeDanger)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (isAvailable) {
              viewModel.updateAccountProfile(
                firstName = editFirstName,
                lastName = editLastName,
                requestedUserId = editUsername,
                email = currentUser?.email ?: "",
                phone = currentUser?.phone ?: "",
                bio = editBio,
                avatarUrl = editAvatarUrl,
                profileVideoUrl = editVideoUrl
              )
              showEditProfileModal = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Save Changes")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEditProfileModal = false }) { Text("Cancel") }
      }
    )
  }
}
