package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VybeCircle
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val circles by viewModel.circles.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var selectedTab by remember { mutableStateOf("DISCOVER") } // "DISCOVER" or "MY_CIRCLES"
  var selectedCategory by remember { mutableStateOf("All") }
  var showCreateDialog by remember { mutableStateOf(false) }

  // Create form state
  var newCircleName by remember { mutableStateOf("") }
  var newCircleDescription by remember { mutableStateOf("") }
  var newCircleCategory by remember { mutableStateOf("Tech & Founders") }
  var newCircleIsPrivate by remember { mutableStateOf(false) }

  val categories = listOf("All", "Tech & Founders", "Wellness", "Creative", "Lifestyle")

  val myCircles = remember(circles, currentUser?.id) {
    circles.filter { it.isMember || (currentUser != null && it.ownerId == currentUser!!.id) }
  }

  val displayedCircles = remember(circles, selectedTab, selectedCategory) {
    val base = if (selectedTab == "MY_CIRCLES") myCircles else circles
    if (selectedCategory == "All") base else base.filter { it.category == selectedCategory }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        windowInsets = WindowInsets.statusBars,
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.size(48.dp).testTag("circle_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "VYBE Circle",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
              )
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                color = VybeBrandViolet.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
              ) {
                Text(
                  text = "COMMUNITIES",
                  color = VybeBrandCyan,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
              }
            }
            Text(
              text = "Private circles & interest networks • AN NR NEXT PRODUCT",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          IconButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.size(48.dp).testTag("create_circle_button")
          ) {
            Icon(Icons.Default.AddCircleOutline, contentDescription = "Create Circle", tint = VybeViolet)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Tab Selector: Discover vs My Circles
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            val isDiscover = selectedTab == "DISCOVER"
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDiscover) VybeViolet else Color.Transparent)
                .clickable { selectedTab = "DISCOVER" }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Discover Circles",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isDiscover) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            val isMy = selectedTab == "MY_CIRCLES"
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isMy) VybeViolet else Color.Transparent)
                .clickable { selectedTab = "MY_CIRCLES" }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "My Circles (${myCircles.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isMy) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // 2. Category Filter Pills
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(categories) { cat ->
            val isSelected = selectedCategory == cat
            Surface(
              color = if (isSelected) VybeBrandCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, if (isSelected) VybeBrandCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
              modifier = Modifier.clickable { selectedCategory = cat }
            ) {
              Text(
                text = cat,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) VybeBrandCyan else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
              )
            }
          }
        }
      }

      // 3. Circles List
      if (displayedCircles.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(18.dp)
          ) {
            Column(
              modifier = Modifier.padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Outlined.Groups, contentDescription = null, tint = VybeBrandViolet, modifier = Modifier.size(44.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text("No Circles Found", fontWeight = FontWeight.Bold, fontSize = 16.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                "Create your own private circle to share moments with close friends, founders, or collaborators.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
              )
              Spacer(modifier = Modifier.height(14.dp))
              Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                shape = RoundedCornerShape(12.dp)
              ) {
                Text("Create a Circle", fontWeight = FontWeight.Bold, color = Color.White)
              }
            }
          }
        }
      } else {
        items(displayedCircles, key = { it.id }) { circle ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth().testTag("circle_card_${circle.id}")
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Box(
                    modifier = Modifier
                      .size(46.dp)
                      .clip(CircleShape)
                      .background(
                        Brush.linearGradient(
                          listOf(VybeBrandViolet, VybeBrandCyan)
                        )
                      ),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = if (circle.isPrivate) Icons.Default.Lock else Icons.Default.Groups,
                      contentDescription = null,
                      tint = Color.White,
                      modifier = Modifier.size(24.dp)
                    )
                  }
                  Spacer(modifier = Modifier.width(12.dp))
                  Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = circle.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                      )
                      if (circle.isPrivate) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Private", tint = VybeViolet, modifier = Modifier.size(14.dp))
                      }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                      text = "${circle.membersCount} members • ${circle.category}",
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                Button(
                  onClick = {
                    if (circle.isMember) {
                      viewModel.leaveCircle(circle.id)
                    } else {
                      viewModel.joinCircle(circle.id)
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (circle.isMember) MaterialTheme.colorScheme.surfaceVariant else VybeViolet
                  ),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = if (circle.isMember) "Joined" else "Join",
                    color = if (circle.isMember) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = circle.description,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )

              if (circle.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  circle.tags.forEach { tag ->
                    Surface(
                      color = VybeBrandCyan.copy(alpha = 0.12f),
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Text(
                        text = tag,
                        color = VybeBrandCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
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
  }

  // Create Circle Dialog
  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      title = { Text("Create a VYBE Circle", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = newCircleName,
            onValueChange = { newCircleName = it },
            label = { Text("Circle Name") },
            placeholder = { Text("e.g. Bangalore Indie Tech") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = newCircleDescription,
            onValueChange = { newCircleDescription = it },
            label = { Text("Description") },
            placeholder = { Text("What is this circle about?") },
            modifier = Modifier.fillMaxWidth()
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text("Private Circle", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Text("Invite-only membership", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
              checked = newCircleIsPrivate,
              onCheckedChange = { newCircleIsPrivate = it }
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (newCircleName.isNotBlank()) {
              viewModel.createCircle(
                name = newCircleName.trim(),
                description = newCircleDescription.trim(),
                category = newCircleCategory,
                isPrivate = newCircleIsPrivate
              )
              newCircleName = ""
              newCircleDescription = ""
              showCreateDialog = false
            }
          },
          enabled = newCircleName.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Create Circle", color = Color.White, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
