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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.util.GitHubUpdater
import com.example.util.GitHubReleaseInfo
import com.example.data.model.AppThemeMode
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

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

  var showDeactivateModal by remember { mutableStateOf(false) }
  var showTicketDialog by remember { mutableStateOf(false) }
  var ticketCategory by remember { mutableStateOf("Account & Verification") }
  var ticketSubject by remember { mutableStateOf("") }
  var ticketMessage by remember { mutableStateOf("") }
  var ticketFeedback by remember { mutableStateOf<String?>(null) }

  // Account Center state
  var editFirstName by remember { mutableStateOf(currentUser?.firstName ?: "Siddharth") }
  var editLastName by remember { mutableStateOf(currentUser?.lastName ?: "Verma") }
  var editUserId by remember { mutableStateOf(currentUser?.username ?: "sid_verma") }
  var userIdCheckStatus by remember { mutableStateOf<String?>(null) }
  var isUserIdAvailable by remember { mutableStateOf(true) }
  var profileSaveStatus by remember { mutableStateOf<String?>(null) }

  // GitHub in-app updater state
  val coroutineScope = rememberCoroutineScope()
  val context = LocalContext.current
  var githubRepoInput by remember { mutableStateOf("my-github-username/vybe") }
  var isCheckingUpdate by remember { mutableStateOf(false) }
  var updateResult by remember { mutableStateOf<Result<GitHubReleaseInfo>?>(null) }

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
              SettingsSubpage.HELP -> "Help & Safety Guidelines"
              SettingsSubpage.SUPPORT_TICKETS -> "Support Tickets"
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
        // Main Settings Directory (Section 5)
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Profile summary card -> Leads to Account Center
          if (currentUser != null) {
            item {
              Spacer(modifier = Modifier.height(4.dp))
              Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f)),
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
                      VerificationBadge(user = currentUser!!)
                    }
                    Text("@${currentUser!!.username} • Account Center", fontSize = 12.sp, color = VybeViolet)
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          // Section A: Account and Preferences
          item {
            Text("Account and Preferences", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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
                  subtitle = "Revisit and restore your private memories",
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
                  subtitle = "Push notifications, sound vibes, and email alerts",
                  onClick = { activeSubpage = SettingsSubpage.NOTIFICATIONS }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                SettingsListItem(
                  icon = Icons.Default.Timer,
                  iconTint = Color(0xFF4ADE80),
                  title = "Time Management",
                  subtitle = "Screen time insights, take-a-break reminders",
                  onClick = { activeSubpage = SettingsSubpage.TIME_MANAGEMENT }
                )
              }
            }
          }

          // Section B: Appearance / Theme (Section 4 requirement)
          item {
            Text("Appearance & Theme", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Text("Select Application Theme:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  listOf(AppThemeMode.DARK, AppThemeMode.LIGHT, AppThemeMode.SYSTEM).forEach { mode ->
                    val isSelected = currentThemeMode == mode
                    Surface(
                      color = if (isSelected) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(12.dp),
                      border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, VybeViolet) else null,
                      modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.setThemeMode(mode) }
                    ) {
                      Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                      ) {
                        Text(
                          text = when (mode) {
                            AppThemeMode.DARK -> "🌙 Dark"
                            AppThemeMode.LIGHT -> "☀️ Light"
                            AppThemeMode.SYSTEM -> "📱 System"
                          },
                          fontSize = 12.sp,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                          color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurface
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          // Section C: More Information
          item {
            Text("More Information", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column {
                SettingsListItem(
                  icon = Icons.Default.HelpOutline,
                  iconTint = VybeBrandCyan,
                  title = "Help & Guidelines",
                  subtitle = "Safety standards, 16+ age policies, privacy FAQ",
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
                  subtitle = "Version 2.0.0 • Product of NR Next",
                  onClick = { activeSubpage = SettingsSubpage.ABOUT }
                )
              }
            }
          }

          // Section D: Account Actions (Logout / Deactivate)
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
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

      // SUBPAGE 1: ACCOUNT CENTER (Section 5)
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
              text = "Manage your identity, personal details, unique User ID, and security activity across VYBE.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Name and ID Management
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

                // Unique User ID with live authoritative availability check (Section 5 & 6)
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
                    if (isUserIdAvailable) {
                      viewModel.updateAccountProfile(
                        firstName = editFirstName,
                        lastName = editLastName,
                        requestedUserId = editUserId,
                        email = currentUser?.email ?: "",
                        phone = currentUser?.phone ?: "",
                        bio = currentUser?.bio ?: "",
                        avatarUrl = currentUser?.avatarUrl ?: "",
                        profileVideoUrl = currentUser?.profileVideoUrl
                      )
                      profileSaveStatus = "Account identity updated successfully!"
                    }
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text("Save Identity Changes", fontWeight = FontWeight.Bold)
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

          // Active Device Sessions & Security Activity
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

      // SUBPAGE 2: SAVED
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
                Text("No saved items yet. Tap the bookmark icon on any post to save.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
              }
            }
          } else {
            items(savedPosts) { post ->
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
                    Text(post.caption, maxLines = 1, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

      // SUBPAGE 3: ARCHIVE
      SettingsSubpage.ARCHIVE -> {
        val archivedPosts = posts.filter { it.isArchived }
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
            Text("Archived posts are hidden from your profile and feed but completely preserved. You can restore them anytime.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          if (archivedPosts.isEmpty()) {
            item {
              Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("Archive is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
              }
            }
          } else {
            items(archivedPosts) { post ->
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
                    Text(post.caption, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Archived memory • ${post.timestamp}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Button(
                    onClick = { viewModel.restoreArchivedPost(post.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
                  ) {
                    Text("Restore", fontSize = 12.sp)
                  }
                }
              }
            }
          }
        }
      }

      // SUBPAGE 4: YOUR ACTIVITY
      SettingsSubpage.YOUR_ACTIVITY -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text("Complete audit trail of your interactions, reactions, comments, and sealed time capsules.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
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
                Text(
                  text = when (act.type) {
                    "POST_CREATED" -> "📝"
                    "REACTION_GIVEN" -> "✨"
                    "CAPSULE_SEALED" -> "⏳"
                    else -> "💬"
                  },
                  fontSize = 20.sp
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

      // SUBPAGE 5: NOTIFICATIONS
      SettingsSubpage.NOTIFICATIONS -> {
        var pDm by remember { mutableStateOf(notifPrefs.pushDirectMessages) }
        var pReact by remember { mutableStateOf(notifPrefs.pushPostReactions) }
        var pCapsule by remember { mutableStateOf(notifPrefs.pushTimeCapsuleUnlocks) }
        var pCrossed by remember { mutableStateOf(notifPrefs.pushCrossedPathsRequests) }

        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                  Switch(checked = pDm, onCheckedChange = { pDm = it })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Reactions & Comments", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(checked = pReact, onCheckedChange = { pReact = it })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Time Capsule Unlock Alerts", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(checked = pCapsule, onCheckedChange = { pCapsule = it })
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Crossed Paths Handshakes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Switch(checked = pCrossed, onCheckedChange = { pCrossed = it })
                }
              }
            }
          }
        }
      }

      // SUBPAGE 6: TIME MANAGEMENT
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
                Text("Schedule a gentle reminder to take a breath and step away from the screen.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

      // SUBPAGE 7: HELP
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
                  text = "1. 16+ Community Gate:\nVYBE requires all users to be aged 16 or older. Teen accounts (16-17) are shielded with mandatory privacy protections.\n\n2. Real-World Crossed Paths Consent:\nCrossed Paths never reveals precise GPS tracks. Both parties must mutually accept before any conversation can occur.\n\n3. Zero Audio Recording in UNLOAD:\nLive listening spaces transmit audio in real-time without server-side recording.\n\n4. UNFILTERED Authenticity:\nIn UNFILTERED, beauty filters, face reshaping, and vanity like/share counters are completely removed.",
                  fontSize = 12.sp,
                  lineHeight = 18.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }

      // SUBPAGE 8: SUPPORT TICKETS
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
                Text("+ New Ticket", fontSize = 12.sp)
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
            items(supportTickets) { ticket ->
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

      // SUBPAGE 9: ABOUT
      SettingsSubpage.ABOUT -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 20.dp, vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
              modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(22.dp))
            ) {
              androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.vybe_app_icon_clean),
                contentDescription = "VYBE Logo",
                modifier = Modifier.fillMaxSize()
              )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text("vybe", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Version 2.0.0 • Production Build", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = "Product of NR Next",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = VybeBrandCyan,
              letterSpacing = 1.sp
            )
          }

          // GitHub In-App Live Updater Card
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth().testTag("github_updater_card")
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.CloudSync, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(24.dp))
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text("GitHub Live Updates", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Sync directly from your repository releases", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                  value = githubRepoInput,
                  onValueChange = { githubRepoInput = it },
                  label = { Text("GitHub Repo (owner/repo)") },
                  placeholder = { Text("e.g. your-org/vybe") },
                  singleLine = true,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier.fillMaxWidth().testTag("github_repo_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text("Installed: v2.0.0", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                  Button(
                    onClick = {
                      isCheckingUpdate = true
                      updateResult = null
                      coroutineScope.launch {
                        val res = GitHubUpdater.checkForUpdates(githubRepoInput)
                        updateResult = res
                        isCheckingUpdate = false
                      }
                    },
                    enabled = !isCheckingUpdate && githubRepoInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("check_github_updates_button")
                  ) {
                    if (isCheckingUpdate) {
                      CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Checking...", fontSize = 12.sp)
                    } else {
                      Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Check for Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                  }
                }

                // Update Result Feedback
                updateResult?.let { res ->
                  Spacer(modifier = Modifier.height(12.dp))
                  res.onSuccess { release ->
                    Surface(
                      color = VybeOnlineGreen.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(10.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, VybeOnlineGreen.copy(alpha = 0.4f)),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VybeOnlineGreen, modifier = Modifier.size(18.dp))
                          Spacer(modifier = Modifier.width(8.dp))
                          Text("New Release: ${release.tagName}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VybeOnlineGreen)
                        }
                        if (release.name.isNotBlank() && release.name != release.tagName) {
                          Text(release.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (release.body.isNotBlank()) {
                          Text(
                            text = release.body.take(200) + if (release.body.length > 200) "..." else "",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                          )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                          onClick = {
                            val targetUrl = release.downloadUrl ?: release.htmlUrl
                            GitHubUpdater.openDownloadUrl(context, targetUrl)
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                          shape = RoundedCornerShape(8.dp),
                          modifier = Modifier.fillMaxWidth().testTag("download_update_button")
                        ) {
                          Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Download & Install Update", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                      }
                    }
                  }.onFailure { err ->
                    Surface(
                      color = VybeDanger.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(10.dp),
                      border = androidx.compose.foundation.BorderStroke(1.dp, VybeDanger.copy(alpha = 0.4f)),
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Icon(Icons.Default.Info, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(16.dp))
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("Release Status", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = VybeDanger)
                        }
                        Text(
                          text = err.message ?: "Could not fetch release.",
                          fontSize = 11.sp,
                          color = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                          text = "Tip: When you push new code to GitHub and create a Release with an APK asset, tapping 'Check for Update' allows instant over-the-air updates.",
                          fontSize = 10.sp,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                          modifier = Modifier.padding(top = 4.dp)
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          item {
            Text(
              text = "VYBE is built around authentic connections, creativity, memories, and distinctive social experiences.\n\nConnect • Share • Be You",
              fontSize = 12.sp,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center,
              lineHeight = 17.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
          }
        }
      }
    }
  }

  // Support Ticket Creation Dialog
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
          Text("Submit Ticket")
        }
      },
      dismissButton = {
        TextButton(onClick = { showTicketDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Deactivate Modal
  if (showDeactivateModal) {
    AlertDialog(
      onDismissRequest = { showDeactivateModal = false },
      title = { Text("Deactivate VYBE Account?", fontWeight = FontWeight.Bold) },
      text = {
        Text("Your profile, posts, and mood history will be hidden from other members. You can restore access anytime within 30 days.")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deactivateAccount()
            showDeactivateModal = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Confirm Deactivation")
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
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconTint: Color,
  title: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable { onClick() }
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
