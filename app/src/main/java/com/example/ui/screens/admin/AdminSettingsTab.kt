package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminAppSettings
import com.example.ui.theme.*

@Composable
fun AdminSettingsTab(
  initialSettings: AdminAppSettings,
  onSaveSettings: (AdminAppSettings) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  var appName by remember(initialSettings) { mutableStateOf(initialSettings.appName) }
  var tagline by remember(initialSettings) { mutableStateOf(initialSettings.approvedBrandTagline) }
  var minVersion by remember(initialSettings) { mutableStateOf(initialSettings.minSupportedVersion) }
  var isMaintenanceMode by remember(initialSettings) { mutableStateOf(initialSettings.isMaintenanceMode) }
  var maintenanceMessage by remember(initialSettings) { mutableStateOf(initialSettings.maintenanceMessage) }
  var isAnnouncementActive by remember(initialSettings) { mutableStateOf(initialSettings.isAnnouncementActive) }
  var announcementTitle by remember(initialSettings) { mutableStateOf(initialSettings.activeAnnouncementTitle) }
  var announcementMessage by remember(initialSettings) { mutableStateOf(initialSettings.activeAnnouncementMessage) }

  // Feature Toggles
  var isReelsEnabled by remember(initialSettings) { mutableStateOf(initialSettings.isReelsEnabled) }
  var isStoriesEnabled by remember(initialSettings) { mutableStateOf(initialSettings.isStoriesEnabled) }
  var isUnloadVoiceEnabled by remember(initialSettings) { mutableStateOf(initialSettings.isUnloadVoiceEnabled) }
  var isCrossedPathsEnabled by remember(initialSettings) { mutableStateOf(initialSettings.isCrossedPathsEnabled) }
  var isCommunitiesEnabled by remember(initialSettings) { mutableStateOf(initialSettings.isCommunitiesEnabled) }
  var isSpiritualModeEnabled by remember(initialSettings) { mutableStateOf(initialSettings.isSpiritualModeEnabled) }

  // Registration & Limits
  var registrationMode by remember(initialSettings) { mutableStateOf(initialSettings.registrationMode) }
  var maxVideoSeconds by remember(initialSettings) { mutableStateOf(initialSettings.maxVideoUploadSeconds.toString()) }
  var supportEmail by remember(initialSettings) { mutableStateOf(initialSettings.supportContactEmail) }
  var grievanceEmail by remember(initialSettings) { mutableStateOf(initialSettings.grievanceOfficerEmail) }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_settings_tab"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Branding Card
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("App Identity & Approved Branding", fontWeight = FontWeight.Bold, fontSize = 13.sp)

        OutlinedTextField(
          value = appName,
          onValueChange = { appName = it },
          label = { Text("App Name") },
          modifier = Modifier.fillMaxWidth().testTag("setting_app_name")
        )

        OutlinedTextField(
          value = tagline,
          onValueChange = { tagline = it },
          label = { Text("Approved Brand Tagline") },
          modifier = Modifier.fillMaxWidth().testTag("setting_app_tagline")
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = minVersion,
            onValueChange = { minVersion = it },
            label = { Text("Min Version") },
            modifier = Modifier.weight(1f).testTag("setting_min_version")
          )
          OutlinedTextField(
            value = initialSettings.currentAppVersion,
            onValueChange = {},
            readOnly = true,
            label = { Text("Active Release") },
            modifier = Modifier.weight(1f)
          )
        }
      }
    }

    // Maintenance Mode & Announcement Banner
    Card(
      colors = CardDefaults.cardColors(
        containerColor = if (isMaintenanceMode) VybeDanger.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
      ),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, if (isMaintenanceMode) VybeDanger.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Emergency Maintenance Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Temporarily pause non-admin logins for server maintenance", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(
            checked = isMaintenanceMode,
            onCheckedChange = { isMaintenanceMode = it },
            modifier = Modifier.testTag("setting_toggle_maintenance")
          )
        }

        if (isMaintenanceMode) {
          OutlinedTextField(
            value = maintenanceMessage,
            onValueChange = { maintenanceMessage = it },
            label = { Text("Public Maintenance Message") },
            modifier = Modifier.fillMaxWidth()
          )
        }

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text("Global App Announcement Banner", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Broadcast a sticky notice at top of main feed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(
            checked = isAnnouncementActive,
            onCheckedChange = { isAnnouncementActive = it },
            modifier = Modifier.testTag("setting_toggle_announcement")
          )
        }

        if (isAnnouncementActive) {
          OutlinedTextField(
            value = announcementTitle,
            onValueChange = { announcementTitle = it },
            label = { Text("Announcement Title") },
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = announcementMessage,
            onValueChange = { announcementMessage = it },
            label = { Text("Announcement Body") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }

    // Feature Availability Toggles
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Feature Availability Toggles", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Enable or disable specific features dynamically across the mobile client", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(4.dp))

        FeatureSwitchRow("VYBES Short-Form Reels", isReelsEnabled) { isReelsEnabled = it }
        FeatureSwitchRow("24-Hour Ephemeral Stories", isStoriesEnabled) { isStoriesEnabled = it }
        FeatureSwitchRow("Explore Zone Live Unload Voice", isUnloadVoiceEnabled) { isUnloadVoiceEnabled = it }
        FeatureSwitchRow("Crossed Paths Nearby Encounters", isCrossedPathsEnabled) { isCrossedPathsEnabled = it }
        FeatureSwitchRow("Community Zone & Topic Groups", isCommunitiesEnabled) { isCommunitiesEnabled = it }
        FeatureSwitchRow("Spiritual Mode & Guided Gratitude", isSpiritualModeEnabled) { isSpiritualModeEnabled = it }
      }
    }

    // Registration Mode & Upload Limits
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Registration & Media Limits", fontWeight = FontWeight.Bold, fontSize = 13.sp)

        Text("Registration Mode", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf("OPEN", "VERIFIED_EMAIL_ONLY", "INVITE_ONLY").forEach { mode ->
            FilterChip(
              selected = registrationMode == mode,
              onClick = { registrationMode = mode },
              label = { Text(mode.replace("_", " "), fontSize = 10.sp) },
              modifier = Modifier.weight(1f)
            )
          }
        }

        OutlinedTextField(
          value = maxVideoSeconds,
          onValueChange = { maxVideoSeconds = it.filter { char -> char.isDigit() } },
          label = { Text("Max Video Duration (Seconds)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    // Public Support & Legal Policies
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Public Support & Regulatory Contacts", fontWeight = FontWeight.Bold, fontSize = 13.sp)

        OutlinedTextField(
          value = supportEmail,
          onValueChange = { supportEmail = it },
          label = { Text("Support Contact Email") },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
          value = grievanceEmail,
          onValueChange = { grievanceEmail = it },
          label = { Text("Grievance Officer Email (IT Rules 2021)") },
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    // Save Button
    Button(
      onClick = {
        val updated = initialSettings.copy(
          appName = appName.trim().ifBlank { "VYBE" },
          approvedBrandTagline = tagline.trim(),
          minSupportedVersion = minVersion.trim(),
          isMaintenanceMode = isMaintenanceMode,
          maintenanceMessage = maintenanceMessage.trim(),
          isAnnouncementActive = isAnnouncementActive,
          activeAnnouncementTitle = announcementTitle.trim(),
          activeAnnouncementMessage = announcementMessage.trim(),
          isReelsEnabled = isReelsEnabled,
          isStoriesEnabled = isStoriesEnabled,
          isUnloadVoiceEnabled = isUnloadVoiceEnabled,
          isCrossedPathsEnabled = isCrossedPathsEnabled,
          isCommunitiesEnabled = isCommunitiesEnabled,
          isSpiritualModeEnabled = isSpiritualModeEnabled,
          registrationMode = registrationMode,
          maxVideoUploadSeconds = maxVideoSeconds.toIntOrNull() ?: 60,
          supportContactEmail = supportEmail.trim(),
          grievanceOfficerEmail = grievanceEmail.trim()
        )
        onSaveSettings(updated)
        onShowSnackbar("Platform settings applied successfully! Synced across active sessions.")
      },
      colors = ButtonDefaults.buttonColors(containerColor = VybeBrandBlue),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
        .testTag("save_app_settings_button")
    ) {
      Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(modifier = Modifier.width(8.dp))
      Text("Save & Apply Platform Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
  }
}

@Composable
private fun FeatureSwitchRow(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(label, fontSize = 12.sp)
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
