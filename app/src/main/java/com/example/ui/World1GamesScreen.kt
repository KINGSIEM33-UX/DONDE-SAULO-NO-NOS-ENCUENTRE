package com.example.ui

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class MiniGameTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val badge: String,
    val colors: List<Color>
)

private val MINI_GAMES_CATALOG = listOf(
    MiniGameTile(
        id = "impostor",
        title = "El Impostor",
        subtitle = "Descubre al espía infiltrado en la quedada",
        emoji = "🕵️‍♂️",
        badge = "Grupo 3-10J",
        colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
    ),
    MiniGameTile(
        id = "game_2048",
        title = "2048 Arcade",
        subtitle = "Suma bloques y bate el récord del grupo",
        emoji = "🔢",
        badge = "Adictivo",
        colors = listOf(Color(0xFFF59E0B), Color(0xFFEA580C))
    ),
    MiniGameTile(
        id = "charadas",
        title = "Charadas 60s",
        subtitle = "Adivina mímica y palabras contrarreloj",
        emoji = "⏱️",
        badge = "Fiesta",
        colors = listOf(Color(0xFFEC4899), Color(0xFFE11D48))
    ),
    MiniGameTile(
        id = "verdad_reto",
        title = "Verdad o Reto IA",
        subtitle = "Retos épicos y ¿Qué Prefieres? para el grupo",
        emoji = "🔥",
        badge = "Con IA",
        colors = listOf(Color(0xFF8B5CF6), Color(0xFFDB2777))
    ),
    MiniGameTile(
        id = "trivia_relampago",
        title = "Trivia Relámpago",
        subtitle = "Cultura pop, cine, música y curiosidades",
        emoji = "⚡",
        badge = "Rápido",
        colors = listOf(Color(0xFF06B6D4), Color(0xFF2563EB))
    ),
    MiniGameTile(
        id = "tres_en_raya",
        title = "Tres en Raya",
        subtitle = "Duelo rápido 2 jugadores o contra la IA",
        emoji = "❌⭕",
        badge = "2J / IA",
        colors = listOf(Color(0xFF10B981), Color(0xFF059669))
    ),
    MiniGameTile(
        id = "memoria_express",
        title = "Memoria Express",
        subtitle = "Encuentra las parejas en el menor tiempo",
        emoji = "🧠",
        badge = "Mental",
        colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
    ),
    MiniGameTile(
        id = "ruleta_quedada",
        title = "Ruleta del Grupo",
        subtitle = "Decide quién invita, qué comer o qué hacer",
        emoji = "🎡",
        badge = "Sorteo",
        colors = listOf(Color(0xFFF97316), Color(0xFFDC2626))
    )
)

@Composable
fun World1GamesScreen(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    var activeGameId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("entertainment_games_screen")
    ) {
        if (activeGameId == null) {
            // --- HUB GRID OF 8 MINI-GAMES ("CUADRADITOS CON NOMBRES DE LOS JUEGOS") ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Entretenimiento 🎮",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Mini-juegos adictivos para pasar el rato en grupo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${profile.gamesHighScore} pts",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(MINI_GAMES_CATALOG, key = { it.id }) { game ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp)
                            .clickable { activeGameId = game.id }
                            .testTag("game_tile_${game.id}"),
                        shape = RoundedCornerShape(24.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(game.colors))
                                .padding(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.22f),
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = game.badge,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Text(text = game.emoji, fontSize = 30.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = game.title,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = game.subtitle,
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val activeGame = MINI_GAMES_CATALOG.first { it.id == activeGameId }
            // Top bar for active mini-game
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { activeGameId = null },
                            modifier = Modifier.size(48.dp).testTag("back_to_games_hub_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver a juegos")
                        }
                        Text(
                            text = "${activeGame.emoji} ${activeGame.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "🏆 ${profile.gamesHighScore} pts",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                when (activeGameId) {
                    "impostor" -> ImpostorMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "game_2048" -> Game2048MiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "charadas" -> CharadasMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "verdad_reto" -> VerdadRetoMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "trivia_relampago" -> TriviaRelampagoMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "tres_en_raya" -> TresEnRayaMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "memoria_express" -> MemoriaExpressMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                    "ruleta_quedada" -> RuletaQuedadaMiniGame(onScore = { viewModel.addMiniGameScore(it) })
                }
            }
        }
    }
}

// 1. EL IMPOSTOR (Espía en el Grupo)
@Composable
private fun ImpostorMiniGame(onScore: (Int) -> Unit) {
    val words = remember {
        listOf(
            "Bolera de Medianoche 🎳",
            "Pizzería Napolitana 🍕",
            "Cine VIP con Palomitas 🍿",
            "Ruta en la Montaña 🏔️",
            "Karaoke Ochentero 🎤",
            "Parque de Atracciones 🎢",
            "Escape Room Egipcio 🕵️"
        )
    }
    var playerCount by remember { mutableIntStateOf(4) }
    var secretWord by remember { mutableStateOf(words.random()) }
    var impostorIndex by remember { mutableIntStateOf((0 until 4).random()) }
    var currentPlayerTurn by remember { mutableIntStateOf(0) }
    var isRoleRevealed by remember { mutableStateOf(false) }
    var roundFinished by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🕵️‍♂️ El Impostor de la Salida",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Pasa el móvil a cada amigo. Todos verán el lugar secreto de la salida excepto EL IMPOSTOR.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )

            if (!roundFinished) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Turno del Jugador ${currentPlayerTurn + 1} de $playerCount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (isRoleRevealed) {
                            if (currentPlayerTurn == impostorIndex) {
                                Text(
                                    text = "🚨 ¡ERES EL IMPOSTOR! 🚨\nDisimula y descubre a dónde van los demás.",
                                    color = Color(0xFFDC2626),
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    fontSize = 18.sp
                                )
                            } else {
                                Text(
                                    text = "Lugar Secreto:\n$secretWord",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    fontSize = 20.sp
                                )
                            }
                        } else {
                            Text(
                                text = "🔒 Toca 'Ver mi rol' sin que nadie más mire la pantalla",
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                if (!isRoleRevealed) {
                    Button(
                        onClick = { isRoleRevealed = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("👁️ Ver mi rol secreto", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            isRoleRevealed = false
                            if (currentPlayerTurn + 1 < playerCount) {
                                currentPlayerTurn++
                            } else {
                                roundFinished = true
                                onScore(25)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text("Ocultar y pasar al siguiente jugador →", fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    text = "🗣️ ¡Todos tienen su rol! Haced 1 pregunta cada uno y votad quién es el Impostor.",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                var revealImpostor by remember { mutableStateOf(false) }
                if (revealImpostor) {
                    Text(
                        text = "El Impostor era el Jugador ${impostorIndex + 1} • Lugar: $secretWord",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    FilledTonalButton(onClick = { revealImpostor = true }) {
                        Text("Descubrir quién era el Impostor")
                    }
                }
                Button(
                    onClick = {
                        secretWord = words.random()
                        impostorIndex = (0 until playerCount).random()
                        currentPlayerTurn = 0
                        isRoleRevealed = false
                        roundFinished = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Nueva Partida (+25 pts)")
                }
            }
        }
    }
}

// 2. 2048 ARCADE
@Composable
private fun Game2048MiniGame(onScore: (Int) -> Unit) {
    var board by remember {
        mutableStateOf(
            listOf(
                2, 0, 2, 0,
                0, 4, 0, 0,
                0, 0, 2, 0,
                0, 0, 0, 0
            )
        )
    }
    var score by remember { mutableIntStateOf(0) }

    fun slideLine(line: List<Int>): Pair<List<Int>, Int> {
        val nonZero = line.filter { it != 0 }.toMutableList()
        val result = mutableListOf<Int>()
        var gained = 0
        var i = 0
        while (i < nonZero.size) {
            if (i + 1 < nonZero.size && nonZero[i] == nonZero[i + 1]) {
                val merged = nonZero[i] * 2
                result.add(merged)
                gained += merged
                i += 2
            } else {
                result.add(nonZero[i])
                i++
            }
        }
        while (result.size < 4) result.add(0)
        return result to gained
    }

    fun move(direction: String) {
        val mutable = board.toMutableList()
        var totalGained = 0
        when (direction) {
            "LEFT" -> {
                for (r in 0..3) {
                    val row = (0..3).map { mutable[r * 4 + it] }
                    val (merged, pts) = slideLine(row)
                    totalGained += pts
                    for (c in 0..3) mutable[r * 4 + c] = merged[c]
                }
            }
            "RIGHT" -> {
                for (r in 0..3) {
                    val row = (0..3).map { mutable[r * 4 + it] }.reversed()
                    val (merged, pts) = slideLine(row)
                    totalGained += pts
                    val rev = merged.reversed()
                    for (c in 0..3) mutable[r * 4 + c] = rev[c]
                }
            }
            "UP" -> {
                for (c in 0..3) {
                    val col = (0..3).map { mutable[it * 4 + c] }
                    val (merged, pts) = slideLine(col)
                    totalGained += pts
                    for (r in 0..3) mutable[r * 4 + c] = merged[r]
                }
            }
            "DOWN" -> {
                for (c in 0..3) {
                    val col = (0..3).map { mutable[it * 4 + c] }.reversed()
                    val (merged, pts) = slideLine(col)
                    totalGained += pts
                    val rev = merged.reversed()
                    for (r in 0..3) mutable[r * 4 + c] = rev[r]
                }
            }
        }
        val emptyIndices = mutable.indices.filter { mutable[it] == 0 }
        if (emptyIndices.isNotEmpty()) {
            mutable[emptyIndices.random()] = if ((0..9).random() < 8) 2 else 4
        }
        board = mutable
        if (totalGained > 0) {
            score += totalGained
            onScore(totalGained / 2)
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
            Text("Puntuación: $score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            OutlinedButton(
                onClick = {
                    board = listOf(2, 0, 2, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                    score = 0
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reiniciar")
            }
        }

        // 4x4 Grid
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (r in 0..3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in 0..3) {
                            val value = board[r * 4 + c]
                            Box(
                                modifier = Modifier
                                    .size(66.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        when (value) {
                                            0 -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                            2 -> Color(0xFFFEF3C7)
                                            4 -> Color(0xFFFDE68A)
                                            8 -> Color(0xFFFBBF24)
                                            16 -> Color(0xFFF59E0B)
                                            32 -> Color(0xFFEA580C)
                                            64 -> Color(0xFFDC2626)
                                            else -> Color(0xFF7C3AED)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (value > 0) {
                                    Text(
                                        text = value.toString(),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 20.sp,
                                        color = if (value <= 4) Color(0xFF78350F) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Directional Control Pad
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { move("UP") }, modifier = Modifier.width(120.dp).height(48.dp)) {
                Text("⬆️ Arriba")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { move("LEFT") }, modifier = Modifier.width(120.dp).height(48.dp)) {
                    Text("⬅️ Izq")
                }
                Button(onClick = { move("RIGHT") }, modifier = Modifier.width(120.dp).height(48.dp)) {
                    Text("Der ➡️")
                }
            }
            Button(onClick = { move("DOWN") }, modifier = Modifier.width(120.dp).height(48.dp)) {
                Text("⬇️ Abajo")
            }
        }
    }
}

// 3. CHARADAS CONTRARRELOJ (60s)
@Composable
private fun CharadasMiniGame(onScore: (Int) -> Unit) {
    val words = remember {
        listOf(
            "Bailar Salsa 💃", "Comer Espaguetis 🍝", "Perder el Autobús 🚌",
            "Hacer Paracaidismo 🪂", "Ganar la Lotería 🤑", "Cantar Ópera 🎭",
            "Jugar a los Bolos 🎳", "Montar a Caballo 🐎", "Hacer un Selfie Grupal 🤳"
        )
    }
    var currentWord by remember { mutableStateOf(words.first()) }
    var timeLeft by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(false) }
    var hits by remember { mutableIntStateOf(0) }

    LaunchedEffect(isRunning) {
        while (isRunning && timeLeft > 0) {
            delay(1000L)
            timeLeft--
        }
        if (timeLeft == 0) isRunning = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("⏱️ Tiempo: ${timeLeft}s  |  Aciertos: $hits", fontWeight = FontWeight.Black, fontSize = 18.sp)

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = currentWord,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (!isRunning) {
                Button(
                    onClick = {
                        timeLeft = 60
                        hits = 0
                        currentWord = words.random()
                        isRunning = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("¡Empezar Ronda de 60 Segundos!", fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { currentWord = words.random() },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("Pasar ⏭️", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            hits++
                            onScore(10)
                            currentWord = words.random()
                        },
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Text("¡Correcto! ✅", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// 4. VERDAD O RETO / ¿QUÉ PREFIERES?
@Composable
private fun VerdadRetoMiniGame(onScore: (Int) -> Unit) {
    val prompts = remember {
        listOf(
            "VERDAD: ¿Cuál ha sido la anécdota más divertida que has vivido en una salida con este grupo?",
            "RETO: Imita a otro miembro del grupo hasta que alguien adivine quién es.",
            "¿QUÉ PREFIERES?: ¿Ir de acampada sin móvil todo el fin de semana o ir a una cena de gala?",
            "RETO: Envía un audio en el chat del grupo cantando el estribillo de tu canción favorita.",
            "VERDAD: ¿Quién del grupo tarda siempre más en llegar al punto de encuentro?",
            "¿QUÉ PREFIERES?: ¿Organizar siempre las salidas o que el lugar sea sorpresa hasta llegar?"
        )
    }
    var currentPrompt by remember { mutableStateOf(prompts.first()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🔥 Rompehielos de Grupo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentPrompt,
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = {
                    currentPrompt = prompts.random()
                    onScore(15)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Casino, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Siguiente Tarjeta (+15 pts)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 5. TRIVIA RELÁMPAGO
@Composable
private fun TriviaRelampagoMiniGame(onScore: (Int) -> Unit) {
    val questions = remember {
        listOf(
            Triple("¿Qué planeta es conocido como el Planeta Rojo?", listOf("Venus", "Marte", "Júpiter"), 1),
            Triple("¿Cuántos jugadores hay en un equipo de baloncesto en cancha?", listOf("5", "6", "7"), 0),
            Triple("¿En qué país nació la pizza napolitana?", listOf("Francia", "Italia", "Grecia"), 1),
            Triple("¿Cuál es el océano más grande del mundo?", listOf("Atlántico", "Índico", "Pacífico"), 2)
        )
    }
    var qIndex by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<String?>(null) }
    val current = questions[qIndex % questions.size]

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(current.first, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            current.second.forEachIndexed { idx, opt ->
                FilledTonalButton(
                    onClick = {
                        if (idx == current.third) {
                            feedback = "¡Correcto! +20 pts 🎉"
                            onScore(20)
                        } else {
                            feedback = "Casi... prueba la siguiente"
                        }
                        qIndex++
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text(opt, fontWeight = FontWeight.Bold)
                }
            }
            if (feedback != null) {
                Text(feedback!!, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// 6. TRES EN RAYA (2J / IA)
@Composable
private fun TresEnRayaMiniGame(onScore: (Int) -> Unit) {
    val cells = remember { mutableStateListOf("", "", "", "", "", "", "", "", "") }
    var turnX by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Turno de ❌") }

    fun checkWinner(): String? {
        val lines = listOf(
            listOf(0, 1, 2), listOf(3, 4, 5), listOf(6, 7, 8),
            listOf(0, 3, 6), listOf(1, 4, 7), listOf(2, 5, 8),
            listOf(0, 4, 8), listOf(2, 4, 6)
        )
        for (l in lines) {
            val a = cells[l[0]]
            if (a.isNotEmpty() && a == cells[l[1]] && a == cells[l[2]]) return a
        }
        return null
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(statusText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        for (r in 0..2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0..2) {
                    val idx = r * 3 + c
                    Surface(
                        onClick = {
                            if (cells[idx].isEmpty() && checkWinner() == null) {
                                cells[idx] = if (turnX) "❌" else "⭕"
                                val win = checkWinner()
                                if (win != null) {
                                    statusText = "¡Ganador: $win! (+25 pts)"
                                    onScore(25)
                                } else if (cells.none { it.isEmpty() }) {
                                    statusText = "¡Empate épico!"
                                } else {
                                    turnX = !turnX
                                    statusText = "Turno de ${if (turnX) "❌" else "⭕"}"
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(cells[idx], fontSize = 32.sp)
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                for (i in 0..8) cells[i] = ""
                turnX = true
                statusText = "Turno de ❌"
            }
        ) {
            Text("Nueva Partida")
        }
    }
}

// 7. MEMORIA EXPRESS
@Composable
private fun MemoriaExpressMiniGame(onScore: (Int) -> Unit) {
    val emojis = remember { listOf("🍕", "🍕", "🎳", "🎳", "🎸", "🎸", "🏔️", "🏔️").shuffled() }
    val revealed = remember { mutableStateListOf(false, false, false, false, false, false, false, false) }
    var firstIdx by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Encuentra las parejas de la salida", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        for (r in 0..1) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in 0..3) {
                    val idx = r * 4 + c
                    Surface(
                        onClick = {
                            if (!revealed[idx]) {
                                revealed[idx] = true
                                val prev = firstIdx
                                if (prev == null) {
                                    firstIdx = idx
                                } else {
                                    if (emojis[prev] == emojis[idx]) {
                                        onScore(15)
                                    } else {
                                        revealed[prev] = false
                                        revealed[idx] = false
                                    }
                                    firstIdx = null
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (revealed[idx]) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (revealed[idx]) emojis[idx] else "❓", fontSize = 26.sp)
                        }
                    }
                }
            }
        }
    }
}

// 8. RULETA DE LA QUEDADA
@Composable
private fun RuletaQuedadaMiniGame(onScore: (Int) -> Unit) {
    val options = listOf(
        "🍕 ¡Hoy toca Pizzería!",
        "🎳 Vamos a la Bolera",
        "🍦 El siguiente en hablar invita los helados",
        "📸 Selfie grupal obligatorio ahora mismo",
        "🎤 Elegir la música del viaje en coche",
        "🍔 Hamburguesas y batidos"
    )
    var chosen by remember { mutableStateOf("🎡 Gira la ruleta para decidir la suerte del grupo") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🎡 Ruleta de Decisiones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                border = BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(180.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = chosen,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Button(
                onClick = {
                    chosen = options.random()
                    onScore(10)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Girar Ruleta Ahora 🎲", fontWeight = FontWeight.Bold)
            }
        }
    }
}
