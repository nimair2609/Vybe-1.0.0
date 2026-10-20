package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VybeViewModel
import com.example.ui.theme.*

/**
 * 14.4 VYBE WHAT NEXT
 * Authentic discovery, cultural predictions, next-action community roadmaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatNextScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val predictions by viewModel.predictions.collectAsState()

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier.testTag("what_next_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text("VYBE WHAT NEXT", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Community Culture & Discovery Consensus", fontSize = 11.sp, color = VybeBrandCyan)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.background
        )
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = VybeViolet.copy(alpha = 0.15f)),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.4f))
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("🔮", fontSize = 28.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "What Next Consensus",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Predict and discover where music, cinema, technology, and city vibes are heading next. Vote with your intuition.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
              )
            }
          }
        }
      }

      item {
        Text("Active Cultural Predictions", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      }

      items(predictions, key = { it.id }) { pred ->
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Surface(
                color = VybeViolet.copy(alpha = 0.2f),
                shape = RoundedCornerShape(8.dp)
              ) {
                Text(
                  text = pred.category.uppercase(),
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  color = VybeViolet,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
              Text(
                text = "Closes in ${pred.closingTimeText}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = pred.title,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp,
              lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Voting Options
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              pred.options.forEach { opt ->
                val isSelected = pred.userSelectedOptionId == opt.id
                Surface(
                  color = if (isSelected) VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                  shape = RoundedCornerShape(10.dp),
                  border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, VybeViolet) else null,
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.votePrediction(pred.id, opt.id) }
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      RadioButton(
                        selected = isSelected,
                        onClick = { viewModel.votePrediction(pred.id, opt.id) }
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(opt.text, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }

                    if (pred.userSelectedOptionId != null) {
                      Text(
                        text = "${opt.percentage}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "${pred.totalVotes} VYBE members voted",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }
  }
}
