import SwiftUI
import ComposeApp
import StripePaymentSheet
#if canImport(OneSignalFramework)
import OneSignalFramework
#endif

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .onOpenURL { url in
                if StripeAPI.handleURLCallback(with: url) {
                    return
                }
                MainViewControllerKt.handleDeepLink(url: url.absoluteString)
            }
    }
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        MainViewControllerKt.doInitKoin()
        StripeSetupBridge.register()
        PushBridgeSetup.register()

        // OneSignal init
        #if canImport(OneSignalFramework)
        OneSignal.Debug.setLogLevel(.LL_NONE)
        OneSignal.initialize("2281f6c6-e979-49e3-a16b-d7b7628b67ea", withLaunchOptions: launchOptions)
        OneSignal.Notifications.addClickListener(NotificationClickHandler.shared)
        #endif

        return true
    }
}

#if canImport(OneSignalFramework)
private let pushPermissionRequestedKey = "pushPermissionRequested"

private func requestPushPermissionOnce() {
    let defaults = UserDefaults.standard
    guard !defaults.bool(forKey: pushPermissionRequestedKey) else { return }
    defaults.set(true, forKey: pushPermissionRequestedKey)
    DispatchQueue.main.async {
        OneSignal.Notifications.requestPermission({ accepted in
            print("Push permission: \(accepted)")
        }, fallbackToSettings: false)
    }
}
#endif

enum PushBridgeSetup {
    static func register() {
        PushBridge_iosKt.setPushBridge(
            login: { userId in
                #if canImport(OneSignalFramework)
                OneSignal.login(userId)
                requestPushPermissionOnce()
                #endif
            },
            logout: {
                #if canImport(OneSignalFramework)
                OneSignal.logout()
                #endif
            }
        )
    }
}

#if canImport(OneSignalFramework)
final class NotificationClickHandler: NSObject, OSNotificationClickListener {
    static let shared = NotificationClickHandler()

    func onClick(event: OSNotificationClickEvent) {
        let data = event.notification.additionalData
        MainViewControllerKt.handleNotificationTap(
            entityType: data?["entity_type"] as? String,
            entityId: data?["entity_id"] as? String
        )
    }
}
#endif
