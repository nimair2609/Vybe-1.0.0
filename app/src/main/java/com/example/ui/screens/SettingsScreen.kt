package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
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
import com.example.ui.components.VybeMatchesManagerDialog
import com.example.ui.theme.*
import com.example.util.GitHubUpdater
import kotlinx.coroutines.launch

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

  var showDeactivateModal by remember { mutableStateOf(false) }
  var showVybeMatchesDialog by remember { mutableStateOf(false) }
  var showTicketDialog by remember { mutableStateOf(false) }
  var ticketCategory by remember { mutableStateOf("Account & Verification") }
  var ticketSubject by remember { mutableStateOf("") }
  var ticketMessage by remember { mutableStateOf("") }

  // Account Center state synced with authenticated user (no hardcoded demo names)
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

  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = {
              if (activeSubpage != SettingsSubpage.NONE) {
                activeSubpage = SettingsSubpage.NONE
              } else {
                viewModel.navigateBack()
              }
            },
            modifier = Modifier.testTag("settings_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text(
            text = when (activeSubpage) {
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
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    when (activeSubpage) {
      SettingsSubpage.NONE -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          if (currentUser != null) {
            item {
              Spacer(modifier = Modifier.height(4.dp))
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f)),
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { activeSubpage = SettingsSubpage.ACCOUNT_CENTER }
                  .testTag("settings_account_center_card")
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(user = currentUser!!, size = 52.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(currentUser!!.displayName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                      Spacer(modifier = Modifier.width(4.dp))
                      VerificationBadge(user = currentUser!!)
                    }
                    Text("@${currentUser!!.username} • Account Center", fontSize = 12.sp, color = VybeViolet)
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          // Section: Privacy & Audience
          item {
            Text("Privacy & Audience", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                      tint = VybeViolet,
                      modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                      Text("Private Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                      Text(
                        text = if (isPrivateAccount) {
                          "Only approved followers can view your posts, reels, and stories."
                        } else {
                          "Your profile, public posts, and stories are visible on VYBE."
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                  Switch(
                    checked = isPrivateAccount,
                    onCheckedChange = { viewModel.setAccountPrivate(it) },
                    modifier = Modifier.testTag("private_account_switch")
                  )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                // Direct Message Privacy Control
                val currentDmPrivacy = currentUser?.messagePrivacy ?: "EVERYONE"
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Who Can Message You", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
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
                        color = if (isSelected) VybeViolet.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        border = if (isSelected) BorderStroke(1.dp, VybeViolet) else null,
                        modifier = Modifier
                          .weight(1f)
                          .heightIn(min = 40.dp)
                          .clickable { viewModel.setMessagePrivacy(key) }
                          .testTag("dm_privacy_${key.lowercase()}")
                      ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
                          Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showVybeMatchesDialog = true }
                    .padding(vertical = 4.dp)
                    .testTag("manage_vybe_matches_settings_item"),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(22.dp))
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text("VYBE Matches Audience", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                      "${vybeMatches.size} people selected for restricted VYBE Matches stories",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          // Section: Account and Preferences
          item {
            Text("Account and Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column {
                SettingsListItem(
                  icon = Icons.Default.Bookmark,
                  iconTint = VybeViolet,
                  title = "Saved",
                  subtitle = "View and organize saved posts and reels",
                  onClick = { activeSubpage = SettingsSubpage.SAVED }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.Archive,
                  iconTint = VybeMagenta,
                  title = "Archive",
                  subtitle = "Revisit and restore your archived posts",
                  onClick = { activeSubpage = SettingsSubpage.ARCHIVE }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.History,
                  iconTint = VybeCyan,
                  title = "Your Activity",
                  subtitle = "Interactions, comments, post history, and capsules",
                  onClick = { activeSubpage = SettingsSubpage.YOUR_ACTIVITY }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.Notifications,
                  iconTint = Color(0xFFFFB74D),
                  title = "Notifications",
                  subtitle = "Push notifications and interaction preferences",
                  onClick = { activeSubpage = SettingsSubpage.NOTIFICATIONS }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.Timer,
                  iconTint = Color(0xFF4ADE80),
                  title = "Time Management",
                  subtitle = "Screen time insights and break reminders",
                  onClick = { activeSubpage = SettingsSubpage.TIME_MANAGEMENT }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.Verified,
                  iconTint = Color(0xFF00B0FF),
                  title = "Badges & Verification",
                  subtitle = "Official badges, verification applications, and display preferences",
                  onClick = { viewModel.navigateTo(Screen.VERIFICATION_CENTER) }
                )
              }
            }
          }

          // Section: Appearance & Theme (kept exclusively in Settings)
          item {
            Text("Appearance & Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text("Application Theme", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                      color = if (isSelected) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(12.dp),
                      border = if (isSelected) BorderStroke(1.5.dp, VybeViolet) else null,
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
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
                          tint = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = label,
                          fontSize = 12.sp,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                          color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurface
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // Section: More Information & Single Designated Update Checker
          item {
            Text("More Information", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column {
                SettingsListItem(
                  icon = Icons.Default.SystemUpdate,
                  iconTint = VybeOnlineGreen,
                  title = "Check for Updates",
                  subtitle = "Installed v${GitHubUpdater.INSTALLED_VERSION} • Check official GitHub releases",
                  onClick = { activeSubpage = SettingsSubpage.CHECK_FOR_UPDATES },
                  testTag = "settings_check_updates_item"
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.HelpOutline,
                  iconTint = VybeBrandCyan,
                  title = "Privacy, Data & Safety",
                  subtitle = "Storage policies, RLS protection, and deletion retention",
                  onClick = { activeSubpage = SettingsSubpage.HELP }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.SupportAgent,
                  iconTint = VybeBrandViolet,
                  title = "Chat with Support",
                  subtitle = "Direct help desk ticket system with NR Next team",
                  onClick = { activeSubpage = SettingsSubpage.SUPPORT_TICKETS }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
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
          }

          if (currentUser?.role == UserRole.ADMIN) {
            item {
              Text("Platform Administration", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            item {
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.5f))
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.logout() }
                    .padding(vertical = 8.dp)
                    .testTag("logout_button"),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                  Spacer(modifier = Modifier.width(12.dp))
                  Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDeactivateModal = true }
                    .padding(vertical = 8.dp)
                    .testTag("deactivate_button"),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = VybeDanger)
                  Spacer(modifier = Modifier.width(12.dp))
                  Text("Deactivate or Delete Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VybeDanger)
                }
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Text(
              text = "Manage your identity, personal details, unique User ID, and active sessions.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("Personal & Identity Information", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))

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
                  leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = VybeViolet) },
                  supportingText = {
                    if (userIdCheckStatus != null) {
                      Text(
                        text = userIdCheckStatus ?: "",
                        color = if (isUserIdAvailable) VybeOnlineGreen else VybeDanger,
                        fontWeight = FontWeight.SemiBold
                      )
                    }
                  },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
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
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Save Identity Changes", fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (profileSaveStatus != null) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = profileSaveStatus ?: "",
                    color = VybeOnlineGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                  )
                }
              }
            }
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("Active Logged-in Sessions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                      Text(session.deviceModel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                      Text("${session.platform} • ${session.locationApprox} • ${session.lastActiveTimestamp}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (session.isCurrent) {
                      Surface(color = VybeOnlineGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                        Text("This Device", color = VybeOnlineGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                      }
                    } else {
                      TextButton(onClick = { viewModel.logoutDevice(session.id) }) {
                        Text("Log Out", color = VybeDanger, fontSize = 11.sp)
                      }
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          if (savedPosts.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("No saved items yet. Tap the bookmark icon on any post to save.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
              }
            }
          } else {
            items(savedPosts, key = { it.id }) { post ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(user = post.author, size = 40.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(post.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(post.caption.ifBlank { post.mediaType.name }, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  IconButton(onClick = { viewModel.toggleSave(post.id) }) {
                    Icon(Icons.Default.BookmarkRemove, contentDescription = "Remove", tint = VybeDanger)
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Text("Archived posts are hidden from your profile and feed. You can restore them anytime.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (archivedPosts.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("Archive is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
              }
            }
          } else {
            items(archivedPosts, key = { it.id }) { post ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text(post.caption.ifBlank { "Media Post" }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Archived • ${post.timestamp}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Button(
                    onClick = { viewModel.restoreArchivedPost(post.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
                  ) {
                    Text("Restore", fontSize = 12.sp, color = Color.White)
                  }
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text("Audit trail of your posts, reactions, comments, and time capsules.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (activities.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("No recent activity recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
              }
            }
          } else {
            items(activities) { act ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = when (act.type) {
                      "POST_CREATED" -> Icons.Default.PostAdd
                      "REACTION_GIVEN" -> Icons.Default.Favorite
                      "CAPSULE_SEALED" -> Icons.Default.LockClock
                      else -> Icons.Default.ChatBubbleOutline
                    },
                    contentDescription = null,
                    tint = VybeViolet
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(act.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(act.details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Text(act.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        val localCtx = androidx.compose.ui.platform.LocalContext.current
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Phone System Notifications", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                      text = if (osNotifEnabled) "Active — Heads-up banners, sound & lockscreen enabled" else "Blocked — Enable in Android settings to receive phone alerts",
                      fontSize = 12.sp,
                      color = if (osNotifEnabled) VybeOnlineGreen else VybeDanger
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  OutlinedButton(
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
                    modifier = Modifier.weight(1f).testTag("open_system_notif_settings_button")
                  ) {
                    Text(if (osNotifEnabled) "Phone Settings" else "Enable Alerts", fontSize = 12.sp)
                  }
                  Button(
                    onClick = {
                      val ok = viewModel.sendTestPhoneNotification()
                      if (ok) {
                        viewModel.postBannerMessage("Test notification sent to your phone's notification shade!")
                      } else {
                        com.example.notifications.VybeNotificationHelper.openSystemNotificationSettings(localCtx)
                      }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    modifier = Modifier.weight(1f).testTag("send_test_phone_notif_button")
                  ) {
                    Text("Test Alert", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
          item {
            Text("Configure in-app and push notification alerts:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Direct Messages & Calls", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(
                    checked = pDm,
                    onCheckedChange = {
                      pDm = it
                      persistPrefs(dm = it)
                    }
                  )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Likes, Reactions & Comments", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(
                    checked = pReact,
                    onCheckedChange = {
                      pReact = it
                      persistPrefs(react = it)
                    }
                  )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Time Capsule Unlock Alerts", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(
                    checked = pCapsule,
                    onCheckedChange = {
                      pCapsule = it
                      persistPrefs(capsule = it)
                    }
                  )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Crossed Paths Handshakes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(
                    checked = pCrossed,
                    onCheckedChange = {
                      pCrossed = it
                      persistPrefs(crossed = it)
                    }
                  )
                }
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("Your Screen Time", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Column {
                    Text("${timeStats.todayUsageMinutes}m", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = VybeViolet)
                    Text("Today's Usage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Column {
                    Text("${timeStats.dailyAverageMinutes}m", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = VybeCyan)
                    Text("Daily Average", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Column {
                    Text("${timeStats.weeklyTotalHours}h", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = VybeMagenta)
                    Text("This Week", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
            }
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("Break Reminders", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Schedule a gentle reminder to step away from the screen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Remind me after $breakMinutes minutes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
      }

      // SUBPAGE: PRIVACY, DATA & SAFETY
      SettingsSubpage.HELP -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("VYBE Community Standards & Safety", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "1. 16+ Community Gate:\nVYBE requires all members to be aged 16 or older.\n\n2. Real-World Crossed Paths Consent:\nCrossed Paths never exposes continuous GPS tracks. Both parties must mutually accept before messaging.\n\n3. Live UNLOAD Rooms:\nLive listening spaces stream in real time without server-side audio recording.\n\n4. UNFILTERED Authenticity:\nBeauty filters and vanity counters are omitted in UNFILTERED spaces.",
                  fontSize = 12.sp,
                  lineHeight = 18.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, VybeBrandCyan.copy(alpha = 0.35f))
            ) {
              Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Data Storage, Access Security & Deletion Policy", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                  text = "• Cloud Storage Architecture: Profile records, posts, comments, stories, story views, VYBE Matches, social graph edges, notifications, and messages are stored in Supabase PostgreSQL with Row Level Security (RLS) enforced per authenticated user JWT. Uploaded media files are stored in dedicated Supabase Storage buckets ('avatars', 'posts', 'stories', and 'chat-media').\n\n" +
                    "• Private Content & Signed URLs: Private account posts/stories, VYBE Matches stories, and chat attachments use restricted Storage buckets and time-limited signed URLs so unauthorized users cannot bypass privacy rules via direct API requests.\n\n" +
                    "• Media & Record Deletion: Deleting a post or story removes the database row (cascading associated likes, comments, and view records) and requests deletion of the corresponding Supabase Storage object.\n\n" +
                    "• Retention, CDN & Backup Transparency: While deleted content is immediately removed from active VYBE feeds and primary Storage buckets, copies may temporarily persist in edge CDN caches until cache TTL expiration, in recipient device memory/image caches if already viewed, or in encrypted Supabase point-in-time recovery database backups until standard cloud backup rotation completes.",
                  fontSize = 12.sp,
                  lineHeight = 18.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("Support Tickets", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
              Button(
                onClick = { showTicketDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Text("+ New Ticket", fontSize = 12.sp, color = Color.White)
              }
            }
          }

          if (supportTickets.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("No support tickets created.", color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          } else {
            items(supportTickets, key = { it.id }) { ticket ->
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(ticket.subject, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Surface(
                      color = when (ticket.status) {
                        "Resolved" -> VybeOnlineGreen.copy(alpha = 0.2f)
                        "Investigating" -> Color(0xFFFFB74D).copy(alpha = 0.2f)
                        else -> VybeViolet.copy(alpha = 0.2f)
                      },
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = ticket.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (ticket.status) {
                          "Resolved" -> VybeOnlineGreen
                          "Investigating" -> Color(0xFFFFB74D)
                          else -> VybeViolet
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(ticket.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  if (ticket.agentResponse != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(modifier = Modifier.padding(8.dp)) {
                        Text("NR Next Safety Support:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = VybeBrandCyan)
                        Text(ticket.agentResponse, fontSize = 11.sp)
                      }
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
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            GitHubUpdateSectionCard(isOnline = isOnline)
          }
        }
      }

      // SUBPAGE: CLEAN ABOUT VYBE (Redundant GitHub Updater Removed)
      SettingsSubpage.ABOUT -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
            ) {
              Image(
                painter = painterResource(id = R.drawable.vybe_app_icon_clean),
                contentDescription = "VYBE Logo",
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("VYBE", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(
              "Version ${GitHubUpdater.INSTALLED_VERSION}",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Product of NR Next",
              fontSize = 12.sp,
              fontWeight = FontWeight.Bold,
              color = VybeBrandCyan,
              letterSpacing = 1.sp
            )
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Text(
                  text = "Designed for Authentic Expression",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp
                )
                Text(
                  text = "VYBE brings together expressive social feeds, mood-aligned spaces, VYBE Matches stories, Time Capsules, and privacy-first connections built by NR Next.",
                  fontSize = 13.sp,
                  lineHeight = 19.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          item {
            Text(
              text = "Connect • Share • Be You",
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant
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
      title = { Text("Open Support Ticket", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = ticketSubject,
            onValueChange = { ticketSubject = it },
            label = { Text("Subject") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = ticketMessage,
            onValueChange = { ticketMessage = it },
            label = { Text("Description of your issue") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (ticketSubject.isNotBlank() && ticketMessage.isNotBlank()) {
              viewModel.submitSupportTicket(ticketCategory, ticketSubject, ticketMessage)
              ticketSubject = ""
              ticketMessage = ""
              showTicketDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Submit Ticket", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showTicketDialog = false }) { Text("Cancel") }
      }
    )
  }

  if (showDeactivateModal) {
    AlertDialog(
      onDismissRequest = { showDeactivateModal = false },
      title = { Text("Deactivate VYBE Account?", fontWeight = FontWeight.Bold) },
      text = {
        Text("Your profile, posts, and mood history will be hidden from other members. You can sign back in to restore access.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deactivateAccount()
            showDeactivateModal = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Confirm Deactivation", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDeactivateModal = false }) { Text("Cancel") }
      }
    )
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
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .clickable { onClick() }
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
      .padding(horizontal = 14.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
    Spacer(modifier = Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
      Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
