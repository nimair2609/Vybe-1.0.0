package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.User
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.ProfileQrCodeDialog
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeModeSwitch
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.components.VybeUnderlineTabs
import com.example.ui.theme.*
import com.example.util.MediaUtils
import com.example.util.QrCodeGenerator
import com.example.util.TimestampUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
  viewModel: VybeViewModel,
  forceOwnProfile: Boolean = false,
  showTopOverlay: Boolean = true
) {
  BackHandler {
    viewModel.closeUserProfileOrBack()
  }

  val palette = LocalVybePalette.current
  val experienceMode by viewModel.experienceMode.collectAsState()
  val currentScreen by viewModel.currentScreen.collectAsState()
  val selectedNavTab by viewModel.selectedTab.collectAsState()
  val profileUser by viewModel.selectedUserProfile.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val followerUserIds by viewModel.followerUserIds.collectAsState()
  val blockedUserIds by viewModel.blockedUserIds.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val isOwnProfileContext = forceOwnProfile || (currentScreen == Screen.MAIN && selectedNavTab == MainTab.PROFILE)

  val user: User = remember(profileUser, currentUser, allUsers, isOwnProfileContext) {
    if (isOwnProfileContext) {
      currentUser ?: allUsers.firstOrNull() ?: User(id = "me", username = "vybe_user", displayName = "VYBE User")
    } else {
      val target = profileUser ?: currentUser ?: allUsers.firstOrNull() ?: User(id = "user", username = "vybe_user", displayName = "VYBE User")
      if (currentUser != null && target.id.equals(currentUser!!.id, ignoreCase = true)) {
        currentUser!!
      } else {
        allUsers.firstOrNull { it.id.equals(target.id, ignoreCase = true) } ?: target
      }
    }
  }

  val isMe = isOwnProfileContext || (currentUser != null && currentUser!!.id.equals(user.id, ignoreCase = true))
  var previewAsVisitor by remember { mutableStateOf(false) }
  val effectiveIsMe = isMe && !previewAsVisitor

  LaunchedEffect(user.id, isMe) {
    if (!isMe) {
      viewModel.recordProfileView(user.id)
    }
  }

  val profileTabs = listOf("Posts", "Vybe Reels", "Unfiltered", "Tagged", "Highlights")
  var selectedTabIndex by remember { mutableIntStateOf(0) }
  val selectedTab = profileTabs.getOrElse(selectedTabIndex) { "Posts" }

  var showEditProfileModal by remember { mutableStateOf(false) }
  var showQrDialog by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }
  var showPhotoActionSheet by remember { mutableStateOf(false) }
  var photoActionTarget by remember { mutableStateOf<String?>(null) }
  var showReportDialog by remember { mutableStateOf(false) }

  var editDisplayName by remember(user) { mutableStateOf(user.displayName) }
  var editBio by remember(user) { mutableStateOf(user.bio) }
  var editLocation by remember(user) { mutableStateOf(user.location.ifBlank { "India" }) }
  var editWebsite by remember(user) { mutableStateOf(user.website) }
  var editInterestsText by remember(user) { mutableStateOf(user.interestTags.joinToString(", ")) }
  var editIsPrivate by remember(user) { mutableStateOf(user.isPrivate) }

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
      } else {
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
      if (photoActionTarget == "AVATAR") {
        viewModel.updateProfilePhoto(uri.toString())
      } else {
        viewModel.updateProfileBanner(uri.toString())
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
        snackbarHostState.showSnackbar("Camera permission is required to capture photos directly.")
      }
    }
  }

  val canViewContent = effectiveIsMe || viewModel.canViewUserContent(user)
  val isBlocked = blockedUserIds.contains(user.id)
  val isFollowed = followingUserIds.contains(user.id)
  val isFollowRequested = requestedFollowUserIds.contains(user.id) || user.isFollowRequestedByMe

  val nonArchivedUserPosts = remember(posts, user.id) {
    posts.filter { it.author.id == user.id && !it.isArchived }
  }

  val displayedTabPosts = remember(nonArchivedUserPosts, posts, selectedTab, canViewContent, user.pinnedPostIds) {
    if (!canViewContent) {
      emptyList()
    } else {
      when (selectedTab) {
        "Vybe Reels" -> nonArchivedUserPosts.filter {
          it.mediaType == MediaType.REEL || it.mediaType == MediaType.VIDEO || it.isVybesReel
        }
        "Unfiltered" -> nonArchivedUserPosts.filter { it.isUnfiltered }
        "Tagged" -> posts.filter { p ->
          !p.isArchived && (
            p.caption.contains("@${user.username}", ignoreCase = true) ||
              p.tags.any { it.contains(user.username, ignoreCase = true) }
            )
        }
        "Highlights" -> {
          val pinned = nonArchivedUserPosts.filter { user.pinnedPostIds.contains(it.id) }
          pinned.ifEmpty { nonArchivedUserPosts.sortedByDescending { it.likesCount }.take(6) }
        }
        else -> nonArchivedUserPosts
      }
    }
  }

  val safeUsername = remember(user.username, user.id) {
    user.username.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
      ?: "vybe_${user.id.filter { it.isLetterOrDigit() }.take(6).lowercase()}"
  }
  val safeDisplayName = remember(user.displayName, safeUsername) {
    user.displayName.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
      ?: safeUsername
  }
  val formattedJoinedText = remember(user.createdAtIso, user.joinedDate) {
    val fromIso = TimestampUtils.formatJoinedMonthYear(user.createdAtIso)
    when {
      !user.createdAtIso.isNullOrBlank() && fromIso != "Joined recently" -> fromIso
      user.joinedDate.isNotBlank() && user.joinedDate != "VYBE Member" ->
        if (user.joinedDate.startsWith("Joined", ignoreCase = true)) user.joinedDate else "Joined ${user.joinedDate}"
      else -> "Joined Aug 2025"
    }
  }

  val statsPosts = nonArchivedUserPosts.size
  val statsFollowers = if (isMe) maxOf(user.followersCount, followerUserIds.size) else user.followersCount
  val statsFollowing = if (isMe) maxOf(user.followingCount, followingUserIds.size) else user.followingCount
  val statsViews = user.profileViewsCount

  fun formatCount(count: Long): String {
    return when {
      count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0).replace(".0M", "M")
      count >= 1_000 -> String.format("%.1fK", count / 1_000.0).replace(".0K", "K")
      else -> count.toString()
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp)
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {

      // ==========================================
      // SECTION 1: COVER BANNER & TOP NAV OVERLAY
      // ==========================================
      item(key = "profile_cover_and_nav") {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
        ) {
          if (user.coverUrl.isNotBlank()) {
            AsyncImage(
              model = user.coverUrl,
              contentDescription = "Cover Banner",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxSize()
                .then(
                  if (effectiveIsMe) Modifier.clickable {
                    photoActionTarget = "BANNER"
                    showPhotoActionSheet = true
                  } else Modifier
                )
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      VybeSocialViolet.copy(alpha = 0.72f),
                      VybeGrowBlue.copy(alpha = 0.65f),
                      palette.canvas
                    )
                  )
                )
                .then(
                  if (effectiveIsMe) Modifier.clickable {
                    photoActionTarget = "BANNER"
                    showPhotoActionSheet = true
                  } else Modifier
                )
            )
          }

          // Top Bar Overlay: Back + ModeSwitch (standalone only) + Share/More
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .let { if (showTopOverlay) it.statusBarsPadding() else it }
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = if (showTopOverlay) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (showTopOverlay) {
              IconButton(
                onClick = { viewModel.closeUserProfileOrBack() },
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.45f))
                  .testTag("profile_back_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = "Back",
                  tint = Color.White
                )
              }

              if (effectiveIsMe) {
                VybeModeSwitch(
                  currentMode = experienceMode,
                  onModeChange = { viewModel.setExperienceMode(it) }
                )
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              IconButton(
                onClick = {
                  val canonicalUrl = QrCodeGenerator.buildCanonicalProfileUrl(user.id, user.username)
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VYBE - @${user.username}")
                    putExtra(Intent.EXTRA_TEXT, "Connect with ${user.displayName} (@${user.username}) on VYBE:\n$canonicalUrl")
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                },
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(Color.Black.copy(alpha = 0.45f))
              ) {
                Icon(
                  imageVector = Icons.Default.Share,
                  contentDescription = "Share Profile",
                  tint = Color.White,
                  modifier = Modifier.size(18.dp)
                )
              }

              Box {
                IconButton(
                  onClick = { showMoreMenu = true },
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                ) {
                  Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Options",
                    tint = Color.White
                  )
                }

                DropdownMenu(
                  expanded = showMoreMenu,
                  onDismissRequest = { showMoreMenu = false },
                  containerColor = palette.card
                ) {
                  DropdownMenuItem(
                    text = { Text("Copy Profile Link", color = palette.textPrimary) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = palette.textSecondary) },
                    onClick = {
                      showMoreMenu = false
                      val canonicalUrl = QrCodeGenerator.buildCanonicalProfileUrl(user.id, user.username)
                      val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                      clipboard.setPrimaryClip(ClipData.newPlainText("VYBE Profile", canonicalUrl))
                      Toast.makeText(context, "Copied profile link!", Toast.LENGTH_SHORT).show()
                    }
                  )
                  if (effectiveIsMe) {
                    DropdownMenuItem(
                      text = { Text("Account Settings", color = palette.textPrimary) },
                      leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = palette.textSecondary) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.navigateTo(Screen.SETTINGS)
                      }
                    )
                    DropdownMenuItem(
                      text = { Text(if (previewAsVisitor) "Exit Visitor Preview" else "Preview as Visitor", color = palette.textPrimary) },
                      leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = palette.textSecondary) },
                      onClick = {
                        showMoreMenu = false
                        previewAsVisitor = !previewAsVisitor
                      }
                    )
                  } else {
                    DropdownMenuItem(
                      text = { Text(if (isBlocked) "Unblock User" else "Block User", color = palette.danger) },
                      leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = palette.danger) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.toggleBlockUser(user.id)
                      }
                    )
                    DropdownMenuItem(
                      text = { Text("Report User", color = palette.danger) },
                      leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = palette.danger) },
                      onClick = {
                        showMoreMenu = false
                        showReportDialog = true
                      }
                    )
                  }
                }
              }
            }
          }

          if (effectiveIsMe) {
            Surface(
              color = Color.Black.copy(alpha = 0.55f),
              shape = CircleShape,
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .clickable {
                  photoActionTarget = "BANNER"
                  showPhotoActionSheet = true
                }
            ) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = "Edit Banner",
                tint = Color.White,
                modifier = Modifier
                  .padding(7.dp)
                  .size(16.dp)
              )
            }
          }
        }
      }

      // ==========================================
      // SECTION 2: AVATAR & IDENTITY DETAILS
      // ==========================================
      item(key = "profile_avatar_and_actions") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .offset(y = (-44).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
          ) {
            Box {
              Box(
                modifier = Modifier
                  .size(88.dp)
                  .clip(CircleShape)
                  .background(palette.canvas)
                  .border(3.dp, palette.canvas, CircleShape)
              ) {
                if (user.avatarUrl.isNotBlank()) {
                  AsyncImage(
                    model = user.avatarUrl,
                    contentDescription = safeDisplayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                } else {
                  VybeAvatar(user = user, size = 88.dp)
                }
              }

              if (user.isVerified) {
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(VybeGrowBlue)
                    .border(2.dp, palette.canvas, CircleShape),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Verified",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                  )
                }
              } else if (effectiveIsMe) {
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(palette.accent)
                    .border(2.dp, palette.canvas, CircleShape)
                    .clickable {
                      photoActionTarget = "AVATAR"
                      showPhotoActionSheet = true
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Avatar",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                  )
                }
              }
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(bottom = 6.dp)
            ) {
              if (effectiveIsMe) {
                Surface(
                  color = palette.elevated,
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  border = BorderStroke(1.dp, palette.border),
                  modifier = Modifier
                    .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                    .clickable { showEditProfileModal = true }
                    .testTag("edit_profile_btn")
                ) {
                  Text(
                    text = "Edit Profile",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                  )
                }

                Surface(
                  color = palette.elevated,
                  shape = CircleShape,
                  border = BorderStroke(1.dp, palette.border),
                  modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable {
                      val canonicalUrl = QrCodeGenerator.buildCanonicalProfileUrl(user.id, user.username)
                      val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "VYBE - @${user.username}")
                        putExtra(Intent.EXTRA_TEXT, "Connect with ${user.displayName} on VYBE:\n$canonicalUrl")
                      }
                      context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                    }
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.Share,
                      contentDescription = "Share",
                      tint = palette.textPrimary,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              } else {
                Button(
                  onClick = { viewModel.toggleFollowUser(user.id) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowed) palette.elevated else palette.accent
                  ),
                  border = if (isFollowed) BorderStroke(1.dp, palette.border) else null,
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                  modifier = Modifier.height(38.dp)
                ) {
                  Text(
                    text = if (isFollowed) "LINKED" else if (isFollowRequested) "Requested" else "Follow",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFollowed) palette.textSecondary else Color.White
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
                  border = BorderStroke(1.dp, palette.border),
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  modifier = Modifier.height(38.dp)
                ) {
                  Text(
                    text = "Message",
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                  )
                }
              }
            }
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-34).dp)
          ) {
            Text(
              text = safeDisplayName,
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 21.sp,
              fontWeight = FontWeight.ExtraBold,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            VerificationBadge(user = user, iconSize = 18.dp)
          }

          Text(
            text = "@$safeUsername",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = palette.textSecondary,
            modifier = Modifier.offset(y = (-34).dp)
          )

          if (user.bio.isNotBlank()) {
            Text(
              text = user.bio,
              fontSize = 13.sp,
              color = palette.textPrimary,
              lineHeight = 19.sp,
              modifier = Modifier
                .offset(y = (-24).dp)
                .fillMaxWidth()
            )
          }

          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
              .offset(y = (-16).dp)
              .horizontalScroll(rememberScrollState())
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = palette.textSecondary,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = user.location.ifBlank { "India" },
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "Joined Date",
                tint = palette.textSecondary,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = formattedJoinedText,
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }

            if (user.website.isNotBlank()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Link,
                  contentDescription = "Website",
                  tint = palette.accentSecondary,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = user.website,
                  fontSize = 12.sp,
                  color = palette.accentSecondary,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }

          // 4-Column Statistics Bar
          VybeSurfaceCard(
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .offset(y = (-8).dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceAround,
              verticalAlignment = Alignment.CenterVertically
            ) {
              ProfileStatCol("${statsPosts}", "Posts", palette.textPrimary, palette.textSecondary)
              ProfileStatCol(formatCount(statsFollowers.toLong()), "Followers", palette.textPrimary, palette.textSecondary)
              ProfileStatCol(formatCount(statsFollowing.toLong()), "Following", palette.textPrimary, palette.textSecondary)
              ProfileStatCol(if (statsViews > 0) formatCount(statsViews) else "0", "Views", palette.accent, palette.textSecondary)
            }
          }

          Spacer(modifier = Modifier.height(4.dp))

          // Interest Tags
          val interestsList = user.interestTags.ifEmpty {
            listOf("Travel", "Tech", "Music", "Fitness", "Lifestyle")
          }
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            interestsList.forEach { tag ->
              Surface(
                color = palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                border = BorderStroke(1.dp, palette.border)
              ) {
                Text(
                  text = tag,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = palette.textSecondary,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // Primary Action Bar: Share Profile, QR Code, More
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            VybeSecondaryButton(
              text = "Share Profile",
              icon = Icons.Default.Share,
              onClick = {
                val canonicalUrl = QrCodeGenerator.buildCanonicalProfileUrl(user.id, user.username)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, "VYBE - @${user.username}")
                  putExtra(Intent.EXTRA_TEXT, "Connect with ${user.displayName} (@${user.username}) on VYBE:\n$canonicalUrl")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
              },
              modifier = Modifier.weight(1f)
            )

            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border),
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable { showQrDialog = true }
                .testTag("profile_qr_code_btn")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.QrCode,
                  contentDescription = "Profile QR Code",
                  tint = palette.textPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
            }

            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.border),
              modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.md))
                .clickable { showMoreMenu = true }
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.MoreHoriz,
                  contentDescription = "Options",
                  tint = palette.textPrimary,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      }

      // ==========================================
      // SECTION 3: 5 CONTENT TABS
      // ==========================================
      item(key = "profile_content_tabs") {
        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
          ) {
            profileTabs.forEachIndexed { index, tab ->
              val isSelected = selectedTabIndex == index
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                  .clickable { selectedTabIndex = index }
                  .padding(top = 10.dp)
                  .testTag("profile_tab_$tab")
              ) {
                Text(
                  text = tab,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 14.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) palette.textPrimary else palette.textMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                  modifier = Modifier
                    .height(2.5.dp)
                    .width(36.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                    .background(if (isSelected) palette.accent else Color.Transparent)
                )
              }
            }
          }
          HorizontalDivider(thickness = 0.8.dp, color = palette.border)
        }
      }

      // ==========================================
      // SECTION 4: 3-COLUMN CONTENT GRID
      // ==========================================
      if (!canViewContent) {
        item(key = "private_profile_barrier") {
          Box(modifier = Modifier.padding(20.dp)) {
            VybeEmptyState(
              icon = Icons.Default.Lock,
              title = "This Account is Private",
              subtitle = "Send a LINKUP request to @${user.username} to view their moments and reels."
            )
          }
        }
      } else if (displayedTabPosts.isEmpty()) {
        item(key = "tab_empty_state") {
          Box(modifier = Modifier.padding(20.dp)) {
            VybeEmptyState(
              icon = when (selectedTab) {
                "Vybe Reels" -> Icons.Default.SmartDisplay
                "Unfiltered" -> Icons.Default.CameraAlt
                "Tagged" -> Icons.Default.Tag
                "Highlights" -> Icons.Default.Star
                else -> Icons.Default.PhotoLibrary
              },
              title = when (selectedTab) {
                "Vybe Reels" -> "No reels published yet"
                "Unfiltered" -> "No unfiltered moments captured"
                "Tagged" -> "No tagged posts"
                "Highlights" -> "No highlighted posts pinned"
                else -> "No posts shared yet"
              },
              subtitle = if (effectiveIsMe) {
                "Publish your first moment to inspire the community."
              } else {
                "@${user.username} hasn't shared anything in $selectedTab yet."
              },
              actionLabel = if (effectiveIsMe) "Create Moment" else null,
              onAction = if (effectiveIsMe) { { viewModel.navigateTo(Screen.CREATE_POST) } } else null
            )
          }
        }
      } else {
        items(displayedTabPosts.chunked(3), key = { chunk -> "row_${chunk.first().id}" }) { rowPosts ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            rowPosts.forEach { post ->
              val isVideo = post.mediaType == MediaType.REEL || post.mediaType == MediaType.VIDEO || post.isVybesReel
              Surface(
                color = palette.elevated,
                modifier = Modifier
                  .weight(1f)
                  .aspectRatio(1f)
                  .clickable {
                    if (isVideo) {
                      viewModel.openReelViewer(post.id)
                    } else {
                      viewModel.openPostFromNotification(post.id)
                    }
                  }
              ) {
                Box(modifier = Modifier.fillMaxSize()) {
                  val mediaUrl = post.mediaUrls.firstOrNull().orEmpty()
                  if (mediaUrl.isNotBlank()) {
                    AsyncImage(
                      model = mediaUrl,
                      contentDescription = post.caption,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                  } else {
                    Box(
                      modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(VybeSocialViolet.copy(alpha = 0.5f), palette.elevated)))
                    )
                  }

                  if (isVideo) {
                    Box(
                      modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                    ) {
                      Icon(
                        imageVector = Icons.Default.SmartDisplay,
                        contentDescription = "Video",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                      )
                    }
                  }

                  Row(
                    modifier = Modifier
                      .align(Alignment.BottomStart)
                      .background(Color.Black.copy(alpha = 0.45f))
                      .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Default.Favorite,
                      contentDescription = "Likes",
                      tint = Color.White,
                      modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                      text = formatCount(post.likesCount.toLong()),
                      color = Color.White,
                      fontSize = 9.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }
            }
            repeat(3 - rowPosts.size) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(28.dp))
      }
    }
  }

  // MODAL 1: EDIT PROFILE FULL DIALOG
  if (effectiveIsMe && showEditProfileModal) {
    BackHandler { showEditProfileModal = false }
    Scaffold(
      containerColor = palette.canvas,
      contentWindowInsets = WindowInsets(0.dp),
      topBar = {
        com.example.ui.components.VybeSubScreenTopBar(
          title = "Edit Profile",
          onBackClick = { showEditProfileModal = false },
          actions = {
            Button(
              onClick = {
                viewModel.updateExtendedProfile(
                  displayName = editDisplayName,
                  bio = editBio,
                  location = editLocation,
                  website = editWebsite,
                  interests = editInterestsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                  avatarUrl = user.avatarUrl,
                  coverUrl = user.coverUrl,
                  isPrivate = editIsPrivate
                )
                showEditProfileModal = false
              },
              colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.padding(end = 8.dp)
            ) {
              Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        )
      }
    ) { innerPadding ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .navigationBarsPadding()
          .imePadding()
          .padding(horizontal = 16.dp, vertical = 14.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          VybeSecondaryButton(
            text = "Change Photo",
            icon = Icons.Default.CameraAlt,
            onClick = {
              photoActionTarget = "AVATAR"
              showPhotoActionSheet = true
            },
            modifier = Modifier.weight(1f)
          )
          VybeSecondaryButton(
            text = "Change Cover",
            icon = Icons.Default.Image,
            onClick = {
              photoActionTarget = "BANNER"
              showPhotoActionSheet = true
            },
            modifier = Modifier.weight(1f)
          )
        }

        OutlinedTextField(
          value = editDisplayName,
          onValueChange = { editDisplayName = it },
          label = { Text("Display Name") },
          singleLine = true,
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = editBio,
          onValueChange = { editBio = it },
          label = { Text("Bio") },
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 5
        )

        OutlinedTextField(
          value = editLocation,
          onValueChange = { editLocation = it },
          label = { Text("Location (e.g. India, Mumbai)") },
          singleLine = true,
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = editWebsite,
          onValueChange = { editWebsite = it },
          label = { Text("Website / Portfolio Link") },
          singleLine = true,
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = editInterestsText,
          onValueChange = { editInterestsText = it },
          label = { Text("Interest Tags (comma separated)") },
          placeholder = { Text("Travel, Tech, Music, Fitness") },
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          modifier = Modifier.fillMaxWidth()
        )

        VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Private Account", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(
                "Only approved followers can view your moments and reels.",
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
            Switch(
              checked = editIsPrivate,
              onCheckedChange = { editIsPrivate = it }
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  if (showQrDialog) {
    ProfileQrCodeDialog(
      user = user,
      onDismiss = { showQrDialog = false }
    )
  }

  if (showPhotoActionSheet) {
    AlertDialog(
      onDismissRequest = {
        showPhotoActionSheet = false
        photoActionTarget = null
      },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Text(
          text = if (photoActionTarget == "AVATAR") "Update Profile Photo" else "Update Cover Banner",
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Choose where you would like to select your image:", color = palette.textSecondary)
          VybePrimaryButton(
            text = "Select from Gallery",
            icon = Icons.Default.PhotoLibrary,
            onClick = {
              galleryPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.fillMaxWidth()
          )
          VybeSecondaryButton(
            text = "Take Photo with Camera",
            icon = Icons.Default.CameraAlt,
            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = {
          showPhotoActionSheet = false
          photoActionTarget = null
        }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }

  if (showReportDialog) {
    var reportReason by remember { mutableStateOf("Spam or Scam") }
    val reasons = listOf("Spam or Scam", "Inappropriate Content", "Harassment", "Impersonation")
    AlertDialog(
      onDismissRequest = { showReportDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Report @${user.username}", fontWeight = FontWeight.Bold, color = palette.textPrimary) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          reasons.forEach { r ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .fillMaxWidth()
                .clickable { reportReason = r }
                .padding(vertical = 4.dp)
            ) {
              RadioButton(selected = reportReason == r, onClick = { reportReason = r })
              Spacer(modifier = Modifier.width(8.dp))
              Text(r, fontSize = 13.sp, color = palette.textPrimary)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            coroutineScope.launch {
              viewModel.submitReport("USER", user.id, reportReason)
              showReportDialog = false
              snackbarHostState.showSnackbar("Report submitted for moderation review.")
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = palette.danger)
        ) {
          Text("Submit Report", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportDialog = false }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }
}

@Composable
private fun ProfileStatCol(
  value: String,
  label: String,
  valueColor: Color,
  labelColor: Color
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontFamily = PlusJakartaSansFontFamily,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 16.sp,
      color = valueColor
    )
    Text(label, fontSize = 11.sp, color = labelColor)
  }
}
