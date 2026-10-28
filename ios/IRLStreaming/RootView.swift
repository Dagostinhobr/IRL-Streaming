import SwiftUI
import UIKit

/// PT: Navegação principal: ao vivo (RTMP), P2P, links, chat e ajustes.
/// EN: Main navigation: live (RTMP), P2P, links, chat and settings.
struct RootView: View {
    @EnvironmentObject private var store: SettingsStore
    @State private var selection: Tab = .live

    enum Tab {
        case live, studio, links, chat, settings
    }

    var body: some View {
        TabView(selection: $selection) {
            RtmpView()
                .tabItem { Label(store.t("Ao vivo", "Live"), systemImage: "dot.radiowaves.left.and.right") }
                .tag(Tab.live)

            StudioView()
                .tabItem { Label("P2P", systemImage: "video.fill") }
                .tag(Tab.studio)

            VdoLinksView()
                .tabItem { Label(store.t("Links", "Links"), systemImage: "link") }
                .tag(Tab.links)

            ChatView()
                .tabItem { Label(store.t("Chat", "Chat"), systemImage: "bubble.left.and.bubble.right.fill") }
                .tag(Tab.chat)

            SettingsView()
                .tabItem { Label(store.t("Ajustes", "Settings"), systemImage: "gearshape.fill") }
                .tag(Tab.settings)
        }
        .tint(Theme.mint)
        .onAppear { applyIdleTimer() }
        .onChange(of: store.settings.keepScreenOn) { _ in applyIdleTimer() }
    }

    private func applyIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled = store.settings.keepScreenOn
    }
}
