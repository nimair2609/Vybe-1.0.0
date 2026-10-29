package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminReport
import com.example.data.model.ReportStatus
import com.example.data.model.ReportTargetType
import com.example.ui.theme.*

@Composable
fun AdminModerationTab(
  reports: List<AdminReport>,
  onTakeDown: (String, String, ReportTargetType) -> Unit,
  onDismiss: (String) -> Unit,
  onSuspendAuthor: (String) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  var selectedFilter by remember { mutableStateOf("PENDING") }

  val filteredReports = remember(reports, selectedFilter) {
    when (selectedFilter) {
      "PENDING" -> reports.filter { it.status == ReportStatus.PENDING }
      "ACTION_TAKEN" -> reports.filter { it.status == ReportStatus.ACTION_TAKEN }
      "DISMISSED" -> reports.filter { it.status == ReportStatus.DISMISSED }
      "COPYRIGHT" -> reports.filter { it.reason.contains("Copyright", ignoreCase = true) || it.targetType == ReportTargetType.COPYRIGHT_DMCA }
      else -> reports
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_moderation_tab"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Header Callout
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
          modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(VybeDanger.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(Icons.Default.Shield, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text("Trust, Safety & Moderation Operations", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text("Review flagged posts, reels, user profiles, and copyright infringement complaints under IT Intermediary Guidelines.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    // Filter Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf("PENDING", "ACTION_TAKEN", "DISMISSED", "COPYRIGHT", "ALL").forEach { filter ->
        FilterChip(
          selected = selectedFilter == filter,
          onClick = { selectedFilter = filter },
          label = { Text(filter.replace("_", " "), fontSize = 11.sp) }
        )
      }
    }

    if (filteredReports.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VybeOnlineGreen, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("No reports currently require action in this filter.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      filteredReports.forEach { report ->
        ReportItemCard(
          report = report,
          onTakeDown = {
            onTakeDown(report.id, report.targetId, report.targetType)
            onShowSnackbar("Content taken down and marked ACTION_TAKEN.")
          },
          onDismiss = {
            onDismiss(report.id)
            onShowSnackbar("Report dismissed as non-violative.")
          },
          onSuspendAuthor = {
            onSuspendAuthor(report.reportedUser)
            onShowSnackbar("Author @${report.reportedUser} suspended.")
          }
        )
      }
    }
  }
}

@Composable
private fun ReportItemCard(
  report: AdminReport,
  onTakeDown: () -> Unit,
  onDismiss: () -> Unit,
  onSuspendAuthor: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(
      1.dp,
      if (report.status == ReportStatus.PENDING) VybeDanger.copy(alpha = 0.4f)
      else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    ),
    modifier = Modifier.fillMaxWidth().testTag("report_card_${report.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
          ) {
            Text(
              report.targetType.name,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }

          Spacer(modifier = Modifier.width(8.dp))

          Surface(
            shape = RoundedCornerShape(6.dp),
            color = when (report.severity) {
              "Critical" -> VybeDanger.copy(alpha = 0.2f)
              "High" -> VybeMagenta.copy(alpha = 0.2f)
              else -> VybeBrandBlue.copy(alpha = 0.2f)
            }
          ) {
            Text(
              "Severity: ${report.severity}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = when (report.severity) {
                "Critical" -> VybeDanger
                "High" -> VybeMagenta
                else -> VybeBrandBlue
              },
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Text(report.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(report.reason, fontWeight = FontWeight.Bold, fontSize = 13.sp)
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        report.summaryText,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Reported: @${report.reportedUser}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("By: @${report.reportedByUsername}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Decision Actions
      if (report.status == ReportStatus.PENDING) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier.testTag("dismiss_report_${report.id}")
          ) {
            Text("Dismiss", fontSize = 11.sp)
          }

          Spacer(modifier = Modifier.width(6.dp))

          OutlinedButton(
            onClick = onSuspendAuthor,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.4f)),
            modifier = Modifier.testTag("suspend_author_${report.id}")
          ) {
            Text("Warn/Suspend", fontSize = 11.sp, color = VybeDanger)
          }

          Spacer(modifier = Modifier.width(6.dp))

          Button(
            onClick = onTakeDown,
            colors = ButtonDefaults.buttonColors(containerColor = VybeDanger),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.testTag("takedown_report_${report.id}")
          ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Take Down", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Check, contentDescription = null, tint = VybeOnlineGreen, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Resolved: ${report.status.name}", fontSize = 11.sp, color = VybeOnlineGreen, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
