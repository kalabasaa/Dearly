package com.dearly.app.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.database.inTx
import com.dearly.app.database.mapRows
import com.dearly.app.model.RenameResult
import com.dearly.app.model.Tag
import com.dearly.app.utils.TextUtils

/**
 * Tags are unique case-insensitively (stored in `name_key`); the first spelling is kept for display.
 * Relationship tables use composite primary keys, so duplicate links are impossible.
 */
class TagRepository(private val dbh: DearlyDatabase) {

    fun all(): List<Tag> = dbh.readableDatabase
        .rawQuery("SELECT id, name FROM tags ORDER BY name COLLATE NOCASE", null)
        .mapRows { Tag(it.getLong(0), it.getString(1)) }

    /** Number of active notes + folders using each tag. */
    fun usageCounts(): Map<Long, Int> {
        val counts = HashMap<Long, Int>()
        dbh.readableDatabase.rawQuery(
            """SELECT tag_id, COUNT(*) FROM (
                 SELECT nt.tag_id FROM note_tags nt JOIN notes n ON n.id = nt.note_id WHERE n.deleted_at IS NULL
                 UNION ALL
                 SELECT ft.tag_id FROM folder_tags ft JOIN folders f ON f.id = ft.folder_id WHERE f.deleted_at IS NULL
               ) GROUP BY tag_id""", null
        ).mapRows { counts[it.getLong(0)] = it.getInt(1) }
        return counts
    }

    /** Returns the existing tag with the same name (ignoring case) or creates it. Null if the name is invalid. */
    fun findOrCreate(rawName: String): Tag? {
        val name = clean(rawName) ?: return null
        val db = dbh.writableDatabase
        return db.inTx {
            val key = TextUtils.tagKey(name)
            val existing = db.rawQuery("SELECT id, name FROM tags WHERE name_key = ?", arrayOf(key))
                .mapRows { Tag(it.getLong(0), it.getString(1)) }.firstOrNull()
            existing ?: Tag(
                db.insertOrThrow("tags", null, ContentValues().apply {
                    put("name", name)
                    put("name_key", key)
                }), name
            )
        }
    }

    fun rename(id: Long, rawName: String): RenameResult {
        val name = clean(rawName) ?: return RenameResult.INVALID
        val db = dbh.writableDatabase
        return db.inTx {
            val key = TextUtils.tagKey(name)
            val clash = db.rawQuery("SELECT 1 FROM tags WHERE name_key = ? AND id <> ?", arrayOf(key, id.toString()))
                .use { it.moveToFirst() }
            if (clash) {
                RenameResult.DUPLICATE
            } else {
                db.update("tags", ContentValues().apply {
                    put("name", name)
                    put("name_key", key)
                }, "id = ?", arrayOf(id.toString()))
                RenameResult.OK
            }
        }
    }

    /** Deleting a tag removes only its links (ON DELETE CASCADE); notes and folders are untouched. */
    fun delete(id: Long) {
        dbh.writableDatabase.delete("tags", "id = ?", arrayOf(id.toString()))
    }

    private fun clean(raw: String): String? =
        TextUtils.cleanName(raw.trim().trimStart('#'), MAX_LENGTH).ifEmpty { null }

    // ---- helpers used inside other repositories' transactions ----

    internal fun idsForNote(db: SQLiteDatabase, noteId: Long): List<Long> =
        db.rawQuery("SELECT tag_id FROM note_tags WHERE note_id = ?", arrayOf(noteId.toString()))
            .mapRows { it.getLong(0) }

    internal fun idsForFolder(db: SQLiteDatabase, folderId: Long): List<Long> =
        db.rawQuery("SELECT tag_id FROM folder_tags WHERE folder_id = ?", arrayOf(folderId.toString()))
            .mapRows { it.getLong(0) }

    internal fun replaceNoteTags(db: SQLiteDatabase, noteId: Long, tagIds: Collection<Long>) {
        db.delete("note_tags", "note_id = ?", arrayOf(noteId.toString()))
        for (tagId in tagIds.toSet()) {
            // INSERT..SELECT skips tag ids that were deleted in the meantime instead of failing the FK.
            db.execSQL(
                "INSERT OR IGNORE INTO note_tags(note_id, tag_id) SELECT ?, id FROM tags WHERE id = ?",
                arrayOf<Any?>(noteId, tagId)
            )
        }
    }

    internal fun replaceFolderTags(db: SQLiteDatabase, folderId: Long, tagIds: Collection<Long>) {
        db.delete("folder_tags", "folder_id = ?", arrayOf(folderId.toString()))
        for (tagId in tagIds.toSet()) {
            db.execSQL(
                "INSERT OR IGNORE INTO folder_tags(folder_id, tag_id) SELECT ?, id FROM tags WHERE id = ?",
                arrayOf<Any?>(folderId, tagId)
            )
        }
    }

    internal fun namesForNotes(db: SQLiteDatabase, noteIds: List<Long>): Map<Long, List<String>> {
        val out = HashMap<Long, MutableList<String>>()
        for (chunk in noteIds.chunked(CHUNK)) {
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT nt.note_id, t.name FROM note_tags nt JOIN tags t ON t.id = nt.tag_id " +
                    "WHERE nt.note_id IN ($marks) ORDER BY t.name COLLATE NOCASE",
                chunk.map { it.toString() }.toTypedArray()
            ).mapRows { out.getOrPut(it.getLong(0)) { ArrayList() }.add(it.getString(1)) }
        }
        return out
    }

    internal fun tagsForFolders(db: SQLiteDatabase, folderIds: List<Long>): Map<Long, List<Tag>> {
        val out = HashMap<Long, MutableList<Tag>>()
        for (chunk in folderIds.chunked(CHUNK)) {
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT ft.folder_id, t.id, t.name FROM folder_tags ft JOIN tags t ON t.id = ft.tag_id " +
                    "WHERE ft.folder_id IN ($marks) ORDER BY t.name COLLATE NOCASE",
                chunk.map { it.toString() }.toTypedArray()
            ).mapRows { out.getOrPut(it.getLong(0)) { ArrayList() }.add(Tag(it.getLong(1), it.getString(2))) }
        }
        return out
    }

    companion object {
        const val MAX_LENGTH = 30
        private const val CHUNK = 500
    }
}
