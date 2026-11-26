package com.example.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BibleParserAndModelsTest {

    @Test
    fun cleanHtmlTags_stripsMarkupAndEntities() {
        val raw = "<p>Porque de tal manera am&oacute; Dios &amp; nos dio <br/> vida&nbsp;eterna.</p>"
        val clean = BibleClient.cleanHtmlTags(raw)
        assertThat(clean).doesNotContain("<p>")
        assertThat(clean).doesNotContain("<br/>")
        assertThat(clean).contains("&")
        assertThat(clean).contains("vida eterna.")
    }

    @Test
    fun parseApiBibleHtmlToVerses_extractsNumberedVerses() {
        val html = """
            <span data-number="16" class="v">16</span>Porque de tal manera amó Dios al mundo.
            <span data-number="17" class="v">17</span>Porque no envió Dios a su Hijo al mundo para condenar al mundo.
        """.trimIndent()

        val verses = BibleClient.parseApiBibleHtmlToVerses(html)
        assertThat(verses).hasSize(2)
        assertThat(verses[0].number).isEqualTo(16)
        assertThat(verses[0].text).contains("amó Dios")
        assertThat(verses[1].number).isEqualTo(17)
        assertThat(verses[1].text).contains("no envió Dios")
    }

    @Test
    fun toBollsTranslation_mapsSpanishVersionsAccurately() {
        assertThat(BibleClient.toBollsTranslation("RVR1960")).isEqualTo("RV1960")
        assertThat(BibleClient.toBollsTranslation("NVI")).isEqualTo("NVI")
        assertThat(BibleClient.toBollsTranslation("NTV")).isEqualTo("NTV")
        assertThat(BibleClient.toBollsTranslation("TLA")).isEqualTo("DHH")
        assertThat(BibleClient.toBollsTranslation("UNKNOWN")).isEqualTo("RV1960")
    }

    @Test
    fun outingRsvpChoiceFor_prefersPerUserMapWhenPresent() {
        val outing = Outing(
            id = "o1",
            myRsvp = RsvpChoice.YES.name,
            rsvpByUser = mapOf("u1" to RsvpChoice.NO.name, "u2" to RsvpChoice.MAYBE.name)
        )
        assertThat(outing.rsvpChoiceFor("u1")).isEqualTo(RsvpChoice.NO)
        assertThat(outing.rsvpChoiceFor("u2")).isEqualTo(RsvpChoice.MAYBE)
        assertThat(outing.rsvpChoiceFor("u3")).isEqualTo(RsvpChoice.YES)
    }

    @Test
    fun socialGroupToCreateMap_includesCreatorInMembersAndNames() {
        val group = SocialGroup(
            id = "QUEDA-7777",
            name = "Amigos del Sábado",
            inviteCode = "queda-7777"
        )
        val map = group.toCreateMap("uid_1", "Carlos")
        assertThat(map["inviteCode"]).isEqualTo("QUEDA-7777")
        assertThat(map["createdBy"]).isEqualTo("uid_1")
        @Suppress("UNCHECKED_CAST")
        val memberIds = map["memberIds"] as List<String>
        assertThat(memberIds).contains("uid_1")
        @Suppress("UNCHECKED_CAST")
        val memberNames = map["memberNames"] as Map<String, String>
        assertThat(memberNames["uid_1"]).isEqualTo("Carlos")
    }
}
