package com.dearly.app.repository

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.database.inTx
import com.dearly.app.database.longOrNull
import com.dearly.app.database.mapRows
import com.dearly.app.model.NoteDraft
import com.dearly.app.model.NoteFull
import com.dearly.app.model.NoteSort
import com.dearly.app.model.NoteSummary
import com.dearly.app.model.SaveResult
import com.dearly.app.model.richtext.NoteContentSerializer
import com.dearly.app.utils.TextUtils

class NoteRepository(private val dbh: DearlyDatabase, private val tags: TagRepository) {

    /**
     * Inserts or updates a note in one transaction.
     * `updated_at` only moves when title, content, folder or tags really changed, and `created_at` never changes.
     * Pin changes alone do not count as an edit.
     */
    fun saveNote(d: NoteDraft): SaveResult {
        val db = dbh.writableDatabase
        val json = NoteContentSerializer.toJson(d.doc)
        val body = d.doc.plainText()
        val now = System.currentTimeMillis()
        return db.inTx {
            if (d.id == 0L) {
                val id = db.insertOrThrow("notes", null, ContentValues().apply {
                    put("title", d.title)
                    put("body_text", body)
                    put("content_json", json)
                    if (d.folderId != null) put("folder_id", d.folderId) else putNull("folder_id")
                    put("pinned", if (d.pinned) 1 else 0)
                    put("created_at", now)
                    put("updated_at", now)
                })
                tags.replaceNoteTags(db, id, d.tagIds)
                replaceStickers(db, id, d)
                touchFolder(db, d.folderId, now)
                SaveResult(id, now, now, true)
            } else {
                val existing = db.rawQuery(
                    "SELECT title, content_json, folder_id, pinned, created_at, updated_at FROM notes " +
                        "WHERE id = ? AND deleted_at IS NULL", arrayOf(d.id.toString())
                ).use { c ->
                    if (!c.moveToFirst()) null else Existing(
                        c.getString(0), c.getString(1), c.longOrNull(2), c.getInt(3) == 1, c.getLong(4), c.getLong(5)
                    )
                } ?: throw IllegalStateException("Note ${d.id} no longer exists")

                val oldTags = tags.idsForNote(db, d.id).toSet()
                val contentChanged = existing.title != d.title || existing.json != json ||
                    existing.folderId != d.folderId || oldTags != d.tagIds.toSet()
                val pinChanged = existing.pinned != d.pinned

                if (!contentChanged && !pinChanged) {
                    SaveResult(d.id, existing.createdAt, existing.updatedAt, false)
                } else {
                    val cv = ContentValues()
                    cv.put("pinned", if (d.pinned) 1 else 0)
                    if (contentChanged) {
                        cv.put("title", d.title)
                        cv.put("body_text", body)
                        cv.put("content_json", json)
                        if (d.folderId != null) cv.put("folder_id", d.folderId) else cv.putNull("folder_id")
                        cv.put("updated_at", now)
                    }
                    db.update("notes", cv, "id = ?", arrayOf(d.id.toString()))
                    if (contentChanged) {
                        tags.replaceNoteTags(db, d.id, d.tagIds)
                        replaceStickers(db, d.id, d)
                        touchFolder(db, d.folderId, now)
                    }
                    SaveResult(d.id, existing.createdAt, if (contentChanged) now else existing.updatedAt, contentChanged)
                }
            }
        }
    }

    private class Existing(
        val title: String, val json: String, val folderId: Long?, val pinned: Boolean,
        val createdAt: Long, val updatedAt: Long
    )

    private fun replaceStickers(db: SQLiteDatabase, noteId: Long, d: NoteDraft) {
        db.delete("note_stickers", "note_id = ?", arrayOf(noteId.toString()))
        for (sid in d.doc.stickerIds()) {
            db.execSQL(
                "INSERT OR IGNORE INTO note_stickers(note_id, sticker_id) SELECT ?, id FROM stickers WHERE id = ?",
                arrayOf<Any?>(noteId, sid)
            )
        }
    }

    private fun touchFolder(db: SQLiteDatabase, folderId: Long?, now: Long) {
        if (folderId == null) return
        db.execSQL("UPDATE folders SET updated_at = ? WHERE id = ? AND deleted_at IS NULL", arrayOf<Any?>(now, folderId))
    }

    fun getFull(id: Long): NoteFull? {
        val db = dbh.readableDatabase
        val base = db.rawQuery(
            "SELECT id, title, content_json, body_text, folder_id, pinned, created_at, updated_at FROM notes " +
                "WHERE id = ? AND deleted_at IS NULL", arrayOf(id.toString())
        ).mapRows {
            NoteFull(
                it.getLong(0), it.getString(1), it.getString(2), it.getString(3), it.longOrNull(4),
                it.getInt(5) == 1, it.getLong(6), it.getLong(7), emptyList()
            )
        }.firstOrNull() ?: return null
        return base.copy(tagIds = tags.idsForNote(db, id))
    }

    fun list(
        sort: NoteSort,
        folderId: Long? = null,
        tagId: Long? = null,
        pinnedOnly: Boolean = false,
        pinnedFirst: Boolean = true,
        limit: Int? = null
    ): List<NoteSummary> {
        val where = StringBuilder("n.deleted_at IS NULL")
        val args = ArrayList<String>()
        if (folderId != null) { where.append(" AND n.folder_id = ?"); args.add(folderId.toString()) }
        if (pinnedOnly) where.append(" AND n.pinned = 1")
        if (tagId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM note_tags nt WHERE nt.note_id = n.id AND nt.tag_id = ?)")
            args.add(tagId.toString())
        }
        val key = when (sort) {
            NoteSort.UPDATED -> "n.updated_at DESC"
            NoteSort.CREATED -> "n.created_at DESC"
            NoteSort.TITLE -> "(n.title = '') ASC, n.title COLLATE NOCASE ASC"
        }
        val order = (if (pinnedFirst) "n.pinned DESC, " else "") + key + ", n.id DESC"
        return querySummaries(where.toString(), args, order, limit)
    }

    fun search(q: String): List<NoteSummary> {
        val like = "%" + TextUtils.escapeLike(q.trim()) + "%"
        return querySummaries(
            "n.deleted_at IS NULL AND (n.title LIKE ? ESCAPE '\\' OR n.body_text LIKE ? ESCAPE '\\' OR EXISTS (" +
                "SELECT 1 FROM note_tags nt JOIN tags t ON t.id = nt.tag_id WHERE nt.note_id = n.id AND t.name LIKE ? ESCAPE '\\'))",
            listOf(like, like, like), "n.updated_at DESC", 100
        )
    }

    internal fun querySummaries(where: String, args: List<String>, order: String, limit: Int?): List<NoteSummary> {
        val db = dbh.readableDatabase
        val sql = "SELECT n.id, n.title, substr(n.body_text, 1, 200), n.folder_id, f.name, n.pinned, " +
            "n.created_at, n.updated_at, n.deleted_at FROM notes n LEFT JOIN folders f ON f.id = n.folder_id " +
            "WHERE $where ORDER BY $order" + (if (limit != null) " LIMIT $limit" else "")
        val rows = db.rawQuery(sql, args.toTypedArray()).mapRows {
            NoteSummary(
                id = it.getLong(0), title = it.getString(1), preview = it.getString(2).replace('\n', ' '),
                folderId = it.longOrNull(3), folderName = if (it.isNull(4)) null else it.getString(4),
                pinned = it.getInt(5) == 1, createdAt = it.getLong(6), updatedAt = it.getLong(7),
                tags = emptyList(), deletedAt = it.longOrNull(8)
            )
        }
        val tagMap = tags.namesForNotes(db, rows.map { it.id })
        return rows.map { it.copy(tags = tagMap[it.id].orEmpty()) }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        dbh.writableDatabase.update("notes", ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ? AND deleted_at IS NULL", arrayOf(id.toString()))
    }

    /** Moves a note to [folderId] (null = no folder). Reminders and tags are keyed by note id and are unaffected. */
    fun moveToFolder(noteId: Long, folderId: Long?) {
        val now = System.currentTimeMillis()
        val db = dbh.writableDatabase
        db.inTx {
            db.update("notes", ContentValues().apply {
                if (folderId != null) put("folder_id", folderId) else putNull("folder_id")
                put("updated_at", now)
            }, "id = ? AND deleted_at IS NULL", arrayOf(noteId.toString()))
            touchFolder(db, folderId, now)
        }
    }

    fun setTags(noteId: Long, tagIds: List<Long>) {
        val db = dbh.writableDatabase
        db.inTx {
            tags.replaceNoteTags(db, noteId, tagIds)
            db.execSQL("UPDATE notes SET updated_at = ? WHERE id = ?", arrayOf<Any?>(System.currentTimeMillis(), noteId))
        }
    }

    fun tagIds(noteId: Long): List<Long> = tags.idsForNote(dbh.readableDatabase, noteId)
}
