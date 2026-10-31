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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
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
import com.example.data.model.Community
import com.example.data.model.CommunityMember
import com.example.data.model.CommunityPost
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.components.VybeLikeIcon
import com.example.ui.theme.*
import com.example.util.MediaUtils
import com.example.util.rememberRelativeTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityZoneScreen(viewModel: VybeViewModel) {
  val context = LocalContext.current
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

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = {
              if (activeCommunity != null) {
                viewModel.closeActiveCommunity()
              } else {
                viewModel.navigateBack()
              }
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("community_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Outlined.Groups,
              contentDescription = null,
              tint = VybeViolet,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = activeCommunity?.name ?: "Community Zone",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
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
            Icon(Icons.Outlined.Refresh, contentDescription = "Sync Community")
          }
          if (activeCommunity == null) {
            IconButton(
              onClick = { showCreateCommunityDialog = true },
              modifier = Modifier
                .size(48.dp)
                .testTag("create_community_button")
            ) {
              Icon(Icons.Outlined.Add, contentDescription = "Create Community", tint = VybeViolet)
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
                Icon(Icons.Outlined.PostAdd, contentDescription = "New Post", tint = VybeViolet)
              }
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VybeViolet.copy(alpha = 0.18f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Outlined.Groups, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (comm.isPrivate) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(
                        Icons.Outlined.Lock,
                        contentDescription = "Private community",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                      )
                    }
                  }
                  Text(
                    "${comm.membersCount} members • ${comm.category}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                      containerColor = if (isActiveMemberOrPending) MaterialTheme.colorScheme.outlineVariant else VybeViolet
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 40.dp)
                      .testTag("toggle_join_active_community_btn")
                  ) {
                    Text(
                      text = membershipLabel,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = if (isActiveMemberOrPending) MaterialTheme.colorScheme.onSurface else Color.White
                    )
                  }
                } else {
                  Surface(
                    color = VybeBrandCyan.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(10.dp)
                  ) {
                    Text(
                      text = "Founder",
                      color = VybeBrandCyan,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
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
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }

                if (canPost) {
                  Button(
                    onClick = { showCreatePostDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 44.dp)
                      .testTag("community_publish_post_cta")
                  ) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Publish Post", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
              .padding(top = 6.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Community Discussions", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            if (!canPost) {
              Text(
                text = if (comm.isPrivate) "Approval required to post" else "Join community to post",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        if (comm.isPrivate && !canPost) {
          item(key = "comm_private_locked") {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Private Community", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = if (comm.membershipStatus == "PENDING") {
                    "Your membership request is pending approval from a community admin."
                  } else {
                    "Request to join this community to view member discussions and publish posts."
                  },
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        } else if (filteredPosts.isEmpty()) {
          item(key = "comm_no_posts") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                "No posts in this community yet. Be the first to start a conversation!",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        } else {
          items(filteredPosts, key = { it.id }) { post ->
            val isAuthor = currentUser?.id == post.author.id
            val canDelete = isAuthor || isAdmin

            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                    Text(post.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    val relTime = rememberRelativeTimestamp(post.createdAtIso, "Recently")
                    val editedSuffix = if (post.isEdited) " • Edited" else ""
                    Text(
                      text = "@${post.author.username} • $relTime$editedSuffix",
                      fontSize = 11.sp,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  if (isAuthor) {
                    IconButton(
                      onClick = { editingPost = post },
                      modifier = Modifier
                        .size(44.dp)
                        .testTag("edit_comm_post_${post.id}")
                    ) {
                      Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit Post",
                        tint = VybeBrandCyan,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                  if (canDelete) {
                    IconButton(
                      onClick = { postToDelete = post },
                      modifier = Modifier
                        .size(44.dp)
                        .testTag("delete_comm_post_${post.id}")
                    ) {
                      Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete Post",
                        tint = VybeDanger,
                        modifier = Modifier.size(18.dp)
                      )
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(post.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (post.content.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    post.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                      .clip(RoundedCornerShape(10.dp))
                  )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(20.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                      .heightIn(min = 36.dp)
                      .clickable { viewModel.toggleLikeCommunityPost(post.id) }
                      .padding(vertical = 4.dp)
                      .testTag("like_comm_post_${post.id}")
                  ) {
                    VybeLikeIcon(
                      isLiked = post.isLikedByMe,
                      size = 18.dp,
                      inactiveTint = Color.White
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("${post.likesCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }

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
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("${post.commentsCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
          Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search communities...", fontSize = 13.sp) },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              singleLine = true,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("community_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(categories) { cat ->
                val isSel = selectedCategory == cat
                Surface(
                  color = if (isSel) VybeViolet else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedCategory = cat }
                ) {
                  Text(
                    text = cat,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                  )
                }
              }
            }
          }
        }

        if (filteredCommunities.isEmpty()) {
          item(key = "no_communities_found") {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No communities found", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Create a new community to gather people around your vibe.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                  onClick = { showCreateCommunityDialog = true },
                  colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Create Community", color = Color.White, fontWeight = FontWeight.Bold)
                }
              }
            }
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

            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(16.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.openCommunity(comm) }
                .testTag("community_card_${comm.id}")
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VybeViolet.copy(alpha = 0.15f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Outlined.Groups, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(26.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (comm.isPrivate) {
                      Spacer(modifier = Modifier.width(6.dp))
                      Icon(Icons.Outlined.Lock, contentDescription = "Private", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    }
                  }
                  Text(
                    text = "${comm.membersCount} members • ${comm.category}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (comm.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                      text = comm.description,
                      fontSize = 12.sp,
                      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                    containerColor = if (isJoinedOrPending) MaterialTheme.colorScheme.outlineVariant else VybeViolet
                  ),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                  modifier = Modifier.heightIn(min = 38.dp)
                ) {
                  Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isJoinedOrPending) MaterialTheme.colorScheme.onSurface else Color.White
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
      title = { Text("Create Community", fontWeight = FontWeight.Bold) },
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
            modifier = Modifier
              .fillMaxWidth()
              .testTag("create_comm_description_input")
          )
          Text("Category", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(selectableCategories) { option ->
              FilterChip(
                selected = cat == option,
                onClick = { cat = option },
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
            Text("Private community (admin approval required)", fontSize = 12.sp)
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Create", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateCommunityDialog = false }) {
          Text("Cancel")
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
      title = { Text("Publish in ${activeCommunity?.name}", fontWeight = FontWeight.Bold) },
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
              shape = RoundedCornerShape(10.dp)
            ) {
              Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(if (attachedImageUri == null) "Attach Photo" else "Change Photo", fontSize = 12.sp)
            }
            if (attachedImageUri != null) {
              TextButton(onClick = { attachedImageUri = null }) {
                Text("Remove", color = VybeDanger, fontSize = 12.sp)
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
                .clip(RoundedCornerShape(10.dp))
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Publish", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreatePostDialog = false }) {
          Text("Cancel")
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
      title = { Text("Edit Community Post", fontWeight = FontWeight.Bold) },
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
          colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
        ) {
          Text("Save Changes", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { editingPost = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // Dialog: Confirm Delete Community Post
  if (postToDelete != null) {
    val target = postToDelete!!
    AlertDialog(
      onDismissRequest = { postToDelete = null },
      title = { Text("Delete Community Post?", fontWeight = FontWeight.Bold) },
      text = { Text("This post and its comments will be permanently removed from ${activeCommunity?.name ?: "the community"}.") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteCommunityPost(target.id)
            postToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
        ) {
          Text("Delete", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { postToDelete = null }) {
          Text("Cancel")
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
      containerColor = MaterialTheme.colorScheme.surface
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
          fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (members.isEmpty()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Text("Loading community members...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
      dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .navigationBarsPadding()
          .imePadding()
      ) {
        Text("Comments (${postComments.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                      Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(rel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(c.content, fontSize = 13.sp)
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
            placeholder = { Text("Write a comment...", fontSize = 12.sp) },
            shape = RoundedCornerShape(20.dp),
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
              .background(VybeViolet)
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
  val isTargetCreator = member.role == "CREATOR"
  val isSelf = member.user.id == viewerUserId

  Surface(
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier.padding(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      VybeAvatar(user = member.user, size = 38.dp)
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(member.user.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
          text = "@${member.user.username} • ${if (member.status == "PENDING") "Pending Approval" else member.role}",
          fontSize = 11.sp,
          color = if (member.status == "PENDING") VybeBrandCyan else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      if (isViewerAdmin && !isTargetCreator && !isSelf) {
        if (member.status == "PENDING") {
          Button(
            onClick = onApprove,
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.heightIn(min = 34.dp)
          ) {
            Text("Approve", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.width(6.dp))
        } else if (isViewerCreator) {
          TextButton(onClick = onToggleAdminRole) {
            Text(
              text = if (member.role == "ADMIN") "Demote" else "Make Admin",
              fontSize = 11.sp,
              color = VybeBrandCyan
            )
          }
        }
        IconButton(
          onClick = onRemove,
          modifier = Modifier.size(38.dp)
        ) {
          Icon(
            Icons.Outlined.PersonRemove,
            contentDescription = "Remove member",
            tint = VybeDanger,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}
