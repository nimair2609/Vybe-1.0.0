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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Community
import com.example.data.model.CommunityPost
import com.example.data.model.User
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeAvatar
import com.example.ui.theme.*
import com.example.util.rememberRelativeTimestamp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityZoneScreen(viewModel: VybeViewModel) {
  val currentUser by viewModel.currentUser.collectAsState()
  val communities by viewModel.communities.collectAsState()
  val activeCommunity by viewModel.activeCommunity.collectAsState()
  val communityPosts by viewModel.communityPosts.collectAsState()

  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }
  var showCreateCommunityDialog by remember { mutableStateOf(false) }
  var showCreatePostDialog by remember { mutableStateOf(false) }
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

  val categories = listOf("All", "General", "Creatives", "Tech & Coding", "Music & Sound", "Wellness", "Gaming")

  val filteredCommunities = remember(communities, selectedCategory, searchQuery) {
    communities.filter { c ->
      (selectedCategory == "All" || c.category.equals(selectedCategory, ignoreCase = true)) &&
        (searchQuery.isBlank() || c.name.contains(searchQuery, ignoreCase = true) || c.description.contains(searchQuery, ignoreCase = true))
    }
  }

  Scaffold(
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
            modifier = Modifier.testTag("community_back_button")
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
              text = activeCommunity?.name ?: "Communities",
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        },
        actions = {
          if (activeCommunity == null) {
            IconButton(
              onClick = { showCreateCommunityDialog = true },
              modifier = Modifier.testTag("create_community_button")
            ) {
              Icon(Icons.Default.Add, contentDescription = "Create Community", tint = VybeViolet)
            }
          } else {
            IconButton(
              onClick = { showCreatePostDialog = true },
              modifier = Modifier.testTag("create_community_post_button")
            ) {
              Icon(Icons.Default.PostAdd, contentDescription = "New Post", tint = VybeViolet)
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    }
  ) { paddingValues ->
    if (activeCommunity != null) {
      // Community Feed Detail View
      val comm = activeCommunity!!
      val isCreator = currentUser?.id == comm.creatorId
      val filteredPosts = communityPosts.filter { it.communityId == comm.id }

      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Community Banner Header
        item(key = "comm_header_${comm.id}") {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VybeViolet.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(Icons.Outlined.Groups, contentDescription = null, tint = VybeViolet, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                  Text(
                    "${comm.membersCount} members • ${comm.category}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Button(
                  onClick = { viewModel.toggleJoinCommunity(comm.id) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (comm.isJoined) MaterialTheme.colorScheme.outlineVariant else VybeViolet
                  ),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                  Text(
                    text = if (comm.isJoined) "Joined" else "Join",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (comm.isJoined) MaterialTheme.colorScheme.onSurface else Color.White
                  )
                }
              }
              if (comm.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = comm.description,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 17.sp
                )
              }
            }
          }
        }

        item(key = "comm_posts_header") {
          Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Community Posts", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            TextButton(onClick = { showCreatePostDialog = true }) {
              Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = VybeViolet)
              Spacer(modifier = Modifier.width(4.dp))
              Text("Post", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VybeViolet)
            }
          }
        }

        if (filteredPosts.isEmpty()) {
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
            val canDelete = isAuthor || isCreator

            Card(
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth().testTag("community_post_${post.id}")
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  VybeAvatar(user = post.author, size = 38.dp)
                  Spacer(modifier = Modifier.width(10.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(post.author.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    val relTime = rememberRelativeTimestamp(post.createdAtIso, "Recently")
                    Text("@${post.author.username} • $relTime", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  if (canDelete) {
                    IconButton(
                      onClick = { viewModel.deleteCommunityPost(post.id) },
                      modifier = Modifier.size(32.dp).testTag("delete_comm_post_${post.id}")
                    ) {
                      Icon(Icons.Outlined.Delete, contentDescription = "Delete Post", tint = VybeDanger, modifier = Modifier.size(18.dp))
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(post.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (post.content.isNotBlank()) {
                  Spacer(modifier = Modifier.height(6.dp))
                  Text(
                    post.content,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
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
                      .height(200.dp)
                      .clip(RoundedCornerShape(10.dp))
                  )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable { viewModel.toggleLikeCommunityPost(post.id) }
                    ) {
                      Icon(
                        imageVector = if (post.isLikedByMe) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.isLikedByMe) VybeHeartbreakRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("${post.likesCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      modifier = Modifier.clickable {
                        activeCommentPostId = post.id
                        viewModel.fetchCommunityComments(post.id)
                      }
                    ) {
                      Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "Comments",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("${post.commentsCount}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        item(key = "search_and_categories") {
          Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search communities...", fontSize = 13.sp) },
              leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().testTag("community_search_input")
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
                  modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                  Text(
                    text = cat,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                  )
                }
              }
            }
          }
        }

        if (filteredCommunities.isEmpty()) {
          item(key = "no_communities_found") {
            Box(
              modifier = Modifier.fillMaxWidth().padding(40.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No communities found", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Create a new community to gather people around your vibe.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        } else {
          items(filteredCommunities, key = { it.id }) { comm ->
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
                    Text(comm.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                  onClick = { viewModel.toggleJoinCommunity(comm.id) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = if (comm.isJoined) MaterialTheme.colorScheme.outlineVariant else VybeViolet
                  ),
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                  modifier = Modifier.heightIn(min = 34.dp)
                ) {
                  Text(
                    text = if (comm.isJoined) "Joined" else "Join",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (comm.isJoined) MaterialTheme.colorScheme.onSurface else Color.White
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Community Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_comm_name_input")
          )
          OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth()
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = isPrivate, onCheckedChange = { isPrivate = it })
            Spacer(modifier = Modifier.width(6.dp))
            Text("Private community (invite only)", fontSize = 12.sp)
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

  // Dialog: Create Community Post
  if (showCreatePostDialog && activeCommunity != null) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showCreatePostDialog = false },
      title = { Text("New Post in ${activeCommunity?.name}", fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("create_comm_post_title_input")
          )
          OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            label = { Text("Content") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("create_comm_post_body_input")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (title.isNotBlank()) {
              viewModel.createCommunityPost(activeCommunity!!.id, title.trim(), body.trim(), null)
              showCreatePostDialog = false
            }
          },
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

  // Comments BottomSheet
  if (activeCommentPostId != null) {
    val postComments = viewModel.communityComments.collectAsState().value[activeCommentPostId].orEmpty()

    ModalBottomSheet(
      onDismissRequest = { activeCommentPostId = null },
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

        Box(modifier = Modifier.weight(1f, fill = false).heightIn(min = 100.dp, max = 340.dp)) {
          if (postComments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              Text("No comments yet. Start the conversation!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
              items(postComments, key = { it.id }) { c ->
                Row(modifier = Modifier.fillMaxWidth()) {
                  VybeAvatar(user = c.author, size = 32.dp)
                  Spacer(modifier = Modifier.width(8.dp))
                  Column {
                    Text(c.author.displayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(c.content, fontSize = 13.sp)
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = commentText,
            onValueChange = { commentText = it },
            placeholder = { Text("Write a comment...", fontSize = 12.sp) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.weight(1f).testTag("comm_comment_input")
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
            modifier = Modifier.size(44.dp).clip(CircleShape).background(VybeViolet)
          ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}
