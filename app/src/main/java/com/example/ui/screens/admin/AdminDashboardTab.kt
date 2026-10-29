package com.example.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AdminAuditLog
import com.example.data.model.AdminDashboardStats
import com.example.ui.theme.*

@Composable
fun AdminDashboardTab(
  stats: AdminDashboardStats,
  recentLogs: List<AdminAuditLog>,
  onNavigateToTab: (String) -> Unit
) {
  var selectedFilter by remember { mutableStateOf("7 Days") }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_dashboard_tab"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Platform Health Banner
    Card(
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      ),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
      modifier = Modifier.fillMaxWidth().testTag("admin_health_banner")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(VybeOnlineGreen)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "VYBE Cloud Platform Health",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = VybeOnlineGreen.copy(alpha = 0.15f)
          ) {
            Text(
              text = stats.backendStatus,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = VybeOnlineGreen,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Column {
            Text("Realtime WebSocket", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("<${stats.realtimeLatencyMs}ms Latency", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VybeBrandCyan)
          }
          Column {
            Text("API Error Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0.00% (Past 24h)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = VybeOnlineGreen)
          }
          Column {
            Text("Supabase Storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${String.format("%.1f", stats.storageUsageMb)} MB / 500 MB", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
        val progress = (stats.storageUsageMb / 500f).coerceIn(0f, 1f)
        LinearProgressIndicator(
          progress = { progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = VybeBrandBlue,
          trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
      }
    }

    // Filter Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text("Range:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
      listOf("Today", "7 Days", "30 Days", "All Time").forEach { filter ->
        FilterChip(
          selected = selectedFilter == filter,
          onClick = { selectedFilter = filter },
          label = { Text(filter, fontSize = 11.sp) },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = VybeBrandBlue.copy(alpha = 0.2f),
            selectedLabelColor = VybeBrandBlue
          )
        )
      }
    }

    // Metrics 2x4 Grid
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile(
          icon = Icons.Default.People,
          iconTint = VybeBrandBlue,
          title = "Total Registered Users",
          value = "${stats.totalUsers}",
          subtitle = "Active 24h: ${stats.activeUsers24h} | 7d: ${stats.activeUsers7d}",
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          icon = Icons.Default.Star,
          iconTint = VybeVerifiedGold,
          title = "Verified & Creators",
          value = "${stats.verifiedCreators}",
          subtitle = "Founding Badges active",
          modifier = Modifier.weight(1f)
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile(
          icon = Icons.Default.VideoLibrary,
          iconTint = VybeViolet,
          title = "Posts & Reels",
          value = "${stats.totalPosts}",
          subtitle = "${stats.totalReels} VYBES Reels in stream",
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          icon = Icons.Default.Groups,
          iconTint = VybeBrandCyan,
          title = "Stories & Communities",
          value = "${stats.totalStories} Stories",
          subtitle = "${stats.totalCommunities} Communities live",
          modifier = Modifier.weight(1f)
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricTile(
          icon = Icons.Default.Campaign,
          iconTint = VybeOnlineGreen,
          title = "Active Ad Campaigns",
          value = "${stats.activeAdCampaigns}",
          subtitle = "${stats.totalImpressionsCount / 1000}K Impr | ${stats.totalClicksCount} Clicks",
          modifier = Modifier.weight(1f)
        )
        MetricTile(
          icon = Icons.Default.Shield,
          iconTint = if (stats.pendingReports > 0) VybeDanger else VybeOnlineGreen,
          title = "Safety & Reports",
          value = "${stats.pendingReports}",
          subtitle = if (stats.pendingReports > 0) "Require review" else "Queue clean",
          modifier = Modifier.weight(1f)
        )
      }
    }

    // Interactive 7-Day Activity Chart
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
      modifier = Modifier.fillMaxWidth().testTag("admin_activity_chart")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Platform Engagement & Daily Traffic", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Text("Last 7 Days", fontSize = 11.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(14.dp))

        // Visual Canvas Bar Chart
        val barHeights = listOf(0.42f, 0.65f, 0.58f, 0.82f, 0.74f, 0.91f, 0.85f)
        val dayLabels = listOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")

        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
        ) {
          val barWidth = 24.dp.toPx()
          val spacing = (size.width - (barWidth * barHeights.size)) / (barHeights.size + 1)

          barHeights.forEachIndexed { i, ratio ->
            val left = spacing + i * (barWidth + spacing)
            val barHeight = size.height * ratio
            val top = size.height - barHeight

            drawRoundRect(
              brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF3B82F6), Color(0xFF00C6FF))
              ),
              topLeft = Offset(left, top),
              size = Size(barWidth, barHeight),
              cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          dayLabels.forEach { label ->
            Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }

    // Quick Action Bar
    Text("Quick Operations", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Button(
        onClick = { onNavigateToTab("BROADCAST") },
        colors = ButtonDefaults.buttonColors(containerColor = VybeBrandBlue),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).testTag("quick_action_broadcast")
      ) {
        Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Broadcast", fontSize = 12.sp)
      }

      OutlinedButton(
        onClick = { onNavigateToTab("MODERATION") },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, VybeDanger.copy(alpha = 0.5f)),
        modifier = Modifier.weight(1f).testTag("quick_action_reports")
      ) {
        Icon(Icons.Default.Shield, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Safety (${stats.pendingReports})", fontSize = 12.sp, color = VybeDanger)
      }

      OutlinedButton(
        onClick = { onNavigateToTab("ADVERTISEMENTS") },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).testTag("quick_action_ads")
      ) {
        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Campaigns", fontSize = 12.sp)
      }
    }

    // Recent Administrative Activity
    Text("Recent Admin Activity", fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (recentLogs.isEmpty()) {
          Text("No administrative actions recorded yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
          recentLogs.take(3).forEach { log ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(32.dp)
                  .clip(CircleShape)
                  .background(VybeBrandBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = VybeBrandBlue, modifier = Modifier.size(16.dp))
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(log.action, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                  Text(log.timestamp, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                  log.details,
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MetricTile(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  iconTint: Color,
  title: String,
  value: String,
  subtitle: String,
  modifier: Modifier = Modifier
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
      }
      Spacer(modifier = Modifier.height(6.dp))
      Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
      Spacer(modifier = Modifier.height(2.dp))
      Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}
