package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class InternalEvent(
    val id: String, 
    val date: LocalDate, 
    val title: String,
    val time: String = "",
    val itemsToBring: String = "",
    val budget: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showAddEvent by remember { mutableStateOf(false) }
    var newEventTitle by remember { mutableStateOf("") }
    var newEventTime by remember { mutableStateOf("") }
    var newEventItems by remember { mutableStateOf("") }
    var newEventBudget by remember { mutableStateOf("") }
    var internalEvents by remember { mutableStateOf(listOf<InternalEvent>()) }
    
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7 // Sunday = 0

    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Programas", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    TextButton(onClick = { currentMonth = YearMonth.now(); selectedDate = LocalDate.now() }) {
                        Text("Hoy", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    showAddEvent = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir evento")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // Month Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mes anterior")
                }
                Text(
                    text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es", "ES"))).capitalize(Locale("es", "ES")),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Mes siguiente")
                }
            }

            // Days of week
            val daysOfWeek = listOf("DOM", "LUN", "MAR", "MIE", "JUE", "VIE", "SAB")
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                daysOfWeek.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Calendar Grid Container (Neo-brutalist card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(firstDayOfWeek) {
                        Box(modifier = Modifier.aspectRatio(1f))
                    }
                    items(daysInMonth) { dayIndex ->
                        val day = dayIndex + 1
                        val date = currentMonth.atDay(day)
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        isToday -> MaterialTheme.colorScheme.primary
                                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    if (isSelected && !isToday) 2.dp else 0.dp,
                                    if (isSelected && !isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedDate = date },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = day.toString(),
                                color = when {
                                    isToday -> MaterialTheme.colorScheme.onPrimary
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Selected Date Details
            Text(
                text = "EVENTOS DEL ${selectedDate.format(DateTimeFormatter.ofPattern("dd MMM"))}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth().padding(bottom = 8.dp)
            )
            
            // Events List
            val selectedDateEvents = internalEvents.filter { it.date == selectedDate }
            if (selectedDateEvents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                        .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay programas guardados para este día.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    items(selectedDateEvents) { event ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(event.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                if (event.time.isNotBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(event.time, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (event.itemsToBring.isNotBlank() || event.budget.isNotBlank()) {
                                Column(modifier = Modifier.padding(start = 44.dp, bottom = 16.dp, end = 16.dp)) {
                                    if (event.itemsToBring.isNotBlank()) {
                                        Text("Llevar: ${event.itemsToBring}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (event.budget.isNotBlank()) {
                                        Text("Presupuesto: ${event.budget}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddEvent) {
            AlertDialog(
                onDismissRequest = { showAddEvent = false },
                title = { Text("Añadir Programa") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newEventTitle,
                            onValueChange = { newEventTitle = it },
                            label = { Text("Título del programa") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEventTime,
                            onValueChange = { newEventTime = it },
                            label = { Text("Hora (ej. 18:00)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEventItems,
                            onValueChange = { newEventItems = it },
                            label = { Text("¿Qué llevar?") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        OutlinedTextField(
                            value = newEventBudget,
                            onValueChange = { newEventBudget = it },
                            label = { Text("Presupuesto / Aporte") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newEventTitle.isNotBlank()) {
                            val newEvent = InternalEvent(
                                id = java.util.UUID.randomUUID().toString(),
                                date = selectedDate,
                                title = newEventTitle,
                                time = newEventTime,
                                itemsToBring = newEventItems,
                                budget = newEventBudget
                            )
                            internalEvents = internalEvents + newEvent
                            newEventTitle = ""
                            newEventTime = ""
                            newEventItems = ""
                            newEventBudget = ""
                            showAddEvent = false
                        }
                    }) {
                        Text("Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddEvent = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
