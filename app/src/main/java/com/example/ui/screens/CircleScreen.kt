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
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val circles by viewModel.circles.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()

  var selectedTab by remember { mutableStateOf("DISCOVER") }
  var selectedCategory by remember { mutableStateOf("All") }
  var showCreateDialog by remember { mutableStateOf(false) }

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
    contentWindowInsets = WindowInsets(0.dp),
    containerColor = palette.canvas,
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Circle",
        subtitle = "Private circles & interest networks",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "circle_back_button",
        actions = {
          IconButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.size(48.dp).testTag("create_circle_button")
          ) {
            Icon(Icons.Default.AddCircleOutline, contentDescription = "Create Circle", tint = palette.accent)
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
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Tab Selector: Discover vs My Circles
      item {
        Surface(
          color = palette.elevated,
          shape = RoundedCornerShape(VybeTokens.Radius.md),
          border = BorderStroke(1.dp, palette.border),
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
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(if (isDiscover) palette.accent else Color.Transparent)
                .clickable { selectedTab = "DISCOVER" }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Discover Circles",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isDiscover) Color.White else palette.textSecondary
              )
            }

            val isMy = selectedTab == "MY_CIRCLES"
            Box(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(if (isMy) palette.accent else Color.Transparent)
                .clickable { selectedTab = "MY_CIRCLES" }
                .padding(vertical = 10.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "My Circles (${myCircles.size})",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isMy) Color.White else palette.textSecondary
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
              color = if (isSelected) palette.accent.copy(alpha = 0.16f) else palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
              modifier = Modifier
                .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                .clickable { selectedCategory = cat }
            ) {
              Text(
                text = cat,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) palette.accent else palette.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
              )
            }
          }
        }
      }

      // 3. Circles List
      if (displayedCircles.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.Groups,
            title = "No Circles Found",
            subtitle = "Create your own private circle to share moments with close friends, founders, or collaborators.",
            actionLabel = "Create a Circle",
            onAction = { showCreateDialog = true }
          )
        }
      } else {
        items(displayedCircles, key = { it.id }) { circle ->
          VybeSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("circle_card_${circle.id}")
          ) {
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
                    .background(Brush.linearGradient(listOf(VybeSocialViolet, VybeGrowBlue))),
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
                      fontFamily = PlusJakartaSansFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 16.sp,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (circle.isPrivate) {
                      Spacer(modifier = Modifier.width(4.dp))
                      Icon(Icons.Default.Lock, contentDescription = "Private", tint = palette.accent, modifier = Modifier.size(14.dp))
                    }
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "${circle.membersCount} members • ${circle.category}",
                    fontSize = 12.sp,
                    color = palette.textSecondary
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
                  containerColor = if (circle.isMember) palette.elevated else palette.accent
                ),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
              ) {
                Text(
                  text = if (circle.isMember) "Joined" else "Join",
                  color = if (circle.isMember) palette.textSecondary else Color.White,
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
              color = palette.textSecondary
            )

            if (circle.tags.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                circle.tags.forEach { tag ->
                  Surface(
                    color = palette.accentSecondary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(VybeTokens.Radius.xs)
                  ) {
                    Text(
                      text = tag,
                      color = palette.accentSecondary,
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

  // Create Circle Dialog
  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = { Text("Create a VYBE Circle", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold) },
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
              Text("Private Circle", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text("Invite-only membership", fontSize = 12.sp, color = palette.textSecondary)
            }
            Switch(
              checked = newCircleIsPrivate,
              onCheckedChange = { newCircleIsPrivate = it }
            )
          }
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Create Circle",
          enabled = newCircleName.isNotBlank(),
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
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { showCreateDialog = false }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }
}
