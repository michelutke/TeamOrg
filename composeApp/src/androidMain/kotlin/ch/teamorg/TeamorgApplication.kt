package ch.teamorg

import android.app.Application
import ch.teamorg.di.sharedModule
import ch.teamorg.di.uiModule
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TeamorgApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@TeamorgApplication)
            modules(sharedModule, uiModule)
        }

        // OneSignal init — AFTER Koin so DI is available
        OneSignal.Debug.logLevel = LogLevel.NONE
        OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val data = event.notification.additionalData
                DeepLinkHandler.openNotification(
                    entityType = data?.optString("entity_type"),
                    entityId = data?.optString("entity_id")
                )
            }
        })
    }
}
