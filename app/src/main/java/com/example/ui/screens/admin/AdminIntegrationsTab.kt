package com.example.ui.screens.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminIntegrationItem
import com.example.data.model.IntegrationHealth
import com.example.ui.theme.*

@Composable
fun AdminIntegrationsTab(
  integrations: List<AdminIntegrationItem>,
  onTestConnection: (String) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  val context = LocalContext.current
  var activeGuideItem by remember { mutableStateOf<AdminIntegrationItem?>(null) }
  var testingId by remember { mutableStateOf<String?>(null) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_integrations_tab"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Security & Architecture Callout Card
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Security, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
          Text("Zero Secret Exposure Architecture", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            "Private API secrets and service-role keys are securely isolated inside backend server environments and BuildConfig. Keys are never exposed over client connections. Masked status below reflects live operational checks.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
          )
        }
      }
    }

    // List of Integrations
    integrations.forEach { item ->
      IntegrationCard(
        item = item,
        isTesting = testingId == item.id,
        onTest = {
          testingId = item.id
          onTestConnection(item.id)
          testingId = null
        },
        onOpenGuide = { activeGuideItem = item },
        onCopyMasked = {
          val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
          cm.setPrimaryClip(ClipData.newPlainText("Masked Identifier", item.maskedSecret))
          onShowSnackbar("Copied masked credential tag: ${item.maskedSecret}")
        }
      )
    }
  }

  // Technical Setup Guide & Secret Rotation Modal
  if (activeGuideItem != null) {
    val guide = activeGuideItem!!
    AlertDialog(
      onDismissRequest = { activeGuideItem = null },
      icon = { Icon(Icons.Default.HelpOutline, contentDescription = null, tint = VybeBrandBlue) },
      title = { Text(guide.name, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Provider: ${guide.provider}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text("Purpose: ${guide.purpose}", fontSize = 12.sp)

          HorizontalDivider()

          Text("Required Configuration Keys:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          guide.requiredFields.forEach { key ->
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(key, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
          }

          HorizontalDivider()

          Text("Safe Secret Configuration & Rotation:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
          Text(guide.setupInstructions, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)

          Text("Documentation: ${guide.documentationUrl}", fontSize = 11.sp, color = VybeBrandCyan)
        }
      },
      confirmButton = {
        Button(onClick = { activeGuideItem = null }) {
          Text("Close Guide")
        }
      }
    )
  }
}

@Composable
private fun IntegrationCard(
  item: AdminIntegrationItem,
  isTesting: Boolean,
  onTest: () -> Unit,
  onOpenGuide: () -> Unit,
  onCopyMasked: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    modifier = Modifier.fillMaxWidth().testTag("integration_card_${item.id}")
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      // Header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text("${item.provider} • ${item.category}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(item.status.colorHex).copy(alpha = 0.15f)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(item.status.colorHex))
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              item.status.label,
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = Color(item.status.colorHex)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))
      Text(item.purpose, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

      Spacer(modifier = Modifier.height(12.dp))

      // Masked Secret Row
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
          .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(item.maskedSecret, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        IconButton(onClick = onCopyMasked, modifier = Modifier.size(24.dp)) {
          Icon(Icons.Default.ContentCopy, contentDescription = "Copy Masked Key", modifier = Modifier.size(14.dp))
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Check: ${item.lastCheckedIso}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = onOpenGuide,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Setup Guide", fontSize = 11.sp)
          }

          Button(
            onClick = onTest,
            colors = ButtonDefaults.buttonColors(containerColor = VybeBrandBlue),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("test_integration_${item.id}")
          ) {
            if (isTesting) {
              CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
            } else {
              Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Ping Check", fontSize = 11.sp)
            }
          }
        }
      }
    }
  }
}
