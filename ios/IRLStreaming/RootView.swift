import SwiftUI
import UIKit

/// PT: Navegação principal: estúdio, links, chat e ajustes.
/// EN: Main navigation: studio, links, chat and settings.
struct RootView: View {
    @EnvironmentObject private var store: SettingsStore
    @State private var selection: Tab = .studio

    enum Tab {
        case studio, links, chat, settings
    }

    var body: some View {
        TabView(selection: $selection) {
            StudioView()
                .tabItem { Label(store.t("Estúdio", "Studio"), systemImage: "video.fill") }
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
