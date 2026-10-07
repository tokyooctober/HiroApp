import SwiftUI
import Shared

/// Hosts the shared Compose Multiplatform UI.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // The proxy URL comes from the app's Info.plist (build setting PROXY_BASE_URL). The app never holds an NLB key.
        let proxyBaseUrl = Bundle.main.object(forInfoDictionaryKey: "ProxyBaseURL") as? String ?? "http://localhost:8080"
        return MainViewControllerKt.MainViewController(proxyBaseUrl: proxyBaseUrl)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard)
    }
}
