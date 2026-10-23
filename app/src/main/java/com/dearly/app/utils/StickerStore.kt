package com.dearly.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Sticker image files live in app-private storage (filesDir/stickers). Imported images are decoded,
 * downscaled and re-encoded as PNG (keeps transparency, also rejects corrupt or non-image files).
 * Each sticker is stored once and referenced by id, never copied per note.
 */
object StickerStore {
    const val MAX_DIMENSION = 1024
    private const val MAX_BYTES = 25L * 1024 * 1024
    private val ALLOWED_TYPES = setOf("image/png", "image/jpeg", "image/webp")

    enum class Failure { UNREADABLE, NOT_AN_IMAGE, TOO_LARGE, STORAGE }

    sealed class ImportResult {
        data class Ok(val fileName: String, val width: Int, val height: Int, val suggestedName: String) : ImportResult()
        data class Failed(val reason: Failure) : ImportResult()
    }

    fun dir(context: Context): File = File(context.filesDir, "stickers").also { it.mkdirs() }

    fun file(context: Context, fileName: String): File = File(dir(context), File(fileName).name)

    fun delete(context: Context, fileName: String) {
        file(context, fileName).delete()
    }

    fun import(context: Context, uri: Uri): ImportResult {
        val resolver = context.contentResolver
        var out: File? = null
        try {
            val type = resolver.getType(uri)
            if (type == null || type !in ALLOWED_TYPES) return ImportResult.Failed(Failure.NOT_AN_IMAGE)
            var size = -1L
            var displayName = "Sticker"
            resolver.query(uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    if (!c.isNull(0)) size = c.getLong(0)
                    if (!c.isNull(1)) displayName = c.getString(1).substringBeforeLast('.').take(40).ifBlank { "Sticker" }
                }
            }
            if (size > MAX_BYTES) return ImportResult.Failed(Failure.TOO_LARGE)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val stream = resolver.openInputStream(uri) ?: return ImportResult.Failed(Failure.UNREADABLE)
            stream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return ImportResult.Failed(Failure.NOT_AN_IMAGE)

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return ImportResult.Failed(Failure.NOT_AN_IMAGE)

            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest > MAX_DIMENSION) {
                val scale = MAX_DIMENSION.toFloat() / longest
                val scaled = Bitmap.createScaledBitmap(
                    bitmap, maxOf(1, (bitmap.width * scale).toInt()), maxOf(1, (bitmap.height * scale).toInt()), true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }

            val name = UUID.randomUUID().toString() + ".png"
            val target = File(dir(context), name)
            out = target
            target.outputStream().use { os ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)) throw IOException("compress failed")
            }
            val result = ImportResult.Ok(name, bitmap.width, bitmap.height, displayName)
            bitmap.recycle()
            out = null
            return result
        } catch (e: OutOfMemoryError) {
            return ImportResult.Failed(Failure.TOO_LARGE)
        } catch (e: SecurityException) {
            return ImportResult.Failed(Failure.UNREADABLE)
        } catch (e: IOException) {
            return ImportResult.Failed(Failure.STORAGE)
        } catch (e: IllegalArgumentException) {
            return ImportResult.Failed(Failure.NOT_AN_IMAGE)
        } finally {
            out?.delete() // never leave a half-written file behind
        }
    }
}
