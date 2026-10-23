package com.dearly.app.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.graphics.Rect
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.FragmentLibraryBinding
import com.dearly.app.model.FolderSort
import com.dearly.app.model.NoteSort
import com.dearly.app.model.Tag
import com.dearly.app.security.Session
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.background
import com.dearly.app.ui.common.show
import com.dearly.app.ui.editor.EditorActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

/** All Folders / All Notes with sorting and tag filtering. */
class LibraryFragment : Fragment() {
    private var _b: FragmentLibraryBinding? = null
    private val b get() = _b!!

    private var page = 0
    private var folderSort = FolderSort.UPDATED
    private var noteSort = NoteSort.UPDATED
    private var tagFilter: Tag? = null

    private var gridSpacing: RecyclerView.ItemDecoration? = null

    private lateinit var folderAdapter: FolderAdapter
    private lateinit var noteAdapter: NoteAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentLibraryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val host = requireActivity() as BaseActivity
        folderAdapter = FolderAdapter(
            onClick = { startActivity(FolderActivity.newIntent(requireContext(), it.id)) },
            onLongClick = { ItemActions.folderMenu(host, it) { refresh() } }
        )
        noteAdapter = NoteAdapter(
            onClick = { startActivity(EditorActivity.newIntent(requireContext(), it.id)) },
            onLongClick = { ItemActions.noteMenu(host, it) { refresh() } }
        )
        b.tabs.addTab(b.tabs.newTab().setText(R.string.all_folders))
        b.tabs.addTab(b.tabs.newTab().setText(R.string.all_notes))
        b.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (page != tab.position) { page = tab.position; applyPage(); refresh() }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        b.btnSort.setOnClickListener { chooseSort() }
        b.btnFilterTag.setOnClickListener { chooseTagFilter() }
        b.fabAdd.setOnClickListener {
            if (page == 0) FolderEditorDialog.show(host, null) { refresh() }
            else startActivity(EditorActivity.newIntent(requireContext(), 0))
        }
        applyPage()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) refresh()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    fun showPage(p: Int) {
        if (_b == null) return
        b.tabs.getTabAt(p)?.select()
        if (page != p) { page = p; applyPage() }
        refresh()
    }

    private fun applyPage() {
        if (_b == null) return
        if (page == 0) {
            val span = (resources.configuration.screenWidthDp / 170).coerceIn(2, 4)
            b.rv.layoutManager = GridLayoutManager(requireContext(), span)
            gridSpacing?.let { b.rv.removeItemDecoration(it) }
            gridSpacing = gridSpacingDecoration(span).also { b.rv.addItemDecoration(it) }
            b.rv.adapter = folderAdapter
            b.fabAdd.setText(R.string.new_folder)
        } else {
            gridSpacing?.let { b.rv.removeItemDecoration(it) }
            gridSpacing = null
            b.rv.layoutManager = LinearLayoutManager(requireContext())
            b.rv.adapter = noteAdapter
            b.fabAdd.setText(R.string.new_note)
        }
        updateButtons()
    }

    /** Even gaps between folder cards while the outer edges stay flush with the page margin. */
    private fun gridSpacingDecoration(span: Int): RecyclerView.ItemDecoration {
        val gap = resources.getDimensionPixelSize(R.dimen.item_gap)
        return object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                val column = (view.layoutParams as GridLayoutManager.LayoutParams).spanIndex
                outRect.left = column * gap / span
                outRect.right = gap - (column + 1) * gap / span
                outRect.bottom = gap
            }
        }
    }

    private fun updateButtons() {
        val sortLabel = if (page == 0) when (folderSort) {
            FolderSort.NAME -> R.string.sort_name
            FolderSort.CREATED -> R.string.sort_created
            FolderSort.UPDATED -> R.string.sort_updated
        } else when (noteSort) {
            NoteSort.TITLE -> R.string.sort_title
            NoteSort.CREATED -> R.string.sort_created
            NoteSort.UPDATED -> R.string.sort_updated
        }
        b.btnSort.text = getString(R.string.sort_by, getString(sortLabel))
        b.btnFilterTag.text = tagFilter?.let { getString(R.string.filter_tag_active, it.name) } ?: getString(R.string.filter_tag)
    }

    fun refresh() {
        if (_b == null || !Session.unlocked || isHidden) return
        val currentPage = page
        val tagId = tagFilter?.id
        if (currentPage == 0) {
            val sort = folderSort
            background(work = { ServiceLocator.folders.list(sort, tagId) }) { list ->
                if (page != 0) return@background
                folderAdapter.submitList(list)
                showEmpty(list.isEmpty())
            }
        } else {
            val sort = noteSort
            background(work = { ServiceLocator.notes.list(sort, tagId = tagId) }) { list ->
                if (page != 1) return@background
                noteAdapter.submitList(list)
                showEmpty(list.isEmpty())
            }
        }
    }

    private fun showEmpty(empty: Boolean) {
        b.tvEmpty.show(empty)
        b.tvEmpty.setText(
            when {
                tagFilter != null -> R.string.empty_filtered
                page == 0 -> R.string.empty_folders_library
                else -> R.string.empty_notes_library
            }
        )
    }

    private fun chooseSort() {
        if (page == 0) {
            val options = arrayOf(R.string.sort_updated, R.string.sort_created, R.string.sort_name)
            val values = arrayOf(FolderSort.UPDATED, FolderSort.CREATED, FolderSort.NAME)
            showSortDialog(options, values.indexOf(folderSort)) { folderSort = values[it] }
        } else {
            val options = arrayOf(R.string.sort_updated, R.string.sort_created, R.string.sort_title)
            val values = arrayOf(NoteSort.UPDATED, NoteSort.CREATED, NoteSort.TITLE)
            showSortDialog(options, values.indexOf(noteSort)) { noteSort = values[it] }
        }
    }

    private fun showSortDialog(labels: Array<Int>, checked: Int, onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort)
            .setSingleChoiceItems(labels.map { getString(it) }.toTypedArray(), checked) { dialog, which ->
                dialog.dismiss()
                onPick(which)
                updateButtons()
                refresh()
            }
            .show()
    }

    private fun chooseTagFilter() {
        background(work = { ServiceLocator.tags.all() }) { tags ->
            val names = (listOf(getString(R.string.all_tags)) + tags.map { "#" + it.name }).toTypedArray()
            val checked = tagFilter?.let { f -> tags.indexOfFirst { it.id == f.id } + 1 } ?: 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.filter_tag)
                .setSingleChoiceItems(names, checked) { dialog, which ->
                    dialog.dismiss()
                    tagFilter = if (which == 0) null else tags[which - 1]
                    updateButtons()
                    refresh()
                }
                .show()
        }
    }
}
