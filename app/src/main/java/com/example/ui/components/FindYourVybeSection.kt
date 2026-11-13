package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.LivePulseStatus
import com.example.data.model.Post
import com.example.data.model.Story
import com.example.data.model.User
import com.example.ui.MainTab
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

enum class FindYourVybeTab(val title: String) {
  LINK_UP("Link Up"),
  SUGGEST("Suggest")
}

internal data class VybeDiscoveryCandidate(
  val user: User,
  val mutualUsers: List<User>,
  val discoveryReason: String,
  val score: Int
)

private fun extractHashtags(text: String): Set<String> {
  if (text.isBlank()) return emptySet()
  return Regex("#([A-Za-z0-9_]{2,30})")
    .findAll(text)
    .map { it.groupValues[1].lowercase() }
    .toSet()
}

internal fun buildFindYourVybeCandidates(
  currentUser: User?,
  allUsers: List<User>,
  posts: List<Post>,
  stories: List<Story>,
  followingIds: Set<String>,
  followerIds: Set<String>,
  vybeMatchIds: Set<String>,
  blockedIds: Set<String>,
  restrictedIds: Set<String>
): List<VybeDiscoveryCandidate> {
  val myId = currentUser?.id.orEmpty()

  val combinedById = LinkedHashMap<String, User>()
  allUsers.forEach { u ->
    if (u.id.isNotBlank()) combinedById[u.id] = u
  }
  posts.forEach { p ->
    if (p.author.id.isNotBlank() && !combinedById.containsKey(p.author.id)) {
      combinedById[p.author.id] = p.author
    }
    p.comments.forEach { c ->
      if (c.author.id.isNotBlank() && !combinedById.containsKey(c.author.id)) {
        combinedById[c.author.id] = c.author
      }
    }
  }
  stories.forEach { s ->
    if (s.author.id.isNotBlank() && !combinedById.containsKey(s.author.id)) {
      combinedById[s.author.id] = s.author
    }
  }

  val myNetworkIds = (followingIds + followerIds + vybeMatchIds) - myId
  val usersLookup = combinedById

  val interactedPeersByUser = HashMap<String, MutableSet<String>>()
  posts.forEach { p ->
    val postAuthorId = p.author.id
    p.comments.forEach { c ->
      if (c.author.id.isNotBlank() && postAuthorId.isNotBlank() && c.author.id != postAuthorId) {
        interactedPeersByUser.getOrPut(c.author.id) { HashSet() }.add(postAuthorId)
        interactedPeersByUser.getOrPut(postAuthorId) { HashSet() }.add(c.author.id)
      }
    }
  }

  val myHashtags = if (myId.isNotBlank()) {
    posts.filter { it.author.id == myId }
      .flatMap { extractHashtags(it.caption) }
      .toSet() + extractHashtags(currentUser?.bio.orEmpty())
  } else {
    emptySet()
  }

  return combinedById.values
    .filter { u ->
      u.id.isNotBlank() &&
        u.id != myId &&
        !u.isBanned &&
        !u.isDeactivated &&
        !blockedIds.contains(u.id) &&
        !restrictedIds.contains(u.id)
    }
    .map { candidate ->
      val candidatePeers = interactedPeersByUser[candidate.id].orEmpty()
      val mutualIdSet = LinkedHashSet<String>()
      candidatePeers.forEach { pid ->
        if (myNetworkIds.contains(pid) && pid != candidate.id && !blockedIds.contains(pid)) {
          mutualIdSet.add(pid)
        }
      }
      val mutualUsers = mutualIdSet.mapNotNull { usersLookup[it] }

      val candidateTags = posts
        .filter { it.author.id == candidate.id }
        .flatMap { extractHashtags(it.caption) }
        .toSet() + extractHashtags(candidate.bio)
      val sharedTags = myHashtags.intersect(candidateTags)

      val sameMood = currentUser?.currentMood != null && candidate.currentMood == currentUser.currentMood
      val followsViewer = candidate.followsMe || followerIds.contains(candidate.id)
      val isMatch = vybeMatchIds.contains(candidate.id)
      val hasActivePulse = candidate.livePulse != LivePulseStatus.NONE

      var score = 0
      if (mutualUsers.isNotEmpty()) score += 45 + (mutualUsers.size * 12)
      if (followsViewer) score += 35
      if (isMatch) score += 30
      if (sameMood) score += 24
      if (sharedTags.isNotEmpty()) score += 18 + (sharedTags.size * 5)
      if (hasActivePulse) score += 12
      if (candidate.isOnline) score += 10
      if (candidate.avatarUrl.isNotBlank()) score += 6
      if (candidate.bio.isNotBlank()) score += 6

      val reason = when {
        mutualUsers.size >= 2 -> "${mutualUsers.size} mutual connections • @${mutualUsers.first().username}"
        mutualUsers.size == 1 -> "Mutual connection with @${mutualUsers.first().username}"
        followsViewer -> "Follows your VYBE"
        sameMood -> "Shares your ${candidate.currentMood?.title} vybe"
        sharedTags.isNotEmpty() -> "Shared interest • #${sharedTags.first()}"
        hasActivePulse -> candidate.livePulse.label
        candidate.isOnline -> "Active now on VYBE"
        else -> "Discover on VYBE"
      }

      VybeDiscoveryCandidate(
        user = candidate,
        mutualUsers = mutualUsers,
        discoveryReason = reason,
        score = score
      )
    }
    .sortedWith(
      compareByDescending<VybeDiscoveryCandidate> { it.score }
        .thenByDescending { it.user.followersCount }
        .thenBy { it.user.displayName.lowercase() }
    )
}

@Composable
fun FindYourVybeSection(
  viewModel: VybeViewModel,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  val posts by viewModel.posts.collectAsState()
  val stories by viewModel.stories.collectAsState()
  val followingUserIds by viewModel.followingUserIds.collectAsState()
  val requestedFollowUserIds by viewModel.requestedFollowUserIds.collectAsState()
  val followerUserIds by viewModel.followerUserIds.collectAsState()
  val incomingRequestIds by viewModel.incomingFollowRequestUserIds.collectAsState()
  val vybeMatches by viewModel.vybeMatches.collectAsState()
  val blockedUserIds by viewModel.blockedUserIds.collectAsState()
  val restrictedUserIds by viewModel.restrictedUserIds.collectAsState()
  val dismissedSuggestionIds by viewModel.dismissedSuggestionUserIds.collectAsState()

  var selectedTab by rememberSaveable { mutableStateOf(FindYourVybeTab.LINK_UP) }
  var showSeeAllDialog by rememberSaveable { mutableStateOf(false) }

  val vybeMatchIds = remember(vybeMatches) { vybeMatches.map { it.id }.toSet() }

  val allCandidates = remember(
    currentUser,
    allUsers,
    posts,
    stories,
    followingUserIds,
    followerUserIds,
    vybeMatchIds,
    blockedUserIds,
    restrictedUserIds
  ) {
    buildFindYourVybeCandidates(
      currentUser = currentUser,
      allUsers = allUsers,
      posts = posts,
      stories = stories,
      followingIds = followingUserIds,
      followerIds = followerUserIds,
      vybeMatchIds = vybeMatchIds,
      blockedIds = blockedUserIds,
      restrictedIds = restrictedUserIds
    )
  }

  val incomingRequests = remember(allCandidates, incomingRequestIds) {
    allCandidates.filter { incomingRequestIds.contains(it.user.id) }
  }
  val linkUpCandidates = remember(allCandidates, followingUserIds, incomingRequestIds) {
    val unlinkedFirst = allCandidates.filter { !followingUserIds.contains(it.user.id) && !incomingRequestIds.contains(it.user.id) }
    val alreadyLinked = allCandidates.filter { followingUserIds.contains(it.user.id) && !incomingRequestIds.contains(it.user.id) }
    (incomingRequests + unlinkedFirst + alreadyLinked).distinctBy { it.user.id }
  }

  val suggestCandidates = remember(allCandidates, followingUserIds, dismissedSuggestionIds) {
    allCandidates
      .filter { candidate ->
        !followingUserIds.contains(candidate.user.id) &&
          !dismissedSuggestionIds.contains(candidate.user.id)
      }
      .distinctBy { it.user.id }
  }

  VybeSurfaceCard(
    contentPadding = PaddingValues(vertical = 16.dp),
    modifier = modifier
      .fillMaxWidth()
      .testTag("find_your_vybe_section")
  ) {
    // Header Row: Brand Title + "See All" Action
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(VybeTokens.Radius.md))
            .background(
              Brush.linearGradient(
                listOf(palette.accent.copy(alpha = 0.22f), palette.accentSecondary.copy(alpha = 0.22f))
              )
            ),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(19.dp)
          )
        }
        Column {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "Find Your Vybe",
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = palette.textPrimary
            )
            if (incomingRequests.isNotEmpty()) {
              Surface(
                color = palette.accent,
                shape = RoundedCornerShape(VybeTokens.Radius.pill)
              ) {
                Text(
                  text = "${incomingRequests.size} new",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                )
              }
            }
          }
          Text(
            text = if (selectedTab == FindYourVybeTab.LINK_UP) {
              "Discover people, mutual connections & link requests"
            } else {
              "Personalized people recommendations tailored to your vybe"
            },
            fontSize = 11.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      TextButton(
        onClick = { showSeeAllDialog = true },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("find_your_vybe_see_all_button")
      ) {
        Text(
          text = "See All",
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = palette.accent
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "See all people in Find Your Vybe",
          tint = palette.accent,
          modifier = Modifier.size(14.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Segmented Tabs: "Link Up" & "Suggest"
    Surface(
      color = palette.elevated,
      shape = RoundedCornerShape(VybeTokens.Radius.md),
      border = BorderStroke(1.dp, palette.border),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FindYourVybeTab.values().forEach { tab ->
          val isSelected = selectedTab == tab
          val tabTag = if (tab == FindYourVybeTab.LINK_UP) {
            "find_your_vybe_tab_link_up"
          } else {
            "find_your_vybe_tab_suggest"
          }
          val pillTextColor by animateColorAsState(
            targetValue = if (isSelected) Color.White else palette.textSecondary,
            animationSpec = tween(200),
            label = "fyv_tab_text_${tab.name}"
          )
          val countBadge = if (tab == FindYourVybeTab.LINK_UP) linkUpCandidates.size else suggestCandidates.size

          Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 44.dp)
              .clip(RoundedCornerShape(VybeTokens.Radius.sm))
              .then(
                if (isSelected) {
                  Modifier.background(Brush.horizontalGradient(listOf(VybeGrowBlue, VybeSocialViolet)))
                } else {
                  Modifier
                }
              )
              .clickable { selectedTab = tab }
              .padding(horizontal = 10.dp, vertical = 8.dp)
              .testTag(tabTag)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(
                imageVector = if (tab == FindYourVybeTab.LINK_UP) Icons.Default.Group else Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = pillTextColor,
                modifier = Modifier.size(15.dp)
              )
              Text(
                text = tab.title,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = pillTextColor
              )
              if (countBadge > 0) {
                Surface(
                  color = if (isSelected) Color.White.copy(alpha = 0.22f) else palette.card,
                  shape = RoundedCornerShape(VybeTokens.Radius.pill)
                ) {
                  Text(
                    text = "$countBadge",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                  )
                }
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    AnimatedContent(
      targetState = selectedTab,
      transitionSpec = {
        val forward = targetState.ordinal > initialState.ordinal
        val enterOffset: (Int) -> Int = { full -> if (forward) full / 4 else -full / 4 }
        val exitOffset: (Int) -> Int = { full -> if (forward) -full / 4 else full / 4 }
        (slideInHorizontally(tween(260, easing = FastOutSlowInEasing), enterOffset) +
          fadeIn(tween(220)))
          .togetherWith(
            slideOutHorizontally(tween(220, easing = FastOutSlowInEasing), exitOffset) +
              fadeOut(tween(180))
          )
      },
      label = "find_your_vybe_tab_content"
    ) { activeTab ->
      when (activeTab) {
        FindYourVybeTab.LINK_UP -> {
          if (linkUpCandidates.isEmpty()) {
            FindYourVybeEmptyCard(
              title = "Discover people on VYBE",
              subtitle = "Search creators or share a post to start linking up with new connections.",
              actionLabel = "Explore People",
              onActionClick = { viewModel.selectTab(MainTab.EXPLORE) },
              palette = palette
            )
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("find_your_vybe_link_up_row")
            ) {
              items(linkUpCandidates.take(12), key = { "linkup_${it.user.id}" }) { item ->
                val u = item.user
                FindYourVybeCard(
                  candidate = item,
                  isSuggestMode = false,
                  isFollowing = followingUserIds.contains(u.id),
                  isFollowRequested = requestedFollowUserIds.contains(u.id) || u.isFollowRequestedByMe,
                  hasIncomingRequest = incomingRequestIds.contains(u.id) || u.hasPendingFollowRequestForMe,
                  palette = palette,
                  onOpenProfile = { viewModel.openUserProfile(u) },
                  onToggleFollow = { viewModel.toggleFollowUser(u.id) },
                  onRespondRequest = { approve -> viewModel.respondToFollowRequest(u.id, approve) },
                  onDismiss = null
                )
              }
            }
          }
        }

        FindYourVybeTab.SUGGEST -> {
          if (suggestCandidates.isEmpty()) {
            FindYourVybeEmptyCard(
              title = if (dismissedSuggestionIds.isNotEmpty()) {
                "You're all caught up on suggestions"
              } else {
                "Personalized suggestions appear here"
              },
              subtitle = if (dismissedSuggestionIds.isNotEmpty()) {
                "Dismissed profiles stay hidden so you never see repeated suggestions."
              } else {
                "As more people join or share interests with you, tailored recommendations will appear."
              },
              actionLabel = if (dismissedSuggestionIds.isNotEmpty()) "Reset Dismissed" else "Explore People",
              onActionClick = {
                if (dismissedSuggestionIds.isNotEmpty()) {
                  viewModel.restoreDismissedSuggestions()
                } else {
                  viewModel.selectTab(MainTab.EXPLORE)
                }
              },
              palette = palette
            )
          } else {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("find_your_vybe_suggest_row")
            ) {
              items(suggestCandidates.take(12), key = { "suggest_${it.user.id}" }) { item ->
                val u = item.user
                FindYourVybeCard(
                  candidate = item,
                  isSuggestMode = true,
                  isFollowing = followingUserIds.contains(u.id),
                  isFollowRequested = requestedFollowUserIds.contains(u.id) || u.isFollowRequestedByMe,
                  hasIncomingRequest = incomingRequestIds.contains(u.id) || u.hasPendingFollowRequestForMe,
                  palette = palette,
                  onOpenProfile = { viewModel.openUserProfile(u) },
                  onToggleFollow = { viewModel.toggleFollowUser(u.id) },
                  onRespondRequest = { approve -> viewModel.respondToFollowRequest(u.id, approve) },
                  onDismiss = { viewModel.dismissSuggestedUser(u.id) }
                )
              }
            }
          }
        }
      }
    }
  }

  if (showSeeAllDialog) {
    FindYourVybeSeeAllDialog(
      initialTab = selectedTab,
      linkUpCandidates = linkUpCandidates,
      suggestCandidates = suggestCandidates,
      followingUserIds = followingUserIds,
      requestedFollowUserIds = requestedFollowUserIds,
      incomingRequestIds = incomingRequestIds,
      hasDismissedSuggestions = dismissedSuggestionIds.isNotEmpty(),
      onOpenProfile = { u ->
        showSeeAllDialog = false
        viewModel.openUserProfile(u)
      },
      onToggleFollow = { uid -> viewModel.toggleFollowUser(uid) },
      onRespondRequest = { uid, approve -> viewModel.respondToFollowRequest(uid, approve) },
      onDismissSuggestion = { uid -> viewModel.dismissSuggestedUser(uid) },
      onRestoreDismissed = { viewModel.restoreDismissedSuggestions() },
      onClose = { showSeeAllDialog = false }
    )
  }
}

@Composable
private fun FindYourVybeCard(
  candidate: VybeDiscoveryCandidate,
  isSuggestMode: Boolean,
  isFollowing: Boolean,
  isFollowRequested: Boolean,
  hasIncomingRequest: Boolean,
  palette: VybePalette,
  onOpenProfile: () -> Unit,
  onToggleFollow: () -> Unit,
  onRespondRequest: (Boolean) -> Unit,
  onDismiss: (() -> Unit)?
) {
  val user = candidate.user

  Surface(
    color = palette.elevated,
    shape = RoundedCornerShape(VybeTokens.Radius.lg),
    border = BorderStroke(1.dp, palette.border),
    modifier = Modifier
      .width(208.dp)
      .clip(RoundedCornerShape(VybeTokens.Radius.lg))
      .clickable { onOpenProfile() }
      .testTag("find_your_vybe_card_${user.id}")
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      if (isSuggestMode && onDismiss != null) {
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .align(Alignment.TopEnd)
            .size(48.dp)
            .testTag("find_your_vybe_dismiss_${user.id}")
        ) {
          Box(
            modifier = Modifier
              .size(26.dp)
              .clip(CircleShape)
              .background(palette.card),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Dismiss ${user.displayName}",
              tint = palette.textSecondary,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }

      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        VybeAvatar(
          user = user,
          size = 60.dp,
          showPulseDot = true,
          onClick = onOpenProfile
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = user.displayName.ifBlank { user.username },
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          VerificationBadge(user = user, iconSize = 15.dp)
          if (user.isPrivate) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Private account",
              tint = palette.textSecondary,
              modifier = Modifier.size(12.dp)
            )
          }
        }

        Text(
          text = "@${user.username}",
          fontSize = 11.sp,
          fontWeight = FontWeight.Medium,
          color = palette.accent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = user.bio.trim().ifBlank {
            if (user.isPrivate) "Private VYBE profile" else "On VYBE • Tap to view profile"
          },
          fontSize = 11.sp,
          lineHeight = 15.sp,
          color = palette.textSecondary,
          textAlign = TextAlign.Center,
          maxLines = 2,
          minLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
          color = if (candidate.mutualUsers.isNotEmpty()) palette.accent.copy(alpha = 0.14f) else palette.card,
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          border = BorderStroke(
            1.dp,
            if (candidate.mutualUsers.isNotEmpty()) palette.accent.copy(alpha = 0.35f) else palette.border
          )
        ) {
          Text(
            text = candidate.discoveryReason,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (candidate.mutualUsers.isNotEmpty()) palette.accent else palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (hasIncomingRequest) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Button(
              onClick = { onRespondRequest(true) },
              colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .testTag("find_your_vybe_accept_${user.id}")
            ) {
              Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            OutlinedButton(
              onClick = { onRespondRequest(false) },
              border = BorderStroke(1.dp, palette.border),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("find_your_vybe_decline_${user.id}")
            ) {
              Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            }
          }
        } else {
          VybeFollowButton(
            isFollowing = isFollowing,
            isFollowRequested = isFollowRequested,
            followsMe = user.followsMe,
            targetUsername = user.username,
            compact = true,
            onClick = onToggleFollow,
            modifier = Modifier.fillMaxWidth(),
            testTag = "find_your_vybe_follow_${user.id}"
          )
        }
      }
    }
  }
}

@Composable
private fun FindYourVybeEmptyCard(
  title: String,
  subtitle: String,
  actionLabel: String,
  onActionClick: () -> Unit,
  palette: VybePalette
) {
  Surface(
    color = palette.elevated,
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    border = BorderStroke(1.dp, palette.border),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 16.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.weight(1f)
      ) {
        Box(
          modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.PersonSearch,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(20.dp)
          )
        }
        Column {
          Text(
            text = title,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary
          )
          Text(
            text = subtitle,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = palette.textSecondary
          )
        }
      }
      Spacer(modifier = Modifier.width(10.dp))
      VybeSecondaryButton(
        text = actionLabel,
        onClick = onActionClick
      )
    }
  }
}

@Composable
private fun FindYourVybeSeeAllDialog(
  initialTab: FindYourVybeTab,
  linkUpCandidates: List<VybeDiscoveryCandidate>,
  suggestCandidates: List<VybeDiscoveryCandidate>,
  followingUserIds: Set<String>,
  requestedFollowUserIds: Set<String>,
  incomingRequestIds: Set<String>,
  hasDismissedSuggestions: Boolean,
  onOpenProfile: (User) -> Unit,
  onToggleFollow: (String) -> Unit,
  onRespondRequest: (String, Boolean) -> Unit,
  onDismissSuggestion: (String) -> Unit,
  onRestoreDismissed: () -> Unit,
  onClose: () -> Unit
) {
  val palette = LocalVybePalette.current
  var activeTab by rememberSaveable { mutableStateOf(initialTab) }
  var filterQuery by rememberSaveable { mutableStateOf("") }

  Dialog(
    onDismissRequest = onClose,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    BackHandler { onClose() }

    Scaffold(
      containerColor = palette.canvas,
      contentWindowInsets = WindowInsets(0.dp),
      modifier = Modifier.testTag("find_your_vybe_see_all_dialog"),
      topBar = {
        VybeSubScreenTopBar(
          title = "Find Your Vybe",
          subtitle = "Link Up & Personalized Suggestions",
          onBackClick = onClose,
          backTestTag = "find_your_vybe_see_all_back",
          actions = {
            if (activeTab == FindYourVybeTab.SUGGEST && hasDismissedSuggestions) {
              TextButton(
                onClick = onRestoreDismissed,
                modifier = Modifier.heightIn(min = 48.dp)
              ) {
                Text(
                  "Reset Hidden",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = palette.accent
                )
              }
            }
          }
        )
      }
    ) { paddingValues ->
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .navigationBarsPadding()
      ) {
        Column(
          modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          VybeSearchBar(
            query = filterQuery,
            onQueryChange = { filterQuery = it },
            placeholder = "Filter by name, @username, or bio...",
            testTag = "find_your_vybe_search_input"
          )

          Spacer(modifier = Modifier.height(10.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            FindYourVybeTab.values().forEach { tab ->
              val selected = activeTab == tab
              Surface(
                color = if (selected) palette.accent else palette.elevated,
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                border = if (selected) null else BorderStroke(1.dp, palette.border),
                modifier = Modifier
                  .weight(1f)
                  .heightIn(min = 44.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
                  .clickable { activeTab = tab }
              ) {
                Box(
                  contentAlignment = Alignment.Center,
                  modifier = Modifier.padding(vertical = 10.dp)
                ) {
                  Text(
                    text = tab.title,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else palette.textSecondary
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(thickness = 0.8.dp, color = palette.border)

        val rawList = if (activeTab == FindYourVybeTab.LINK_UP) linkUpCandidates else suggestCandidates
        val cleanQuery = filterQuery.trim().lowercase()
        val displayedList = remember(rawList, cleanQuery) {
          if (cleanQuery.isBlank()) rawList
          else rawList.filter {
            it.user.displayName.lowercase().contains(cleanQuery) ||
              it.user.username.lowercase().contains(cleanQuery) ||
              it.user.bio.lowercase().contains(cleanQuery) ||
              it.discoveryReason.lowercase().contains(cleanQuery)
          }
        }

        LazyColumn(
          modifier = Modifier
            .widthIn(max = 640.dp)
            .fillMaxSize()
            .align(Alignment.CenterHorizontally),
          contentPadding = PaddingValues(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(displayedList, key = { "seeall_${activeTab.name}_${it.user.id}" }) { item ->
            val u = item.user
            val hasIncoming = incomingRequestIds.contains(u.id) || u.hasPendingFollowRequestForMe
            VybeSurfaceCard(
              onClick = { onOpenProfile(u) },
              contentPadding = PaddingValues(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
              ) {
                VybeAvatar(user = u, size = 50.dp, onClick = { onOpenProfile(u) })
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = u.displayName.ifBlank { u.username },
                      fontFamily = PlusJakartaSansFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 14.sp,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    VerificationBadge(user = u, iconSize = 15.dp)
                    if (u.isPrivate) {
                      Spacer(modifier = Modifier.width(4.dp))
                      Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private account",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(12.dp)
                      )
                    }
                  }
                  Text(
                    text = "@${u.username}",
                    fontSize = 12.sp,
                    color = palette.accent,
                    maxLines = 1
                  )
                  if (u.bio.isNotBlank()) {
                    Text(
                      text = u.bio,
                      fontSize = 12.sp,
                      color = palette.textSecondary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  Text(
                    text = item.discoveryReason,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.accentSecondary
                  )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (hasIncoming) {
                  Button(
                    onClick = { onRespondRequest(u.id, true) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    shape = RoundedCornerShape(VybeTokens.Radius.md),
                    modifier = Modifier.heightIn(min = 48.dp)
                  ) {
                    Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                  }
                } else {
                  VybeFollowButton(
                    isFollowing = followingUserIds.contains(u.id),
                    isFollowRequested = requestedFollowUserIds.contains(u.id) || u.isFollowRequestedByMe,
                    followsMe = u.followsMe,
                    targetUsername = u.username,
                    compact = true,
                    onClick = { onToggleFollow(u.id) }
                  )
                }
                if (activeTab == FindYourVybeTab.SUGGEST) {
                  IconButton(
                    onClick = { onDismissSuggestion(u.id) },
                    modifier = Modifier.size(48.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Dismiss ${u.displayName}",
                      tint = palette.textSecondary,
                      modifier = Modifier.size(18.dp)
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
