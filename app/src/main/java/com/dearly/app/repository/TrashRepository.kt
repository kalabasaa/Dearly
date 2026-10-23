package com.dearly.app.repository

import android.content.ContentValues
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.database.inTx
import com.dearly.app.model.DeletedFolder
import com.dearly.app.model.DeletedNote
import com.dearly.app.model.FolderSort
import com.dearly.app.utils.RetentionPolicy

/**
 * Soft deletion, restoration and the 30-day purge.
 *
 * Folder deletion policy: the folder and every ACTIVE note in it are soft-deleted at the same instant,
 * and those notes remember `deleted_via_folder_id`. Restoring the folder restores exactly those notes.
 * Notes deleted on their own earlier stay in Recently Deleted with their own timestamp.
 */
class TrashRepository(private val dbh: DearlyDatabase, private val notes: NoteRepository, private val folders: FolderRepository) {

    fun deleteNote(id: Long) {
        dbh.writableDatabase.update("notes", ContentValues().apply {
            put("deleted_at", System.currentTimeMillis())
        }, "id = ? AND deleted_at IS NULL", arrayOf(id.toString()))
    }

    fun deleteFolder(id: Long) {
        val now = System.currentTimeMillis()
        val db = dbh.writableDatabase
        db.inTx {
            val changed = db.update("folders", ContentValues().apply { put("deleted_at", now) },
                "id = ? AND deleted_at IS NULL", arrayOf(id.toString()))
            if (changed > 0) {
                db.update("notes", ContentValues().apply {
                    put("deleted_at", now)
                    put("deleted_via_folder_id", id)
                }, "folder_id = ? AND deleted_at IS NULL", arrayOf(id.toString()))
            }
        }
    }

    fun deletedFolders(): List<DeletedFolder> =
        folders.query("f.deleted_at IS NOT NULL", emptyList(), FolderSort.UPDATED, null)
            .map { DeletedFolder(it, it.deletedAt ?: 0L) }
            .sortedByDescending { it.deletedAt }

    /** Notes deleted on their own (notes that went with a folder are listed under that folder). */
    fun deletedNotes(): List<DeletedNote> =
        notes.querySummaries("n.deleted_at IS NOT NULL AND n.deleted_via_folder_id IS NULL", emptyList(), "n.deleted_at DESC", null)
            .map { DeletedNote(it, it.deletedAt ?: 0L) }

    fun countNotesDeletedWithFolder(folderId: Long): Int =
        dbh.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM notes WHERE deleted_via_folder_id = ?", arrayOf(folderId.toString())
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Restores a note; if its folder is still in the trash the note comes back without a folder. */
    fun restoreNote(id: Long) {
        val db = dbh.writableDatabase
        db.inTx {
            db.execSQL(
                "UPDATE notes SET deleted_at = NULL, deleted_via_folder_id = NULL, " +
                    "folder_id = CASE WHEN folder_id IN (SELECT id FROM folders WHERE deleted_at IS NOT NULL) THEN NULL ELSE folder_id END " +
                    "WHERE id = ? AND deleted_at IS NOT NULL", arrayOf<Any?>(id)
            )
        }
    }

    fun restoreFolder(id: Long) {
        val db = dbh.writableDatabase
        db.inTx {
            val changed = db.update("folders", ContentValues().apply {
                putNull("deleted_at")
                put("updated_at", System.currentTimeMillis())
            }, "id = ? AND deleted_at IS NOT NULL", arrayOf(id.toString()))
            if (changed > 0) {
                db.execSQL(
                    "UPDATE notes SET deleted_at = NULL, deleted_via_folder_id = NULL WHERE deleted_via_folder_id = ?",
                    arrayOf<Any?>(id)
                )
            }
        }
    }

    fun purgeNote(id: Long) {
        dbh.writableDatabase.delete("notes", "id = ? AND deleted_at IS NOT NULL", arrayOf(id.toString()))
    }

    /** Also removes the notes that were deleted with this folder (FK cascade on deleted_via_folder_id). */
    fun purgeFolder(id: Long) {
        dbh.writableDatabase.delete("folders", "id = ? AND deleted_at IS NOT NULL", arrayOf(id.toString()))
    }

    /** Removes everything whose stored deletion time is older than the retention window. Returns rows removed. */
    fun purgeExpired(now: Long = System.currentTimeMillis()): Int {
        val cutoff = RetentionPolicy.cutoff(now).toString()
        val db = dbh.writableDatabase
        return db.inTx {
            val n = db.delete("notes", "deleted_at IS NOT NULL AND deleted_at <= ?", arrayOf(cutoff))
            val f = db.delete("folders", "deleted_at IS NOT NULL AND deleted_at <= ?", arrayOf(cutoff))
            n + f
        }
    }
}
