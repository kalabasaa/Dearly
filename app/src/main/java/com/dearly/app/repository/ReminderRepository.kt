package com.dearly.app.repository

import android.content.ContentValues
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.database.longOrNull
import com.dearly.app.database.mapRows
import com.dearly.app.model.Reminder
import com.dearly.app.model.RepeatType
import com.dearly.app.utils.WallTime

class ReminderRepository(private val dbh: DearlyDatabase) {

    /** Inserts when [Reminder.id] is 0, otherwise updates. Editing a reminder clears its "fired" marker. */
    fun save(r: Reminder): Long {
        val now = System.currentTimeMillis()
        val db = dbh.writableDatabase
        val cv = ContentValues().apply {
            put("title", r.title)
            put("description", r.description)
            put("local_start", r.start.serialize())
            put("repeat_type", r.repeat.code)
            put("completed", if (r.completed) 1 else 0)
            putNull("last_fired_at")
            if (r.noteId != null) put("note_id", r.noteId) else putNull("note_id")
            put("updated_at", now)
        }
        return if (r.id == 0L) {
            cv.put("created_at", now)
            db.insertOrThrow("reminders", null, cv)
        } else {
            db.update("reminders", cv, "id = ?", arrayOf(r.id.toString()))
            r.id
        }
    }

    fun get(id: Long): Reminder? = query("r.id = ?", arrayOf(id.toString())).firstOrNull()

    fun all(): List<Reminder> = query(null, null)

    private fun query(where: String?, args: Array<String>?): List<Reminder> {
        val sql = "SELECT r.id, r.title, r.description, r.local_start, r.repeat_type, r.completed, r.last_fired_at, " +
            "n.id, n.title FROM reminders r LEFT JOIN notes n ON n.id = r.note_id AND n.deleted_at IS NULL" +
            (if (where != null) " WHERE $where" else "") + " ORDER BY r.local_start, r.id"
        return dbh.readableDatabase.rawQuery(sql, args).mapRows {
            val start = WallTime.parse(it.getString(3)) ?: WallTime(1970, 1, 1, 0, 0)
            Reminder(
                id = it.getLong(0), title = it.getString(1), description = it.getString(2), start = start,
                repeat = RepeatType.fromCode(it.getInt(4)), completed = it.getInt(5) == 1,
                lastFiredAt = it.longOrNull(6), noteId = it.longOrNull(7),
                noteTitle = if (it.isNull(8)) null else it.getString(8)
            )
        }
    }

    fun delete(id: Long) {
        dbh.writableDatabase.delete("reminders", "id = ?", arrayOf(id.toString()))
    }

    fun setCompleted(id: Long, completed: Boolean) {
        dbh.writableDatabase.update("reminders", ContentValues().apply {
            put("completed", if (completed) 1 else 0)
            putNull("last_fired_at")
            put("updated_at", System.currentTimeMillis())
        }, "id = ?", arrayOf(id.toString()))
    }

    fun markFired(id: Long, at: Long) {
        dbh.writableDatabase.update("reminders", ContentValues().apply { put("last_fired_at", at) },
            "id = ?", arrayOf(id.toString()))
    }
}
