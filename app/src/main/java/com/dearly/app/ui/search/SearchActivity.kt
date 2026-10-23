package com.dearly.app.ui.search

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivitySearchBinding
import com.dearly.app.databinding.ItemFolderBinding
import com.dearly.app.databinding.ItemHeaderBinding
import com.dearly.app.databinding.ItemNoteBinding
import com.dearly.app.model.Folder
import com.dearly.app.model.NoteSummary
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.dp
import com.dearly.app.ui.common.show
import com.dearly.app.ui.editor.EditorActivity
import com.dearly.app.ui.library.FolderActivity
import com.dearly.app.ui.library.FolderBinder
import com.dearly.app.ui.library.NoteBinder

/** Searches note titles, note text, folder names/descriptions and tag names. */
class SearchActivity : BaseActivity() {
    private lateinit var b: ActivitySearchBinding
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var requestId = 0

    private sealed class Row {
        data class Header(val text: String) : Row()
        data class FolderRow(val folder: Folder) : Row()
        data class NoteRow(val note: NoteSummary) : Row()
    }

    private val adapter = ResultAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.rv.layoutManager = LinearLayoutManager(this)
        b.rv.adapter = adapter
        b.btnBack.setOnClickListener { finish() }
        b.btnClear.setOnClickListener { b.etQuery.text.clear() }
        b.etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                b.btnClear.show(!s.isNullOrEmpty())
                pending?.let { handler.removeCallbacks(it) }
                val r = Runnable { runSearch(s?.toString().orEmpty()) }
                pending = r
                handler.postDelayed(r, 250)
            }
        })
        b.etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false
        }
        b.etQuery.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        if (isUnlocked && b.etQuery.text.isNotBlank()) runSearch(b.etQuery.text.toString())
    }

    override fun onDestroy() {
        pending?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }

    private fun runSearch(query: String) {
        val q = query.trim()
        val id = ++requestId
        if (q.isEmpty()) {
            adapter.submitList(emptyList())
            b.tvEmpty.setText(R.string.search_prompt)
            b.tvEmpty.show(true)
            return
        }
        background(work = {
            ServiceLocator.folders.search(q) to ServiceLocator.notes.search(q)
        }) { (folders, notes) ->
            if (id != requestId) return@background // a newer query superseded this one
            val rows = ArrayList<Row>()
            if (folders.isNotEmpty()) {
                rows.add(Row.Header(getString(R.string.section_folders)))
                folders.forEach { rows.add(Row.FolderRow(it)) }
            }
            if (notes.isNotEmpty()) {
                rows.add(Row.Header(getString(R.string.section_notes)))
                notes.forEach { rows.add(Row.NoteRow(it)) }
            }
            adapter.submitList(rows)
            b.tvEmpty.setText(R.string.search_no_results)
            b.tvEmpty.show(rows.isEmpty())
        }
    }

    private inner class ResultAdapter : ListAdapter<Row, RecyclerView.ViewHolder>(DIFF) {
        override fun getItemViewType(position: Int) = when (getItem(position)) {
            is Row.Header -> 0
            is Row.FolderRow -> 1
            is Row.NoteRow -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> object : RecyclerView.ViewHolder(ItemHeaderBinding.inflate(inflater, parent, false).root) {}
                1 -> object : RecyclerView.ViewHolder(ItemFolderBinding.inflate(inflater, parent, false).also {
                    (it.root.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = parent.context.dp(10)
                }.root) {}
                else -> object : RecyclerView.ViewHolder(ItemNoteBinding.inflate(inflater, parent, false).root) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = getItem(position)) {
                is Row.Header -> ItemHeaderBinding.bind(holder.itemView).tvHeader.text = row.text
                is Row.FolderRow -> {
                    val fb = ItemFolderBinding.bind(holder.itemView)
                    FolderBinder.bind(fb, row.folder)
                    holder.itemView.setOnClickListener { startActivity(FolderActivity.newIntent(this@SearchActivity, row.folder.id)) }
                    holder.itemView.setOnLongClickListener(null)
                }
                is Row.NoteRow -> {
                    NoteBinder.bind(ItemNoteBinding.bind(holder.itemView), row.note)
                    holder.itemView.setOnClickListener { startActivity(EditorActivity.newIntent(this@SearchActivity, row.note.id)) }
                    holder.itemView.setOnLongClickListener(null)
                }
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(a: Row, b: Row) = when {
                a is Row.Header && b is Row.Header -> a.text == b.text
                a is Row.FolderRow && b is Row.FolderRow -> a.folder.id == b.folder.id
                a is Row.NoteRow && b is Row.NoteRow -> a.note.id == b.note.id
                else -> false
            }

            override fun areContentsTheSame(a: Row, b: Row) = a == b
        }
    }
}
