package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.User
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.PostCard
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import com.example.util.MediaUtils
import kotlinx.coroutines.launch

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
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val followerUserIds by viewModel.followerUserIds.collectAsState()
  val incomingFollowRequestUserIds by viewModel.incomingFollowRequestUserIds.collectAsState()
  val blockedUserIds by viewModel.blockedUserIds.collectAsState()
  val restrictedUserIds by viewModel.restrictedUserIds.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var selectedTab by remember { mutableStateOf("Posts") }
  var showFollowListModal by remember { mutableStateOf<String?>(null) }
  var showEditProfileModal by remember { mutableStateOf(false) }
  var showPeerMenu by remember { mutableStateOf(false) }
  var showPeerReportDialog by remember { mutableStateOf(false) }
  var peerReportCategory by remember { mutableStateOf("Impersonation") }
  var peerReportDetails by remember { mutableStateOf("") }

  // Edit profile form state (strictly owner-only)
  var editFirstName by remember { mutableStateOf("") }
  var editLastName by remember { mutableStateOf("") }
  var editUsername by remember { mutableStateOf("") }
  var editBio by remember { mutableStateOf("") }
  var editAvatarUrl by remember { mutableStateOf("") }
  var editVideoUrl by remember { mutableStateOf<String?>("") }
  var editCoverUrl by remember { mutableStateOf("") }
  var editIsPrivate by remember { mutableStateOf(false) }
  var availabilityMessage by remember { mutableStateOf<String?>(null) }
  var isAvailable by remember { mutableStateOf(true) }

  var photoActionTarget by remember { mutableStateOf<String?>(null) } // "AVATAR" or "BANNER"
  var showPhotoActionSheet by remember { mutableStateOf(false) }

  if (profileUser == null && currentUser == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("User not found")
    }
    return
  }

  // Strict ID-based ownership check — never match by username fallback alone
  val user: User = remember(profileUser, currentUser, allUsers) {
    val target = profileUser ?: currentUser!!
    if (currentUser != null && target.id == currentUser!!.id) {
      currentUser!!
    } else {
      allUsers.firstOrNull { it.id == target.id } ?: target
    }
  }
  val isMe = currentUser != null && currentUser!!.id == user.id

  LaunchedEffect(user.id) {
    if (!isMe && selectedTab == "Saved") {
      selectedTab = "Posts"
    }
  }

  val galleryPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null && isMe) {
      val uriStr = MediaUtils.copyUriToLocalCache(
        context,
        uri,
        prefix = if (photoActionTarget == "AVATAR") "avatar" else "banner"
      )
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
    if (bitmap != null && isMe) {
      val uri = MediaUtils.saveBitmapToCache(
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

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      cameraCaptureLauncher.launch(null)
    } else {
      coroutineScope.launch {
        snackbarHostState.showSnackbar("Camera permission is required to capture a photo. You can still choose from Gallery.")
      }
    }
  }

  val avatarPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null && isMe) {
      editAvatarUrl = MediaUtils.copyUriToLocalCache(context, uri, "avatar_edit")
    }
  }

  val bannerPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null && isMe) {
      editCoverUrl = MediaUtils.copyUriToLocalCache(context, uri, "banner_edit")
    }
  }

  val canViewContent = isMe || viewModel.canViewUserContent(user)
  val isBlocked = blockedUserIds.contains(user.id)
  val isRestricted = restrictedUserIds.contains(user.id)
  val isFollowed = followingUserIds.contains(user.id)
  val isFollowRequested = requestedFollowUserIds.contains(user.id) || user.isFollowRequestedByMe
  val followsMe = followerUserIds.contains(user.id) || user.followsMe
  val hasIncomingRequestFromUser = incomingFollowRequestUserIds.contains(user.id) || user.hasPendingFollowRequestForMe
  val isInVybeMatches = remember(vybeMatches, user.id) { vybeMatches.any { it.id == user.id } }

  val nonArchivedUserPosts = remember(posts, user.id) {
    posts.filter { it.author.id == user.id && !it.isArchived }
  }

  val displayedTabPosts = remember(nonArchivedUserPosts, posts, selectedTab, isMe, canViewContent) {
    if (!canViewContent) {
      emptyList()
    } else {
      when (selectedTab) {
        "Reels" -> nonArchivedUserPosts.filter {
          it.mediaType == MediaType.REEL || it.mediaType == MediaType.VIDEO || it.isVybesReel
        }
        "Saved" -> if (isMe) posts.filter { it.isSaved && !it.isArchived } else emptyList()
        else -> nonArchivedUserPosts
      }
    }
  }

  val displayedPostsCount = if (canViewContent) maxOf(user.postsCount, nonArchivedUserPosts.size) else user.postsCount
  val displayedFollowersCount = if (isMe) maxOf(user.followersCount, followerUserIds.size) else user.followersCount
  val displayedFollowingCount = if (isMe) maxOf(user.followingCount, followingUserIds.size) else user.followingCount

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (user.isPrivate) {
              Icon(
                Icons.Default.Lock,
                contentDescription = "Private Account",
                tint = VybeViolet,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
              text = "@${user.username}",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
        actions = {
          if (isMe) {
            IconButton(
              onClick = { viewModel.navigateTo(Screen.SETTINGS) },
              modifier = Modifier.testTag("profile_settings_button")
            ) {
              Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
          } else {
            Box {
              IconButton(
                onClick = { showPeerMenu = true },
                modifier = Modifier.testTag("peer_profile_options_button")
              ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Profile Options")
              }
              DropdownMenu(
                expanded = showPeerMenu,
                onDismissRequest = { showPeerMenu = false }
              ) {
                DropdownMenuItem(
                  text = { Text("Share Profile") },
                  leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                  onClick = {
                    showPeerMenu = false
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "VYBE - @${user.username}")
                      putExtra(Intent.EXTRA_TEXT, "Connect with @${user.username} on VYBE (ID: ${user.publicVybeId})")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                  }
                )
                DropdownMenuItem(
                  text = { Text(if (isInVybeMatches) "Remove from VYBE Matches" else "Add to VYBE Matches") },
                  leadingIcon = {
                    Icon(
                      Icons.Default.Star,
                      contentDescription = null,
                      tint = Color(0xFF10B981)
                    )
                  },
                  onClick = {
                    showPeerMenu = false
                    if (isInVybeMatches) viewModel.removeVybeMatch(user.id) else viewModel.addVybeMatch(user)
                  }
                )
                HorizontalDivider()
                DropdownMenuItem(
                  text = { Text(if (isRestricted) "Unrestrict Account" else "Restrict Account") },
                  leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                  onClick = {
                    showPeerMenu = false
                    viewModel.toggleRestrictUser(user.id)
                  }
                )
                DropdownMenuItem(
                  text = { Text(if (isBlocked) "Unblock Account" else "Block Account", color = VybeDanger) },
                  leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = VybeDanger) },
                  onClick = {
                    showPeerMenu = false
                    viewModel.toggleBlockUser(user.id)
                  }
                )
                DropdownMenuItem(
                  text = { Text("Report Account", color = VybeDanger) },
                  leadingIcon = { Icon(Icons.Default.Report, contentDescription = null, tint = VybeDanger) },
                  onClick = {
                    showPeerMenu = false
                    peerReportDetails = ""
                    showPeerReportDialog = true
                  }
                )
              }
            }
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
      item {
        // Cover & Avatar
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
                  Brush.horizontalGradient(
                    listOf(VybeVioletDark, ObsidianSurface, VybeVioletDark)
                  )
                )
            )
          }

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

        // Name, Privacy Badge, Bio, Stats
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = user.displayName,
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            VerificationBadge(user = user)
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = "@${user.username} • Joined ${user.joinedDate}",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
              color = if (user.isPrivate) VybeViolet.copy(alpha = 0.16f) else VybeOnlineGreen.copy(alpha = 0.14f),
              shape = RoundedCornerShape(6.dp)
            ) {
              Text(
                text = if (user.isPrivate) "Private" else "Public",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (user.isPrivate) VybeViolet else VybeOnlineGreen,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
            if (!isMe && followsMe) {
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "Follows you",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          // Public VYBE ID (Copyable)
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
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
              Text("$displayedPostsCount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Posts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = canViewContent) { showFollowListModal = "Followers" }
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("profile_followers_stat")
            ) {
              Text("$displayedFollowersCount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Followers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = canViewContent) { showFollowListModal = "Following" }
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .testTag("profile_following_stat")
            ) {
              Text("$displayedFollowingCount", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text("Following", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Incoming Follow Request Approval Banner
          if (!isMe && hasIncomingRequestFromUser) {
            Card(
              colors = CardDefaults.cardColors(containerColor = VybeViolet.copy(alpha = 0.12f)),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("incoming_follow_request_banner")
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "@${user.username} wants to follow you",
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Button(
                    onClick = { viewModel.respondToFollowRequest(user.id, approve = true) },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 44.dp)
                      .testTag("approve_follow_request_button")
                  ) {
                    Text("Approve", fontWeight = FontWeight.Bold, color = Color.White)
                  }
                  OutlinedButton(
                    onClick = { viewModel.respondToFollowRequest(user.id, approve = false) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 44.dp)
                      .testTag("decline_follow_request_button")
                  ) {
                    Text("Decline", fontWeight = FontWeight.SemiBold)
                  }
                }
              }
            }
          }

          // Profile Actions: Strict Owner vs Peer Controls
          if (!isMe) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                val followLabel = when {
                  isBlocked -> "Unblock"
                  isFollowed -> "Following"
                  isFollowRequested -> "Requested"
                  followsMe -> "Follow Back"
                  else -> "Follow"
                }
                val followContainer = when {
                  isBlocked -> VybeDanger
                  isFollowed || isFollowRequested -> MaterialTheme.colorScheme.surfaceVariant
                  else -> VybeViolet
                }
                val followTextColor = when {
                  isFollowed || isFollowRequested -> MaterialTheme.colorScheme.onSurface
                  else -> Color.White
                }

                Button(
                  onClick = {
                    if (isBlocked) {
                      viewModel.toggleBlockUser(user.id)
                    } else {
                      viewModel.toggleFollowUser(user.id)
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = followContainer),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("follow_user_button")
                ) {
                  Text(
                    text = followLabel,
                    color = followTextColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                  )
                }

                OutlinedButton(
                  onClick = {
                    viewModel.startConversationWithUser(user) { deniedReason ->
                      coroutineScope.launch {
                        snackbarHostState.showSnackbar(deniedReason)
                      }
                    }
                  },
                  enabled = !isBlocked,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("message_user_button")
                ) {
                  Icon(
                    Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Message", fontWeight = FontWeight.Bold, maxLines = 1)
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    if (isInVybeMatches) {
                      viewModel.removeVybeMatch(user.id)
                    } else {
                      viewModel.addVybeMatch(user)
                    }
                  },
                  border = BorderStroke(
                    1.dp,
                    if (isInVybeMatches) Color(0xFF10B981) else MaterialTheme.colorScheme.outline
                  ),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("toggle_vybe_match_profile_button")
                ) {
                  Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (isInVybeMatches) "In VYBE Matches" else "Add Match",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                OutlinedButton(
                  onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "VYBE - Follow @${user.username}")
                      putExtra(Intent.EXTRA_TEXT, "Connect with @${user.username} on VYBE! Public ID: ${user.publicVybeId}")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("share_peer_profile_button")
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
                }
              }
            }
          } else {
            // Strictly Authenticated User's Own Profile Controls
            Column(
              modifier = Modifier.fillMaxWidth(),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = {
                    editFirstName = user.firstName
                    editLastName = user.lastName
                    editUsername = user.username
                    editBio = user.bio
                    editAvatarUrl = user.avatarUrl
                    editCoverUrl = user.coverUrl
                    editVideoUrl = user.profileVideoUrl
                    editIsPrivate = user.isPrivate
                    availabilityMessage = null
                    isAvailable = true
                    showEditProfileModal = true
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("edit_profile_button")
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
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("change_profile_photo_btn")
                ) {
                  Icon(Icons.Default.CameraAlt, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Photo", fontWeight = FontWeight.Bold)
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = { viewModel.navigateTo(Screen.LIVE_STUDIO) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("profile_go_live_button")
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
                      putExtra(Intent.EXTRA_TEXT, "Join me on VYBE! Follow @${user.username} (ID: ${user.publicVybeId})")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share VYBE Profile"))
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("profile_share_vybe_button")
                ) {
                  Icon(Icons.Default.Share, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Share VYBE", fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          // Profile Tabs (only shown when viewer is permitted to view content)
          if (canViewContent) {
            val availableTabs = if (isMe) listOf("Posts", "Reels", "Saved") else listOf("Posts", "Reels")
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              availableTabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Surface(
                  color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 42.dp)
                    .clickable { selectedTab = tab }
                    .testTag("profile_tab_${tab.lowercase()}")
                ) {
                  Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
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
      }

      // Tab Content or Private Profile Lock Gate
      if (!canViewContent) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 20.dp)
              .testTag("private_profile_lock_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                shape = CircleShape,
                color = VybeViolet.copy(alpha = 0.14f),
                modifier = Modifier.size(60.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(
                    Icons.Default.Lock,
                    contentDescription = "Private Account",
                    tint = VybeViolet,
                    modifier = Modifier.size(28.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(14.dp))
              Text(
                text = "This Account is Private",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = if (isFollowRequested) {
                  "Your follow request to @${user.username} is pending approval."
                } else {
                  "Follow @${user.username} to view their posts, reels, and stories."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      } else if (displayedTabPosts.isEmpty()) {
        item {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(40.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = when (selectedTab) {
                "Reels" -> "No reels shared yet."
                "Saved" -> "No saved posts yet."
                else -> "No posts shared yet."
              },
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        items(displayedTabPosts, key = { it.id }) { post ->
          PostCard(
            post = post,
            onLikeClick = { viewModel.toggleLike(post.id) },
            onSaveClick = { viewModel.toggleSave(post.id) },
            onCommentClick = { viewModel.loadCommentsForPost(post.id) },
            onAddComment = { text -> viewModel.addComment(post.id, text) },
            onShareClick = {},
            onUserClick = { viewModel.openUserProfile(post.author) },
            onDeleteClick = { viewModel.deletePost(post.id) },
            onPostViewed = { viewModel.recordPostView(it) },
            currentUserId = currentUser?.id
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(88.dp))
      }
    }
  }

  // Photo & Banner Action Bottom Sheet (Strictly Owner Only)
  if (isMe && showPhotoActionSheet && photoActionTarget != null) {
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

        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .clickable {
              val hasCameraPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
              ) == PackageManager.PERMISSION_GRANTED
              if (hasCameraPermission) {
                cameraCaptureLauncher.launch(null)
              } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
              }
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

  // Edit Profile Full Screen (Strictly Owner Only)
  if (isMe && showEditProfileModal) {
    BackHandler { showEditProfileModal = false }
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
          navigationIcon = {
            IconButton(onClick = { showEditProfileModal = false }) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          },
          actions = {
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
                  if (editIsPrivate != (currentUser?.isPrivate == true)) {
                    viewModel.setAccountPrivate(editIsPrivate)
                  }
                  showEditProfileModal = false
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
              modifier = Modifier
                .padding(end = 8.dp)
                .testTag("save_edit_profile_button")
            ) {
              Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = 20.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Spacer(modifier = Modifier.height(4.dp))

        Text("Profile Banner Cover", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
              Brush.horizontalGradient(
                listOf(VybeVioletDark, ObsidianSurface, VybeVioletDark)
              )
            )
            .clickable {
              bannerPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            }
        ) {
          if (editCoverUrl.isNotBlank()) {
            AsyncImage(
              model = editCoverUrl,
              contentDescription = "Cover preview",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          }
          Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = CircleShape,
            modifier = Modifier
              .align(Alignment.Center)
              .padding(8.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Change Cover Banner", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        Text("Avatar Profile Photo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(listOf(VybeBrandViolet, VybeBrandMagenta))
              )
              .clickable {
                avatarPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
            contentAlignment = Alignment.Center
          ) {
            if (editAvatarUrl.isNotBlank()) {
              AsyncImage(
                model = editAvatarUrl,
                contentDescription = "Avatar preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                  .fillMaxSize()
                  .clip(CircleShape)
              )
            } else {
              Text(
                text = editFirstName.take(1).uppercase().ifBlank { "U" },
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
              )
            }
          }
          Spacer(modifier = Modifier.width(16.dp))
          OutlinedButton(
            onClick = {
              avatarPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Upload Photo", fontWeight = FontWeight.SemiBold)
          }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
          label = { Text("User ID (@username)") },
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
          label = { Text("Bio (Introduce your vibe)") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth()
        )

        // Public / Private Account Toggle
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
              Text(
                text = if (editIsPrivate) "Private Profile" else "Public Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Text(
                text = if (editIsPrivate) {
                  "Only approved followers can view your posts, reels, and stories."
                } else {
                  "Anyone on VYBE can view your public posts, reels, and stories."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Switch(
              checked = editIsPrivate,
              onCheckedChange = { editIsPrivate = it },
              modifier = Modifier.testTag("edit_profile_private_switch")
            )
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
    return
  }

  // Report Peer User Dialog
  if (!isMe && showPeerReportDialog) {
    val reportCategories = listOf(
      "Impersonation",
      "Harassment / Bullying",
      "Spam or Scam",
      "Inappropriate Content",
      "Hate Speech or Threats"
    )
    AlertDialog(
      onDismissRequest = { showPeerReportDialog = false },
      title = { Text("Report @${user.username}", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Select reason:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          reportCategories.forEach { cat ->
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (peerReportCategory == cat) VybeViolet.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { peerReportCategory = cat }
            ) {
              Text(
                text = cat,
                fontSize = 12.sp,
                fontWeight = if (peerReportCategory == cat) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
              )
            }
          }
          OutlinedTextField(
            value = peerReportDetails,
            onValueChange = { peerReportDetails = it },
            label = { Text("Additional details (optional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            coroutineScope.launch {
              viewModel.submitReport(
                targetType = "USER",
                targetId = user.id,
                reason = peerReportCategory,
                details = peerReportDetails
              )
              snackbarHostState.showSnackbar("Report submitted for safety review.")
            }
            showPeerReportDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Submit Report", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showPeerReportDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // Followers & Following List Dialog
  if (showFollowListModal != null && canViewContent) {
    val title = showFollowListModal!!
    val listUsers = remember(title, isMe, allUsers, followingUserIds, followerUserIds, currentUser, user) {
      if (isMe) {
        if (title == "Following") {
          allUsers.filter { followingUserIds.contains(it.id) }
        } else {
          allUsers.filter { followerUserIds.contains(it.id) || it.followsMe }
        }
      } else {
        if (title == "Followers") {
          buildList {
            if (isFollowed && currentUser != null) add(currentUser!!)
          }
        } else {
          buildList {
            if (followsMe && currentUser != null) add(currentUser!!)
          }
        }
      }
    }

    AlertDialog(
      onDismissRequest = { showFollowListModal = null },
      title = { Text(title, fontWeight = FontWeight.Bold) },
      text = {
        if (listUsers.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = if (title == "Followers") "No followers to display yet." else "Not following anyone yet.",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 13.sp,
              textAlign = TextAlign.Center
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 320.dp)
          ) {
            items(listUsers, key = { it.id }) { u ->
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
                  val isURequested = requestedFollowUserIds.contains(u.id) || u.isFollowRequestedByMe
                  OutlinedButton(
                    onClick = { viewModel.toggleFollowUser(u.id) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(
                      text = when {
                        isUFollowed -> "Following"
                        isURequested -> "Requested"
                        else -> "Follow"
                      },
                      fontSize = 11.sp
                    )
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
