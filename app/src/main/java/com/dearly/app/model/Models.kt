package com.dearly.app.model

import com.dearly.app.model.richtext.Document
import com.dearly.app.utils.WallTime

data class Tag(val id: Long, val name: String)

data class Folder(
    val id: Long,
    val name: String,
    val description: String,
    val color: Int,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<Tag> = emptyList(),
    val noteCount: Int = 0,
    val deletedAt: Long? = null
)

data class NoteSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val folderId: Long?,
    val folderName: String?,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String>,
    val deletedAt: Long? = null
)

data class NoteFull(
    val id: Long,
    val title: String,
    val contentJson: String,
    val bodyText: String,
    val folderId: Long?,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val tagIds: List<Long>
)

/** What the editor hands to the repository. [id] == 0 means "not created yet". */
data class NoteDraft(
    val id: Long,
    val title: String,
    val doc: Document,
    val folderId: Long?,
    val tagIds: List<Long>,
    val pinned: Boolean
)

data class SaveResult(val noteId: Long, val createdAt: Long, val updatedAt: Long, val changed: Boolean)

data class Sticker(val id: Long, val name: String, val fileName: String, val width: Int, val height: Int)

data class Reminder(
    val id: Long,
    val title: String,
    val description: String,
    val start: WallTime,
    val repeat: RepeatType,
    val completed: Boolean,
    val lastFiredAt: Long?,
    val noteId: Long?,
    val noteTitle: String?
)

data class DeletedFolder(val folder: Folder, val deletedAt: Long)
data class DeletedNote(val note: NoteSummary, val deletedAt: Long)

enum class NoteSort { UPDATED, CREATED, TITLE }
enum class FolderSort { NAME, CREATED, UPDATED }

data class SearchResults(val notes: List<NoteSummary>, val folders: List<Folder>)

enum class RenameResult { OK, INVALID, DUPLICATE }
