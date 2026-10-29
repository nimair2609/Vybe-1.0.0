package com.example.ui.screens.admin

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.AdCampaign
import com.example.data.model.AdCampaignPlacement
import com.example.data.model.AdCampaignStatus
import com.example.ui.theme.*

@Composable
fun AdminAdsTab(
  campaigns: List<AdCampaign>,
  onToggleStatus: (String) -> Unit,
  onArchive: (String) -> Unit,
  onCreateCampaign: (AdCampaign) -> Unit,
  onShowSnackbar: (String) -> Unit
) {
  val context = LocalContext.current
  var selectedFilter by remember { mutableStateOf("ALL") }
  var isCreateDialogOpen by remember { mutableStateOf(false) }

  // New campaign state
  var advertiserName by remember { mutableStateOf("") }
  var campaignTitle by remember { mutableStateOf("") }
  var selectedPlacement by remember { mutableStateOf(AdCampaignPlacement.FEED_NATIVE) }
  var creativeUrl by remember { mutableStateOf("") }
  var targetCtaUrl by remember { mutableStateOf("") }
  var ctaButtonText by remember { mutableStateOf("Learn More") }
  var dailyBudget by remember { mutableStateOf("1500") }
  var totalBudget by remember { mutableStateOf("25000") }

  val filteredCampaigns = remember(campaigns, selectedFilter) {
    when (selectedFilter) {
      "ACTIVE" -> campaigns.filter { it.status == AdCampaignStatus.ACTIVE }
      "PAUSED" -> campaigns.filter { it.status == AdCampaignStatus.PAUSED }
      "SCHEDULED" -> campaigns.filter { it.status == AdCampaignStatus.SCHEDULED }
      "ARCHIVED" -> campaigns.filter { it.status == AdCampaignStatus.ARCHIVED }
      else -> campaigns
    }
  }

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_ads_tab"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Summary Cards Header
    val totalImpressions = campaigns.sumOf { it.impressions }
    val totalClicks = campaigns.sumOf { it.clicks }
    val totalSpent = campaigns.sumOf { it.spentInr }
    val avgCtr = if (totalImpressions > 0) (totalClicks.toFloat() / totalImpressions.toFloat()) * 100f else 0f

    Card(
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("VYBE Ad & Sponsorship Network", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Button(
            onClick = { isCreateDialogOpen = true },
            colors = ButtonDefaults.buttonColors(containerColor = VybeBrandBlue),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("create_campaign_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Create Campaign", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Column {
            Text("Total Impressions", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${totalImpressions / 1000}K", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
          Column {
            Text("Total Clicks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$totalClicks", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
          Column {
            Text("Average CTR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${String.format("%.2f", avgCtr)}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = VybeOnlineGreen)
          }
          Column {
            Text("Total Spent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("₹$totalSpent", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }

    // Filter Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf("ALL", "ACTIVE", "PAUSED", "SCHEDULED", "ARCHIVED").forEach { filter ->
        FilterChip(
          selected = selectedFilter == filter,
          onClick = { selectedFilter = filter },
          label = { Text(filter, fontSize = 11.sp) }
        )
      }
    }

    // Campaign Cards
    if (filteredCampaigns.isEmpty()) {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(24.dp).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
          Spacer(modifier = Modifier.height(8.dp))
          Text("No campaigns match this filter.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      filteredCampaigns.forEach { campaign ->
        CampaignCard(
          campaign = campaign,
          onToggleStatus = { onToggleStatus(campaign.id) },
          onArchive = { onArchive(campaign.id) },
          onPreviewCta = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse(campaign.targetCtaUrl))
              context.startActivity(intent)
            } catch (_: Exception) {
              onShowSnackbar("Could not open URL: ${campaign.targetCtaUrl}")
            }
          }
        )
      }
    }
  }

  // Create Campaign Modal Dialog
  if (isCreateDialogOpen) {
    AlertDialog(
      onDismissRequest = { isCreateDialogOpen = false },
      title = { Text("Create Ad Campaign", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = advertiserName,
            onValueChange = { advertiserName = it },
            label = { Text("Advertiser / Brand Name") },
            modifier = Modifier.fillMaxWidth().testTag("input_ad_advertiser")
          )
          OutlinedTextField(
            value = campaignTitle,
            onValueChange = { campaignTitle = it },
            label = { Text("Campaign Headline") },
            modifier = Modifier.fillMaxWidth().testTag("input_ad_title")
          )
          OutlinedTextField(
            value = creativeUrl,
            onValueChange = { creativeUrl = it },
            label = { Text("Creative Image / Video URL") },
            modifier = Modifier.fillMaxWidth().testTag("input_ad_creative")
          )
          OutlinedTextField(
            value = targetCtaUrl,
            onValueChange = { targetCtaUrl = it },
            label = { Text("Destination CTA URL") },
            modifier = Modifier.fillMaxWidth().testTag("input_ad_cta_url")
          )

          Text("Placement:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AdCampaignPlacement.values().forEach { placement ->
              FilterChip(
                selected = selectedPlacement == placement,
                onClick = { selectedPlacement = placement },
                label = { Text(placement.name.take(6), fontSize = 9.sp) }
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (advertiserName.isBlank() || campaignTitle.isBlank()) {
              onShowSnackbar("Please enter advertiser name and title.")
              return@Button
            }
            val newCampaign = AdCampaign(
              id = "ad_${System.currentTimeMillis()}",
              advertiserName = advertiserName.trim(),
              campaignTitle = campaignTitle.trim(),
              placement = selectedPlacement,
              creativeUrl = creativeUrl.trim().ifBlank { "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800&q=80" },
              targetCtaUrl = targetCtaUrl.trim().ifBlank { "https://vybe.nrnext.in" },
              ctaButtonText = ctaButtonText.trim(),
              dailyBudgetInr = dailyBudget.toIntOrNull() ?: 1000,
              totalBudgetInr = totalBudget.toIntOrNull() ?: 10000,
              spentInr = 0,
              impressions = 0,
              clicks = 0,
              status = AdCampaignStatus.ACTIVE,
              startDateIso = "Today",
              endDateIso = "30 days"
            )
            onCreateCampaign(newCampaign)
            isCreateDialogOpen = false
            onShowSnackbar("Campaign '${newCampaign.campaignTitle}' created and activated!")
          },
          colors = ButtonDefaults.buttonColors(containerColor = VybeBrandBlue),
          modifier = Modifier.testTag("confirm_create_ad_button")
        ) {
          Text("Launch Campaign")
        }
      },
      dismissButton = {
        TextButton(onClick = { isCreateDialogOpen = false }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun CampaignCard(
  campaign: AdCampaign,
  onToggleStatus: () -> Unit,
  onArchive: () -> Unit,
  onPreviewCta: () -> Unit
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
    modifier = Modifier.fillMaxWidth().testTag("campaign_card_${campaign.id}")
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
          Text(campaign.advertiserName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(campaign.campaignTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text("Placement: ${campaign.placement.label}", fontSize = 11.sp, color = VybeBrandCyan)
        }

        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(campaign.status.colorHex).copy(alpha = 0.15f)
        ) {
          Text(
            text = campaign.status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(campaign.status.colorHex),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Thumbnail & Stats Row
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
          model = campaign.creativeUrl,
          contentDescription = "Ad Creative",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Impressions: ${campaign.impressions}", fontSize = 11.sp)
            Text("Clicks: ${campaign.clicks}", fontSize = 11.sp)
          }
          Spacer(modifier = Modifier.height(2.dp))
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("CTR: ${String.format("%.2f", campaign.ctrPercent)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VybeOnlineGreen)
            Text("Spent: ₹${campaign.spentInr} / ₹${campaign.totalBudgetInr}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Buttons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        TextButton(
          onClick = onPreviewCta,
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Preview CTA", fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.width(6.dp))

        OutlinedButton(
          onClick = onToggleStatus,
          shape = RoundedCornerShape(8.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
          val label = if (campaign.status == AdCampaignStatus.ACTIVE) "Pause" else "Resume"
          Text(label, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.width(6.dp))

        if (campaign.status != AdCampaignStatus.ARCHIVED) {
          IconButton(onClick = onArchive, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Archive, contentDescription = "Archive", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
          }
        }
      }
    }
  }
}
