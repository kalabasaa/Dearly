package com.dearly.app.repository

import android.content.ContentValues
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.database.mapRows
import com.dearly.app.model.Sticker

/** Sticker metadata. Image bytes live in app-private storage; notes only reference sticker ids. */
class StickerRepository(private val dbh: DearlyDatabase) {

    fun add(name: String, fileName: String, width: Int, height: Int): Long =
        dbh.writableDatabase.insertOrThrow("stickers", null, ContentValues().apply {
            put("name", name)
            put("file_name", fileName)
            put("width", width)
            put("height", height)
            put("created_at", System.currentTimeMillis())
        })

    fun all(): List<Sticker> = dbh.readableDatabase
        .rawQuery("SELECT id, name, file_name, width, height FROM stickers ORDER BY created_at DESC, id DESC", null)
        .mapRows { Sticker(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3), it.getInt(4)) }

    fun get(id: Long): Sticker? = dbh.readableDatabase
        .rawQuery("SELECT id, name, file_name, width, height FROM stickers WHERE id = ?", arrayOf(id.toString()))
        .mapRows { Sticker(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3), it.getInt(4)) }
        .firstOrNull()

    /** Returns map id -> sticker for every id found (missing ids are simply absent). */
    fun getAll(ids: Collection<Long>): Map<Long, Sticker> = all().filter { it.id in ids }.associateBy { it.id }

    fun rename(id: Long, name: String) {
        dbh.writableDatabase.update("stickers", ContentValues().apply { put("name", name.trim()) },
            "id = ?", arrayOf(id.toString()))
    }

    fun usageCount(id: Long): Int = dbh.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM note_stickers ns JOIN notes n ON n.id = ns.note_id " +
            "WHERE ns.sticker_id = ? AND n.deleted_at IS NULL", arrayOf(id.toString())
    ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Deletes the row and returns the file name so the caller can delete the file. */
    fun delete(id: Long): String? {
        val sticker = get(id) ?: return null
        dbh.writableDatabase.delete("stickers", "id = ?", arrayOf(id.toString()))
        return sticker.fileName
    }
}
