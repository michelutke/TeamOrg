import UIKit
import StripePaymentSheet
import ComposeApp

enum StripeSetupBridge {
    private static var current: PaymentSheet?

    static func register() {
        CardSetupSheet_iosKt.setSetupPresenter { publishableKey, clientSecret, completion in
            DispatchQueue.main.async {
                STPAPIClient.shared.publishableKey = publishableKey

                var configuration = PaymentSheet.Configuration()
                configuration.merchantDisplayName = "Teamorg"
                configuration.returnURL = "teamorg://stripe-redirect"

                let paymentSheet = PaymentSheet(setupIntentClientSecret: clientSecret, configuration: configuration)
                current = paymentSheet

                guard let topViewController = topViewController() else {
                    completion("failed")
                    return
                }

                paymentSheet.present(from: topViewController) { result in
                    switch result {
                    case .completed:
                        completion("completed")
                    case .canceled:
                        completion("canceled")
                    case .failed:
                        completion("failed")
                    }
                    current = nil
                }
            }
        }
    }

    private static func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }

        guard let rootViewController = scene?.windows.first(where: { $0.isKeyWindow })?.rootViewController else {
            return nil
        }

        var topViewController = rootViewController
        while let presentedViewController = topViewController.presentedViewController {
            topViewController = presentedViewController
        }
        return topViewController
    }
}
