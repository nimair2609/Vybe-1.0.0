package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BadgeVerificationRequest
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * Official VYBE Badges & Verification Center.
 * Supports:
 * 1. Three official badge families (Verified Blue, VIP/Shop Gold with VIP expiry awareness, Worker Green).
 * 2. User acceptance workflow (Accept & Display vs. Decline for newly approved badges).
 * 3. Public visibility toggle (Show vs. Hide without losing approval, honoring mandatory Worker policy).
 * 4. Badge application submission & live status tracking (Not Applied, Pending, Approved, Rejected, More Info Required, Expired).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationCenterScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val currentUser by viewModel.currentUser.collectAsState()
  val allRequests by viewModel.verificationRequests.collectAsState()
  val user = currentUser

  LaunchedEffect(user?.id) {
    if (user != null) {
      viewModel.refreshVerificationRequests()
    }
  }

  var selectedBadgeType by remember { mutableStateOf("VERIFIED") }
  var fullNameInput by remember { mutableStateOf("") }
  var categoryInput by remember { mutableStateOf("") }
  var documentUrlInput by remember { mutableStateOf("") }
  var statementInput by remember { mutableStateOf("") }

  var isSubmitting by remember { mutableStateOf(false) }
  var isRespondingOffer by remember { mutableStateOf(false) }
  var snackbarMessage by remember { mutableStateOf<String?>(null) }

  val myRequests = remember(allRequests, user?.id) {
    if (user == null) emptyList() else allRequests.filter { it.userId == user.id }
  }

  val selectedCategoryRequest = remember(myRequests, selectedBadgeType) {
    myRequests.firstOrNull { it.requestType.equals(selectedBadgeType, ignoreCase = true) }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            "Badges & Verification",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
          )
        },
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("verification_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back"
            )
          }
        },
        actions = {
          IconButton(
            onClick = { viewModel.refreshVerificationRequests() },
            modifier = Modifier.testTag("verification_refresh_button")
          ) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh status")
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
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = VybeBrandCyan)
        }
        return@Column
      }

      val hasApprovedActiveBadge = user.hasAnyApprovedBadge
      val overallStatusSummary = when {
        user.isVip && user.isVipExpired && !user.isVerified && !user.isVerifiedBusiness && !user.isWorker ->
          "Status: VIP Badge Expired"
        user.isPendingBadgeAcceptance && hasApprovedActiveBadge ->
          "Status: Approved — Awaiting Your Acceptance"
        hasApprovedActiveBadge && user.canDisplayBadgesPublicly ->
          "Status: Active & Displayed Publicly"
        hasApprovedActiveBadge && user.badgeDisplayStatus == "HIDDEN" ->
          "Status: Approved (Hidden from Public)"
        hasApprovedActiveBadge && user.badgeDisplayStatus == "DECLINED" ->
          "Status: Declined"
        myRequests.any { it.status == "PENDING" } ->
          "Status: Application Pending Review"
        myRequests.any { it.status == "MORE_INFO_REQUIRED" } ->
          "Status: More Information Requested"
        myRequests.any { it.status == "REJECTED" } ->
          "Status: Previous Application Not Approved"
        else ->
          "Status: Not Applied"
      }

      // Profile Summary Card with Live Public Badge Preview
      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("verification_profile_card")
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          VybeAvatar(user = user, size = 54.dp)
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = user.displayName.ifBlank { user.username },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              VerificationBadge(user = user, enableExplanationDialog = true)
            }
            Text(
              text = "@${user.username} • ${user.publicVybeId}",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = overallStatusSummary,
              fontSize = 11.sp,
              color = if (hasApprovedActiveBadge && user.canDisplayBadgesPublicly) VybeOnlineGreen else Color(0xFFD97706),
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.testTag("verification_overall_status_text")
            )
          }
        }
      }

      // Expired VIP Notice
      if (user.isVip && user.isVipExpired) {
        Surface(
          color = VybeDanger.copy(alpha = 0.1f),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.45f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("vip_expired_notice_card")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.History, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("VYBE VIP Gold Status: Expired", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = VybeDanger)
              Text(
                text = "Your VIP recognition expired${user.vipExpiresAtIso?.take(10)?.let { " on $it" } ?: ""}. Expired VIP badges are automatically hidden from public view. You may submit a renewal request below.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
        }
      }

      // 1. Newly Approved Badge Acceptance Card (Accept & Display vs Decline)
      if (user.isPendingBadgeAcceptance && hasApprovedActiveBadge) {
        val approvedLabels = buildList {
          if (user.isVerified || user.isVerifiedPublicFigure) add("VYBE Verified (Blue Tick)")
          if (user.hasActiveVip) add("VYBE VIP (Gold Star)")
          if (user.isVerifiedBusiness) add("Verified Business / Shop (Gold Badge)")
          if (user.isWorker) add("VYBE Worker (Green Tick)")
        }
        Surface(
          color = Color(0xFF2563EB).copy(alpha = 0.1f),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.5.dp, Color(0xFF2563EB).copy(alpha = 0.65f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_badge_offer_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(22.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Official Badge Approved!",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Approved badge(s): ${approvedLabels.joinToString(", ")}. Accept to display your badge across your profile, posts, Reels, and comments, or decline if you prefer not to activate it.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 17.sp
            )
            if (user.hasActiveVip && !user.vipExpiresAtIso.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "VIP valid until: ${user.vipExpiresAtIso.take(10)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD97706)
              )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Button(
                onClick = {
                  isRespondingOffer = true
                  viewModel.respondToBadgeOffer("ACCEPT") { ok, msg ->
                    isRespondingOffer = false
                    snackbarMessage = msg ?: if (ok) "Badge accepted and displayed!" else "Could not accept badge."
                  }
                },
                enabled = !isRespondingOffer,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("accept_badge_offer_button")
              ) {
                Text("Accept & Display", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
              }

              OutlinedButton(
                onClick = {
                  isRespondingOffer = true
                  viewModel.respondToBadgeOffer("DECLINE") { ok, msg ->
                    isRespondingOffer = false
                    snackbarMessage = msg ?: if (ok) "Badge offer declined." else "Could not decline badge."
                  }
                },
                enabled = !isRespondingOffer && !(user.isWorker && user.isWorkerBadgeMandatory),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .testTag("decline_badge_offer_button")
              ) {
                Text("Decline", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              }
            }
          }
        }
      }

      // 2. Public Visibility Controls (Show / Hide without losing approval)
      if (hasApprovedActiveBadge && !user.isPendingBadgeAcceptance) {
        val isMandatoryWorker = user.isWorker && user.isWorkerBadgeMandatory
        val isCurrentlyVisible = user.badgeDisplayStatus == "ACCEPTED" || isMandatoryWorker

        Surface(
          color = MaterialTheme.colorScheme.surface,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("badge_visibility_card")
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                  text = "Show Badge Publicly",
                  fontSize = 15.sp,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Hide or show your approved badge at any time without losing verification approval.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 16.sp
                )
              }
              Switch(
                checked = isCurrentlyVisible,
                enabled = !isMandatoryWorker && !isRespondingOffer,
                onCheckedChange = { shouldShow ->
                  isRespondingOffer = true
                  viewModel.respondToBadgeOffer(if (shouldShow) "SHOW" else "HIDE") { _, msg ->
                    isRespondingOffer = false
                    snackbarMessage = msg
                  }
                },
                modifier = Modifier.testTag("badge_visibility_switch")
              )
            }

            if (user.hasActiveVip && !user.vipExpiresAtIso.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "VIP active until ${user.vipExpiresAtIso.take(10)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD97706)
              )
            }

            if (isMandatoryWorker) {
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                color = Color(0xFF059669).copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("mandatory_worker_badge_notice")
              ) {
                Text(
                  text = "Organizational Policy: Authorized VYBE Worker badges remain publicly visible while staff authorization is active.",
                  fontSize = 11.sp,
                  color = Color(0xFF059669),
                  fontWeight = FontWeight.Medium,
                  modifier = Modifier.padding(10.dp)
                )
              }
            } else if (user.badgeDisplayStatus != "DECLINED") {
              Spacer(modifier = Modifier.height(8.dp))
              TextButton(
                onClick = {
                  isRespondingOffer = true
                  viewModel.respondToBadgeOffer("DECLINE") { _, msg ->
                    isRespondingOffer = false
                    snackbarMessage = msg
                  }
                },
                modifier = Modifier.testTag("decline_active_badge_button")
              ) {
                Text("Decline Badge Privileges", color = VybeDanger, fontSize = 12.sp)
              }
            }
          }
        }
      }

      // 3. My Applications & Review Status History
      if (myRequests.isNotEmpty()) {
        Text(
          text = "My Applications (${myRequests.size})",
          fontSize = 15.sp,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          myRequests.forEach { req ->
            ApplicationStatusRow(req = req)
          }
        }
      }

      // 4. Official VYBE Badge Standards
      Text(
        text = "Official VYBE Badge Standards",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BadgeInfoCard(
          title = "VYBE Verified (Blue Tick)",
          subtitle = "Authentic Public Profiles, Creators & Public Figures",
          description = "Confirms authentic identity after manual admin review. Never granted automatically by follower count.",
          badgeColor = Color(0xFF2563EB),
          icon = Icons.Default.Check
        )

        BadgeInfoCard(
          title = "VYBE VIP / Shop (Gold Badge)",
          subtitle = "Approved VIP Members (Star) & Verified Businesses (Storefront)",
          description = "Gold Star recognizes approved VYBE VIP members (active while unexpired). Gold Storefront identifies verified commercial businesses and shops.",
          badgeColor = Color(0xFFD97706),
          icon = Icons.Default.Star
        )

        BadgeInfoCard(
          title = "VYBE Worker (Green Tick)",
          subtitle = "Authorized VYBE Employees & Trust/Safety Staff",
          description = "Reserved strictly for verified VYBE staff and workers. Automatically revocable when staff authorization ends.",
          badgeColor = Color(0xFF059669),
          icon = Icons.Default.Check
        )
      }

      // 5. Submit Verification Application Form
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Apply for an Official Badge",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )

      Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("verification_application_form")
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          Text(
            "Select Badge Category",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            listOf(
              Triple("VERIFIED", "Verified", Color(0xFF2563EB)),
              Triple("VIP", "VIP Gold", Color(0xFFD97706)),
              Triple("SHOP", "Shop Gold", Color(0xFFD97706)),
              Triple("WORKER", "Worker", Color(0xFF059669))
            ).forEach { (typeVal, label, badgeColor) ->
              val isSelected = selectedBadgeType == typeVal
              Surface(
                color = if (isSelected) badgeColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isSelected) badgeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 44.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { selectedBadgeType = typeVal }
                  .testTag("badge_option_$typeVal")
              ) {
                Box(modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                  Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                  )
                }
              }
            }
          }

          val hasPendingForCategory = selectedCategoryRequest?.status == "PENDING"
          if (hasPendingForCategory) {
            Surface(
              color = Color(0xFFD97706).copy(alpha = 0.12f),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "You already have a Pending application for $selectedBadgeType. Our Trust & Safety team will notify you once reviewed.",
                fontSize = 12.sp,
                color = Color(0xFFD97706),
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          val nameLabel = when (selectedBadgeType) {
            "SHOP" -> "Registered Business / Shop Name"
            "WORKER" -> "Employee Full Name"
            else -> "Full Legal or Public Name"
          }
          val categoryLabel = when (selectedBadgeType) {
            "SHOP" -> "Business Category & Registration / GST Ref"
            "WORKER" -> "VYBE Department / Role Title"
            "VIP" -> "Community / Creator Domain"
            else -> "Category / Industry / Public Role"
          }
          val proofLabel = when (selectedBadgeType) {
            "SHOP" -> "Official Business Website or Registry Link"
            "WORKER" -> "Internal Staff Reference / Official Email Domain"
            else -> "Supporting Link (Official Website / Press / Portfolio)"
          }

          OutlinedTextField(
            value = fullNameInput,
            onValueChange = { fullNameInput = it },
            label = { Text(nameLabel) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("verification_full_name_input")
          )

          OutlinedTextField(
            value = categoryInput,
            onValueChange = { categoryInput = it },
            label = { Text(categoryLabel) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("verification_category_input")
          )

          OutlinedTextField(
            value = documentUrlInput,
            onValueChange = { documentUrlInput = it },
            label = { Text(proofLabel) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("verification_proof_link_input")
          )

          OutlinedTextField(
            value = statementInput,
            onValueChange = { statementInput = it },
            label = { Text("Verification Statement") },
            placeholder = { Text("Share brief context to help reviewers verify this request...") },
            minLines = 3,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("verification_statement_input")
          )

          Button(
            onClick = {
              if (fullNameInput.isBlank() || categoryInput.isBlank()) {
                snackbarMessage = "Please fill in the required name and category fields."
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
                  fullNameInput = ""
                  categoryInput = ""
                  documentUrlInput = ""
                  statementInput = ""
                  snackbarMessage = "Application submitted! Track status under My Applications."
                } else {
                  snackbarMessage = msg ?: "Submission failed. Please try again."
                }
              }
            },
            enabled = !isSubmitting && !hasPendingForCategory,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("verification_submit_button"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color(0xFF2563EB)
            )
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
              Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (selectedCategoryRequest?.status == "MORE_INFO_REQUIRED") "Submit Updated Info" else "Submit Badge Application",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ApplicationStatusRow(req: BadgeVerificationRequest) {
  val (statusLabel, statusColor) = when (req.status.uppercase()) {
    "APPROVED" -> Pair("Approved", VybeOnlineGreen)
    "REJECTED" -> Pair("Rejected", VybeDanger)
    "MORE_INFO_REQUIRED" -> Pair("More Info Required", Color(0xFF2563EB))
    "EXPIRED" -> Pair("Expired", VybeDanger)
    else -> Pair("Pending", Color(0xFFD97706))
  }

  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("my_verification_req_${req.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "${req.requestType.uppercase()} Badge • ${req.fullName}",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
          color = statusColor.copy(alpha = 0.16f),
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = statusLabel,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
      if (req.category.isNotBlank()) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
          text = req.category,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      if (!req.reviewerNotes.isNullOrBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Reviewer note: ${req.reviewerNotes}",
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = statusColor
        )
      }
    }
  }
}

@Composable
private fun BadgeInfoCard(
  title: String,
  subtitle: String,
  description: String,
  badgeColor: Color,
  icon: ImageVector
) {
  Surface(
    color = MaterialTheme.colorScheme.surface,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
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
          .background(badgeColor.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = badgeColor,
          modifier = Modifier.size(18.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(2.dp))
        Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
      }
    }
  }
}
