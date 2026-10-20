package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.VybeShop
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.VybeCyan
import com.example.ui.theme.VybeOnlineGreen
import com.example.ui.theme.VybeVerifiedGold
import com.example.ui.theme.VybeViolet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val shop by viewModel.selectedShop.collectAsState()
  var showReviewDialog by remember { mutableStateOf(false) }
  var reviewRating by remember { mutableStateOf(5) }
  var reviewText by remember { mutableStateOf("") }

  if (shop == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Shop not found")
    }
    return
  }

  val s = shop!!

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
            Text(s.businessName, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.Store, contentDescription = "Verified Shop", tint = VybeVerifiedGold, modifier = Modifier.size(16.dp))
          }
        },
        actions = {
          Button(
            onClick = { showReviewDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(end = 8.dp).testTag("write_review_button")
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
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.businessName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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

          Text(s.description, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)

          Spacer(modifier = Modifier.height(14.dp))

          // Business Details: Hours, Address, Contact
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hours: ${s.openingHours}", fontSize = 12.sp)
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Place, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Location: ${s.locationAddress}", fontSize = 12.sp)
              }
              Spacer(modifier = Modifier.height(6.dp))
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Phone: ${s.contactPhone}", fontSize = 12.sp)
              }
            }
          }
        }
      }

      // Catalog Section
      item {
        Text(
          text = "Curated Catalogue (${s.catalog.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
      }

      items(s.catalog, key = { it.id }) { item ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
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
              Text(item.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
              Spacer(modifier = Modifier.height(4.dp))
              Text(item.priceFormatted, fontWeight = FontWeight.ExtraBold, color = VybeViolet, fontSize = 13.sp)
            }
          }
        }
      }

      // Customer Reviews Section
      item {
        Spacer(modifier = Modifier.height(14.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Customer Reviews (${s.reviews.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          TextButton(onClick = { showReviewDialog = true }) {
            Text("Write a Review", fontWeight = FontWeight.Bold)
          }
        }
      }

      items(s.reviews, key = { it.id }) { review ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                VybeAvatar(user = review.author, size = 32.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(review.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                  Text(review.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }

              Row {
                repeat(review.ratingStars) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(14.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(review.reviewText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)

            if (review.ownerReply != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(10.dp)) {
                  Text("Owner Reply 🏪", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = VybeVerifiedGold)
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(review.ownerReply, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }

  // Write Review Dialog
  if (showReviewDialog) {
    AlertDialog(
      onDismissRequest = { showReviewDialog = false },
      title = { Text("Review ${s.businessName}", fontWeight = FontWeight.Bold) },
      text = {
        Column(modifier = Modifier.fillMaxWidth()) {
          Text("Select Rating (1 to 5 Stars):", fontSize = 12.sp)
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { star ->
              IconButton(
                onClick = { reviewRating = star },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Star,
                  contentDescription = "$star Stars",
                  tint = if (star <= reviewRating) VybeVerifiedGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = reviewText,
            onValueChange = { reviewText = it },
            label = { Text("Your Honest Feedback") },
            placeholder = { Text("How was your experience, service, and product?") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("review_input_field")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (reviewText.isNotBlank()) {
              viewModel.addShopReview(s.id, reviewRating, reviewText)
              showReviewDialog = false
              reviewText = ""
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Submit Review")
        }
      },
      dismissButton = {
        TextButton(onClick = { showReviewDialog = false }) { Text("Cancel") }
      }
    )
  }
}
