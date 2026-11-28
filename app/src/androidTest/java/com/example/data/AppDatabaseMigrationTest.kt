package com.example.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @Test
    fun migrationPreservesLegacyRowsAndAddsTranslationIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${System.nanoTime()}.db"
        val old = helper(context, name, 7, object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE verse_notes (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sectionId TEXT NOT NULL, verseReference TEXT NOT NULL, noteText TEXT NOT NULL, timestamp INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE favorite_verses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, sectionId TEXT NOT NULL, verseReference TEXT NOT NULL, verseText TEXT NOT NULL, timestamp INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE recent_chapters (chapterId TEXT NOT NULL PRIMARY KEY, bibleId TEXT NOT NULL, reference TEXT NOT NULL, timestamp INTEGER NOT NULL)")
            }
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        })
        old.writableDatabase.execSQL("INSERT INTO verse_notes(sectionId, verseReference, noteText, timestamp) VALUES ('JHN.3', 'Juan 3:16', 'nota legacy', 1)")
        old.writableDatabase.execSQL("INSERT INTO favorite_verses(sectionId, verseReference, verseText, timestamp) VALUES ('JHN.3', 'Juan 3:16', 'texto legacy', 2)")
        old.writableDatabase.execSQL("INSERT INTO recent_chapters(chapterId, bibleId, reference, timestamp) VALUES ('JHN.3', 'RVR1960', 'Juan 3', 3)")
        old.close()

        val upgraded = helper(context, name, 8, object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                AppDatabase.MIGRATION_7_8.migrate(db)
            }
        })
        val db = upgraded.writableDatabase
        assertThat(db.query("SELECT COUNT(*) FROM verse_notes").use { it.moveToFirst(); it.getInt(0) }).isEqualTo(1)
        assertThat(db.query("SELECT bibleId FROM verse_notes").use { it.moveToFirst(); it.isNull(0) }).isTrue()
        assertThat(db.query("SELECT COUNT(*) FROM favorite_verses").use { it.moveToFirst(); it.getInt(0) }).isEqualTo(1)
        assertThat(db.query("SELECT bibleId FROM favorite_verses").use { it.moveToFirst(); it.isNull(0) }).isTrue()
        assertThat(db.query("SELECT COUNT(*) FROM recent_chapters").use { it.moveToFirst(); it.getInt(0) }).isEqualTo(1)
        upgraded.close()
        context.deleteDatabase(name)
    }

    private fun helper(context: Context, name: String, version: Int, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(callback)
                .build()
        )
}
