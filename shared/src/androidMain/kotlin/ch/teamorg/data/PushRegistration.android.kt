package ch.teamorg.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

private const val PREFS_NAME = "teamorg_prefs"
private const val TAG = "PushRegistration"
private const val KEY_PERMISSION_ASKED = "notification_permission_asked"

actual object PushRegistration : PushBridge {
    override fun login(userId: String) {
        OneSignal.login(userId)
        requestNotificationPermissionOnce()
    }

    override fun logout() {
        OneSignal.logout()
    }

    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val prefs = KoinPlatform.getKoin().get<Context>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PERMISSION_ASKED, false) || OneSignal.Notifications.permission) return
        CoroutineScope(Dispatchers.Main).launch {
            runCatching { OneSignal.Notifications.requestPermission(false) }
                .onSuccess { prefs.edit().putBoolean(KEY_PERMISSION_ASKED, true).apply() }
                .onFailure { Log.w(TAG, "Notification permission request failed", it) }
        }
    }
}
