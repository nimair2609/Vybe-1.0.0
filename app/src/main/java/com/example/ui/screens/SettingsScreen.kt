package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AppThemeMode
import com.example.data.model.UserRole
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.GitHubUpdateSectionCard
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeMatchesManagerDialog
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.GitHubUpdater

enum class SettingsSubpage {
  NONE,
  ACCOUNT_CENTER,
  SAVED,
  ARCHIVE,
  YOUR_ACTIVITY,
  NOTIFICATIONS,
  TIME_MANAGEMENT,
  HELP,
  SUPPORT_TICKETS,
  CHECK_FOR_UPDATES,
  ABOUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VybeViewModel) {
  var activeSubpage by remember { mutableStateOf(SettingsSubpage.NONE) }
  val palette = LocalVybePalette.current

  BackHandler {
    if (activeSubpage != SettingsSubpage.NONE) {
      activeSubpage = SettingsSubpage.NONE
    } else {
      viewModel.navigateBack()
    }
  }

  val currentUser by viewModel.currentUser.collectAsState()
  val currentThemeMode by viewModel.themeMode.collectAsState()
  val supportTickets by viewModel.supportTickets.collectAsState()
  val timeStats by viewModel.timeManagementStats.collectAsState()
  val deviceSessions by viewModel.activeDeviceSessions.collectAsState()
  val notifPrefs by viewModel.notificationPreferences.collectAsState()
  val activities by viewModel.userActivity.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val isOnline by viewModel.isOnline.collectAsState()
  val diagState by viewModel.deviceNotificationDiagnostics.collectAsState()

  var showDeactivateModal by remember { mutableStateOf(false) }
  var showVybeMatchesDialog by remember { mutableStateOf(false) }
  var showTicketDialog by remember { mutableStateOf(false) }
  var ticketCategory by remember { mutableStateOf("Account & Verification") }
  var ticketSubject by remember { mutableStateOf("") }
  var ticketMessage by remember { mutableStateOf("") }

  var editFirstName by remember { mutableStateOf(currentUser?.firstName.orEmpty()) }
  var editLastName by remember { mutableStateOf(currentUser?.lastName.orEmpty()) }
  var editUserId by remember { mutableStateOf(currentUser?.username.orEmpty()) }
  var userIdCheckStatus by remember { mutableStateOf<String?>(null) }
  var isUserIdAvailable by remember { mutableStateOf(true) }
  var profileSaveStatus by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(currentUser?.id) {
    editFirstName = currentUser?.firstName.orEmpty()
    editLastName = currentUser?.lastName.orEmpty()
    editUserId = currentUser?.username.orEmpty()
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    containerColor = palette.canvas,
    topBar = {
      VybeSubScreenTopBar(
        title = when (activeSubpage) {
          SettingsSubpage.NONE -> "Settings"
          SettingsSubpage.ACCOUNT_CENTER -> "Account Center"
          SettingsSubpage.SAVED -> "Saved Content"
          SettingsSubpage.ARCHIVE -> "Archived Content"
          SettingsSubpage.YOUR_ACTIVITY -> "Your Activity"
          SettingsSubpage.NOTIFICATIONS -> "Notification Preferences"
          SettingsSubpage.TIME_MANAGEMENT -> "Time Management"
          SettingsSubpage.HELP -> "Privacy, Data & Safety"
          SettingsSubpage.SUPPORT_TICKETS -> "Support Tickets"
          SettingsSubpage.CHECK_FOR_UPDATES -> "Check for Updates"
          SettingsSubpage.ABOUT -> "About VYBE"
        },
        subtitle = if (activeSubpage == SettingsSubpage.NONE) "Preferences, Security & Account" else null,
        onBackClick = {
          if (activeSubpage != SettingsSubpage.NONE) {
            activeSubpage = SettingsSubpage.NONE
          } else {
            viewModel.navigateBack()
          }
        },
        backTestTag = "settings_back_button"
      )
    }
  ) { paddingValues ->
    when (activeSubpage) {
      SettingsSubpage.NONE -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (currentUser != null) {
            item {
              Spacer(modifier = Modifier.height(6.dp))
              VybeSurfaceCard(
                onClick = { activeSubpage = SettingsSubpage.ACCOUNT_CENTER },
                borderColor = palette.accent.copy(alpha = 0.38f),
                contentPadding = PaddingValues(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("settings_account_center_card")
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  VybeAvatar(user = currentUser!!, size = 52.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        currentUser!!.displayName,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = palette.textPrimary
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      VerificationBadge(user = currentUser!!)
                    }
                    Text(
                      "@${currentUser!!.username} • Account Center",
                      fontSize = 12.sp,
                      color = palette.accent,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
                }
              }
            }
          }

          // Section: Privacy & Audience
          item {
            SettingsSectionHeader("Privacy & Audience")
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(14.dp)) {
              Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val isPrivateAccount = currentUser?.isPrivate == true
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = if (isPrivateAccount) Icons.Default.Lock else Icons.Default.Public,
                      contentDescription = null,
                      tint = palette.accent,
                      modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text("Private Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
                      Text(
                        text = if (isPrivateAccount) {
                          "Only approved followers can view your posts, reels, and stories."
                        } else {
                          "Your profile, public posts, and stories are visible on VYBE."
                        },
                        fontSize = 11.sp,
                        color = palette.textSecondary
                      )
                    }
                  }
                  Switch(
                    checked = isPrivateAccount,
                    onCheckedChange = { viewModel.setAccountPrivate(it) },
                    modifier = Modifier.testTag("private_account_switch")
                  )
                }

                HorizontalDivider(color = palette.border)

                val currentDmPrivacy = currentUser?.messagePrivacy ?: "EVERYONE"
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Who Can Message You", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
                  }
                  Spacer(modifier = Modifier.height(8.dp))
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    listOf(
                      "EVERYONE" to "Everyone",
                      "MUTUAL_FOLLOWS" to "Mutuals",
                      "NONE" to "No One"
                    ).forEach { (key, label) ->
                      val isSelected = currentDmPrivacy == key
                      Surface(
                        color = if (isSelected) palette.accent.copy(alpha = 0.18f) else palette.elevated,
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
                        modifier = Modifier
                          .weight(1f)
                          .heightIn(min = 42.dp)
                          .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                          .clickable { viewModel.setMessagePrivacy(key) }
                          .testTag("dm_privacy_${key.lowercase()}")
                      ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                          Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) palette.accent else palette.textPrimary
                          )
                        }
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = when (currentDmPrivacy) {
                      "MUTUAL_FOLLOWS" -> "Mutuals: Direct messaging opens only when both accounts follow each other."
                      "NONE" -> "No One: New direct message threads from other users are disabled."
                      else -> "Everyone: Registered VYBE members can start a conversation with you."
                    },
                    fontSize = 11.sp,
                    color = palette.textSecondary
                  )
                }

                HorizontalDivider(color = palette.border)

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { showVybeMatchesDialog = true }
                    .padding(vertical = 4.dp)
                    .testTag("manage_vybe_matches_settings_item"),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = palette.success, modifier = Modifier.size(22.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text("VYBE Matches Audience", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
                    Text(
                      "${vybeMatches.size} people selected for restricted VYBE Matches stories",
                      fontSize = 11.sp,
                      color = palette.textSecondary
                    )
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
                }
              }
            }
          }

          // Section: Account and Preferences
          item {
            SettingsSectionHeader("Account and Preferences")
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(0.dp)) {
              SettingsListItem(
                icon = Icons.Default.Bookmark,
                iconTint = palette.accent,
                title = "Saved",
                subtitle = "View and organize saved posts and reels",
                onClick = { activeSubpage = SettingsSubpage.SAVED }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.Archive,
                iconTint = VybeBrandMagenta,
                title = "Archive",
                subtitle = "Revisit and restore your archived posts",
                onClick = { activeSubpage = SettingsSubpage.ARCHIVE }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.History,
                iconTint = palette.accentSecondary,
                title = "Your Activity",
                subtitle = "Interactions, comments, post history, and capsules",
                onClick = { activeSubpage = SettingsSubpage.YOUR_ACTIVITY }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.Notifications,
                iconTint = palette.warning,
                title = "Notifications",
                subtitle = "Push notifications and interaction preferences",
                onClick = { activeSubpage = SettingsSubpage.NOTIFICATIONS }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.Timer,
                iconTint = palette.success,
                title = "Time Management",
                subtitle = "Screen time insights and break reminders",
                onClick = { activeSubpage = SettingsSubpage.TIME_MANAGEMENT }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.Verified,
                iconTint = VybeGrowBlueBright,
                title = "Badges & Verification",
                subtitle = "Official badges, verification applications, and display preferences",
                onClick = { viewModel.navigateTo(Screen.VERIFICATION_CENTER) }
              )
            }
          }

          // Section: Appearance & Theme
          item {
            SettingsSectionHeader("Appearance & Theme")
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(14.dp)) {
              Text("Application Theme", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.textPrimary)
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                listOf(
                  Triple(AppThemeMode.DARK, "Dark", Icons.Default.DarkMode),
                  Triple(AppThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                  Triple(AppThemeMode.SYSTEM, "System", Icons.Default.SettingsBrightness)
                ).forEach { (mode, label, icon) ->
                  val isSelected = currentThemeMode == mode
                  Surface(
                    color = if (isSelected) palette.accent.copy(alpha = 0.2f) else palette.elevated,
                    shape = RoundedCornerShape(VybeTokens.Radius.md),
                    border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) palette.accent else palette.border),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.md))
                      .clickable { viewModel.setThemeMode(mode) }
                      .testTag("theme_mode_${mode.name.lowercase()}")
                  ) {
                    Row(
                      modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                      horizontalArrangement = Arrangement.Center,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) palette.accent else palette.textSecondary,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) palette.accent else palette.textPrimary
                      )
                    }
                  }
                }
              }
            }
          }

          // Section: More Information & Single Designated Update Checker
          item {
            SettingsSectionHeader("More Information")
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(0.dp)) {
              SettingsListItem(
                icon = Icons.Default.SystemUpdate,
                iconTint = palette.success,
                title = "Check for Updates",
                subtitle = "Installed v${GitHubUpdater.INSTALLED_VERSION} • Check official GitHub releases",
                onClick = { activeSubpage = SettingsSubpage.CHECK_FOR_UPDATES },
                testTag = "settings_check_updates_item"
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.HelpOutline,
                iconTint = palette.accentSecondary,
                title = "Privacy, Data & Safety",
                subtitle = "Storage policies, RLS protection, and deletion retention",
                onClick = { activeSubpage = SettingsSubpage.HELP }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.SupportAgent,
                iconTint = palette.accent,
                title = "Chat with Support",
                subtitle = "Direct help desk ticket system with NR Next team",
                onClick = { activeSubpage = SettingsSubpage.SUPPORT_TICKETS }
              )
              HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = palette.border)
              SettingsListItem(
                icon = Icons.Default.Info,
                iconTint = VybeBrandMagenta,
                title = "About VYBE",
                subtitle = "Version ${GitHubUpdater.INSTALLED_VERSION} • Product of NR Next",
                onClick = { activeSubpage = SettingsSubpage.ABOUT },
                testTag = "settings_about_vybe_item"
              )
            }
          }

          if (currentUser?.role == UserRole.ADMIN) {
            item {
              SettingsSectionHeader("Platform Administration")
            }
            item {
              VybeSurfaceCard(
                borderColor = VybeVerifiedGold.copy(alpha = 0.5f),
                contentPadding = PaddingValues(0.dp)
              ) {
                SettingsListItem(
                  icon = Icons.Default.AdminPanelSettings,
                  iconTint = VybeVerifiedGold,
                  title = "Admin & Trust Safety Console",
                  subtitle = "Review moderation reports, verified badges, and age compliance",
                  onClick = { viewModel.navigateTo(Screen.ADMIN_CONSOLE) }
                )
              }
            }
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(14.dp)) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clickable { viewModel.logout() }
                  .padding(vertical = 8.dp)
                  .testTag("logout_button"),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = palette.textPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
              }

              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.border)

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clickable { showDeactivateModal = true }
                  .padding(vertical = 8.dp)
                  .testTag("deactivate_button"),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = palette.danger)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Deactivate or Delete Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.danger)
              }
            }
          }

          item {
            Spacer(modifier = Modifier.height(60.dp))
          }
        }
      }

      // SUBPAGE: ACCOUNT CENTER
      SettingsSubpage.ACCOUNT_CENTER -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Text(
              text = "Manage your identity, personal details, unique User ID, and active sessions.",
              fontSize = 13.sp,
              color = palette.textSecondary
            )
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
              Text(
                "Personal & Identity Information",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(12.dp))

              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                  value = editFirstName,
                  onValueChange = { editFirstName = it },
                  label = { Text("First Name") },
                  singleLine = true,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  colors = fieldColors,
                  modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                  value = editLastName,
                  onValueChange = { editLastName = it },
                  label = { Text("Last Name") },
                  singleLine = true,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  colors = fieldColors,
                  modifier = Modifier.weight(1f)
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              OutlinedTextField(
                value = editUserId,
                onValueChange = {
                  editUserId = it
                  val (available, msg) = viewModel.checkUserIdAvailability(it)
                  isUserIdAvailable = available
                  userIdCheckStatus = msg
                },
                label = { Text("Unique User ID (@username)") },
                leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = palette.accent) },
                supportingText = {
                  if (userIdCheckStatus != null) {
                    Text(
                      text = userIdCheckStatus ?: "",
                      color = if (isUserIdAvailable) palette.success else palette.danger,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
              )

              Spacer(modifier = Modifier.height(12.dp))

              VybePrimaryButton(
                text = "Save Identity Changes",
                onClick = {
                  if (isUserIdAvailable && editUserId.isNotBlank()) {
                    viewModel.updateAccountProfile(
                      firstName = editFirstName,
                      lastName = editLastName,
                      requestedUserId = editUserId,
                      email = currentUser?.email.orEmpty(),
                      phone = currentUser?.phone.orEmpty(),
                      bio = currentUser?.bio.orEmpty(),
                      avatarUrl = currentUser?.avatarUrl,
                      profileVideoUrl = currentUser?.profileVideoUrl,
                      coverUrl = currentUser?.coverUrl
                    )
                    profileSaveStatus = "Account identity updated across devices."
                  }
                },
                modifier = Modifier.fillMaxWidth()
              )

              if (profileSaveStatus != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = profileSaveStatus ?: "",
                  color = palette.success,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
              Text(
                "Active Logged-in Sessions",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(8.dp))
              deviceSessions.forEach { session ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(session.deviceModel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.textPrimary)
                    Text(
                      "${session.platform} • ${session.locationApprox} • ${session.lastActiveTimestamp}",
                      fontSize = 11.sp,
                      color = palette.textSecondary
                    )
                  }
                  if (session.isCurrent) {
                    Surface(
                      color = palette.success.copy(alpha = 0.16f),
                      shape = RoundedCornerShape(VybeTokens.Radius.xs)
                    ) {
                      Text(
                        "This Device",
                        color = palette.success,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  } else {
                    TextButton(onClick = { viewModel.logoutDevice(session.id) }) {
                      Text("Log Out", color = palette.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // SUBPAGE: SAVED
      SettingsSubpage.SAVED -> {
        val savedPosts = posts.filter { it.isSaved }
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          if (savedPosts.isEmpty()) {
            item {
              VybeEmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = "No saved items yet",
                subtitle = "Tap the bookmark icon on any post or reel to organize it here."
              )
            }
          } else {
            items(savedPosts, key = { it.id }) { post ->
              VybeSurfaceCard(contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  VybeAvatar(user = post.author, size = 40.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(post.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                    Text(
                      post.caption.ifBlank { post.mediaType.name },
                      maxLines = 1,
                      fontSize = 12.sp,
                      color = palette.textSecondary
                    )
                  }
                  IconButton(onClick = { viewModel.toggleSave(post.id) }) {
                    Icon(Icons.Default.BookmarkRemove, contentDescription = "Remove", tint = palette.danger)
                  }
                }
              }
            }
          }
        }
      }

      // SUBPAGE: ARCHIVE
      SettingsSubpage.ARCHIVE -> {
        val archivedPosts = posts.filter { it.isArchived && it.author.id == currentUser?.id }
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Text(
              "Archived posts are hidden from your profile and feed. You can restore them anytime.",
              fontSize = 12.sp,
              color = palette.textSecondary
            )
          }
          if (archivedPosts.isEmpty()) {
            item {
              VybeEmptyState(
                icon = Icons.Outlined.Inventory2,
                title = "Archive is empty",
                subtitle = "Posts you archive from your profile will appear here."
              )
            }
          } else {
            items(archivedPosts, key = { it.id }) { post ->
              VybeSurfaceCard(contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(post.caption.ifBlank { "Media Post" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                    Text("Archived • ${post.timestamp}", fontSize = 11.sp, color = palette.textSecondary)
                  }
                  VybePrimaryButton(
                    text = "Restore",
                    onClick = { viewModel.restoreArchivedPost(post.id) }
                  )
                }
              }
            }
          }
        }
      }

      // SUBPAGE: YOUR ACTIVITY
      SettingsSubpage.YOUR_ACTIVITY -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text(
              "Audit trail of your posts, reactions, comments, and time capsules.",
              fontSize = 12.sp,
              color = palette.textSecondary
            )
          }
          if (activities.isEmpty()) {
            item {
              VybeEmptyState(
                icon = Icons.Outlined.History,
                title = "No recent activity yet",
                subtitle = "Your posts, reactions, and milestones will appear here."
              )
            }
          } else {
            items(activities) { act ->
              VybeSurfaceCard(
                cornerRadius = VybeTokens.Radius.md,
                contentPadding = PaddingValues(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = when (act.type) {
                      "POST_CREATED" -> Icons.Default.PostAdd
                      "REACTION_GIVEN" -> Icons.Default.Favorite
                      "CAPSULE_SEALED" -> Icons.Default.LockClock
                      else -> Icons.Default.ChatBubbleOutline
                    },
                    contentDescription = null,
                    tint = palette.accent
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(act.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                    Text(act.details, fontSize = 11.sp, color = palette.textSecondary)
                  }
                  Text(act.timestamp, fontSize = 10.sp, color = palette.textMuted)
                }
              }
            }
          }
        }
      }

      // SUBPAGE: NOTIFICATIONS
      SettingsSubpage.NOTIFICATIONS -> {
        var pDm by remember(notifPrefs) { mutableStateOf(notifPrefs.pushDirectMessages) }
        var pReact by remember(notifPrefs) { mutableStateOf(notifPrefs.pushPostReactions) }
        var pCapsule by remember(notifPrefs) { mutableStateOf(notifPrefs.pushTimeCapsuleUnlocks) }
        var pCrossed by remember(notifPrefs) { mutableStateOf(notifPrefs.pushCrossedPathsRequests) }
        val localCtx = LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        var osNotifEnabled by remember {
          mutableStateOf(com.example.notifications.VybeNotificationHelper.arePhoneNotificationsEnabled(localCtx))
        }
        val settingsPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
          androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
          osNotifEnabled = com.example.notifications.VybeNotificationHelper.arePhoneNotificationsEnabled(localCtx)
          if (!granted) {
            com.example.notifications.VybeNotificationHelper.openSystemNotificationSettings(localCtx)
          }
        }
        DisposableEffect(lifecycleOwner) {
          val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
              osNotifEnabled = com.example.notifications.VybeNotificationHelper.arePhoneNotificationsEnabled(localCtx)
            }
          }
          lifecycleOwner.lifecycle.addObserver(obs)
          onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
        }

        fun persistPrefs(
          dm: Boolean = pDm,
          react: Boolean = pReact,
          capsule: Boolean = pCapsule,
          crossed: Boolean = pCrossed
        ) {
          viewModel.updateNotificationPreferences(
            notifPrefs.copy(
              pushDirectMessages = dm,
              pushPostReactions = react,
              pushComments = react,
              pushTimeCapsuleUnlocks = capsule,
              pushCrossedPathsRequests = crossed
            )
          )
        }

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            VybeSurfaceCard(contentPadding = PaddingValues(14.dp)) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Phone System Notifications", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                Text(
                  text = if (osNotifEnabled) "Active — Heads-up banners, sound & lockscreen enabled" else "Blocked — Enable in Android settings to receive phone alerts",
                  fontSize = 12.sp,
                  color = if (osNotifEnabled) palette.success else palette.danger
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  VybeSecondaryButton(
                    text = if (osNotifEnabled) "Phone Settings" else "Enable Alerts",
                    testTag = "open_system_notif_settings_button",
                    onClick = {
                      if (
                        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                          localCtx,
                          android.Manifest.permission.POST_NOTIFICATIONS
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                      ) {
                        settingsPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                      } else {
                        com.example.notifications.VybeNotificationHelper.openSystemNotificationSettings(localCtx)
                      }
                    },
                    modifier = Modifier.weight(1f)
                  )
                  VybePrimaryButton(
                    text = "Test Alert",
                    testTag = "send_test_phone_notif_button",
                    onClick = {
                      val ok = viewModel.sendTestPhoneNotification()
                      if (ok) {
                        viewModel.postBannerMessage("Test notification sent to your phone's notification shade!")
                      } else {
                        com.example.notifications.VybeNotificationHelper.openSystemNotificationSettings(localCtx)
                      }
                    },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
          item {
            val isReg = diagState.registrationStatus.equals("REGISTERED", ignoreCase = true)
            val isFail = diagState.registrationStatus.equals("FAILED", ignoreCase = true)
            val isPend = diagState.registrationStatus.equals("PENDING", ignoreCase = true) || diagState.isSyncing
            val badgeColor = when {
              isReg -> palette.success
              isPend -> palette.warning
              isFail -> palette.danger
              else -> palette.textSecondary
            }

            VybeSurfaceCard(contentPadding = PaddingValues(14.dp)) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.CloudQueue, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    Text("Device FCM Registration", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
                  }
                  Surface(
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                  ) {
                    Text(
                      text = diagState.registrationStatus,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      color = badgeColor,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                }

                Text(
                  text = "FCM Token: ${diagState.fcmTokenMasked} • Supabase: ${if (diagState.supabaseAuthenticated) "Authenticated" else "Pending"}",
                  fontSize = 12.sp,
                  color = palette.textSecondary
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  VybeSecondaryButton(
                    text = "Sync Token",
                    enabled = !diagState.isSyncing,
                    testTag = "settings_sync_fcm_token_button",
                    onClick = { viewModel.retryDeviceTokenRegistration() },
                    modifier = Modifier.weight(1f)
                  )
                  VybePrimaryButton(
                    text = "Diagnostics",
                    testTag = "settings_open_fcm_diag_button",
                    onClick = { viewModel.navigateTo(Screen.NOTIFICATION_DIAGNOSTICS) },
                    modifier = Modifier.weight(1f)
                  )
                }
              }
            }
          }
          item {
            Text("Configure in-app and push notification alerts:", fontSize = 13.sp, color = palette.textSecondary)
          }
          item {
            VybeSurfaceCard(contentPadding = PaddingValues(14.dp)) {
              NotificationSwitchRow("Direct Messages & Calls", pDm) {
                pDm = it; persistPrefs(dm = it)
              }
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.border)
              NotificationSwitchRow("Likes, Reactions & Comments", pReact) {
                pReact = it; persistPrefs(react = it)
              }
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.border)
              NotificationSwitchRow("Time Capsule Unlock Alerts", pCapsule) {
                pCapsule = it; persistPrefs(capsule = it)
              }
              HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = palette.border)
              NotificationSwitchRow("Crossed Paths Handshakes", pCrossed) {
                pCrossed = it; persistPrefs(crossed = it)
              }
            }
          }
        }
      }

      // SUBPAGE: TIME MANAGEMENT
      SettingsSubpage.TIME_MANAGEMENT -> {
        var breakEnabled by remember { mutableStateOf(timeStats.isBreakReminderEnabled) }
        var breakMinutes by remember { mutableIntStateOf(timeStats.breakReminderMinutes) }

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
              Text("Your Screen Time", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
              Spacer(modifier = Modifier.height(12.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                  Text("${timeStats.todayUsageMinutes}m", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = palette.accent)
                  Text("Today's Usage", fontSize = 11.sp, color = palette.textSecondary)
                }
                Column {
                  Text("${timeStats.dailyAverageMinutes}m", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = palette.accentSecondary)
                  Text("Daily Average", fontSize = 11.sp, color = palette.textSecondary)
                }
                Column {
                  Text("${timeStats.weeklyTotalHours}h", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = VybeBrandMagenta)
                  Text("This Week", fontSize = 11.sp, color = palette.textSecondary)
                }
              }
            }
          }

          item {
            VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
              Text("Break Reminders", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Spacer(modifier = Modifier.height(6.dp))
              Text("Schedule a gentle reminder to step away from the screen.", fontSize = 12.sp, color = palette.textSecondary)
              Spacer(modifier = Modifier.height(10.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text("Remind me after $breakMinutes minutes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.textPrimary)
                Switch(
                  checked = breakEnabled,
                  onCheckedChange = {
                    breakEnabled = it
                    viewModel.updateTimeManagement(breakMinutes, it)
                  }
                )
              }
            }
          }
        }
      }

      // SUBPAGE: PRIVACY, DATA & SAFETY
      SettingsSubpage.HELP -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
              Text("VYBE Community Standards & Safety", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "1. 16+ Community Gate:\nVYBE requires all members to be aged 16 or older.\n\n2. Real-World Crossed Paths Consent:\nCrossed Paths never exposes continuous GPS tracks. Both parties must mutually accept before messaging.\n\n3. Live UNLOAD Rooms:\nLive listening spaces stream in real time without server-side audio recording.\n\n4. UNFILTERED Authenticity:\nBeauty filters and vanity counters are omitted in UNFILTERED spaces.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = palette.textSecondary
              )
            }
          }

          item {
            VybeSurfaceCard(
              borderColor = palette.accentSecondary.copy(alpha = 0.35f),
              contentPadding = PaddingValues(16.dp)
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data Storage, Access Security & Deletion Policy", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
                Text(
                  text = "• Cloud Storage Architecture: Profile records, posts, comments, stories, story views, VYBE Matches, social graph edges, notifications, and messages are stored in Supabase PostgreSQL with Row Level Security (RLS) enforced per authenticated user JWT. Uploaded media files are stored in dedicated Supabase Storage buckets ('avatars', 'posts', 'stories', and 'chat-media').\n\n" +
                    "• Private Content & Signed URLs: Private account posts/stories, VYBE Matches stories, and chat attachments use restricted Storage buckets and time-limited signed URLs so unauthorized users cannot bypass privacy rules via direct API requests.\n\n" +
                    "• Media & Record Deletion: Deleting a post or story removes the database row (cascading associated likes, comments, and view records) and requests deletion of the corresponding Supabase Storage object.\n\n" +
                    "• Retention, CDN & Backup Transparency: While deleted content is immediately removed from active VYBE feeds and primary Storage buckets, copies may temporarily persist in edge CDN caches until cache TTL expiration, in recipient device memory/image caches if already viewed, or in encrypted Supabase point-in-time recovery database backups until standard cloud backup rotation completes.",
                  fontSize = 12.sp,
                  lineHeight = 18.sp,
                  color = palette.textSecondary
                )
              }
            }
          }
        }
      }

      // SUBPAGE: SUPPORT TICKETS
      SettingsSubpage.SUPPORT_TICKETS -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Support Tickets", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
              VybePrimaryButton(
                text = "+ New Ticket",
                onClick = { showTicketDialog = true }
              )
            }
          }

          if (supportTickets.isEmpty()) {
            item {
              VybeEmptyState(
                icon = Icons.Outlined.SupportAgent,
                title = "No support tickets",
                subtitle = "Need assistance? Open a direct ticket with the NR Next support team.",
                actionLabel = "Open Ticket",
                onAction = { showTicketDialog = true }
              )
            }
          } else {
            items(supportTickets, key = { it.id }) { ticket ->
              VybeSurfaceCard(contentPadding = PaddingValues(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(ticket.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary, modifier = Modifier.weight(1f))
                  val statusTint = when (ticket.status) {
                    "Resolved" -> palette.success
                    "Investigating" -> palette.warning
                    else -> palette.accent
                  }
                  Surface(
                    color = statusTint.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(VybeTokens.Radius.xs)
                  ) {
                    Text(
                      text = ticket.status,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = statusTint,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(ticket.message, fontSize = 12.sp, color = palette.textSecondary)
                if (ticket.agentResponse != null) {
                  Spacer(modifier = Modifier.height(8.dp))
                  Surface(
                    color = palette.elevated,
                    shape = RoundedCornerShape(VybeTokens.Radius.sm),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                      Text("NR Next Safety Support:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = palette.accentSecondary)
                      Text(ticket.agentResponse, fontSize = 11.sp, color = palette.textPrimary)
                    }
                  }
                }
              }
            }
          }
        }
      }

      // SUBPAGE: DESIGNATED SINGLE CHECK FOR UPDATES SCREEN
      SettingsSubpage.CHECK_FOR_UPDATES -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            GitHubUpdateSectionCard(isOnline = isOnline)
          }
        }
      }

      // SUBPAGE: ABOUT VYBE
      SettingsSubpage.ABOUT -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.xl))
            ) {
              Image(
                painter = painterResource(id = R.drawable.vybe_app_icon_clean),
                contentDescription = "VYBE Logo",
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("VYBE", fontFamily = PlusJakartaSansFontFamily, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = palette.textPrimary)
            Text(
              "Version ${GitHubUpdater.INSTALLED_VERSION}",
              fontSize = 12.sp,
              color = palette.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "AN NR NEXT PRODUCT",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = palette.accentSecondary,
              letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier
                .size(width = 54.dp, height = 3.dp)
                .clip(RoundedCornerShape(2.dp))
            ) {
              Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFF9933)))
              Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFFFFFFF)))
              Box(modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF138808)))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "🇮🇳 PROUDLY MADE IN INDIA",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.textSecondary,
              letterSpacing = 1.sp
            )
          }

          item {
            VybeSurfaceCard(
              contentPadding = PaddingValues(18.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "BORN IN INDIA. BUILT FOR THE WORLD.",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.accent
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "VYBE is an original next-generation social networking ecosystem combining expressive feeds, mood-aligned spaces, authentic circles, personal and professional growth journeys, and privacy-first connections built by NR NEXT.",
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = palette.textSecondary
              )
            }
          }

          item {
            Text(
              text = "CONNECT. EXPRESS. GROW.",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              textAlign = TextAlign.Center,
              color = palette.accentSecondary,
              letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
          }
        }
      }
    }
  }

  if (showVybeMatchesDialog) {
    VybeMatchesManagerDialog(
      currentUserId = currentUser?.id.orEmpty(),
      allUsers = allUsers,
      selectedMatches = vybeMatches,
      onAddMatch = { viewModel.addVybeMatch(it) },
      onRemoveMatch = { viewModel.removeVybeMatch(it) },
      onDismiss = { showVybeMatchesDialog = false }
    )
  }

  if (showTicketDialog) {
    AlertDialog(
      onDismissRequest = { showTicketDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = { Text("Open Support Ticket", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = ticketSubject,
            onValueChange = { ticketSubject = it },
            label = { Text("Subject") },
            singleLine = true,
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = ticketMessage,
            onValueChange = { ticketMessage = it },
            label = { Text("Description of your issue") },
            minLines = 3,
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Submit Ticket",
          enabled = ticketSubject.isNotBlank() && ticketMessage.isNotBlank(),
          onClick = {
            if (ticketSubject.isNotBlank() && ticketMessage.isNotBlank()) {
              viewModel.submitSupportTicket(ticketCategory, ticketSubject, ticketMessage)
              ticketSubject = ""
              ticketMessage = ""
              showTicketDialog = false
            }
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showTicketDialog = false }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }

  if (showDeactivateModal) {
    AlertDialog(
      onDismissRequest = { showDeactivateModal = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = { Text("Deactivate VYBE Account?", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        Text("Your profile, posts, and mood history will be hidden from other members. You can sign back in to restore access.")
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Confirm Deactivation",
          containerColor = palette.danger,
          onClick = {
            viewModel.deactivateAccount()
            showDeactivateModal = false
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showDeactivateModal = false }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }
}

@Composable
private fun SettingsSectionHeader(title: String) {
  val palette = LocalVybePalette.current
  Text(
    text = title,
    fontFamily = PlusJakartaSansFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 15.sp,
    color = palette.textPrimary
  )
}

@Composable
private fun NotificationSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  val palette = LocalVybePalette.current
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = palette.textPrimary)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

@Composable
fun SettingsListItem(
  icon: ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String? = null
) {
  val palette = LocalVybePalette.current
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 54.dp)
      .clickable { onClick() }
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
      Text(subtitle, fontSize = 11.sp, color = palette.textSecondary)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
  }
}
