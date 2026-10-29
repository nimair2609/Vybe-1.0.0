package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.VybeViewModel
import com.example.ui.screens.admin.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class AdminCentralTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
  DASHBOARD("Dashboard", Icons.Default.Dashboard),
  INTEGRATIONS("APIs & Integrations", Icons.Default.Api),
  SETTINGS("App Settings", Icons.Default.Settings),
  ADVERTISEMENTS("Ads & Sponsors", Icons.Default.Campaign),
  USERS_CREATORS("Users & Creators", Icons.Default.People),
  MODERATION("Content Safety", Icons.Default.Shield),
  BROADCAST("Push Alerts", Icons.Default.Notifications),
  AUDIT_LOGS("Audit Logs", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val currentUser by viewModel.currentUser.collectAsState()
  val adminSubRole by viewModel.adminSubRole.collectAsState()
  val adminAppSettings by viewModel.adminAppSettings.collectAsState()
  val adCampaigns by viewModel.adCampaigns.collectAsState()
  val adminIntegrations by viewModel.adminIntegrations.collectAsState()
  val adminAuditLogs by viewModel.adminAuditLogs.collectAsState()
  val adminStats by viewModel.adminStats.collectAsState()
  val reports by viewModel.adminReports.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()

  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var isVerifyingRole by remember { mutableStateOf(currentUser == null) }
  var selectedTab by remember { mutableStateOf(AdminCentralTab.DASHBOARD) }
  var isRoleSwitcherOpen by remember { mutableStateOf(false) }

  fun showSnackbar(message: String) {
    scope.launch {
      snackbarHostState.showSnackbar(message)
    }
  }

  LaunchedEffect(currentUser?.id) {
    if (currentUser == null || currentUser?.role != UserRole.ADMIN) {
      isVerifyingRole = true
      viewModel.refreshCurrentUserRole {
        isVerifyingRole = false
      }
    } else {
      isVerifyingRole = false
    }
  }

  if (isVerifyingRole) {
    Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = VybeVerifiedGold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
          "Verifying administrator authorization...",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    return
  }

  if (currentUser?.role != UserRole.ADMIN) {
    Box(
      modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Admin Access Restricted", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "This portal is reserved for authorized platform administrators and trust & safety moderators.",
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.navigateBack() }) {
          Text("Go Back")
        }
      }
    }
    return
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isWideScreen = maxWidth >= 720.dp

    Scaffold(
      snackbarHost = { SnackbarHost(snackbarHostState) },
      topBar = {
        TopAppBar(
          navigationIcon = {
            IconButton(
              onClick = { viewModel.navigateBack() },
              modifier = Modifier.testTag("admin_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          },
          title = {
            Column {
              Text("Central Admin Control Center", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Text(
                currentUser?.email?.ifBlank { "owner@vybe.nrnext.in" } ?: "owner@vybe.nrnext.in",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          },
          actions = {
            // Role switcher pill button
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(adminSubRole.badgeColorHex).copy(alpha = 0.15f),
              border = BorderStroke(1.dp, Color(adminSubRole.badgeColorHex).copy(alpha = 0.4f)),
              modifier = Modifier
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { isRoleSwitcherOpen = true }
                .testTag("admin_role_badge_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(adminSubRole.badgeColorHex))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = adminSubRole.displayName,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color(adminSubRole.badgeColorHex)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  Icons.Default.ArrowDropDown,
                  contentDescription = "Switch Role",
                  tint = Color(adminSubRole.badgeColorHex),
                  modifier = Modifier.size(14.dp)
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )
      }
    ) { paddingValues ->

      if (isWideScreen) {
        // Tablet / Desktop Wide Layout with Side Navigation Rail
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
          NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(180.dp)
          ) {
            AdminCentralTab.values().forEach { tab ->
              NavigationRailItem(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                icon = { Icon(tab.icon, contentDescription = tab.title) },
                label = { Text(tab.title, fontSize = 10.sp, maxLines = 1) },
                modifier = Modifier.testTag("admin_rail_tab_${tab.name.lowercase()}")
              )
            }
          }

          VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            item {
              RenderActiveTabContent(
                tab = selectedTab,
                viewModel = viewModel,
                stats = adminStats,
                recentLogs = adminAuditLogs,
                integrations = adminIntegrations,
                appSettings = adminAppSettings,
                campaigns = adCampaigns,
                reports = reports,
                users = allUsers,
                onNavigateToTab = { target ->
                  AdminCentralTab.values().firstOrNull { it.name == target }?.let { selectedTab = it }
                },
                onShowSnackbar = { showSnackbar(it) }
              )
            }
            item { Spacer(modifier = Modifier.height(60.dp)) }
          }
        }
      } else {
        // Mobile Compact Layout with Horizontal Scrollable Tab Row
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
          ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            edgePadding = 12.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().testTag("admin_scrollable_tabs")
          ) {
            AdminCentralTab.values().forEach { tab ->
              Tab(
                selected = selectedTab == tab,
                onClick = { selectedTab = tab },
                text = {
                  Text(
                    tab.title,
                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp
                  )
                },
                modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
              )
            }
          }

          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            item {
              RenderActiveTabContent(
                tab = selectedTab,
                viewModel = viewModel,
                stats = adminStats,
                recentLogs = adminAuditLogs,
                integrations = adminIntegrations,
                appSettings = adminAppSettings,
                campaigns = adCampaigns,
                reports = reports,
                users = allUsers,
                onNavigateToTab = { target ->
                  AdminCentralTab.values().firstOrNull { it.name == target }?.let { selectedTab = it }
                },
                onShowSnackbar = { showSnackbar(it) }
              )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
          }
        }
      }
    }
  }

  // Role Switcher Modal Dialog
  if (isRoleSwitcherOpen) {
    AlertDialog(
      onDismissRequest = { isRoleSwitcherOpen = false },
      title = { Text("Select Administrator Role", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Switch perspective to test role-specific view permissions and audit log attribution.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(4.dp))

          AdminSubRole.values().forEach { role ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = if (adminSubRole == role) Color(role.badgeColorHex).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
              border = BorderStroke(1.dp, if (adminSubRole == role) Color(role.badgeColorHex) else Color.Transparent),
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  viewModel.setAdminSubRole(role)
                  isRoleSwitcherOpen = false
                  showSnackbar("Switched active view to ${role.displayName}")
                }
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(role.badgeColorHex))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(role.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { isRoleSwitcherOpen = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun RenderActiveTabContent(
  tab: AdminCentralTab,
  viewModel: VybeViewModel,
  stats: AdminDashboardStats,
  recentLogs: List<AdminAuditLog>,
  integrations: List<AdminIntegrationItem>,
  appSettings: AdminAppSettings,
  campaigns: List<AdCampaign>,
  reports: List<AdminReport>,
  users: List<User>,
  onNavigateToTab: (String) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  when (tab) {
    AdminCentralTab.DASHBOARD -> {
      AdminDashboardTab(
        stats = stats,
        recentLogs = recentLogs,
        onNavigateToTab = onNavigateToTab
      )
    }

    AdminCentralTab.INTEGRATIONS -> {
      AdminIntegrationsTab(
        integrations = integrations,
        onTestConnection = { id ->
          val result = viewModel.pingIntegration(id)
          onShowSnackbar(result.second)
        },
        onShowSnackbar = onShowSnackbar
      )
    }

    AdminCentralTab.SETTINGS -> {
      AdminSettingsTab(
        initialSettings = appSettings,
        onSaveSettings = { updated ->
          viewModel.updateAppSettings(updated)
        },
        onShowSnackbar = onShowSnackbar
      )
    }

    AdminCentralTab.ADVERTISEMENTS -> {
      AdminAdsTab(
        campaigns = campaigns,
        onToggleStatus = { id -> viewModel.toggleAdCampaignStatus(id) },
        onArchive = { id -> viewModel.archiveAdCampaign(id) },
        onCreateCampaign = { campaign -> viewModel.createAdCampaign(campaign) },
        onShowSnackbar = onShowSnackbar
      )
    }

    AdminCentralTab.USERS_CREATORS -> {
      AdminUsersTab(
        users = users,
        onSuspendUser = { uid, hours, reason -> viewModel.suspendUser(uid, hours, reason) },
        onUnsuspendUser = { uid -> viewModel.unsuspendUser(uid) },
        onBanUser = { uid, reason -> viewModel.banUser(uid, reason) },
        onUnbanUser = { uid -> viewModel.unbanUser(uid) },
        onToggleFoundingBadge = { uid -> viewModel.toggleFoundingCreatorBadge(uid) },
        onToggleSpotlight = { uid -> viewModel.toggleCreatorSpotlight(uid) },
        onHandleDeletionRequest = { uid, approve -> viewModel.handleAccountDeletionRequest(uid, approve) },
        onShowSnackbar = onShowSnackbar
      )
    }

    AdminCentralTab.MODERATION -> {
      AdminModerationTab(
        reports = reports,
        onTakeDown = { repId, targetId, type ->
          viewModel.takeDownReportedContent(repId, targetId, type)
        },
        onDismiss = { repId ->
          viewModel.submitAdminAction(repId, ReportStatus.DISMISSED)
        },
        onSuspendAuthor = { username ->
          val target = users.firstOrNull { it.username == username }
          if (target != null) {
            viewModel.suspendUser(target.id, 24, "Content Moderation Sanction")
          }
        },
        onShowSnackbar = onShowSnackbar
      )
    }

    AdminCentralTab.BROADCAST -> {
      AdminBroadcastTab(
        onSendBroadcast = { title, body, destination, mediaUrl, callback ->
          viewModel.sendAdminBroadcastNotification(title, body, destination, mediaUrl, callback)
        },
        onShowSnackbar = onShowSnackbar
      )
    }

    AdminCentralTab.AUDIT_LOGS -> {
      AdminAuditTab(
        auditLogs = recentLogs,
        backendStatus = stats.backendStatus,
        storageUsageMb = stats.storageUsageMb
      )
    }
  }
}
