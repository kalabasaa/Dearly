package com.dearly.app.ui.editor

import android.graphics.Paint
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TableRow
import com.dearly.app.R
import com.dearly.app.databinding.RowStickerBlockBinding
import com.dearly.app.databinding.RowTableBlockBinding
import com.dearly.app.databinding.RowTextBlockBinding
import com.dearly.app.model.richtext.Block
import com.dearly.app.model.richtext.BlockOps
import com.dearly.app.model.richtext.Document
import com.dearly.app.model.richtext.ListType
import com.dearly.app.model.richtext.SpanData
import com.dearly.app.model.richtext.SpanKind
import com.dearly.app.model.richtext.SpanOps
import com.dearly.app.model.richtext.StickerSizes
import com.dearly.app.model.richtext.TextSizes
import com.dearly.app.ui.BaseActivity
import com.dearly.app.ui.common.dp
import com.dearly.app.ui.common.show
import com.dearly.app.utils.StickerImages

/**
 * Renders a [Document] as a vertical list of blocks (paragraph/list items, tables, stickers) and edits it.
 * Text formatting applies to the selection of ONE paragraph (a selection cannot span blocks).
 * Structural changes (Enter, Backspace, lists, tables, stickers, undo) rebuild the views from the model.
 */
class BlockEditor(
    private val activity: BaseActivity,
    private val container: LinearLayout,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        /** [immediate] = structural/format change that should become its own undo step right away. */
        fun onContentChanged(immediate: Boolean)
        fun onFormatStateChanged()
    }

    class FormatState(
        val bold: Boolean, val italic: Boolean, val underline: Boolean, val highlight: Boolean, val sizeLevel: Int?
    )

    var stickerFiles: Map<Long, String> = emptyMap()
    var lastFocusedIndex = 0
        private set

    private sealed class Holder { abstract val root: View }
    private class TextHolder(val b: RowTextBlockBinding, var list: ListType) : Holder() {
        override val root: View get() = b.root
    }
    private class TableHolder(val b: RowTableBlockBinding, val cells: List<List<EditText>>) : Holder() {
        override val root: View get() = b.root
    }
    private class StickerHolder(val b: RowStickerBlockBinding, val stickerId: Long, var sizeDp: Int) : Holder() {
        override val root: View get() = b.root
    }

    private val holders = ArrayList<Holder>()
    private val inflater = LayoutInflater.from(activity)
    private var building = false
    private var splitScheduled = false
    private var lastSelStart = 0
    private var lastSelEnd = 0

    private val highlightColor: Int get() = activity.getColor(R.color.dearly_highlight)

    // ------------------------------------------------------------------ rendering

    fun render(doc: Document, focusIndex: Int = -1, focusPos: Int = 0) {
        building = true
        try {
            container.removeAllViews()
            holders.clear()
            val blocks = BlockOps.ensureTrailingText(doc.blocks)
            val numbering = BlockOps.numbering(blocks)
            blocks.forEachIndexed { i, block ->
                val holder = when (block) {
                    is Block.Text -> buildText(block, numbering[i])
                    is Block.Table -> buildTable(block)
                    is Block.Sticker -> buildSticker(block)
                }
                holders.add(holder)
                container.addView(holder.root)
            }
        } finally {
            building = false
        }
        lastFocusedIndex = lastFocusedIndex.coerceIn(0, holders.size - 1)
        if (focusIndex >= 0) focus(focusIndex, focusPos)
    }

    fun readDocument(): Document = Document(holders.map { holder ->
        when (holder) {
            is TextHolder -> {
                val text = holder.b.blockEdit.text ?: Editable.Factory.getInstance().newEditable("")
                Block.Text(
                    text = text.toString(),
                    spans = SpanFormatter.read(text),
                    list = holder.list,
                    checked = holder.list == ListType.CHECK && holder.b.cbCheck.isChecked
                )
            }
            is TableHolder -> Block.Table(holder.cells.map { row -> row.map { it.text.toString() } })
            is StickerHolder -> Block.Sticker(holder.stickerId, holder.sizeDp)
        }
    })

    private fun buildText(block: Block.Text, number: Int): TextHolder {
        val b = RowTextBlockBinding.inflate(inflater, container, false)
        val holder = TextHolder(b, block.list)
        val edit = b.blockEdit
        edit.setText(block.text)
        edit.text?.let { SpanFormatter.write(it, block.spans, highlightColor) }
        applyListStyle(holder, block.list, number, block.checked)
        // The first line's height can change (text size, wrapping), so keep the checkbox centred on it.
        edit.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (holder.list == ListType.CHECK) alignCheckbox(holder)
        }

        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                if (building) return
                if (s.indexOf('\n') >= 0) scheduleSplit(holder) else callbacks.onContentChanged(false)
            }
        })
        edit.onSelectionChange = { start, end ->
            if (!building) {
                lastFocusedIndex = holders.indexOf(holder).coerceAtLeast(0)
                lastSelStart = start
                lastSelEnd = end
                callbacks.onFormatStateChanged()
            }
        }
        edit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && !building) {
                lastFocusedIndex = holders.indexOf(holder).coerceAtLeast(0)
                lastSelStart = edit.selectionStart
                lastSelEnd = edit.selectionEnd
                callbacks.onFormatStateChanged()
            }
        }
        edit.onBackspaceAtStart = { handleBackspace(holders.indexOf(holder)) }
        b.cbCheck.setOnCheckedChangeListener { _, checked ->
            if (!building) {
                updateStrike(holder, checked)
                callbacks.onContentChanged(true)
            }
        }
        return holder
    }

    private fun applyListStyle(h: TextHolder, list: ListType, number: Int, checked: Boolean) {
        h.list = list
        h.b.flMarker.show(list == ListType.CHECK)
        h.b.tvMarker.show(list == ListType.NUMBERED)
        h.b.vBullet.show(list == ListType.BULLET)
        h.b.tvMarker.text = "$number."
        if (list == ListType.BULLET) alignBullet(h)
        if (list == ListType.CHECK) alignCheckbox(h)
        h.b.cbCheck.show(list == ListType.CHECK)
        h.b.cbCheck.isChecked = list == ListType.CHECK && checked
        updateStrike(h, list == ListType.CHECK && checked)
    }

    /**
     * Puts the middle of the bullet on the middle of the first line's lower-case letters: the baseline
     * minus half the x-height, measured from the editor's own font so it holds for any font or size.
     */
    private fun alignBullet(h: TextHolder) {
        val paint = h.b.blockEdit.paint
        val xBounds = android.graphics.Rect()
        paint.getTextBounds("x", 0, 1, xBounds)
        val dot = activity.dp(BULLET_DP)
        val centre = -paint.fontMetrics.ascent - xBounds.height() / 2f
        val lp = h.b.vBullet.layoutParams as LinearLayout.LayoutParams
        lp.width = dot
        lp.height = dot
        lp.topMargin = (centre - dot / 2f).toInt().coerceAtLeast(0)
        h.b.vBullet.layoutParams = lp
    }

    /**
     * Makes the checkbox column exactly as tall as the first line of text, so the box (centred in the column)
     * sits level with that line instead of drifting down. Only touches the layout when the height changes,
     * so the layout listener that calls this cannot loop.
     */
    private fun alignCheckbox(h: TextHolder) {
        val edit = h.b.blockEdit
        val layout = edit.layout
        val lineHeight = if (layout != null && layout.lineCount > 0) {
            layout.getLineBottom(0) - layout.getLineTop(0)
        } else {
            val fm = edit.paint.fontMetricsInt
            fm.descent - fm.ascent
        }
        val target = lineHeight.coerceAtLeast(activity.dp(CHECKBOX_DP))
        val lp = h.b.flMarker.layoutParams
        if (lp.height != target) {
            lp.height = target
            h.b.flMarker.layoutParams = lp
        }
    }

    private fun updateStrike(h: TextHolder, on: Boolean) {
        val edit = h.b.blockEdit
        edit.paintFlags = if (on) edit.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else edit.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        edit.alpha = if (on) 0.6f else 1f
    }

    private fun buildTable(block: Block.Table): TableHolder {
        val b = RowTableBlockBinding.inflate(inflater, container, false)
        val cells = block.cells.map { row ->
            val tableRow = TableRow(activity)
            val editRow = row.map { text ->
                val et = EditText(activity)
                et.setBackgroundResource(R.drawable.bg_table_cell)
                et.minWidth = activity.dp(104)
                et.minHeight = activity.dp(44)
                et.setPadding(activity.dp(8), activity.dp(8), activity.dp(8), activity.dp(8))
                et.setTextColor(activity.getColor(R.color.dearly_text))
                et.textSize = 15f
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                et.setText(text)
                et.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        if (!building) callbacks.onContentChanged(false)
                    }
                })
                tableRow.addView(et, TableRow.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                et
            }
            b.table.addView(tableRow)
            editRow
        }
        val holder = TableHolder(b, cells)
        val rows = cells.size
        val cols = cells.firstOrNull()?.size ?: 1
        b.btnAddRow.isEnabled = rows < BlockOps.MAX_TABLE_ROWS
        b.btnAddCol.isEnabled = cols < BlockOps.MAX_TABLE_COLS
        b.btnDelRow.isEnabled = rows > 1
        b.btnDelCol.isEnabled = cols > 1
        b.btnAddRow.setOnClickListener { modifyTable(holder) { c -> c + listOf(List(cols) { "" }) } }
        b.btnAddCol.setOnClickListener { modifyTable(holder) { c -> c.map { it + "" } } }
        b.btnDelRow.setOnClickListener { modifyTable(holder) { c -> c.dropLast(1) } }
        b.btnDelCol.setOnClickListener { modifyTable(holder) { c -> c.map { it.dropLast(1) } } }
        b.btnDelTable.setOnClickListener { removeBlock(holders.indexOf(holder)) }
        return holder
    }

    private fun buildSticker(block: Block.Sticker): StickerHolder {
        val b = RowStickerBlockBinding.inflate(inflater, container, false)
        val size = StickerSizes.clamp(block.size)
        val holder = StickerHolder(b, block.stickerId, size)
        val file = stickerFiles[block.stickerId]
        // Decode once at the largest size so the slider can grow the sticker without reloading it.
        StickerImages.bind(b.ivSticker, file, activity.dp(StickerSizes.MAX))
        if (file == null) b.ivSticker.contentDescription = activity.getString(R.string.sticker_unavailable)
        applyStickerSize(holder, size)

        // Tap the sticker to show / hide its size slider and remove button (one sticker's controls open at a time).
        b.ivSticker.setOnClickListener {
            val open = b.llStickerControls.visibility != View.VISIBLE
            holders.filterIsInstance<StickerHolder>().forEach { it.b.llStickerControls.show(false) }
            b.llStickerControls.show(open)
        }
        b.sliderSize.valueFrom = StickerSizes.MIN.toFloat()
        b.sliderSize.valueTo = StickerSizes.MAX.toFloat()
        b.sliderSize.stepSize = StickerSizes.STEP.toFloat()
        b.sliderSize.value = nearestStep(size)
        b.sliderSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !building) {
                applyStickerSize(holder, value.toInt())
                callbacks.onContentChanged(false) // keeps saving while dragging, also for keyboard/TalkBack changes
            }
        }
        b.sliderSize.addOnSliderTouchListener(object : com.google.android.material.slider.Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: com.google.android.material.slider.Slider) {}
            override fun onStopTrackingTouch(slider: com.google.android.material.slider.Slider) {
                callbacks.onContentChanged(true) // one undo step per resize
            }
        })
        // Removes the block only; the sticker stays in the library.
        b.btnRemoveSticker.setOnClickListener { removeBlock(holders.indexOf(holder)) }
        return holder
    }

    /** Longest side of the sticker in dp; the image keeps its proportions and stays left-aligned. */
    private fun applyStickerSize(holder: StickerHolder, sizeDp: Int) {
        holder.sizeDp = StickerSizes.clamp(sizeDp)
        val px = activity.dp(holder.sizeDp)
        holder.b.ivSticker.maxWidth = px
        holder.b.ivSticker.maxHeight = px
    }

    private fun nearestStep(size: Int): Float {
        val steps = (size - StickerSizes.MIN + StickerSizes.STEP / 2) / StickerSizes.STEP
        return (StickerSizes.MIN + steps * StickerSizes.STEP).coerceIn(StickerSizes.MIN, StickerSizes.MAX).toFloat()
    }

    // ------------------------------------------------------------------ focus

    fun focus(index: Int, pos: Int = 0) {
        when (val h = holders.getOrNull(index)) {
            is TextHolder -> {
                val edit = h.b.blockEdit
                edit.requestFocus()
                edit.setSelection(pos.coerceIn(0, edit.text?.length ?: 0))
                showKeyboard(edit)
            }
            is TableHolder -> h.cells.firstOrNull()?.firstOrNull()?.requestFocus()
            else -> Unit
        }
    }

    fun focusEnd() {
        val last = holders.indexOfLast { it is TextHolder }
        if (last >= 0) focus(last, Int.MAX_VALUE)
    }

    private fun showKeyboard(view: View) {
        view.post { activity.getSystemService(InputMethodManager::class.java).showSoftInput(view, InputMethodManager.SHOW_IMPLICIT) }
    }

    // ------------------------------------------------------------------ Enter / Backspace

    private fun scheduleSplit(holder: TextHolder) {
        if (splitScheduled) return
        splitScheduled = true
        container.post {
            splitScheduled = false
            val index = holders.indexOf(holder)
            if (index >= 0) splitBlock(index, holder)
        }
    }

    private fun splitBlock(index: Int, holder: TextHolder) {
        val doc = readDocument()
        val block = doc.blocks.getOrNull(index) as? Block.Text ?: return
        if (!block.text.contains('\n')) return
        val caret = holder.b.blockEdit.selectionStart.coerceAtLeast(0)
        val parts = BlockOps.splitAtNewlines(block)
        val (partIndex, offset) = locate(parts, caret)
        val blocks = doc.blocks.toMutableList()
        blocks.removeAt(index)
        blocks.addAll(index, parts)
        render(Document(blocks), index + partIndex, offset)
        callbacks.onContentChanged(true)
    }

    private fun locate(parts: List<Block.Text>, caret: Int): Pair<Int, Int> {
        var p = caret
        for (i in parts.indices) {
            if (p <= parts[i].text.length) return i to p
            p -= parts[i].text.length + 1
        }
        return parts.lastIndex to parts.last().text.length
    }

    private fun handleBackspace(index: Int) {
        if (index < 0) return
        val doc = readDocument()
        val current = doc.blocks.getOrNull(index) as? Block.Text ?: return
        val blocks = doc.blocks.toMutableList()
        if (current.list != ListType.NONE) { // first Backspace leaves the list
            blocks[index] = current.copy(list = ListType.NONE, checked = false)
            render(Document(blocks), index, 0)
            callbacks.onContentChanged(true)
            return
        }
        if (index == 0) return
        val previous = blocks[index - 1] as? Block.Text ?: return // tables/stickers are removed with their own button
        val merged = BlockOps.mergeWithPrevious(previous, current)
        blocks[index - 1] = merged
        blocks.removeAt(index)
        render(Document(blocks), index - 1, previous.text.length)
        callbacks.onContentChanged(true)
    }

    // ------------------------------------------------------------------ formatting

    private fun target(): TextHolder? =
        (holders.getOrNull(lastFocusedIndex) as? TextHolder) ?: holders.filterIsInstance<TextHolder>().firstOrNull()

    private fun selectionOf(h: TextHolder): Pair<Int, Int> {
        val e = h.b.blockEdit
        val len = e.text?.length ?: 0
        val s = if (e.hasFocus()) e.selectionStart else lastSelStart
        val t = if (e.hasFocus()) e.selectionEnd else lastSelEnd
        return minOf(s, t).coerceIn(0, len) to maxOf(s, t).coerceIn(0, len)
    }

    private fun restoreSelection(h: TextHolder, start: Int, end: Int) {
        val e = h.b.blockEdit
        if (!e.hasFocus()) e.requestFocus()
        e.setSelection(start, end)
    }

    fun toggleStyle(kind: SpanKind) {
        val h = target() ?: return
        val text = h.b.blockEdit.text ?: return
        val (s, e) = selectionOf(h)
        restoreSelection(h, s, e)
        val current = SpanFormatter.read(text)
        if (s != e) {
            SpanFormatter.write(text, SpanOps.toggle(current, kind, s, e, text.length), highlightColor)
        } else {
            val typing = SpanFormatter.typingState(text, s).first
            if (kind in typing) {
                SpanFormatter.write(text, splitAt(current, kind, s), highlightColor, noExtendAt = s)
            } else {
                SpanFormatter.write(text, current, highlightColor)
                SpanFormatter.addPending(text, SpanData(kind, s, s), highlightColor)
            }
        }
        finishFormat()
    }

    fun setSize(level: Int) {
        val h = target() ?: return
        val text = h.b.blockEdit.text ?: return
        val (s, e) = selectionOf(h)
        restoreSelection(h, s, e)
        val current = SpanFormatter.read(text)
        if (s != e) {
            SpanFormatter.write(text, SpanOps.setSize(current, s, e, level, text.length), highlightColor)
        } else {
            SpanFormatter.write(text, splitAt(current, SpanKind.SIZE, s), highlightColor, noExtendAt = s)
            if (level != TextSizes.DEFAULT_LEVEL) {
                SpanFormatter.addPending(text, SpanData(SpanKind.SIZE, s, s, level), highlightColor)
            }
        }
        finishFormat()
    }

    /** Splits spans of [kind] that straddle [pos] so that typing at [pos] can start fresh. */
    private fun splitAt(spans: List<SpanData>, kind: SpanKind, pos: Int): List<SpanData> =
        spans.flatMap { sp ->
            if (sp.kind == kind && sp.start < pos && sp.end > pos) listOf(sp.copy(end = pos), sp.copy(start = pos)) else listOf(sp)
        }

    private fun finishFormat() {
        callbacks.onContentChanged(true)
        callbacks.onFormatStateChanged()
    }

    fun formatState(): FormatState {
        val h = target() ?: return FormatState(false, false, false, false, TextSizes.DEFAULT_LEVEL)
        val text = h.b.blockEdit.text ?: return FormatState(false, false, false, false, TextSizes.DEFAULT_LEVEL)
        val (s, e) = selectionOf(h)
        if (s == e) {
            val (kinds, size) = SpanFormatter.typingState(text, s)
            return FormatState(
                SpanKind.BOLD in kinds, SpanKind.ITALIC in kinds, SpanKind.UNDERLINE in kinds,
                SpanKind.HIGHLIGHT in kinds, size
            )
        }
        val spans = SpanFormatter.read(text)
        return FormatState(
            SpanOps.isFullyCovered(spans, SpanKind.BOLD, s, e),
            SpanOps.isFullyCovered(spans, SpanKind.ITALIC, s, e),
            SpanOps.isFullyCovered(spans, SpanKind.UNDERLINE, s, e),
            SpanOps.isFullyCovered(spans, SpanKind.HIGHLIGHT, s, e),
            SpanOps.sizeLevel(spans, s, e)
        )
    }

    // ------------------------------------------------------------------ lists, tables, stickers

    fun toggleList(type: ListType) {
        val h = target() ?: return
        val index = holders.indexOf(h)
        val doc = readDocument()
        val block = doc.blocks.getOrNull(index) as? Block.Text ?: return
        val caret = h.b.blockEdit.selectionStart.coerceAtLeast(0)
        val blocks = doc.blocks.toMutableList()
        blocks[index] = block.copy(list = if (block.list == type) ListType.NONE else type, checked = false)
        render(Document(blocks), index, caret)
        callbacks.onContentChanged(true)
    }

    fun insertTable() = insertBlock(BlockOps.emptyTable(), focusBlock = true)

    fun insertSticker(stickerId: Long) = insertBlock(Block.Sticker(stickerId), focusBlock = false)

    private fun insertBlock(block: Block, focusBlock: Boolean) {
        val doc = readDocument()
        val blocks = doc.blocks.toMutableList()
        val index = target()?.let { holders.indexOf(it) }?.takeIf { it >= 0 } ?: blocks.lastIndex
        val current = blocks.getOrNull(index)
        val insertAt = if (current is Block.Text && current.text.isEmpty() && current.list == ListType.NONE) {
            blocks.removeAt(index)
            index
        } else index + 1
        blocks.add(insertAt, block)
        render(Document(BlockOps.ensureTrailingText(blocks)), if (focusBlock) insertAt else insertAt + 1, 0)
        callbacks.onContentChanged(true)
    }

    private fun modifyTable(holder: TableHolder, change: (List<List<String>>) -> List<List<String>>) {
        val index = holders.indexOf(holder)
        val doc = readDocument()
        val table = doc.blocks.getOrNull(index) as? Block.Table ?: return
        val updated = change(table.cells)
        if (updated.isEmpty() || updated.first().isEmpty()) return
        val blocks = doc.blocks.toMutableList()
        blocks[index] = Block.Table(updated)
        render(Document(blocks), index, 0)
        callbacks.onContentChanged(true)
    }

    private fun removeBlock(index: Int) {
        if (index < 0) return
        val doc = readDocument()
        val blocks = doc.blocks.toMutableList()
        if (index >= blocks.size) return
        blocks.removeAt(index)
        render(Document(BlockOps.ensureTrailingText(blocks)), -1)
        callbacks.onContentChanged(true)
    }
}

private const val BULLET_DP = 8
private const val CHECKBOX_DP = 24
