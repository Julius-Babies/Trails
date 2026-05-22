import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    let url: String

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        MainViewControllerKt.updateView(url: url)
    }
}

struct ContentView: View {
    let url: String

    var body: some View {
        ComposeView(url: url)
            .ignoresSafeArea()
    }
}
