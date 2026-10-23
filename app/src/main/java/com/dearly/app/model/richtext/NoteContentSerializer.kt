package com.dearly.app.model.richtext

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Persists a [Document] as explicit structured JSON (versioned) and reconstructs it. */
object NoteContentSerializer {
    private const val VERSION = 1

    fun toJson(doc: Document): String {
        val blocks = JSONArray()
        for (b in doc.blocks) {
            val obj = JSONObject()
            when (b) {
                is Block.Text -> {
                    obj.put("t", "text")
                    obj.put("x", b.text)
                    if (b.list != ListType.NONE) obj.put("l", b.list.code)
                    if (b.checked) obj.put("c", true)
                    // Canonical order keeps the JSON (and undo-history equality) stable across round trips.
                    val canonical = SpanOps.normalize(b.spans, b.text.length)
                    if (canonical.isNotEmpty()) {
                        val arr = JSONArray()
                        for (sp in canonical) {
                            val s = JSONObject()
                            s.put("k", sp.kind.code)
                            s.put("a", sp.start)
                            s.put("e", sp.end)
                            if (sp.value != 0) s.put("v", sp.value)
                            arr.put(s)
                        }
                        obj.put("s", arr)
                    }
                }
                is Block.Table -> {
                    obj.put("t", "table")
                    val rows = JSONArray()
                    for (r in b.cells) {
                        val row = JSONArray()
                        for (c in r) row.put(c)
                        rows.put(row)
                    }
                    obj.put("rows", rows)
                }
                is Block.Sticker -> {
                    obj.put("t", "sticker")
                    obj.put("id", b.stickerId)
                    obj.put("sz", StickerSizes.clamp(b.size))
                }
            }
            blocks.put(obj)
        }
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("blocks", blocks)
        return root.toString()
    }

    /** Never throws: unreadable content falls back to [fallbackText] so a note can always be opened. */
    fun fromJson(json: String?, fallbackText: String = ""): Document {
        if (json.isNullOrBlank()) return Document.fromPlainText(fallbackText)
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("blocks") ?: return Document.fromPlainText(fallbackText)
            val blocks = ArrayList<Block>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                when (o.optString("t")) {
                    "text" -> blocks.add(parseText(o))
                    "table" -> parseTable(o)?.let { blocks.add(it) }
                    "sticker" -> if (o.has("id")) {
                        // Notes saved before sizes existed have no "sz": they get the small default.
                        blocks.add(Block.Sticker(o.optLong("id"), StickerSizes.clamp(o.optInt("sz", StickerSizes.DEFAULT))))
                    }
                }
            }
            Document(BlockOps.ensureTrailingText(blocks))
        } catch (e: JSONException) {
            Document.fromPlainText(fallbackText)
        }
    }

    private fun parseText(o: JSONObject): Block.Text {
        val text = o.optString("x", "")
        val spans = ArrayList<SpanData>()
        val arr = o.optJSONArray("s")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val kind = SpanKind.fromCode(s.optString("k")) ?: continue
                spans.add(SpanData(kind, s.optInt("a"), s.optInt("e"), s.optInt("v", 0)))
            }
        }
        return Block.Text(
            text = text,
            spans = SpanOps.normalize(spans, text.length),
            list = ListType.fromCode(o.optString("l", "none")),
            checked = o.optBoolean("c", false)
        )
    }

    private fun parseTable(o: JSONObject): Block.Table? {
        val rowsArr = o.optJSONArray("rows") ?: return null
        val rows = ArrayList<List<String>>()
        for (i in 0 until rowsArr.length()) {
            val r = rowsArr.optJSONArray(i) ?: continue
            rows.add(List(r.length()) { r.optString(it, "") })
        }
        if (rows.isEmpty()) return null
        val width = maxOf(1, rows.maxOf { it.size })
        return Block.Table(rows.map { row -> row + List(width - row.size) { "" } })
    }
}
