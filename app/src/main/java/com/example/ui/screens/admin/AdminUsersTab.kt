package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.User
import com.example.data.model.UserRole
import com.example.ui.theme.*

@Composable
fun AdminUsersTab(
  users: List<User>,
  onSuspendUser: (String, Int, String) -> Unit,
  onUnsuspendUser: (String) -> Unit,
  onBanUser: (String, String) -> Unit,
  onUnbanUser: (String) -> Unit,
  onToggleFoundingBadge: (String) -> Unit,
  onToggleSpotlight: (String) -> Unit,
  onHandleDeletionRequest: (String, Boolean) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var filterRole by remember { mutableStateOf("ALL") }
  var selectedUserForSanction by remember { mutableStateOf<User?>(null) }
  var sanctionHours by remember { mutableStateOf("24") }
  var sanctionReason by remember { mutableStateOf("Community Guidelines violation") }

  val filteredUsers = remember(users, searchQuery, filterRole) {
    users.filter { user ->
      val matchesQuery = searchQuery.isBlank() ||
        user.displayName.contains(searchQuery, ignoreCase = true) ||
        user.username.contains(searchQuery, ignoreCase = true) ||
        user.publicVybeId.contains(searchQuery, ignoreCase = true) ||
        user.email.contains(searchQuery, ignoreCase = true)

      val matchesFilter = when (filterRole) {
        "CREATORS" -> user.role == UserRole.CREATOR || user.isFoundingCreator
        "VERIFIED" -> user.isVerified || user.isVip
        "SUSPENDED" -> user.isSuspended || user.isBanned
        else -> true
      }
      matchesQuery && matchesFilter
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_users_tab"),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Search & Filter Row
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      placeholder = { Text("Search by name, @username, VYBE ID, or email...") },
      leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
      trailingIcon = {
        if (searchQuery.isNotBlank()) {
          IconButton(onClick = { searchQuery = "" }) {
            Icon(Icons.Default.Clear, contentDescription = "Clear")
          }
        }
      },
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().testTag("admin_users_search_input")
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf("ALL", "CREATORS", "VERIFIED", "SUSPENDED").forEach { role ->
        FilterChip(
          selected = filterRole == role,
          onClick = { filterRole = role },
          label = { Text(role, fontSize = 11.sp) }
        )
      }
    }

    Text(
      "Showing ${filteredUsers.size} user records",
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (filteredUsers.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(Icons.Default.PersonSearch, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("No users found matching query.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      filteredUsers.forEach { user ->
        UserRecordCard(
          user = user,
          onOpenSanction = { selectedUserForSanction = user },
          onToggleFounding = {
            onToggleFoundingBadge(user.id)
            onShowSnackbar("Updated Founding Creator status for @${user.username}")
          },
          onToggleSpotlight = {
            onToggleSpotlight(user.id)
            onShowSnackbar("Toggled Creator Spotlight for @${user.username}")
          },
          onApproveDeletion = {
            onHandleDeletionRequest(user.id, true)
            onShowSnackbar("Account deletion approved and purged for @${user.username}")
          },
          onRejectDeletion = {
            onHandleDeletionRequest(user.id, false)
            onShowSnackbar("Deletion request dismissed for @${user.username}")
          }
        )
      }
    }
  }

  // User Sanction Bottom Sheet / Dialog
  if (selectedUserForSanction != null) {
    val target = selectedUserForSanction!!
    AlertDialog(
      onDismissRequest = { selectedUserForSanction = null },
      icon = { Icon(Icons.Default.Gavel, contentDescription = null, tint = VybeDanger) },
      title = { Text("Sanction User @${target.username}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Name: ${target.displayName}", fontSize = 12.sp)
          Text("Public ID: ${target.publicVybeId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

          HorizontalDivider()

          Text("Suspension Duration (Hours):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("24", "72", "168", "720").forEach { h ->
              FilterChip(
                selected = sanctionHours == h,
                onClick = { sanctionHours = h },
                label = { Text("${h}h", fontSize = 10.sp) },
                modifier = Modifier.weight(1f)
              )
            }
          }

          OutlinedTextField(
            value = sanctionReason,
            onValueChange = { sanctionReason = it },
            label = { Text("Official Reason / Policy Ref") },
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (target.isSuspended || target.isBanned) {
            Button(
              onClick = {
                onUnsuspendUser(target.id)
                onUnbanUser(target.id)
                selectedUserForSanction = null
                onShowSnackbar("Restored user @${target.username}")
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeOnlineGreen)
            ) {
              Text("Re-instate User")
            }
          } else {
            Button(
              onClick = {
                val hours = sanctionHours.toIntOrNull() ?: 24
                onSuspendUser(target.id, hours, sanctionReason)
                selectedUserForSanction = null
                onShowSnackbar("Suspended @${target.username} for ${hours}h")
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeDanger)
            ) {
              Text("Apply Suspension")
            }
          }
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedUserForSanction = null }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun UserRecordCard(
  user: User,
  onOpenSanction: () -> Unit,
  onToggleFounding: () -> Unit,
  onToggleSpotlight: () -> Unit,
  onApproveDeletion: () -> Unit,
  onRejectDeletion: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(
      1.dp,
      if (user.isSuspended || user.isBanned) VybeDanger.copy(alpha = 0.5f)
      else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    ),
    modifier = Modifier.fillMaxWidth().testTag("user_card_${user.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          AsyncImage(
            model = user.avatarUrl.ifBlank { "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&q=80" },
            contentDescription = "Avatar",
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
          )

          Spacer(modifier = Modifier.width(10.dp))

          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(user.displayName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
              if (user.isVerified) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Verified, contentDescription = "Verified", tint = VybeBrandBlue, modifier = Modifier.size(14.dp))
              }
            }
            Text("@${user.username} • ${user.publicVybeId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }

        // Badges
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (user.isFoundingCreator) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = VybeVerifiedGold.copy(alpha = 0.15f)
            ) {
              Text("FOUNDING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = VybeVerifiedGold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
          }
          if (user.isSuspended) {
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = VybeDanger.copy(alpha = 0.15f)
            ) {
              Text("SUSPENDED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = VybeDanger, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action Buttons Row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onToggleFounding,
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
          border = BorderStroke(1.dp, if (user.isFoundingCreator) VybeVerifiedGold else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
          Icon(Icons.Default.Star, contentDescription = null, tint = if (user.isFoundingCreator) VybeVerifiedGold else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (user.isFoundingCreator) "Revoke Badge" else "Grant Founding", fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.width(6.dp))

        OutlinedButton(
          onClick = onToggleSpotlight,
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(if (user.isCreatorSpotlight) "In Spotlight" else "Spotlight", fontSize = 10.sp)
        }

        Spacer(modifier = Modifier.width(6.dp))

        Button(
          onClick = onOpenSanction,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (user.isSuspended) VybeOnlineGreen else VybeDanger.copy(alpha = 0.85f)
          ),
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          modifier = Modifier.testTag("sanction_button_${user.id}")
        ) {
          Text(if (user.isSuspended) "Restore" else "Sanction", fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
      }
    }
  }
}
