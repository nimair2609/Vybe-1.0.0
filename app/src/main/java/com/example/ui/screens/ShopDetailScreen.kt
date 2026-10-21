package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.BusinessVerificationStatus
import com.example.data.model.VybeShop
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*

/**
 * VYBE VERIFIED BUSINESS SHOP 2.0
 * - Shop profiles with verified badge, catalog and ratings.
 * - Submit shop verification application.
 * - Application status tracking and activation.
 * - Customer reviews with rating stars, photos, and moderation.
 * - Help-Center route for merchant inquiries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

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
  var appCategory by remember { mutableStateOf("Artisan Coffee & Bakery") }
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

  if (shop == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Shop not found")
    }
    return
  }

  val s = shop!!
  val myApp = businessApplications.firstOrNull { it.ownerId == currentUser?.id }

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("shop_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.businessName, fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Store, contentDescription = "Verified Shop", tint = VybeVerifiedGold, modifier = Modifier.size(16.dp))
          }
        },
        actions = {
          IconButton(
            onClick = { showHelpCenterDialog = true },
            modifier = Modifier.testTag("shop_help_center_button")
          ) {
            Icon(Icons.Default.HelpOutline, contentDescription = "Merchant Help Center")
          }
          Button(
            onClick = { showReviewDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .testTag("write_review_button")
          ) {
            Icon(Icons.Default.RateReview, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Review", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    ) {
      // Cover & Header
      item {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                Text(s.businessName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.CheckCircle, contentDescription = "Verified Business", tint = VybeVerifiedGold, modifier = Modifier.size(18.dp))
              }
              Text(s.category, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
              color = VybeVerifiedGold.copy(alpha = 0.15f),
              shape = RoundedCornerShape(10.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("${s.rating}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(s.description, fontSize = 13.sp, lineHeight = 18.sp)
          Spacer(modifier = Modifier.height(10.dp))

          // Contact details
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(s.locationAddress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(s.openingHours, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }

          // Verification Application Banner & Status
          Spacer(modifier = Modifier.height(16.dp))
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Business Verification Program", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                if (myApp != null) {
                  Surface(
                    color = when (myApp.status) {
                      BusinessVerificationStatus.APPROVED -> VybeOnlineGreen.copy(alpha = 0.2f)
                      BusinessVerificationStatus.PENDING -> VybeVerifiedGold.copy(alpha = 0.2f)
                      else -> VybeDanger.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = myApp.status.name,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold,
                      color = when (myApp.status) {
                        BusinessVerificationStatus.APPROVED -> VybeOnlineGreen
                        BusinessVerificationStatus.PENDING -> VybeVerifiedGold
                        else -> VybeDanger
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
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
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("apply_verification_button")
                ) {
                  Text("Apply for Shop Verification", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              } else if (myApp.status == BusinessVerificationStatus.APPROVED && !myApp.isActivated) {
                Button(
                  onClick = { viewModel.activateBusinessShop(s.id) },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.testTag("activate_shop_button")
                ) {
                  Text("Activate Approved Shop Profile ✓", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      }

      // Catalog Items
      item {
        PaddingValues(horizontal = 16.dp)
        Text("Catalog & Offerings (${s.catalog.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
      }

      items(s.catalog, key = { it.id }) { item ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
          Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(10.dp))
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
              Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text(item.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
              Spacer(modifier = Modifier.height(4.dp))
              Text(item.priceFormatted, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = VybeBrandCyan)
            }
          }
        }
      }

      // Customer Reviews
      item {
        Spacer(modifier = Modifier.height(10.dp))
        Text("Customer Reviews & Photos (${s.reviews.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
      }

      items(s.reviews, key = { it.id }) { r ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                VybeAvatar(user = r.author, size = 28.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(r.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Text(r.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
              Row {
                repeat(r.ratingStars) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(14.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(r.reviewText, fontSize = 12.sp, lineHeight = 16.sp)

            if (r.ownerReply != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(8.dp)) {
                  Text("Owner Reply:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = VybeViolet)
                  Text(r.ownerReply, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                }
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
      title = { Text("Write Customer Review", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Rate your experience:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { star ->
              IconButton(onClick = { reviewRating = star }, modifier = Modifier.size(32.dp)) {
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
            placeholder = { Text("Quality, ambiance, service...") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("review_input_text")
          )

          OutlinedButton(
            onClick = {
              photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (reviewPhotoUri != null) "Photo Attached ✓" else "Attach Store/Product Photo", fontSize = 11.sp)
          }

          Text(
            text = "Notice: Reviews undergo automatic anti-spam moderation. Vulgarity or fraudulent reviews are purged.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
          modifier = Modifier.testTag("submit_review_button")
        ) {
          Text("Submit Review", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Business Verification Application Dialog
  if (showApplicationDialog) {
    AlertDialog(
      onDismissRequest = { showApplicationDialog = false },
      title = { Text("Shop Verification Application", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          item {
            Text("Provide your registered business details for verification by NR Next Trust & Safety:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          item {
            OutlinedTextField(
              value = appBusinessName,
              onValueChange = { appBusinessName = it },
              label = { Text("Business / Brand Name") },
              modifier = Modifier.fillMaxWidth().testTag("app_business_name_input"),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appGst,
              onValueChange = { appGst = it },
              label = { Text("GSTIN or Incorporation Registration") },
              placeholder = { Text("e.g., GSTIN27AABCU9603R1ZM") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appEmail,
              onValueChange = { appEmail = it },
              label = { Text("Official Business Email") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appPhone,
              onValueChange = { appPhone = it },
              label = { Text("Support Phone Number") },
              modifier = Modifier.fillMaxWidth(),
              singleLine = true
            )
          }
          item {
            OutlinedTextField(
              value = appAddress,
              onValueChange = { appAddress = it },
              label = { Text("Physical Commercial Address") },
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
          modifier = Modifier.testTag("submit_app_button")
        ) {
          Text("Submit Application", color = Color.Black, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showApplicationDialog = false }) { Text("Cancel") }
      }
    )
  }

  // Help Center Dialog
  if (showHelpCenterDialog) {
    AlertDialog(
      onDismissRequest = { showHelpCenterDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.HelpOutline, contentDescription = null, tint = VybeBrandCyan)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Merchant Help Center", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Dedicated guidance for verified businesses & sellers on VYBE:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text("1. Verification Processing: Applications take 24–48 hours for GST and location validation.", fontSize = 11.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("2. Payment Settlement: In-app buyer settlements are routed directly to your connected bank account.", fontSize = 11.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("3. Dispute Support: Contact merchant-help@vybe.in for mediation or review appeals.", fontSize = 11.sp)
            }
          }
        }
      },
      confirmButton = {
        Button(onClick = { showHelpCenterDialog = false }) { Text("Got it") }
      }
    )
  }

  if (appSubmittedSuccess) {
    AlertDialog(
      onDismissRequest = { appSubmittedSuccess = false },
      title = { Text("Application Submitted", fontWeight = FontWeight.Bold) },
      text = {
        Text("Your business verification request has been submitted to the NR Next Trust & Safety queue. An email confirmation has been queued for dispatch when email infrastructure is connected.", fontSize = 13.sp)
      },
      confirmButton = {
        Button(onClick = { appSubmittedSuccess = false }) { Text("Done") }
      }
    )
  }
}
