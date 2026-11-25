package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
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

// --- Retrofit Interface ---
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
        @retrofit2.http.Query("query") query: String
    ): SearchResponse
}

// --- API Client ---
object BibleClient {
    private const val BASE_URL = "https://api.scripture.api.bible/"
    
    // Interceptor to add API Key to all requests
    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("api-key", BuildConfig.BIBLE_API_KEY)
        val request = requestBuilder.build()
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val service: BibleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(BibleApiService::class.java)
    }
}

