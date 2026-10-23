package com.dearly.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.dearly.app.data.ServiceLocator
import com.dearly.app.notifications.NotificationHelper
import com.dearly.app.security.Session

class DearlyApp : Application() {
    private var startedActivities = 0
    private var liveActivities = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        AppCompatDelegate.setDefaultNightMode(
            if (ServiceLocator.settings.darkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        NotificationHelper.ensureChannel(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) { liveActivities++ }

            override fun onActivityDestroyed(activity: Activity) {
                liveActivities--
                // Every screen is gone = the app was really closed (e.g. Back out of the last screen).
                // Re-check after a moment so screen rotation or Activity-to-Activity hand-offs don't count.
                if (liveActivities <= 0 && !activity.isChangingConfigurations) {
                    handler.postDelayed({ if (liveActivities <= 0) Session.lock() }, 1000)
                }
            }

            override fun onActivityStarted(activity: Activity) { startedActivities++ }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                // Leaving to Home or another app is only a pause: the 30-minute inactivity clock starts now.
                if (startedActivities <= 0 && !activity.isChangingConfigurations) Session.touch()
            }

            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
        // Expiry is decided by stored timestamps, so running it at every process start is always correct.
        ServiceLocator.io.execute { runCatching { ServiceLocator.trash.purgeExpired() } }
    }
}
