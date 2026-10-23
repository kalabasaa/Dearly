package com.dearly.app.ui.stickers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityStickersBinding
import com.dearly.app.databinding.ItemStickerBinding
import com.dearly.app.model.Sticker
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.InputDialog
import com.dearly.app.ui.common.show
import com.dearly.app.utils.StickerImages
import com.dearly.app.utils.StickerStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The reusable sticker library. In pick mode tapping a sticker returns its id to the editor;
 * otherwise tapping opens rename/delete. Long-press always opens rename/delete.
 */
class StickerPickerActivity : BaseActivity() {
    private lateinit var b: ActivityStickersBinding
    private lateinit var adapter: StickerAdapter
    private var pickMode = false

    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importAll(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityStickersBinding.inflate(layoutInflater)
        setContentView(b.root)
        pickMode = intent.getBooleanExtra(EXTRA_PICK, false)
        b.toolbar.setNavigationOnClickListener { finish() }
        b.toolbar.setTitle(if (pickMode) R.string.sticker_choose else R.string.stickers)
        adapter = StickerAdapter(
            onClick = { if (pickMode) pick(it) else manage(it) },
            onLongClick = { manage(it) }
        )
        b.rv.layoutManager = GridLayoutManager(this, (resources.configuration.screenWidthDp / 110).coerceIn(3, 6))
        b.rv.adapter = adapter
        b.btnImport.setOnClickListener {
            importPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp"))
        }
    }

    override fun onResume() {
        super.onResume()
        if (isUnlocked) refresh()
    }

    private fun refresh() {
        background(work = { ServiceLocator.stickers.all() }) {
            adapter.submitList(it)
            b.tvEmpty.show(it.isEmpty())
        }
    }

    private fun pick(sticker: Sticker) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_STICKER_ID, sticker.id))
        finish()
    }

    private fun importAll(uris: List<android.net.Uri>) {
        val app = applicationContext
        b.btnImport.isEnabled = false
        background(
            onError = { b.btnImport.isEnabled = true; toast(R.string.error_generic) },
            work = {
                var ok = 0
                val failures = ArrayList<StickerStore.Failure>()
                for (uri in uris) {
                    when (val r = StickerStore.import(app, uri)) {
                        is StickerStore.ImportResult.Ok -> try {
                            ServiceLocator.stickers.add(r.suggestedName, r.fileName, r.width, r.height)
                            ok++
                        } catch (e: Exception) {
                            StickerStore.delete(app, r.fileName) // no orphan file if the DB write failed
                            failures.add(StickerStore.Failure.STORAGE)
                        }
                        is StickerStore.ImportResult.Failed -> failures.add(r.reason)
                    }
                }
                ok to failures
            }
        ) { (ok, failures) ->
            b.btnImport.isEnabled = true
            if (ok > 0) toast(resources.getQuantityString(R.plurals.stickers_imported, ok, ok))
            if (failures.isNotEmpty()) {
                toast(
                    getString(
                        when (failures.first()) {
                            StickerStore.Failure.NOT_AN_IMAGE -> R.string.sticker_error_not_image
                            StickerStore.Failure.TOO_LARGE -> R.string.sticker_error_too_large
                            StickerStore.Failure.UNREADABLE -> R.string.sticker_error_unreadable
                            StickerStore.Failure.STORAGE -> R.string.sticker_error_storage
                        }
                    )
                )
            }
            refresh()
        }
    }

    private fun manage(sticker: Sticker) {
        MaterialAlertDialogBuilder(this)
            .setTitle(sticker.name)
            .setItems(arrayOf(getString(R.string.rename), getString(R.string.delete))) { _, which ->
                if (which == 0) rename(sticker) else confirmDelete(sticker)
            }
            .show()
    }

    private fun rename(sticker: Sticker) {
        InputDialog.show(this, getString(R.string.rename), sticker.name, getString(R.string.hint_sticker_name), 40) { text ->
            val name = text.trim()
            if (name.isEmpty()) return@show getString(R.string.error_name_required)
            background(work = { ServiceLocator.stickers.rename(sticker.id, name) }) { refresh() }
            null
        }
    }

    private fun confirmDelete(sticker: Sticker) {
        background(work = { ServiceLocator.stickers.usageCount(sticker.id) }) { uses ->
            val message = if (uses > 0) resources.getQuantityString(R.plurals.sticker_delete_used, uses, uses)
            else getString(R.string.sticker_delete_unused)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.sticker_delete_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete) { _, _ ->
                    val app = applicationContext
                    background(work = {
                        ServiceLocator.stickers.delete(sticker.id)?.let { file ->
                            StickerImages.evict(file)
                            StickerStore.delete(app, file)
                        }
                    }) { refresh() }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private class StickerAdapter(
        val onClick: (Sticker) -> Unit,
        val onLongClick: (Sticker) -> Unit
    ) : ListAdapter<Sticker, StickerAdapter.VH>(DIFF) {
        class VH(val b: ItemStickerBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemStickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = getItem(position)
            holder.b.tvName.text = s.name
            StickerImages.bind(holder.b.ivThumb, s.fileName, 256)
            holder.b.root.contentDescription = s.name
            holder.b.root.setOnClickListener { onClick(s) }
            holder.b.root.setOnLongClickListener { onLongClick(s); true }
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<Sticker>() {
                override fun areItemsTheSame(a: Sticker, b: Sticker) = a.id == b.id
                override fun areContentsTheSame(a: Sticker, b: Sticker) = a == b
            }
        }
    }

    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_STICKER_ID = "sticker_id"

        fun pickIntent(context: Context): Intent =
            Intent(context, StickerPickerActivity::class.java).putExtra(EXTRA_PICK, true)

        fun manageIntent(context: Context): Intent = Intent(context, StickerPickerActivity::class.java)
    }
}
