package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "GeminiClient"

data class AgenticActionResponse(
    val replyText: String,
    val actionType: String = "NONE", // CREATE_OUTING, UPDATE_STATUS, RSVP, UPDATE_KIT, SWITCH_THEME, NONE
    val title: String = "",
    val location: String = "",
    val meetingPoint: String = "",
    val dateIso: String = "",
    val time: String = "",
    val status: String = "CONFIRMED", // CONFIRMED, PLANNED, CANCELLED
    val budgetPerPerson: String = "",
    val kitItems: String = "",
    val additionalInfo: String = "",
    val rsvpChoice: String = "YES", // YES, MAYBE, NO
    val targetTitleQuery: String = "",
    val themeTarget: String = "" // LIGHT, DARK, ZEN
)

object GeminiClient {
    const val MODEL_NAME = "gemini-2.5-flash"

    private val moshiMapAdapter by lazy {
        val moshi = Moshi.Builder().build()
        val type = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        moshi.adapter<Map<String, Any?>>(type)
    }

    private fun todayIso(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    private val systemPrompt = """
        Eres Adora IA, un asistente de estudio bíblico amable, claro y teológicamente riguroso integrado en la app Adora.
        Ayudas al usuario a comprender pasajes, contexto histórico, aplicación devocional y preguntas de estudio bíblico.
        Además, puedes ejecutar acciones dentro de la app incluyendo exactamente uno de estos comandos cuando el usuario lo solicite:
        - Agendar un estudio/evento en el calendario bíblico: [CMD:EVENT:Titulo_del_evento|Detalles|YYYY-MM-DD]
        - Cambiar el tema visual: [CMD:THEME:ZEN] o [CMD:THEME:DEFAULT]
        - Cambiar el tamaño de fuente del lector (12-32): [CMD:FONT:22]
        - Buscar un pasaje o versículo: [CMD:VERSE:Juan 3:16]
        Responde siempre en español con tono edificante, cercano y conciso.
    """.trimIndent()

    private fun buildSocialAgentPrompt(): String {
        val today = todayIso()
        return """
            Eres el Agente IA Social de "Adora: Quedadas & Salidas". Ayudas al grupo a organizar salidas, presupuestos, mapas, puntos de encuentro y juegos.
            Fecha actual de referencia: $today.
            Puedes EJECUTAR ACCIONES REALES en la app. Responde siempre con un mensaje útil en español y, cuando el usuario pida crear o modificar una salida, cambiar su estado (Confirmada=CONFIRMED, Pensada=PLANNED, Cancelada=CANCELLED), actualizar el kit, confirmar asistencia (RSVP) o cambiar el tema visual (SWITCH_THEME), añade al final una sola línea con el formato exacto:
            ACTION_JSON:{"actionType":"CREATE_OUTING|UPDATE_STATUS|RSVP|UPDATE_KIT|SWITCH_THEME","title":"...","location":"...","meetingPoint":"...","dateIso":"YYYY-MM-DD","time":"HH:mm","status":"CONFIRMED|PLANNED|CANCELLED","budgetPerPerson":"...","kitItems":"...","additionalInfo":"...","rsvpChoice":"YES|MAYBE|NO","targetTitleQuery":"...","themeTarget":"LIGHT|DARK"}

            Reglas:
            - Si no se especifica fecha, usa $today.
            - Incluye siempre targetTitleQuery con el nombre de la salida a modificar cuando uses UPDATE_STATUS, RSVP o UPDATE_KIT.
            - Si el usuario solo pregunta o pide ideas, NO incluyas ACTION_JSON.
        """.trimIndent()
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.7f
            },
            systemInstruction = content {
                text(systemPrompt)
            }
        )
    }

    private val socialAgentModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.5f
            },
            systemInstruction = content {
                text(buildSocialAgentPrompt())
            }
        )
    }

    private val generalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0.8f
            }
        )
    }

    @Volatile
    private var bibleChatSession: Chat? = null

    @Volatile
    private var socialChatSession: Chat? = null

    fun resetSessions() {
        bibleChatSession = null
        socialChatSession = null
    }

    suspend fun chatWithSaul(prompt: String): String {
        return try {
            val session = bibleChatSession ?: chatModel.startChat().also { bibleChatSession = it }
            val response = withTimeout(60_000L) {
                session.sendMessage(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                text
            } else {
                "No recibí respuesta en este momento. Inténtalo de nuevo."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat generation timed out", e)
            "La consulta tardó más de 60 segundos. Por favor, inténtalo de nuevo."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            bibleChatSession = null
            Log.e(TAG, "Firebase AI chat generation failed", e)
            "No pude conectar con Gemini ahora mismo (${e.localizedMessage ?: "verifica tu conexión o configuración de Firebase"})."
        }
    }

    suspend fun runSocialAgentCommand(
        userPrompt: String,
        currentOutingsSummary: String
    ): AgenticActionResponse {
        return try {
            val session = socialChatSession ?: socialAgentModel.startChat().also { socialChatSession = it }
            val fullPrompt = "Fecha de hoy: ${todayIso()}\nSalidas actuales del grupo:\n${currentOutingsSummary.ifBlank { "(Sin salidas todavía)" }}\n\nSolicitud del usuario: $userPrompt"
            val response = withTimeout(60_000L) {
                session.sendMessage(fullPrompt)
            }
            val rawText = response.text ?: "¡Hecho! He procesado tu solicitud para el grupo."
            parseAgenticResponse(rawText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            socialChatSession = null
            Log.e(TAG, "Social Agent AI call failed", e)
            AgenticActionResponse(
                replyText = "No pude conectar con Gemini en este momento (${e.localizedMessage ?: "error de conexión"}). Puedes usar los botones rápidos o intentarlo de nuevo."
            )
        }
    }

    suspend fun generateQuickPrompt(prompt: String): String? {
        return try {
            val response = withTimeout(25_000L) {
                generalModel.generateContent(prompt)
            }
            response.text?.trim()?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Quick Gemini prompt failed: ${e.message}")
            null
        }
    }

    fun parseAgenticResponse(rawText: String): AgenticActionResponse {
        val marker = "ACTION_JSON:"
        val idx = rawText.indexOf(marker)
        if (idx == -1) {
            return AgenticActionResponse(replyText = rawText.trim())
        }
        val cleanReply = rawText.substring(0, idx).trim().ifBlank { "¡Listo! He ejecutado la acción en tus salidas." }
        val jsonPart = rawText.substring(idx + marker.length).trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val firstBrace = jsonPart.indexOf('{')
            val lastBrace = jsonPart.lastIndexOf('}')
            val jsonSubstring = if (firstBrace != -1 && lastBrace > firstBrace) {
                jsonPart.substring(firstBrace, lastBrace + 1)
            } else {
                jsonPart
            }
            val map = moshiMapAdapter.fromJson(jsonSubstring) ?: return AgenticActionResponse(replyText = cleanReply)
            fun str(key: String, default: String = ""): String =
                (map[key] as? String)?.ifBlank { default } ?: default

            AgenticActionResponse(
                replyText = cleanReply,
                actionType = str("actionType", "NONE"),
                title = str("title", "Nueva Salida"),
                location = str("location", "Centro"),
                meetingPoint = str("meetingPoint", "Entrada principal"),
                dateIso = str("dateIso", todayIso()),
                time = str("time", "20:00"),
                status = str("status", "CONFIRMED"),
                budgetPerPerson = str("budgetPerPerson", "15 €"),
                kitItems = str("kitItems", ""),
                additionalInfo = str("additionalInfo", ""),
                rsvpChoice = str("rsvpChoice", "YES"),
                targetTitleQuery = str("targetTitleQuery", ""),
                themeTarget = str("themeTarget", "")
            )
        } catch (e: Exception) {
            try { Log.w(TAG, "Failed to parse ACTION_JSON from AI response", e) } catch (_: Throwable) {}
            AgenticActionResponse(replyText = cleanReply)
        }
    }
}
