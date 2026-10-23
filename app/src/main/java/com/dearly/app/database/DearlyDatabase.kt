package com.dearly.app.database

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Versioned SQLite schema. NEVER drop tables in [onUpgrade]: add an entry to [MIGRATIONS]
 * for every schema change so updates keep user data.
 *
 * Soft deletion: `deleted_at` (epoch millis). Notes removed together with their folder also carry
 * `deleted_via_folder_id`, which lets the folder be restored with exactly the notes it took with it.
 */
class DearlyDatabase(context: Context, name: String = DB_NAME) :
    SQLiteOpenHelper(context.applicationContext, name, null, DB_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (sql in SCHEMA) db.execSQL(sql)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (version in (oldVersion + 1)..newVersion) {
            MIGRATIONS[version]?.forEach { db.execSQL(it) }
        }
    }

    companion object {
        const val DB_NAME = "dearly.db"
        const val DB_VERSION = 1

        /** version -> statements that upgrade (version - 1) to version. */
        val MIGRATIONS: Map<Int, List<String>> = emptyMap()

        val SCHEMA: List<String> = listOf(
            """CREATE TABLE settings (
                key TEXT PRIMARY KEY NOT NULL,
                value TEXT
            )""",
            """CREATE TABLE folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                color INTEGER NOT NULL DEFAULT 0,
                pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER
            )""",
            """CREATE TABLE notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL DEFAULT '',
                body_text TEXT NOT NULL DEFAULT '',
                content_json TEXT NOT NULL DEFAULT '',
                folder_id INTEGER REFERENCES folders(id) ON DELETE SET NULL,
                pinned INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                deleted_at INTEGER,
                deleted_via_folder_id INTEGER REFERENCES folders(id) ON DELETE CASCADE
            )""",
            """CREATE TABLE tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                name_key TEXT NOT NULL UNIQUE
            )""",
            """CREATE TABLE note_tags (
                note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                PRIMARY KEY (note_id, tag_id)
            )""",
            """CREATE TABLE folder_tags (
                folder_id INTEGER NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
                tag_id INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
                PRIMARY KEY (folder_id, tag_id)
            )""",
            """CREATE TABLE stickers (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                file_name TEXT NOT NULL UNIQUE,
                width INTEGER NOT NULL,
                height INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )""",
            """CREATE TABLE note_stickers (
                note_id INTEGER NOT NULL REFERENCES notes(id) ON DELETE CASCADE,
                sticker_id INTEGER NOT NULL REFERENCES stickers(id) ON DELETE CASCADE,
                PRIMARY KEY (note_id, sticker_id)
            )""",
            """CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                local_start TEXT NOT NULL,
                repeat_type INTEGER NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                last_fired_at INTEGER,
                note_id INTEGER REFERENCES notes(id) ON DELETE SET NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""",
            "CREATE INDEX idx_notes_deleted_updated ON notes(deleted_at, updated_at)",
            "CREATE INDEX idx_notes_folder ON notes(folder_id)",
            "CREATE INDEX idx_notes_via_folder ON notes(deleted_via_folder_id)",
            "CREATE INDEX idx_folders_deleted ON folders(deleted_at)",
            "CREATE INDEX idx_note_tags_tag ON note_tags(tag_id)",
            "CREATE INDEX idx_folder_tags_tag ON folder_tags(tag_id)",
            "CREATE INDEX idx_reminders_note ON reminders(note_id)"
        )
    }
}

/** Runs [block] in a transaction that is rolled back if it throws. */
inline fun <T> SQLiteDatabase.inTx(block: () -> T): T {
    beginTransaction()
    try {
        val result = block()
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

/** Maps every row and always closes the cursor. */
inline fun <T> Cursor.mapRows(block: (Cursor) -> T): List<T> = use { c ->
    val out = ArrayList<T>(c.count)
    while (c.moveToNext()) out.add(block(c))
    out
}

fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
