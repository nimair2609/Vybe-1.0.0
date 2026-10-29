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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminAuditLog
import com.example.ui.theme.*

@Composable
fun AdminAuditTab(
  auditLogs: List<AdminAuditLog>,
  backendStatus: String,
  storageUsageMb: Float
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedActionFilter by remember { mutableStateOf("ALL") }

  val filteredLogs = remember(auditLogs, searchQuery, selectedActionFilter) {
    auditLogs.filter { log ->
      val matchesSearch = searchQuery.isBlank() ||
        log.action.contains(searchQuery, ignoreCase = true) ||
        log.details.contains(searchQuery, ignoreCase = true) ||
        log.adminEmail.contains(searchQuery, ignoreCase = true) ||
        log.targetId.contains(searchQuery, ignoreCase = true)

      val matchesAction = when (selectedActionFilter) {
        "SECURITY" -> log.action.contains("SUSPEND", true) || log.action.contains("BAN", true)
        "MODERATION" -> log.action.contains("TAKEDOWN", true) || log.action.contains("REPORT", true)
        "CONFIG" -> log.action.contains("SETTINGS", true) || log.action.contains("ROLE", true)
        "ADS" -> log.action.contains("CAMPAIGN", true)
        "API" -> log.action.contains("API", true) || log.action.contains("HEALTH", true)
        else -> true
      }
      matchesSearch && matchesAction
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_audit_tab"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Platform Compliance Status Card (Preserved & Enhanced)
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = VybeBrandBlue, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Platform Compliance & Legal Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        ComplianceRow("Entity Name:", "VYBE by NR Next | NR Group")
        ComplianceRow("Statutory Intermediary Rules:", "Active (24h Grievance Escalation Window)", VybeOnlineGreen)
        ComplianceRow("Age Gate & Minor Protections:", "Enforced (18+ Adult Tagging & Safe DMs)", VybeOnlineGreen)
        ComplianceRow("Zero-Trust RLS Policies:", "Row Level Security Active Across All Supabase Tables", VybeOnlineGreen)
        ComplianceRow("Real-Time Broadcast Engine:", "FCM HTTP v1 + Supabase Realtime Channels", VybeOnlineGreen)
        ComplianceRow("Postgres Storage Quota:", "${String.format("%.1f", storageUsageMb)} MB Active / 500 MB Free Tier Allocation")
      }
    }

    // Web Admin Console Card
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      shape = RoundedCornerShape(16.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Companion Web Admin Console Active", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          "For large-screen operations on desktop and laptop displays, access the VYBE Web Admin Panel with authenticated Owner Admin credentials. All session activity is synchronized in real-time.",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 15.sp
        )
      }
    }

    // Audit Trail Search & Filter
    Text("Cryptographic Audit Trail", fontWeight = FontWeight.Bold, fontSize = 14.sp)

    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search audit logs by admin, action, or target ID...") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotBlank()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Clear, contentDescription = "Clear")
          }
        }
      },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().testTag("admin_audit_search_input")
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf("ALL", "SECURITY", "MODERATION", "CONFIG", "ADS", "API").forEach { filter ->
        FilterChip(
          selected = selectedActionFilter == filter,
          onClick = { selectedActionFilter = filter },
          label = { Text(filter, fontSize = 9.sp) },
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Audit Log List
    if (filteredLogs.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("No audit log records match filter.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      filteredLogs.forEach { log ->
        AuditLogCard(log = log)
      }
    }
  }
}

@Composable
private fun ComplianceRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
  }
}

@Composable
private fun AuditLogCard(log: AdminAuditLog) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    modifier = Modifier.fillMaxWidth().testTag("audit_card_${log.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = VybeBrandBlue.copy(alpha = 0.15f)
        ) {
          Text(
            text = log.action,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = VybeBrandBlue,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }

        Text(log.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }

      Spacer(modifier = Modifier.height(6.dp))
      Text(log.details, fontSize = 12.sp, fontWeight = FontWeight.Medium)

      Spacer(modifier = Modifier.height(6.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text("Admin: ${log.adminEmail}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Target: ${log.targetType}:${log.targetId}", fontSize = 10.sp, color = VybeBrandCyan)
      }
    }
  }
}
