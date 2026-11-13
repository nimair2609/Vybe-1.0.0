package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CommunityMember
import com.example.data.model.CommunityPost
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybeSearchBar
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.MediaUtils
import com.example.util.rememberRelativeTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityZoneScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
  val palette = LocalVybePalette.current
  val currentUser by viewModel.currentUser.collectAsState()
  val communities by viewModel.communities.collectAsState()
  val activeCommunity by viewModel.activeCommunity.collectAsState()
  val communityPosts by viewModel.communityPosts.collectAsState()
  val communityMembers by viewModel.communityMembers.collectAsState()

  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }
  var showCreateCommunityDialog by remember { mutableStateOf(false) }
  var showCreatePostDialog by remember { mutableStateOf(false) }
  var editingPost by remember { mutableStateOf<CommunityPost?>(null) }
  var postToDelete by remember { mutableStateOf<CommunityPost?>(null) }
  var showMembersSheet by remember { mutableStateOf(false) }
  var activeCommentPostId by remember { mutableStateOf<String?>(null) }
  var commentText by remember { mutableStateOf("") }

  BackHandler {
    if (activeCommunity != null) {
      viewModel.closeActiveCommunity()
    } else {
      viewModel.navigateBack()
    }
  }

  LaunchedEffect(Unit) {
    viewModel.fetchCommunities()
  }

  val categories = remember {
    listOf("All", "General", "Creatives", "Tech & Coding", "Music & Sound", "Wellness", "Gaming")
  }
  val selectableCategories = remember {
    listOf("General", "Creatives", "Tech & Coding", "Music & Sound", "Wellness", "Gaming")
  }

  val filteredCommunities = remember(communities, selectedCategory, searchQuery) {
    communities.filter { c ->
      (selectedCategory == "All" || c.category.equals(selectedCategory, ignoreCase = true)) &&
        (searchQuery.isBlank() || c.name.contains(searchQuery, ignoreCase = true) || c.description.contains(searchQuery, ignoreCase = true))
    }
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = activeCommunity?.name ?: "Community Zone",
        subtitle = if (activeCommunity != null) "${activeCommunity!!.membersCount} members • ${activeCommunity!!.category}" else "Interest circles & discussions",
        onBackClick = {
          if (activeCommunity != null) {
            viewModel.closeActiveCommunity()
          } else {
            viewModel.navigateBack()
          }
        },
        backTestTag = "community_back_button",
        actions = {
          IconButton(
            onClick = {
              val active = activeCommunity
              if (active != null) {
                viewModel.openCommunity(active)
              } else {
                viewModel.fetchCommunities()
              }
            },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Sync Community", tint = palette.textPrimary)
          }
          if (activeCommunity == null) {
            IconButton(
              onClick = { showCreateCommunityDialog = true },
              modifier = Modifier
                .size(48.dp)
                .testTag("create_community_button")
            ) {
              Icon(Icons.Outlined.Add, contentDescription = "Create Community", tint = palette.accent)
            }
          } else {
            val comm = activeCommunity!!
            val canPost = comm.isJoined || comm.creatorId == currentUser?.id
            if (canPost) {
              IconButton(
                onClick = { showCreatePostDialog = true },
                modifier = Modifier
                  .size(48.dp)
                  .testTag("create_community_post_button")
              ) {
                Icon(Icons.Outlined.PostAdd, contentDescription = "New Post", tint = palette.accent)
              }
            }
          }
        }
      )
    }
  ) { paddingValues ->
    if (activeCommunity != null) {
      val comm = communities.firstOrNull { it.id == activeCommunity!!.id } ?: activeCommunity!!
      val isCreator = currentUser?.id == comm.creatorId
      val isAdmin = isCreator || comm.myRole == "CREATOR" || comm.myRole == "ADMIN"
      val canPost = comm.isJoined || isCreator
      val members = communityMembers
      val pendingRequestsCount = members.count { it.status == "PENDING" }
      val filteredPosts = communityPosts.filter { it.communityId == comm.id }

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .navigationBarsPadding()
          .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Community Banner Header + Membership Controls
        item(key = "comm_header_${comm.id}") {
          VybeSurfaceCard(
            elevated = true,
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
                    .background(palette.accent.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Outlined.Groups, contentDescription = null, tint = palette.accent, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      comm.name,
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily,
                      fontSize = 16.sp,
                      color = palette.textPrimary
                    )
                    if (comm.isPrivate) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Private community",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                  Text(
                    "${comm.membersCount} members • ${comm.category}",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                  )
                }

                if (!isCreator) {
                  val membershipLabel = when {
                    comm.isJoined || comm.membershipStatus == "APPROVED" -> "Joined"
                    comm.membershipStatus == "PENDING" -> "Pending"
                    comm.isPrivate -> "Request"
                    else -> "Join"
                  }
                  val isActiveMemberOrPending = comm.isJoined || comm.membershipStatus == "APPROVED" || comm.membershipStatus == "PENDING"
                  Button(
                    onClick = { viewModel.toggleJoinCommunity(comm.id) },
                    colors = ButtonDefaults.buttonColors(
                      containerColor = if (isActiveMemberOrPending) palette.card else palette.accent,
                      contentColor = if (isActiveMemberOrPending) palette.textPrimary else Color.White
                    ),
                    border = if (isActiveMemberOrPending) BorderStroke(1.dp, palette.border) else null,
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 40.dp)
                      .testTag("toggle_join_active_community_btn")
                  ) {
                    Text(
                      text = membershipLabel,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily
                    )
                  }
                } else {
                  Surface(
                    color = palette.accentSecondary.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, palette.accentSecondary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(VybeTokens.Radius.pill)
                  ) {
                    Text(
                      text = "Founder",
                      color = palette.accentSecondary,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                  }
                }
              }

              if (comm.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = comm.description,
                  fontSize = 13.sp,
                  color = palette.textSecondary,
                  lineHeight = 18.sp
                )
              }

              Spacer(modifier = Modifier.height(12.dp))
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    viewModel.fetchCommunityMembers(comm.id)
                    showMembersSheet = true
                  },
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  border = BorderStroke(1.dp, palette.border),
                  colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = palette.card,
                    contentColor = palette.textPrimary
                  ),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("community_members_button")
                ) {
                  Icon(
                    imageVector = if (isAdmin) Icons.Outlined.ManageAccounts else Icons.Outlined.Group,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = if (isAdmin && pendingRequestsCount > 0) {
                      "Manage ($pendingRequestsCount Pending)"
                    } else if (isAdmin) {
                      "Manage Members"
                    } else {
                      "Members (${comm.membersCount})"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = PlusJakartaSansFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                if (canPost) {
                  Button(
                    onClick = { showCreatePostDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("community_publish_post_cta")
                  ) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish Post", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
                  }
                }
              }
            }
          }
        }

        item(key = "comm_posts_header") {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              "Community Discussions",
              fontWeight = FontWeight.Bold,
              fontFamily = PlusJakartaSansFontFamily,
              fontSize = 14.sp,
              color = palette.textPrimary
            )
            if (!canPost) {
              Text(
                text = if (comm.isPrivate) "Approval required to post" else "Join community to post",
                fontSize = 11.sp,
                color = palette.textSecondary
              )
            }
          }
        }

        if (comm.isPrivate && !canPost) {
          item(key = "comm_private_locked") {
            VybeEmptyState(
              icon = Icons.Outlined.Lock,
              title = "Private Community",
              subtitle = if (comm.membershipStatus == "PENDING") {
                "Your membership request is pending approval from a community admin."
              } else {
                "Request to join this community to view member discussions and publish posts."
              }
            )
          }
        } else if (filteredPosts.isEmpty()) {
          item(key = "comm_no_posts") {
            VybeEmptyState(
              icon = Icons.Outlined.Forum,
              title = "No posts yet",
              subtitle = "Be the first to start a conversation in ${comm.name}!"
            )
          }
        } else {
          items(filteredPosts, key = { it.id }) { post ->
            val isAuthor = currentUser?.id == post.author.id
            val canDelete = isAuthor || isAdmin

            VybeSurfaceCard(
              modifier = Modifier
                .fillMaxWidth()
                .testTag("community_post_${post.id}")
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(
                    user = post.author,
                    size = 38.dp,
                    onClick = { viewModel.openUserProfile(post.author) }
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      post.author.displayName,
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily,
                      fontSize = 13.sp,
                      color = palette.textPrimary
                    )
                    val relTime = rememberRelativeTimestamp(post.createdAtIso, "Recently")
                    val editedSuffix = if (post.isEdited) " • Edited" else ""
                    Text(
                      text = "@${post.author.username} • $relTime$editedSuffix",
                      fontSize = 11.sp,
                      color = palette.textSecondary
                    )
                  }
                  if (isAuthor) {
                    IconButton(
                      onClick = { editingPost = post },
                      modifier = Modifier
                        .size(48.dp)
                        .testTag("edit_comm_post_${post.id}")
                    ) {
                      Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit Post",
                        tint = palette.accentSecondary,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                  if (canDelete) {
                    IconButton(
                      onClick = { postToDelete = post },
                      modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_comm_post_${post.id}")
                    ) {
                      Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Post",
                        tint = palette.danger,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  post.title,
                  fontWeight = FontWeight.Bold,
                  fontFamily = PlusJakartaSansFontFamily,
                  fontSize = 15.sp,
                  color = palette.textPrimary
                )
                if (post.content.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    post.content,
                    fontSize = 13.sp,
                    color = palette.textSecondary,
                    lineHeight = 19.sp
                  )
                }

                if (!post.mediaUrl.isNullOrBlank()) {
                  Spacer(modifier = Modifier.height(10.dp))
                  AsyncImage(
                    model = post.mediaUrl,
                    contentDescription = "Post attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                      .fillMaxWidth()
                      .height(210.dp)
                      .clip(RoundedCornerShape(VybeTokens.Radius.md))
                  )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(20.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  com.example.ui.components.FingerHeartReaction(
                    isLiked = post.isLikedByMe,
                    count = post.likesCount,
                    onClick = { viewModel.toggleLikeCommunityPost(post.id) },
                    iconSize = 19.dp,
                    testTag = "like_comm_post_${post.id}"
                  )

                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .heightIn(min = 36.dp)
                      .clickable {
                        activeCommentPostId = post.id
                        viewModel.fetchCommunityComments(post.id)
                      }
                      .padding(vertical = 4.dp)
                      .testTag("comments_comm_post_${post.id}")
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.ChatBubbleOutline,
                      contentDescription = "Comments",
                      tint = palette.textSecondary,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("${post.commentsCount}", fontSize = 12.sp, color = palette.textSecondary)
                  }
                }
              }
            }
          }
        }
      }
    } else {
      // All Communities Discovery List
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .navigationBarsPadding()
          .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item(key = "search_and_categories") {
          Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            VybeSearchBar(
              query = searchQuery,
              onQueryChange = { searchQuery = it },
              placeholder = "Search communities...",
              testTag = "community_search_input"
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(categories) { cat ->
                val isSel = selectedCategory == cat
                Surface(
                  color = if (isSel) palette.accent else palette.elevated,
                  border = BorderStroke(1.dp, if (isSel) palette.accent else palette.border),
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  modifier = Modifier
                    .clip(RoundedCornerShape(VybeTokens.Radius.pill))
                    .clickable { selectedCategory = cat }
                ) {
                  Text(
                    text = cat,
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) Color.White else palette.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                  )
                }
              }
            }
          }
        }

        if (filteredCommunities.isEmpty()) {
          item(key = "no_communities_found") {
            VybeEmptyState(
              icon = Icons.Outlined.Groups,
              title = "No communities found",
              subtitle = "Create a new community to gather people around your vibe.",
              actionLabel = "Create Community",
              onAction = { showCreateCommunityDialog = true }
            )
          }
        } else {
          items(filteredCommunities, key = { it.id }) { comm ->
            val isCreator = currentUser?.id == comm.creatorId
            val label = when {
              isCreator -> "Founder"
              comm.isJoined || comm.membershipStatus == "APPROVED" -> "Joined"
              comm.membershipStatus == "PENDING" -> "Pending"
              comm.isPrivate -> "Request"
              else -> "Join"
            }
            val isJoinedOrPending = isCreator || comm.isJoined || comm.membershipStatus == "APPROVED" || comm.membershipStatus == "PENDING"

            VybeSurfaceCard(
              onClick = { viewModel.openCommunity(comm) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("community_card_${comm.id}")
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
                    .background(palette.accent.copy(alpha = 0.14f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Outlined.Groups, contentDescription = null, tint = palette.accent, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      comm.name,
                      fontWeight = FontWeight.Bold,
                      fontFamily = PlusJakartaSansFontFamily,
                      fontSize = 14.sp,
                      color = palette.textPrimary,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    if (comm.isPrivate) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(Icons.Outlined.Lock, contentDescription = "Private", tint = palette.textSecondary, modifier = Modifier.size(13.dp))
                    }
                  }
                  Text(
                    text = "${comm.membersCount} members • ${comm.category}",
                    fontSize = 11.sp,
                    color = palette.textSecondary
                  )
                  if (comm.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                      text = comm.description,
                      fontSize = 12.sp,
                      color = palette.textSecondary,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                  onClick = {
                    if (isCreator) {
                      viewModel.openCommunity(comm)
                    } else {
                      viewModel.toggleJoinCommunity(comm.id)
                    }
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (isJoinedOrPending) palette.elevated else palette.accent,
                    contentColor = if (isJoinedOrPending) palette.textPrimary else Color.White
                  ),
                  border = if (isJoinedOrPending) BorderStroke(1.dp, palette.border) else null,
                  shape = RoundedCornerShape(VybeTokens.Radius.pill),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.heightIn(min = 38.dp)
                ) {
                  Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFontFamily
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  // Dialog: Create Community
  if (showCreateCommunityDialog) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("General") }
    var isPrivate by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showCreateCommunityDialog = false },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Create Community",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Community Name") },
            singleLine = true,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_comm_name_input")
          )
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_comm_description_input")
          )
          Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(selectableCategories) { option ->
              FilterChip(
                selected = cat == option,
                onClick = { cat = option },
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = palette.accent,
                  selectedLabelColor = Color.White,
                  containerColor = palette.elevated,
                  labelColor = palette.textSecondary
                ),
                label = { Text(option, fontSize = 11.sp) }
              )
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { isPrivate = !isPrivate }
          ) {
            Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
            Spacer(modifier = Modifier.width(6.dp))
            Text("Private community (admin approval required)", fontSize = 12.sp, color = palette.textSecondary)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (name.isNotBlank()) {
              viewModel.createCommunity(name.trim(), description.trim(), cat, isPrivate)
              showCreateCommunityDialog = false
            }
          },
          enabled = name.isNotBlank(),
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
        ) {
          Text("Create", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateCommunityDialog = false }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // Dialog: Create Community Post (with optional photo attachment)
  if (showCreatePostDialog && activeCommunity != null) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<String?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
      if (uri != null) {
        attachedImageUri = MediaUtils.copyUriToLocalCache(context, uri, "community_post")
      }
    }

    AlertDialog(
      onDismissRequest = { showCreatePostDialog = false },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Publish in ${activeCommunity?.name}",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Post Title") },
            singleLine = true,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_comm_post_title_input")
          )
          OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Share thoughts with members...") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_comm_post_body_input")
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = {
                imagePickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              border = BorderStroke(1.dp, palette.border),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.textPrimary)
            ) {
              Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (attachedImageUri == null) "Attach Photo" else "Change Photo", fontSize = 12.sp)
            }
            if (attachedImageUri != null) {
              TextButton(onClick = { attachedImageUri = null }) {
                Text("Remove", color = palette.danger, fontSize = 12.sp)
              }
            }
          }
          if (attachedImageUri != null) {
            AsyncImage(
              model = attachedImageUri,
              contentDescription = "Selected attachment",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(VybeTokens.Radius.sm))
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (title.isNotBlank() || body.isNotBlank()) {
              val resolvedTitle = title.trim().ifBlank { body.trim().take(50) }
              viewModel.createCommunityPost(
                communityId = activeCommunity!!.id,
                title = resolvedTitle,
                content = body.trim(),
                mediaUri = attachedImageUri?.let { android.net.Uri.parse(it) }
              )
              showCreatePostDialog = false
            }
          },
          enabled = title.isNotBlank() || body.isNotBlank(),
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
        ) {
          Text("Publish", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreatePostDialog = false }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // Dialog: Edit Own Community Post
  if (editingPost != null) {
    val target = editingPost!!
    var editedTitle by remember(target.id) { mutableStateOf(target.title) }
    var editedBody by remember(target.id) { mutableStateOf(target.content) }

    AlertDialog(
      onDismissRequest = { editingPost = null },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Edit Community Post",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Column(
          modifier = Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedTextField(
            value = editedTitle,
            onValueChange = { editedTitle = it },
            label = { Text("Post Title") },
            singleLine = true,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_comm_post_title_input")
          )
          OutlinedTextField(
            value = editedBody,
            onValueChange = { editedBody = it },
            label = { Text("Content") },
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(VybeTokens.Radius.sm),
            colors = fieldColors,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("edit_comm_post_body_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (editedTitle.isNotBlank() || editedBody.isNotBlank()) {
              viewModel.editCommunityPost(
                postId = target.id,
                newTitle = editedTitle.trim().ifBlank { target.title },
                newContent = editedBody.trim()
              )
              editingPost = null
            }
          },
          enabled = editedTitle.isNotBlank() || editedBody.isNotBlank(),
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White)
        ) {
          Text("Save Changes", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { editingPost = null }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // Dialog: Confirm Delete Community Post
  if (postToDelete != null) {
    val target = postToDelete!!
    AlertDialog(
      onDismissRequest = { postToDelete = null },
      containerColor = palette.card,
      shape = RoundedCornerShape(VybeTokens.Radius.lg),
      title = {
        Text(
          "Delete Community Post?",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          color = palette.textPrimary
        )
      },
      text = {
        Text(
          "This post and its comments will be permanently removed from ${activeCommunity?.name ?: "the community"}.",
          color = palette.textSecondary
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteCommunityPost(target.id)
            postToDelete = null
          },
          shape = RoundedCornerShape(VybeTokens.Radius.pill),
          colors = ButtonDefaults.buttonColors(containerColor = palette.danger, contentColor = Color.White)
        ) {
          Text("Delete", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
        }
      },
      dismissButton = {
        TextButton(onClick = { postToDelete = null }) {
          Text("Cancel", color = palette.textSecondary, fontFamily = PlusJakartaSansFontFamily)
        }
      }
    )
  }

  // BottomSheet: Members & Membership Management
  if (showMembersSheet && activeCommunity != null) {
    val comm = communities.firstOrNull { it.id == activeCommunity!!.id } ?: activeCommunity!!
    val isCreator = currentUser?.id == comm.creatorId
    val isAdmin = isCreator || comm.myRole == "CREATOR" || comm.myRole == "ADMIN"
    val members = communityMembers

    ModalBottomSheet(
      onDismissRequest = { showMembersSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      contentWindowInsets = { WindowInsets(0.dp) },
      dragHandle = { BottomSheetDefaults.DragHandle(color = palette.border) },
      containerColor = palette.card,
      contentColor = palette.textPrimary
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .navigationBarsPadding()
          .imePadding()
      ) {
        Text(
          text = "Community Members (${members.size})",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 16.sp,
          color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (members.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Loading community members...", fontSize = 12.sp, color = palette.textSecondary)
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 380.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(members, key = { it.user.id }) { m ->
              CommunityMemberRow(
                member = m,
                isViewerCreator = isCreator,
                isViewerAdmin = isAdmin,
                viewerUserId = currentUser?.id.orEmpty(),
                onApprove = {
                  viewModel.updateCommunityMember(comm.id, m.user.id, role = null, status = "APPROVED")
                },
                onToggleAdminRole = {
                  val nextRole = if (m.role == "ADMIN") "MEMBER" else "ADMIN"
                  viewModel.updateCommunityMember(comm.id, m.user.id, role = nextRole, status = null)
                },
                onRemove = {
                  viewModel.removeCommunityMember(comm.id, m.user.id)
                }
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  // Comments BottomSheet (Keyboard-Safe)
  if (activeCommentPostId != null) {
    val postComments = viewModel.communityComments.collectAsState().value[activeCommentPostId].orEmpty()

    ModalBottomSheet(
      onDismissRequest = { activeCommentPostId = null },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      contentWindowInsets = { WindowInsets(0.dp) },
      dragHandle = { BottomSheetDefaults.DragHandle(color = palette.border) },
      containerColor = palette.card,
      contentColor = palette.textPrimary
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .navigationBarsPadding()
          .imePadding()
      ) {
        Text(
          "Comments (${postComments.size})",
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 15.sp,
          color = palette.textPrimary
        )
        Spacer(modifier = Modifier.height(10.dp))

        Box(
          modifier = Modifier
            .weight(1f, fill = false)
            .heightIn(min = 100.dp, max = 320.dp)
        ) {
          if (postComments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text(
                "No comments yet. Start the conversation!",
                fontSize = 12.sp,
                color = palette.textSecondary
              )
            }
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              items(postComments, key = { it.id }) { c ->
                val rel = rememberRelativeTimestamp(c.createdAtIso, "Just now")
                Row(modifier = Modifier.fillMaxWidth()) {
                  VybeAvatar(user = c.author, size = 32.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        c.author.displayName,
                        fontWeight = FontWeight.Bold,
                        fontFamily = PlusJakartaSansFontFamily,
                        fontSize = 12.sp,
                        color = palette.textPrimary
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(rel, fontSize = 10.sp, color = palette.textMuted)
                    }
                    Text(c.content, fontSize = 13.sp, color = palette.textSecondary)
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = { Text("Write a comment...", fontSize = 12.sp, color = palette.textMuted) },
            shape = RoundedCornerShape(VybeTokens.Radius.pill),
            colors = fieldColors,
            maxLines = 3,
            modifier = Modifier
              .weight(1f)
              .testTag("comm_comment_input")
          )
          Spacer(modifier = Modifier.width(8.dp))
          IconButton(
            onClick = {
              val clean = commentText.trim()
              if (clean.isNotBlank()) {
                viewModel.addCommunityComment(activeCommentPostId!!, clean)
                commentText = ""
              }
            },
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(palette.accent)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun CommunityMemberRow(
  member: CommunityMember,
  isViewerCreator: Boolean,
  isViewerAdmin: Boolean,
  viewerUserId: String,
  onApprove: () -> Unit,
  onToggleAdminRole: () -> Unit,
  onRemove: () -> Unit
) {
  val palette = LocalVybePalette.current
  val isTargetCreator = member.role == "CREATOR"
  val isSelf = member.user.id == viewerUserId

  Surface(
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    color = palette.elevated,
    border = BorderStroke(1.dp, palette.border),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VybeAvatar(user = member.user, size = 38.dp)
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          member.user.displayName,
          fontWeight = FontWeight.Bold,
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 13.sp,
          color = palette.textPrimary
        )
        Text(
          text = "@${member.user.username} • ${if (member.status == "PENDING") "Pending Approval" else member.role}",
          fontSize = 11.sp,
          color = if (member.status == "PENDING") palette.accentSecondary else palette.textSecondary
        )
      }

      if (isViewerAdmin && !isTargetCreator && !isSelf) {
        if (member.status == "PENDING") {
          Button(
            onClick = onApprove,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(VybeTokens.Radius.pill),
            modifier = Modifier.heightIn(min = 36.dp)
          ) {
            Text("Approve", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
          }
          Spacer(modifier = Modifier.width(6.dp))
        } else if (isViewerCreator) {
          TextButton(onClick = onToggleAdminRole) {
            Text(
              text = if (member.role == "ADMIN") "Demote" else "Make Admin",
              fontSize = 11.sp,
              fontWeight = FontWeight.SemiBold,
              color = palette.accentSecondary
            )
          }
        }
        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(40.dp)
        ) {
          Icon(
            Icons.Outlined.PersonRemove,
            contentDescription = "Remove member",
            tint = palette.danger,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
