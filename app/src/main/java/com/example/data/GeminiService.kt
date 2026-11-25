package com.example.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

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
    val targetTitleQuery: String = ""
)

object GeminiClient {
    private val systemPrompt = "Eres un asistente de inteligencia artificial evangélico integrado en la aplicación bíblica. Tienes la capacidad de ayudar al usuario con su estudio bíblico. " +
        "NO te llames 'Santo Padre' ni asumas la identidad de Saulo. El usuario solicita que uses un nombre moderno y original. " +
        "Al responder a esta primera solicitud, SUGIERE 20 nombres originales y modernos para ti mismo en tu respuesta. " +
        "ADEMÁS, posees 'habilidades' (plugins) que te permiten controlar la app. Para usar una habilidad, debes incluir exactamente uno de los siguientes bloques de texto en tu respuesta. La app detectará estos bloques y ejecutará la acción:" +
        "\n- Para agendar un evento: [CMD:EVENT:Titulo_del_evento|Detalles|Fecha_u_Hora]" +
        "\n- Para dar preguntas bíblicas: simplemente formula las preguntas en el texto." +
        "\n- Para cambiar el tema visual (ej. a Zen): [CMD:THEME:ZEN] o [CMD:THEME:DEFAULT]" +
        "\n- Para cambiar el tamaño de fuente (ej. a 24): [CMD:FONT:24]" +
        "\n- Para buscar un versículo (ej. 1 Cor 1:5): [CMD:VERSE:1 Corintios 1:5]" +
        "\nCuando uses un comando, el usuario verá el resultado visualmente en la aplicación. Puedes combinar comandos y texto normal. Siempre mantén una postura evangélica y edificante."

    private val agenticOutingsSystemPrompt = """
        Eres el Agente IA Social de "Adora: Quedadas & Salidas". Ayudas al grupo a organizar salidas, quedadas, presupuestos, mapas y juegos.
        Puedes EJECUTAR ACCIONES REALES en la aplicación.
        Responde SIEMPRE con tu mensaje amigable para el usuario, y si el usuario pide crear, modificar, cambiar el color/estado (Confirmada=Verde, Pensada=Gris, Cancelada=Rojo) o confirmar asistencia (RSVP) a una salida, añade al final una línea con el formato exacto:
        ACTION_JSON:{"actionType":"CREATE_OUTING|UPDATE_STATUS|RSVP|UPDATE_KIT","title":"...","location":"...","meetingPoint":"...","dateIso":"YYYY-MM-DD","time":"HH:mm","status":"CONFIRMED|PLANNED|CANCELLED","budgetPerPerson":"...","kitItems":"...","additionalInfo":"...","rsvpChoice":"YES|MAYBE|NO","targetTitleQuery":"..."}
        
        Reglas de fechas:
        - Si el usuario menciona "martes 10 de noviembre" o "10 de noviembre", usa "2026-11-10".
        - Si no especifica fecha, usa "2026-11-10".
        - Si dice confirmada o círculo verde -> status = "CONFIRMED".
        - Si dice pensada, por confirmar o círculo gris -> status = "PLANNED".
        - Si dice cancelada o círculo rojo -> status = "CANCELLED".
    """.trimIndent()

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
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
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.5f
            },
            systemInstruction = content {
                text(agenticOutingsSystemPrompt)
            }
        )
    }

    suspend fun chatWithSaul(prompt: String): String {
        return try {
            val response = withTimeout(60_000L) {
                chatModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                text
            } else {
                "No he recibido palabras para revelarte en este momento. Inténtalo de nuevo."
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Chat generation timed out", e)
            "La consulta tardó más de 60 segundos. Por favor, inténtalo de nuevo."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI chat generation failed", e)
            "Surgió un contratiempo en la conexión con Gemini: ${e.localizedMessage ?: "Error desconocido"}."
        }
    }

    suspend fun runSocialAgentCommand(
        userPrompt: String,
        currentOutingsSummary: String
    ): AgenticActionResponse {
        return try {
            val fullPrompt = "Salidas actuales del grupo:\n$currentOutingsSummary\n\nSolicitud del usuario: $userPrompt"
            val response = withTimeout(60_000L) {
                socialAgentModel.generateContent(fullPrompt)
            }
            val rawText = response.text ?: "¡Hecho! He procesado tu solicitud para el grupo."
            parseAgenticResponse(rawText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Social Agent AI call failed", e)
            AgenticActionResponse(
                replyText = "No pude conectar con Gemini en este momento (${e.localizedMessage ?: "error de red"}), pero puedes usar los botones rápidos o intentarlo de nuevo."
            )
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
            val obj = JSONObject(jsonSubstring)
            AgenticActionResponse(
                replyText = cleanReply,
                actionType = obj.optString("actionType", "NONE"),
                title = obj.optString("title", "Nueva Salida"),
                location = obj.optString("location", "Centro"),
                meetingPoint = obj.optString("meetingPoint", "Entrada principal"),
                dateIso = obj.optString("dateIso", "2026-11-10"),
                time = obj.optString("time", "20:00"),
                status = obj.optString("status", "CONFIRMED"),
                budgetPerPerson = obj.optString("budgetPerPerson", "15 €"),
                kitItems = obj.optString("kitItems", ""),
                additionalInfo = obj.optString("additionalInfo", ""),
                rsvpChoice = obj.optString("rsvpChoice", "YES"),
                targetTitleQuery = obj.optString("targetTitleQuery", "")
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse ACTION_JSON from AI response", e)
            AgenticActionResponse(replyText = cleanReply)
        }
    }
}
