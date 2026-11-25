package com.example.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backpack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

    // Defaults to November 2026 so Tuesday Nov 10 (Martes 10 de noviembre) is immediately visible with its double green circle!
    var displayedYear by remember { mutableIntStateOf(2026) }
    var displayedMonthZeroIndexed by remember { mutableIntStateOf(10) } // 10 = Noviembre

    var showOutingFormDialog by remember { mutableStateOf(false) }
    var editingOuting by remember { mutableStateOf<Outing?>(null) }
    var preselectedDateIso by remember { mutableStateOf("2026-11-10") }

    var activeDetailOuting by remember { mutableStateOf<Outing?>(null) }

    val monthOutingsMap = remember(outings, displayedYear, displayedMonthZeroIndexed) {
        val prefix = "%04d-%02d-".format(displayedYear, displayedMonthZeroIndexed + 1)
        outings.filter { it.dateIso.startsWith(prefix) }
            .groupBy { it.dateIso.removePrefix(prefix).toIntOrNull() ?: -1 }
    }

    val upcomingOutings = remember(outings) {
        outings.filter { it.dateIso >= "2026-01-01" }.sortedBy { it.dateIso + " " + it.time }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- 1. TOP BAR: TITLE + SUBTLE HISTORIAL PILL + CAMERA (📷) + AI AGENT (🤖) ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Salidas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        // Subtle, at-a-glance "Historial de salidas" link
                        Surface(
                            onClick = { viewModel.showOutingHistoryModal.value = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .testTag("open_outing_history_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Historial de salidas",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Historial de salidas (${outings.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // CAMERA & INSTANTÁNEAS BUTTON (📷)
                        Surface(
                            onClick = { viewModel.showCameraStoriesModal.value = true },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("open_camera_stories_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Cámara e Instantáneas",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Álbum (${stories.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // AI AGENT BUTTON (🤖✨)
                        IconButton(
                            onClick = { viewModel.showSocialAgentModal.value = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .testTag("open_ai_agent_outings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = "Agente IA",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // --- 2. MINI CALENDAR WITH DOUBLE GREEN CIRCLE / GRAY CIRCLE / RED CIRCLE ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Month Navigation Bar
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
                                modifier = Modifier.size(48.dp).testTag("calendar_prev_month_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mes anterior")
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${MONTH_NAMES_ES[displayedMonthZeroIndexed]} $displayedYear",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Toca cualquier número para crear o editar salida",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (displayedMonthZeroIndexed == 11) {
                                        displayedMonthZeroIndexed = 0
                                        displayedYear++
                                    } else {
                                        displayedMonthZeroIndexed++
                                    }
                                },
                                modifier = Modifier.size(48.dp).testTag("calendar_next_month_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Mes siguiente")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Weekday Headers (L M X J V S D)
                        val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")
                        Row(modifier = Modifier.fillMaxWidth()) {
                            daysOfWeek.forEach { d ->
                                Text(
                                    text = d,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Calendar Days Grid
                        val cal = remember(displayedYear, displayedMonthZeroIndexed) {
                            Calendar.getInstance().apply {
                                set(Calendar.YEAR, displayedYear)
                                set(Calendar.MONTH, displayedMonthZeroIndexed)
                                set(Calendar.DAY_OF_MONTH, 1)
                            }
                        }
                        val firstDayOfWeekJava = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
                        val mondayBasedOffset = if (firstDayOfWeekJava == Calendar.SUNDAY) 6 else firstDayOfWeekJava - 2
                        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val totalCells = ((mondayBasedOffset + daysInMonth + 6) / 7) * 7

                        for (week in 0 until (totalCells / 7)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
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
                                        Spacer(modifier = Modifier.weight(1f).height(48.dp))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Status Color Legend
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CalendarLegendItem(
                                label = "Confirmada (Doble verde)",
                                innerColor = StatusConfirmedGreen,
                                outerRingColor = StatusConfirmedOuterRing,
                                isDoubleCircle = true
                            )
                            CalendarLegendItem(
                                label = "Pensada (Gris)",
                                innerColor = StatusPlannedGray,
                                outerRingColor = StatusPlannedOuter,
                                isDoubleCircle = false
                            )
                            CalendarLegendItem(
                                label = "Cancelada (Rojo)",
                                innerColor = StatusCancelledRed,
                                outerRingColor = StatusCancelledOuter,
                                isDoubleCircle = false
                            )
                        }
                    }
                }
            }

            // --- 3. PRÓXIMAS SALIDAS HEADER ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Próximas Salidas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Toca una salida para anotar si asistirás",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // --- 4. PRÓXIMAS SALIDAS CARDS WITH COLOR RULE & QUICK RSVP ---
            items(upcomingOutings, key = { it.id }) { outing ->
                OutingCardItem(
                    outing = outing,
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

        // --- 5. BOTTOM-RIGHT SQUARE '+' FAB ---
        FloatingActionButton(
            onClick = {
                editingOuting = null
                preselectedDateIso = "%04d-%02d-10".format(displayedYear, displayedMonthZeroIndexed + 1)
                showOutingFormDialog = true
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(58.dp)
                .testTag("add_outing_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Nueva salida",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // --- QUICK DETAIL & RSVP DIALOG ---
    activeDetailOuting?.let { currentOuting ->
        val freshOuting = outings.firstOrNull { it.id == currentOuting.id } ?: currentOuting
        OutingQuickDetailDialog(
            outing = freshOuting,
            onDismiss = { activeDetailOuting = null },
            onRsvpChange = { choice ->
                viewModel.updateOutingRsvp(freshOuting.id, choice)
            },
            onStatusChange = { newStatus ->
                viewModel.updateOutingStatusColor(freshOuting.id, newStatus)
            },
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
                val dateToUse = freshOuting.dateIso
                activeDetailOuting = null
                editingOuting = null
                preselectedDateIso = dateToUse
                showOutingFormDialog = true
            }
        )
    }

    // --- CREATE / EDIT OUTING FORM DIALOG ---
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val status = outing?.outingStatus
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("calendar_day_$dayNumber"),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            OutingStatus.CONFIRMED -> {
                // GREEN CIRCLE INSIDE A GREEN CIRCLE (Doble circulito verde)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .border(2.5.dp, StatusConfirmedOuterRing, CircleShape)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(StatusConfirmedGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }
            }
            OutingStatus.PLANNED -> {
                // GRAY CIRCLE (Pensada / Por confirmar)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.5.dp, StatusPlannedOuter, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(StatusPlannedGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            OutingStatus.CANCELLED -> {
                // RED CIRCLE (Cancelada)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .border(1.5.dp, StatusCancelledOuter, CircleShape)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(StatusCancelledRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayNumber.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            null -> {
                Text(
                    text = dayNumber.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isDoubleCircle) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(1.5.dp, outerRingColor, CircleShape)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(innerColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(innerColor)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun OutingCardItem(
    outing: Outing,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onRsvpSelected: (RsvpChoice) -> Unit
) {
    val statusColor = when (outing.outingStatus) {
        OutingStatus.CONFIRMED -> StatusConfirmedGreen
        OutingStatus.PLANNED -> StatusPlannedGray
        OutingStatus.CANCELLED -> StatusCancelledRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("outing_card_${outing.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, statusColor.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Status Circle Indicator matching the calendar rule
                    if (outing.outingStatus == OutingStatus.CONFIRMED) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(2.dp, StatusConfirmedOuterRing, CircleShape)
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(StatusConfirmedGreen)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                    }

                    Column {
                        Text(
                            text = outing.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${outing.dateIso} • ${outing.time} • ${outing.outingStatus.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Money / Budget Pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = outing.budgetPerPerson.ifBlank { "Libre" },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(48.dp).testTag("edit_outing_${outing.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar salida",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Destination & Meeting point concise info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Lugar: ${outing.location}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (outing.meetingPoint.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Nos encontramos en: ${outing.meetingPoint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            if (outing.kitItems.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Backpack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Kit de la Quedada: ${outing.kitItems}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick 1-Tap Attendance (RSVP) Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RsvpQuickButton(
                    text = "Asistiré (${outing.rsvpYesCount})",
                    selected = outing.rsvpChoice == RsvpChoice.YES,
                    activeColor = StatusConfirmedGreen,
                    modifier = Modifier.weight(1f).testTag("rsvp_yes_${outing.id}"),
                    onClick = { onRsvpSelected(RsvpChoice.YES) }
                )
                RsvpQuickButton(
                    text = "Duda (${outing.rsvpMaybeCount})",
                    selected = outing.rsvpChoice == RsvpChoice.MAYBE,
                    activeColor = StatusPlannedGray,
                    modifier = Modifier.weight(1f).testTag("rsvp_maybe_${outing.id}"),
                    onClick = { onRsvpSelected(RsvpChoice.MAYBE) }
                )
                RsvpQuickButton(
                    text = "No voy (${outing.rsvpNoCount})",
                    selected = outing.rsvpChoice == RsvpChoice.NO,
                    activeColor = StatusCancelledRed,
                    modifier = Modifier.weight(1f).testTag("rsvp_no_${outing.id}"),
                    onClick = { onRsvpSelected(RsvpChoice.NO) }
                )
            }
        }
    }
}

@Composable
private fun RsvpQuickButton(
    text: String,
    selected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun OutingQuickDetailDialog(
    outing: Outing,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = outing.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${outing.dateIso} a las ${outing.time} • ${outing.outingStatus.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📍 Lugar de la salida: ${outing.location}", fontWeight = FontWeight.SemiBold)
                if (outing.meetingPoint.isNotBlank()) {
                    Text("🤝 Punto de encuentro: ${outing.meetingPoint}")
                }
                Text("💶 Dinero a llevar: ${outing.budgetPerPerson.ifBlank { "No especificado" }}", fontWeight = FontWeight.Bold)
                if (outing.kitItems.isNotBlank()) {
                    Text("🎒 Kit de la Quedada (Qué llevar): ${outing.kitItems}")
                }
                if (outing.additionalInfo.isNotBlank()) {
                    Text("ℹ️ Información adicional: ${outing.additionalInfo}")
                }

                HorizontalDivider()

                Text(
                    text = "¿Vas a asistir a esta salida?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RsvpQuickButton(
                        text = "✅ Asistiré (${outing.rsvpYesCount})",
                        selected = outing.rsvpChoice == RsvpChoice.YES,
                        activeColor = StatusConfirmedGreen,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRsvpChange(RsvpChoice.YES) }
                    )
                    RsvpQuickButton(
                        text = "🤔 Aún no lo tengo claro (${outing.rsvpMaybeCount})",
                        selected = outing.rsvpChoice == RsvpChoice.MAYBE,
                        activeColor = StatusPlannedGray,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRsvpChange(RsvpChoice.MAYBE) }
                    )
                    RsvpQuickButton(
                        text = "❌ No asistiré (${outing.rsvpNoCount})",
                        selected = outing.rsvpChoice == RsvpChoice.NO,
                        activeColor = StatusCancelledRed,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRsvpChange(RsvpChoice.NO) }
                    )
                }

                HorizontalDivider()

                Text(
                    text = "Cambiar color / estado en el calendario:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onStatusChange(OutingStatus.CONFIRMED) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("🟢 Sí", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onStatusChange(OutingStatus.PLANNED) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("⚪ Pensada", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onStatusChange(OutingStatus.CANCELLED) },
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("🔴 No", fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenOnMap,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Ver en Mapa 🗺️", fontSize = 12.sp)
                    }
                    FilledTonalButton(
                        onClick = onNewOutingSameDay,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("+ Otra este día", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onEdit) {
                Text("Editar Salida")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
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
    var title by remember { mutableStateOf(initialOuting?.title ?: "") }
    var location by remember { mutableStateOf(initialOuting?.location ?: "") }
    var meetingPoint by remember { mutableStateOf(initialOuting?.meetingPoint ?: "") }
    var time by remember { mutableStateOf(initialOuting?.time ?: "20:00") }
    var dateIso by remember { mutableStateOf(initialOuting?.dateIso ?: defaultDateIso) }
    var selectedStatus by remember {
        mutableStateOf(initialOuting?.outingStatus ?: OutingStatus.CONFIRMED)
    }
    var budget by remember {
        mutableStateOf(initialOuting?.budgetPerPerson ?: "15 $defaultCurrency")
    }
    var kitItems by remember { mutableStateOf(initialOuting?.kitItems ?: "") }
    var additionalInfo by remember { mutableStateOf(initialOuting?.additionalInfo ?: "") }
    var selectedPhotoPreset by remember {
        mutableStateOf(initialOuting?.placePhotoUrl ?: "preset_bowling")
    }

    val photoPresets = listOf(
        "preset_bowling" to "🎳 Bolera / Ocio",
        "preset_mountain" to "🏔️ Naturaleza",
        "preset_cinema" to "🍿 Cine / Teatro",
        "preset_escape" to "🕵️ Escape / Juegos",
        "preset_beach" to "🏖️ Playa / Piscina",
        "preset_plaza" to "🍕 Cena / Centro"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialOuting == null) "Nueva Salida" else "Editar Salida",
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre de la salida") },
                    placeholder = { Text("Ej: Cena y Bolera de Grupo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_title_input")
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Lugar (A dónde iremos)") },
                    placeholder = { Text("Ej: Centro Comercial Norte") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_location_input")
                )

                OutlinedTextField(
                    value = meetingPoint,
                    onValueChange = { meetingPoint = it },
                    label = { Text("Punto de encuentro (Dónde nos vemos)") },
                    placeholder = { Text("Ej: Entrada principal junto a la fuente") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Hora") },
                        placeholder = { Text("20:30") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = dateIso,
                        onValueChange = { dateIso = it },
                        label = { Text("Fecha (Auto)") },
                        placeholder = { Text("2026-11-10") },
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Color y Estado en el Calendario y Mapa:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChoiceRow(
                        label = "🟢 Confirmada (Doble círculo verde)",
                        selected = selectedStatus == OutingStatus.CONFIRMED,
                        color = StatusConfirmedGreen,
                        onClick = { selectedStatus = OutingStatus.CONFIRMED }
                    )
                    StatusChoiceRow(
                        label = "⚪ Pensada / Por confirmar (Círculo gris)",
                        selected = selectedStatus == OutingStatus.PLANNED,
                        color = StatusPlannedGray,
                        onClick = { selectedStatus = OutingStatus.PLANNED }
                    )
                    StatusChoiceRow(
                        label = "🔴 Cancelada (Círculo rojo)",
                        selected = selectedStatus == OutingStatus.CANCELLED,
                        color = StatusCancelledRed,
                        onClick = { selectedStatus = OutingStatus.CANCELLED }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("¿Cuánto dinero hay que llevar?") },
                    placeholder = { Text("Ej: 15 € por persona") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_budget_input")
                )

                OutlinedTextField(
                    value = kitItems,
                    onValueChange = { kitItems = it },
                    label = { Text("Kit de la Quedada (¿Qué llevar?)") },
                    placeholder = { Text("Ej: Ropa cómoda, agua, entradas, batería...") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("outing_form_kit_input")
                )

                OutlinedTextField(
                    value = additionalInfo,
                    onValueChange = { additionalInfo = it },
                    label = { Text("Información adicional") },
                    placeholder = { Text("Detalles, reservas, transporte...") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Foto / Icono del lugar para el Mapa:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    photoPresets.forEach { (key, label) ->
                        val isSel = selectedPhotoPreset == key
                        Surface(
                            onClick = { selectedPhotoPreset = key },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusCancelledRed),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Eliminar esta salida")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val base = initialOuting ?: Outing()
                    onSave(
                        base.copy(
                            title = title.ifBlank { "Salida del Grupo" },
                            location = location.ifBlank { "Centro" },
                            meetingPoint = meetingPoint.ifBlank { "Entrada Principal" },
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
                Text("Guardar en Calendario")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun StatusChoiceRow(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else Color.Transparent),
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface,
                fontSize = 13.sp
            )
        }
    }
}
