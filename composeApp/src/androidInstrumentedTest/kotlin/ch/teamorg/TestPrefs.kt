package ch.teamorg

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The app persists its JWT in SharedPreferences, so auth state survives between
 * instrumented tests in the same process. Every test that cares about the starting
 * screen must clear it BEFORE the activity launches.
 */
object TestPrefs {
    fun clear() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("teamorg_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
