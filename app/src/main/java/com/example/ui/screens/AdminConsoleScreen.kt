package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ReportStatus
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeVerifiedGold
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val reports by viewModel.adminReports.collectAsState()

  Scaffold(
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
            Text("Admin & Trust Safety", fontWeight = FontWeight.Bold)
            Text("Moderation queue, age compliance & report audit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
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
              text = "Indian IT Rules (Intermediary Guidelines) compliance active. Reports are reviewed with audit logging. Private messages remain inaccessible to moderators without lawful judicial warrant.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp
            )
          }
        }
      }

      item {
        Text("Pending Incident & Verification Queue (${reports.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      items(reports, key = { it.id }) { report ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("report_card_${report.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
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
                  Text("Enforce Suspension / Restrict", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

      // Business Verification Queue (Upgrade J)
      item {
        val businessApplications by viewModel.businessApplications.collectAsState()
        Text(
          "Business Verification Applications (${businessApplications.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 10.dp)
        )
      }

      item {
        val businessApplications by viewModel.businessApplications.collectAsState()
        if (businessApplications.isEmpty()) {
          Text("No pending business applications.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            businessApplications.forEach { app ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("business_app_card_${app.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.4f))
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
                        com.example.data.model.BusinessVerificationStatus.APPROVED -> VybeOnlineGreen.copy(alpha = 0.2f)
                        com.example.data.model.BusinessVerificationStatus.PENDING -> VybeVerifiedGold.copy(alpha = 0.2f)
                        else -> VybeDanger.copy(alpha = 0.2f)
                      },
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = app.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (app.status) {
                          com.example.data.model.BusinessVerificationStatus.APPROVED -> VybeOnlineGreen
                          com.example.data.model.BusinessVerificationStatus.PENDING -> VybeVerifiedGold
                          else -> VybeDanger
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }

                  Spacer(modifier = Modifier.height(4.dp))
                  Text("${app.category} • Reg/GST: ${app.registrationNumberOrGst}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  Text("Address: ${app.address} • Contact: ${app.contactEmail}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                  if (app.status == com.example.data.model.BusinessVerificationStatus.PENDING) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.End
                    ) {
                      TextButton(
                        onClick = {
                          viewModel.reviewBusinessApplication(
                            app.id,
                            com.example.data.model.BusinessVerificationStatus.REJECTED,
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
                            com.example.data.model.BusinessVerificationStatus.APPROVED,
                            "Verified against GST and commercial registry."
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

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }
}
