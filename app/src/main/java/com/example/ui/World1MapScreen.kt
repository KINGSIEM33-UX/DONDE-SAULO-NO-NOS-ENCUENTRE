package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.MediaStorageHelper
import com.example.data.Outing
import com.example.data.OutingStatus
import com.example.data.RsvpChoice
import com.example.ui.theme.StatusCancelledOuter
import com.example.ui.theme.StatusCancelledRed
import com.example.ui.theme.StatusConfirmedGreen
import com.example.ui.theme.StatusConfirmedOuterRing
import com.example.ui.theme.StatusPlannedGray
import com.example.ui.theme.StatusPlannedOuter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

enum class MapPinMode {
    MARK_DESTINATION,
    MARK_MEETING_POINT
}

enum class InAppMapStyle(val label: String, val googleMapType: MapType) {
    GOOGLE_STANDARD("Estándar", MapType.NORMAL),
    HYBRID("Híbrido", MapType.HYBRID),
    SATELLITE("Satélite", MapType.SATELLITE),
    TERRAIN("Relieve", MapType.TERRAIN)
}

private data class PendingPinMove(
    val outing: Outing,
    val isDestination: Boolean,
    val lat: Double,
    val lng: Double,
    val label: String? = null
)

private fun openExternalGoogleMaps(context: Context, lat: Double, lng: Double, label: String) {
    try {
        val encodedLabel = Uri.encode(label)
        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($encodedLabel)")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo abrir Google Maps", Toast.LENGTH_SHORT).show()
    }
}

private fun openTurnByTurnDirections(context: Context, lat: Double, lng: Double) {
    try {
        val uri = Uri.parse("google.navigation:q=$lat,$lng")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            val webUri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    } catch (_: Exception) {
        Toast.makeText(context, "No se pudo iniciar la ruta en Google Maps", Toast.LENGTH_SHORT).show()
    }
}

@SuppressLint("MissingPermission")
@Composable
fun World1MapScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val outings by viewModel.outings.collectAsState()
    val selectedFromExternal by viewModel.selectedOutingForDetail.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()

    val hasValidMapsApiKey = remember {
        BuildConfig.MAPS_API_KEY.isNotBlank() &&
            !BuildConfig.MAPS_API_KEY.equals("MY_MAPS_API_KEY", ignoreCase = true)
    }

    var activeOutingId by remember(selectedFromExternal) {
        mutableStateOf(selectedFromExternal?.id ?: outings.firstOrNull()?.id)
    }
    val activeOuting = outings.firstOrNull { it.id == activeOutingId } ?: outings.firstOrNull()

    var showMiniScreen by remember(selectedFromExternal) {
        mutableStateOf(selectedFromExternal != null)
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchStatusText by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var pinMode by remember { mutableStateOf(MapPinMode.MARK_DESTINATION) }
    var mapStyle by remember { mutableStateOf(InAppMapStyle.GOOGLE_STANDARD) }
    var pendingPinMove by remember { mutableStateOf<PendingPinMove?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var userGpsLocation by remember { mutableStateOf<LatLng?>(null) }

    val initialLatLng = remember(activeOuting) {
        if (activeOuting != null) LatLng(activeOuting.destLat, activeOuting.destLng)
        else LatLng(40.4168, -3.7038)
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialLatLng, 14f)
    }

    // Smoothly pan camera whenever an outing is chosen externally or from chips
    LaunchedEffect(selectedFromExternal?.id) {
        selectedFromExternal?.let { target ->
            activeOutingId = target.id
            showMiniScreen = true
            if (hasValidMapsApiKey) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(target.destLat, target.destLng), 15f)
                )
            }
        }
    }

    fun fetchCurrentGpsAndCenter() {
        try {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    userGpsLocation = latLng
                    searchStatusText = "Ubicación GPS en vivo activada 📍"
                    if (hasValidMapsApiKey) {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(latLng, 15.5f))
                        }
                    }
                } else {
                    searchStatusText = "No se pudo obtener señal GPS ahora. Activa la ubicación del dispositivo."
                }
            }.addOnFailureListener {
                searchStatusText = "Error al leer GPS: ${it.localizedMessage}"
            }
        } catch (_: Exception) {
            searchStatusText = "No se pudo acceder al servicio de ubicación"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            fetchCurrentGpsAndCenter()
        } else {
            searchStatusText = "Permiso de ubicación denegado; usa el buscador de direcciones."
        }
    }

    fun executeRealPlaceSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        isSearching = true
        searchStatusText = "Buscando '$clean'..."
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    if (!Geocoder.isPresent()) return@withContext null
                    @Suppress("DEPRECATION")
                    val matches = Geocoder(context, Locale.getDefault()).getFromLocationName(clean, 1)
                    matches?.firstOrNull()?.let { addr ->
                        val label = addr.getAddressLine(0)
                            ?: listOfNotNull(addr.featureName, addr.locality).joinToString(", ").ifBlank { clean }
                        Triple(label, addr.latitude, addr.longitude)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            isSearching = false
            if (result == null) {
                searchStatusText = "No se encontró '$clean'. Prueba con ciudad o dirección completa."
                return@launch
            }
            val (label, lat, lng) = result
            if (hasValidMapsApiKey) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15.5f))
            }
            val target = activeOuting
            if (target != null) {
                pendingPinMove = PendingPinMove(
                    outing = target,
                    isDestination = pinMode == MapPinMode.MARK_DESTINATION,
                    lat = lat,
                    lng = lng,
                    label = label
                )
            } else {
                searchStatusText = "Encontrado: $label (${"%.4f".format(lat)}, ${"%.4f".format(lng)})"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("in_app_live_map_screen")
    ) {
        if (hasValidMapsApiKey) {
            // --- REAL EMBEDDED GOOGLE MAPS COMPOSE ---
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapStyle.googleMapType,
                    isMyLocationEnabled = hasLocationPermission
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = true,
                    mapToolbarEnabled = false
                ),
                onMapLongClick = { tappedLatLng ->
                    val target = activeOuting
                    if (target != null) {
                        pendingPinMove = PendingPinMove(
                            outing = target,
                            isDestination = pinMode == MapPinMode.MARK_DESTINATION,
                            lat = tappedLatLng.latitude,
                            lng = tappedLatLng.longitude
                        )
                    }
                }
            ) {
                // Route dashed line between meeting point and destination for active outing
                activeOuting?.let { active ->
                    Polyline(
                        points = listOf(
                            LatLng(active.meetLat, active.meetLng),
                            LatLng(active.destLat, active.destLng)
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        width = 8f,
                        pattern = listOf(Dash(20f), Gap(12f))
                    )
                }

                // Outing Destination + Meeting Point markers
                outings.forEach { outing ->
                    val isSelected = activeOuting?.id == outing.id
                    val ringColor = when (outing.outingStatus) {
                        OutingStatus.CONFIRMED -> StatusConfirmedGreen
                        OutingStatus.PLANNED -> StatusPlannedGray
                        OutingStatus.CANCELLED -> StatusCancelledRed
                    }
                    val outerRing = when (outing.outingStatus) {
                        OutingStatus.CONFIRMED -> StatusConfirmedOuterRing
                        OutingStatus.PLANNED -> StatusPlannedOuter
                        OutingStatus.CANCELLED -> StatusCancelledOuter
                    }

                    // Destination Pin with Circular Photo
                    MarkerComposable(
                        keys = arrayOf<Any>(outing.id, outing.destLat, outing.destLng, outing.status, outing.placePhotoUrl, isSelected),
                        state = MarkerState(position = LatLng(outing.destLat, outing.destLng)),
                        onClick = {
                            activeOutingId = outing.id
                            showMiniScreen = true
                            false
                        }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 62.dp else 52.dp)
                                    .shadow(if (isSelected) 8.dp else 4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(outerRing)
                                    .border(
                                        width = if (outing.outingStatus == OutingStatus.CONFIRMED) 3.dp else 2.dp,
                                        color = ringColor,
                                        shape = CircleShape
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                PlacePhotoThumbnail(
                                    photoKey = outing.placePhotoUrl,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                                shadowElevation = 3.dp,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = outing.title.take(18),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Meeting Point Pin
                    if (outing.meetingPoint.isNotBlank()) {
                        MarkerComposable(
                            keys = arrayOf<Any>("meet_${outing.id}", outing.meetLat, outing.meetLng, isSelected),
                            state = MarkerState(position = LatLng(outing.meetLat, outing.meetLng)),
                            onClick = {
                                activeOutingId = outing.id
                                showMiniScreen = true
                                false
                            }
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary,
                                border = BorderStroke(2.dp, Color.White),
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🤝", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // --- GOOGLE MAPS COMPANION MODE (WHEN MAPS_API_KEY IS NOT SET YET) ---
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 186.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 220.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = "Google Maps SDK integrado y listo",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                text = "Para activar el mapa interactivo incrustado, añade tu clave real MAPS_API_KEY en el archivo .env y recompila. Mientras tanto, puedes buscar direcciones reales (Geocoder), usar GPS y abrir cualquier punto directamente en Google Maps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                            userGpsLocation?.let { gps ->
                                Text(
                                    text = "📍 Tu GPS actual: ${"%.5f".format(gps.latitude)}, ${"%.5f".format(gps.longitude)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                items(outings, key = { it.id }) { outing ->
                    val statusColor = when (outing.outingStatus) {
                        OutingStatus.CONFIRMED -> StatusConfirmedGreen
                        OutingStatus.PLANNED -> StatusPlannedGray
                        OutingStatus.CANCELLED -> StatusCancelledRed
                    }
                    Card(
                        onClick = {
                            activeOutingId = outing.id
                            showMiniScreen = true
                        },
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(if (activeOuting?.id == outing.id) 2.dp else 1.dp, statusColor),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                PlacePhotoThumbnail(
                                    photoKey = outing.placePhotoUrl,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, statusColor, CircleShape)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(outing.title, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("📍 ${outing.location}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (outing.meetingPoint.isNotBlank()) {
                                        Text("🤝 Encuentro: ${outing.meetingPoint}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    Text(
                                        text = "Coordenadas: ${"%.4f".format(outing.destLat)}, ${"%.4f".format(outing.destLng)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { openExternalGoogleMaps(context, outing.destLat, outing.destLng, outing.location) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Ver en Maps", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { openTurnByTurnDirections(context, outing.destLat, outing.destLng) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Cómo llegar", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 2. TOP OVERLAY: SEARCH BAR, PIN MODE TOGGLE & OUTING CHIPS ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar lugar o dirección real...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Buscar lugar")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executeRealPlaceSearch(searchQuery) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_place_input")
                    )
                    Button(
                        onClick = { executeRealPlaceSearch(searchQuery) },
                        enabled = searchQuery.isNotBlank() && !isSearching,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.height(42.dp).testTag("map_search_submit_btn")
                    ) {
                        Text(if (isSearching) "..." else "Ir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Mode Toggle: Destination vs Meeting Point
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { pinMode = MapPinMode.MARK_DESTINATION },
                    shape = RoundedCornerShape(16.dp),
                    color = if (pinMode == MapPinMode.MARK_DESTINATION) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    },
                    contentColor = if (pinMode == MapPinMode.MARK_DESTINATION) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shadowElevation = 4.dp,
                    modifier = Modifier.weight(1f).height(42.dp).testTag("mode_mark_destination_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Marcar Destino", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    onClick = { pinMode = MapPinMode.MARK_MEETING_POINT },
                    shape = RoundedCornerShape(16.dp),
                    color = if (pinMode == MapPinMode.MARK_MEETING_POINT) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    },
                    contentColor = if (pinMode == MapPinMode.MARK_MEETING_POINT) {
                        MaterialTheme.colorScheme.onSecondary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shadowElevation = 4.dp,
                    modifier = Modifier.weight(1f).height(42.dp).testTag("mode_mark_meeting_point_btn")
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Punto Encuentro", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Outing quick-jump selector chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(outings, key = { it.id }) { outing ->
                    val isSel = activeOuting?.id == outing.id
                    val dotColor = when (outing.outingStatus) {
                        OutingStatus.CONFIRMED -> StatusConfirmedGreen
                        OutingStatus.PLANNED -> StatusPlannedGray
                        OutingStatus.CANCELLED -> StatusCancelledRed
                    }
                    Surface(
                        onClick = {
                            activeOutingId = outing.id
                            showMiniScreen = true
                            if (hasValidMapsApiKey) {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(LatLng(outing.destLat, outing.destLng), 15f)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.93f),
                        border = BorderStroke(1.5.dp, if (isSel) dotColor else Color.Transparent),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dotColor))
                            Text(
                                text = outing.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            if (searchStatusText != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = searchStatusText!!,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { searchStatusText = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Ocultar aviso",
                                tint = MaterialTheme.colorScheme.inverseOnSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. RIGHT FLOATING MAP CONTROLS ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasValidMapsApiKey) {
                Surface(
                    onClick = {
                        val all = InAppMapStyle.entries
                        mapStyle = all[(all.indexOf(mapStyle) + 1) % all.size]
                        searchStatusText = "Vista de mapa: ${mapStyle.label}"
                    },
                    shape = CircleShape,
                    shadowElevation = 5.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Layers, contentDescription = "Cambiar capa del mapa")
                    }
                }
            }

            Surface(
                onClick = {
                    if (hasLocationPermission) {
                        fetchCurrentGpsAndCenter()
                    } else {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                shape = CircleShape,
                shadowElevation = 5.dp,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Mi ubicación GPS",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (hasValidMapsApiKey) {
                Surface(
                    onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) } },
                    shape = CircleShape,
                    shadowElevation = 5.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Acercar")
                    }
                }

                Surface(
                    onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) } },
                    shape = CircleShape,
                    shadowElevation = 5.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Remove, contentDescription = "Alejar")
                    }
                }
            }
        }

        // --- 4. BOTTOM MINI-SCREEN CARD ---
        if (showMiniScreen && activeOuting != null) {
            val selected = activeOuting
            val statusColor = when (selected.outingStatus) {
                OutingStatus.CONFIRMED -> StatusConfirmedGreen
                OutingStatus.PLANNED -> StatusPlannedGray
                OutingStatus.CANCELLED -> StatusCancelledRed
            }
            val userRsvp = selected.rsvpChoiceFor(currentUserId)

            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, statusColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .testTag("map_outing_mini_screen")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            PlacePhotoThumbnail(
                                photoKey = selected.placePhotoUrl,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, statusColor, CircleShape)
                            )
                            Column {
                                Text(
                                    text = selected.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selected.dateIso} • ${selected.time} • ${selected.outingStatus.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                showMiniScreen = false
                                viewModel.selectedOutingForDetail.value = null
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar mini pantalla")
                        }
                    }

                    Text(
                        text = "📍 Destino: ${selected.location}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (selected.meetingPoint.isNotBlank()) {
                        Text(
                            text = "🤝 Punto de encuentro: ${selected.meetingPoint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "💶 Llevar: ${selected.budgetPerPerson}  |  🎒 ${selected.kitItems.ifBlank { "Sin materiales extra" }}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    HorizontalDivider()

                    // Quick RSVP controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { viewModel.updateOutingRsvp(selected.id, RsvpChoice.YES) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userRsvp == RsvpChoice.YES) StatusConfirmedGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (userRsvp == RsvpChoice.YES) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Asistiré (${selected.rsvpYesCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Button(
                            onClick = { viewModel.updateOutingRsvp(selected.id, RsvpChoice.MAYBE) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userRsvp == RsvpChoice.MAYBE) StatusPlannedGray else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (userRsvp == RsvpChoice.MAYBE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("Duda (${selected.rsvpMaybeCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                        Button(
                            onClick = { viewModel.updateOutingRsvp(selected.id, RsvpChoice.NO) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (userRsvp == RsvpChoice.NO) StatusCancelledRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (userRsvp == RsvpChoice.NO) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text("No (${selected.rsvpNoCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openTurnByTurnDirections(context, selected.destLat, selected.destLng) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cómo llegar", fontSize = 12.sp)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.world1Tab.value = "outings" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ver en Salidas", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (hasValidMapsApiKey) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(14.dp)
            ) {
                Text(
                    text = "💡 Mantén pulsado el mapa para mover ${if (pinMode == MapPinMode.MARK_DESTINATION) "el Destino" else "el Punto de encuentro"} o toca un marcador",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }

    // --- CONFIRMATION DIALOG BEFORE MOVING AN OUTING PIN ---
    pendingPinMove?.let { move ->
        val kindLabel = if (move.isDestination) "el destino" else "el punto de encuentro"
        AlertDialog(
            onDismissRequest = { pendingPinMove = null },
            title = { Text("Actualizar ubicación", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "¿Quieres mover $kindLabel de '${move.outing.title}' a ${move.label ?: "(${"%.4f".format(move.lat)}, ${"%.4f".format(move.lng)})"}?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateOutingCoordinates(
                            outingId = move.outing.id,
                            isDestination = move.isDestination,
                            lat = move.lat,
                            lng = move.lng,
                            placeLabel = move.label
                        )
                        searchStatusText = "✅ Ubicación actualizada en '${move.outing.title}'"
                        pendingPinMove = null
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPinMove = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun PlacePhotoThumbnail(
    photoKey: String,
    modifier: Modifier = Modifier
) {
    if (MediaStorageHelper.isRenderableImageUri(photoKey)) {
        AsyncImage(
            model = MediaStorageHelper.toCoilModel(photoKey),
            contentDescription = "Foto del lugar",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        return
    }

    val (gradient, emoji) = when (photoKey) {
        "preset_bowling" -> listOf(Color(0xFF7C3AED), Color(0xFFEC4899)) to "🎳"
        "preset_mountain" -> listOf(Color(0xFF059669), Color(0xFF34D399)) to "🏔️"
        "preset_cinema" -> listOf(Color(0xFFDC2626), Color(0xFFF59E0B)) to "🍿"
        "preset_escape" -> listOf(Color(0xFF1D4ED8), Color(0xFF06B6D4)) to "🕵️"
        "preset_beach" -> listOf(Color(0xFF0284C7), Color(0xFFFDE047)) to "🏖️"
        else -> listOf(Color(0xFFEA580C), Color(0xFFF43F5E)) to "🍕"
    }
    Box(
        modifier = modifier.background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 20.sp)
    }
}
