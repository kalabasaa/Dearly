package com.dearly.app.ui.library

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dearly.app.R
import com.dearly.app.databinding.ItemFolderBinding
import com.dearly.app.databinding.ItemNoteBinding
import com.dearly.app.model.Folder
import com.dearly.app.model.NoteSummary
import com.dearly.app.ui.common.FolderColors
import com.dearly.app.ui.common.TagChips
import com.dearly.app.ui.common.show
import com.dearly.app.utils.Formatters

object NoteBinder {
    fun bind(b: ItemNoteBinding, n: NoteSummary, showFolder: Boolean = true) {
        val ctx = b.root.context
        b.tvTitle.text = n.title.ifBlank { ctx.getString(R.string.untitled) }
        b.tvPreview.text = n.preview
        b.tvPreview.show(n.preview.isNotBlank())
        b.ivPin.show(n.pinned)
        TagChips.fill(b.tagRow, n.tags)
        val updated = ctx.getString(R.string.updated_on, Formatters.dateTime(ctx, n.updatedAt))
        b.tvMeta.text = if (showFolder && n.folderName != null) "$updated  •  ${n.folderName}" else updated
    }
}

object FolderBinder {
    /** [showTags] is false for the compact folder cards on the home screen. */
    fun bind(b: ItemFolderBinding, f: Folder, showTags: Boolean = true) {
        val ctx = b.root.context
        b.root.setCardBackgroundColor(FolderColors.color(ctx, f.color))
        b.tvName.text = f.name
        b.tvCount.text = ctx.resources.getQuantityString(R.plurals.notes_count, f.noteCount, f.noteCount)
        b.ivPin.show(f.pinned)
        if (showTags) TagChips.fill(b.tagRow, f.tags.map { it.name }, 2) else b.tagRow.show(false)
    }
}

class NoteAdapter(
    private val onClick: (NoteSummary) -> Unit,
    private val onLongClick: (NoteSummary) -> Unit
) : ListAdapter<NoteSummary, NoteAdapter.VH>(DIFF) {

    class VH(val b: ItemNoteBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val note = getItem(position)
        NoteBinder.bind(holder.b, note)
        holder.b.root.setOnClickListener { onClick(note) }
        holder.b.root.setOnLongClickListener { onLongClick(note); true }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NoteSummary>() {
            override fun areItemsTheSame(a: NoteSummary, b: NoteSummary) = a.id == b.id
            override fun areContentsTheSame(a: NoteSummary, b: NoteSummary) = a == b
        }
    }
}

class FolderAdapter(
    private val onClick: (Folder) -> Unit,
    private val onLongClick: (Folder) -> Unit
) : ListAdapter<Folder, FolderAdapter.VH>(DIFF) {

    class VH(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root)

    // Spacing between cards comes from the grid's item decoration (see LibraryFragment), not per-item margins.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val folder = getItem(position)
        FolderBinder.bind(holder.b, folder)
        holder.b.root.setOnClickListener { onClick(folder) }
        holder.b.root.setOnLongClickListener { onLongClick(folder); true }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Folder>() {
            override fun areItemsTheSame(a: Folder, b: Folder) = a.id == b.id
            override fun areContentsTheSame(a: Folder, b: Folder) = a == b
        }
    }
}
