package com.dearly.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.dearly.app.R
import com.dearly.app.data.ServiceLocator
import com.dearly.app.databinding.ActivityMainBinding
import com.dearly.app.ui.home.HomeFragment
import com.dearly.app.ui.library.LibraryFragment
import com.dearly.app.ui.reminders.RemindersFragment
import com.dearly.app.ui.settings.SettingsFragment

/** Hosts the four primary destinations. Fragments are kept alive and shown/hidden so their state survives tab switches. */
class MainActivity : BaseActivity() {
    private lateinit var b: ActivityMainBinding
    private var currentTab = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectToSetupIfNeeded()) return
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        // The selected tab is a circle from bg_nav_item; Material's own indicator would add a second, clipped pill.
        b.bottomNav.isItemActiveIndicatorEnabled = false

        currentTab = savedInstanceState?.getInt(KEY_TAB, TAB_HOME) ?: TAB_HOME
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        for (tab in 0..3) {
            if (fm.findFragmentByTag(TAGS[tab]) == null) {
                val fragment = createFragment(tab)
                tx.add(R.id.container, fragment, TAGS[tab])
                if (tab != currentTab) tx.hide(fragment) // hidden tabs load nothing until shown
            }
        }
        tx.commitNow()

        b.bottomNav.setOnItemSelectedListener { item ->
            select(ITEMS.indexOf(item.itemId))
            true
        }
        select(currentTab, force = true)
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    private fun redirectToSetupIfNeeded(): Boolean {
        val needsOnboarding = !ServiceLocator.settings.isOnboarded
        val securityMissing = !needsOnboarding && !ServiceLocator.auth.isPinSet()
        if (!needsOnboarding && !securityMissing) return false
        startActivity(
            Intent(this, OnboardingActivity::class.java).putExtra(OnboardingActivity.EXTRA_REPAIR, securityMissing)
        )
        finish()
        return true
    }

    private fun createFragment(tab: Int): Fragment = when (tab) {
        TAB_HOME -> HomeFragment()
        TAB_LIBRARY -> LibraryFragment()
        TAB_REMINDERS -> RemindersFragment()
        else -> SettingsFragment()
    }

    private fun handleIntent(intent: Intent) {
        if (!intent.hasExtra(EXTRA_TAB)) return
        val tab = intent.getIntExtra(EXTRA_TAB, TAB_HOME).coerceIn(0, 3)
        select(tab)
        if (tab == TAB_LIBRARY) openLibrary(intent.getIntExtra(EXTRA_LIBRARY_PAGE, 0))
        intent.removeExtra(EXTRA_TAB)
    }

    fun select(tab: Int, force: Boolean = false) {
        if (tab !in 0..3 || (tab == currentTab && !force)) return
        currentTab = tab
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
        for (i in 0..3) {
            val f = fm.findFragmentByTag(TAGS[i]) ?: continue
            if (i == tab) tx.show(f) else tx.hide(f)
        }
        tx.commitNowAllowingStateLoss()
        if (b.bottomNav.selectedItemId != ITEMS[tab]) b.bottomNav.selectedItemId = ITEMS[tab]
    }

    /** page 0 = All Folders, 1 = All Notes */
    fun openLibrary(page: Int) {
        select(TAB_LIBRARY)
        (supportFragmentManager.findFragmentByTag(TAGS[TAB_LIBRARY]) as? LibraryFragment)?.showPage(page)
    }

    companion object {
        const val TAB_HOME = 0
        const val TAB_LIBRARY = 1
        const val TAB_REMINDERS = 2
        const val TAB_SETTINGS = 3
        const val EXTRA_TAB = "tab"
        const val EXTRA_LIBRARY_PAGE = "library_page"
        private const val KEY_TAB = "current_tab"
        private val TAGS = arrayOf("home", "library", "reminders", "settings")
        private val ITEMS = intArrayOf(R.id.nav_home, R.id.nav_library, R.id.nav_reminders, R.id.nav_settings)

        fun newIntent(context: Context, tab: Int, libraryPage: Int = 0): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .putExtra(EXTRA_LIBRARY_PAGE, libraryPage)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
