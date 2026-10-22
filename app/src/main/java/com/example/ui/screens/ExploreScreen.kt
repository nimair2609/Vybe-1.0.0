package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.SocialPrediction
import com.example.data.model.VybeShop
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.VerificationBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(viewModel: VybeViewModel) {
  val searchQuery by viewModel.searchQuery.collectAsState()
  val predictions by viewModel.predictions.collectAsState()
  val shops by viewModel.shops.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  val trendingTags = listOf("MumbaiLoFi", "CricketIndia", "DesignEthics", "MonsoonCoffee", "BangaloreTech", "IndieCinema")

  val isSearching = searchQuery.isNotBlank()
  val cleanQuery = remember(searchQuery) { searchQuery.trim().lowercase().replace("@", "") }

  val matchedUsers = remember(cleanQuery, allUsers) {
    if (cleanQuery.isBlank()) emptyList() else {
      allUsers.filter {
        it.publicVybeId.lowercase().contains(cleanQuery) ||
        it.username.lowercase().contains(cleanQuery) ||
        it.displayName.lowercase().contains(cleanQuery)
      }
    }
  }

  val matchedShops = remember(cleanQuery, shops) {
    if (cleanQuery.isBlank()) emptyList() else {
      shops.filter {
        it.businessName.lowercase().contains(cleanQuery) ||
        it.category.lowercase().contains(cleanQuery)
      }
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholder = { Text("Search VYBE, creators, shops, #tags...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(50.dp)
              .testTag("explore_search_bar")
          )
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
      if (isSearching) {
        item {
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Search Results for \"$searchQuery\"",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
        }

        if (matchedUsers.isEmpty() && matchedShops.isEmpty()) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              shape = RoundedCornerShape(16.dp)
            ) {
              Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("No matching profiles or shops", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Search by username (@username) or public VYBE ID (e.g. VYBE-7K4M9P2Q).",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
              }
            }
          }
        } else {
          if (matchedUsers.isNotEmpty()) {
            item {
              Text(
                text = "People & Creators (${matchedUsers.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeViolet,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }

            items(matchedUsers, key = { it.id }) { matchedUser ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clickable { viewModel.openUserProfile(matchedUser) }
                  .testTag("search_user_card_${matchedUser.id}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  com.example.ui.components.VybeAvatar(user = matchedUser, size = 44.dp)
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(matchedUser.displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                      Spacer(modifier = Modifier.width(4.dp))
                      VerificationBadge(user = matchedUser)
                    }
                    Text("@${matchedUser.username}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                      color = MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(6.dp)
                    ) {
                      Text(
                        text = "ID: ${matchedUser.publicVybeId}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = VybeBrandCyan,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                      )
                    }
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          if (matchedShops.isNotEmpty()) {
            item {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Verified Shops (${matchedShops.size})",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = VybeVerifiedGold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
              )
            }

            items(matchedShops, key = { it.id }) { matchedShop ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 5.dp)
                  .clickable { viewModel.openShopDetail(matchedShop) },
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
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(VybeVerifiedGold.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(Icons.Default.Store, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(24.dp))
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(matchedShop.businessName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${matchedShop.category} • ${matchedShop.locationAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      } else {
        // Core VYBE Experiences - Highlighted as the foundational signature features
        item {
          Spacer(modifier = Modifier.height(14.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("✨", fontSize = 18.sp)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "CORE VYBE EXPERIENCES",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.8.sp,
                    color = VybeBrandCyan
                  )
                }
                Surface(
                  color = VybeViolet.copy(alpha = 0.25f),
                  shape = RoundedCornerShape(8.dp)
                ) {
                  Text(
                    text = "FLAGSHIP",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeViolet,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Six intimate, anti-algorithm ways to connect without vanity metrics or superficial filters.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
              )
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        // Feature 1 & 2: UNLOAD & UNFILTERED (Large Featured Pair)
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // UNLOAD
            Card(
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
                .testTag("explore_unload_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, VybeViolet.copy(alpha = 0.6f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeViolet.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("🕊️", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text("UNLOAD", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                  Spacer(modifier = Modifier.width(4.dp))
                  Box(
                    modifier = Modifier
                      .size(6.dp)
                      .clip(CircleShape)
                      .background(VybeOnlineGreen)
                  )
                }
                Text("24/7 Live Listening", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Zero audio recordings. Ephemeral voice & chat sanctuary.",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Enter Sanctuary →",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeViolet
                )
              }
            }

            // UNFILTERED
            Card(
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.navigateTo(Screen.UNFILTERED_VIEW) }
                .testTag("explore_unfiltered_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, VybeOnlineGreen.copy(alpha = 0.6f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeOnlineGreen.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("🌿", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("UNFILTERED", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Raw & Real Moments", fontSize = 11.sp, color = VybeOnlineGreen, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Authentic snapshots without skin-smoothing or filters.",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Post Unfiltered →",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeOnlineGreen
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Feature 3 & 4: WHAT NEXT & TIME CAPSULE
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // WHAT NEXT
            Card(
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.navigateTo(Screen.WHAT_NEXT_VIEW) }
                .testTag("explore_what_next_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, VybeBrandCyan.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeBrandCyan.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("🔮", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("WHAT NEXT", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Culture Pulse Polls", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Vote on dilemmas & predictions before they unfold.",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Vote Now →",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandCyan
                )
              }
            }

            // TIME CAPSULE
            Card(
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) }
                .testTag("explore_time_capsule_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, VybeVerifiedGold.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeVerifiedGold.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("⏳", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("TIME CAPSULE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Sealed Vaults", fontSize = 11.sp, color = VybeVerifiedGold, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Lock memories into future dates with invite links.",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Seal Memory →",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeVerifiedGold
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
        }

        // Feature 5 & 6: DOUBLE TAKE & CROSSED PATHS
        item {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            // DOUBLE TAKE
            Card(
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.navigateTo(Screen.DOUBLE_TAKE_VIEW) }
                .testTag("explore_double_take_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, VybeHeartbreakRed.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeHeartbreakRed.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("🎭", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("DOUBLE TAKE", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Two-Sided Dilemmas", fontSize = 11.sp, color = VybeHeartbreakRed, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Public & private dilemmas connecting synced answers.",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Sync Minds →",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeHeartbreakRed
                )
              }
            }

            // CROSSED PATHS
            Card(
              modifier = Modifier
                .weight(1f)
                .clickable { viewModel.navigateTo(Screen.CREATE_CROSSED_PATH) }
                .testTag("explore_crossed_card"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(18.dp),
              border = androidx.compose.foundation.BorderStroke(1.2.dp, VybeBrandCyan.copy(alpha = 0.5f))
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(VybeBrandCyan.copy(alpha = 0.25f)),
                  contentAlignment = Alignment.Center
                ) {
                  Text("📍", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("CROSSED PATHS", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                Text("Real-World Spark", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = "Post encounters by venue & Google Maps location.",
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "Find Connection →",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeBrandCyan
                )
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

      // Admin & Moderation Quick Bar (Isolated to Admin accounts only)
      if (currentUser?.role == com.example.data.model.UserRole.ADMIN) {
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
              .clickable { viewModel.navigateTo(Screen.ADMIN_CONSOLE) }
              .testTag("explore_admin_card")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Default.Security, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Moderation Queue & Safety Review",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.weight(1f))
              Text(
                text = "2 Pending",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = VybeVerifiedGold
              )
            }
          }
        }
      }

      // "What Happens Next?" Predictions
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡ What Happens Next?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          }
          Text(
            text = "Pure Entertainment",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      items(predictions, key = { it.id }) { prediction ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("prediction_card_${prediction.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = VybeViolet.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = prediction.category,
                  fontSize = 11.sp,
                  color = VybeViolet,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
              Text(
                text = "${prediction.totalVotes} votes • ${prediction.closingTimeText}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = prediction.title,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            prediction.options.forEach { option ->
              val isSelected = prediction.userSelectedOptionId == option.id
              Surface(
                color = if (isSelected) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, VybeViolet) else null,
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp)
                  .clickable { viewModel.votePrediction(prediction.id, option.id) }
                  .testTag("option_${prediction.id}_${option.id}")
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = option.text,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                  )
                  if (prediction.userSelectedOptionId != null) {
                    Text(
                      text = "${option.percentage}%",
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp,
                      color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Verified Businesses & VYBE Shops
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text("🛍️ Verified Businesses & Shops", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          Text(
            text = "Authenticity Badge",
            style = MaterialTheme.typography.labelSmall,
            color = VybeVerifiedGold,
            fontWeight = FontWeight.Bold
          )
        }
      }

      items(shops, key = { it.id }) { shop ->
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { viewModel.openShopDetail(shop) }
            .testTag("shop_card_${shop.id}"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(18.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        ) {
          Column {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
              AsyncImage(
                model = shop.coverUrl,
                contentDescription = shop.businessName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
              Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .padding(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(12.dp))
                  Spacer(modifier = Modifier.width(3.dp))
                  Text(
                    text = "${shop.rating} (${shop.reviewsCount})",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                  )
                }
              }
            }

            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = shop.businessName,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                  imageVector = Icons.Default.Store,
                  contentDescription = "Verified Shop",
                  tint = VybeVerifiedGold,
                  modifier = Modifier.size(16.dp)
                )
              }
              Text(
                text = "${shop.category} • ${shop.locationAddress}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = shop.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
              )

              // Quick Catalog pill previews
              Spacer(modifier = Modifier.height(8.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shop.catalog.take(2).forEach { item ->
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(
                      text = "${item.name} (${item.priceFormatted})",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Trending Hashtags
      item {
        Spacer(modifier = Modifier.height(18.dp))
        Text(
          text = "🔥 Trending in India",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
          modifier = Modifier.fillMaxWidth(),
          contentPadding = PaddingValues(horizontal = 16.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          items(trendingTags) { tag ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp)
            ) {
              Text(
                text = "#$tag",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = VybeViolet,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
              )
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(80.dp))
      }
    }
  }
}
}
