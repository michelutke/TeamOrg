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

        // OneSignal init
        #if canImport(OneSignalFramework)
        OneSignal.Debug.setLogLevel(.LL_NONE)
        OneSignal.initialize("2281f6c6-e979-49e3-a16b-d7b7628b67ea", withLaunchOptions: launchOptions)
        OneSignal.Notifications.requestPermission({ accepted in
            print("Push permission: \(accepted)")
        }, fallbackToSettings: true)
        #endif

        return true
    }
}
