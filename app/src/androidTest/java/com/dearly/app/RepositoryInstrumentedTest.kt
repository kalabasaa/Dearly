package com.dearly.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.model.Block
import com.dearly.app.model.Document
import com.dearly.app.model.FolderSort
import com.dearly.app.model.NoteDraft
import com.dearly.app.model.NoteSort
import com.dearly.app.model.Reminder
import com.dearly.app.model.RepeatType
import com.dearly.app.model.richtext.ListType
import com.dearly.app.model.richtext.NoteContentSerializer
import com.dearly.app.model.richtext.SpanData
import com.dearly.app.model.richtext.SpanKind
import com.dearly.app.repository.FolderRepository
import com.dearly.app.repository.NoteRepository
import com.dearly.app.repository.ReminderRepository
import com.dearly.app.repository.TagRepository
import com.dearly.app.repository.TrashRepository
import com.dearly.app.utils.WallTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Run with: ./gradlew connectedDebugAndroidTest (needs an emulator or device).
 * Uses a throw-away database file so it never touches real diary data.
 */
class RepositoryInstrumentedTest {
    private lateinit var context: Context
    private lateinit var dbh: DearlyDatabase
    private lateinit var tags: TagRepository
    private lateinit var folders: FolderRepository
    private lateinit var notes: NoteRepository
    private lateinit var trash: TrashRepository

    @Before fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        dbh = DearlyDatabase(context, TEST_DB) // separate file: never touches the real dearly.db
        tags = TagRepository(dbh)
        folders = FolderRepository(dbh, tags)
        notes = NoteRepository(dbh, tags)
        trash = TrashRepository(dbh, notes, folders)
    }

    @After fun tearDown() {
        dbh.close()
        context.deleteDatabase(TEST_DB)
    }

    private fun draft(id: Long = 0, title: String, text: String, folder: Long? = null, tagIds: List<Long> = emptyList()) =
        NoteDraft(id, title, Document(listOf(Block.Text(text))), folder, tagIds, false)

    @Test fun formattingSurvivesSaveAndReload() {
        val doc = Document(listOf(
            Block.Text("Hello world", listOf(SpanData(SpanKind.BOLD, 0, 5), SpanData(SpanKind.SIZE, 6, 11, 5))),
            Block.Text("task", list = ListType.CHECK, checked = true),
            Block.Table(listOf(listOf("a", "b"), listOf("c", "d"))),
            Block.Text("")
        ))
        val r = notes.saveNote(NoteDraft(0, "T", doc, null, emptyList(), false))
        val loaded = notes.getFull(r.noteId)!!
        assertEquals(doc, NoteContentSerializer.fromJson(loaded.contentJson, loaded.bodyText))
    }

    @Test fun creationDateNeverChangesAndUpdatedOnlyOnRealEdit() {
        val first = notes.saveNote(draft(title = "A", text = "one"))
        Thread.sleep(5)
        val same = notes.saveNote(draft(first.noteId, "A", "one"))
        assertFalse(same.changed)
        assertEquals(first.updatedAt, same.updatedAt)
        Thread.sleep(5)
        val edited = notes.saveNote(draft(first.noteId, "A", "two"))
        assertTrue(edited.changed)
        assertEquals(first.createdAt, edited.createdAt)
        assertTrue(edited.updatedAt > first.updatedAt)
    }

    @Test fun pinningDoesNotChangeUpdatedAt() {
        val a = notes.saveNote(draft(title = "A", text = "x"))
        notes.setPinned(a.noteId, true)
        assertEquals(a.updatedAt, notes.getFull(a.noteId)!!.updatedAt)
    }

    @Test fun folderDeleteAndRestoreKeepRelationships() {
        val f = folders.create("F", "", 0, emptyList())
        val n1 = notes.saveNote(draft(title = "in", text = "x", folder = f)).noteId
        val n2 = notes.saveNote(draft(title = "gone-earlier", text = "y", folder = f)).noteId
        trash.deleteNote(n2)
        trash.deleteFolder(f)
        assertNull(notes.getFull(n1))
        assertEquals(1, trash.deletedFolders().size)
        assertEquals(1, trash.deletedNotes().size) // only the note deleted on its own
        trash.restoreFolder(f)
        assertNotNull(notes.getFull(n1))
        assertEquals(f, notes.getFull(n1)!!.folderId)
        assertNull(notes.getFull(n2)) // stays deleted
        assertEquals(1, folders.list(FolderSort.NAME).size)
    }

    @Test fun purgeExpiredUsesStoredTimestamps() {
        val id = notes.saveNote(draft(title = "old", text = "x")).noteId
        trash.deleteNote(id)
        assertEquals(0, trash.purgeExpired(System.currentTimeMillis() + 29L * 86_400_000L))
        assertEquals(1, trash.purgeExpired(System.currentTimeMillis() + 31L * 86_400_000L))
        assertTrue(trash.deletedNotes().isEmpty())
    }

    @Test fun tagsAreCaseInsensitiveAndDeletingKeepsNotes() {
        val t1 = tags.findOrCreate("Travel")!!
        val t2 = tags.findOrCreate("travel")!!
        assertEquals(t1.id, t2.id)
        val n = notes.saveNote(draft(title = "n", text = "x", tagIds = listOf(t1.id))).noteId
        assertEquals(listOf("Travel"), notes.list(NoteSort.UPDATED).first { it.id == n }.tags)
        tags.delete(t1.id)
        assertTrue(notes.list(NoteSort.UPDATED).first { it.id == n }.tags.isEmpty())
    }

    @Test fun searchFindsTitleBodyFolderAndTag() {
        val f = folders.create("Holiday plans", "", 0, emptyList())
        val tag = tags.findOrCreate("gratitude")!!
        notes.saveNote(draft(title = "Monday", text = "went hiking", tagIds = listOf(tag.id)))
        assertEquals(1, notes.search("hiking").size)
        assertEquals(1, notes.search("mond").size)
        assertEquals(1, notes.search("gratit").size)
        assertEquals(1, folders.search("holiday").size)
        assertEquals(0, notes.search("100%").size) // wildcard characters are literal
        assertNotNull(f)
    }

    @Test fun reminderRoundTrip() {
        val rem = ReminderRepository(dbh)
        val id = rem.save(Reminder(0, "Call", "", WallTime(2030, 1, 2, 9, 30), RepeatType.WEEKLY, false, null, null, null))
        val loaded = rem.get(id)!!
        assertEquals(WallTime(2030, 1, 2, 9, 30), loaded.start)
        assertEquals(RepeatType.WEEKLY, loaded.repeat)
        rem.setCompleted(id, true)
        assertTrue(rem.get(id)!!.completed)
        rem.delete(id)
        assertNull(rem.get(id))
    }

    private companion object {
        const val TEST_DB = "dearly_test.db"
    }
}
