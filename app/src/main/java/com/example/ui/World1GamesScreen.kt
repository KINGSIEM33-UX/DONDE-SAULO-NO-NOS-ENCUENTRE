package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GeminiClient
import com.example.ui.theme.StatusCancelledRed
import com.example.ui.theme.StatusConfirmedGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.vector.ImageVector

internal fun localTruthOrDareFallback(dares: List<String>): String = "🔥 RETO:\n\n${dares.random()}"

private data class MiniGameMeta(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val subtitle: String,
    val badge: String
)

private val MINI_GAMES_LIST = listOf(
    MiniGameMeta(
        id = "impostor",
        title = "El Impostor",
        icon = Icons.Default.VisibilityOff,
        subtitle = "Descubre quién no conoce la palabra secreta",
        badge = "3–10 J"
    ),
    MiniGameMeta(
        id = "2048",
        title = "2048 Arcade",
        icon = Icons.Default.Grid4x4,
        subtitle = "Desliza y fusiona fichas hasta 2048",
        badge = "Lógica"
    ),
    MiniGameMeta(
        id = "charadas",
        title = "Charadas 60s",
        icon = Icons.Default.TheaterComedy,
        subtitle = "Actúa o describe antes de que acabe el reloj",
        badge = "Grupo"
    ),
    MiniGameMeta(
        id = "verdad_reto",
        title = "Verdad o Reto",
        icon = Icons.Default.Whatshot,
        subtitle = "Preguntas y retos con generador Gemini IA",
        badge = "Con IA"
    ),
    MiniGameMeta(
        id = "trivia",
        title = "Trivia Relámpago",
        icon = Icons.Default.Bolt,
        subtitle = "10 preguntas rápidas de cultura general",
        badge = "10 Rondas"
    ),
    MiniGameMeta(
        id = "tictactoe",
        title = "Tres en Raya",
        icon = Icons.Default.GridOn,
        subtitle = "Duelo clásico 2 jugadores o contra la IA",
        badge = "2J / IA"
    ),
    MiniGameMeta(
        id = "memory",
        title = "Memoria Express",
        icon = Icons.Default.Psychology,
        subtitle = "Encuentra las 6 parejas ocultas",
        badge = "Memoria"
    ),
    MiniGameMeta(
        id = "ruleta",
        title = "Ruleta del Grupo",
        icon = Icons.Default.Casino,
        subtitle = "Decide al azar entre opciones o miembros",
        badge = "Azar"
    )
)

@Composable
fun World1GamesScreen(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val members by viewModel.groupMembers.collectAsState()
    var activeGameId by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = activeGameId != null) {
        activeGameId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("world1_games_hub_screen")
    ) {
        if (activeGameId == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    SplitTextReveal(
                        text = "Juegos",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "8 dinámicas diseñadas para tus encuentros",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        CountUpText(
                            targetValue = profile.gamesHighScore.toInt(),
                            suffix = " pts",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(MINI_GAMES_LIST, key = { _, g -> g.id }) { index, game ->
                    AnimatedStaggeredItem(index = index, staggerMs = 40) {
                        SpotlightEditorialCard(
                            onClick = { activeGameId = game.id },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(164.dp)
                                .testTag("game_card_${game.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    EditorialIconBadge(
                                        icon = game.icon,
                                        size = 38.dp,
                                        iconTint = MaterialTheme.colorScheme.secondary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Text(
                                            text = game.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        text = game.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = game.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val activeMeta = MINI_GAMES_LIST.first { it.id == activeGameId }
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { activeGameId = null },
                        modifier = Modifier.size(42.dp).testTag("game_back_to_hub_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a juegos")
                    }
                    EditorialIconBadge(icon = activeMeta.icon, size = 34.dp, iconTint = MaterialTheme.colorScheme.secondary)
                    Text(
                        text = activeMeta.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                when (activeGameId) {
                    "impostor" -> ImpostorGameView(members = members, onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "2048" -> Game2048View(onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "charadas" -> CharadesGameView(onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "verdad_reto" -> TruthOrDareGameView(onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "trivia" -> LightningTriviaGameView(onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "tictactoe" -> TicTacToeGameView(onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "memory" -> MemoryExpressGameView(onEarnPoints = { viewModel.addMiniGameScore(it) })
                    "ruleta" -> GroupRouletteGameView(members = members, onEarnPoints = { viewModel.addMiniGameScore(it) })
                }
            }
        }
    }
}

// ============================================================================
// GAME 1: EL IMPOSTOR (3-10 PLAYERS + 32 WORDS + REVEAL)
// ============================================================================
@Composable
private fun ImpostorGameView(members: List<String>, onEarnPoints: (Int) -> Unit) {
    val words = remember {
        listOf(
            "Bolera" to "Ocio", "Pizza de Pepperoni" to "Comida", "Escape Room" to "Ocio",
            "Cine al Aire Libre" to "Ocio", "Montaña Rusa" to "Parque", "Playa al Atardecer" to "Viaje",
            "Concierto de Rock" to "Música", "Hamburguesería" to "Comida", "Aeropuerto" to "Viaje",
            "Submarino" to "Transporte", "Estadio de Fútbol" to "Deporte", "Sushi" to "Comida",
            "Biblioteca" to "Lugar", "Camping en el Bosque" to "Naturaleza", "Museo de Arte" to "Cultura",
            "Karaoke" to "Fiesta", "Pista de Patinaje" to "Deporte", "Acuario" to "Lugar",
            "Tacos Mexicanos" to "Comida", "Heladería" to "Comida", "Teleférico" to "Transporte",
            "Castillo Medieval" to "Historia", "Gimnasio" to "Deporte", "Cafetería" to "Lugar",
            "Circo" to "Espectáculo", "Planetario" to "Ciencia", "Parque Acuático" to "Ocio",
            "Pizzería Italiana" to "Comida", "Tren Bala" to "Transporte", "Feria de Pueblo" to "Fiesta",
            "Cueva Subterránea" to "Naturaleza", "Barco Pirata" to "Aventura"
        )
    }

    var playerCount by remember { mutableIntStateOf(members.size.coerceIn(3, 10)) }
    var currentRoundWord by remember { mutableStateOf(words.random()) }
    var impostorIdx by remember { mutableIntStateOf((0 until playerCount).random()) }
    var currentPlayerIdx by remember { mutableIntStateOf(0) }
    var isCardRevealed by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("SETUP") } // SETUP, PASSING, DEBATE, REVEALED

    fun playerLabel(idx: Int): String = members.getOrNull(idx) ?: "Jugador ${idx + 1}"

    fun startRound() {
        currentRoundWord = words.random()
        impostorIdx = (0 until playerCount).random()
        currentPlayerIdx = 0
        isCardRevealed = false
        phase = "PASSING"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (phase) {
            "SETUP" -> {
                Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Configura la partida 🕵️‍♂️", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(
                            "Pasa el móvil entre los jugadores. Todos verán la misma palabra secreta excepto 1 Impostor.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { if (playerCount > 3) playerCount-- },
                                enabled = playerCount > 3
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Menos jugadores")
                            }
                            Text(
                                text = "$playerCount Jugadores",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            FilledTonalButton(
                                onClick = { if (playerCount < 10) playerCount++ },
                                enabled = playerCount < 10
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Más jugadores")
                            }
                        }
                        Button(
                            onClick = { startRound() },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Repartir Roles", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "PASSING" -> {
                Text(
                    text = "Turno ${currentPlayerIdx + 1} de $playerCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Pasa el móvil a: ${playerLabel(currentPlayerIdx)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center
                        )

                        if (isCardRevealed) {
                            val isImpostor = currentPlayerIdx == impostorIdx
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (isImpostor) StatusCancelledRed.copy(alpha = 0.16f) else StatusConfirmedGreen.copy(alpha = 0.16f),
                                border = BorderStroke(2.dp, if (isImpostor) StatusCancelledRed else StatusConfirmedGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isImpostor) "🕵️‍♂️ ¡ERES EL IMPOSTOR!" else "✅ PALABRA SECRETA",
                                        fontWeight = FontWeight.Black,
                                        color = if (isImpostor) StatusCancelledRed else StatusConfirmedGreen,
                                        fontSize = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isImpostor) {
                                            "Pista de categoría: ${currentRoundWord.second}\nDisimula y descubre la palabra."
                                        } else {
                                            "${currentRoundWord.first}\n(Categoría: ${currentRoundWord.second})"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 19.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    isCardRevealed = false
                                    if (currentPlayerIdx < playerCount - 1) {
                                        currentPlayerIdx++
                                    } else {
                                        phase = "DEBATE"
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Text(
                                    if (currentPlayerIdx < playerCount - 1) "Ocultar y pasar al siguiente" else "Ocultar e iniciar debate",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { isCardRevealed = true },
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            ) {
                                Text("Tocar para ver mi rol secreto", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            "DEBATE" -> {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🗣️ ¡Tiempo de Debate!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(
                            text = "Cada jugador dice 1 pista sobre la palabra sin ser demasiado obvio. Luego votad quién creéis que es el Impostor.",
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = {
                                onEarnPoints(30)
                                phase = "REVEALED"
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text("Revelar al Impostor (+30 pts)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            "REVEALED" -> {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, StatusCancelledRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🕵️‍♂️ El Impostor era:", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = playerLabel(impostorIdx),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = StatusCancelledRed
                        )
                        Text(
                            text = "Palabra secreta: ${currentRoundWord.first}",
                            fontWeight = FontWeight.Bold,
                            color = StatusConfirmedGreen
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { phase = "SETUP" }, modifier = Modifier.weight(1f)) {
                                Text("Cambiar jugadores")
                            }
                            Button(onClick = { startRound() }, modifier = Modifier.weight(1f)) {
                                Text("Nueva Ronda")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// GAME 2: 2048 ARCADE (SWIPE GESTURES + VALID MOVE CHECK + WIN / GAME OVER)
// ============================================================================
@Composable
private fun Game2048View(onEarnPoints: (Int) -> Unit) {
    fun spawnTile(board: List<Int>): List<Int> {
        val empties = board.indices.filter { board[it] == 0 }
        if (empties.isEmpty()) return board
        val next = board.toMutableList()
        next[empties.random()] = if ((0..9).random() == 0) 4 else 2
        return next
    }

    fun initialBoard(): List<Int> = spawnTile(spawnTile(List(16) { 0 }))

    fun canMove(board: List<Int>): Boolean {
        if (board.any { it == 0 }) return true
        for (r in 0..3) {
            for (c in 0..3) {
                val v = board[r * 4 + c]
                if (c < 3 && board[r * 4 + c + 1] == v) return true
                if (r < 3 && board[(r + 1) * 4 + c] == v) return true
            }
        }
        return false
    }

    var board by remember { mutableStateOf(initialBoard()) }
    var score by remember { mutableIntStateOf(0) }
    var reached2048 by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }

    fun slideLine(line: List<Int>): Pair<List<Int>, Int> {
        val nonZero = line.filter { it != 0 }.toMutableList()
        var gained = 0
        val merged = mutableListOf<Int>()
        var i = 0
        while (i < nonZero.size) {
            if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
                val doubled = nonZero[i] * 2
                merged.add(doubled)
                gained += doubled
                i += 2
            } else {
                merged.add(nonZero[i])
                i++
            }
        }
        while (merged.size < 4) merged.add(0)
        return merged to gained
    }

    fun move(direction: String) {
        if (isGameOver) return
        val next = board.toMutableList()
        var totalGained = 0
        when (direction) {
            "LEFT" -> for (r in 0..3) {
                val (res, g) = slideLine((0..3).map { board[r * 4 + it] })
                totalGained += g
                for (c in 0..3) next[r * 4 + c] = res[c]
            }
            "RIGHT" -> for (r in 0..3) {
                val (res, g) = slideLine((3 downTo 0).map { board[r * 4 + it] })
                totalGained += g
                for (c in 0..3) next[r * 4 + (3 - c)] = res[c]
            }
            "UP" -> for (c in 0..3) {
                val (res, g) = slideLine((0..3).map { board[it * 4 + c] })
                totalGained += g
                for (r in 0..3) next[r * 4 + c] = res[r]
            }
            "DOWN" -> for (c in 0..3) {
                val (res, g) = slideLine((3 downTo 0).map { board[it * 4 + c] })
                totalGained += g
                for (r in 0..3) next[(3 - r) * 4 + c] = res[r]
            }
        }
        // Only spawn a new tile if the board actually changed!
        if (next != board) {
            val spawned = spawnTile(next)
            board = spawned
            if (totalGained > 0) {
                score += totalGained
                onEarnPoints((totalGained / 4).coerceAtLeast(1))
            }
            if (!reached2048 && spawned.any { it >= 2048 }) {
                reached2048 = true
                onEarnPoints(100)
            }
            if (!canMove(spawned)) {
                isGameOver = true
            }
        }
    }

    var dragAccumX by remember { mutableStateOf(0f) }
    var dragAccumY by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Puntuación: $score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            FilledTonalButton(
                onClick = {
                    board = initialBoard()
                    score = 0
                    reached2048 = false
                    isGameOver = false
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reiniciar")
            }
        }

        if (reached2048) {
            Surface(shape = RoundedCornerShape(14.dp), color = StatusConfirmedGreen.copy(alpha = 0.2f)) {
                Text(
                    "🏆 ¡Alcanzaste 2048! (+100 pts) Puedes seguir sumando.",
                    color = StatusConfirmedGreen,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
        if (isGameOver) {
            Surface(shape = RoundedCornerShape(14.dp), color = StatusCancelledRed.copy(alpha = 0.18f)) {
                Text(
                    "💥 Fin de partida • Puntuación final: $score",
                    color = StatusCancelledRed,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        // Swipeable 4x4 Grid
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFBBADA0))
                .pointerInput(isGameOver) {
                    detectDragGestures(
                        onDragStart = {
                            dragAccumX = 0f
                            dragAccumY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumX += dragAmount.x
                            dragAccumY += dragAmount.y
                        },
                        onDragEnd = {
                            val threshold = 45f
                            if (abs(dragAccumX) > abs(dragAccumY) && abs(dragAccumX) > threshold) {
                                move(if (dragAccumX > 0) "RIGHT" else "LEFT")
                            } else if (abs(dragAccumY) > threshold) {
                                move(if (dragAccumY > 0) "DOWN" else "UP")
                            }
                        }
                    )
                }
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0..3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        for (c in 0..3) {
                            val v = board[r * 4 + c]
                            val bg = when (v) {
                                0 -> Color(0xFFCDC1B4)
                                2 -> Color(0xFFEEE4DA)
                                4 -> Color(0xFFEDE0C8)
                                8 -> Color(0xFFF2B179)
                                16 -> Color(0xFFF59563)
                                32 -> Color(0xFFF67C5F)
                                64 -> Color(0xFFF65E3B)
                                128 -> Color(0xFFEDCF72)
                                256 -> Color(0xFFEDCC61)
                                else -> Color(0xFFEDC22E)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg),
                                contentAlignment = Alignment.Center
                            ) {
                                if (v > 0) {
                                    Text(
                                        text = v.toString(),
                                        fontSize = if (v >= 1024) 16.sp else 20.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (v <= 4) Color(0xFF776E65) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("Desliza el tablero o usa los botones:", style = MaterialTheme.typography.labelSmall)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { move("UP") }, modifier = Modifier.width(120.dp).height(46.dp)) { Text("⬆️ Arriba") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { move("LEFT") }, modifier = Modifier.width(120.dp).height(46.dp)) { Text("⬅️ Izq") }
                Button(onClick = { move("RIGHT") }, modifier = Modifier.width(120.dp).height(46.dp)) { Text("Der ➡️") }
            }
            Button(onClick = { move("DOWN") }, modifier = Modifier.width(120.dp).height(46.dp)) { Text("⬇️ Abajo") }
        }
    }
}

// ============================================================================
// GAME 3: CHARADAS 60s (36 SHUFFLED WORDS, NO REPEATS, ROUND SUMMARY)
// ============================================================================
@Composable
private fun CharadesGameView(onEarnPoints: (Int) -> Unit) {
    val allWords = remember {
        listOf(
            "Hacer Pizza", "Subir una Montaña", "Jugar a los Bolos", "Surfear una Ola",
            "Tocar la Guitarra", "Bailar Salsa", "Conducir un Kart", "Sacar una Selfie",
            "Escapar de un Laberinto", "Pintar un Cuadro", "Pescar un Tiburón", "Saltar en Paracaídas",
            "Cantar Ópera", "Jugar al Tenis", "Cocinar Tacos", "Hacer Yoga",
            "Caminar en la Luna", "Esquiar en la Nieve", "Montar a Caballo", "Hacer Magia",
            "Apagar un Incendio", "Dirigir una Orquesta", "Boxear", "Tomar un Café Caliente",
            "Cambiar una Rueda", "Pasear a un Perro", "Nadar con Delfines", "Hacer Malabares",
            "Atrapado en un Ascensor", "Leer el Futuro", "Tocar la Batería", "Patinar sobre Hielo",
            "Armar una Tienda de Campaña", "Desfilar en Pasarela", "Hacer Fotos en un Safari", "Jugar Videojuegos"
        )
    }
    var deck by remember { mutableStateOf(allWords.shuffled()) }
    var wordIndex by remember { mutableIntStateOf(0) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(false) }
    var roundFinished by remember { mutableStateOf(false) }
    var hits by remember { mutableIntStateOf(0) }
    var skips by remember { mutableIntStateOf(0) }

    fun nextWord() {
        if (wordIndex + 1 < deck.size) {
            wordIndex++
        } else {
            deck = allWords.shuffled()
            wordIndex = 0
        }
    }

    LaunchedEffect(isRunning) {
        while (isRunning && secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        if (isRunning && secondsLeft == 0) {
            isRunning = false
            roundFinished = true
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "⏱️ ${secondsLeft}s   |   ✅ Aciertos: $hits",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().height(190.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = when {
                        isRunning -> deck[wordIndex]
                        roundFinished -> "🏁 ¡Tiempo terminado!\nAciertos: $hits • Pasadas: $skips"
                        else -> "Pulsa Empezar y actúa para tu grupo 🎭"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!isRunning) {
            Button(
                onClick = {
                    deck = allWords.shuffled()
                    wordIndex = 0
                    secondsLeft = 60
                    hits = 0
                    skips = 0
                    roundFinished = false
                    isRunning = true
                },
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(if (roundFinished) "Jugar otra ronda (60s)" else "Empezar Ronda de 60s", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        skips++
                        nextWord()
                    },
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("Pasar ⏭️")
                }
                Button(
                    onClick = {
                        hits++
                        onEarnPoints(10)
                        nextWord()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusConfirmedGreen),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) {
                    Text("¡Acertado! +10")
                }
            }
        }
    }
}

// ============================================================================
// GAME 4: VERDAD O RETO IA (48 PROMPTS + REAL GEMINI GENERATOR + FAIR SCORING)
// ============================================================================
@Composable
private fun TruthOrDareGameView(onEarnPoints: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    val truths = remember {
        listOf(
            "¿Cuál ha sido la salida más épica que has vivido con este grupo?",
            "¿Qué canción cantas a todo volumen cuando nadie te escucha?",
            "¿Cuál es tu comida favorita que nunca compartirías?",
            "¿Cuál fue tu momento más divertido o vergonzoso en un viaje?",
            "¿A qué lugar del mundo te gustaría organizar la próxima escapada?",
            "¿Quién del grupo tarda siempre más en llegar a las quedadas?",
            "¿Qué talento oculto tienes que casi nadie sabe?",
            "¿Cuál es la película que has visto más veces en tu vida?",
            "¿Qué hábito raro tienes antes de dormir?",
            "¿Cuál ha sido el mejor consejo que te han dado?",
            "Si pudieras cenar con cualquier personaje histórico, ¿quién sería?",
            "¿Qué cosa pequeña te alegra el día al instante?",
            "¿Cuál fue tu primer trabajo o reto importante?",
            "¿Qué comida probaste pensando que no te gustaría y ahora te encanta?",
            "¿Cuál es tu recuerdo favorito de la infancia?",
            "¿Qué habilidad te gustaría dominar en 24 horas?",
            "¿Cuál es la anécdota más graciosa que recuerdas con tus amigos?",
            "¿Prefieres montaña, playa o ciudad para una semana entera?",
            "¿Cuál es la serie o libro que más te ha marcado?",
            "¿Qué es lo más espontáneo que has hecho este año?",
            "¿A qué persona del grupo llamarías primero si te pierdes en otra ciudad?",
            "¿Cuál es tu juego de mesa o videojuego invencible?",
            "¿Qué idioma te encantaría hablar con fluidez?",
            "¿Qué meta te haría más ilusión cumplir antes de fin de año?"
        )
    }
    val dares = remember {
        listOf(
            "Imita durante 15 segundos a alguien del grupo sin decir su nombre.",
            "Cuenta un chiste malo intentando no reírte ni un segundo.",
            "Haz 10 sentadillas mientras dices 5 ciudades del mundo.",
            "Habla con acento extranjero hasta que acabe el siguiente turno.",
            "Improvisa un rap de 4 versos sobre la próxima salida del grupo.",
            "Saca una foto grupal épica con la pose más creativa posible.",
            "Canta el estribillo de tu canción favorita a capella.",
            "Haz equilibrio sobre un pie durante 20 segundos contando hacia atrás desde 30.",
            "Describe tu película favorita usando solo 3 palabras.",
            "Envía un emoji misterioso al chat del grupo y deja que adivinen qué significa.",
            "Haz un brindis épico y formal por la amistad del grupo.",
            "Dibuja en el aire un animal hasta que alguien lo adivine.",
            "Di el abecedario al revés lo más rápido que puedas.",
            "Nombra 6 países de Europa en menos de 8 segundos.",
            "Actúa como un presentador de noticias anunciando la próxima quedada.",
            "Mantén una cara totalmente seria durante 20 segundos mientras el grupo intenta hacerte reír.",
            "Inventa un saludo secreto nuevo con la persona de tu derecha.",
            "Di 3 cumplidos genuinos a 3 personas del grupo.",
            "Haz 5 pasos de baile inventados en el momento.",
            "Habla durante 15 segundos sin usar la letra 'A'.",
            "Imita el sonido de 3 animales distintos seguidos.",
            "Crea un lema oficial para el grupo en 10 segundos.",
            "Recomienda una canción y explica en 1 frase por qué todos deben escucharla.",
            "Haz de estatua sin moverte hasta que otro jugador diga tu nombre."
        )
    }

    var currentCard by remember { mutableStateOf("Elige Verdad, Reto o genera uno nuevo con Gemini IA 🔥") }
    var canClaimPoints by remember { mutableStateOf(false) }
    var isGeneratingAi by remember { mutableStateOf(false) }
    var usedTruthIdx by remember { mutableIntStateOf(-1) }
    var usedDareIdx by remember { mutableIntStateOf(-1) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            modifier = Modifier.fillMaxWidth().height(210.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                if (isGeneratingAi) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Gemini está creando un reto original...", fontSize = 12.sp)
                    }
                } else {
                    Text(
                        text = currentCard,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val next = (truths.indices).filter { it != usedTruthIdx }.random()
                    usedTruthIdx = next
                    currentCard = "💬 VERDAD:\n\n${truths[next]}"
                    canClaimPoints = true
                },
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text("Verdad 💬", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    val next = (dares.indices).filter { it != usedDareIdx }.random()
                    usedDareIdx = next
                    currentCard = "🔥 RETO:\n\n${dares[next]}"
                    canClaimPoints = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Text("Reto 🔥", fontWeight = FontWeight.Bold)
            }
        }

        FilledTonalButton(
            onClick = {
                if (isGeneratingAi) return@FilledTonalButton
                isGeneratingAi = true
                scope.launch {
                    val aiText = GeminiClient.generateQuickPrompt(
                        "Genera 1 único reto o pregunta de Verdad o Reto divertido, sano y original para un grupo de amigos en una quedada. Responde solo con el texto en español precedido por '✨ IA: '."
                    )
                    currentCard = aiText ?: localTruthOrDareFallback(dares)
                    canClaimPoints = true
                    isGeneratingAi = false
                }
            },
            enabled = !isGeneratingAi,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Generar reto único con Gemini IA")
        }

        if (canClaimPoints) {
            Button(
                onClick = {
                    onEarnPoints(15)
                    canClaimPoints = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = StatusConfirmedGreen),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("✅ ¡Cumplido! Reclamar +15 pts", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ============================================================================
// GAME 5: TRIVIA RELÁMPAGO (20 SHUFFLED QUESTIONS + 10-QUESTION ROUNDS)
// ============================================================================
@Composable
private fun LightningTriviaGameView(onEarnPoints: (Int) -> Unit) {
    val allQuestions = remember {
        listOf(
            Triple("¿Cuál es el planeta más grande del Sistema Solar?", listOf("Marte", "Júpiter", "Saturno"), 1),
            Triple("¿En qué país se inventó la pizza moderna?", listOf("Italia", "Francia", "Grecia"), 0),
            Triple("¿Cuántos jugadores tiene un equipo de fútbol en el campo?", listOf("9", "11", "12"), 1),
            Triple("¿Cuál es el océano más grande del planeta?", listOf("Atlántico", "Índico", "Pacífico"), 2),
            Triple("¿Qué gas respiramos principalmente en la atmósfera terrestre?", listOf("Nitrógeno", "Oxígeno", "Helio"), 0),
            Triple("¿Cuál es la capital de Japón?", listOf("Kioto", "Osaka", "Tokio"), 2),
            Triple("¿Cuántos continentes tiene la Tierra?", listOf("5", "6 o 7 según modelo", "4"), 1),
            Triple("¿Quién pintó La Noche Estrellada?", listOf("Pablo Picasso", "Vincent van Gogh", "Salvador Dalí"), 1),
            Triple("¿Cuál es el río más caudaloso del mundo?", listOf("Nilo", "Amazonas", "Danubio"), 1),
            Triple("¿Qué metal es líquido a temperatura ambiente?", listOf("Mercurio", "Aluminio", "Cobre"), 0),
            Triple("¿En qué año llegó el ser humano a la Luna por primera vez?", listOf("1959", "1969", "1975"), 1),
            Triple("¿Cuántas cuerdas tiene una guitarra clásica estándar?", listOf("5", "6", "8"), 1),
            Triple("¿Qué animal terrestre es el más rápido del mundo?", listOf("Guepardo", "León", "Antílope"), 0),
            Triple("¿Cuál es el hueso más largo del cuerpo humano?", listOf("Tibia", "Húmero", "Fémur"), 2),
            Triple("¿En qué continente está el desierto del Sahara?", listOf("Asia", "África", "América"), 1),
            Triple("¿Cuál es la montaña más alta del mundo sobre el nivel del mar?", listOf("K2", "Everest", "Aconcagua"), 1),
            Triple("¿Qué instrumento mide la presión atmosférica?", listOf("Barómetro", "Termómetro", "Anemómetro"), 0),
            Triple("¿Cuántos minutos tiene un día completo?", listOf("1.240", "1.440", "1.640"), 1),
            Triple("¿Cuál es el idioma oficial de Brasil?", listOf("Español", "Portugués", "Italiano"), 1),
            Triple("¿Qué vitamina produce principalmente el cuerpo con la luz solar?", listOf("Vitamina C", "Vitamina D", "Vitamina K"), 1)
        )
    }

    var roundQuestions by remember { mutableStateOf(allQuestions.shuffled().take(10)) }
    var qIdx by remember { mutableIntStateOf(0) }
    var selectedOpt by remember { mutableStateOf<Int?>(null) }
    var correctCount by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    if (finished) {
        Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("⚡ ¡Ronda completada!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Aciertos: $correctCount / ${roundQuestions.size}", style = MaterialTheme.typography.titleLarge, color = StatusConfirmedGreen, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        roundQuestions = allQuestions.shuffled().take(10)
                        qIdx = 0
                        selectedOpt = null
                        correctCount = 0
                        finished = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Jugar otra ronda de 10")
                }
            }
        }
        return
    }

    val (q, opts, correct) = roundQuestions[qIdx]

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Pregunta ${qIdx + 1} / ${roundQuestions.size}   •   Aciertos: $correctCount",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = q,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(20.dp)
            )
        }
        opts.forEachIndexed { idx, opt ->
            val bgColor = when {
                selectedOpt == null -> MaterialTheme.colorScheme.surfaceVariant
                idx == correct -> StatusConfirmedGreen
                idx == selectedOpt -> StatusCancelledRed
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val fgColor = if (selectedOpt != null && (idx == correct || idx == selectedOpt)) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                onClick = {
                    if (selectedOpt == null) {
                        selectedOpt = idx
                        if (idx == correct) {
                            correctCount++
                            onEarnPoints(20)
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                contentColor = fgColor,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(opt, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (selectedOpt != null) {
            Button(
                onClick = {
                    if (qIdx + 1 < roundQuestions.size) {
                        qIdx++
                        selectedOpt = null
                    } else {
                        finished = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(if (qIdx + 1 < roundQuestions.size) "Siguiente Pregunta →" else "Ver Resultado Final")
            }
        }
    }
}

// ============================================================================
// GAME 6: TRES EN RAYA (2 PLAYERS OR UNBEATABLE MINIMAX AI)
// ============================================================================
@Composable
private fun TicTacToeGameView(onEarnPoints: (Int) -> Unit) {
    val winLines = remember {
        listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
    }

    fun checkWinner(b: List<String>): String? {
        winLines.forEach { (a, bIdx, c) ->
            if (b[a].isNotEmpty() && b[a] == b[bIdx] && b[a] == b[c]) return b[a]
        }
        if (b.none { it.isEmpty() }) return "EMPATE"
        return null
    }

    fun minimax(b: MutableList<String>, isMax: Boolean): Int {
        when (checkWinner(b)) {
            "O" -> return 10
            "X" -> return -10
            "EMPATE" -> return 0
        }
        if (isMax) {
            var best = Int.MIN_VALUE
            for (i in 0..8) {
                if (b[i].isEmpty()) {
                    b[i] = "O"
                    best = maxOf(best, minimax(b, false))
                    b[i] = ""
                }
            }
            return best
        } else {
            var best = Int.MAX_VALUE
            for (i in 0..8) {
                if (b[i].isEmpty()) {
                    b[i] = "X"
                    best = minOf(best, minimax(b, true))
                    b[i] = ""
                }
            }
            return best
        }
    }

    fun bestAiMove(b: List<String>): Int {
        val copy = b.toMutableList()
        var bestScore = Int.MIN_VALUE
        var move = -1
        for (i in 0..8) {
            if (copy[i].isEmpty()) {
                copy[i] = "O"
                val sc = minimax(copy, false)
                copy[i] = ""
                if (sc > bestScore) {
                    bestScore = sc
                    move = i
                }
            }
        }
        return move
    }

    var vsAi by remember { mutableStateOf(true) }
    var board by remember { mutableStateOf(List(9) { "" }) }
    var turnX by remember { mutableStateOf(true) }
    var winner by remember { mutableStateOf<String?>(null) }
    var winsX by remember { mutableIntStateOf(0) }
    var winsO by remember { mutableIntStateOf(0) }
    var draws by remember { mutableIntStateOf(0) }

    fun resetBoard() {
        board = List(9) { "" }
        turnX = true
        winner = null
    }

    // Trigger AI move when playing vs IA and it's O's turn
    LaunchedEffect(vsAi, turnX, winner) {
        if (vsAi && !turnX && winner == null) {
            delay(320L)
            val aiMove = bestAiMove(board)
            if (aiMove in 0..8) {
                val next = board.toMutableList()
                next[aiMove] = "O"
                board = next
                val w = checkWinner(next)
                if (w != null) {
                    winner = w
                    if (w == "O") winsO++ else if (w == "EMPATE") { draws++; onEarnPoints(15) }
                } else {
                    turnX = true
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                onClick = { vsAi = true; resetBoard() },
                modifier = Modifier.weight(1f)
            ) {
                Text("🤖 vs IA", fontWeight = if (vsAi) FontWeight.Black else FontWeight.Normal)
            }
            FilledTonalButton(
                onClick = { vsAi = false; resetBoard() },
                modifier = Modifier.weight(1f)
            ) {
                Text("👥 2 Jugadores", fontWeight = if (!vsAi) FontWeight.Black else FontWeight.Normal)
            }
        }

        Text(
            text = "❌ $winsX   •   🤝 $draws   •   ⭕ $winsO",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = when (winner) {
                "EMPATE" -> "🤝 ¡Empate!"
                null -> if (turnX) "Turno de ❌" else if (vsAi) "Pensando IA ⭕..." else "Turno de ⭕"
                else -> "🏆 ¡Ganó $winner!"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0..2) {
                        val idx = r * 3 + c
                        Surface(
                            onClick = {
                                if (winner == null && board[idx].isEmpty() && (!vsAi || turnX)) {
                                    val next = board.toMutableList()
                                    next[idx] = if (turnX) "X" else "O"
                                    board = next
                                    val w = checkWinner(next)
                                    if (w != null) {
                                        winner = w
                                        when (w) {
                                            "X" -> { winsX++; onEarnPoints(25) }
                                            "O" -> { winsO++; onEarnPoints(25) }
                                            "EMPATE" -> { draws++; onEarnPoints(15) }
                                        }
                                    } else {
                                        turnX = !turnX
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(92.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (board[idx]) {
                                        "X" -> "❌"
                                        "O" -> "⭕"
                                        else -> ""
                                    },
                                    fontSize = 36.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = { resetBoard() }) {
            Text("Nueva Partida")
        }
    }
}

// ============================================================================
// GAME 7: MEMORIA EXPRESS (FIXED 750ms FLIP-BACK DELAY + MOVES + WIN STATE)
// ============================================================================
@Composable
private fun MemoryExpressGameView(onEarnPoints: (Int) -> Unit) {
    val baseIcons = remember { listOf("🍕", "🎳", "🏔️", "🎸", "📸", "🎮") }
    var cards by remember { mutableStateOf((baseIcons + baseIcons).shuffled()) }
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var matched by remember { mutableStateOf(setOf<Int>()) }
    var isMismatchLocked by remember { mutableStateOf(false) }
    var moves by remember { mutableIntStateOf(0) }
    var hasWon by remember { mutableStateOf(false) }

    fun restart() {
        cards = (baseIcons + baseIcons).shuffled()
        revealed = emptySet()
        matched = emptySet()
        isMismatchLocked = false
        moves = 0
        hasWon = false
    }

    // Properly show both mismatched cards for 750ms before flipping them back!
    LaunchedEffect(revealed) {
        if (revealed.size == 2) {
            val (first, second) = revealed.toList()
            moves++
            if (cards[first] == cards[second]) {
                val nextMatched = matched + first + second
                matched = nextMatched
                revealed = emptySet()
                if (nextMatched.size == cards.size) {
                    hasWon = true
                    onEarnPoints(40)
                }
            } else {
                isMismatchLocked = true
                delay(750L)
                revealed = emptySet()
                isMismatchLocked = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Parejas: ${matched.size / 2}/${baseIcons.size}  •  Intentos: $moves",
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(onClick = { restart() }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Reiniciar")
            }
        }

        if (hasWon) {
            Surface(shape = RoundedCornerShape(14.dp), color = StatusConfirmedGreen.copy(alpha = 0.2f)) {
                Text(
                    "🎉 ¡Completado en $moves intentos! (+40 pts)",
                    color = StatusConfirmedGreen,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (r in 0..2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (c in 0..3) {
                        val idx = r * 4 + c
                        val isOpen = idx in matched || idx in revealed
                        Surface(
                            onClick = {
                                if (!isMismatchLocked && !isOpen && revealed.size < 2) {
                                    revealed = revealed + idx
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = when {
                                idx in matched -> StatusConfirmedGreen.copy(alpha = 0.28f)
                                isOpen -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(74.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isOpen) cards[idx] else "❓",
                                    fontSize = 28.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// GAME 8: RULETA DEL GRUPO (ANIMATED SPINNING WHEEL + EDITABLE + GROUP MEMBERS)
// ============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupRouletteGameView(members: List<String>, onEarnPoints: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var options by remember {
        mutableStateOf(
            listOf(
                "¡Invita a los helados! 🍦",
                "Elige la próxima peli 🎬",
                "Pone la música del coche 🎶",
                "Saca la foto oficial 📸",
                "Propone la próxima salida 🗺️",
                "¡Comodín libre! ⭐"
            )
        )
    }
    var newOptionText by remember { mutableStateOf("") }
    var winnerText by remember { mutableStateOf("Pulsa 'Girar Ruleta' para elegir al azar 🎡") }
    var isSpinning by remember { mutableStateOf(false) }
    val rotationAnim = remember { Animatable(0f) }

    val sliceColors = remember {
        listOf(
            Color(0xFF6366F1), Color(0xFFEC4899), Color(0xFFF59E0B),
            Color(0xFF10B981), Color(0xFF06B6D4), Color(0xFF8B5CF6),
            Color(0xFFEF4444), Color(0xFF14B8A6)
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Pointer indicator
        Text("▼", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)

        // Animated Canvas Roulette Wheel
        Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val count = options.size.coerceAtLeast(1)
                val sweep = 360f / count
                rotate(rotationAnim.value) {
                    for (i in 0 until count) {
                        drawArc(
                            color = sliceColors[i % sliceColors.size],
                            startAngle = -90f + i * sweep,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = Offset.Zero,
                            size = Size(size.width, size.height)
                        )
                    }
                }
                drawCircle(color = Color.White, radius = size.minDimension * 0.14f)
            }
            Text("🎡", fontSize = 22.sp)
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = winnerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            )
        }

        Button(
            onClick = {
                if (isSpinning || options.isEmpty()) return@Button
                isSpinning = true
                scope.launch {
                    val chosenIdx = options.indices.random()
                    val sliceDeg = 360f / options.size
                    val targetAngle = rotationAnim.value + 1440f + (360f - (chosenIdx * sliceDeg + sliceDeg / 2f) - (rotationAnim.value % 360f))
                    rotationAnim.animateTo(
                        targetValue = targetAngle,
                        animationSpec = tween(durationMillis = 2200, easing = FastOutSlowInEasing)
                    )
                    winnerText = "🎉 Resultado:\n${options[chosenIdx]}"
                    onEarnPoints(10)
                    isSpinning = false
                }
            },
            enabled = !isSpinning && options.size >= 2,
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Default.Casino, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isSpinning) "Girando..." else "Girar Ruleta (+10 pts)", fontWeight = FontWeight.Bold)
        }

        // Load real group members button
        if (members.size >= 2) {
            OutlinedButton(
                onClick = {
                    options = members
                    winnerText = "Miembros del grupo cargados en la ruleta (${members.size})"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Cargar miembros del grupo (${members.size})")
            }
        }

        // Add custom option
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = newOptionText,
                onValueChange = { newOptionText = it },
                placeholder = { Text("Añadir opción personalizada...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = {
                    val clean = newOptionText.trim()
                    if (clean.isNotBlank() && options.size < 12) {
                        options = options + clean
                        newOptionText = ""
                    }
                },
                enabled = newOptionText.isNotBlank() && options.size < 12
            ) {
                Icon(Icons.Default.Add, contentDescription = "Añadir")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEachIndexed { idx, opt ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(opt, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (options.size > 2) {
                            IconButton(
                                onClick = { options = options.filterIndexed { i, _ -> i != idx } },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
