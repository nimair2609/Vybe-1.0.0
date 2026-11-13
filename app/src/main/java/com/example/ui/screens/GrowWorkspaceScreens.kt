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
  val notifications by viewModel.notifications.collectAsState()
  val unreadCount = remember(notifications) { notifications.count { !it.isRead } }

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
      .statusBarsPadding(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header: VYBE Grow + Mode Switch + Notification Bell + Avatar
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        VybeBrandWordmark(isGrowMode = true)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          VybeModeSwitch(
            currentMode = VybeExperienceMode.GROW,
            onModeChange = { viewModel.setExperienceMode(it) }
          )
          VybeNotificationBellButton(
            unreadCount = unreadCount,
            onClick = { viewModel.navigateTo(Screen.NOTIFICATIONS) },
            testTag = "grow_notifications_button"
          )
          if (currentUser != null) {
            VybeAvatar(
              user = currentUser!!,
              size = 36.dp,
              showPulseDot = false,
              onClick = { viewModel.selectGrowTab("PROFILE") }
            )
          }
        }
      }
    }

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
              modifier = Modifier.heightIn(min = 44.dp).testTag("grow_continue_learning_button")
            ) {
              Text("Continue Learning", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              Spacer(modifier = Modifier.width(6.dp))
              Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
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
        TextButton(onClick = { viewModel.navigateTo(Screen.VYBE_GROW) }) {
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
              text = "Skill & Habit Momentum",
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

    // Featured Card (Matches Mockup "Build Your Future")
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
              icon = Icons.Default.ArrowForward,
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
              imageVector = Icons.Default.RocketLaunch,
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
    modifier = modifier
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
  var filterIndex by remember { mutableIntStateOf(0) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }
  var newDesc by remember { mutableStateOf("") }
  var newPrivate by remember { mutableStateOf(false) }

  val filtered = remember(circles, query, filterIndex, currentUser?.id) {
    val base = when (filterIndex) {
      1 -> circles.filter { it.isMember || (currentUser != null && it.ownerId == currentUser!!.id) }
      else -> circles
    }
    val q = query.trim().lowercase()
    if (q.isEmpty()) base else base.filter {
      it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.category.lowercase().contains(q)
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
        Icon(Icons.Default.Edit, contentDescription = "Create Circle")
      }
    }
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .statusBarsPadding(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          VybeBrandWordmark(isGrowMode = true)
          VybeModeSwitch(
            currentMode = VybeExperienceMode.GROW,
            onModeChange = { viewModel.setExperienceMode(it) }
          )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "Circle",
          fontFamily = PlusJakartaSansFontFamily,
          fontWeight = FontWeight.ExtraBold,
          fontSize = 24.sp,
          color = palette.textPrimary
        )
        Text(
          text = "Connect with like-minded builders, creators & peers",
          fontSize = 13.sp,
          color = palette.textSecondary
        )
      }

      item {
        VybeSearchBar(
          query = query,
          onQueryChange = { query = it },
          placeholder = "Search Circle...",
          testTag = "grow_circle_search"
        )
      }

      item {
        VybeUnderlineTabs(
          tabs = listOf("All Circles", "My Circles", "Communities"),
          selectedIndex = filterIndex,
          onTabSelected = { idx ->
            if (idx == 2) {
              viewModel.openCommunityZone()
            } else {
              filterIndex = idx
            }
          },
          testTagPrefix = "grow_circle_tab"
        )
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
            modifier = Modifier.fillMaxWidth().testTag("grow_circle_card_${circle.id}")
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
                  imageVector = if (circle.isPrivate) Icons.Default.Lock else Icons.Default.Groups,
                  contentDescription = null,
                  tint = palette.accent,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = circle.name,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = palette.textPrimary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = "${circle.membersCount} members • ${circle.category}",
                  fontSize = 12.sp,
                  color = palette.textSecondary
                )
              }
              if (circle.isMember) {
                VybeSecondaryButton(
                  text = "Joined",
                  onClick = { viewModel.leaveCircle(circle.id) }
                )
              } else {
                VybePrimaryButton(
                  text = "Join",
                  onClick = { viewModel.joinCircle(circle.id) }
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
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
          OutlinedTextField(
            value = newDesc,
            onValueChange = { newDesc = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth()
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Private Circle", fontSize = 13.sp, color = palette.textPrimary, fontWeight = FontWeight.SemiBold)
            Switch(checked = newPrivate, onCheckedChange = { newPrivate = it })
          }
        }
      },
      confirmButton = {
        VybePrimaryButton(
          text = "Create",
          enabled = newName.isNotBlank(),
          onClick = {
            viewModel.createCircle(newName.trim(), newDesc.trim(), "Tech & Founders", newPrivate)
            newName = ""
            newDesc = ""
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
      .statusBarsPadding(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        VybeBrandWordmark(isGrowMode = true)
        VybeModeSwitch(
          currentMode = VybeExperienceMode.GROW,
          onModeChange = { viewModel.setExperienceMode(it) }
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
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
  val communities by viewModel.communities.collectAsState()
  val growBadges by viewModel.growBadges.collectAsState()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .background(palette.canvas)
      .statusBarsPadding(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        VybeBrandWordmark(isGrowMode = true)
        VybeModeSwitch(
          currentMode = VybeExperienceMode.GROW,
          onModeChange = { viewModel.setExperienceMode(it) }
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = "Opportunities & Credentials",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        color = palette.textPrimary
      )
      Text(
        text = "Creator programs, verified shops, and earned credentials",
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
          Icon(Icons.Default.AutoGraph, contentDescription = null, tint = palette.accent, modifier = Modifier.size(28.dp))
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
          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
        }
      }
    }

    item {
      VybeSurfaceCard(
        onClick = { viewModel.navigateTo(Screen.VERIFICATION_CENTER) },
        borderColor = palette.accentSecondary.copy(alpha = 0.35f)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Verified, contentDescription = null, tint = palette.accentSecondary, modifier = Modifier.size(28.dp))
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
          Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
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
            Icon(Icons.Default.Storefront, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(shop.businessName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(shop.category, fontSize = 12.sp, color = palette.textSecondary)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = palette.textMuted)
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
            Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(badge.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = palette.textPrimary)
              Text(badge.description, fontSize = 12.sp, color = palette.textSecondary)
            }
          }
        }
      }
    } else if (communities.isEmpty() && shops.isEmpty()) {
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
