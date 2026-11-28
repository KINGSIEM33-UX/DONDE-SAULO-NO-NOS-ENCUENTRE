package com.example.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

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
    fun parseApiBibleHtmlToVerses_skipsEmptyNumberedVerses() {
        val html = """
            <span data-number="1" class="v">1</span>
            <span data-number="2" class="v">2</span>Texto válido.
        """.trimIndent()

        val verses = BibleClient.parseApiBibleHtmlToVerses(html)

        assertThat(verses).containsExactly(ParsedVerse(2, "Texto válido.")).inOrder()
    }

    @Test
    fun validateChapterVerses_rejectsChapterWithoutText() {
        val error = assertThrows(EmptyChapterException::class.java) {
            BibleClient.validateChapterVerses(
                listOf(ParsedVerse(1, " "), ParsedVerse(2, "")),
                "API.Bible"
            )
        }

        assertThat(error).hasMessageThat().contains("API.Bible")
    }

    @Test
    fun fetchChapter_rejectsEmptyBollsResponse() {
        val error = assertThrows(EmptyChapterException::class.java) {
            runBlocking {
                BibleClient.fetchChapter(
                    bibleId = "RV1960",
                    chapterId = "JHN.3",
                    bollsBibleService = FakeBollsBibleApiService(emptyList())
                )
            }
        }

        assertThat(error).hasMessageThat().contains("Bolls.life")
    }

    @Test
    fun fetchChapter_rejectsEmptyApiBibleResponse_withoutChangingProvider() {
        val error = assertThrows(EmptyChapterException::class.java) {
            runBlocking {
                BibleClient.fetchChapter(
                    bibleId = "api-bible-id",
                    chapterId = "JHN.3",
                    apiBibleService = FakeBibleApiService("<span data-number=\"1\">1</span>"),
                    bollsBibleService = FakeBollsBibleApiService(
                        listOf(BollsVerseDto(verse = 1, text = "Texto de otro proveedor"))
                    ),
                    useApiBible = true
                )
            }
        }

        assertThat(error).hasMessageThat().contains("API.Bible")
    }

    @Test
    fun fetchChapter_doesNotShowBollsTextWhenApiBibleFails() {
        val error = assertThrows(IOException::class.java) {
            runBlocking {
                BibleClient.fetchChapter(
                    bibleId = "api-bible-id",
                    chapterId = "JHN.3",
                    apiBibleService = FakeBibleApiService(
                        content = "",
                        error = IOException("API.Bible unavailable")
                    ),
                    bollsBibleService = FakeBollsBibleApiService(
                        listOf(BollsVerseDto(verse = 1, text = "Texto de RV1960 que no corresponde"))
                    ),
                    useApiBible = true
                )
            }
        }

        assertThat(error).hasMessageThat().isEqualTo("API.Bible unavailable")
    }

    @Test
    fun fetchChapter_doesNotSwallowCancellation() {
        val cancellation = CancellationException("cancelled")
        val error = assertThrows(CancellationException::class.java) {
            runBlocking {
                BibleClient.fetchChapter(
                    bibleId = "RV1960",
                    chapterId = "JHN.3",
                    bollsBibleService = FakeBollsBibleApiService(error = cancellation)
                )
            }
        }

        assertThat(error).isSameInstanceAs(cancellation)
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

    private class FakeBollsBibleApiService(
        private val verses: List<BollsVerseDto> = emptyList(),
        private val error: Throwable? = null
    ) : BollsBibleApiService {
        override suspend fun getBooks(translation: String): List<BollsBookDto> = emptyList()

        override suspend fun getChapterText(
            translation: String,
            book: Int,
            chapter: Int
        ): List<BollsVerseDto> {
            error?.let { throw it }
            return verses
        }

        override suspend fun search(
            translation: String,
            query: String
        ): List<BollsSearchHitDto> = emptyList()
    }

    private class FakeBibleApiService(
        private val content: String,
        private val error: Throwable? = null
    ) : BibleApiService {
        override suspend fun getBibles(): BiblesResponse = BiblesResponse(emptyList())

        override suspend fun getBooks(bibleId: String): BooksResponse = BooksResponse(emptyList())

        override suspend fun getChapters(bibleId: String, bookId: String): ChaptersResponse =
            ChaptersResponse(emptyList())

        override suspend fun getChapterContent(
            bibleId: String,
            chapterId: String
        ): ChapterContentResponse {
            error?.let { throw it }
            return ChapterContentResponse(
                ChapterContentDto(chapterId, content, "Juan 3")
            )
        }

        override suspend fun searchBible(bibleId: String, query: String): SearchResponse =
            SearchResponse(SearchDataDto(query, 0, 0, 0, 0, emptyList()))
    }
}
