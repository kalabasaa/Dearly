package com.dearly.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dearly.app.database.DearlyDatabase
import com.dearly.app.repository.FolderRepository
import com.dearly.app.repository.NoteRepository
import com.dearly.app.repository.ReminderRepository
import com.dearly.app.repository.SettingsRepository
import com.dearly.app.repository.StickerRepository
import com.dearly.app.repository.TagRepository
import com.dearly.app.repository.TrashRepository
import com.dearly.app.security.AuthManager
import com.dearly.app.security.SecureStore
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Tiny manual dependency container. All database work runs on the single [io] thread, which also
 * guarantees that saves are applied in the order they were submitted.
 */
object ServiceLocator {
    private lateinit var app: Context
    private val mainHandler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        app = context.applicationContext
    }

    val context: Context get() = app

    val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "dearly-io") }

    val database by lazy { DearlyDatabase(app) }
    val settings by lazy { SettingsRepository(database) }
    val tags by lazy { TagRepository(database) }
    val folders by lazy { FolderRepository(database, tags) }
    val notes by lazy { NoteRepository(database, tags) }
    val trash by lazy { TrashRepository(database, notes, folders) }
    val stickers by lazy { StickerRepository(database) }
    val reminders by lazy { ReminderRepository(database) }
    val secureStore by lazy { SecureStore(app) }
    val auth by lazy { AuthManager(secureStore) }

    fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }
}
