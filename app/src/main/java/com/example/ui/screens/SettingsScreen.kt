package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.VybeViewModel
import com.example.ui.theme.VybeDanger
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val currentUser by viewModel.currentUser.collectAsState()
  val isDarkMode by viewModel.isDarkMode.collectAsState()
  var isPrivateAccount by remember { mutableStateOf(false) }
  var showPrivacyModal by remember { mutableStateOf(false) }
  var showDeactivateModal by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("settings_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Text("Settings & Privacy", fontWeight = FontWeight.Bold)
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
      // Preferences
      item {
        Text("Preferences & Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, contentDescription = null, tint = VybeViolet)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Dark Cyber-Luxe Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  Text("High-contrast midnight aesthetic", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              Switch(
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleDarkMode() },
                modifier = Modifier.testTag("dark_mode_switch")
              )
            }
          }
        }
      }

      // Privacy & Social Graph
      item {
        Text("Safety & Visibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = VybeViolet)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Private Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  Text("Only approved followers can view posts", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              Switch(
                checked = isPrivateAccount,
                onCheckedChange = { isPrivateAccount = it }
              )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showPrivacyModal = true },
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Policy, contentDescription = null, tint = VybeViolet)
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text("Age Verification & Safety Policy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Minimum age 16 enforcement details", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }

      // Account Actions
      item {
        Text("Account Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }

      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.logout() }
                .padding(vertical = 6.dp)
                .testTag("logout_button"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Log Out", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { showDeactivateModal = true }
                .padding(vertical = 6.dp)
                .testTag("deactivate_button"),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = VybeDanger)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Deactivate or Delete Account", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = VybeDanger)
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }

  // Safety Policy Modal
  if (showPrivacyModal) {
    AlertDialog(
      onDismissRequest = { showPrivacyModal = false },
      title = { Text("VYBE Safety & Age Policy", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "1. Minimum Age 16 Requirement:\nVYBE is strictly for individuals aged 16 and above.\n\n2. Teen Protection (16-17):\nAccounts belonging to users aged 16–17 receive strict privacy protections: they cannot receive direct message requests from untrusted adult accounts or be surfaced in public nearby discovery.\n\n3. Consent-First Real-World Encounters:\nCrossed Paths never reveals exact GPS coordinates, never enables stranger face-scanning, and always requires mutual acceptance before messaging.",
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showPrivacyModal = false },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Got it")
        }
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
