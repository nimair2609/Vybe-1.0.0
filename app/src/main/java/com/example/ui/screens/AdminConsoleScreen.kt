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
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeVerifiedGold
import com.example.ui.theme.VybeViolet
import java.time.Instant
import java.time.temporal.ChronoUnit

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

  if (isVerifyingRole) {
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
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    return
  }

  if (currentUser?.role != UserRole.ADMIN) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("Admin Access Restricted", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "This portal is reserved for authorized platform administrators and trust & safety moderators.",
          textAlign = TextAlign.Center,
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
    return Instant.now().plus(days.toLong(), ChronoUnit.DAYS).toString()
  }

  Scaffold(
    topBar = {
      Column {
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
              Text("Admin & Trust Safety", fontWeight = FontWeight.Bold)
              Text("Authorized Admin Console", fontSize = 11.sp, color = VybeVerifiedGold)
            }
          },
          actions = {
            IconButton(
              onClick = {
                viewModel.refreshAllData()
                viewModel.refreshVerificationRequests()
              },
              modifier = Modifier.testTag("admin_refresh_button")
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Refresh Admin Queues")
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = VybeVerifiedGold.copy(alpha = 0.15f),
              modifier = Modifier.padding(end = 12.dp)
            ) {
              Text(
                text = "OWNER ADMIN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = VybeVerifiedGold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )

        TabRow(
          selectedTabIndex = selectedTab.ordinal,
          containerColor = MaterialTheme.colorScheme.surface,
          contentColor = MaterialTheme.colorScheme.primary
        ) {
          AdminTab.values().forEach { tab ->
            Tab(
              selected = selectedTab == tab,
              onClick = { selectedTab = tab },
              text = {
                Text(
                  tab.title,
                  fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
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
          modifier = Modifier.padding(16.dp),
          action = {
            TextButton(onClick = { feedbackBanner = null }) {
              Text("OK", color = VybeBrandCyan)
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
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(22.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Zero-Trust Security & Minimum Age Enforcement", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Indian IT Rules (Intermediary Guidelines) compliance active. Reports are reviewed with audit logging. Private messages remain inaccessible without lawful warrant.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 15.sp
                )
              }
            }
          }

          item {
            Text(
              "Pending Incident & Violation Queue (${reports.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          if (reports.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(32.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("No flagged reports in queue.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
              }
            }
          } else {
            items(reports, key = { it.id }) { report ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("report_card_${report.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }

                  Spacer(modifier = Modifier.height(8.dp))
                  Text(report.reason, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = report.summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                  )
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    text = "Reported User: @${report.reportedUser} • Reporter: @${report.reportedByUsername}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Dismiss")
                      }
                      Spacer(modifier = Modifier.width(8.dp))
                      Button(
                        onClick = { viewModel.submitAdminAction(report.id, ReportStatus.ACTION_TAKEN) },
                        colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("action_report_${report.id}")
                      ) {
                        Text("Take Down / Suspend", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
                    }
                  } else {
                    Surface(
                      color = VybeOnlineGreen.copy(alpha = 0.15f),
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.align(Alignment.End)
                    ) {
                      Text(
                        text = "Status: ${report.status.name}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeOnlineGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                      )
                    }
                  }
                }
              }
            }
          }
        }

        AdminTab.VERIFICATIONS -> {
          // 1. Direct Official Badge Assignment & Revocation Panel
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.45f)),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_direct_badge_panel")
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Verified, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    "Assign or Revoke Official VYBE Badges",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                }
                Text(
                  "Changes execute via server-verified RPC with audit logging. Assigned badges notify the recipient for user acceptance.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                  value = userSearchQuery,
                  onValueChange = { userSearchQuery = it },
                  label = { Text("Search Member (@username, Name, or VYBE ID)") },
                  singleLine = true,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_badge_user_search_input")
                )

                if (matchedUsersForBadge.isNotEmpty()) {
                  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    matchedUsersForBadge.forEach { member ->
                      val isSelected = selectedTargetUserId == member.id
                      Surface(
                        color = if (isSelected) VybeViolet.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                          1.dp,
                          if (isSelected) VybeViolet else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier
                          .fillMaxWidth()
                          .clip(RoundedCornerShape(10.dp))
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
                              Text(member.displayName.ifBlank { member.username }, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                              VerificationBadge(user = member, iconSize = 15.dp)
                            }
                            Text("@${member.username} • ${member.publicVybeId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                          }
                          if (isSelected) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = VybeViolet, modifier = Modifier.size(18.dp))
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
                    Triple("VERIFIED", "Blue Tick", Color(0xFF2563EB)),
                    Triple("VIP", "VIP Gold", Color(0xFFD97706)),
                    Triple("SHOP", "Shop Gold", Color(0xFFD97706)),
                    Triple("WORKER", "Worker", Color(0xFF059669))
                  ).forEach { (bType, bLabel, bColor) ->
                    val isSelected = selectedBadgeType == bType
                    Surface(
                      color = if (isSelected) bColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                      shape = RoundedCornerShape(8.dp),
                      border = BorderStroke(1.dp, if (isSelected) bColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                      modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedBadgeType = bType }
                        .testTag("admin_badge_type_$bType")
                    ) {
                      Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(bLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) bColor else MaterialTheme.colorScheme.onSurface)
                      }
                    }
                  }
                }

                // Optional VIP Expiry Window
                if (selectedBadgeType == "VIP") {
                  Text("VIP Validity Window:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                        label = { Text(label, fontSize = 11.sp) }
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
                      fontWeight = FontWeight.Medium
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
                  placeholder = { Text("e.g., Confirmed identity / Employment ended") },
                  singleLine = true,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_badge_reason_input")
                )

                val isTargetingSelf = selectedTargetUserId.isNotBlank() && selectedTargetUserId == currentUser?.id
                if (isTargetingSelf) {
                  Text(
                    "Security Policy: Administrators cannot self-grant badges to their own account.",
                    fontSize = 11.sp,
                    color = VybeDanger,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp),
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
                    border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 46.dp)
                      .testTag("admin_revoke_badge_button")
                  ) {
                    Text("Revoke Badge", color = VybeDanger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  }
                }
              }
            }
          }

          // 2. Official Badge Verification Requests Queue
          item {
            Text(
              "Badge Verification Applications (${verificationRequests.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          if (verificationRequests.isEmpty()) {
            item {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("No badge applications in queue.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          } else {
            items(verificationRequests, key = { "vreq_${it.id}" }) { req ->
              var reviewerNoteInput by remember(req.id) { mutableStateOf(req.reviewerNotes.orEmpty()) }
              var reqVipDays by remember(req.id) { mutableStateOf<Int?>(365) }
              val isSelfApplicant = req.userId == currentUser?.id

              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("verification_req_card_${req.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
              ) {
                Column(modifier = Modifier.padding(14.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "${req.fullName.ifBlank { req.displayName }} (@${req.username.ifBlank { req.userId.take(8) }})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                      )
                      Text(
                        text = "Requested: ${req.requestType} • ${req.category}",
                        fontSize = 11.sp,
                        color = VybeBrandCyan,
                        fontWeight = FontWeight.SemiBold
                      )
                    }
                    Surface(
                      color = when (req.status) {
                        "APPROVED" -> VybeOnlineGreen.copy(alpha = 0.18f)
                        "REJECTED" -> VybeDanger.copy(alpha = 0.18f)
                        else -> VybeVerifiedGold.copy(alpha = 0.18f)
                      },
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = req.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (req.status) {
                          "APPROVED" -> VybeOnlineGreen
                          "REJECTED" -> VybeDanger
                          else -> VybeVerifiedGold
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                      )
                    }
                  }

                  if (!req.identityDocumentUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Evidence / Link: ${req.identityDocumentUrl}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  if (req.statement.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Statement: ${req.statement}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                  }

                  if (req.status == "PENDING" || req.status == "MORE_INFO_REQUIRED") {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (req.requestType.equals("VIP", ignoreCase = true)) {
                      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "No Expiry", 90 to "90d", 365 to "1yr").forEach { (d, label) ->
                          FilterChip(
                            selected = reqVipDays == d,
                            onClick = { reqVipDays = d },
                            label = { Text(label, fontSize = 10.sp) }
                          )
                        }
                      }
                    }

                    OutlinedTextField(
                      value = reviewerNoteInput,
                      onValueChange = { reviewerNoteInput = it },
                      label = { Text("Reviewer Decision Note") },
                      singleLine = true,
                      shape = RoundedCornerShape(8.dp),
                      modifier = Modifier.fillMaxWidth()
                    )

                    if (isSelfApplicant) {
                      Spacer(modifier = Modifier.height(6.dp))
                      Text(
                        "Cannot self-approve your own application. Another authorized admin must review.",
                        fontSize = 11.sp,
                        color = VybeDanger,
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
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("reject_vreq_${req.id}")
                      ) {
                        Text("Reject", color = VybeDanger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }

                      OutlinedButton(
                        onClick = {
                          viewModel.adminReviewVerificationRequest(
                            requestId = req.id,
                            decision = "MORE_INFO_REQUIRED",
                            notes = reviewerNoteInput.ifBlank { "Please provide an official verifiable link." }
                          ) { _, msg -> feedbackBanner = msg }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("more_info_vreq_${req.id}")
                      ) {
                        Text("More Info", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                        colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("approve_vreq_${req.id}")
                      ) {
                        Text("Approve ✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                      }
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
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          item {
            if (businessApplications.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("No pending commercial shop applications.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            } else {
              Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                businessApplications.forEach { app ->
                  Card(
                    modifier = Modifier
                      .fillMaxWidth()
                      .testTag("business_app_card_${app.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.4f))
                  ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(app.businessName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Surface(
                          color = when (app.status) {
                            BusinessVerificationStatus.APPROVED -> VybeOnlineGreen.copy(alpha = 0.2f)
                            BusinessVerificationStatus.PENDING -> VybeVerifiedGold.copy(alpha = 0.2f)
                            else -> VybeDanger.copy(alpha = 0.2f)
                          },
                          shape = RoundedCornerShape(6.dp)
                        ) {
                          Text(
                            text = app.status.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (app.status) {
                              BusinessVerificationStatus.APPROVED -> VybeOnlineGreen
                              BusinessVerificationStatus.PENDING -> VybeVerifiedGold
                              else -> VybeDanger
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                          )
                        }
                      }

                      Spacer(modifier = Modifier.height(4.dp))
                      Text("${app.category} • Reg/GST: ${app.registrationNumberOrGst}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                      Text("Address: ${app.address} • Contact: ${app.contactEmail}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

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
                            Text("Reject", color = VybeDanger, fontSize = 11.sp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("approve_biz_app_${app.id}")
                          ) {
                            Text("Approve Verification ✓", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                          }
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
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("VYBE Platform Compliance Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Brand:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("VYBE by NR Next | NR Group", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Intermediary Rules:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("Active (24h Grievance Window)", fontSize = 12.sp, color = VybeOnlineGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Badge Security & Audit:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("RLS Trigger + Audit Log Enforced", fontSize = 12.sp, color = VybeOnlineGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Push Architecture:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("FCM HTTP v1 Data-Only + Event Dedup", fontSize = 12.sp, color = VybeOnlineGreen, fontWeight = FontWeight.Bold)
                }
              }
            }
          }

          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Web Admin Console Available", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "For large-screen management on Windows laptops and desktops, access the VYBE Web Admin Panel via the web portal with Owner Admin credentials.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 15.sp
                )
              }
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
