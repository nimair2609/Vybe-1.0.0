package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notifications.VybeNotificationHelper
import com.example.ui.VybeViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDiagnosticScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val context = LocalContext.current
  val diagnostics by viewModel.deviceNotificationDiagnostics.collectAsState()
  var osNotifEnabled by remember {
    mutableStateOf(VybeNotificationHelper.arePhoneNotificationsEnabled(context))
  }

  val permLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    osNotifEnabled = VybeNotificationHelper.arePhoneNotificationsEnabled(context)
    viewModel.refreshDeviceNotificationDiagnostics()
  }

  LaunchedEffect(Unit) {
    viewModel.refreshDeviceNotificationDiagnostics()
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "Push & Device Diagnostics",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("diag_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshDeviceNotificationDiagnostics() },
            modifier = Modifier.testTag("diag_refresh_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh Diagnostics"
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // 1. Primary Status Header Card
      item {
        val isRegistered = diagnostics.registrationStatus.equals("REGISTERED", ignoreCase = true)
        val isPending = diagnostics.registrationStatus.equals("PENDING", ignoreCase = true) || diagnostics.isSyncing
        val isFailed = diagnostics.registrationStatus.equals("FAILED", ignoreCase = true)

        val bannerColor = when {
          isRegistered -> VybeOnlineGreen
          isPending -> Color(0xFFF59E0B)
          isFailed -> VybeDanger
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        val statusText = when {
          isRegistered -> "Registered in Supabase"
          isPending -> "Registration in Progress"
          isFailed -> "Registration Failed"
          else -> "Ready for Sync"
        }

        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier.fillMaxWidth().testTag("diag_status_card")
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(bannerColor.copy(alpha = 0.15f))
                  .border(1.dp, bannerColor.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
              ) {
                if (diagnostics.isSyncing) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = bannerColor,
                    strokeWidth = 2.dp
                  )
                } else {
                  Icon(
                    imageVector = when {
                      isRegistered -> Icons.Default.CheckCircle
                      isPending -> Icons.Default.Schedule
                      isFailed -> Icons.Default.Error
                      else -> Icons.Default.Notifications
                    },
                    contentDescription = null,
                    tint = bannerColor,
                    modifier = Modifier.size(24.dp)
                  )
                }
              }

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = statusText,
                  fontWeight = FontWeight.Bold,
                  fontSize = 17.sp,
                  color = bannerColor
                )
                Text(
                  text = if (diagnostics.lastRegistrationTimeMs > 0) {
                    val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault())
                    "Last attempt: ${fmt.format(Date(diagnostics.lastRegistrationTimeMs))}"
                  } else "No registration recorded in this session",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (!diagnostics.lastError.isNullOrBlank()) {
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = VybeDanger.copy(alpha = 0.12f),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = VybeDanger,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = diagnostics.lastError.orEmpty(),
                    fontSize = 12.sp,
                    color = VybeDanger,
                    fontFamily = FontFamily.Monospace
                  )
                }
              }
            }

            Button(
              onClick = { viewModel.retryDeviceTokenRegistration() },
              enabled = !diagnostics.isSyncing,
              shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = VybeViolet
              ),
              modifier = Modifier.fillMaxWidth().testTag("diag_sync_now_button")
            ) {
              if (diagnostics.isSyncing) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = Color.White,
                  strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registering Token...")
              } else {
                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test & Sync Token Now")
              }
            }
          }
        }
      }

      // 2. Firebase & FCM Credentials Card
      item {
        DiagnosticSectionCard(
          title = "Firebase & FCM Client",
          icon = Icons.Default.CloudQueue
        ) {
          DiagnosticRow(
            label = "Firebase App Initialized",
            value = if (diagnostics.firebaseInitialized) "INITIALIZED" else "FAILED",
            isOk = diagnostics.firebaseInitialized
          )
          DiagnosticRow(
            label = "FCM Registration Token",
            value = diagnostics.fcmTokenMasked,
            isOk = diagnostics.fcmTokenAvailable,
            isMonospace = true
          )
          DiagnosticRow(
            label = "Token Cached Locally",
            value = if (diagnostics.tokenSavedLocally) "SAVED IN SESSION" else "NOT SAVED",
            isOk = diagnostics.tokenSavedLocally
          )
          DiagnosticRow(
            label = "Direct-Boot Service",
            value = "VybeFirebaseMessagingService",
            isOk = true
          )
        }
      }

      // 3. Supabase Auth & RLS Card
      item {
        DiagnosticSectionCard(
          title = "Supabase Authentication & RLS",
          icon = Icons.Default.Security
        ) {
          DiagnosticRow(
            label = "Authenticated Session",
            value = if (diagnostics.supabaseAuthenticated) "ACTIVE" else "UNAUTHENTICATED",
            isOk = diagnostics.supabaseAuthenticated
          )
          DiagnosticRow(
            label = "Target User UUID",
            value = diagnostics.authenticatedUserIdMasked,
            isOk = diagnostics.supabaseAuthenticated,
            isMonospace = true
          )
          DiagnosticRow(
            label = "RLS Check Policy",
            value = "auth.uid() = user_id",
            isOk = true,
            isMonospace = true
          )
          DiagnosticRow(
            label = "Unique Constraint",
            value = "UNIQUE(user_id, fcm_token)",
            isOk = true,
            isMonospace = true
          )
        }
      }

      // 4. Remote Persistence & Backend Gateway Card
      item {
        DiagnosticSectionCard(
          title = "Database & Gateway Delivery",
          icon = Icons.Default.Storage
        ) {
          DiagnosticRow(
            label = "Target Table",
            value = "public.user_device_tokens",
            isOk = true,
            isMonospace = true
          )
          DiagnosticRow(
            label = "Last HTTP Status",
            value = if (diagnostics.lastRegistrationStatusCode > 0) "HTTP ${diagnostics.lastRegistrationStatusCode}" else "Pending",
            isOk = diagnostics.lastRegistrationStatusCode in 200..204
          )
          DiagnosticRow(
            label = "Platform / Model",
            value = "android (${Build.MANUFACTURER} ${Build.MODEL})",
            isOk = true
          )
          DiagnosticRow(
            label = "Push Dispatch Function",
            value = "push-dispatch (project: vybe-social-app)",
            isOk = true
          )
        }
      }

      // 5. System Notifications Card
      item {
        DiagnosticSectionCard(
          title = "Android System Delivery",
          icon = Icons.Default.NotificationsActive
        ) {
          DiagnosticRow(
            label = "POST_NOTIFICATIONS Permission",
            value = if (osNotifEnabled) "GRANTED" else "DENIED / BLOCKED",
            isOk = osNotifEnabled
          )
          DiagnosticRow(
            label = "High-Priority Notification Channel",
            value = "vybe_activity_high_v2",
            isOk = true,
            isMonospace = true
          )
          DiagnosticRow(
            label = "Urgent Calls Channel",
            value = "vybe_calls_urgent_v2",
            isOk = true,
            isMonospace = true
          )

          Spacer(modifier = Modifier.height(6.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            if (!osNotifEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              Button(
                onClick = { permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                modifier = Modifier.weight(1f)
              ) {
                Text("Grant Permission", fontSize = 12.sp)
              }
            }

            OutlinedButton(
              onClick = { VybeNotificationHelper.openSystemNotificationSettings(context) },
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("System Settings", fontSize = 12.sp)
            }
          }
        }
      }

      // Spacer at bottom
      item {
        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun DiagnosticSectionCard(
  title: String,
  icon: ImageVector,
  content: @Composable ColumnScope.() -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = VybeViolet,
          modifier = Modifier.size(20.dp)
        )
        Text(
          text = title,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

      content()
    }
  }
}

@Composable
private fun DiagnosticRow(
  label: String,
  value: String,
  isOk: Boolean,
  isMonospace: Boolean = false
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(if (isOk) VybeOnlineGreen else VybeDanger)
      )
      Text(
        text = value,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
        color = if (isOk) MaterialTheme.colorScheme.onSurface else VybeDanger
      )
    }
  }
}
