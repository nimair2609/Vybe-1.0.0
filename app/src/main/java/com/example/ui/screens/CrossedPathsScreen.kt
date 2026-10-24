package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.theme.*
import com.example.util.MediaUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossedPathsScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val context = LocalContext.current
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

  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = { viewModel.navigateBack() },
            modifier = Modifier
              .size(48.dp)
              .testTag("crossed_paths_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = {
          Column {
            Text(
              text = "Crossed Paths",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
            Text(
              text = "Consent-first real-world discovery",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          Button(
            onClick = {
              newLocation = ""
              newVenue = ""
              newDescription = ""
              pickedPhotoPath = null
              showCreateDialog = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .padding(end = 8.dp)
              .heightIn(min = 42.dp)
              .testTag("noticed_someone_button")
          ) {
            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Post Notice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    ) {
      // Consent & Safety Banner
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          shape = RoundedCornerShape(18.dp)
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = VybeCyan,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Consent-First Architecture",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "No background tracking or live GPS broadcast. Encounters expire in 72 hours. Both parties must consent before messaging unlocks.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
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
          FilterChip(
            selected = selectedTab == CrossedPathType.MISSED_CONNECTION,
            onClick = { selectedTab = CrossedPathType.MISSED_CONNECTION },
            label = { Text("Missed", fontSize = 12.sp) }
          )
          FilterChip(
            selected = selectedTab == CrossedPathType.VENUE_EVENT,
            onClick = { selectedTab = CrossedPathType.VENUE_EVENT },
            label = { Text("Venue / Events", fontSize = 12.sp) }
          )
          FilterChip(
            selected = selectedTab == CrossedPathType.BROAD_NEARBY,
            onClick = { selectedTab = CrossedPathType.BROAD_NEARBY },
            label = { Text("Area Nearby", fontSize = 12.sp) }
          )
        }
      }

      if (filteredEncounters.isEmpty()) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Default.Place, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(32.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("No active encounters in this category", fontWeight = FontWeight.Bold, fontSize = 14.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tap Post Notice above to share a respectful venue or area encounter.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      } else {
        items(filteredEncounters, key = { it.id }) { encounter ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 6.dp)
              .testTag("encounter_card_${encounter.id}"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                    tint = VybeMagenta,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = encounter.venueOrEventName ?: encounter.broadLocation,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                }
                Surface(
                  color = VybeViolet.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "Expires in ${encounter.expiresHoursRemaining}h",
                    fontSize = 10.sp,
                    color = VybeViolet,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = encounter.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = encounter.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 17.sp
              )

              if (encounter.photoUrl != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(10.dp))
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
                  Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp), tint = VybeBrandCyan)
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("View on Maps", fontSize = 12.sp, color = VybeBrandCyan, fontWeight = FontWeight.Bold)
                }

                if (encounter.hasSentRequest) {
                  Surface(
                    color = VybeOnlineGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                  ) {
                    Text(
                      text = "Request Pending",
                      color = VybeOnlineGreen,
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                  }
                } else {
                  Button(
                    onClick = { viewModel.sendCrossedPathRequest(encounter.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier
                      .heightIn(min = 40.dp)
                      .testTag("request_connection_${encounter.id}")
                  ) {
                    Text("Was That You?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
        modifier = Modifier
          .fillMaxSize()
          .imePadding(),
        topBar = {
          TopAppBar(
            navigationIcon = {
              IconButton(onClick = { showCreateDialog = false }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
              }
            },
            title = {
              Column {
                Text("Post Crossed Path", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Consent-First Discovery", fontSize = 11.sp, color = VybeMagenta)
              }
            },
            actions = {
              Button(
                onClick = { submitEncounter() },
                enabled = newDescription.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                  .padding(end = 8.dp)
                  .heightIn(min = 42.dp)
                  .testTag("submit_crossed_path_button")
              ) {
                Text("Publish", fontWeight = FontWeight.Bold, fontSize = 13.sp)
              }
            },
            colors = TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.background
            )
          )
        }
      ) { innerPadding ->
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
          item {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
              color = VybeMagenta.copy(alpha = 0.12f),
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, VybeMagenta.copy(alpha = 0.3f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = VybeMagenta, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = "Post a respectful, non-identifying description. No live tracking. Encounters expire automatically after 72 hours.",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurface,
                  lineHeight = 15.sp
                )
              }
            }
          }

          item {
            Text("Venue or Map Location", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
              onClick = { showMapPicker = true },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("open_crossed_path_map_picker")
            ) {
              Icon(Icons.Default.Map, contentDescription = null, tint = VybeBrandCyan, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (newVenue.isNotBlank()) "Change Map Location" else "Pick Venue on Google Maps",
                fontWeight = FontWeight.SemiBold
              )
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = newVenue,
              onValueChange = { newVenue = it },
              label = { Text("Venue / Landmark") },
              placeholder = { Text("Enter cafe, metro station, or venue") },
              leadingIcon = { Icon(Icons.Default.Place, contentDescription = null, tint = VybeMagenta) },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("encounter_venue_input"),
              shape = RoundedCornerShape(12.dp)
            )
          }

          item {
            OutlinedTextField(
              value = newLocation,
              onValueChange = { newLocation = it },
              label = { Text("Broad Area / City") },
              placeholder = { Text("Neighborhood or city") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("encounter_location_input"),
              shape = RoundedCornerShape(12.dp)
            )
          }

          item {
            OutlinedTextField(
              value = newDescription,
              onValueChange = { newDescription = it },
              label = { Text("Respectful Description") },
              placeholder = { Text("Describe the moment respectfully...") },
              minLines = 4,
              maxLines = 6,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("encounter_desc_input"),
              shape = RoundedCornerShape(14.dp)
            )
          }

          item {
            Text("Optional Venue Photo", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            if (pickedPhotoPath != null) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(150.dp)
                  .clip(RoundedCornerShape(12.dp))
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
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                    .size(36.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(16.dp))
                }
              }
            } else {
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attach Venue Photo", fontSize = 13.sp)
              }
            }
          }

          item {
            Button(
              onClick = { submitEncounter() },
              enabled = newDescription.isNotBlank(),
              colors = ButtonDefaults.buttonColors(containerColor = VybeMagenta),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("submit_crossed_path_large_button")
            ) {
              Text("Post Respectful Encounter", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
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
