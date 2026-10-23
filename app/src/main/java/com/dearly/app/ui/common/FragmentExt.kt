package com.dearly.app.ui.common

import androidx.fragment.app.Fragment
import com.dearly.app.ui.BaseActivity

/** Runs [work] on the database thread; [onResult] runs on main only while the fragment's view still exists. */
fun <T> Fragment.background(work: () -> T, onResult: (T) -> Unit) {
    val host = activity as? BaseActivity ?: return
    host.background(work = work) { result ->
        if (isAdded && view != null) onResult(result)
    }
}
