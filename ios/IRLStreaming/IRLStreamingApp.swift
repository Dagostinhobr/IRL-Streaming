import AVFoundation
import SwiftUI

@main
struct IRLStreamingApp: App {
    @StateObject private var store = SettingsStore()
    @StateObject private var chat = TwitchChatClient()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(chat)
                .preferredColorScheme(.dark)
                .environment(\.locale, Locale(identifier: store.settings.language == .en ? "en" : "pt-PT"))
        }
    }
}

/// PT: Ativa a sessão de áudio usada pelo WebRTC dentro do WKWebView.
/// EN: Activates the audio session used by WebRTC inside the WKWebView.
enum AudioSessionManager {
    static func activate() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .videoChat, options: [.defaultToSpeaker, .allowBluetooth])
        try? session.setActive(true)
    }

    static func deactivate() {
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
