package com.dearly.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.notifications.NotificationHelper
import com.dearly.app.security.Session

/**
 * Base for every screen that shows diary content. If the session is locked (app was closed, or 30 minutes
 * passed without activity) it puts the lock screen on top, so no screen can be reached by skipping the flow.
 */
abstract class BaseActivity : AppCompatActivity() {

    private var notificationCallback: ((Boolean) -> Unit)? = null
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationCallback?.invoke(granted)
            notificationCallback = null
        }

    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleCheck = Runnable { checkInactivity() }

    override fun onStart() {
        super.onStart()
        if (Session.isTimedOut()) Session.lock()
        showLockIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        scheduleIdleCheck()
    }

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacks(idleCheck)
    }

    /** Every touch or key press counts as activity. */
    override fun onUserInteraction() {
        super.onUserInteraction()
        Session.touch()
    }

    private fun showLockIfNeeded() {
        if (!Session.unlocked && !Session.lockScreenShowing) {
            Session.lockScreenShowing = true
            startActivity(Intent(this, LockActivity::class.java))
        }
    }

    // The app can sit open and untouched: lock it when the 30 minutes run out.
    private fun scheduleIdleCheck() {
        idleHandler.removeCallbacks(idleCheck)
        if (Session.unlocked) idleHandler.postDelayed(idleCheck, Session.msUntilTimeout() + 1000L)
    }

    private fun checkInactivity() {
        if (Session.isTimedOut()) {
            Session.lock()
            showLockIfNeeded()
        } else {
            scheduleIdleCheck()
        }
    }

    /** Content should only be loaded or shown while this is true. */
    protected val isUnlocked: Boolean get() = Session.unlocked

    /** Runs [work] on the database thread and delivers the result on the main thread (only if still alive). */
    fun <T> background(
        onError: (Throwable) -> Unit = { toast(R.string.error_generic) },
        work: () -> T,
        onResult: (T) -> Unit = {}
    ) {
        ServiceLocator.io.execute {
            try {
                val result = work()
                ServiceLocator.runOnMain { if (!isDestroyed) onResult(result) }
            } catch (t: Throwable) {
                Log.e(javaClass.simpleName, "Background work failed", t)
                ServiceLocator.runOnMain { if (!isDestroyed) onError(t) }
            }
        }
    }

    fun toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    fun toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    fun hideKeyboard() {
        val view = currentFocus ?: return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
    }

    /** Asks for POST_NOTIFICATIONS on Android 13+. [onResult] gets true when notifications may be shown. */
    fun ensureNotificationPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < 33 || NotificationHelper.hasPermission(this)) {
            onResult(true)
            return
        }
        notificationCallback = onResult
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
