package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Share
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
import com.example.data.model.BusinessVerificationStatus
import com.example.data.model.ReportStatus
import com.example.data.model.UserRole
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeMagenta
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeVerifiedGold
import com.example.ui.theme.VybeViolet
import kotlinx.coroutines.launch

enum class AdminTab(val title: String) {
  MODERATION("Moderation"),
  BROADCAST("Broadcast Push"),
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
  val currentUser by viewModel.currentUser.collectAsState()
  val isRealtimeConnected by viewModel.isRealtimeConnected.collectAsState()
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  var isVerifyingRole by remember { mutableStateOf(currentUser == null) }
  var selectedTab by remember { mutableStateOf(AdminTab.MODERATION) }

  // Broadcast Push composer state
  var pushTitle by remember { mutableStateOf("") }
  var pushBody by remember { mutableStateOf("") }
  var pushMediaUrl by remember { mutableStateOf("") }
  var pushDestination by remember { mutableStateOf("NOTIFICATIONS") }
  var isDispatchingPush by remember { mutableStateOf(false) }

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

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
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
              Text("Owner Admin: renukadas083@gmail.com", fontSize = 11.sp, color = VybeVerifiedGold)
            }
          },
          actions = {
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

        ScrollableTabRow(
          selectedTabIndex = selectedTab.ordinal,
          edgePadding = 12.dp,
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

                  Text(
                    text = report.reason,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )

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

        AdminTab.BROADCAST -> {
          item {
            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f))
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Campaign, contentDescription = null, tint = VybeMagenta)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Instagram-Style Real-Time Broadcast", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                  }
                  Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRealtimeConnected) VybeOnlineGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                  ) {
                    Text(
                      text = if (isRealtimeConnected) "● REALTIME LIVE" else "● READY",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isRealtimeConnected) VybeOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                  }
                }

                Text(
                  text = "Send an official VYBE notification with avatar branding, optional BigPicture banner preview, and instant (<100ms) real-time delivery to every connected phone.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 16.sp
                )

                OutlinedTextField(
                  value = pushTitle,
                  onValueChange = { pushTitle = it },
                  label = { Text("Notification Headline") },
                  placeholder = { Text("e.g. VYBE Creator Drop Live 🔥") },
                  singleLine = true,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_push_title_input")
                )

                OutlinedTextField(
                  value = pushBody,
                  onValueChange = { pushBody = it },
                  label = { Text("Message Body") },
                  placeholder = { Text("Write a crisp, professional update for VYBE members...") },
                  minLines = 3,
                  maxLines = 5,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_push_body_input")
                )

                OutlinedTextField(
                  value = pushMediaUrl,
                  onValueChange = { pushMediaUrl = it },
                  label = { Text("Banner / Post Image URL (Optional)") },
                  placeholder = { Text("https://... (expands as Android BigPicture preview)") },
                  singleLine = true,
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_push_media_input")
                )

                Text("Tap Destination", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  listOf(
                    "NOTIFICATIONS" to "Activity",
                    "MAIN" to "Home Feed",
                    "INBOX" to "Direct Inbox",
                    "LIVE_STUDIO" to "Live Studio"
                  ).forEach { (destKey, label) ->
                    FilterChip(
                      selected = pushDestination == destKey,
                      onClick = { pushDestination = destKey },
                      label = { Text(label, fontSize = 11.sp) },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = VybeViolet.copy(alpha = 0.2f),
                        selectedLabelColor = VybeViolet
                      )
                    )
                  }
                }

                // Live Phone Notification Shade Preview Card
                Text("Live Android Notification Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VybeBrandCyan)
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = Color(0xFF14141D),
                  border = BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f)),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Box(
                        modifier = Modifier
                          .size(38.dp)
                          .clip(CircleShape)
                          .background(Brush.linearGradient(listOf(VybeViolet, VybeMagenta))),
                        contentAlignment = Alignment.Center
                      ) {
                        Text("V", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                      }
                      Spacer(modifier = Modifier.width(10.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                          Text(
                            text = pushTitle.ifBlank { "VYBE Official" },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                          )
                          Spacer(modifier = Modifier.width(6.dp))
                          Text("• Official Update • now", color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                        }
                        Text(
                          text = pushBody.ifBlank { "Your real-time message preview will appear here..." },
                          color = Color.White.copy(alpha = 0.85f),
                          fontSize = 12.sp,
                          maxLines = 3,
                          overflow = TextOverflow.Ellipsis
                        )
                      }
                    }
                    if (pushMediaUrl.startsWith("http", ignoreCase = true)) {
                      Spacer(modifier = Modifier.height(10.dp))
                      AsyncImage(
                        model = pushMediaUrl,
                        contentDescription = "Broadcast preview image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(130.dp)
                          .clip(RoundedCornerShape(10.dp))
                      )
                    }
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Button(
                    onClick = {
                      isDispatchingPush = true
                      viewModel.sendAdminBroadcastNotification(
                        title = pushTitle,
                        body = pushBody,
                        destination = pushDestination,
                        mediaUrl = pushMediaUrl.takeIf { it.isNotBlank() }
                      ) { ok, msg ->
                        isDispatchingPush = false
                        if (ok) {
                          pushTitle = ""
                          pushBody = ""
                          pushMediaUrl = ""
                        }
                        scope.launch {
                          snackbarHostState.showSnackbar(msg)
                        }
                      }
                    },
                    enabled = !isDispatchingPush && pushTitle.isNotBlank() && pushBody.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("admin_send_broadcast_button")
                  ) {
                    Text(
                      text = if (isDispatchingPush) "Dispatching..." else "Send Live Push",
                      fontWeight = FontWeight.Bold,
                      color = Color.White
                    )
                  }

                  OutlinedButton(
                    onClick = {
                      val shareMsg = buildString {
                        append("⚡ ${pushTitle.ifBlank { "VYBE Official Update" }}\n\n")
                        append(pushBody.ifBlank { "Check out what's new on VYBE!" })
                        if (pushMediaUrl.isNotBlank()) {
                          append("\n\n$pushMediaUrl")
                        }
                      }
                      val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, pushTitle.ifBlank { "VYBE Update" })
                        putExtra(Intent.EXTRA_TEXT, shareMsg)
                      }
                      context.startActivity(Intent.createChooser(intent, "Share Official Update"))
                    },
                    enabled = pushTitle.isNotBlank() || pushBody.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .testTag("admin_share_broadcast_button")
                  ) {
                    Icon(Icons.Outlined.Share, contentDescription = "Share externally", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                  }
                }
              }
            }
          }
        }

        AdminTab.VERIFICATIONS -> {
          item {
            val businessApplications by viewModel.businessApplications.collectAsState()
            Text(
              "Business & Shop Verification Applications (${businessApplications.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          item {
            val businessApplications by viewModel.businessApplications.collectAsState()
            if (businessApplications.isEmpty()) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Text("No pending business applications.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Approve Verification ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                  Text("Age Verification (18+):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("Enforced on Adult Content & DM", fontSize = 12.sp, color = VybeOnlineGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Real-Time Push Engine:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("FCM + Phoenix WS (<100ms)", fontSize = 12.sp, color = VybeOnlineGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                  Text("Target Launch Date:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("January 1, 2027", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
