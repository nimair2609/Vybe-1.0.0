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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GrowJourney
import com.example.ui.Screen
import com.example.ui.VybeViewModel
import com.example.ui.components.*
import com.example.ui.theme.*

@Composable
fun GrowHomeWorkspace(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val currentUser by viewModel.currentUser.collectAsState()
  val growJourneys by viewModel.growJourneys.collectAsState()
  val growBadges by viewModel.growBadges.collectAsState()
  val circles by viewModel.circles.collectAsState()

  val joinedJourneys = remember(growJourneys) { growJourneys.filter { it.isJoined } }
  val primaryJourney = joinedJourneys.firstOrNull() ?: growJourneys.firstOrNull()
  val overallProgress = remember(joinedJourneys) {
    if (joinedJourneys.isEmpty()) 0f
    else joinedJourneys.map { (it.currentDay.toFloat() / it.durationDays.coerceAtLeast(1)).coerceIn(0f, 1f) }.average().toFloat()
  }
  val progressPct = (overallProgress * 100).toInt()
  val firstName = currentUser?.firstName?.ifBlank { currentUser?.displayName?.split(" ")?.firstOrNull() } ?: "Member"

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(palette.canvas)
      .testTag("grow_home_workspace"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Hero Banner Card ("Welcome back, ...")
    item {
      Surface(
        shape = RoundedCornerShape(VybeTokens.Radius.xl),
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                listOf(VybeGrowBlue, VybeSocialViolet)
              )
            )
            .padding(20.dp)
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = "Welcome back, $firstName",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.ExtraBold,
              fontSize = 20.sp,
              color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = primaryJourney?.let { "Continue \"${it.title}\" — Day ${it.currentDay} of ${it.durationDays}" }
                ?: "Keep learning, building, and growing with your VYBE network.",
              fontSize = 13.sp,
              color = Color.White.copy(alpha = 0.88f),
              lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
              onClick = { viewModel.navigateTo(Screen.VYBE_GROW) },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.heightIn(min = 48.dp).testTag("grow_continue_learning_button")
            ) {
              Text("Continue Learning", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Spacer(modifier = Modifier.width(6.dp))
              Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
          }
        }
      }
    }

    // Your Progress Card (Progress Ring + Skill Development + Mini Bars)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Your Progress",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 17.sp,
          color = palette.textPrimary
        )
        TextButton(
          onClick = { viewModel.navigateTo(Screen.VYBE_GROW) },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text("View all", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
        }
      }

      VybeSurfaceCard(
        onClick = { viewModel.navigateTo(Screen.VYBE_GROW) },
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          VybeProgressRing(
            progress = if (joinedJourneys.isEmpty()) 0.0f else overallProgress,
            label = "$progressPct%"
          )
          Spacer(modifier = Modifier.width(16.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Skill & Track Momentum",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = if (joinedJourneys.isNotEmpty()) {
                "${joinedJourneys.size} active track(s) • ${growBadges.size} badge(s) earned"
              } else {
                "Join a guided track to measure weekly progress"
              },
              fontSize = 12.sp,
              color = palette.textSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            VybeActivityMiniBars(
              values = if (joinedJourneys.isEmpty()) {
                listOf(0.2f, 0.25f, 0.2f, 0.3f, 0.25f, 0.35f, 0.3f)
              } else {
                listOf(0.35f, 0.55f, 0.45f, 0.72f, 0.68f, 0.85f, overallProgress.coerceAtLeast(0.4f))
              }
            )
          }
        }
      }
    }

    // Quick Access 4-Tile Strip (Learning, Circle, Projects, Opportunities)
    item {
      Text(
        text = "Quick Access",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = palette.textPrimary
      )
      Spacer(modifier = Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GrowQuickTile(
          label = "Learning",
          icon = Icons.Outlined.MenuBook,
          onClick = { viewModel.navigateTo(Screen.VYBE_GROW) },
          modifier = Modifier.weight(1f)
        )
        GrowQuickTile(
          label = "Circle",
          icon = Icons.Outlined.Groups,
          onClick = { viewModel.selectGrowTab("CIRCLE") },
          modifier = Modifier.weight(1f)
        )
        GrowQuickTile(
          label = "Projects",
          icon = Icons.Outlined.FolderOpen,
          onClick = { viewModel.selectGrowTab("PROJECTS") },
          modifier = Modifier.weight(1f)
        )
        GrowQuickTile(
          label = "Jobs",
          icon = Icons.Outlined.WorkOutline,
          onClick = { viewModel.selectGrowTab("OPPORTUNITIES") },
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Featured Card
    item {
      Text(
        text = "Featured",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = palette.textPrimary
      )
      Spacer(modifier = Modifier.height(8.dp))
      VybeSurfaceCard(
        borderColor = palette.accent.copy(alpha = 0.28f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Build Your Future",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp,
              color = palette.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Join ${circles.size} active professional circles and collaborate on curated tracks.",
              fontSize = 12.sp,
              color = palette.textSecondary,
              lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            VybePrimaryButton(
              text = "Explore Circles",
              icon = Icons.AutoMirrored.Outlined.ArrowForward,
              onClick = { viewModel.selectGrowTab("CIRCLE") },
              testTag = "grow_featured_explore_button"
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(RoundedCornerShape(VybeTokens.Radius.lg))
              .background(Brush.linearGradient(listOf(VybeGrowBlue.copy(alpha = 0.16f), VybeSocialViolet.copy(alpha = 0.16f)))),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Outlined.RocketLaunch,
              contentDescription = null,
              tint = palette.accent,
              modifier = Modifier.size(30.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun GrowQuickTile(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  VybeSurfaceCard(
    onClick = onClick,
    cornerRadius = VybeTokens.Radius.md,
    contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
    modifier = modifier.defaultMinSize(minHeight = 48.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(VybeTokens.Radius.sm))
          .background(palette.accent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = palette.accent,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = label,
        fontFamily = PlusJakartaSansFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun GrowCircleWorkspace(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val circles by viewModel.circles.collectAsState()
  val currentUser by viewModel.currentUser.collectAsState()
  var query by remember { mutableStateOf("") }
  var selectedTab by remember { mutableStateOf("DISCOVER") }
  var selectedCategory by remember { mutableStateOf("All") }
  var showCreateDialog by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }
  var newDesc by remember { mutableStateOf("") }
  var newCategory by remember { mutableStateOf("Tech & Founders") }
  var newPrivate by remember { mutableStateOf(false) }

  val categories = remember { listOf("All", "Tech & Founders", "Wellness", "Creative", "Lifestyle") }
  val createCategories = remember { listOf("Tech & Founders", "Wellness", "Creative", "Lifestyle") }

  val myCircles = remember(circles, currentUser?.id) {
    circles.filter { it.isMember || (currentUser != null && it.ownerId == currentUser!!.id) }
  }

  val filtered = remember(circles, myCircles, query, selectedTab, selectedCategory) {
    val tabBase = if (selectedTab == "MY_CIRCLES") myCircles else circles
    val catBase = if (selectedCategory == "All") tabBase else tabBase.filter { it.category == selectedCategory }
    val q = query.trim().lowercase()
    if (q.isEmpty()) catBase else catBase.filter {
      it.name.lowercase().contains(q) ||
        it.description.lowercase().contains(q) ||
        it.category.lowercase().contains(q) ||
        it.tags.any { tag -> tag.lowercase().contains(q) }
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    containerColor = palette.canvas,
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showCreateDialog = true },
        containerColor = palette.accent,
        contentColor = Color.White,
        modifier = Modifier.testTag("grow_circle_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Create Circle")
      }
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .testTag("grow_circle_workspace"),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Circle",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.ExtraBold,
              fontSize = 22.sp,
              color = palette.textPrimary
            )
            Text(
              text = "Private circles & professional peer networks",
              fontSize = 13.sp,
              color = palette.textSecondary
            )
          }
          IconButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
              .size(48.dp)
              .testTag("create_circle_button")
          ) {
            Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Create Circle", tint = palette.accent)
          }
        }
      }

      item {
        VybeSearchBar(
          query = query,
          onQueryChange = { query = it },
          placeholder = "Search Circle...",
          testTag = "grow_circle_search"
        )
      }

      // Single Unified 2-Tab Selector: Discover Circles | My Circles
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
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(if (isDiscover) palette.accent else Color.Transparent)
                .clickable { selectedTab = "DISCOVER" }
                .padding(vertical = 10.dp)
                .testTag("grow_circle_discover_tab"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "Discover Circles",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isDiscover) Color.White else palette.textSecondary,
                maxLines = 1,
                softWrap = false
              )
            }

            val isMy = selectedTab == "MY_CIRCLES"
            Box(
              modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
                .background(if (isMy) palette.accent else Color.Transparent)
                .clickable { selectedTab = "MY_CIRCLES" }
                .padding(vertical = 10.dp)
                .testTag("grow_circle_my_tab"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "My Circles (${myCircles.size})",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isMy) Color.White else palette.textSecondary,
                maxLines = 1,
                softWrap = false
              )
            }
          }
        }
      }

      // Category Filter Pills
      item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(categories) { cat ->
            val isSelected = selectedCategory == cat
            Surface(
              color = if (isSelected) palette.accent.copy(alpha = 0.16f) else palette.elevated,
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              border = BorderStroke(1.dp, if (isSelected) palette.accent else palette.border),
              modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                .clickable { selectedCategory = cat }
                .testTag("grow_circle_category_${cat.lowercase().replace(" ", "_")}")
            ) {
              Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(
                  text = cat,
                  fontSize = 12.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isSelected) palette.accent else palette.textPrimary
                )
              }
            }
          }
        }
      }

      if (filtered.isEmpty()) {
        item {
          VybeEmptyState(
            icon = Icons.Outlined.Groups,
            title = "No matching circles",
            subtitle = "Start a dedicated peer circle for founders, learners, or creators.",
            actionLabel = "Create Circle",
            onAction = { showCreateDialog = true }
          )
        }
      } else {
        items(filtered, key = { it.id }) { circle ->
          VybeSurfaceCard(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("grow_circle_card_${circle.id}")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(44.dp)
                  .clip(CircleShape)
                  .background(palette.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (circle.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Groups,
                  contentDescription = null,
                  tint = palette.accent,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = circle.name,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                  )
                  if (circle.isPrivate) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                      imageVector = Icons.Outlined.Lock,
                      contentDescription = "Private",
                      tint = palette.accent,
                      modifier = Modifier.size(14.dp)
                    )
                  }
                }
                Text(
                  text = "${circle.membersCount} members • ${circle.category}",
                  fontSize = 12.sp,
                  color = palette.textSecondary
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              if (circle.isMember) {
                VybeSecondaryButton(
                  text = "Joined",
                  onClick = { viewModel.leaveCircle(circle.id) },
                  testTag = "grow_circle_action_${circle.id}"
                )
              } else {
                VybePrimaryButton(
                  text = "Join",
                  onClick = { viewModel.joinCircle(circle.id) },
                  testTag = "grow_circle_action_${circle.id}"
                )
              }
            }
            if (circle.description.isNotBlank()) {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = circle.description,
                fontSize = 13.sp,
                color = palette.textSecondary,
                lineHeight = 18.sp
              )
            }
            if (circle.tags.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(circle.tags) { tag ->
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

  if (showCreateDialog) {
    AlertDialog(
      onDismissRequest = { showCreateDialog = false },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = {
        Text("Create Circle", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("Circle Name") },
            placeholder = { Text("e.g. Bangalore Indie Tech") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_circle_name_input")
          )
          OutlinedTextField(
            value = newDesc,
            onValueChange = { newDesc = it },
            label = { Text("Description") },
            placeholder = { Text("What is this circle about?") },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_circle_desc_input")
          )
          Text(
            text = "Category",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textPrimary
          )
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(createCategories) { cat ->
              val isCatSelected = newCategory == cat
              Surface(
                color = if (isCatSelected) palette.accent.copy(alpha = 0.16f) else palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                border = BorderStroke(1.dp, if (isCatSelected) palette.accent else palette.border),
                modifier = Modifier
                  .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                  .clickable { newCategory = cat }
              ) {
                Text(
                  text = cat,
                  fontSize = 11.sp,
                  fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                  color = if (isCatSelected) palette.accent else palette.textPrimary,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column {
              Text("Private Circle", fontSize = 13.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
              Text("Invite-only membership", fontSize = 11.sp, color = palette.textSecondary)
            }
            Switch(checked = newPrivate, onCheckedChange = { newPrivate = it })
          }
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Create",
          enabled = newName.isNotBlank(),
          testTag = "create_circle_submit_button",
          onClick = {
            viewModel.createCircle(newName.trim(), newDesc.trim(), newCategory, newPrivate)
            newName = ""
            newDesc = ""
            newCategory = "Tech & Founders"
            newPrivate = false
            showCreateDialog = false
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

@Composable
fun GrowProjectsWorkspace(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val growJourneys by viewModel.growJourneys.collectAsState()
  var selectedTrackForReflect by remember { mutableStateOf<GrowJourney?>(null) }
  var reflectionText by remember { mutableStateOf("") }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(palette.canvas)
      .testTag("grow_projects_workspace"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Text(
        text = "Projects & Skill Tracks",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        color = palette.textPrimary
      )
      Text(
        text = "Structured 7 / 21 / 30-day tracks synced with your Grow profile",
        fontSize = 13.sp,
        color = palette.textSecondary
      )
    }

    if (growJourneys.isEmpty()) {
      item {
        VybeEmptyState(
          icon = Icons.Outlined.FolderOpen,
          title = "No guided projects yet",
          subtitle = "Open the full Grow console to refresh and enroll in upcoming cohorts.",
          actionLabel = "Open Full Grow",
          onAction = { viewModel.navigateTo(Screen.VYBE_GROW) }
        )
      }
    } else {
      items(growJourneys, key = { it.id }) { journey ->
        VybeSurfaceCard(
          borderColor = if (journey.isJoined) palette.accent.copy(alpha = 0.45f) else palette.border,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
              Text(
                text = journey.title,
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
              Text(
                text = "${journey.category} • ${journey.durationDays}-Day Track • ${journey.participantsCount} peers",
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
            if (journey.isJoined) {
              VybePrimaryButton(
                text = "Reflect",
                onClick = { selectedTrackForReflect = journey }
              )
            } else {
              VybeSecondaryButton(
                text = "Start",
                onClick = { viewModel.joinGrowJourney(journey.id) }
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = journey.description,
            fontSize = 13.sp,
            color = palette.textSecondary,
            lineHeight = 18.sp
          )
          if (journey.isJoined) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(
              progress = { (journey.currentDay.toFloat() / journey.durationDays.coerceAtLeast(1)).coerceIn(0f, 1f) },
              color = palette.accent,
              trackColor = palette.elevated,
              modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(VybeTokens.Radius.pill))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Day ${journey.currentDay} / ${journey.durationDays} • ${journey.dailyPrompt}",
              fontSize = 12.sp,
              color = palette.accent,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }

  if (selectedTrackForReflect != null) {
    val track = selectedTrackForReflect!!
    AlertDialog(
      onDismissRequest = { selectedTrackForReflect = null },
      containerColor = palette.card,
      titleContentColor = palette.textPrimary,
      textContentColor = palette.textSecondary,
      shape = RoundedCornerShape(VybeTokens.Radius.xl),
      title = {
        Text("Day ${track.currentDay} Check-In", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(track.dailyPrompt, fontSize = 13.sp, color = palette.textSecondary)
          OutlinedTextField(
            value = reflectionText,
            onValueChange = { reflectionText = it },
            placeholder = { Text("Share your progress milestone...") },
            modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp)
          )
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Save Progress",
          onClick = {
            viewModel.submitGrowReflection(
              journeyId = track.id,
              dayNumber = track.currentDay,
              text = reflectionText.ifBlank { "Completed Day ${track.currentDay} milestone" },
              isShared = false
            )
            reflectionText = ""
            selectedTrackForReflect = null
          }
        )
      },
      dismissButton = {
        TextButton(onClick = { selectedTrackForReflect = null }) {
          Text("Cancel", color = palette.textSecondary)
        }
      }
    )
  }
}

@Composable
fun GrowOpportunitiesWorkspace(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val shops by viewModel.shops.collectAsState()
  val growBadges by viewModel.growBadges.collectAsState()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(palette.canvas)
      .testTag("grow_opportunities_workspace"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Text(
        text = "Opportunities & Credentials",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        color = palette.textPrimary
      )
      Text(
        text = "Creator programs, verified hubs, and earned credentials",
        fontSize = 13.sp,
        color = palette.textSecondary
      )
    }

    item {
      VybeSurfaceCard(
        onClick = { viewModel.navigateTo(Screen.CREATOR_STUDIO) },
        borderColor = palette.accent.copy(alpha = 0.35f)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.AutoGraph, contentDescription = null, tint = palette.accent, modifier = Modifier.size(26.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Creator Studio & Insights",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = palette.textPrimary
            )
            Text(
              "Track audience growth, reach, and collaboration readiness",
              fontSize = 12.sp,
              color = palette.textSecondary
            )
          }
          Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
        }
      }
    }

    item {
      VybeSurfaceCard(
        onClick = { viewModel.navigateTo(Screen.VERIFICATION_CENTER) },
        borderColor = palette.accentSecondary.copy(alpha = 0.35f)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.Verified, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(26.dp))
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text(
              "Verification & Business Badges",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              color = palette.textPrimary
            )
            Text(
              "Apply for creator verification or register your verified storefront",
              fontSize = 12.sp,
              color = palette.textSecondary
            )
          }
          Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
        }
      }
    }

    if (shops.isNotEmpty()) {
      item {
        Text(
          text = "Verified Business Partner Hubs (${shops.size})",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = palette.textPrimary
        )
      }
      items(shops.take(4), key = { it.id }) { shop ->
        VybeSurfaceCard(
          onClick = { viewModel.openShopDetail(shop) }
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Storefront, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(shop.businessName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(shop.category, fontSize = 12.sp, color = palette.textSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
          }
        }
      }
    }

    if (growBadges.isNotEmpty()) {
      item {
        Text(
          text = "Earned Credentials (${growBadges.size})",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          color = palette.textPrimary
        )
      }
      items(growBadges, key = { it.id }) { badge ->
        VybeSurfaceCard(borderColor = VybeVerifiedGold.copy(alpha = 0.4f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(badge.description, fontSize = 12.sp, color = palette.textSecondary)
            }
          }
        }
      }
    } else if (shops.isEmpty()) {
      item {
        VybeEmptyState(
          icon = Icons.Outlined.WorkspacePremium,
          title = "Your credentials appear here",
          subtitle = "Complete Grow projects or verify your business to showcase achievements."
        )
      }
    }
  }
}

@Composable
fun GrowProfileWorkspace(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val currentUser by viewModel.currentUser.collectAsState()
  val growJourneys by viewModel.growJourneys.collectAsState()
  val circles by viewModel.circles.collectAsState()
  val growBadges by viewModel.growBadges.collectAsState()

  val joinedTracks = remember(growJourneys) { growJourneys.filter { it.isJoined } }
  val joinedCircles = remember(circles, currentUser?.id) {
    circles.filter { it.isMember || (currentUser != null && it.ownerId == currentUser!!.id) }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(palette.canvas)
      .testTag("grow_profile_workspace"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Dedicated Professional Grow Identity Card
    item {
      VybeSurfaceCard(contentPadding = PaddingValues(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (currentUser != null) {
            VybeAvatar(user = currentUser!!, size = 64.dp, showPulseDot = false)
            Spacer(modifier = Modifier.width(14.dp))
          }
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = currentUser?.displayName ?: "VYBE Member",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              if (currentUser != null) {
                VerificationBadge(user = currentUser!!, enableExplanationDialog = true)
              }
            }
            Text(
              text = "@${currentUser?.username.orEmpty().ifBlank { "member" }}",
              fontSize = 12.sp,
              fontWeight = FontWeight.Medium,
              color = palette.accent
            )
            if (!currentUser?.bio.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = currentUser!!.bio,
                fontSize = 12.sp,
                color = palette.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
          IconButton(
            onClick = { viewModel.navigateTo(Screen.SETTINGS) },
            modifier = Modifier
              .size(48.dp)
              .testTag("grow_profile_settings_button")
          ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = palette.textPrimary)
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.6.dp, color = palette.border)
        Spacer(modifier = Modifier.height(12.dp))

        // Grow Metrics Strip (Strictly Grow stats)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          GrowMetricItem(count = joinedTracks.size, label = "Tracks")
          GrowMetricItem(count = joinedCircles.size, label = "Circles")
          GrowMetricItem(count = growBadges.size, label = "Badges")
          GrowMetricItem(count = currentUser?.followingCount ?: 0, label = "Linkups")
        }
      }
    }

    // Active Skill Tracks
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Active Skill Tracks",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = palette.textPrimary
        )
        TextButton(
          onClick = { viewModel.selectGrowTab("PROJECTS") },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text("Explore Tracks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
        }
      }
    }

    if (joinedTracks.isEmpty()) {
      item {
        VybeEmptyState(
          icon = Icons.Outlined.MenuBook,
          title = "No enrolled tracks yet",
          subtitle = "Join a structured skill track from Projects to build measurable momentum.",
          actionLabel = "Browse Tracks",
          onAction = { viewModel.selectGrowTab("PROJECTS") }
        )
      }
    } else {
      items(joinedTracks, key = { it.id }) { track ->
        VybeSurfaceCard(
          onClick = { viewModel.navigateTo(Screen.VYBE_GROW) },
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(track.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(
                text = "Day ${track.currentDay} of ${track.durationDays} • ${track.category}",
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
            Text(
              text = "${((track.currentDay.toFloat() / track.durationDays.coerceAtLeast(1)) * 100).toInt()}%",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = palette.accent
            )
          }
          Spacer(modifier = Modifier.height(8.dp))
          LinearProgressIndicator(
            progress = { (track.currentDay.toFloat() / track.durationDays.coerceAtLeast(1)).coerceIn(0f, 1f) },
            color = palette.accent,
            trackColor = palette.elevated,
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(VybeTokens.Radius.pill))
          )
        }
      }
    }

    // Joined Circles
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Your Circles (${joinedCircles.size})",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 16.sp,
          color = palette.textPrimary
        )
        TextButton(
          onClick = { viewModel.selectGrowTab("CIRCLE") },
          modifier = Modifier.defaultMinSize(minHeight = 48.dp)
        ) {
          Text("Manage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.accent)
        }
      }
    }

    if (joinedCircles.isEmpty()) {
      item {
        VybeSurfaceCard(onClick = { viewModel.selectGrowTab("CIRCLE") }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Groups, contentDescription = null, tint = palette.accent, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Join your first Circle", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
              Text("Connect with founders, learners, and creators in Grow Circle.", fontSize = 12.sp, color = palette.textSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
          }
        }
      }
    } else {
      items(joinedCircles, key = { it.id }) { circle ->
        VybeSurfaceCard(onClick = { viewModel.selectGrowTab("CIRCLE") }) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (circle.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Groups,
              contentDescription = null,
              tint = palette.accent,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(circle.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text("${circle.membersCount} members • ${circle.category}", fontSize = 12.sp, color = palette.textSecondary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
          }
        }
      }
    }

    // Earned Credentials & Creator Links
    item {
      Text(
        text = "Credentials & Studio",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = palette.textPrimary
      )
    }

    if (growBadges.isNotEmpty()) {
      items(growBadges, key = { it.id }) { badge ->
        VybeSurfaceCard(borderColor = VybeVerifiedGold.copy(alpha = 0.35f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(badge.description, fontSize = 12.sp, color = palette.textSecondary)
            }
          }
        }
      }
    }

    item {
      VybeSurfaceCard(contentPadding = PaddingValues(0.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable { viewModel.navigateTo(Screen.CREATOR_STUDIO) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Outlined.AutoGraph, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Creator Studio & Insights", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
            Text("Analytics, growth signals, and milestones", fontSize = 11.sp, color = palette.textSecondary)
          }
          Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
        }
        HorizontalDivider(thickness = 0.6.dp, color = palette.border)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .clickable { viewModel.navigateTo(Screen.VERIFICATION_CENTER) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Outlined.Verified, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(22.dp))
          Spacer(modifier = Modifier.width(14.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("Verification & Business Center", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = palette.textPrimary)
            Text("Manage creator verification & partner storefronts", fontSize = 11.sp, color = palette.textSecondary)
          }
          Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textMuted)
        }
      }
    }
  }
}

@Composable
private fun GrowMetricItem(count: Int, label: String) {
  val palette = LocalVybePalette.current
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = count.toString(),
      fontFamily = PlusJakartaSansFontFamily,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 17.sp,
      color = palette.textPrimary
    )
    Text(
      text = label,
      fontSize = 11.sp,
      color = palette.textSecondary
    )
  }
}
