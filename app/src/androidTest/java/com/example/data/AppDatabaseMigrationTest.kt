package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun roomOpensAndValidatesSchemaFromVersion7To8PreservingLegacyRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-7-8-${System.nanoTime()}.db"
        try {
            createFixture(context, name, 7)
            seedLegacyRows(context, name, 7)

            val database = openRoomDatabase(context, name)
            val db = database.openHelper.writableDatabase

            assertLegacyRowsPreserved(db)
            assertEquals(8, db.version)
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun roomOpensAndValidatesSchemaFromVersion6Through7To8() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-6-8-${System.nanoTime()}.db"
        try {
            createFixture(context, name, 6)
            seedLegacyRows(context, name, 6)

            val database = openRoomDatabase(context, name)
            val db = database.openHelper.writableDatabase

            assertLegacyRowsPreserved(db)
            assertEquals(8, db.version)
            assertEquals(1, count(db, "study_events"))
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun concurrentFavoriteInsertsAreSerializedAndLegacyDuplicatesRemain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "favorite-concurrency-${System.nanoTime()}.db"
        try {
            createFixture(context, name, 7)
            seedLegacyRows(context, name, 7)
            val database = openRoomDatabase(context, name)
            val db = database.openHelper.writableDatabase
            db.execSQL("INSERT INTO favorite_verses(sectionId, bibleId, verseReference, verseText, timestamp) VALUES ('JHN.3', NULL, 'Juan 3:16', 'segundo legado', 3)")
            val dao = database.favoriteVerseDao()
            (1..20).map {
                async(Dispatchers.IO) {
                    dao.insertFavoriteVerseIfAbsent(FavoriteVerse(sectionId = "ROM.8", bibleId = "RVR1960", verseReference = "Romanos 8:1", verseText = "texto"))
                }
            }.awaitAll()
            dao.insertFavoriteVerseIfAbsent(FavoriteVerse(sectionId = "ROM.8", bibleId = "NVI", verseReference = "Romanos 8:1", verseText = "otra traducción"))
            assertEquals(2, db.query("SELECT COUNT(*) FROM favorite_verses WHERE sectionId = 'JHN.3'").use { it.moveToFirst(); it.getInt(0) })
            assertEquals(2, db.query("SELECT COUNT(*) FROM favorite_verses WHERE sectionId = 'ROM.8'").use { it.moveToFirst(); it.getInt(0) })
            database.close()
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun openRoomDatabase(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_6_7, AppDatabase.MIGRATION_7_8)
            .build()
            .also { it.openHelper.writableDatabase }

    private fun createFixture(context: Context, name: String, version: Int) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        createSchema(db, version)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
        )
        helper.writableDatabase
        helper.close()
    }

    private fun createSchema(db: SupportSQLiteDatabase, version: Int) {
        db.execSQL("""
            CREATE TABLE bible_sections (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                contentNTV TEXT NOT NULL,
                contentNVI TEXT NOT NULL,
                contentTLA TEXT NOT NULL,
                details TEXT NOT NULL,
                passageReference TEXT NOT NULL,
                isRead INTEGER NOT NULL,
                isBookmarked INTEGER NOT NULL,
                notes TEXT NOT NULL,
                readTimestamp INTEGER NOT NULL,
                rating INTEGER NOT NULL,
                aiExplanation TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE verse_notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sectionId TEXT NOT NULL,
                verseReference TEXT NOT NULL,
                noteText TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE favorite_verses (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                sectionId TEXT NOT NULL,
                verseReference TEXT NOT NULL,
                verseText TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE recent_chapters (
                chapterId TEXT NOT NULL PRIMARY KEY,
                bibleId TEXT NOT NULL,
                reference TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """.trimIndent())
        if (version >= 7) {
            db.execSQL("""
                CREATE TABLE study_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    details TEXT NOT NULL,
                    dateIso TEXT NOT NULL,
                    time TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    private fun seedLegacyRows(context: Context, name: String, version: Int) {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO verse_notes(sectionId, verseReference, noteText, timestamp) VALUES ('JHN.3', 'Juan 3:16', 'nota legacy', 1)")
        db.execSQL("INSERT INTO favorite_verses(sectionId, verseReference, verseText, timestamp) VALUES ('JHN.3', 'Juan 3:16', 'texto legacy', 2)")
        db.execSQL("INSERT INTO recent_chapters(chapterId, bibleId, reference, timestamp) VALUES ('JHN.3', 'RVR1960', 'Juan 3', 3)")
        helper.close()
    }

    private fun assertLegacyRowsPreserved(db: SupportSQLiteDatabase) {
        assertEquals(1, count(db, "verse_notes"))
        assertTrue(db.query("SELECT bibleId FROM verse_notes").use { it.moveToFirst(); it.isNull(0) })
        assertEquals(1, count(db, "favorite_verses"))
        assertTrue(db.query("SELECT bibleId FROM favorite_verses").use { it.moveToFirst(); it.isNull(0) })
        assertEquals(1, count(db, "recent_chapters"))
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
}
