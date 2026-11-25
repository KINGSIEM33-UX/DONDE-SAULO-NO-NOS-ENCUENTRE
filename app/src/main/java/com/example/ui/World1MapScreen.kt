package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

enum class MapPinMode {
    MARK_DESTINATION,
    MARK_MEETING_POINT
}

enum class InAppMapStyle(val label: String) {
    GOOGLE_STANDARD("Estándar"),
    SNAP_NIGHT("Nocturno"),
    SATELLITE("Satélite")
}

private data class KnownPlaceLocation(
    val name: String,
    val lat: Double,
    val lng: Double
)

private val KNOWN_PLACES = listOf(
    KnownPlaceLocation("Plaza Mayor, Centro", 40.4155, -3.7074),
    KnownPlaceLocation("Puerta del Sol", 40.4169, -3.7035),
    KnownPlaceLocation("Parque del Retiro", 40.4153, -3.6845),
    KnownPlaceLocation("Gran Vía / Callao", 40.4200, -3.7058),
    KnownPlaceLocation("Estación Central Atocha", 40.4065, -3.6896),
    KnownPlaceLocation("Santiago Bernabéu / Castellana", 40.4531, -3.6883),
    KnownPlaceLocation("Mirador del Valle Verde", 40.4240, -3.6880),
    KnownPlaceLocation("Ozone Bowling & Burger Plaza", 40.4168, -3.7038)
)

@SuppressLint("MissingPermission")
@Composable
fun World1MapScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val outings by viewModel.outings.collectAsState()
    val selectedFromExternal by viewModel.selectedOutingForDetail.collectAsState()

    var activeOuting by remember(selectedFromExternal, outings) {
        mutableStateOf(selectedFromExternal ?: outings.firstOrNull())
    }
    var showMiniScreen by remember(selectedFromExternal) {
        mutableStateOf(selectedFromExternal != null)
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchStatusText by remember { mutableStateOf<String?>(null) }
    var pinMode by remember { mutableStateOf(MapPinMode.MARK_DESTINATION) }
    var mapStyle by remember { mutableStateOf(InAppMapStyle.GOOGLE_STANDARD) }

    // Map viewport state (centered around 40.4168, -3.7038)
    var centerLat by remember { mutableFloatStateOf(40.4168f) }
    var centerLng by remember { mutableFloatStateOf(-3.7000f) }
    var zoomLevel by remember { mutableFloatStateOf(1f) } // 0.6f .. 2.5f
    var userGpsLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                client.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        userGpsLocation = loc.latitude to loc.longitude
                        centerLat = loc.latitude.toFloat()
                        centerLng = loc.longitude.toFloat()
                        searchStatusText = "Ubicación GPS en vivo activada 📍"
                    } else {
                        userGpsLocation = 40.4168 to -3.7038
                        searchStatusText = "GPS listo en el mapa"
                    }
                }
            } catch (_: Exception) {
                userGpsLocation = 40.4168 to -3.7038
            }
        } else {
            searchStatusText = "Permiso de ubicación omitido; puedes usar el buscador del mapa."
        }
    }

    fun executePlaceSearch(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val results = Geocoder(context, Locale.getDefault()).getFromLocationName(clean, 1)
                    if (!results.isNullOrEmpty()) {
                        KnownPlaceLocation(
                            name = results[0].featureName ?: clean,
                            lat = results[0].latitude,
                            lng = results[0].longitude
                        )
                    } else null
                } catch (_: Exception) {
                    null
                }
            } ?: KNOWN_PLACES.firstOrNull { it.name.contains(clean, ignoreCase = true) }
            ?: KnownPlaceLocation(
                name = clean,
                lat = 40.4140 + ((clean.hashCode() % 120) * 0.0001),
                lng = -3.7050 + ((clean.hashCode() % 90) * 0.0001)
            )

            centerLat = found.lat.toFloat()
            centerLng = found.lng.toFloat()
            val targetOuting = activeOuting ?: outings.firstOrNull()
            if (targetOuting != null) {
                val isDest = pinMode == MapPinMode.MARK_DESTINATION
                viewModel.updateOutingCoordinates(
                    outingId = targetOuting.id,
                    isDestination = isDest,
                    lat = found.lat,
                    lng = found.lng,
                    placeLabel = found.name
                )
                searchStatusText = if (isDest) {
                    "📍 Destino de '${targetOuting.title}' marcado en ${found.name}"
                } else {
                    "🤝 Punto de encuentro de '${targetOuting.title}' marcado en ${found.name}"
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("in_app_live_map_screen")
    ) {
        // --- 1. INTERACTIVE IN-APP MAP CANVAS WITH CIRCULAR PHOTO MARKERS ---
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(zoomLevel, pinMode, activeOuting) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val scale = 0.000045f / zoomLevel
                        centerLat += dragAmount.y * scale
                        centerLng -= dragAmount.x * scale
                    }
                }
                .pointerInput(zoomLevel, pinMode, activeOuting) {
                    detectTapGestures { tapOffset ->
                        val widthPx = size.width.toFloat().coerceAtLeast(1f)
                        val heightPx = size.height.toFloat().coerceAtLeast(1f)
                        val spanLat = 0.028f / zoomLevel
                        val spanLng = 0.035f / zoomLevel

                        val tappedLat = centerLat + ((0.5f - (tapOffset.y / heightPx)) * spanLat)
                        val tappedLng = centerLng + (((tapOffset.x / widthPx) - 0.5f) * spanLng)

                        val target = activeOuting ?: outings.firstOrNull()
                        if (target != null) {
                            val isDest = pinMode == MapPinMode.MARK_DESTINATION
                            viewModel.updateOutingCoordinates(
                                outingId = target.id,
                                isDestination = isDest,
                                lat = tappedLat.toDouble(),
                                lng = tappedLng.toDouble()
                            )
                            searchStatusText = if (isDest) {
                                "📍 Lugar de salida '${target.title}' actualizado en el mapa"
                            } else {
                                "🤝 Punto de encuentro de '${target.title}' actualizado en el mapa"
                            }
                        }
                    }
                }
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val heightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            val spanLat = 0.028f / zoomLevel
            val spanLng = 0.035f / zoomLevel

            fun latLngToPixel(lat: Double, lng: Double): Offset {
                val normX = (((lng.toFloat() - centerLng) / spanLng) + 0.5f)
                val normY = (0.5f - ((lat.toFloat() - centerLat) / spanLat))
                return Offset(normX * widthPx, normY * heightPx)
            }

            // Map Streets, Parks, Rivers & Routes Layer
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bgColor = when (mapStyle) {
                    InAppMapStyle.GOOGLE_STANDARD -> Color(0xFFEAE6DF)
                    InAppMapStyle.SNAP_NIGHT -> Color(0xFF111827)
                    InAppMapStyle.SATELLITE -> Color(0xFF1B3328)
                }
                val roadColor = when (mapStyle) {
                    InAppMapStyle.GOOGLE_STANDARD -> Color(0xFFFFFFFF)
                    InAppMapStyle.SNAP_NIGHT -> Color(0xFF1F2937)
                    InAppMapStyle.SATELLITE -> Color(0xFF94A3B8).copy(alpha = 0.55f)
                }
                val highwayColor = when (mapStyle) {
                    InAppMapStyle.GOOGLE_STANDARD -> Color(0xFFFDE68A)
                    InAppMapStyle.SNAP_NIGHT -> Color(0xFF374151)
                    InAppMapStyle.SATELLITE -> Color(0xFFF59E0B).copy(alpha = 0.65f)
                }
                val parkColor = when (mapStyle) {
                    InAppMapStyle.GOOGLE_STANDARD -> Color(0xFFC8E6C9)
                    InAppMapStyle.SNAP_NIGHT -> Color(0xFF064E3B).copy(alpha = 0.55f)
                    InAppMapStyle.SATELLITE -> Color(0xFF14532D)
                }
                val waterColor = when (mapStyle) {
                    InAppMapStyle.GOOGLE_STANDARD -> Color(0xFF93C5FD)
                    InAppMapStyle.SNAP_NIGHT -> Color(0xFF1E3A8A)
                    InAppMapStyle.SATELLITE -> Color(0xFF0284C7)
                }

                drawRect(color = bgColor)

                // Parks & Water zones
                val parkPos = latLngToPixel(40.4153, -3.6885)
                drawCircle(color = parkColor, radius = 160f * zoomLevel, center = parkPos)

                val northParkPos = latLngToPixel(40.4240, -3.6920)
                drawCircle(color = parkColor, radius = 120f * zoomLevel, center = northParkPos)

                // River / Water channel
                drawLine(
                    color = waterColor,
                    start = Offset(0f, size.height * 0.78f),
                    end = Offset(size.width, size.height * 0.62f),
                    strokeWidth = 36f * zoomLevel
                )

                // Street Grid
                val gridStep = (95f * zoomLevel).coerceIn(45f, 220f)
                var x = (centerLng * 10000f) % gridStep
                while (x < size.width) {
                    drawLine(
                        color = roadColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 10f
                    )
                    x += gridStep
                }
                var y = (centerLat * 10000f) % gridStep
                while (y < size.height) {
                    drawLine(
                        color = roadColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 10f
                    )
                    y += gridStep
                }

                // Diagonal Avenues
                drawLine(
                    color = highwayColor,
                    start = Offset(0f, size.height * 0.25f),
                    end = Offset(size.width, size.height * 0.70f),
                    strokeWidth = 18f
                )
                drawLine(
                    color = highwayColor,
                    start = Offset(size.width * 0.20f, 0f),
                    end = Offset(size.width * 0.75f, size.height),
                    strokeWidth = 16f
                )

                // Dashed connection lines between each Outing's Meeting Point and Destination
                outings.forEach { outing ->
                    val destPt = latLngToPixel(outing.destLat, outing.destLng)
                    val meetPt = latLngToPixel(outing.meetLat, outing.meetLng)
                    val lineColor = when (outing.outingStatus) {
                        OutingStatus.CONFIRMED -> StatusConfirmedGreen
                        OutingStatus.PLANNED -> StatusPlannedGray
                        OutingStatus.CANCELLED -> StatusCancelledRed
                    }
                    drawLine(
                        color = lineColor.copy(alpha = 0.85f),
                        start = meetPt,
                        end = destPt,
                        strokeWidth = 6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                    )
                }
            }

            // Render Meeting Point Markers ("Punto de Encuentro 🤝")
            outings.forEach { outing ->
                val meetPx = latLngToPixel(outing.meetLat, outing.meetLng)
                if (meetPx.x in -100f..(widthPx + 100f) && meetPx.y in -100f..(heightPx + 100f)) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (meetPx.x - 24.dp.toPx()).roundToInt(),
                                    (meetPx.y - 24.dp.toPx()).roundToInt()
                                )
                            }
                            .clickable {
                                activeOuting = outing
                                showMiniScreen = true
                            }
                            .testTag("map_meeting_marker_${outing.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .shadow(4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB))
                                    .border(2.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🤝", fontSize = 16.sp)
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E3A8A).copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "Encuentro",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Render Circular Destination Photo Markers with Status Borders (Verde / Gris / Rojo)
            outings.forEach { outing ->
                val destPx = latLngToPixel(outing.destLat, outing.destLng)
                if (destPx.x in -120f..(widthPx + 120f) && destPx.y in -120f..(heightPx + 120f)) {
                    MapOutingCircularMarker(
                        outing = outing,
                        isSelected = activeOuting?.id == outing.id,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (destPx.x - 36.dp.toPx()).roundToInt(),
                                    (destPx.y - 42.dp.toPx()).roundToInt()
                                )
                            }
                            .testTag("map_dest_marker_${outing.id}"),
                        onClick = {
                            activeOuting = outing
                            showMiniScreen = true
                        }
                    )
                }
            }

            // User Live GPS Marker if active
            userGpsLocation?.let { (uLat, uLng) ->
                val uPx = latLngToPixel(uLat, uLng)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (uPx.x - 12.dp.toPx()).roundToInt(),
                                (uPx.y - 12.dp.toPx()).roundToInt()
                            )
                        }
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3B82F6).copy(alpha = 0.3f))
                        .border(2.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB))
                    )
                }
            }
        }

        // --- 2. TOP OVERLAY: GOOGLE MAPS-STYLE SEARCH BAR + PIN MODE SELECTOR ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search Bar Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar en Google Maps interno",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Buscar lugar o dirección en el mapa...",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { executePlaceSearch(searchQuery) }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("map_search_input")
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                    Button(
                        onClick = { executePlaceSearch(searchQuery) },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(40.dp).testTag("map_search_submit_button")
                    ) {
                        Text("Ir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Quick Mode Toggle: Marcar Destino vs Marcar Punto de Encuentro
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
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    },
                    contentColor = if (pinMode == MapPinMode.MARK_DESTINATION) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("mode_mark_destination_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Marcar Salida", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    onClick = { pinMode = MapPinMode.MARK_MEETING_POINT },
                    shape = RoundedCornerShape(16.dp),
                    color = if (pinMode == MapPinMode.MARK_MEETING_POINT) {
                        Color(0xFF2563EB)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    },
                    contentColor = if (pinMode == MapPinMode.MARK_MEETING_POINT) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("mode_mark_meeting_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Marcar Encuentro", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Outing Selector Chips on Map
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(outings, key = { it.id }) { item ->
                    val statusColor = when (item.outingStatus) {
                        OutingStatus.CONFIRMED -> StatusConfirmedGreen
                        OutingStatus.PLANNED -> StatusPlannedGray
                        OutingStatus.CANCELLED -> StatusCancelledRed
                    }
                    val isSel = activeOuting?.id == item.id
                    Surface(
                        onClick = {
                            activeOuting = item
                            centerLat = item.destLat.toFloat()
                            centerLng = item.destLng.toFloat()
                            showMiniScreen = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        border = BorderStroke(if (isSel) 2.dp else 1.dp, statusColor),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = "${item.title} (${item.outingStatus.shortBadge})",
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.ExtraBold else FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            if (!searchStatusText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f)
                ) {
                    Text(
                        text = searchStatusText ?: "",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // --- 3. FLOATING MAP CONTROLS (ZOOM, LAYER STYLE, MY GPS LOCATION) ---
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    val nextOrd = (mapStyle.ordinal + 1) % InAppMapStyle.entries.size
                    mapStyle = InAppMapStyle.entries[nextOrd]
                },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("map_layer_toggle_btn")
            ) {
                Icon(Icons.Default.Layers, contentDescription = "Cambiar vista de mapa")
            }

            IconButton(
                onClick = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("map_my_location_btn")
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Mi ubicación en vivo",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel * 1.25f).coerceAtMost(2.5f) },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("map_zoom_in_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Acercar mapa")
            }

            IconButton(
                onClick = { zoomLevel = (zoomLevel / 1.25f).coerceAtLeast(0.6f) },
                modifier = Modifier
                    .size(48.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("map_zoom_out_btn")
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Alejar mapa")
            }
        }

        // --- 4. IN-MAP MINI SCREEN (BOTTOM SHEET CARD WHEN MARKER IS TAPPED) ---
        if (showMiniScreen && activeOuting != null) {
            val current = outings.firstOrNull { it.id == activeOuting?.id } ?: activeOuting!!
            val borderColor = when (current.outingStatus) {
                OutingStatus.CONFIRMED -> StatusConfirmedGreen
                OutingStatus.PLANNED -> StatusPlannedGray
                OutingStatus.CANCELLED -> StatusCancelledRed
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .testTag("map_outing_mini_screen"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                border = BorderStroke(2.5.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                presetKey = current.placePhotoUrl,
                                status = current.outingStatus,
                                sizeDp = 52
                            )
                            Column {
                                Text(
                                    text = current.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${current.outingStatus.shortBadge} • ${current.outingStatus.label} • ${current.dateIso} (${current.time})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = borderColor,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        IconButton(
                            onClick = { showMiniScreen = false },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar mini pantalla")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "📍 Lugar de la Salida: ${current.location}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "🤝 Punto de Encuentro: ${current.meetingPoint.ifBlank { "Por definir en el mapa" }}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "💶 Dinero a llevar: ${current.budgetPerPerson}  |  🎒 Kit: ${current.kitItems.ifBlank { "Libre" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    if (current.additionalInfo.isNotBlank()) {
                        Text(
                            text = "ℹ️ ${current.additionalInfo}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Attendance buttons right inside the Map Mini-Screen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.updateOutingRsvp(current.id, RsvpChoice.YES) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (current.rsvpChoice == RsvpChoice.YES) StatusConfirmedGreen else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (current.rsvpChoice == RsvpChoice.YES) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Asistiré (${current.rsvpYesCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.updateOutingRsvp(current.id, RsvpChoice.MAYBE) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (current.rsvpChoice == RsvpChoice.MAYBE) StatusPlannedGray else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (current.rsvpChoice == RsvpChoice.MAYBE) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("Duda (${current.rsvpMaybeCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        FilledTonalButton(
                            onClick = { viewModel.updateOutingRsvp(current.id, RsvpChoice.NO) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = if (current.rsvpChoice == RsvpChoice.NO) StatusCancelledRed else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (current.rsvpChoice == RsvpChoice.NO) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) {
                            Text("No voy (${current.rsvpNoCount})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapOutingCircularMarker(
    outing: Outing,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val frameColor = when (outing.outingStatus) {
        OutingStatus.CONFIRMED -> StatusConfirmedGreen
        OutingStatus.PLANNED -> StatusPlannedGray
        OutingStatus.CANCELLED -> StatusCancelledRed
    }

    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status pill on top of circular marker ("Sí", "Tal vez", "No")
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = frameColor,
            shadowElevation = 4.dp
        ) {
            Text(
                text = "${outing.outingStatus.shortBadge} • ${outing.title.take(12)}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Circular Frame synced with Calendar Status Color + Place Photo inside
        PlacePhotoThumbnail(
            presetKey = outing.placePhotoUrl,
            status = outing.outingStatus,
            sizeDp = if (isSelected) 64 else 56
        )
    }
}

@Composable
fun PlacePhotoThumbnail(
    presetKey: String,
    status: OutingStatus,
    sizeDp: Int
) {
    val (outerColor, innerColor) = when (status) {
        OutingStatus.CONFIRMED -> StatusConfirmedOuterRing to StatusConfirmedGreen
        OutingStatus.PLANNED -> StatusPlannedOuter to StatusPlannedGray
        OutingStatus.CANCELLED -> StatusCancelledOuter to StatusCancelledRed
    }

    val (bgGradient, emojiIcon) = when {
        presetKey.contains("bowling") -> listOf(Color(0xFF4F46E5), Color(0xFFEC4899)) to "🎳"
        presetKey.contains("mountain") -> listOf(Color(0xFF059669), Color(0xFF10B981)) to "🏔️"
        presetKey.contains("cinema") -> listOf(Color(0xFF7C3AED), Color(0xFFDB2777)) to "🍿"
        presetKey.contains("escape") -> listOf(Color(0xFFD97706), Color(0xFFEA580C)) to "🕵️"
        presetKey.contains("beach") -> listOf(Color(0xFF0284C7), Color(0xFF38BDF8)) to "🏖️"
        else -> listOf(Color(0xFF2563EB), Color(0xFF7C3AED)) to "🍕"
    }

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .shadow(6.dp, CircleShape)
            .border(3.dp, outerColor, CircleShape)
            .padding(3.dp)
            .border(2.5.dp, innerColor, CircleShape)
            .padding(3.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(bgGradient)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emojiIcon,
            fontSize = (sizeDp * 0.42f).sp,
            textAlign = TextAlign.Center
        )
    }
}
