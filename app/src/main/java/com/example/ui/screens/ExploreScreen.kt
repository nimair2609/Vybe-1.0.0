package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaType
import com.example.data.model.UserRole
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

  var selectedCategoryFilter by remember { mutableStateOf("All") }
  val categoryFilters = listOf("All", "Spaces", "Grid", "Dilemmas", "Verified")

  val trendingTags = listOf(
    "MumbaiLoFi" to "18.4K",
    "CricketIndia" to "42.1K",
    "DesignEthics" to "9.6K",
    "MonsoonCoffee" to "14.2K",
    "BangaloreTech" to "31.8K",
    "IndieCinema" to "11.5K"
  )

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

  // Filter media posts for the community explore grid
  val mediaPosts = remember(posts) {
    posts.filter { it.mediaUrls.isNotEmpty() }
  }

  Scaffold(
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(MaterialTheme.colorScheme.background)
      ) {
        TopAppBar(
          title = {
            // Modern, sleek Instagram/Threads-style pill search input
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Search,
                  contentDescription = "Search",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                TextField(
                  value = searchQuery,
                  onValueChange = { viewModel.setSearchQuery(it) },
                  placeholder = {
                    Text(
                      text = "Search creators, @username, #tags, venues...",
                      fontSize = 13.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                  },
                  singleLine = true,
                  colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("explore_search_bar")
                )
                if (isSearching) {
                  IconButton(
                    onClick = { viewModel.setSearchQuery("") },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Clear,
                      contentDescription = "Clear search",
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(16.dp)
                    )
                  }
                }
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
          )
        )

        // Filter Pills Ribbon (when not searching)
        if (!isSearching) {
          LazyRow(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(categoryFilters) { filter ->
              val isSelected = selectedCategoryFilter == filter
              Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp),
                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier
                  .clickable { selectedCategoryFilter = filter }
                  .testTag("filter_chip_$filter")
              ) {
                Text(
                  text = filter,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
              }
            }
          }
        }
      }
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (isSearching) {
        item {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "SEARCH RESULTS FOR \"$searchQuery\"",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
        }

        if (matchedUsers.isEmpty() && matchedShops.isEmpty()) {
          item {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
              Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(
                  imageVector = Icons.Default.SearchOff,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                  modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "No matching profiles or venues",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Search by creator username (@handle) or public VYBE ID.",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                  lineHeight = 16.sp
                )
              }
            }
          }
        } else {
          if (matchedUsers.isNotEmpty()) {
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "CREATORS & PROFILES",
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  letterSpacing = 0.8.sp,
                  color = VybeViolet
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  color = VybeViolet.copy(alpha = 0.15f),
                  shape = CircleShape
                ) {
                  Text(
                    text = "${matchedUsers.size}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeViolet,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                  )
                }
              }
            }

            items(matchedUsers, key = { it.id }) { matchedUser ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 4.dp)
                  .clickable { viewModel.openUserProfile(matchedUser) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(CircleShape)
                      .background(MaterialTheme.colorScheme.surfaceVariant)
                  ) {
                    if (!matchedUser.avatarUrl.isNullOrBlank()) {
                      AsyncImage(
                        model = matchedUser.avatarUrl,
                        contentDescription = matchedUser.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                      )
                    } else {
                      Text(
                        text = matchedUser.displayName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Center)
                      )
                    }
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = matchedUser.displayName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      VerificationBadge(user = matchedUser)
                    }
                    Text(
                      text = "@${matchedUser.username}",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = matchedUser.publicVybeId,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = VybeBrandCyan,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(6.dp))
                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }

          if (matchedShops.isNotEmpty()) {
            item {
              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "VERIFIED SHOPS",
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  letterSpacing = 0.8.sp,
                  color = VybeVerifiedGold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  color = VybeVerifiedGold.copy(alpha = 0.15f),
                  shape = CircleShape
                ) {
                  Text(
                    text = "${matchedShops.size}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeVerifiedGold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                  )
                }
              }
            }

            items(matchedShops, key = { it.id }) { matchedShop ->
              Card(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 4.dp)
                  .clickable { viewModel.openShopDetail(matchedShop) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Box(
                    modifier = Modifier
                      .size(44.dp)
                      .clip(RoundedCornerShape(10.dp))
                      .background(VybeVerifiedGold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.Store,
                      contentDescription = null,
                      tint = VybeVerifiedGold,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(matchedShop.businessName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                      text = "${matchedShop.category} • ${matchedShop.locationAddress}",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                  )
                }
              }
            }
          }
        }
      } else {
        // Regular Discovery Experience (Non-Searching)

        // Section: Trending Topics (Instagram / Twitter style)
        item {
          Spacer(modifier = Modifier.height(4.dp))
          LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            items(trendingTags) { (tag, count) ->
              Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.clickable { viewModel.setSearchQuery(tag) }
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.TrendingUp,
                    contentDescription = null,
                    tint = VybeViolet,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "#$tag",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = count,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(14.dp))
        }

        // Section 1: Signature VYBE Spaces & Hubs
        if (selectedCategoryFilter == "All" || selectedCategoryFilter == "Spaces") {
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "SIGNATURE SPACES",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 12.sp,
                  letterSpacing = 1.2.sp,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Intimate, anti-algorithm ways to connect",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Pair 1: Unload Sanctuary & Unfiltered Camera
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // UNLOAD
              EditorialFeatureCard(
                title = "UNLOAD",
                badgeText = "24/7 LIVE",
                subtitle = "Ephemeral Voice & Chat",
                description = "Zero audio recordings. Safe real-time listening sanctuary.",
                accentColor = VybeViolet,
                icon = Icons.Default.GraphicEq,
                isLive = true,
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.navigateTo(Screen.UNLOAD_VIEW) }
                  .testTag("explore_unload_card")
              )

              // UNFILTERED
              EditorialFeatureCard(
                title = "UNFILTERED",
                badgeText = "RAW 1X",
                subtitle = "Pure Impressions",
                description = "Authentic unedited moments with comments-only interaction.",
                accentColor = VybeOnlineGreen,
                icon = Icons.Default.CameraAlt,
                isLive = false,
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.navigateTo(Screen.UNFILTERED_VIEW) }
                  .testTag("explore_unfiltered_card")
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Pair 2: Time Capsule & Crossed Paths
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // TIME CAPSULE
              EditorialFeatureCard(
                title = "TIME CAPSULE",
                badgeText = "TIMELOCK",
                subtitle = "Sealed Digital Vaults",
                description = "Lock media into future milestones with private access links.",
                accentColor = VybeVerifiedGold,
                icon = Icons.Default.Lock,
                isLive = false,
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.navigateTo(Screen.TIME_CAPSULES_VIEW) }
                  .testTag("explore_time_capsule_card")
              )

              // CROSSED PATHS
              EditorialFeatureCard(
                title = "CROSSED PATHS",
                badgeText = "MAP RADAR",
                subtitle = "Real-World Encounters",
                description = "Rediscover missed connections by venue & map location.",
                accentColor = VybeBrandCyan,
                icon = Icons.Default.NearMe,
                isLive = false,
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.navigateTo(Screen.CREATE_CROSSED_PATH) }
                  .testTag("explore_crossed_card")
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          // Pair 3: Double Take & What Next
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // DOUBLE TAKE
              EditorialFeatureCard(
                title = "DOUBLE TAKE",
                badgeText = "MIND SYNC",
                subtitle = "Two-Sided Dilemmas",
                description = "Public & private dilemmas pairing synchronized perspectives.",
                accentColor = MoodFun,
                icon = Icons.Default.CompareArrows,
                isLive = false,
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.navigateTo(Screen.DOUBLE_TAKE_VIEW) }
                  .testTag("explore_double_take_card")
              )

              // WHAT NEXT
              EditorialFeatureCard(
                title = "WHAT NEXT",
                badgeText = "PULSE POLLS",
                subtitle = "Culture Consensus",
                description = "Predict cultural moments and vote on dilemmas in real time.",
                accentColor = VybeMagenta,
                icon = Icons.Default.Poll,
                isLive = false,
                modifier = Modifier
                  .weight(1f)
                  .clickable { viewModel.navigateTo(Screen.WHAT_NEXT_VIEW) }
                  .testTag("explore_what_next_card")
              )
            }
            Spacer(modifier = Modifier.height(16.dp))
          }
        }

        // Section: Admin & Safety Console (Admin Only)
        if (currentUser?.role == UserRole.ADMIN) {
          item {
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.4f)),
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clickable { viewModel.navigateTo(Screen.ADMIN_CONSOLE) }
                .testTag("explore_admin_card")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Security,
                  contentDescription = null,
                  tint = VybeVerifiedGold,
                  modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Safety & Moderation Queue",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  Text(
                    text = "Admin privileges active • Content review desk",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Surface(
                  color = VybeVerifiedGold.copy(alpha = 0.2f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "2 Review",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeVerifiedGold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(14.dp))
          }
        }

        // Section 2: Real-World Social Media Explore Grid (Instagram-style Visual Discovery)
        if ((selectedCategoryFilter == "All" || selectedCategoryFilter == "Grid") && mediaPosts.isNotEmpty()) {
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column {
                Text(
                  text = "DISCOVER COMMUNITY",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 12.sp,
                  letterSpacing = 1.2.sp,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Curated visual moments from across India",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
          }

          val chunkedPosts = mediaPosts.take(12).chunked(3)
          chunkedPosts.forEach { rowItems ->
            item {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                rowItems.forEach { mediaPost ->
                  val primaryMedia = mediaPost.mediaUrls.firstOrNull() ?: ""
                  Box(
                    modifier = Modifier
                      .weight(1f)
                      .aspectRatio(1f)
                      .clip(RoundedCornerShape(8.dp))
                      .background(MaterialTheme.colorScheme.surfaceVariant)
                      .clickable { viewModel.openUserProfile(mediaPost.author) }
                  ) {
                    AsyncImage(
                      model = primaryMedia,
                      contentDescription = mediaPost.caption,
                      contentScale = ContentScale.Crop,
                      modifier = Modifier.fillMaxSize()
                    )
                    // Badge if video/reel
                    if (mediaPost.isVybesReel || mediaPost.mediaType == MediaType.VIDEO || mediaPost.mediaType == MediaType.REEL) {
                      Box(
                        modifier = Modifier
                          .align(Alignment.TopEnd)
                          .padding(6.dp)
                          .size(20.dp)
                          .clip(CircleShape)
                          .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                      ) {
                        Icon(
                          imageVector = Icons.Default.PlayArrow,
                          contentDescription = "Video",
                          tint = Color.White,
                          modifier = Modifier.size(12.dp)
                        )
                      }
                    }
                  }
                }
                // Fill remaining spaces in row if less than 3
                repeat(3 - rowItems.size) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
          item {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }

        // Section 3: Culture Consensus ("What Happens Next?" Polls)
        if (selectedCategoryFilter == "All" || selectedCategoryFilter == "Dilemmas") {
          item {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text(
                  text = "CULTURE PULSE",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 12.sp,
                  letterSpacing = 1.2.sp,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Interactive dilemmas & cultural predictions",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Surface(
                color = VybeMagenta.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = "LIVE POLLS",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeMagenta,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          items(predictions, key = { it.id }) { prediction ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("prediction_card_${prediction.id}"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Surface(
                    color = VybeViolet.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                  ) {
                    Text(
                      text = prediction.category.uppercase(),
                      fontSize = 10.sp,
                      color = VybeViolet,
                      fontWeight = FontWeight.Bold,
                      letterSpacing = 0.5.sp,
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                  }
                  Text(
                    text = "${prediction.totalVotes} votes • ${prediction.closingTimeText}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                  text = prediction.title,
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                prediction.options.forEach { option ->
                  val isSelected = prediction.userSelectedOptionId == option.id
                  val hasVoted = prediction.userSelectedOptionId != null

                  Surface(
                    color = if (isSelected) VybeViolet.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    border = if (isSelected) BorderStroke(1.5.dp, VybeViolet) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(vertical = 4.dp)
                      .clickable { viewModel.votePrediction(prediction.id, option.id) }
                      .testTag("option_${prediction.id}_${option.id}")
                  ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                      // Smooth percentage fill bar
                      if (hasVoted) {
                        Box(
                          modifier = Modifier
                            .fillMaxWidth(fraction = (option.percentage / 100f).coerceIn(0f, 1f))
                            .matchParentSize()
                            .background(if (isSelected) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        )
                      }
                      Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = option.text,
                          fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                          fontSize = 13.sp,
                          color = MaterialTheme.colorScheme.onSurface,
                          modifier = Modifier.weight(1f)
                        )
                        if (hasVoted) {
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
          }
        }

        // Section 4: Verified Brands & Venues
        if (selectedCategoryFilter == "All" || selectedCategoryFilter == "Verified") {
          item {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column {
                Text(
                  text = "VERIFIED CREATOR SHOPS",
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = 12.sp,
                  letterSpacing = 1.2.sp,
                  color = MaterialTheme.colorScheme.primary
                )
                Text(
                  text = "Authenticated independent brands & storefronts",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Surface(
                color = VybeVerifiedGold.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = VybeVerifiedGold,
                    modifier = Modifier.size(12.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "VERIFIED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeVerifiedGold
                  )
                }
              }
            }
            Spacer(modifier = Modifier.height(10.dp))
          }

          items(shops, key = { it.id }) { shop ->
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clickable { viewModel.openShopDetail(shop) }
                .testTag("shop_card_${shop.id}"),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
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
                  // Rating Badge
                  Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .padding(8.dp)
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Icon(Icons.Default.Star, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(12.dp))
                      Spacer(modifier = Modifier.width(4.dp))
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
                      fontWeight = FontWeight.Bold,
                      fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                      imageVector = Icons.Default.Verified,
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
                    maxLines = 2,
                    lineHeight = 16.sp
                  )

                  if (shop.catalog.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                      shop.catalog.take(2).forEach { item ->
                        Surface(
                          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                          shape = RoundedCornerShape(8.dp)
                        ) {
                          Text(
                            text = "${item.name} (${item.priceFormatted})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                          )
                        }
                      }
                    }
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
  }
}

/**
 * Editorial, high-end feature space card for VYBE flagship features.
 * Eliminates oversized childish emojis in favor of sleek iconography,
 * high-contrast micro-typography, crisp live indicators, and subtle glassmorphic borders.
 */
@Composable
private fun EditorialFeatureCard(
  title: String,
  badgeText: String,
  subtitle: String,
  description: String,
  accentColor: Color,
  icon: ImageVector,
  isLive: Boolean,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Icon container with subtle tonal tint
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.15f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(20.dp)
          )
        }

        // Clean micro-badge
        Surface(
          color = accentColor.copy(alpha = 0.12f),
          shape = RoundedCornerShape(6.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            if (isLive) {
              Box(
                modifier = Modifier
                  .size(6.dp)
                  .clip(CircleShape)
                  .background(VybeOnlineGreen)
              )
              Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
              text = badgeText,
              fontSize = 9.sp,
              fontWeight = FontWeight.ExtraBold,
              letterSpacing = 0.5.sp,
              color = accentColor
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = title,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurface
      )

      Text(
        text = subtitle,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = accentColor
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = description,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 14.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Enter Space",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = accentColor
        )
        Spacer(modifier = Modifier.width(2.dp))
        Icon(
          imageVector = Icons.Default.ChevronRight,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(14.dp)
        )
      }
    }
  }
}
