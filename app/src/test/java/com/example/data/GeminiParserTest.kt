package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeminiParserTest {

    @Test
    fun parseAgenticResponse_plainText_returnsNoneAction() {
        val result = GeminiClient.parseAgenticResponse("¡Hola! Aquí tienes 3 ideas para vuestra salida.")
        assertThat(result.actionType).isEqualTo("NONE")
        assertThat(result.replyText).contains("3 ideas")
    }

    @Test
    fun parseAgenticResponse_createOutingJson_parsesAllFields() {
        val raw = """
            ¡Perfecto! He creado la salida de bolera para el grupo.
            ACTION_JSON:{"actionType":"CREATE_OUTING","title":"Bolera Nocturna","location":"Ozone Plaza","meetingPoint":"Entrada Sur","dateIso":"2026-12-01","time":"21:00","status":"CONFIRMED","budgetPerPerson":"18 €","kitItems":"Calcetines","additionalInfo":"Llegar puntual"}
        """.trimIndent()

        val result = GeminiClient.parseAgenticResponse(raw)
        assertThat(result.actionType).isEqualTo("CREATE_OUTING")
        assertThat(result.title).isEqualTo("Bolera Nocturna")
        assertThat(result.location).isEqualTo("Ozone Plaza")
        assertThat(result.meetingPoint).isEqualTo("Entrada Sur")
        assertThat(result.dateIso).isEqualTo("2026-12-01")
        assertThat(result.time).isEqualTo("21:00")
        assertThat(result.status).isEqualTo("CONFIRMED")
        assertThat(result.budgetPerPerson).isEqualTo("18 €")
        assertThat(result.kitItems).isEqualTo("Calcetines")
        assertThat(result.replyText).contains("He creado la salida")
    }

    @Test
    fun parseAgenticResponse_switchThemeWithCodeFence_parsesThemeTarget() {
        val raw = """
            Cambiando a modo oscuro para cuidar la vista.
            ACTION_JSON:```json
            {"actionType":"SWITCH_THEME","themeTarget":"DARK"}
            ```
        """.trimIndent()

        val result = GeminiClient.parseAgenticResponse(raw)
        assertThat(result.actionType).isEqualTo("SWITCH_THEME")
        assertThat(result.themeTarget).isEqualTo("DARK")
    }

    @Test
    fun parseAgenticResponse_malformedJson_fallsBackSafelyToNone() {
        val raw = "Listo para ayudar.\nACTION_JSON:{broken_json"
        val result = GeminiClient.parseAgenticResponse(raw)
        assertThat(result.actionType).isEqualTo("NONE")
        assertThat(result.replyText).isEqualTo("Listo para ayudar.")
    }

    @Test
    fun geminiModelName_usesValidSupportedModel() {
        assertThat(GeminiClient.MODEL_NAME).isEqualTo("gemini-2.5-flash")
    }
}
