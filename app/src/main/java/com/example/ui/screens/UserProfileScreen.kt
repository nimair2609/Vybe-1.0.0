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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.*
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
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.Post
import com.example.data.model.User
import com.example.ui.MainTab
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.ProfileQrCodeDialog
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import com.example.util.MediaUtils
import com.example.util.QrCodeGenerator
import com.example.util.TimestampUtils
import kotlinx.coroutines.launch

/**
 * VYBE 2.1 – USER PROFILE
 * Redesigned Personal Identity Screen based on the official VYBE 2.1 visual system.
 * Features:
 * - Cover banner with circular overlapping avatar & verified blue badge
 * - Profile identity details: Display Name with creator crown, handle, multi-line bio,
 *   location pin ("India"), joined date, and website link
 * - 4-Column Statistics Bar: Posts, Followers, Following, Genuine Deduplicated Profile Views
 * - Interest Tag chips (Travel, Tech, Music, Fitness, Lifestyle)
 * - Primary Action Bar: Share Profile, QR Code, and more options
 * - 5 Content Tabs: Posts, Vybe Reels, Unfiltered, Tagged, Highlights
 * - 3-Column Content Grid with video indicator badges and like counts overlay
 * - Owner Customization (Edit Profile, Change Photo/Banner, Interests, Website, Private Account)
 * - Genuine 24h deduplicated view tracking for visitors (profile-owner views not counted)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
  viewModel: VybeViewModel,
  forceOwnProfile: Boolean = false
) {
  BackHandler {
    viewModel.closeUserProfileOrBack()
  }

  val currentScreen by viewModel.currentScreen.collectAsState()
  val selectedNavTab by viewModel.selectedTab.collectAsState()
  val profileUser by viewModel.selectedUserProfile.collectAsState()
  val profileLookupError by viewModel.profileLookupError.collectAsState()
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

  // Strict User Resolution
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

  // Visitor Genuine View Counting (Deduplicated 24-hr window, owner visits not counted)
  LaunchedEffect(user.id, isMe) {
    if (!isMe) {
      viewModel.recordProfileView(user.id)
    }
  }

  // Content Tabs: Posts, Vybe Reels, Unfiltered, Tagged, Highlights
  val profileTabs = listOf("Posts", "Vybe Reels", "Unfiltered", "Tagged", "Highlights")
  var selectedTab by remember { mutableStateOf("Posts") }

  // Modals & Sheets
  var showEditProfileModal by remember { mutableStateOf(false) }
  var showQrDialog by remember { mutableStateOf(false) }
  var showMoreMenu by remember { mutableStateOf(false) }
  var showPhotoActionSheet by remember { mutableStateOf(false) }
  var photoActionTarget by remember { mutableStateOf<String?>(null) } // "AVATAR" or "BANNER"
  var showReportDialog by remember { mutableStateOf(false) }

  // Edit Profile Form State
  var editDisplayName by remember(user) { mutableStateOf(user.displayName) }
  var editBio by remember(user) { mutableStateOf(user.bio) }
  var editLocation by remember(user) { mutableStateOf(user.location.ifBlank { "India" }) }
  var editWebsite by remember(user) { mutableStateOf(user.website) }
  var editInterestsText by remember(user) { mutableStateOf(user.interestTags.joinToString(", ")) }
  var editIsPrivate by remember(user) { mutableStateOf(user.isPrivate) }

  // Media Pickers
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
  val followsMe = followerUserIds.contains(user.id) || user.followsMe

  // User's genuine posts
  val nonArchivedUserPosts = remember(posts, user.id) {
    posts.filter { it.author.id == user.id && !it.isArchived }
  }

  // Filtered posts per tab
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

  // Formatted statistics
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
    containerColor = MaterialTheme.colorScheme.background,
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
          // Cover Image
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
            // Elegant sunset gradient fallback as shown in mockup
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    listOf(
                      Color(0xFF7C2D12),
                      Color(0xFF9A3412),
                      Color(0xFF1E1B4B)
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

          // Top Navigation Bar (Overlaid on Cover)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .statusBarsPadding()
              .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = { viewModel.closeUserProfileOrBack() },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .testTag("profile_top_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
              )
            }

            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              // Share Profile Quick Action
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
                  .size(40.dp)
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

              // 3-Dot Overflow Menu
              Box {
                IconButton(
                  onClick = { showMoreMenu = true },
                  modifier = Modifier
                    .size(40.dp)
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
                  onDismissRequest = { showMoreMenu = false }
                ) {
                  DropdownMenuItem(
                    text = { Text("Copy Profile Link") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
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
                      text = { Text("Account Settings") },
                      leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.navigateTo(Screen.SETTINGS)
                      }
                    )
                    DropdownMenuItem(
                      text = { Text(if (previewAsVisitor) "Exit Visitor Preview" else "Preview as Visitor") },
                      leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) },
                      onClick = {
                        showMoreMenu = false
                        previewAsVisitor = !previewAsVisitor
                      }
                    )
                  } else {
                    DropdownMenuItem(
                      text = { Text(if (isBlocked) "Unblock User" else "Block User", color = VybeDanger) },
                      leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = VybeDanger) },
                      onClick = {
                        showMoreMenu = false
                        viewModel.toggleBlockUser(user.id)
                      }
                    )
                    DropdownMenuItem(
                      text = { Text("Report User", color = VybeDanger) },
                      leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = VybeDanger) },
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

          // Camera icon for cover banner (owner only)
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
                  .padding(6.dp)
                  .size(16.dp)
              )
            }
          }
        }
      }

      // ==========================================
      // SECTION 2: AVATAR & HEADER ACTION ROW
      // ==========================================
      item(key = "profile_avatar_and_actions") {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
        ) {
          // Avatar + Edit Profile / Follow Button Row
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .offset(y = (-44).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
          ) {
            // Overlapping Circular Avatar with glowing border
            Box {
              Box(
                modifier = Modifier
                  .size(88.dp)
                  .clip(CircleShape)
                  .border(3.dp, Color.Black, CircleShape)
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

              // Blue verified badge on avatar bottom-right
              if (user.isVerified) {
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8))
                    .border(2.dp, Color.Black, CircleShape),
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
                // Camera icon on avatar for owner
                Box(
                  modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(VybeBrandViolet)
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
                    modifier = Modifier.size(14.dp)
                  )
                }
              }
            }

            // Right side: Edit Profile / Follow Action Buttons
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.padding(bottom = 6.dp)
            ) {
              if (effectiveIsMe) {
                // Owner: Edit Profile Button
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                  shape = RoundedCornerShape(20.dp),
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showEditProfileModal = true }
                    .testTag("edit_profile_btn")
                ) {
                  Text(
                    text = "Edit Profile",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                  )
                }

                // Share icon button
                Surface(
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                  shape = CircleShape,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .size(36.dp)
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
                      tint = MaterialTheme.colorScheme.onSurface,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              } else {
                // Visitor: Follow & Message buttons
                Button(
                  onClick = { viewModel.toggleFollowUser(user.id) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isFollowed) MaterialTheme.colorScheme.surfaceVariant else VybeBrandViolet
                  ),
                  shape = RoundedCornerShape(20.dp),
                  contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                  modifier = Modifier.height(36.dp)
                ) {
                  Text(
                    text = if (isFollowed) "LINKED" else if (isFollowRequested) "Requested" else "Follow",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFollowed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
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
                  shape = RoundedCornerShape(20.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                  modifier = Modifier.height(36.dp)
                ) {
                  Text(
                    text = "Message",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }

          // User Display Name with Creator Crown + @Username
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.offset(y = (-36).dp)
          ) {
            Text(
              text = safeDisplayName,
              fontSize = 20.sp,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("👑", fontSize = 16.sp)
          }

          Text(
            text = "@$safeUsername",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(y = (-36).dp)
          )

          // Bio Text
          val bioText = user.bio.ifBlank { "Dream Bigger. Vibe Stronger. 🚀\n#VYBEfamily #Travel #Tech #Life" }
          Text(
            text = bioText,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp,
            modifier = Modifier
              .offset(y = (-28).dp)
              .fillMaxWidth()
          )

          // Metadata Chips (Location & Joined Date)
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.offset(y = (-20).dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = "Location",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = user.location.ifBlank { "India" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.CalendarToday,
                contentDescription = "Joined Date",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = formattedJoinedText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            if (user.website.isNotBlank()) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.Link,
                  contentDescription = "Website",
                  tint = VybeBrandCyan,
                  modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = user.website,
                  fontSize = 12.sp,
                  color = VybeBrandCyan,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }

          // ==========================================
          // 4-COLUMN PROFILE STATISTICS BAR
          // ==========================================
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .offset(y = (-10).dp)
              .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
              .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "$statsPosts",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text("Posts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = formatCount(statsFollowers.toLong()),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text("Followers", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = formatCount(statsFollowing.toLong()),
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text("Following", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = if (statsViews > 0) formatCount(statsViews) else "0",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = VybeBrandCyan
              )
              Text("Profile Views", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          // ==========================================
          // INTEREST TAGS (Travel, Tech, Music, etc.)
          // ==========================================
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
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.6.dp, Color(0xFF334155))
              ) {
                Text(
                  text = tag,
                  fontSize = 11.sp,
                  fontWeight = FontWeight.Medium,
                  color = Color(0xFFE2E8F0),
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          // ==========================================
          // PRIMARY ACTION BAR: Share Profile, QR, More
          // ==========================================
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Surface(
              color = Color.Transparent,
              shape = RoundedCornerShape(24.dp),
              border = BorderStroke(1.5.dp, VybeBrandViolet),
              modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable {
                  val canonicalUrl = QrCodeGenerator.buildCanonicalProfileUrl(user.id, user.username)
                  val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "VYBE - @${user.username}")
                    putExtra(Intent.EXTRA_TEXT, "Connect with ${user.displayName} (@${user.username}) on VYBE:\n$canonicalUrl")
                  }
                  context.startActivity(Intent.createChooser(shareIntent, "Share Profile"))
                }
            ) {
              Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Share,
                  contentDescription = null,
                  tint = VybeBrandViolet,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Share Profile",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White
                )
              }
            }

            // QR Code Button
            Surface(
              color = Color(0xFF1E293B),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, Color(0xFF334155)),
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { showQrDialog = true }
                .testTag("profile_qr_code_btn")
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.QrCode,
                  contentDescription = "Profile QR Code",
                  tint = Color.White,
                  modifier = Modifier.size(20.dp)
                )
              }
            }

            // More Options Button
            Surface(
              color = Color(0xFF1E293B),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, Color(0xFF334155)),
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { showMoreMenu = true }
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = Icons.Default.MoreHoriz,
                  contentDescription = "Options",
                  tint = Color.White,
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
        Column(modifier = Modifier.fillMaxWidth()) {
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            profileTabs.forEach { tab ->
              val isSelected = selectedTab == tab
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                  .clickable { selectedTab = tab }
                  .padding(vertical = 8.dp)
                  .testTag("profile_tab_$tab")
              ) {
                Text(
                  text = tab,
                  fontSize = 13.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                  modifier = Modifier
                    .height(2.dp)
                    .width(36.dp)
                    .background(if (isSelected) VybeBrandViolet else Color.Transparent)
                )
              }
            }
          }
          HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant)
        }
      }

      // ==========================================
      // SECTION 4: 3-COLUMN CONTENT GRID
      // ==========================================
      if (!canViewContent) {
        item(key = "private_profile_barrier") {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp)
          ) {
            Column(
              modifier = Modifier.padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Private Account",
                tint = VybeBrandViolet,
                modifier = Modifier.size(36.dp)
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "This Account is Private",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Send a LINKUP request to @${user.username} to view their moments and reels.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      } else if (displayedTabPosts.isEmpty()) {
        item(key = "tab_empty_state") {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp)
          ) {
            Column(
              modifier = Modifier.padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = when (selectedTab) {
                  "Vybe Reels" -> Icons.Default.SmartDisplay
                  "Unfiltered" -> Icons.Default.CameraAlt
                  "Tagged" -> Icons.Default.Tag
                  "Highlights" -> Icons.Default.Star
                  else -> Icons.Default.PhotoLibrary
                },
                contentDescription = null,
                tint = VybeBrandViolet,
                modifier = Modifier.size(32.dp)
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = when (selectedTab) {
                  "Vybe Reels" -> "No reels published yet"
                  "Unfiltered" -> "No unfiltered moments captured"
                  "Tagged" -> "No tagged posts"
                  "Highlights" -> "No highlighted posts pinned"
                  else -> "No posts shared yet"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = if (effectiveIsMe) "Publish your first moment to inspire the community!" else "@${user.username} hasn't shared anything in $selectedTab yet.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
              if (effectiveIsMe) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                  onClick = { viewModel.navigateTo(Screen.CREATE_POST) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Text("Create Moment", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      } else {
        // 3-Column Media Grid
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
                color = Color(0xFF111827),
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
                        .background(
                          Brush.linearGradient(listOf(VybeVioletDark, Color(0xFF1E1B4B)))
                        )
                    )
                  }

                  // Top-right Reel Video Icon
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

                  // Bottom-left Likes Count with Heart Icon
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
            // Fill spacing if row has fewer than 3 items
            repeat(3 - rowPosts.size) {
              Spacer(modifier = Modifier.weight(1f))
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(30.dp))
      }
    }
  }

  // ==========================================
  // MODAL 1: EDIT PROFILE FULL DIALOG
  // ==========================================
  if (effectiveIsMe && showEditProfileModal) {
    BackHandler { showEditProfileModal = false }
    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
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
              colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
              modifier = Modifier.padding(end = 8.dp)
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

        // Photos Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              photoActionTarget = "AVATAR"
              showPhotoActionSheet = true
            },
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Change Photo", fontSize = 12.sp)
          }

          OutlinedButton(
            onClick = {
              photoActionTarget = "BANNER"
              showPhotoActionSheet = true
            },
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Change Cover", fontSize = 12.sp)
          }
        }

        // Display Name
        OutlinedTextField(
          value = editDisplayName,
          onValueChange = { editDisplayName = it },
          label = { Text("Display Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Bio
        OutlinedTextField(
          value = editBio,
          onValueChange = { editBio = it },
          label = { Text("Bio") },
          modifier = Modifier.fillMaxWidth(),
          minLines = 3,
          maxLines = 5
        )

        // Location
        OutlinedTextField(
          value = editLocation,
          onValueChange = { editLocation = it },
          label = { Text("Location (e.g. India, Mumbai)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Website
        OutlinedTextField(
          value = editWebsite,
          onValueChange = { editWebsite = it },
          label = { Text("Website / Portfolio Link") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )

        // Interest Tags
        OutlinedTextField(
          value = editInterestsText,
          onValueChange = { editInterestsText = it },
          label = { Text("Interest Tags (comma separated)") },
          placeholder = { Text("Travel, Tech, Music, Fitness") },
          modifier = Modifier.fillMaxWidth()
        )

        // Account Privacy Toggle
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Private Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
              "Only approved followers can view your moments and reels.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          Switch(
            checked = editIsPrivate,
            onCheckedChange = { editIsPrivate = it }
          )
        }

        Spacer(modifier = Modifier.height(20.dp))
      }
    }
  }

  // ==========================================
  // MODAL 2: QR CODE PROFILE DIALOG
  // ==========================================
  if (showQrDialog) {
    ProfileQrCodeDialog(
      user = user,
      onDismiss = { showQrDialog = false }
    )
  }

  // ==========================================
  // MODAL 3: PHOTO / BANNER CAPTURE ACTION SHEET
  // ==========================================
  if (showPhotoActionSheet) {
    AlertDialog(
      onDismissRequest = {
        showPhotoActionSheet = false
        photoActionTarget = null
      },
      title = {
        Text(if (photoActionTarget == "AVATAR") "Update Profile Photo" else "Update Cover Banner")
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Choose where you would like to select your image:")
          Button(
            onClick = {
              galleryPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select from Gallery")
          }
          OutlinedButton(
            onClick = {
              cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Take Photo with Camera")
          }
        }
      },
      confirmButton = {},
      dismissButton = {
        TextButton(onClick = {
          showPhotoActionSheet = false
          photoActionTarget = null
        }) {
          Text("Cancel")
        }
      }
    )
  }

  // ==========================================
  // MODAL 4: REPORT USER DIALOG
  // ==========================================
  if (showReportDialog) {
    var reportReason by remember { mutableStateOf("Spam or Scam") }
    val reasons = listOf("Spam or Scam", "Inappropriate Content", "Harassment", "Impersonation")
    AlertDialog(
      onDismissRequest = { showReportDialog = false },
      title = { Text("Report @${user.username}") },
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
              Text(r, fontSize = 13.sp)
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Submit Report", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReportDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
