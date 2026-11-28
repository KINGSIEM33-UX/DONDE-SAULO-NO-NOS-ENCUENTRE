package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Outing
import com.example.data.OutingStatus
import com.example.data.RsvpChoice
import com.example.data.todayIsoDate
import com.example.ui.theme.StatusCancelledOuter
import com.example.ui.theme.StatusCancelledRed
import com.example.ui.theme.StatusConfirmedGreen
import com.example.ui.theme.StatusConfirmedOuterRing
import com.example.ui.theme.StatusPlannedGray
import com.example.ui.theme.StatusPlannedOuter
import java.util.Calendar

private val MONTH_NAMES_ES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

@Composable
fun World1OutingsScreen(viewModel: MainViewModel) {
    val outings by viewModel.outings.collectAsState()
    val stories by viewModel.stories.collectAsState()
    val defaultCurrency by viewModel.defaultCurrency.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()

    val todayCal = remember { Calendar.getInstance() }
    val todayIso = remember { todayIsoDate() }
    var displayedYear by remember { mutableIntStateOf(todayCal.get(Calendar.YEAR)) }
    var displayedMonthZeroIndexed by remember { mutableIntStateOf(todayCal.get(Calendar.MONTH)) }

    var showOutingFormDialog by remember { mutableStateOf(false) }
    var editingOuting by remember { mutableStateOf<Outing?>(null) }
    var preselectedDateIso by remember { mutableStateOf(todayIso) }
    var activeDetailOuting by remember { mutableStateOf<Outing?>(null) }

    val monthOutingsMap = remember(outings, displayedYear, displayedMonthZeroIndexed) {
        val prefix = "%04d-%02d-".format(displayedYear, displayedMonthZeroIndexed + 1)
        outings.filter { it.dateIso.startsWith(prefix) }
            .groupBy { it.dateIso.removePrefix(prefix).toIntOrNull() ?: -1 }
    }

    val upcomingOutings = remember(outings, todayIso) {
        outings.filter { it.dateIso >= todayIso }.sortedBy { it.dateIso + " " + it.time }
    }

    val totalAlbumPhotos = remember(outings, stories) {
        outings.sumOf { it.albumPhotos.size } + stories.size
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // --- 1. EDITORIAL HEADER (Matches Approved Mockups) ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SplitTextReveal(
                            text = "Salidas",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EditorialWorldTogglePill(
                                label = "Biblia",
                                icon = Icons.AutoMirrored.Filled.MenuBook,
                                onClick = { viewModel.switchToWorld2Bible() }
                            )

                            IconButton(
                                onClick = { viewModel.showCameraStoriesModal.value = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .testTag("open_camera_stories_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Álbum e historias",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.showSocialAgentModal.value = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(0.75.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .testTag("open_ai_agent_outings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Asistente IA",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Subtle secondary metadata strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { viewModel.showOutingHistoryModal.value = true },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.testTag("open_outing_history_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Historial (${outings.size})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            onClick = { viewModel.showCameraStoriesModal.value = true },
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Álbum ($totalAlbumPhotos)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // --- 2. PRECISION EDITORIAL MONTHLY CALENDAR ---
            item {
                EditorialCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (displayedMonthZeroIndexed == 0) {
                                        displayedMonthZeroIndexed = 11
                                        displayedYear--
                                    } else {
                                        displayedMonthZeroIndexed--
                                    }
                                },
                                modifier = Modifier.size(38.dp).testTag("calendar_prev_month_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                            }

                            Text(
                                text = "${MONTH_NAMES_ES[displayedMonthZeroIndexed]} $displayedYear",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            IconButton(
                                onClick = {
                                    if (displayedMonthZeroIndexed == 11) {
                                        displayedMonthZeroIndexed = 0
                                        displayedYear++
                                    } else {
                                        displayedMonthZeroIndexed++
                                    }
                                },
                                modifier = Modifier.size(38.dp).testTag("calendar_next_month_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            daysOfWeek.forEach { d ->
                                Text(
                                    text = d,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val cal = remember(displayedYear, displayedMonthZeroIndexed) {
                            Calendar.getInstance().apply {
                                set(Calendar.YEAR, displayedYear)
                                set(Calendar.MONTH, displayedMonthZeroIndexed)
                                set(Calendar.DAY_OF_MONTH, 1)
                            }
                        }
                        val firstDayOfWeekJava = cal.get(Calendar.DAY_OF_WEEK)
                        val mondayBasedOffset = if (firstDayOfWeekJava == Calendar.SUNDAY) 6 else firstDayOfWeekJava - 2
                        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val totalCells = ((mondayBasedOffset + daysInMonth + 6) / 7) * 7

                        for (week in 0 until (totalCells / 7)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (dow in 0..6) {
                                    val cellIdx = week * 7 + dow
                                    val dayNumber = cellIdx - mondayBasedOffset + 1
                                    if (dayNumber in 1..daysInMonth) {
                                        val dayOutings = monthOutingsMap[dayNumber].orEmpty()
                                        val primaryOuting = dayOutings.firstOrNull()
                                        val dateIso = "%04d-%02d-%02d".format(
                                            displayedYear,
                                            displayedMonthZeroIndexed + 1,
                                            dayNumber
                                        )
                                        CalendarDayCell(
                                            dayNumber = dayNumber,
                                            outing = primaryOuting,
                                            outingsCount = dayOutings.size,
                                            isToday = dateIso == todayIso,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (primaryOuting != null) {
                                                    activeDetailOuting = primaryOuting
                                                } else {
                                                    editingOuting = null
                                                    preselectedDateIso = dateIso
                                                    showOutingFormDialog = true
                                                }
                                            }
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).height(44.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CalendarLegendItem(
                                label = "Confirmada",
                                innerColor = StatusConfirmedGreen,
                                outerRingColor = StatusConfirmedOuterRing,
                                isDoubleCircle = true
                            )
                            CalendarLegendItem(
                                label = "Pensada",
                                innerColor = StatusPlannedGray,
                                outerRingColor = StatusPlannedOuter,
                                isDoubleCircle = false
                            )
                            CalendarLegendItem(
                                label = "Cancelada",
                                innerColor = StatusCancelledRed,
                                outerRingColor = StatusCancelledOuter,
                                isDoubleCircle = false
                            )
                        }
                    }
                }
            }

            // --- 3. PRÓXIMAS SALIDAS ---
            item {
                EditorialSectionLabel(
                    text = "Próximas salidas (${upcomingOutings.size})"
                )
            }

            if (upcomingOutings.isEmpty()) {
                item {
                    EditorialCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("No hay salidas programadas", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Toca un día del calendario o el botón '+' para crear un nuevo encuentro.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(upcomingOutings, key = { _, o -> o.id }) { index, outing ->
                    AnimatedStaggeredItem(index = index) {
                        EditorialOutingCard(
                            outing = outing,
                            currentUserId = currentUserId,
                            onCardClick = { activeDetailOuting = outing },
                            onEditClick = {
                                editingOuting = outing
                                preselectedDateIso = outing.dateIso
                                showOutingFormDialog = true
                            },
                            onRsvpSelected = { rsvpChoice ->
                                viewModel.updateOutingRsvp(outing.id, rsvpChoice)
                            }
                        )
                    }
                }
            }
        }

        // --- 4. MINIMALIST OBSIDIAN / CHAMPAGNE FAB ---
        FloatingActionButton(
            onClick = {
                editingOuting = null
                preselectedDateIso = todayIso
                showOutingFormDialog = true
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(54.dp)
                .testTag("add_outing_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Nueva salida",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // --- QUICK DETAIL & RSVP DIALOG ---
    activeDetailOuting?.let { currentOuting ->
        val freshOuting = outings.firstOrNull { it.id == currentOuting.id } ?: currentOuting
        OutingQuickDetailDialog(
            outing = freshOuting,
            currentUserId = currentUserId,
            onDismiss = { activeDetailOuting = null },
            onRsvpChange = { choice -> viewModel.updateOutingRsvp(freshOuting.id, choice) },
            onStatusChange = { newStatus -> viewModel.updateOutingStatusColor(freshOuting.id, newStatus) },
            onEdit = {
                activeDetailOuting = null
                editingOuting = freshOuting
                preselectedDateIso = freshOuting.dateIso
                showOutingFormDialog = true
            },
            onOpenOnMap = {
                activeDetailOuting = null
                viewModel.selectedOutingForDetail.value = freshOuting
                viewModel.world1Tab.value = "map"
            },
            onNewOutingSameDay = {
                activeDetailOuting = null
                editingOuting = null
                preselectedDateIso = freshOuting.dateIso
                showOutingFormDialog = true
            }
        )
    }

    // --- CREATE / EDIT OUTING DIALOG ---
    if (showOutingFormDialog) {
        OutingFormDialog(
            initialOuting = editingOuting,
            defaultDateIso = preselectedDateIso,
            defaultCurrency = defaultCurrency,
            onDismiss = { showOutingFormDialog = false },
            onSave = { savedOuting ->
                viewModel.saveOrUpdateOuting(savedOuting)
                showOutingFormDialog = false
            },
            onDelete = editingOuting?.let { toDelete ->
                {
                    viewModel.deleteOuting(toDelete.id)
                    showOutingFormDialog = false
                }
            }
        )
    }
}

@Composable
private fun CalendarDayCell(
    dayNumber: Int,
    outing: Outing?,
    outingsCount: Int = 0,
    isToday: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val status = outing?.outingStatus
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("calendar_day_$dayNumber"),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            OutingStatus.CONFIRMED -> {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.5.dp, StatusConfirmedOuterRing, CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(StatusConfirmedGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            OutingStatus.PLANNED -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.2.dp, StatusPlannedOuter, CircleShape)
                        .clip(CircleShape)
                        .background(StatusPlannedGray.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            OutingStatus.CANCELLED -> {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .border(1.2.dp, StatusCancelledOuter, CircleShape)
                        .clip(CircleShape)
                        .background(StatusCancelledRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = StatusCancelledRed,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
            null -> {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .then(
                            if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = if (isToday) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }
        if (outingsCount > 1) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
            ) {
                Text(
                    text = "+${outingsCount - 1}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun CalendarLegendItem(
    label: String,
    innerColor: Color,
    outerRingColor: Color,
    isDoubleCircle: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (isDoubleCircle) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .border(1.2.dp, outerRingColor, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(innerColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(innerColor)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EditorialOutingCard(
    outing: Outing,
    currentUserId: String?,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onRsvpSelected: (RsvpChoice) -> Unit
) {
    val statusColor = when (outing.outingStatus) {
        OutingStatus.CONFIRMED -> StatusConfirmedGreen
        OutingStatus.PLANNED -> StatusPlannedGray
        OutingStatus.CANCELLED -> StatusCancelledRed
    }
    val userRsvp = outing.rsvpChoiceFor(currentUserId)

    SpotlightEditorialCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCardClick
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorialStatusPill(
                        label = "${outing.outingStatus.label}  •  ${outing.dateIso}",
                        color = statusColor,
                        isDoubleRing = outing.outingStatus == OutingStatus.CONFIRMED
                    )
                    Text(
                        text = outing.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onEditClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar salida",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                EditorialMetaItem(
                    icon = Icons.Default.LocationOn,
                    text = "${outing.location}  •  ${outing.time}"
                )
                if (outing.meetingPoint.isNotBlank()) {
                    EditorialMetaItem(
                        icon = Icons.Default.Handshake,
                        text = "Encuentro: ${outing.meetingPoint}"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EditorialMetaItem(
                        icon = Icons.Default.Payments,
                        text = outing.budgetPerPerson.ifBlank { "Sin coste" }
                    )
                    if (outing.kitItems.isNotBlank()) {
                        EditorialMetaItem(
                            icon = Icons.Default.Backpack,
                            text = outing.kitItems,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // React Bits Micro 2.0 — Rubber Segmented RSVP Bar (Asistiré | Duda | No)
            RubberSegmentedBar(
                options = listOf(
                    RubberSegmentOption(
                        id = RsvpChoice.YES.name,
                        label = "Asistiré (${outing.rsvpYesCount})",
                        activeColor = StatusConfirmedGreen,
                        activeContentColor = Color.White
                    ),
                    RubberSegmentOption(
                        id = RsvpChoice.MAYBE.name,
                        label = "Duda (${outing.rsvpMaybeCount})",
                        activeColor = StatusPlannedGray,
                        activeContentColor = Color.White
                    ),
                    RubberSegmentOption(
                        id = RsvpChoice.NO.name,
                        label = "No (${outing.rsvpNoCount})",
                        activeColor = StatusCancelledRed,
                        activeContentColor = Color.White
                    )
                ),
                selectedId = userRsvp?.name,
                onSelect = { id ->
                    RsvpChoice.entries.firstOrNull { it.name == id }?.let(onRsvpSelected)
                },
                height = 40.dp
            )
        }
    }
}

@Composable
fun OutingQuickDetailDialog(
    outing: Outing,
    currentUserId: String?,
    onDismiss: () -> Unit,
    onRsvpChange: (RsvpChoice) -> Unit,
    onStatusChange: (OutingStatus) -> Unit,
    onEdit: () -> Unit,
    onOpenOnMap: () -> Unit,
    onNewOutingSameDay: () -> Unit
) {
    val statusColor = when (outing.outingStatus) {
        OutingStatus.CONFIRMED -> StatusConfirmedGreen
        OutingStatus.PLANNED -> StatusPlannedGray
        OutingStatus.CANCELLED -> StatusCancelledRed
    }
    val userRsvp = outing.rsvpChoiceFor(currentUserId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                EditorialStatusPill(
                    label = "${outing.outingStatus.label} • ${outing.dateIso} ${outing.time}",
                    color = statusColor,
                    isDoubleRing = outing.outingStatus == OutingStatus.CONFIRMED
                )
                Text(text = outing.title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EditorialMetaItem(icon = Icons.Default.LocationOn, text = outing.location)
                if (outing.meetingPoint.isNotBlank()) {
                    EditorialMetaItem(icon = Icons.Default.Handshake, text = "Punto de encuentro: ${outing.meetingPoint}")
                }
                EditorialMetaItem(icon = Icons.Default.Payments, text = "Presupuesto: ${outing.budgetPerPerson.ifBlank { "No especificado" }}")
                if (outing.kitItems.isNotBlank()) {
                    EditorialMetaItem(icon = Icons.Default.Backpack, text = "Kit: ${outing.kitItems}")
                }
                if (outing.additionalInfo.isNotBlank()) {
                    Text(
                        text = outing.additionalInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Tu asistencia", style = MaterialTheme.typography.labelLarge)
                RubberSegmentedBar(
                    options = listOf(
                        RubberSegmentOption(RsvpChoice.YES.name, "Asistiré", StatusConfirmedGreen, Color.White),
                        RubberSegmentOption(RsvpChoice.MAYBE.name, "Duda", StatusPlannedGray, Color.White),
                        RubberSegmentOption(RsvpChoice.NO.name, "No", StatusCancelledRed, Color.White)
                    ),
                    selectedId = userRsvp?.name,
                    onSelect = { id -> RsvpChoice.entries.firstOrNull { it.name == id }?.let(onRsvpChange) },
                    height = 40.dp
                )

                Text("Estado en el calendario", style = MaterialTheme.typography.labelLarge)
                RubberSegmentedBar(
                    options = listOf(
                        RubberSegmentOption(OutingStatus.CONFIRMED.name, "Confirmada", StatusConfirmedGreen, Color.White),
                        RubberSegmentOption(OutingStatus.PLANNED.name, "Pensada", StatusPlannedGray, Color.White),
                        RubberSegmentOption(OutingStatus.CANCELLED.name, "Cancelada", StatusCancelledRed, Color.White)
                    ),
                    selectedId = outing.outingStatus.name,
                    onSelect = { id -> OutingStatus.entries.firstOrNull { it.name == id }?.let(onStatusChange) },
                    height = 38.dp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onOpenOnMap, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Ver mapa", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = onNewOutingSameDay, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Otra hoy", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) { Text("Editar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutingFormDialog(
    initialOuting: Outing?,
    defaultDateIso: String,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (Outing) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val pickerContext = LocalContext.current
    var title by remember { mutableStateOf(initialOuting?.title ?: "") }
    var location by remember { mutableStateOf(initialOuting?.location ?: "") }
    var meetingPoint by remember { mutableStateOf(initialOuting?.meetingPoint ?: "") }
    var time by remember { mutableStateOf(initialOuting?.time ?: "20:00") }
    var dateIso by remember { mutableStateOf(initialOuting?.dateIso ?: defaultDateIso) }
    var selectedStatus by remember { mutableStateOf(initialOuting?.outingStatus ?: OutingStatus.CONFIRMED) }
    var budget by remember { mutableStateOf(initialOuting?.budgetPerPerson ?: "15 $defaultCurrency") }
    var kitItems by remember { mutableStateOf(initialOuting?.kitItems ?: "") }
    var additionalInfo by remember { mutableStateOf(initialOuting?.additionalInfo ?: "") }
    var selectedPhotoPreset by remember { mutableStateOf(initialOuting?.placePhotoUrl ?: "preset_bowling") }

    val photoPresets = listOf(
        "preset_bowling" to "Ocio",
        "preset_mountain" to "Naturaleza",
        "preset_cinema" to "Cine",
        "preset_escape" to "Juegos",
        "preset_beach" to "Costa",
        "preset_plaza" to "Gastronomía"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialOuting == null) "Nueva salida" else "Editar salida",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_title_input")
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lugar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_location_input")
                )
                OutlinedTextField(
                    value = meetingPoint,
                    onValueChange = { meetingPoint = it },
                    label = { Text("Punto de encuentro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val parts = time.split(":")
                            val initH = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 20
                            val initM = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                            TimePickerDialog(
                                pickerContext,
                                { _, h, m -> time = "%02d:%02d".format(h, m) },
                                initH,
                                initM,
                                true
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(time.ifBlank { "20:00" }, style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val parts = dateIso.split("-")
                            val cal = Calendar.getInstance()
                            val initY = parts.getOrNull(0)?.toIntOrNull() ?: cal.get(Calendar.YEAR)
                            val initM = (parts.getOrNull(1)?.toIntOrNull() ?: (cal.get(Calendar.MONTH) + 1)) - 1
                            val initD = parts.getOrNull(2)?.toIntOrNull() ?: cal.get(Calendar.DAY_OF_MONTH)
                            DatePickerDialog(
                                pickerContext,
                                { _, y, m, d -> dateIso = "%04d-%02d-%02d".format(y, m + 1, d) },
                                initY,
                                initM.coerceIn(0, 11),
                                initD.coerceIn(1, 31)
                            ).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.35f).height(48.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(dateIso.ifBlank { defaultDateIso }, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Text("Estado", style = MaterialTheme.typography.labelLarge)
                RubberSegmentedBar(
                    options = listOf(
                        RubberSegmentOption(OutingStatus.CONFIRMED.name, "Confirmada", StatusConfirmedGreen, Color.White),
                        RubberSegmentOption(OutingStatus.PLANNED.name, "Pensada", StatusPlannedGray, Color.White),
                        RubberSegmentOption(OutingStatus.CANCELLED.name, "Cancelada", StatusCancelledRed, Color.White)
                    ),
                    selectedId = selectedStatus.name,
                    onSelect = { id ->
                        OutingStatus.entries.firstOrNull { it.name == id }?.let { selectedStatus = it }
                    },
                    height = 40.dp
                )

                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Presupuesto estimado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_budget_input")
                )
                OutlinedTextField(
                    value = kitItems,
                    onValueChange = { kitItems = it },
                    label = { Text("Kit / Qué llevar") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_kit_input")
                )
                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    label = { Text("Notas adicionales") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Categoría visual", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    photoPresets.forEach { (key, label) ->
                        val isSel = selectedPhotoPreset == key
                        Surface(
                            onClick = { selectedPhotoPreset = key },
                            shape = RoundedCornerShape(999.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // React Bits Micro 2.0 — Hold Button for safe destructive confirmation
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    HoldToConfirmButton(
                        label = "Mantener para eliminar salida",
                        doneLabel = "Eliminando…",
                        icon = Icons.Default.Delete,
                        fillColor = StatusCancelledRed,
                        textColor = StatusCancelledRed,
                        onHoldComplete = onDelete
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initialOuting ?: Outing()
                    onSave(
                        base.copy(
                            title = title.ifBlank { "Salida del grupo" },
                            location = location.ifBlank { "Centro" },
                            meetingPoint = meetingPoint.ifBlank { "Entrada principal" },
                            time = time.ifBlank { "20:00" },
                            dateIso = dateIso.ifBlank { defaultDateIso },
                            status = selectedStatus.name,
                            budgetPerPerson = budget.ifBlank { "Libre" },
                            kitItems = kitItems,
                            additionalInfo = additionalInfo,
                            placePhotoUrl = selectedPhotoPreset
                        )
                    )
                },
                modifier = Modifier.testTag("outing_form_save_button")
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
