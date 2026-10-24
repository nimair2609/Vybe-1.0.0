package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.remote.SupabaseConfig
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class SelectedLocationResult(
  val displayName: String,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val isCoordinatesVerified: Boolean = false
)

/**
 * Real Google Maps + FusedLocationProvider + Android Geocoder location picker.
 *
 * Rules enforced:
 * - Requests location permission ONLY when the user explicitly taps "Use Current Location".
 * - Never displays fake map results or pretends a manual label was GPS/Map verified when it was not.
 * - Keeps manual location entry available when location permission is denied or Maps API key is pending in Secrets.
 */
@Composable
fun LocationPickerDialog(
  initialLocationName: String? = null,
  onDismiss: () -> Unit,
  onLocationSelected: (SelectedLocationResult?) -> Unit
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isMapsKeyConfigured = remember { SupabaseConfig.isGoogleMapsConfigured() }

  var searchOrManualText by remember { mutableStateOf(initialLocationName.orEmpty()) }
  var selectedPin by remember { mutableStateOf<LatLng?>(null) }
  var resolvedAddressLabel by remember { mutableStateOf<String?>(null) }
  var geocodedCandidates by remember { mutableStateOf<List<SelectedLocationResult>>(emptyList()) }
  var isLocatingGps by remember { mutableStateOf(false) }
  var isSearchingPlaces by remember { mutableStateOf(false) }
  var statusBanner by remember { mutableStateOf<String?>(null) }

  val defaultCenter = remember { LatLng(19.0760, 72.8777) } // Default map viewport center before pin selection
  val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(defaultCenter, 11f)
  }

  fun fetchDeviceGpsLocation() {
    isLocatingGps = true
    statusBanner = null
    scope.launch {
      val latLng = getCurrentDeviceLocation(context)
      if (latLng != null) {
        selectedPin = latLng
        if (isMapsKeyConfigured) {
          try {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
          } catch (_: Exception) {}
        }
        val reverseLabel = reverseGeocodeLatLng(context, latLng.latitude, latLng.longitude)
          ?: String.format(Locale.US, "%.4f, %.4f", latLng.latitude, latLng.longitude)
        resolvedAddressLabel = reverseLabel
        searchOrManualText = reverseLabel
      } else {
        statusBanner = "Could not obtain GPS fix. Ensure device Location is turned on, or enter your location manually below."
      }
      isLocatingGps = false
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    if (granted) {
      fetchDeviceGpsLocation()
    } else {
      statusBanner = "Location permission denied. You can still type a manual location name below."
    }
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.95f)
        .wrapContentHeight()
        .padding(vertical = 20.dp),
      shape = RoundedCornerShape(22.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp)
      ) {
        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.LocationOn,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
              text = "Select Location",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(48.dp).testTag("close_location_picker")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close location picker")
          }
        }

        Spacer(Modifier.height(10.dp))

        // Search / Manual Input Field
        OutlinedTextField(
          value = searchOrManualText,
          onValueChange = {
            searchOrManualText = it
            // If user edits text manually after dropping a pin, mark manual unless they re-select a geocoded pin
            if (it != resolvedAddressLabel) {
              selectedPin = null
            }
          },
          placeholder = { Text("Search address, city, or enter manual place...") },
          singleLine = true,
          trailingIcon = {
            IconButton(
              onClick = {
                val q = searchOrManualText.trim()
                if (q.isNotBlank()) {
                  isSearchingPlaces = true
                  statusBanner = null
                  scope.launch {
                    val found = forwardGeocodeQuery(context, q)
                    geocodedCandidates = found
                    if (found.isEmpty()) {
                      statusBanner = "No geocoded coordinates found for \"$q\". You can still use it as an unverified manual location label."
                    } else {
                      val top = found.first()
                      if (top.latitude != null && top.longitude != null) {
                        val pos = LatLng(top.latitude, top.longitude)
                        selectedPin = pos
                        resolvedAddressLabel = top.displayName
                        if (isMapsKeyConfigured) {
                          try {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(pos, 14f))
                          } catch (_: Exception) {}
                        }
                      }
                    }
                    isSearchingPlaces = false
                  }
                }
              },
              modifier = Modifier.size(48.dp).testTag("search_location_button")
            ) {
              if (isSearchingPlaces) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
              } else {
                Icon(Icons.Default.Search, contentDescription = "Search place")
              }
            }
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("location_input_field")
        )

        Spacer(Modifier.height(10.dp))

        // Action to request GPS only on explicit user tap
        OutlinedButton(
          onClick = {
            val hasFine = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
              fetchDeviceGpsLocation()
            } else {
              locationPermissionLauncher.launch(
                arrayOf(
                  Manifest.permission.ACCESS_FINE_LOCATION,
                  Manifest.permission.ACCESS_COARSE_LOCATION
                )
              )
            }
          },
          enabled = !isLocatingGps,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("use_current_gps_button")
        ) {
          if (isLocatingGps) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Detecting GPS location...")
          } else {
            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Use Current Location")
          }
        }

        Spacer(Modifier.height(12.dp))

        // Interactive Google Map when GOOGLE_MAPS_API_KEY is configured; clear notice otherwise
        if (isMapsKeyConfigured) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(230.dp)
              .clip(RoundedCornerShape(16.dp))
          ) {
            GoogleMap(
              modifier = Modifier.fillMaxSize(),
              cameraPositionState = cameraPositionState,
              uiSettings = MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = false
              ),
              onMapClick = { tappedLatLng ->
                selectedPin = tappedLatLng
                scope.launch {
                  val label = reverseGeocodeLatLng(context, tappedLatLng.latitude, tappedLatLng.longitude)
                    ?: String.format(Locale.US, "%.4f, %.4f", tappedLatLng.latitude, tappedLatLng.longitude)
                  resolvedAddressLabel = label
                  searchOrManualText = label
                }
              }
            ) {
              selectedPin?.let { pin ->
                Marker(
                  state = MarkerState(position = pin),
                  title = resolvedAddressLabel ?: "Selected Pin"
                )
              }
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(
            text = "Tap anywhere on the map to drop a pin.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "Interactive map preview requires GOOGLE_MAPS_API_KEY in AI Studio Secrets. GPS lookup and manual location entry remain available.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        // Geocoder search candidates
        if (geocodedCandidates.isNotEmpty()) {
          Spacer(Modifier.height(10.dp))
          Text(
            text = "Matched Places",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
          )
          Spacer(Modifier.height(4.dp))
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 140.dp)
          ) {
            items(geocodedCandidates) { candidate ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .clickable {
                    searchOrManualText = candidate.displayName
                    resolvedAddressLabel = candidate.displayName
                    if (candidate.latitude != null && candidate.longitude != null) {
                      selectedPin = LatLng(candidate.latitude, candidate.longitude)
                    }
                  }
                  .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  Icons.Default.LocationOn,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = candidate.displayName,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f)
                )
              }
            }
          }
        }

        if (!statusBanner.isNullOrBlank()) {
          Spacer(Modifier.height(8.dp))
          Text(
            text = statusBanner!!,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Spacer(Modifier.height(16.dp))

        // Footer buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (!initialLocationName.isNullOrBlank()) {
            OutlinedButton(
              onClick = {
                onLocationSelected(null)
                onDismiss()
              },
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            ) {
              Text("Remove")
            }
          } else {
            OutlinedButton(
              onClick = onDismiss,
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            ) {
              Text("Cancel")
            }
          }

          Button(
            onClick = {
              val cleanText = searchOrManualText.trim()
              if (cleanText.isNotEmpty()) {
                val pin = selectedPin
                onLocationSelected(
                  SelectedLocationResult(
                    displayName = cleanText,
                    latitude = pin?.latitude,
                    longitude = pin?.longitude,
                    isCoordinatesVerified = pin != null
                  )
                )
              }
              onDismiss()
            },
            enabled = searchOrManualText.trim().isNotEmpty(),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("confirm_location_button")
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Confirm")
          }
        }
      }
    }
  }
}

@SuppressLint("MissingPermission")
private suspend fun getCurrentDeviceLocation(context: Context): LatLng? = withContext(Dispatchers.IO) {
  try {
    val client = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()
    suspendCancellableCoroutine { cont ->
      client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
        .addOnSuccessListener { loc ->
          if (cont.isActive) {
            if (loc != null) cont.resume(LatLng(loc.latitude, loc.longitude))
            else {
              client.lastLocation
                .addOnSuccessListener { last ->
                  if (cont.isActive) {
                    cont.resume(if (last != null) LatLng(last.latitude, last.longitude) else null)
                  }
                }
                .addOnFailureListener {
                  if (cont.isActive) cont.resume(null)
                }
            }
          }
        }
        .addOnFailureListener {
          if (cont.isActive) cont.resume(null)
        }
    }
  } catch (_: Exception) {
    null
  }
}

@Suppress("DEPRECATION")
private suspend fun reverseGeocodeLatLng(context: Context, lat: Double, lng: Double): String? =
  withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    try {
      val geocoder = Geocoder(context, Locale.getDefault())
      val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocation(lat, lng, 1) { list ->
            if (cont.isActive) cont.resume(list)
          }
        }
      } else {
        geocoder.getFromLocation(lat, lng, 1)
      }
      addresses?.firstOrNull()?.let { formatAddressConcise(it) }
    } catch (_: Exception) {
      null
    }
  }

@Suppress("DEPRECATION")
private suspend fun forwardGeocodeQuery(context: Context, query: String): List<SelectedLocationResult> =
  withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext emptyList()
    try {
      val geocoder = Geocoder(context, Locale.getDefault())
      val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { cont ->
          geocoder.getFromLocationName(query, 5) { list ->
            if (cont.isActive) cont.resume(list)
          }
        }
      } else {
        geocoder.getFromLocationName(query, 5)
      }
      addresses.orEmpty().mapNotNull { addr ->
        val label = formatAddressConcise(addr) ?: return@mapNotNull null
        SelectedLocationResult(
          displayName = label,
          latitude = if (addr.hasLatitude()) addr.latitude else null,
          longitude = if (addr.hasLongitude()) addr.longitude else null,
          isCoordinatesVerified = addr.hasLatitude() && addr.hasLongitude()
        )
      }.distinctBy { it.displayName }
    } catch (_: Exception) {
      emptyList()
    }
  }

private fun formatAddressConcise(address: Address): String? {
  val parts = listOfNotNull(
    address.subLocality ?: address.featureName?.takeIf { it.any { c -> c.isLetter() } },
    address.locality ?: address.subAdminArea,
    address.adminArea ?: address.countryName
  ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

  if (parts.isNotEmpty()) return parts.take(3).joinToString(", ")
  val line = address.getAddressLine(0)?.trim()
  return line?.takeIf { it.isNotBlank() }
}
