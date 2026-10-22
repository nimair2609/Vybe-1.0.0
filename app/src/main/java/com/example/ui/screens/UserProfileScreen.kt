package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val profileUser by viewModel.selectedUserProfile.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  var selectedTab by remember { mutableStateOf("Posts") }
  var showFollowListModal by remember { mutableStateOf<String?>(null) }

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
  var editCoverUrl by remember { mutableStateOf("") }
  var availabilityMessage by remember { mutableStateOf<String?>(null) }
  var isAvailable by remember { mutableStateOf(true) }

  val context = LocalContext.current
  var showGoLiveModal by remember { mutableStateOf(false) }
  var isLiveStreamingActive by remember { mutableStateOf(false) }
  var liveStreamTitle by remember { mutableStateOf("Casual Sunday Jam & Real Talk") }
  var isMicMuted by remember { mutableStateOf(false) }
  var isFrontCamera by remember { mutableStateOf(true) }

  var photoActionTarget by remember { mutableStateOf<String?>(null) } // "AVATAR" or "BANNER"
  var showPhotoActionSheet by remember { mutableStateOf(false) }

  val galleryPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      val uriStr = uri.toString()
      if (photoActionTarget == "AVATAR") {
        viewModel.updateProfilePhoto(uriStr)
      } else if (photoActionTarget == "BANNER") {
        viewModel.updateProfileBanner(uriStr)
      }
      showPhotoActionSheet = false
      photoActionTarget = null
    }
  }

  val cameraCaptureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: android.graphics.Bitmap? ->
    if (bitmap != null) {
      val uri = com.example.util.MediaUtils.saveBitmapToCache(
        context,
        bitmap,
        prefix = if (photoActionTarget == "AVATAR") "avatar" else "banner"
      )
      val uriStr = uri.toString()
      if (photoActionTarget == "AVATAR") {
        viewModel.updateProfilePhoto(uriStr)
      } else if (photoActionTarget == "BANNER") {
        viewModel.updateProfileBanner(uriStr)
      }
      showPhotoActionSheet = false
      photoActionTarget = null
    }
  }

  // Profile photo: still image only (Requirement L)
  val avatarPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      editAvatarUrl = uri.toString()
    }
  }

  // Banner photo: photo only
  val bannerPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      editCoverUrl = uri.toString()
    }
  }

  if (profileUser == null && currentUser == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("User not found")
    }
    return
  }

  val user = if (profileUser != null && currentUser != null && (profileUser!!.id == currentUser!!.id || profileUser!!.username.equals(currentUser!!.username, ignoreCase = true))) {
    currentUser!!
  } else {
    profileUser ?: currentUser!!
  }
  val isMe = currentUser?.id == user.id || currentUser?.username.equals(user.username, ignoreCase = true)

  val userPosts = remember(posts, user) {
    posts.filter { it.author.id == user.id || it.author.username.equals(user.username, ignoreCase = true) }
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
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .then(
              if (isMe) Modifier.clickable {
                photoActionTarget = "BANNER"
                showPhotoActionSheet = true
              } else Modifier
            )
        ) {
          if (user.coverUrl.isNotEmpty()) {
            AsyncImage(
              model = user.coverUrl,
              contentDescription = "Cover Banner",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(VybeVioletDark, ObsidianSurface, VybeVioletDark)
                  )
                )
            )
          }

          // Edit banner overlay button if owner
          if (isMe) {
            Surface(
              color = Color.Black.copy(alpha = 0.65f),
              shape = CircleShape,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clickable {
                  photoActionTarget = "BANNER"
                  showPhotoActionSheet = true
                }
                .testTag("edit_banner_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Edit Banner", tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit Banner", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
              }
            }
          }

          // Avatar with tap action
          Box(
            modifier = Modifier
              .align(Alignment.BottomStart)
              .offset(x = 16.dp, y = 42.dp)
          ) {
            VybeAvatar(
              user = user,
              size = 84.dp,
              onClick = {
                if (isMe) {
                  photoActionTarget = "AVATAR"
                  showPhotoActionSheet = true
                }
              }
            )
            if (isMe) {
              Box(
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .size(28.dp)
                  .clip(CircleShape)
                  .background(VybeViolet)
                  .clickable {
                    photoActionTarget = "AVATAR"
                    showPhotoActionSheet = true
                  }
                  .testTag("edit_avatar_badge"),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Edit Avatar", tint = Color.White, modifier = Modifier.size(15.dp))
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(48.dp))

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

          Spacer(modifier = Modifier.height(6.dp))

          // Public VYBE ID (Copyable with feedback)
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("VYBE Public ID", user.publicVybeId)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Copied Public ID: ${user.publicVybeId}", android.widget.Toast.LENGTH_SHORT).show()
              }
              .testTag("copy_public_id_chip")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Public ID: ${user.publicVybeId}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = VybeViolet
              )
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Copy Public VYBE ID",
                tint = VybeBrandCyan,
                modifier = Modifier.size(14.dp)
              )
            }
          }

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
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showFollowListModal = "Followers" }
                .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
              Text("${user.followersCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Followers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { showFollowListModal = "Following" }
                .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
              Text("${user.followingCount}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Following", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Actions: Follow / Message or Edit Profile
          if (!isMe) {
            val isFollowed = followingUserIds.contains(user.id)
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = { viewModel.toggleFollowUser(user.id) },
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

              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(
                onClick = {
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VYBE - Follow @${user.username}")
                    putExtra(Intent.EXTRA_TEXT, "Check out @${user.username} on VYBE! https://vybe.app/u/${user.username}")
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("share_peer_profile_button")
              ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share Profile", fontWeight = FontWeight.Bold)
              }
            }
          } else {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                    editCoverUrl = user.coverUrl
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
                    photoActionTarget = "AVATAR"
                    showPhotoActionSheet = true
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).testTag("change_profile_photo_btn")
                ) {
                  Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Change Photo", fontWeight = FontWeight.Bold)
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              OutlinedButton(
                onClick = {
                  photoActionTarget = "BANNER"
                  showPhotoActionSheet = true
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag("change_banner_btn")
              ) {
                Icon(Icons.Default.Panorama, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Change Banner", fontWeight = FontWeight.Bold)
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Creator Actions: Go Live & Share VYBE (Requirement L)
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = { viewModel.navigateTo(Screen.LIVE_STUDIO) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).testTag("profile_go_live_button")
                ) {
                  Icon(Icons.Default.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Go Live", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                  onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "Connect on VYBE")
                      putExtra(Intent.EXTRA_TEXT, "Join me on VYBE! Follow @${user.username} at https://vybe.app/u/${user.username}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share VYBE Profile"))
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.weight(1f).testTag("profile_share_vybe_button")
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Share VYBE", fontWeight = FontWeight.Bold)
                }
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
            onAddComment = { text -> viewModel.addComment(post.id, text) },
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

  // Photo & Banner Action Bottom Sheet (Requirement: Tap profile pic or banner to Edit / Remove / Replace via Gallery or Camera)
  if (showPhotoActionSheet && photoActionTarget != null) {
    ModalBottomSheet(
      onDismissRequest = {
        showPhotoActionSheet = false
        photoActionTarget = null
      },
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = if (photoActionTarget == "AVATAR") "Profile Photo" else "Profile Banner",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(18.dp))

        // Take Photo (Camera)
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              cameraCaptureLauncher.launch(null)
            }
            .testTag("action_sheet_camera_button")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VybeViolet)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Take Photo with Camera", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Choose from Gallery
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              galleryPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            }
            .testTag("action_sheet_gallery_button")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = VybeBrandCyan)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Choose from Gallery", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Remove
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              if (photoActionTarget == "AVATAR") {
                viewModel.updateProfilePhoto("")
              } else {
                viewModel.updateProfileBanner("")
              }
              showPhotoActionSheet = false
              photoActionTarget = null
            }
            .testTag("action_sheet_remove_button")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = VybeDanger)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = if (photoActionTarget == "AVATAR") "Remove Profile Photo" else "Remove Banner",
              fontWeight = FontWeight.SemiBold,
              fontSize = 14.sp,
              color = VybeDanger
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
          onClick = {
            showPhotoActionSheet = false
            photoActionTarget = null
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text("Cancel", fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
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

          // Media Selection (Requirement L)
          Text("Profile Photo (Still Image Only):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (editAvatarUrl.isNotBlank()) "Photo attached" else "Default avatar",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = {
                avatarPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Choose Photo", fontSize = 11.sp)
            }
          }

          Text("Banner (Photo Only):", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = if (editCoverUrl.isNotBlank()) "Banner attached" else "Default banner",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
              onClick = {
                bannerPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Default.Panorama, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Choose Banner", fontSize = 11.sp)
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
                profileVideoUrl = editVideoUrl,
                coverUrl = editCoverUrl
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

  // Go Live Modal (Requirement L)
  if (showGoLiveModal) {
    AlertDialog(
      onDismissRequest = {
        if (isLiveStreamingActive) isLiveStreamingActive = false
        showGoLiveModal = false
      },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Podcasts, contentDescription = null, tint = VybeMagenta)
          Spacer(modifier = Modifier.width(8.dp))
          Text("VYBE Live Studio", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          // Camera Preview Box
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF0F1720)),
            contentAlignment = Alignment.Center
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(
                if (isFrontCamera) Icons.Default.CameraFront else Icons.Default.CameraRear,
                contentDescription = null,
                tint = if (isLiveStreamingActive) VybeOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                if (isLiveStreamingActive) "Broadcasting Live via WebRTC/RTMP" else "Camera Preview Ready",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                if (isFrontCamera) "Front Camera • 1080p60" else "Rear Ultra-Wide • 1080p60",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp
              )
            }

            // Status Badge
            Surface(
              color = if (isLiveStreamingActive) VybeDanger else Color.Black.copy(alpha = 0.6f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isLiveStreamingActive) Color.White else Color.Gray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  if (isLiveStreamingActive) "LIVE • 14 viewers" else "OFFLINE",
                  color = Color.White,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold
                )
              }
            }
          }

          OutlinedTextField(
            value = liveStreamTitle,
            onValueChange = { liveStreamTitle = it },
            label = { Text("Stream Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )

          // Camera & Mic Controls
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
          ) {
            IconButton(
              onClick = { isMicMuted = !isMicMuted },
              modifier = Modifier
                .clip(CircleShape)
                .background(if (isMicMuted) VybeDanger.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Icon(
                if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Mute Mic",
                tint = if (isMicMuted) VybeDanger else MaterialTheme.colorScheme.onSurface
              )
            }

            IconButton(
              onClick = { isFrontCamera = !isFrontCamera },
              modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
              Icon(
                Icons.Default.FlipCameraAndroid,
                contentDescription = "Flip Camera",
                tint = VybeBrandCyan
              )
            }
          }

          Text(
            text = "Infrastructure: WebRTC ultra-low latency signaling active. Requires camera & microphone permissions.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 14.sp
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            isLiveStreamingActive = !isLiveStreamingActive
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isLiveStreamingActive) VybeDanger else VybeMagenta
          )
        ) {
          Text(if (isLiveStreamingActive) "End Broadcast" else "Start Live Stream", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            if (isLiveStreamingActive) isLiveStreamingActive = false
            showGoLiveModal = false
          }
        ) {
          Text("Close")
        }
      }
    )
  }

  // Followers & Following List Dialog
  if (showFollowListModal != null) {
    val title = showFollowListModal!!
    val listUsers = if (title == "Following") {
      allUsers.filter { followingUserIds.contains(it.id) || it.id != user.id }
    } else {
      allUsers.filter { it.id != user.id }
    }
    AlertDialog(
      onDismissRequest = { showFollowListModal = null },
      title = { Text(title, fontWeight = FontWeight.Bold) },
      text = {
        if (listUsers.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Text("No users yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
            items(listUsers) { u ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable {
                    showFollowListModal = null
                    viewModel.openUserProfile(u)
                  }
                  .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                VybeAvatar(user = u, size = 38.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(u.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    VerificationBadge(user = u)
                  }
                  Text("@${u.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (u.id != currentUser?.id) {
                  val isUFollowed = followingUserIds.contains(u.id)
                  OutlinedButton(
                    onClick = { viewModel.toggleFollowUser(u.id) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(if (isUFollowed) "Following" else "Follow", fontSize = 11.sp)
                  }
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showFollowListModal = null }) { Text("Close") }
      }
    )
  }
}
