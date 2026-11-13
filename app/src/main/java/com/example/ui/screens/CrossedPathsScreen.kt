package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.CrossedPathType
import com.example.ui.VybeViewModel
import com.example.ui.components.LocationPickerDialog
import com.example.ui.components.VybeEmptyState
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.*
import com.example.util.MediaUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossedPathsScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val context = LocalContext.current
  val palette = LocalVybePalette.current
  val encounters by viewModel.crossedPaths.collectAsState()
  var selectedTab by remember { mutableStateOf(CrossedPathType.MISSED_CONNECTION) }

  var showCreateDialog by remember { mutableStateOf(false) }
  var showMapPicker by remember { mutableStateOf(false) }
  var newLocation by remember { mutableStateOf("") }
  var newVenue by remember { mutableStateOf("") }
  var newDescription by remember { mutableStateOf("") }
  var pickedPhotoPath by remember { mutableStateOf<String?>(null) }

  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      pickedPhotoPath = MediaUtils.copyUriToLocalCache(context, uri, "crossed_path")
    }
  }

  val filteredEncounters = remember(encounters, selectedTab) {
    encounters.filter { it.type == selectedTab }
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
        title = "Crossed Paths",
        subtitle = "Consent-first real-world discovery",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "crossed_paths_back_button",
        actions = {
          Button(
            onClick = {
              newLocation = ""
              newVenue = ""
              newDescription = ""
              pickedPhotoPath = null
              showCreateDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
            shape = RoundedCornerShape(VybeTokens.Radius.pill),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier
              .padding(end = 4.dp)
              .heightIn(min = 40.dp)
              .testTag("noticed_someone_button")
          ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Post Notice", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
          }
        }
      )
    }
  ) { paddingValues ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding(),
      contentPadding = PaddingValues(bottom = 24.dp)
    ) {
      // Consent & Safety Banner
      item {
        VybeSurfaceCard(
          elevated = true,
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = palette.accentSecondary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Consent-First Architecture",
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 13.sp,
                color = palette.textPrimary
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "No background tracking or live GPS broadcast. Encounters expire in 72 hours. Both parties must consent before messaging unlocks.",
              fontSize = 11.sp,
              color = palette.textSecondary,
              lineHeight = 15.sp
            )
          }
        }
      }

      // Filter Tabs
      item {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          listOf(
            CrossedPathType.MISSED_CONNECTION to "Missed",
            CrossedPathType.VENUE_EVENT to "Venue / Events",
            CrossedPathType.BROAD_NEARBY to "Area Nearby"
          ).forEach { (tab, label) ->
            val selected = selectedTab == tab
            FilterChip(
              selected = selected,
              onClick = { selectedTab = tab },
              shape = RoundedCornerShape(VybeTokens.Radius.pill),
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = palette.accent,
                selectedLabelColor = Color.White,
                containerColor = palette.elevated,
                labelColor = palette.textSecondary
              ),
              border = FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = palette.border,
                selectedBorderColor = palette.accent
              ),
              label = { Text(label, fontSize = 12.sp, fontFamily = PlusJakartaSansFontFamily) }
            )
          }
        }
      }

      if (filteredEncounters.isEmpty()) {
        item {
          Box(modifier = Modifier.padding(16.dp)) {
            VybeEmptyState(
              icon = Icons.Default.Place,
              title = "No active encounters in this category",
              subtitle = "Tap Post Notice above to share a respectful venue or area encounter."
            )
          }
        }
      } else {
        items(filteredEncounters, key = { it.id }) { encounter ->
          VybeSurfaceCard(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .testTag("encounter_card_${encounter.id}")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                  Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = encounter.venueOrEventName ?: encounter.broadLocation,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSansFontFamily,
                    fontSize = 13.sp,
                    color = palette.textPrimary
                  )
                }
                Surface(
                  color = palette.accent.copy(alpha = 0.14f),
                  shape = RoundedCornerShape(VybeTokens.Radius.xs)
                ) {
                  Text(
                    text = "Expires in ${encounter.expiresHoursRemaining}h",
                    fontSize = 10.sp,
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = encounter.title,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSansFontFamily,
                fontSize = 15.sp,
                color = palette.textPrimary
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = encounter.description,
                fontSize = 12.sp,
                color = palette.textSecondary,
                lineHeight = 17.sp
              )

              if (encounter.photoUrl != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(VybeTokens.Radius.md))
                ) {
                  AsyncImage(
                    model = encounter.photoUrl,
                    contentDescription = "Encounter scene photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                TextButton(
                  onClick = {
                    val query = encounter.venueOrEventName ?: encounter.broadLocation
                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(query)}"))
                    try {
                      context.startActivity(mapIntent)
                    } catch (_: Exception) {
                      val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=${Uri.encode(query)}"))
                      context.startActivity(webIntent)
                    }
                  },
                  modifier = Modifier.testTag("open_maps_${encounter.id}")
                ) {
                  Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp), tint = palette.accentSecondary)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("View on Maps", fontSize = 12.sp, color = palette.accentSecondary, fontWeight = FontWeight.Bold)
                }

                if (encounter.hasSentRequest) {
                  Surface(
                    color = palette.success.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(VybeTokens.Radius.pill)
                  ) {
                    Text(
                      text = "Request Pending",
                      color = palette.success,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                  }
                } else {
                  Button(
                    onClick = { viewModel.sendCrossedPathRequest(encounter.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                    shape = RoundedCornerShape(VybeTokens.Radius.pill),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 40.dp)
                      .testTag("request_connection_${encounter.id}")
                  ) {
                    Text("Was That You?", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  // Create Encounter Full-Page Dialog with Real Google Maps Location Picker
  if (showCreateDialog) {
    fun submitEncounter() {
      if (newDescription.isBlank()) return
      viewModel.createCrossedPathEncounter(
        type = selectedTab,
        venueOrEventName = newVenue,
        broadLocation = newLocation,
        description = newDescription,
        photoUrl = pickedPhotoPath
      )
      showCreateDialog = false
      newDescription = ""
      newVenue = ""
      newLocation = ""
      pickedPhotoPath = null
    }

    BackHandler { showCreateDialog = false }
    Dialog(
      onDismissRequest = { showCreateDialog = false },
      properties = DialogProperties(
        usePlatformDefaultWidth = false,
        dismissOnBackPress = true
      )
    ) {
      Scaffold(
        containerColor = palette.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        modifier = Modifier
          .fillMaxSize()
          .imePadding(),
        topBar = {
          VybeSubScreenTopBar(
            title = "Post Crossed Path",
            subtitle = "Consent-First Discovery",
            onBackClick = { showCreateDialog = false },
            actions = {
              Button(
                onClick = { submitEncounter() },
                enabled = newDescription.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(VybeTokens.Radius.pill),
                modifier = Modifier
                  .padding(end = 4.dp)
                  .heightIn(min = 40.dp)
                  .testTag("submit_crossed_path_button")
              ) {
                Text("Publish", fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily, fontSize = 13.sp)
              }
            }
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
              color = palette.accent.copy(alpha = 0.12f),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Post a respectful, non-identifying description. No live tracking. Encounters expire automatically after 72 hours.",
                  fontSize = 11.sp,
                  color = palette.textPrimary,
                  lineHeight = 15.sp
                )
              }
            }
          }

          item {
            Text("Venue or Map Location", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            VybeSecondaryButton(
              text = if (newVenue.isNotBlank()) "Change Map Location" else "Pick Venue on Google Maps",
              icon = Icons.Default.Map,
              onClick = { showMapPicker = true },
              modifier = Modifier.fillMaxWidth().testTag("open_crossed_path_map_picker")
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = newVenue,
              onValueChange = { newVenue = it },
              label = { Text("Venue / Landmark") },
              placeholder = { Text("Enter cafe, metro station, or venue", color = palette.textMuted) },
              leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = palette.accent) },
              singleLine = true,
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("encounter_venue_input"),
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            OutlinedTextField(
              value = newLocation,
              onValueChange = { newLocation = it },
              label = { Text("Broad Area / City") },
              placeholder = { Text("Neighborhood or city", color = palette.textMuted) },
              singleLine = true,
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("encounter_location_input"),
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            OutlinedTextField(
              value = newDescription,
              onValueChange = { newDescription = it },
              label = { Text("Respectful Description") },
              placeholder = { Text("Describe the moment respectfully...", color = palette.textMuted) },
              minLines = 4,
              maxLines = 6,
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("encounter_desc_input"),
              shape = RoundedCornerShape(VybeTokens.Radius.md)
            )
          }

          item {
            Text("Optional Venue Photo", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSansFontFamily, color = palette.textPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            if (pickedPhotoPath != null) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(150.dp)
                  .clip(RoundedCornerShape(VybeTokens.Radius.md))
              ) {
                AsyncImage(
                  model = pickedPhotoPath,
                  contentDescription = "Selected Photo",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.fillMaxSize()
                )
                IconButton(
                  onClick = { pickedPhotoPath = null },
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(ObsidianCanvas.copy(alpha = 0.72f), CircleShape)
                    .size(36.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
              }
            } else {
              VybeSecondaryButton(
                text = "Attach Venue Photo",
                icon = Icons.Default.AddAPhoto,
                onClick = {
                  photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth()
              )
            }
          }

          item {
            VybePrimaryButton(
              text = "Post Respectful Encounter",
              onClick = { submitEncounter() },
              enabled = newDescription.isNotBlank(),
              modifier = Modifier.fillMaxWidth().testTag("submit_crossed_path_large_button")
            )
          }

          item {
            Spacer(modifier = Modifier.height(30.dp))
          }
        }
      }
    }
  }

  if (showMapPicker) {
    LocationPickerDialog(
      initialLocationName = newVenue.takeIf { it.isNotBlank() },
      onDismiss = { showMapPicker = false },
      onLocationSelected = { picked ->
        if (picked != null) {
          newVenue = picked.displayName
          if (newLocation.isBlank()) {
            newLocation = picked.displayName.substringAfter(", ", picked.displayName)
          }
        }
      }
    )
  }
}
