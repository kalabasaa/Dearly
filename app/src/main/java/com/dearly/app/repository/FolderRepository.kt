package com.dearly.app.repository

import android.content.ContentValues
import android.database.Cursor
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.database.inTx
import com.dearly.app.database.mapRows
import com.dearly.app.model.Folder
import com.dearly.app.model.FolderSort
import com.dearly.app.utils.TextUtils

class FolderRepository(private val dbh: DearlyDatabase, private val tags: TagRepository) {

    fun create(name: String, description: String, color: Int, tagIds: List<Long>): Long {
        val now = System.currentTimeMillis()
        val db = dbh.writableDatabase
        return db.inTx {
            val id = db.insertOrThrow("folders", null, ContentValues().apply {
                put("name", name.trim())
                put("description", description.trim())
                put("color", color)
                put("pinned", 0)
                put("created_at", now)
                put("updated_at", now)
            })
            tags.replaceFolderTags(db, id, tagIds)
            id
        }
    }

    fun update(id: Long, name: String, description: String, color: Int, tagIds: List<Long>) {
        val db = dbh.writableDatabase
        db.inTx {
            db.update("folders", ContentValues().apply {
                put("name", name.trim())
                put("description", description.trim())
                put("color", color)
                put("updated_at", System.currentTimeMillis())
            }, "id = ? AND deleted_at IS NULL", arrayOf(id.toString()))
            tags.replaceFolderTags(db, id, tagIds)
        }
    }

    /** Pinning does not count as an edit, so it leaves updated_at alone. */
    fun setPinned(id: Long, pinned: Boolean) {
        dbh.writableDatabase.update("folders", ContentValues().apply { put("pinned", if (pinned) 1 else 0) },
            "id = ? AND deleted_at IS NULL", arrayOf(id.toString()))
    }

    fun get(id: Long): Folder? = query("f.id = ? AND f.deleted_at IS NULL", listOf(id.toString()), FolderSort.UPDATED, null)
        .firstOrNull()

    fun list(sort: FolderSort, tagId: Long? = null, limit: Int? = null): List<Folder> {
        val where = StringBuilder("f.deleted_at IS NULL")
        val args = ArrayList<String>()
        if (tagId != null) {
            where.append(" AND EXISTS (SELECT 1 FROM folder_tags ft WHERE ft.folder_id = f.id AND ft.tag_id = ?)")
            args.add(tagId.toString())
        }
        return query(where.toString(), args, sort, limit)
    }

    fun search(q: String): List<Folder> {
        val like = "%" + TextUtils.escapeLike(q.trim()) + "%"
        return query(
            "f.deleted_at IS NULL AND (f.name LIKE ? ESCAPE '\\' OR f.description LIKE ? ESCAPE '\\' OR EXISTS (" +
                "SELECT 1 FROM folder_tags ft JOIN tags t ON t.id = ft.tag_id WHERE ft.folder_id = f.id AND t.name LIKE ? ESCAPE '\\'))",
            listOf(like, like, like), FolderSort.NAME, 50
        )
    }

    internal fun query(where: String, args: List<String>, sort: FolderSort, limit: Int?): List<Folder> {
        val order = when (sort) {
            FolderSort.NAME -> "f.name COLLATE NOCASE ASC"
            FolderSort.CREATED -> "f.created_at DESC"
            FolderSort.UPDATED -> "f.updated_at DESC"
        }
        val sql = "SELECT f.id, f.name, f.description, f.color, f.pinned, f.created_at, f.updated_at, f.deleted_at, " +
            "(SELECT COUNT(*) FROM notes n WHERE n.folder_id = f.id AND n.deleted_at IS NULL) " +
            "FROM folders f WHERE $where ORDER BY f.pinned DESC, $order, f.id DESC" +
            (if (limit != null) " LIMIT $limit" else "")
        val db = dbh.readableDatabase
        val rows = db.rawQuery(sql, args.toTypedArray()).mapRows { readFolder(it) }
        val tagMap = tags.tagsForFolders(db, rows.map { it.id })
        return rows.map { it.copy(tags = tagMap[it.id].orEmpty()) }
    }

    private fun readFolder(c: Cursor) = Folder(
        id = c.getLong(0), name = c.getString(1), description = c.getString(2), color = c.getInt(3),
        pinned = c.getInt(4) == 1, createdAt = c.getLong(5), updatedAt = c.getLong(6),
        deletedAt = if (c.isNull(7)) null else c.getLong(7), noteCount = c.getInt(8)
    )
}
