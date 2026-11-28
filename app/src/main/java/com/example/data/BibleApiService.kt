package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.CancellationException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- API.BIBLE Moshi Data Classes ---

@JsonClass(generateAdapter = true)
data class BiblesResponse(val data: List<BibleDto>)

@JsonClass(generateAdapter = true)
data class BibleDto(
    val id: String,
    val name: String,
    val abbreviation: String?,
    val description: String?,
    val language: LanguageDto?
)

@JsonClass(generateAdapter = true)
data class LanguageDto(
    val id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class BooksResponse(val data: List<BookDto>)

@JsonClass(generateAdapter = true)
data class BookDto(
    val id: String,
    val name: String,
    val nameLong: String?
)

@JsonClass(generateAdapter = true)
data class ChaptersResponse(val data: List<ChapterDto>)

@JsonClass(generateAdapter = true)
data class ChapterDto(
    val id: String,
    val number: String,
    val reference: String?
)

@JsonClass(generateAdapter = true)
data class ChapterContentResponse(val data: ChapterContentDto)

@JsonClass(generateAdapter = true)
data class ChapterContentDto(
    val id: String,
    val content: String,
    val reference: String?
)

@JsonClass(generateAdapter = true)
data class SearchResponse(val data: SearchDataDto)

@JsonClass(generateAdapter = true)
data class SearchDataDto(
    val query: String,
    val limit: Int,
    val offset: Int,
    val total: Int,
    val verseCount: Int,
    val verses: List<SearchVerseDto>
)

@JsonClass(generateAdapter = true)
data class SearchVerseDto(
    val id: String,
    val bibleId: String,
    val bookId: String,
    val chapterId: String,
    val text: String,
    val reference: String?
)

// --- Free Keyless Bolls.life Fallback DTOs ---

@JsonClass(generateAdapter = true)
data class BollsBookDto(
    val bookid: Int,
    val name: String,
    val chapters: Int
)

@JsonClass(generateAdapter = true)
data class BollsVerseDto(
    val pk: Long? = null,
    val verse: Int,
    val text: String
)

data class ParsedVerse(
    val number: Int,
    val text: String
)

data class ResolvedChapter(
    val bibleId: String,
    val bookId: String,
    val chapterId: String,
    val chapterNumber: Int,
    val totalChapters: Int,
    val reference: String,
    val verses: List<ParsedVerse>
)

class EmptyChapterException(message: String) : IllegalStateException(message)

// --- Retrofit Interfaces ---

interface BibleApiService {
    @GET("v1/bibles")
    suspend fun getBibles(): BiblesResponse

    @GET("v1/bibles/{bibleId}/books")
    suspend fun getBooks(
        @Path("bibleId") bibleId: String
    ): BooksResponse

    @GET("v1/bibles/{bibleId}/books/{bookId}/chapters")
    suspend fun getChapters(
        @Path("bibleId") bibleId: String,
        @Path("bookId") bookId: String
    ): ChaptersResponse

    @GET("v1/bibles/{bibleId}/chapters/{chapterId}")
    suspend fun getChapterContent(
        @Path("bibleId") bibleId: String,
        @Path("chapterId") chapterId: String
    ): ChapterContentResponse

    @GET("v1/bibles/{bibleId}/search")
    suspend fun searchBible(
        @Path("bibleId") bibleId: String,
        @Query("query") query: String
    ): SearchResponse
}

interface BollsBibleApiService {
    @GET("get-books/{translation}/")
    suspend fun getBooks(
        @Path("translation") translation: String
    ): List<BollsBookDto>

    @GET("get-text/{translation}/{book}/{chapter}/")
    suspend fun getChapterText(
        @Path("translation") translation: String,
        @Path("book") book: Int,
        @Path("chapter") chapter: Int
    ): List<BollsVerseDto>

    @GET("find/{translation}/")
    suspend fun search(
        @Path("translation") translation: String,
        @Query("search") query: String
    ): List<BollsSearchHitDto>
}

@JsonClass(generateAdapter = true)
data class BollsSearchHitDto(
    val pk: Long? = null,
    val book: Int = 1,
    val chapter: Int = 1,
    val verse: Int = 1,
    val text: String = ""
)

// --- Unified API Client (API.Bible + Keyless Bolls.life Spanish Fallback) ---

object BibleClient {
    private const val API_BIBLE_BASE_URL = "https://api.scripture.api.bible/"
    private const val BOLLS_BASE_URL = "https://bolls.life/"

    val hasValidApiBibleKey: Boolean
        get() = BuildConfig.BIBLE_API_KEY.isNotBlank() &&
            !BuildConfig.BIBLE_API_KEY.equals("MY_BIBLE_API_KEY", ignoreCase = true)

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("api-key", BuildConfig.BIBLE_API_KEY)
        chain.proceed(requestBuilder.build())
    }

    private val apiBibleOkHttp = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val publicOkHttp = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    val service: BibleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(API_BIBLE_BASE_URL)
            .client(apiBibleOkHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(BibleApiService::class.java)
    }

    private val bollsService: BollsBibleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BOLLS_BASE_URL)
            .client(publicOkHttp)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(BollsBibleApiService::class.java)
    }

    // Map app's preferred Spanish version names to Bolls.life translation codes
    fun toBollsTranslation(version: String): String {
        return when (version.uppercase()) {
            "NVI" -> "NVI"
            "NTV" -> "NTV"
            "LBLA" -> "LBLA"
            "TLA", "DHH" -> "DHH"
            "RV1960", "RVR1960" -> "RV1960"
            else -> "RV1960"
        }
    }

    fun availableSpanishBibles(): List<BibleDto> = listOf(
        BibleDto("RV1960", "Reina-Valera 1960", "RVR1960", "Traducción clásica en español", LanguageDto("spa", "Español")),
        BibleDto("NVI", "Nueva Versión Internacional", "NVI", "Español contemporáneo claro", LanguageDto("spa", "Español")),
        BibleDto("NTV", "Nueva Traducción Viviente", "NTV", "Lectura fluida y actual", LanguageDto("spa", "Español")),
        BibleDto("LBLA", "La Biblia de las Américas", "LBLA", "Traducción literal fiel", LanguageDto("spa", "Español")),
        BibleDto("DHH", "Dios Habla Hoy", "DHH", "Lenguaje sencillo y directo", LanguageDto("spa", "Español"))
    )

    /** Returns the app's stable ID for known aliases; unknown API.Bible IDs are preserved. */
    fun canonicalBibleId(bibleId: String): String {
        val known = availableSpanishBibles().firstOrNull {
            it.id.equals(bibleId, ignoreCase = true) ||
                it.abbreviation?.equals(bibleId, ignoreCase = true) == true
        }
        return known?.id ?: bibleId
    }

    // Standard USFM 3-letter book ID <-> Bolls.life 1..66 book number
    private val usfmToBookNumber = mapOf(
        "GEN" to 1, "EXO" to 2, "LEV" to 3, "NUM" to 4, "DEU" to 5,
        "JOS" to 6, "JDG" to 7, "RUT" to 8, "1SA" to 9, "2SA" to 10,
        "1KI" to 11, "2KI" to 12, "1CH" to 13, "2CH" to 14, "EZR" to 15,
        "NEH" to 16, "EST" to 17, "JOB" to 18, "PSA" to 19, "PRO" to 20,
        "ECC" to 21, "SNG" to 22, "ISA" to 23, "JER" to 24, "LAM" to 25,
        "EZK" to 26, "DAN" to 27, "HOS" to 28, "JOL" to 29, "AMO" to 30,
        "OBA" to 31, "JON" to 32, "MIC" to 33, "NAM" to 34, "HAB" to 35,
        "ZEP" to 36, "HAG" to 37, "ZEC" to 38, "MAL" to 39,
        "MAT" to 40, "MRK" to 41, "LUK" to 42, "JHN" to 43, "ACT" to 44,
        "ROM" to 45, "1CO" to 46, "2CO" to 47, "GAL" to 48, "EPH" to 49,
        "PHP" to 50, "COL" to 51, "1TH" to 52, "2TH" to 53, "1TI" to 54,
        "2TI" to 55, "TIT" to 56, "PHM" to 57, "HEB" to 58, "JAS" to 59,
        "1PE" to 60, "2PE" to 61, "1JN" to 62, "2JN" to 63, "3JN" to 64,
        "JUD" to 65, "REV" to 66
    )

    private val bookNumberToSpanishName = mapOf(
        1 to "Génesis", 2 to "Éxodo", 3 to "Levítico", 4 to "Números", 5 to "Deuteronomio",
        6 to "Josué", 7 to "Jueces", 8 to "Rut", 9 to "1 Samuel", 10 to "2 Samuel",
        11 to "1 Reyes", 12 to "2 Reyes", 13 to "1 Crónicas", 14 to "2 Crónicas", 15 to "Esdras",
        16 to "Nehemías", 17 to "Ester", 18 to "Job", 19 to "Salmos", 20 to "Proverbios",
        21 to "Eclesiastés", 22 to "Cantares", 23 to "Isaías", 24 to "Jeremías", 25 to "Lamentaciones",
        26 to "Ezequiel", 27 to "Daniel", 28 to "Oseas", 29 to "Joel", 30 to "Amós",
        31 to "Abdías", 32 to "Jonás", 33 to "Miqueas", 34 to "Nahúm", 35 to "Habacuc",
        36 to "Sofonías", 37 to "Hageo", 38 to "Zacarías", 39 to "Malaquías",
        40 to "Mateo", 41 to "Marcos", 42 to "Lucas", 43 to "Juan", 44 to "Hechos",
        45 to "Romanos", 46 to "1 Corintios", 47 to "2 Corintios", 48 to "Gálatas", 49 to "Efesios",
        50 to "Filipenses", 51 to "Colosenses", 52 to "1 Tesalonicenses", 53 to "2 Tesalonicenses", 54 to "1 Timoteo",
        55 to "2 Timoteo", 56 to "Tito", 57 to "Filemón", 58 to "Hebreos", 59 to "Santiago",
        60 to "1 Pedro", 61 to "2 Pedro", 62 to "1 Juan", 63 to "2 Juan", 64 to "3 Juan",
        65 to "Judas", 66 to "Apocalipsis"
    )

    private val bookNumberToChaptersCount = mapOf(
        1 to 50, 2 to 40, 3 to 27, 4 to 36, 5 to 34, 6 to 24, 7 to 21, 8 to 4, 9 to 31, 10 to 24,
        11 to 22, 12 to 25, 13 to 29, 14 to 36, 15 to 10, 16 to 13, 17 to 10, 18 to 42, 19 to 150, 20 to 31,
        21 to 12, 22 to 8, 23 to 66, 24 to 52, 25 to 5, 26 to 48, 27 to 12, 28 to 14, 29 to 3, 30 to 9,
        31 to 1, 32 to 4, 33 to 7, 34 to 3, 35 to 3, 36 to 3, 37 to 2, 38 to 14, 39 to 4,
        40 to 28, 41 to 16, 42 to 24, 43 to 21, 44 to 28, 45 to 16, 46 to 16, 47 to 13, 48 to 6, 49 to 6,
        50 to 4, 51 to 4, 52 to 5, 53 to 3, 54 to 6, 55 to 4, 56 to 3, 57 to 1, 58 to 13, 59 to 5,
        60 to 5, 61 to 3, 62 to 5, 63 to 1, 64 to 1, 65 to 1, 66 to 22
    )

    private val bookNumberToUsfm = usfmToBookNumber.entries.associate { (k, v) -> v to k }

    private fun isBollsBibleId(bibleId: String): Boolean {
        return bibleId.uppercase() in setOf("RV1960", "RVR1960", "NVI", "NTV", "LBLA", "DHH", "TLA")
    }

    suspend fun fetchBibles(): List<BibleDto> {
        if (hasValidApiBibleKey) {
            try {
                val res = service.getBibles().data
                val spanish = res.filter {
                    it.language?.id == "spa" || it.language?.name?.contains("Spanish", ignoreCase = true) == true
                }
                if (spanish.isNotEmpty()) return spanish
                if (res.isNotEmpty()) return res
            } catch (_: Exception) {
            }
        }
        return availableSpanishBibles()
    }

    suspend fun fetchBooks(bibleId: String): List<BookDto> {
        if (hasValidApiBibleKey && !isBollsBibleId(bibleId)) {
            try {
                return service.getBooks(bibleId).data
            } catch (_: Exception) {
            }
        }
        val translation = toBollsTranslation(bibleId)
        return try {
            bollsService.getBooks(translation).map { b ->
                val usfm = bookNumberToUsfm[b.bookid] ?: b.bookid.toString()
                BookDto(id = usfm, name = b.name, nameLong = b.name)
            }
        } catch (_: Exception) {
            // Static Spanish 66-book fallback so navigation works even offline
            (1..66).map { num ->
                val usfm = bookNumberToUsfm[num] ?: num.toString()
                val name = bookNumberToSpanishName[num] ?: "Libro $num"
                BookDto(id = usfm, name = name, nameLong = name)
            }
        }
    }

    suspend fun fetchChapters(bibleId: String, bookId: String): List<ChapterDto> {
        if (hasValidApiBibleKey && !isBollsBibleId(bibleId)) {
            try {
                return service.getChapters(bibleId, bookId).data.filter { it.number != "intro" }
            } catch (_: Exception) {
            }
        }
        val bookNum = resolveBookNumber(bookId)
        val count = bookNumberToChaptersCount[bookNum] ?: 1
        val usfm = bookNumberToUsfm[bookNum] ?: bookId
        val bookName = bookNumberToSpanishName[bookNum] ?: bookId
        return (1..count).map { num ->
            ChapterDto(id = "$usfm.$num", number = num.toString(), reference = "$bookName $num")
        }
    }

    internal suspend fun fetchChapter(
        bibleId: String,
        chapterId: String,
        apiBibleService: BibleApiService = service,
        bollsBibleService: BollsBibleApiService = bollsService,
        useApiBible: Boolean = hasValidApiBibleKey && !isBollsBibleId(bibleId)
    ): ResolvedChapter {
        val parts = chapterId.split(".")
        val rawBook = parts.getOrNull(0) ?: "JHN"
        val chapterNum = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 3
        val bookNum = resolveBookNumber(rawBook)
        val usfm = bookNumberToUsfm[bookNum] ?: rawBook
        val totalChapters = bookNumberToChaptersCount[bookNum] ?: chapterNum
        val bookName = bookNumberToSpanishName[bookNum] ?: rawBook

        if (useApiBible && !isBollsBibleId(bibleId)) {
            try {
                val res = apiBibleService.getChapterContent(bibleId, chapterId).data
                val parsed = validateChapterVerses(parseApiBibleHtmlToVerses(res.content), "API.Bible")
                return ResolvedChapter(
                    bibleId = bibleId,
                    bookId = usfm,
                    chapterId = "$usfm.$chapterNum",
                    chapterNumber = chapterNum,
                    totalChapters = totalChapters,
                    reference = res.reference ?: "$bookName $chapterNum",
                    verses = parsed
                )
            } catch (e: EmptyChapterException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never present a different translation as the requested API.Bible text.
                // The Bolls fallback is only valid for the known keyless translations.
                throw e
            }
        }

        val translation = toBollsTranslation(bibleId)
        val rawVerses = bollsBibleService.getChapterText(translation, bookNum, chapterNum)
        val verses = validateChapterVerses(rawVerses.map { v ->
            ParsedVerse(
                number = v.verse,
                text = cleanHtmlTags(v.text)
            )
        }, "Bolls.life")
        return ResolvedChapter(
            bibleId = translation,
            bookId = usfm,
            chapterId = "$usfm.$chapterNum",
            chapterNumber = chapterNum,
            totalChapters = totalChapters,
            reference = "$bookName $chapterNum",
            verses = verses
        )
    }

    suspend fun searchVerses(bibleId: String, query: String): List<SearchVerseDto> {
        val clean = query.trim()
        if (clean.isBlank()) return emptyList()

        if (hasValidApiBibleKey && !isBollsBibleId(bibleId)) {
            try {
                return service.searchBible(bibleId, clean).data.verses
            } catch (_: Exception) {
            }
        }
        val translation = toBollsTranslation(bibleId)
        return try {
            bollsService.search(translation, clean).take(25).map { hit ->
                val usfm = bookNumberToUsfm[hit.book] ?: hit.book.toString()
                val bookName = bookNumberToSpanishName[hit.book] ?: usfm
                SearchVerseDto(
                    id = "$usfm.${hit.chapter}.${hit.verse}",
                    bibleId = translation,
                    bookId = usfm,
                    chapterId = "$usfm.${hit.chapter}",
                    text = cleanHtmlTags(hit.text),
                    reference = "$bookName ${hit.chapter}:${hit.verse}"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun resolveBookNumber(bookId: String): Int {
        bookId.toIntOrNull()?.let { return it.coerceIn(1, 66) }
        usfmToBookNumber[bookId.uppercase()]?.let { return it }
        bookNumberToSpanishName.entries.firstOrNull {
            it.value.equals(bookId, ignoreCase = true)
        }?.let { return it.key }
        return 43 // Juan
    }

    fun cleanHtmlTags(raw: String): String {
        return raw
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<.*?>"), "")
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun parseApiBibleHtmlToVerses(html: String): List<ParsedVerse> {
        val verseRegex = Regex("<span[^>]*data-number=\"(\\d+)\"[^>]*>.*?</span>(.*?)(?=<span[^>]*data-number=\"\\d+\"|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val matches = verseRegex.findAll(html).toList()
        if (matches.isNotEmpty()) {
            return matches.mapNotNull { m ->
                val num = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val text = cleanHtmlTags(m.groupValues[2])
                if (text.isBlank()) null else ParsedVerse(num, text)
            }
        }
        val plain = cleanHtmlTags(html)
        return if (plain.isNotBlank()) listOf(ParsedVerse(1, plain)) else emptyList()
    }

    fun validateChapterVerses(verses: List<ParsedVerse>, source: String): List<ParsedVerse> {
        val nonEmptyVerses = verses.filter { it.text.isNotBlank() }
        if (nonEmptyVerses.isEmpty()) {
            throw EmptyChapterException("$source no devolvió texto para un capítulo")
        }
        return nonEmptyVerses
    }
}
