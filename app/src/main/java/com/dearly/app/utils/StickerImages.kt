package com.dearly.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.widget.ImageView
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import java.util.concurrent.Executors

/** Loads sticker thumbnails off the main thread with a small in-memory cache. */
object StickerImages {
    private val executor = Executors.newFixedThreadPool(2) { r -> Thread(r, "dearly-img") }
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtMost(24 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Shows the sticker, or a placeholder if the file is missing or unreadable. */
    fun bind(view: ImageView, fileName: String?, maxPx: Int) {
        val ctx = view.context.applicationContext
        view.tag = fileName
        if (fileName == null) {
            view.setImageResource(R.drawable.ic_broken_image)
            return
        }
        val key = "$fileName@$maxPx"
        val hit = cache.get(key)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        executor.execute {
            val bmp = decode(ctx, fileName, maxPx)
            if (bmp != null) cache.put(key, bmp)
            ServiceLocator.runOnMain {
                if (view.tag == fileName) {
                    if (bmp != null) view.setImageBitmap(bmp) else view.setImageResource(R.drawable.ic_broken_image)
                }
            }
        }
    }

    fun evict(fileName: String) {
        for (key in cache.snapshot().keys) if (key.startsWith("$fileName@")) cache.remove(key)
    }

    private fun decode(context: Context, fileName: String, maxPx: Int): Bitmap? {
        val file = StickerStore.file(context, fileName)
        if (!file.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
