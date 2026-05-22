import SwiftUI
import SafariServices
import ComposeApp

func getVisibleViewController(_ root: UIViewController?) -> UIViewController? {
    var current = root
    while let presented = current?.presentedViewController {
        current = presented
    }
    return current
}

@main
struct iOSApp: App {
    @State private var launchUrl: String = ""

    var body: some Scene {
        WindowGroup {
            ContentView(url: launchUrl)
                .onOpenURL { url in
                    print("[DeepLink] onOpenURL: \(url.absoluteString)")
                    let rootVC = UIApplication.shared.connectedScenes
                        .compactMap { $0 as? UIWindowScene }
                        .first?.keyWindow?.rootViewController
                    if let safariVC = getVisibleViewController(rootVC) as? SFSafariViewController {
                        safariVC.dismiss(animated: true)
                    }
                    launchUrl = url.absoluteString
                }
        }
    }
}
