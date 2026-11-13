package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BusinessVerificationStatus
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

/**
 * VYBE VERIFIED BUSINESS SHOP 2.0
 * - Shop profiles with verified badge, catalog and ratings.
 * - Submit shop verification application.
 * - Application status tracking and activation.
 * - Customer reviews with rating stars, photos, and moderation.
 * - Help-Center route for merchant inquiries.
 */
@Composable
fun ShopDetailScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val shop by viewModel.selectedShop.collectAsState()
  val businessApplications by viewModel.businessApplications.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var showReviewDialog by remember { mutableStateOf(false) }
  var reviewRating by remember { mutableIntStateOf(5) }
  var reviewText by remember { mutableStateOf("") }
  var reviewPhotoUri by remember { mutableStateOf<Uri?>(null) }

  var showApplicationDialog by remember { mutableStateOf(false) }
  var showHelpCenterDialog by remember { mutableStateOf(false) }

  var appBusinessName by remember { mutableStateOf("") }
  val appCategory by remember { mutableStateOf("Artisan Coffee & Bakery") }
  var appGst by remember { mutableStateOf("") }
  var appEmail by remember { mutableStateOf("") }
  var appPhone by remember { mutableStateOf("") }
  var appAddress by remember { mutableStateOf("") }
  var appSubmittedSuccess by remember { mutableStateOf(false) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    reviewPhotoUri = uri
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  if (shop == null) {
    Scaffold(
      containerColor = palette.canvas,
      contentWindowInsets = WindowInsets(0.dp),
      topBar = {
        VybeSubScreenTopBar(
          title = "Verified Shop",
          onBackClick = { viewModel.navigateBack() },
          backTestTag = "shop_back_button"
        )
      }
    ) { paddingValues ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        VybeEmptyState(
          icon = Icons.Default.Store,
          title = "Shop Not Found",
          subtitle = "This verified storefront is currently unavailable.",
          actionLabel = "Go Back",
          onAction = { viewModel.navigateBack() }
        )
      }
    }
    return
  }

  val s = shop!!
  val myApp = businessApplications.firstOrNull { it.ownerId == currentUser?.id }

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = s.businessName,
        subtitle = "Verified Storefront • ${s.category}",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "shop_back_button",
        actions = {
          IconButton(
            onClick = { showHelpCenterDialog = true },
            modifier = Modifier.size(48.dp).testTag("shop_help_center_button")
          ) {
            Icon(Icons.Default.HelpOutline, contentDescription = "Merchant Help Center", tint = palette.textPrimary)
          }
          Button(
            onClick = { showReviewDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .heightIn(min = 40.dp)
              .testTag("write_review_button")
          ) {
            Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Review", fontFamily = PlusJakartaSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
        }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding(),
      contentPadding = PaddingValues(bottom = 32.dp)
    ) {
      // Cover & Header
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(palette.elevated)
        ) {
          AsyncImage(
            model = s.coverUrl,
            contentDescription = s.businessName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
          )
        }

        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = s.businessName,
                  fontFamily = PlusJakartaSansFontFamily,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = palette.textPrimary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = "Verified Business", tint = VybeVerifiedGold, modifier = Modifier.size(18.dp))
              }
              Text(s.category, fontSize = 13.sp, color = palette.textSecondary)
            }

            Surface(
              color = VybeVerifiedGold.copy(alpha = 0.16f),
              shape = RoundedCornerShape(VybeTokens.Radius.sm),
              border = BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.35f))
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${s.rating}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(s.description, fontSize = 13.sp, lineHeight = 19.sp, color = palette.textPrimary)
          Spacer(modifier = Modifier.height(12.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(s.locationAddress, fontSize = 12.sp, color = palette.textSecondary)
          }
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(s.openingHours, fontSize = 12.sp, color = palette.textSecondary)
          }

          // Verification Application Banner & Status
          Spacer(modifier = Modifier.height(16.dp))
          VybeSurfaceCard(
            containerColor = palette.elevated,
            borderColor = VybeVerifiedGold.copy(alpha = 0.35f),
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Business Verification Program", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = palette.textPrimary)
              }

              if (myApp != null) {
                Surface(
                  color = when (myApp.status) {
                    BusinessVerificationStatus.APPROVED -> palette.success.copy(alpha = 0.2f)
                    BusinessVerificationStatus.PENDING -> VybeVerifiedGold.copy(alpha = 0.2f)
                    else -> palette.danger.copy(alpha = 0.2f)
                  },
                  shape = RoundedCornerShape(VybeTokens.Radius.xs)
                ) {
                  Text(
                    text = myApp.status.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (myApp.status) {
                      BusinessVerificationStatus.APPROVED -> palette.success
                      BusinessVerificationStatus.PENDING -> VybeVerifiedGold
                      else -> palette.danger
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = if (myApp != null) {
                "Your application for \"${myApp.businessName}\" is currently ${myApp.status.name.lowercase().replace("_", " ")}. ${myApp.reviewerNotes ?: ""}"
              } else {
                "Are you an independent merchant or brand? Submit your registration credentials for verification, gold checkmark, and direct client catalog display."
              },
              fontSize = 11.sp,
              color = palette.textSecondary,
              lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            if (myApp == null) {
              Button(
                onClick = {
                  appBusinessName = ""
                  appGst = ""
                  appEmail = currentUser?.email ?: ""
                  appPhone = currentUser?.phone ?: ""
                  appAddress = ""
                  showApplicationDialog = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = VybeVerifiedGold),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp).testTag("apply_verification_button")
              ) {
                Text("Apply for Shop Verification", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            } else if (myApp.status == BusinessVerificationStatus.APPROVED && !myApp.isActivated) {
              Button(
                onClick = { viewModel.activateBusinessShop(s.id) },
                colors = ButtonDefaults.buttonColors(containerColor = palette.success),
                shape = RoundedCornerShape(VybeTokens.Radius.sm),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.heightIn(min = 44.dp).testTag("activate_shop_button")
              ) {
                Text("Activate Approved Shop Profile ✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Catalog Items
      item {
        Text(
          text = "Catalog & Offerings (${s.catalog.size})",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = palette.textPrimary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
      }

      items(s.catalog, key = { it.id }) { item ->
        VybeSurfaceCard(
          contentPadding = PaddingValues(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(palette.elevated)
            ) {
              AsyncImage(
                model = item.imageUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(item.name, fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(item.description, fontSize = 12.sp, color = palette.textSecondary, maxLines = 2)
              Spacer(modifier = Modifier.height(4.dp))
              Text(item.priceFormatted, fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = palette.accentSecondary)
            }
          }
        }
      }

      // Customer Reviews
      item {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Customer Reviews & Photos (${s.reviews.size})",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = palette.textPrimary,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
      }

      items(s.reviews, key = { it.id }) { r ->
        VybeSurfaceCard(
          contentPadding = PaddingValues(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              VybeAvatar(user = r.author, size = 30.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(r.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = palette.textPrimary)
                Text(r.timestamp, fontSize = 10.sp, color = palette.textMuted)
              }
            }
            Row {
              repeat(r.ratingStars) {
                Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(14.dp))
              }
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text(r.reviewText, fontSize = 12.sp, lineHeight = 17.sp, color = palette.textPrimary)

          if (r.ownerReply != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
              color = palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.xs),
              border = BorderStroke(1.dp, palette.border),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Text("Owner Reply:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = palette.accent)
                Spacer(modifier = Modifier.height(2.dp))
                Text(r.ownerReply, fontSize = 11.sp, color = palette.textPrimary)
              }
            }
          }
        }
      }
    }
  }

  // Write Review Dialog
  if (showReviewDialog) {
    AlertDialog(
      onDismissRequest = { showReviewDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Write Customer Review", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Rate your experience:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { star ->
              IconButton(onClick = { reviewRating = star }, modifier = Modifier.size(36.dp)) {
                Icon(
                  imageVector = if (star <= reviewRating) Icons.Default.Star else Icons.Default.StarBorder,
                  contentDescription = "$star stars",
                  tint = VybeVerifiedGold
                )
              }
            }
          }

          OutlinedTextField(
            value = reviewText,
            onValueChange = { reviewText = it },
            label = { Text("Your Honest Feedback") },
            placeholder = { Text("Quality, ambiance, service...", color = palette.textMuted) },
            colors = fieldColors,
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("review_input_text")
          )

          VybeSecondaryButton(
            text = if (reviewPhotoUri != null) "Photo Attached ✓" else "Attach Store/Product Photo",
            onClick = {
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            icon = Icons.Default.AddPhotoAlternate,
            modifier = Modifier.fillMaxWidth()
          )

          Text(
            text = "Notice: Reviews undergo automatic anti-spam moderation. Vulgarity or fraudulent reviews are purged.",
            fontSize = 10.sp,
            color = palette.textMuted
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (reviewText.isNotBlank()) {
              viewModel.addShopReview(s.id, reviewRating, reviewText.trim())
              reviewText = ""
              reviewPhotoUri = null
              showReviewDialog = false
            }
          },
          enabled = reviewText.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
          shape = RoundedCornerShape(VybeTokens.Radius.sm),
          modifier = Modifier.testTag("submit_review_button")
        ) {
          Text("Submit Review", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReviewDialog = false }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // Business Verification Application Dialog
  if (showApplicationDialog) {
    AlertDialog(
      onDismissRequest = { showApplicationDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Shop Verification Application", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text("Provide your registered business details for verification by NR Next Trust & Safety:", fontSize = 12.sp, color = palette.textSecondary)
          }
          item {
            OutlinedTextField(
              value = appBusinessName,
              onValueChange = { appBusinessName = it },
              label = { Text("Business / Brand Name") },
              colors = fieldColors,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth().testTag("app_business_name_input"),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appGst,
              onValueChange = { appGst = it },
              label = { Text("GSTIN or Incorporation Registration") },
              placeholder = { Text("e.g., GSTIN27AABCU9603R1ZM", color = palette.textMuted) },
              colors = fieldColors,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appEmail,
              onValueChange = { appEmail = it },
              label = { Text("Official Business Email") },
              colors = fieldColors,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appPhone,
              onValueChange = { appPhone = it },
              label = { Text("Support Phone Number") },
              colors = fieldColors,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appAddress,
              onValueChange = { appAddress = it },
              label = { Text("Physical Commercial Address") },
              colors = fieldColors,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth(),
              maxLines = 2
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (appBusinessName.isNotBlank() && appEmail.isNotBlank()) {
              viewModel.submitBusinessApplication(
                name = appBusinessName.trim(),
                cat = appCategory,
                gst = appGst.trim(),
                email = appEmail.trim(),
                phone = appPhone.trim(),
                addr = appAddress.trim()
              )
              showApplicationDialog = false
              appSubmittedSuccess = true
            }
          },
          enabled = appBusinessName.isNotBlank() && appEmail.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = VybeVerifiedGold),
          shape = RoundedCornerShape(VybeTokens.Radius.sm),
          modifier = Modifier.testTag("submit_app_button")
        ) {
          Text("Submit Application", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showApplicationDialog = false }) { Text("Cancel", color = palette.textSecondary) }
      }
    )
  }

  // Help Center Dialog
  if (showHelpCenterDialog) {
    AlertDialog(
      onDismissRequest = { showHelpCenterDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.HelpOutline, contentDescription = null, tint = palette.accentSecondary)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Merchant Help Center", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Dedicated guidance for verified businesses & sellers on VYBE:", fontSize = 12.sp, color = palette.textSecondary)
          VybeSurfaceCard(
            containerColor = palette.elevated,
            cornerRadius = VybeTokens.Radius.sm,
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("1. Verification Processing: Applications take 24–48 hours for GST and location validation.", fontSize = 11.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("2. Payment Settlement: In-app buyer settlements are routed directly to your connected bank account.", fontSize = 11.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("3. Dispute Support: Contact merchant-help@vybe.in for mediation or review appeals.", fontSize = 11.sp, color = palette.textPrimary)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showHelpCenterDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
        ) {
          Text("Got it")
        }
      }
    )
  }

  if (appSubmittedSuccess) {
    AlertDialog(
      onDismissRequest = { appSubmittedSuccess = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      title = { Text("Application Submitted", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
      text = {
        Text("Your business verification request has been submitted to the NR Next Trust & Safety queue. An email confirmation has been queued for dispatch when email infrastructure is connected.", fontSize = 13.sp)
      },
      confirmButton = {
        Button(
          onClick = { appSubmittedSuccess = false },
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
        ) {
          Text("Done")
        }
      }
    )
  }
}
