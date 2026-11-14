package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BusinessVerificationStatus
import com.example.data.model.ReportStatus
import com.example.data.model.UserRole
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.TimestampUtils

enum class AdminTab(val title: String) {
  MODERATION("Moderation"),
  VERIFICATIONS("Badges & Shops"),
  COMPLIANCE("Security & Audit")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val reports by viewModel.adminReports.collectAsState()
  val verificationRequests by viewModel.verificationRequests.collectAsState()
  val businessApplications by viewModel.businessApplications.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var isVerifyingRole by remember { mutableStateOf(currentUser == null) }
  var selectedTab by remember { mutableStateOf(AdminTab.MODERATION) }
  var feedbackBanner by remember { mutableStateOf<String?>(null) }

  // Direct Official Badge Assignment & Revocation State
  var userSearchQuery by remember { mutableStateOf("") }
  var selectedTargetUserId by remember { mutableStateOf("") }
  var selectedBadgeType by remember { mutableStateOf("VERIFIED") }
  var selectedVipDurationDays by remember { mutableStateOf<Int?>(null) } // null = No Expiry, 30, 90, 365
  var mandatoryWorkerDisplay by remember { mutableStateOf(false) }
  var adminActionReason by remember { mutableStateOf("") }
  var isAssigningBadge by remember { mutableStateOf(false) }

  LaunchedEffect(currentUser?.id) {
    if (currentUser == null || currentUser?.role != UserRole.ADMIN) {
      isVerifyingRole = true
      viewModel.refreshCurrentUserRole {
        isVerifyingRole = false
      }
    } else {
      isVerifyingRole = false
      viewModel.refreshVerificationRequests()
    }
  }

  LaunchedEffect(selectedTab) {
    if (selectedTab == AdminTab.VERIFICATIONS && currentUser?.role == UserRole.ADMIN) {
      viewModel.refreshVerificationRequests()
    }
  }

  val palette = LocalVybePalette.current

  if (isVerifyingRole) {
    Surface(color = palette.canvas, modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(color = VybeVerifiedGold)
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            "Verifying administrator authorization...",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
          )
        }
      }
    }
    return
  }

  if (currentUser?.role != UserRole.ADMIN) {
    Surface(color = palette.canvas, modifier = Modifier.fillMaxSize()) {
      Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
          Icon(Icons.Default.Lock, contentDescription = null, tint = palette.danger, modifier = Modifier.size(48.dp))
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            "Admin Access Restricted",
            fontFamily = PlusJakartaSansFontFamily,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "This portal is reserved for authorized platform administrators and trust & safety moderators.",
            textAlign = TextAlign.Center,
            color = palette.textSecondary
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = { viewModel.navigateBack() },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            shape = RoundedCornerShape(VybeTokens.Radius.md)
          ) {
            Text("Go Back", color = Color.White)
          }
        }
      }
    }
    return
  }

  val matchedUsersForBadge = remember(userSearchQuery, allUsers) {
    val q = userSearchQuery.trim().removePrefix("@").lowercase()
    if (q.isBlank()) {
      allUsers.take(6)
    } else {
      allUsers.filter {
        it.username.lowercase().contains(q) ||
          it.displayName.lowercase().contains(q) ||
          it.publicVybeId.lowercase().contains(q) ||
          it.id.lowercase().contains(q)
      }.take(8)
    }
  }

  val resolvedTargetUser = remember(selectedTargetUserId, allUsers) {
    allUsers.firstOrNull { it.id == selectedTargetUserId }
  }

  fun computeVipExpiresIso(days: Int?): String? {
    if (days == null || days <= 0) return null
    return TimestampUtils.formatIsoUtc(System.currentTimeMillis() + days.toLong() * 86_400_000L)
  }

  val adminFieldColors = OutlinedTextFieldDefaults.colors(
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
      Column {
        VybeSubScreenTopBar(
          title = "Admin & Trust Safety",
          subtitle = "Authorized Admin Console",
          onBackClick = { viewModel.navigateBack() },
          backTestTag = "admin_back_button",
          actions = {
            IconButton(
              onClick = {
                viewModel.refreshAllData()
                viewModel.refreshVerificationRequests()
              },
              modifier = Modifier.testTag("admin_refresh_button")
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh Admin Queues", tint = palette.textPrimary)
            }
            Surface(
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              color = VybeVerifiedGold.copy(alpha = 0.16f),
              modifier = Modifier.padding(end = 8.dp)
            ) {
              Text(
                text = "OWNER ADMIN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = VybeVerifiedGold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        )

        TabRow(
          selectedTabIndex = selectedTab.ordinal,
          containerColor = palette.card,
          contentColor = palette.accent
        ) {
          AdminTab.values().forEach { tab ->
            Tab(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              text = {
                Text(
                  tab.title,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                  color = if (selectedTab == tab) palette.accent else palette.textSecondary,
                  fontSize = 12.sp
                )
              },
              modifier = Modifier.testTag("admin_tab_${tab.name.lowercase()}")
            )
          }
        }
      }
    },
    snackbarHost = {
      feedbackBanner?.let { msg ->
        Snackbar(
          containerColor = palette.card,
          contentColor = palette.textPrimary,
          modifier = Modifier.padding(16.dp),
          action = {
            TextButton(onClick = { feedbackBanner = null }) {
              Text("OK", color = palette.accent, fontWeight = FontWeight.Bold)
            }
          }
        ) {
          Text(msg)
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      when (selectedTab) {
        AdminTab.MODERATION -> {
          item {
            VybeSurfaceCard(containerColor = palette.elevated) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  "Zero-Trust Security & Minimum Age Enforcement",
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = palette.textPrimary
                )
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Indian IT Rules (Intermediary Guidelines) compliance active. Reports are reviewed with audit logging. Private messages remain inaccessible without lawful warrant.",
                fontSize = 11.sp,
                color = palette.textSecondary,
                lineHeight = 15.sp
              )
            }
          }

          item {
            Text(
              "Pending Incident & Violation Queue (${reports.size})",
              fontFamily = PlusJakartaSansFontFamily,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
          }

          if (reports.isEmpty()) {
            item {
              VybeEmptyState(
                icon = Icons.Default.VerifiedUser,
                title = "No Flagged Reports",
                subtitle = "No moderation reports are currently waiting for review."
              )
            }
          } else {
            items(reports, key = { it.id }) { report ->
              VybeSurfaceCard(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("report_card_${report.id}")
              ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Surface(
                      color = if (report.severity == "High") VybeDanger.copy(alpha = 0.15f) else VybeVerifiedGold.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "Severity: ${report.severity}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (report.severity == "High") VybeDanger else VybeVerifiedGold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }

                    Text(
                      text = report.timestamp,
                      fontSize = 11.sp,
                      color = palette.textSecondary
                    )
                  }

                  Spacer(modifier = Modifier.height(8.dp))
                  Text(report.reason, fontFamily = PlusJakartaSansFontFamily, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.textPrimary)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = report.summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                    lineHeight = 18.sp
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Reported User: @${report.reportedUser} • Reporter: @${report.reportedByUsername}",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                  )
                  Spacer(modifier = Modifier.height(12.dp))

                  if (report.status == ReportStatus.PENDING) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      TextButton(
                        onClick = { viewModel.submitAdminAction(report.id, ReportStatus.DISMISSED) },
                        modifier = Modifier.testTag("dismiss_report_${report.id}")
                      ) {
                        Text("Dismiss", color = palette.textSecondary)
                      }
                      Spacer(modifier = Modifier.width(8.dp))
                      Button(
                        onClick = { viewModel.submitAdminAction(report.id, ReportStatus.ACTION_TAKEN) },
                        colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = Color.White),
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        modifier = Modifier.testTag("action_report_${report.id}")
                      ) {
                        Text("Take Down / Suspend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  } else {
                    Surface(
                      color = palette.success.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(VybeTokens.Radius.xs),
                      modifier = Modifier.align(Alignment.End)
                    ) {
                      Text(
                        text = "Status: ${report.status.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.success,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                      )
                    }
                  }
                }
            }
          }
        }

        AdminTab.VERIFICATIONS -> {
          // 1. Direct Official Badge Assignment & Revocation Panel
          item {
            VybeSurfaceCard(
              borderColor = palette.accent.copy(alpha = 0.45f),
              contentPadding = PaddingValues(16.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_direct_badge_panel")
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Verified, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    "Assign or Revoke Official VYBE Badges",
                    fontFamily = PlusJakartaSansFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary
                  )
                }
                Text(
                  "Changes execute via server-verified RPC with audit logging. Assigned badges notify the recipient for user acceptance.",
                  fontSize = 11.sp,
                  color = palette.textSecondary
                )

                OutlinedTextField(
                  value = userSearchQuery,
                  onValueChange = { userSearchQuery = it },
                  label = { Text("Search Member (@username, Name, or VYBE ID)") },
                  singleLine = true,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  colors = adminFieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_badge_user_search_input")
                )

                if (matchedUsersForBadge.isNotEmpty()) {
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    matchedUsersForBadge.forEach { member ->
                      val isSelected = selectedTargetUserId == member.id
                      Surface(
                        color = if (isSelected) palette.accent.copy(alpha = 0.16f) else palette.elevated,
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        border = BorderStroke(
                          1.dp,
                          if (isSelected) palette.accent else palette.border
                        ),
                        modifier = Modifier
                          .fillMaxWidth()
                          .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                          .clickable { selectedTargetUserId = member.id }
                          .testTag("admin_select_member_${member.id}")
                      ) {
                        Row(
                          modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          VybeAvatar(user = member, size = 34.dp)
                          Spacer(modifier = Modifier.width(10.dp))
                          Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                              Text(member.displayName.ifBlank { member.username }, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
                              VerificationBadge(user = member, iconSize = 15.dp)
                            }
                            Text("@${member.username} • ${member.publicVybeId}", fontSize = 11.sp, color = palette.textSecondary)
                          }
                          if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = palette.accent, modifier = Modifier.size(18.dp))
                          }
                        }
                      }
                    }
                  }
                }

                // Badge Type Selector
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  listOf(
                    Triple("VERIFIED", "Blue Tick", VybeGrowBlueBright),
                    Triple("VIP", "VIP Gold", VybeVerifiedGold),
                    Triple("SHOP", "Shop Gold", VybeVerifiedGold),
                    Triple("WORKER", "Worker", palette.success)
                  ).forEach { (bType, bLabel, bColor) ->
                    val isSelected = selectedBadgeType == bType
                    Surface(
                      color = if (isSelected) bColor.copy(alpha = 0.2f) else palette.elevated,
                      shape = RoundedCornerShape(VybeTokens.Radius.sm),
                      border = BorderStroke(1.dp, if (isSelected) bColor else palette.border),
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 42.dp)
                        .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                        .clickable { selectedBadgeType = bType }
                        .testTag("admin_badge_type_$bType")
                    ) {
                      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(bLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) bColor else palette.textPrimary)
                      }
                    }
                  }
                }

                // Optional VIP Expiry Window
                if (selectedBadgeType == "VIP") {
                  Text("VIP Validity Window:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
                  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                      null to "No Expiry",
                      30 to "30 Days",
                      90 to "90 Days",
                      365 to "1 Year"
                    ).forEach { (days, label) ->
                      FilterChip(
                        selected = selectedVipDurationDays == days,
                        onClick = { selectedVipDurationDays = days },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                          selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                          containerColor = palette.elevated
                        )
                      )
                    }
                  }
                }

                // Mandatory Organizational Display Policy for Worker Badges
                if (selectedBadgeType == "WORKER") {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      "Mandatory Public Staff Display",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Medium,
                      color = palette.textPrimary
                    )
                    Switch(
                      checked = mandatoryWorkerDisplay,
                      onCheckedChange = { mandatoryWorkerDisplay = it },
                      modifier = Modifier.testTag("admin_worker_mandatory_switch")
                    )
                  }
                }

                OutlinedTextField(
                  value = adminActionReason,
                  onValueChange = { adminActionReason = it },
                  label = { Text("Audit Reason / Reviewer Note") },
                  placeholder = { Text("e.g., Confirmed identity / Employment ended", color = palette.textMuted) },
                  singleLine = true,
                  shape = RoundedCornerShape(VybeTokens.Radius.md),
                  colors = adminFieldColors,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_badge_reason_input")
                )

                val isTargetingSelf = selectedTargetUserId.isNotBlank() && selectedTargetUserId == currentUser?.id
                if (isTargetingSelf) {
                  Text(
                    "Security Policy: Administrators cannot self-grant badges to their own account.",
                    fontSize = 11.sp,
                    color = palette.danger,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("admin_self_grant_warning")
                  )
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Button(
                    onClick = {
                      if (selectedTargetUserId.isBlank()) {
                        feedbackBanner = "Select a member first."
                        return@Button
                      }
                      isAssigningBadge = true
                      viewModel.adminAssignBadge(
                        userId = selectedTargetUserId,
                        badgeCategory = selectedBadgeType,
                        isGrant = true,
                        vipExpiresAtIso = if (selectedBadgeType == "VIP") computeVipExpiresIso(selectedVipDurationDays) else null,
                        reason = adminActionReason.trim().ifBlank { "Approved by Admin" },
                        workerMandatory = if (selectedBadgeType == "WORKER") mandatoryWorkerDisplay else false
                      ) { ok, msg ->
                        isAssigningBadge = false
                        feedbackBanner = msg ?: if (ok) "Badge assigned to @${resolvedTargetUser?.username ?: selectedTargetUserId}" else "Assignment failed."
                      }
                    },
                    enabled = selectedTargetUserId.isNotBlank() && !isTargetingSelf && !isAssigningBadge,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(VybeTokens.Radius.md),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 46.dp)
                      .testTag("admin_assign_badge_button")
                  ) {
                    Text("Assign Badge", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }

                  OutlinedButton(
                    onClick = {
                      if (selectedTargetUserId.isBlank()) {
                        feedbackBanner = "Select a member first."
                        return@OutlinedButton
                      }
                      isAssigningBadge = true
                      viewModel.adminAssignBadge(
                        userId = selectedTargetUserId,
                        badgeCategory = selectedBadgeType,
                        isGrant = false,
                        vipExpiresAtIso = null,
                        reason = adminActionReason.trim().ifBlank { "Revoked by Admin" },
                        workerMandatory = false
                      ) { ok, msg ->
                        isAssigningBadge = false
                        feedbackBanner = msg ?: if (ok) "Badge revoked from @${resolvedTargetUser?.username ?: selectedTargetUserId}" else "Revocation failed."
                      }
                    },
                    enabled = selectedTargetUserId.isNotBlank() && !isAssigningBadge,
                    border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(VybeTokens.Radius.md),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 46.dp)
                      .testTag("admin_revoke_badge_button")
                  ) {
                    Text("Revoke Badge", color = palette.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }
                }
              }
            }
          }

          // 2. Official Badge Verification Requests Queue
          item {
            Text(
              "Badge Verification Applications (${verificationRequests.size})",
              fontFamily = PlusJakartaSansFontFamily,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
          }

          if (verificationRequests.isEmpty()) {
            item {
              VybeEmptyState(
                icon = Icons.Default.Verified,
                title = "No Badge Applications",
                subtitle = "No verification requests are pending review."
              )
            }
          } else {
            items(verificationRequests, key = { "vreq_${it.id}" }) { req ->
              var reviewerNoteInput by remember(req.id) { mutableStateOf(req.reviewerNotes.orEmpty()) }
              var reqVipDays by remember(req.id) { mutableStateOf<Int?>(365) }
              val isSelfApplicant = req.userId == currentUser?.id

              VybeSurfaceCard(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("verification_req_card_${req.id}")
              ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "${req.fullName.ifBlank { req.displayName }} (@${req.username.ifBlank { req.userId.take(8) }})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = palette.textPrimary
                      )
                      Text(
                        text = "Requested: ${req.requestType} • ${req.category}",
                        fontSize = 11.sp,
                        color = palette.accentSecondary,
                        fontWeight = FontWeight.SemiBold
                      )
                    }
                    Surface(
                      color = when (req.status) {
                        "APPROVED" -> palette.success.copy(alpha = 0.18f)
                        "REJECTED" -> palette.danger.copy(alpha = 0.18f)
                        else -> VybeVerifiedGold.copy(alpha = 0.18f)
                      },
                      shape = RoundedCornerShape(VybeTokens.Radius.xs)
                    ) {
                      Text(
                        text = req.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (req.status) {
                          "APPROVED" -> palette.success
                          "REJECTED" -> palette.danger
                          else -> VybeVerifiedGold
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                      )
                    }
                  }

                  if (!req.identityDocumentUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Evidence / Link: ${req.identityDocumentUrl}", fontSize = 11.sp, color = palette.textSecondary)
                  }
                  if (req.statement.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Statement: ${req.statement}", fontSize = 12.sp, color = palette.textPrimary)
                  }

                  if (req.status == "PENDING" || req.status == "MORE_INFO_REQUIRED") {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (req.requestType.equals("VIP", ignoreCase = true)) {
                      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "No Expiry", 90 to "90d", 365 to "1yr").forEach { (d, label) ->
                          FilterChip(
                            selected = reqVipDays == d,
                            onClick = { reqVipDays = d },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                              selectedContainerColor = palette.accent.copy(alpha = 0.16f),
                              containerColor = palette.elevated
                            )
                          )
                        }
                      }
                    }

                    OutlinedTextField(
                      value = reviewerNoteInput,
                      onValueChange = { reviewerNoteInput = it },
                      label = { Text("Reviewer Decision Note") },
                      colors = adminFieldColors,
                      singleLine = true,
                      shape = RoundedCornerShape(VybeTokens.Radius.sm),
                      modifier = Modifier.fillMaxWidth()
                    )

                    if (isSelfApplicant) {
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(
                        "Cannot self-approve your own application. Another authorized admin must review.",
                        fontSize = 11.sp,
                        color = palette.danger,
                        fontWeight = FontWeight.SemiBold
                      )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                      OutlinedButton(
                        onClick = {
                          viewModel.adminReviewVerificationRequest(
                            requestId = req.id,
                            decision = "REJECTED",
                            notes = reviewerNoteInput.ifBlank { "Criteria not met at this time." }
                          ) { _, msg -> feedbackBanner = msg }
                        },
                        border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        modifier = Modifier.weight(1f).testTag("reject_vreq_${req.id}")
                      ) {
                        Text("Reject", color = palette.danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      OutlinedButton(
                        onClick = {
                          viewModel.adminReviewVerificationRequest(
                            requestId = req.id,
                            decision = "MORE_INFO_REQUIRED",
                            notes = reviewerNoteInput.ifBlank { "Please provide an official verifiable link." }
                          ) { _, msg -> feedbackBanner = msg }
                        },
                        border = BorderStroke(1.dp, palette.border),
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        modifier = Modifier.weight(1f).testTag("more_info_vreq_${req.id}")
                      ) {
                        Text("More Info", color = palette.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                      }

                      Button(
                        onClick = {
                          viewModel.adminReviewVerificationRequest(
                            requestId = req.id,
                            decision = "APPROVED",
                            notes = reviewerNoteInput.ifBlank { "Verified by VYBE Trust & Safety." },
                            vipExpiresAtIso = if (req.requestType.equals("VIP", ignoreCase = true)) computeVipExpiresIso(reqVipDays) else null
                          ) { _, msg -> feedbackBanner = msg }
                        },
                        enabled = !isSelfApplicant,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.success, contentColor = Color.White),
                        shape = RoundedCornerShape(VybeTokens.Radius.sm),
                        modifier = Modifier.weight(1f).testTag("approve_vreq_${req.id}")
                      ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }
            }
          }

          // 3. Existing Commercial Shop Registry Applications
          item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              "Commercial Shop Registry Applications (${businessApplications.size})",
              fontFamily = PlusJakartaSansFontFamily,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
          }

          item {
            if (businessApplications.isEmpty()) {
              VybeEmptyState(
                icon = Icons.Default.Store,
                title = "No Commercial Applications",
                subtitle = "No commercial shop registry applications in the queue."
              )
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                businessApplications.forEach { app ->
                  VybeSurfaceCard(
                    borderColor = VybeVerifiedGold.copy(alpha = 0.4f),
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("business_app_card_${app.id}")
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(app.businessName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = palette.textPrimary)
                      Surface(
                        color = when (app.status) {
                          BusinessVerificationStatus.APPROVED -> palette.success.copy(alpha = 0.2f)
                          BusinessVerificationStatus.PENDING -> VybeVerifiedGold.copy(alpha = 0.2f)
                          else -> palette.danger.copy(alpha = 0.2f)
                        },
                        shape = RoundedCornerShape(VybeTokens.Radius.xs)
                      ) {
                        Text(
                          text = app.status.name,
                          fontSize = 10.sp,
                          fontWeight = FontWeight.Bold,
                          color = when (app.status) {
                            BusinessVerificationStatus.APPROVED -> palette.success
                            BusinessVerificationStatus.PENDING -> VybeVerifiedGold
                            else -> palette.danger
                          },
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                      }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${app.category} • Reg/GST: ${app.registrationNumberOrGst}", fontSize = 11.sp, color = palette.textSecondary)
                    Text("Address: ${app.address} • Contact: ${app.contactEmail}", fontSize = 11.sp, color = palette.textSecondary)

                    if (app.status == BusinessVerificationStatus.PENDING) {
                      Spacer(modifier = Modifier.height(10.dp))
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                      ) {
                        TextButton(
                          onClick = {
                            viewModel.reviewBusinessApplication(
                              app.id,
                              BusinessVerificationStatus.REJECTED,
                              "Missing trade license documentation."
                            )
                          },
                          modifier = Modifier.testTag("reject_biz_app_${app.id}")
                        ) {
                          Text("Reject", color = palette.danger, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                          onClick = {
                            viewModel.reviewBusinessApplication(
                              app.id,
                              BusinessVerificationStatus.APPROVED,
                              "Verified against commercial registry."
                            )
                          },
                          colors = ButtonDefaults.buttonColors(containerColor = palette.success),
                          shape = RoundedCornerShape(VybeTokens.Radius.sm),
                          modifier = Modifier.testTag("approve_biz_app_${app.id}")
                        ) {
                          Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                          Spacer(modifier = Modifier.width(4.dp))
                          Text("Approve Verification", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }

        AdminTab.COMPLIANCE -> {
          item {
            VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
              Text(
                "VYBE Platform Compliance Status",
                fontFamily = PlusJakartaSansFontFamily,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(8.dp))

              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Brand:", fontSize = 12.sp, color = palette.textSecondary)
                Text("VYBE by NR Next | NR Group", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Intermediary Rules:", fontSize = 12.sp, color = palette.textSecondary)
                Text("Active (24h Grievance Window)", fontSize = 12.sp, color = palette.success, fontWeight = FontWeight.Bold)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Badge Security & Audit:", fontSize = 12.sp, color = palette.textSecondary)
                Text("RLS Trigger + Audit Log Enforced", fontSize = 12.sp, color = palette.success, fontWeight = FontWeight.Bold)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Push Architecture:", fontSize = 12.sp, color = palette.textSecondary)
                Text("FCM HTTP v1 Data-Only + Event Dedup", fontSize = 12.sp, color = palette.success, fontWeight = FontWeight.Bold)
              }
            }
          }

          item {
            VybeSurfaceCard(containerColor = palette.elevated) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Language, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Web Admin Console Available", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "For large-screen management on Windows laptops and desktops, access the VYBE Web Admin Panel via the web portal with Owner Admin credentials.",
                fontSize = 11.sp,
                color = palette.textSecondary,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }
}
