package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*

@Composable
fun CreatorStudioScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current
  val stats by viewModel.creatorStats.collectAsState()

  Scaffold(
    containerColor = palette.canvas,
    contentWindowInsets = WindowInsets(0.dp),
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Creator Studio",
        subtitle = "Growth insights & verified monetization",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "creator_studio_back_button"
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding()
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      contentPadding = PaddingValues(top = 14.dp, bottom = 72.dp)
    ) {
      // Monetization Summary Card
      item {
        VybeSurfaceCard(
          borderColor = palette.accent.copy(alpha = 0.45f),
          contentPadding = PaddingValues(18.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Estimated Revenue (30d)", fontSize = 13.sp, color = palette.textSecondary)
            Surface(
              color = palette.success.copy(alpha = 0.14f),
              shape = RoundedCornerShape(VybeTokens.Radius.xs)
            ) {
              Text(
                text = "VERIFIED CREATOR",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = palette.success,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
            text = "₹${stats.estimatedMonthlyEarningsInr}",
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.textPrimary
          )

          Spacer(modifier = Modifier.height(14.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            VybeSurfaceCard(
              containerColor = palette.elevated,
              cornerRadius = VybeTokens.Radius.md,
              contentPadding = PaddingValues(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Confirmed Payout", fontSize = 11.sp, color = palette.textSecondary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "₹${stats.confirmedPayoutInr}",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = palette.success
              )
            }

            VybeSurfaceCard(
              containerColor = palette.elevated,
              cornerRadius = VybeTokens.Radius.md,
              contentPadding = PaddingValues(12.dp),
              modifier = Modifier.weight(1f)
            ) {
              Text("Pending Clearance", fontSize = 11.sp, color = palette.textSecondary)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "₹${stats.pendingPayoutInr}",
                fontFamily = PlusJakartaSansFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = VybeVerifiedGold
              )
            }
          }
        }
      }

      // Performance Metrics Grid
      item {
        Text(
          text = "Content & Audience Analytics",
          fontFamily = PlusJakartaSansFontFamily,
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          color = palette.textPrimary
        )
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          VybeSurfaceCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Total Impressions", fontSize = 11.sp, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${stats.totalImpressions30d / 1000}K", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text("↑ +14.2% this month", fontSize = 10.sp, color = palette.success, fontWeight = FontWeight.SemiBold)
          }

          VybeSurfaceCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Engagement Rate", fontSize = 11.sp, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${stats.engagementRatePercent}%", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Top 5% among Indian creators", fontSize = 10.sp, color = palette.accent, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          VybeSurfaceCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Net LINKUP Gain", fontSize = 11.sp, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("+${stats.netFollowersGain30d}", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Verified real users", fontSize = 10.sp, color = palette.accentSecondary, fontWeight = FontWeight.SemiBold)
          }

          VybeSurfaceCard(
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier.weight(1f)
          ) {
            Text("Profile Visits", fontSize = 11.sp, color = palette.textSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("${stats.profileVisits30d}", fontFamily = PlusJakartaSansFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Organic discovery", fontSize = 10.sp, color = VybeBrandMagenta, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      // Brand Collaboration Readiness
      item {
        VybeSurfaceCard(
          containerColor = palette.elevated,
          contentPadding = PaddingValues(14.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Verified, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Brand Deals & Sponsorships",
              fontFamily = PlusJakartaSansFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = palette.textPrimary
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Status: ${stats.brandCollaborationReadiness}. Standardized Indian creator rate cards and direct in-app campaign contracts available for eligible verified creators.",
            fontSize = 12.sp,
            color = palette.textSecondary,
            lineHeight = 16.sp
          )
        }
      }
    }
  }
}
