package com.dearly.app.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.FragmentHomeBinding
import com.dearly.app.databinding.ItemFolderBinding
import com.dearly.app.databinding.ItemNoteBinding
import com.dearly.app.model.Folder
import com.dearly.app.model.FolderSort
import com.dearly.app.model.NoteSort
import com.dearly.app.model.NoteSummary
import com.dearly.app.security.Session
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.MainActivity
import com.dearly.app.ui.common.background
import com.dearly.app.ui.common.show
import com.dearly.app.ui.editor.EditorActivity
import com.dearly.app.ui.library.FolderActivity
import com.dearly.app.ui.library.FolderBinder
import com.dearly.app.ui.library.FolderEditorDialog
import com.dearly.app.ui.library.ItemActions
import com.dearly.app.ui.library.NoteBinder
import com.dearly.app.ui.search.SearchActivity
import java.util.Calendar

class HomeFragment : Fragment() {
    private var _b: FragmentHomeBinding? = null
    private val b get() = _b!!
    private var fabOpen = false

    private class HomeData(val name: String, val folders: List<Folder>, val pinned: List<NoteSummary>, val latest: List<NoteSummary>)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.cardSearch.setOnClickListener { startActivity(Intent(requireContext(), SearchActivity::class.java)) }
        b.btnViewAllFolders.setOnClickListener { (activity as? MainActivity)?.openLibrary(0) }
        b.btnViewAllNotes.setOnClickListener { (activity as? MainActivity)?.openLibrary(1) }
        b.btnEmptyCreateFolder.setOnClickListener { createFolder() }
        b.btnEmptyCreateNote.setOnClickListener { createNote() }
        b.fabMain.setOnClickListener { setFabOpen(!fabOpen) }
        b.fabScrim.setOnClickListener { setFabOpen(false) }
        b.fabCreateNote.setOnClickListener { setFabOpen(false); createNote() }
        b.fabCreateFolder.setOnClickListener { setFabOpen(false); createFolder() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        setFabOpen(false)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) setFabOpen(false) else refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    private fun setFabOpen(open: Boolean) {
        if (_b == null) return
        fabOpen = open
        b.fabScrim.show(open)
        b.fabCreateNote.show(open)
        b.fabCreateFolder.show(open)
        b.fabMain.setImageResource(if (open) R.drawable.ic_close else R.drawable.ic_add)
        b.fabMain.contentDescription = getString(if (open) R.string.fab_close else R.string.fab_open)
    }

    private fun createNote() = startActivity(EditorActivity.newIntent(requireContext(), 0))

    private fun createFolder() {
        val host = activity as? BaseActivity ?: return
        FolderEditorDialog.show(host, null) { refresh() }
    }

    fun refresh() {
        if (_b == null || !Session.unlocked || isHidden) return
        background(work = {
            HomeData(
                ServiceLocator.settings.userName,
                ServiceLocator.folders.list(FolderSort.UPDATED, limit = 3),
                ServiceLocator.notes.list(NoteSort.UPDATED, pinnedOnly = true, limit = 5),
                ServiceLocator.notes.list(NoteSort.UPDATED, pinnedFirst = false, limit = 10)
            )
        }) { render(it) }
    }

    private fun greeting(name: String): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val res = when {
            hour < 12 -> R.string.greeting_morning
            hour < 18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
        return getString(res, name)
    }

    private fun render(data: HomeData) {
        val host = activity as? BaseActivity ?: return
        b.tvGreeting.text = greeting(data.name)

        // Folders (max three, equal width)
        b.llFolders.removeAllViews()
        b.llFolders.show(data.folders.isNotEmpty())
        b.emptyFolders.show(data.folders.isEmpty())
        for (i in 0 until 3) {
            // Equal-width, equal-height cards (MATCH_PARENT height makes the row as tall as its tallest card).
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            lp.marginEnd = if (i < 2) resources.getDimensionPixelSize(R.dimen.item_gap) else 0
            val folder = data.folders.getOrNull(i)
            if (folder == null) {
                b.llFolders.addView(View(requireContext()), lp)
                continue
            }
            val item = ItemFolderBinding.inflate(layoutInflater, b.llFolders, false)
            FolderBinder.bind(item, folder, showTags = false)
            item.root.setOnClickListener { startActivity(FolderActivity.newIntent(requireContext(), folder.id)) }
            item.root.setOnLongClickListener { ItemActions.folderMenu(host, folder) { refresh() }; true }
            b.llFolders.addView(item.root, lp)
        }

        fillNotes(b.llPinned, data.pinned, host)
        b.tvEmptyPinned.show(data.pinned.isEmpty())
        fillNotes(b.llLatest, data.latest, host)
        b.emptyLatest.show(data.latest.isEmpty())
    }

    private fun fillNotes(parent: LinearLayout, notes: List<NoteSummary>, host: BaseActivity) {
        parent.removeAllViews()
        for (note in notes) {
            val item = ItemNoteBinding.inflate(layoutInflater, parent, false)
            NoteBinder.bind(item, note)
            item.root.setOnClickListener { startActivity(EditorActivity.newIntent(requireContext(), note.id)) }
            item.root.setOnLongClickListener { ItemActions.noteMenu(host, note) { refresh() }; true }
            parent.addView(item.root)
        }
    }
}
