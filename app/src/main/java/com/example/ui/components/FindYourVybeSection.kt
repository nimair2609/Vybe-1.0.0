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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.example.ui.theme.VybeBrandCyan
import com.example.ui.theme.VybeBrandMagenta
import com.example.ui.theme.VybeBrandViolet

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

    // Consolidate real user records from directory + post/comment/story authors without duplicates
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

    // Build real co-interaction graph (comments on posts & @mentions) for genuine mutual connection signals
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
            if ((candidate.followsMe || followerIds.contains(candidate.id)) && vybeMatchIds.contains(candidate.id)) {
                // Direct mutual resonance
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

/**
 * Upgraded "Find Your Vybe" Home Section containing "Link Up" and "Suggest" tabs,
 * Neo-style animated transitions, Mutual Connections, Follow/Request/Dismiss actions,
 * and a "See All" full-directory modal.
 */
@Composable
fun FindYourVybeSection(
    viewModel: VybeViewModel,
    modifier: Modifier = Modifier
) {
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

    // Link Up: Incoming follow requests first + discoverable new/existing connections
    val incomingRequests = remember(allCandidates, incomingRequestIds) {
        allCandidates.filter { incomingRequestIds.contains(it.user.id) }
    }
    val linkUpCandidates = remember(allCandidates, followingUserIds, incomingRequestIds) {
        val unlinkedFirst = allCandidates.filter { !followingUserIds.contains(it.user.id) && !incomingRequestIds.contains(it.user.id) }
        val alreadyLinked = allCandidates.filter { followingUserIds.contains(it.user.id) && !incomingRequestIds.contains(it.user.id) }
        (incomingRequests + unlinkedFirst + alreadyLinked).distinctBy { it.user.id }
    }

    // Suggest: Personalized unlinked & non-dismissed recommendations (strictly deduplicated)
    val suggestCandidates = remember(allCandidates, followingUserIds, dismissedSuggestionIds) {
        allCandidates
            .filter { candidate ->
                !followingUserIds.contains(candidate.user.id) &&
                    !dismissedSuggestionIds.contains(candidate.user.id)
            }
            .distinctBy { it.user.id }
    }

    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerSurface = MaterialTheme.colorScheme.surface
    val cardBorderColor = if (isLightTheme) {
        VybeBrandViolet.copy(alpha = 0.24f)
    } else {
        VybeBrandCyan.copy(alpha = 0.28f)
    }
    val tabTrackColor = if (isLightTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    } else {
        Color(0xFF111827).copy(alpha = 0.85f)
    }

    Surface(
        color = containerSurface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("find_your_vybe_section")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            VybeBrandViolet.copy(alpha = if (isLightTheme) 0.06f else 0.14f),
                            VybeBrandCyan.copy(alpha = if (isLightTheme) 0.03f else 0.05f),
                            containerSurface
                        )
                    )
                )
                .padding(vertical = 16.dp)
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF00C6FF).copy(alpha = 0.22f),
                                        VybeBrandViolet.copy(alpha = 0.26f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (isLightTheme) VybeBrandViolet else VybeBrandCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Find Your Vybe",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (incomingRequests.isNotEmpty()) {
                                Surface(
                                    color = VybeBrandMagenta,
                                    shape = RoundedCornerShape(999.dp)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLightTheme) VybeBrandViolet else VybeBrandCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "See all people in Find Your Vybe",
                        tint = if (isLightTheme) VybeBrandViolet else VybeBrandCyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Separate Accessible Neo-Style Segmented Tabs: "Link Up" & "Suggest"
            Surface(
                color = tabTrackColor,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)),
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
                            targetValue = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(200),
                            label = "fyv_tab_text_${tab.name}"
                        )
                        val countBadge = if (tab == FindYourVybeTab.LINK_UP) {
                            linkUpCandidates.size
                        } else {
                            suggestCandidates.size
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF2563EB), VybeBrandViolet)
                                            )
                                        )
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
                                    imageVector = if (tab == FindYourVybeTab.LINK_UP) {
                                        Icons.Default.Group
                                    } else {
                                        Icons.Default.AutoAwesome
                                    },
                                    contentDescription = null,
                                    tint = pillTextColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = tab.title,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = pillTextColor
                                )
                                if (countBadge > 0) {
                                    Surface(
                                        color = if (isSelected) {
                                            Color.White.copy(alpha = 0.22f)
                                        } else {
                                            MaterialTheme.colorScheme.surface
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text(
                                            text = "$countBadge",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Smooth Neo-style horizontal transition between Link Up and Suggest
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
                                onActionClick = { viewModel.selectTab(MainTab.EXPLORE) }
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
                                }
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
    onOpenProfile: () -> Unit,
    onToggleFollow: () -> Unit,
    onRespondRequest: (Boolean) -> Unit,
    onDismiss: (() -> Unit)?
) {
    val user = candidate.user
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardBg = if (isLightTheme) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    } else {
        Color(0xFF131B2E).copy(alpha = 0.92f)
    }
    val borderBrush = Brush.linearGradient(
        listOf(
            VybeBrandCyan.copy(alpha = if (isLightTheme) 0.30f else 0.36f),
            VybeBrandViolet.copy(alpha = if (isLightTheme) 0.30f else 0.38f)
        )
    )

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, borderBrush),
        modifier = Modifier
            .width(208.dp)
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
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss ${user.displayName}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Text(
                    text = "@${user.username}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isLightTheme) VybeBrandViolet else VybeBrandCyan,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mutual connections or personalized discovery signal pill
                Surface(
                    color = if (candidate.mutualUsers.isNotEmpty()) {
                        VybeBrandViolet.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                    },
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(
                        0.7.dp,
                        if (candidate.mutualUsers.isNotEmpty()) {
                            VybeBrandViolet.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
                        }
                    )
                ) {
                    Text(
                        text = candidate.discoveryReason,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (candidate.mutualUsers.isNotEmpty()) {
                            if (isLightTheme) VybeBrandViolet else Color(0xFFC4B5FD)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
                            colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("find_your_vybe_accept_${user.id}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { onRespondRequest(false) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("find_your_vybe_decline_${user.id}")
                        ) {
                            Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
    onActionClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
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
                        .background(VybeBrandViolet.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonSearch,
                        contentDescription = null,
                        tint = VybeBrandViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
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
    var activeTab by rememberSaveable { mutableStateOf(initialTab) }
    var filterQuery by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BackHandler { onClose() }

        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .testTag("find_your_vybe_see_all_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("find_your_vybe_see_all_back")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                text = "Find Your Vybe",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Link Up & Personalized Suggestions",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (activeTab == FindYourVybeTab.SUGGEST && hasDismissedSuggestions) {
                        TextButton(
                            onClick = onRestoreDismissed,
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Reset Hidden", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Search Bar + Tab Switcher
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        placeholder = { Text("Filter by name, @username, or bio...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filterQuery.isNotBlank()) {
                                IconButton(onClick = { filterQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear filter")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("find_your_vybe_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FindYourVybeTab.values().forEach { tab ->
                            val selected = activeTab == tab
                            Surface(
                                color = if (selected) VybeBrandViolet else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { activeTab = tab }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = tab.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))

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
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProfile(u) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                VybeAvatar(user = u, size = 50.dp, onClick = { onOpenProfile(u) })
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = u.displayName.ifBlank { u.username },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        VerificationBadge(user = u, iconSize = 15.dp)
                                        if (u.isPrivate) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Private account",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "@${u.username}",
                                        fontSize = 12.sp,
                                        color = VybeBrandViolet,
                                        maxLines = 1
                                    )
                                    if (u.bio.isNotBlank()) {
                                        Text(
                                            text = u.bio,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = item.discoveryReason,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (hasIncoming) {
                                    Button(
                                        onClick = { onRespondRequest(u.id, true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = VybeBrandViolet),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
