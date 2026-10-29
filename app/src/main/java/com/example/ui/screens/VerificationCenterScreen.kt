package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * Official VYBE Badges & Verification Center.
 * Handles:
 * - Badge display management (Accept & Display, Hide, Decline)
 * - Three official badge types:
 *   1. VYBE Verified (Blue Tick): Public figures, creators, organizations
 *   2. VYBE VIP / Shop (Gold Badge): VIP members, verified shops
 *   3. VYBE Worker (Green Tick): Authorized VYBE employees & workers
 * - In-app badge verification application submission and real-time status tracking
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationCenterScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val currentUser by viewModel.currentUser.collectAsState()
  val user = currentUser

  var selectedBadgeType by remember { mutableStateOf("VERIFIED") }
  var fullNameInput by remember { mutableStateOf("") }
  var categoryInput by remember { mutableStateOf("") }
  var documentUrlInput by remember { mutableStateOf("") }
  var statementInput by remember { mutableStateOf("") }

  var isSubmitting by remember { mutableStateOf(false) }
  var applicationSubmitted by remember { mutableStateOf(false) }
  var snackbarMessage by remember { mutableStateOf<String?>(null) }

  val hasAnyBadge = user != null && (
    user.isVerified || user.isVip || user.isWorker || user.isVerifiedBusiness
  )

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "Badges & Verification",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.White
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("verification_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    },
    snackbarHost = {
      snackbarMessage?.let { msg ->
        Snackbar(
          modifier = Modifier.padding(16.dp),
          action = {
            TextButton(onClick = { snackbarMessage = null }) {
              Text("Dismiss", color = VybeBrandCyan)
            }
          }
        ) {
          Text(msg)
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
      if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = VybeBrandCyan)
        }
        return@Column
      }

      // Profile Summary Card with Live Badge Preview
      Surface(
        color = Color(0xFF131929),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF263252)),
        modifier = Modifier.fillMaxWidth().testTag("verification_profile_card")
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          VybeAvatar(user = user, size = 52.dp)
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = user.displayName,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
              )
              VerificationBadge(user = user)
            }
            Text(
              text = "@${user.username} • ${user.publicVybeId}",
              fontSize = 12.sp,
              color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            val statusLabel = when (user.badgeDisplayStatus) {
              "HIDDEN" -> "Badge Status: Hidden (Private)"
              "DECLINED" -> "Badge Status: Declined"
              else -> if (hasAnyBadge) "Badge Status: Active & Displayed" else "Badge Status: Standard Account"
            }
            Text(
              text = statusLabel,
              fontSize = 11.sp,
              color = if (hasAnyBadge && user.badgeDisplayStatus == "ACCEPTED") VybeOnlineGreen else Color(0xFFFBBF24),
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      // 1. Badge Display Preferences (if user has an assigned badge)
      if (hasAnyBadge) {
        Surface(
          color = Color(0xFF131929),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, Color(0xFF263252)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Badge Display Preference",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "You have official verification privileges. Control how your badge appears across VYBE.",
              fontSize = 12.sp,
              color = Color.White.copy(alpha = 0.65f),
              lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            listOf(
              Triple("ACCEPTED", "Display on Profile, Feed & Comments", "Your official badge is visible publicly next to your name."),
              Triple("HIDDEN", "Hide Badge", "Your verification remains active on your account, but is hidden from public view."),
              Triple("DECLINED", "Decline Badge", "Choose not to display or accept official verification privileges.")
            ).forEach { (statusVal, title, desc) ->
              val isSelected = user.badgeDisplayStatus == statusVal
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable {
                    viewModel.updateBadgeDisplayStatus(statusVal)
                    snackbarMessage = "Badge display updated: $statusVal"
                  }
                  .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(
                  selected = isSelected,
                  onClick = {
                    viewModel.updateBadgeDisplayStatus(statusVal)
                    snackbarMessage = "Badge display updated: $statusVal"
                  },
                  colors = RadioButtonDefaults.colors(
                    selectedColor = VybeBrandCyan,
                    unselectedColor = Color.White.copy(alpha = 0.4f)
                  )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                  Text(desc, fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
                }
              }
            }
          }
        }
      }

      // 2. The Three Official VYBE Badges Explained
      Text(
        text = "Official VYBE Badge Standards",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
      )

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Blue Tick
        BadgeInfoCard(
          title = "VYBE Verified (Blue Tick)",
          subtitle = "Public Figures, Creators, Artists & Organizations",
          description = "Confirms authentic identity for notable creators, public figures, musical artists, and registered organizations on VYBE.",
          badgeColor = Color(0xFF00B0FF),
          icon = Icons.Default.Check
        )

        // Gold Badge
        BadgeInfoCard(
          title = "VYBE VIP / Shop (Gold Badge)",
          subtitle = "VIP Creators & Registered Commercial Shops",
          description = "Exclusive distinction for VIP community leaders, top contributors, and approved commercial shops on VYBE Commerce.",
          badgeColor = Color(0xFFFFD700),
          icon = Icons.Default.Star
        )

        // Green Tick
        BadgeInfoCard(
          title = "VYBE Worker (Green Tick)",
          subtitle = "Authorized VYBE Staff, Engineers & Support",
          description = "Exclusively reserved for authorized employees and staff of VYBE by NR Next. Protects users against official impersonation.",
          badgeColor = Color(0xFF00E676),
          icon = Icons.Default.Check
        )
      }

      // 3. Application Workflow
      if (!hasAnyBadge) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = "Apply for Verification",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = Color.White
        )

        if (applicationSubmitted) {
          Surface(
            color = Color(0xFF0D251A),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
          ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
              Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(38.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("Application Submitted Successfully", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Your application is now under review by the VYBE Trust & Safety team. You will receive an instant notification when your review completes.",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        } else {
          Surface(
            color = Color(0xFF131929),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF263252)),
            modifier = Modifier.fillMaxWidth().testTag("verification_application_form")
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Text(
                "Select Badge Category",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                listOf(
                  Triple("VERIFIED", "Blue Tick", Color(0xFF00B0FF)),
                  Triple("VIP", "Gold VIP", Color(0xFFFFD700)),
                  Triple("WORKER", "Green Worker", Color(0xFF00E676))
                ).forEach { (typeVal, label, badgeColor) ->
                  val isSelected = selectedBadgeType == typeVal
                  Surface(
                    color = if (isSelected) badgeColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSelected) badgeColor else Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier
                      .weight(1f)
                      .clip(RoundedCornerShape(10.dp))
                      .clickable { selectedBadgeType = typeVal }
                      .testTag("badge_option_$typeVal")
                  ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                      Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) badgeColor else Color.White.copy(alpha = 0.8f)
                      )
                    }
                  }
                }
              }

              OutlinedTextField(
                value = fullNameInput,
                onValueChange = { fullNameInput = it },
                label = { Text("Full Legal or Professional Name") },
                placeholder = { Text("e.g., Alex Morgan") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("verification_full_name_input")
              )

              OutlinedTextField(
                value = categoryInput,
                onValueChange = { categoryInput = it },
                label = { Text("Category / Industry / Job Title") },
                placeholder = { Text("e.g., Electronic Music Producer / Core Engineer") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("verification_category_input")
              )

              OutlinedTextField(
                value = documentUrlInput,
                onValueChange = { documentUrlInput = it },
                label = { Text("Proof / Portfolio / Official Link") },
                placeholder = { Text("https://yourwebsite.com or portfolio link") },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("verification_proof_link_input")
              )

              OutlinedTextField(
                value = statementInput,
                onValueChange = { statementInput = it },
                label = { Text("Statement / Verification Details") },
                placeholder = { Text("Describe why you qualify for this official badge on VYBE...") },
                minLines = 3,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("verification_statement_input")
              )

              Button(
                onClick = {
                  if (fullNameInput.isBlank() || categoryInput.isBlank()) {
                    snackbarMessage = "Please enter your full name and category."
                    return@Button
                  }
                  isSubmitting = true
                  viewModel.submitVerificationRequest(
                    requestType = selectedBadgeType,
                    fullName = fullNameInput.trim(),
                    category = categoryInput.trim(),
                    identityUrl = documentUrlInput.trim().ifBlank { null },
                    statement = statementInput.trim()
                  ) { success, msg ->
                    isSubmitting = false
                    if (success) {
                      applicationSubmitted = true
                      snackbarMessage = "Application submitted for review!"
                    } else {
                      snackbarMessage = msg ?: "Submission failed. Please try again."
                    }
                  }
                },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verification_submit_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color(0xFF7B2CBF)
                )
              ) {
                if (isSubmitting) {
                  CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                  Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Submit Verification Application", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun BadgeInfoCard(
  title: String,
  subtitle: String,
  description: String,
  badgeColor: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector
) {
  Surface(
    color = Color(0xFF131929),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, Color(0xFF263252)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.Top
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(badgeColor.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = badgeColor,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
        Text(subtitle, fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(description, fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f), lineHeight = 15.sp)
      }
    }
  }
}
