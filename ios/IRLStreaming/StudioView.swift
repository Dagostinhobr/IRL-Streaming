import SwiftUI
import UIKit
import WebKit

/// PT: Estúdio VDO.Ninja embutido. A câmara e o microfone são pedidos pelo
/// PT: próprio WebView e as permissões são concedidas pelo coordenador.
/// EN: Embedded VDO.Ninja studio. Camera and microphone are requested by the
/// EN: WebView itself and permissions are granted by the coordinator.
struct StudioView: View {
    @EnvironmentObject private var store: SettingsStore
    @EnvironmentObject private var chat: TwitchChatClient
    @State private var reloadToken = 0
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var started = false

    private var vdo: VdoSettings { store.settings.vdoNinja }
    private var targetId: String { VdoNinja.pushId(vdo) }
    private var studioURL: URL? { URL(string: VdoNinja.studioURL(vdo)) }

    var body: some View {
        NavigationStack {
            if targetId.isEmpty || !started {
                setupView
                    .navigationTitle(store.t("Estúdio", "Studio"))
            } else {
                studioView
                    .navigationTitle(store.t("Estúdio", "Studio"))
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItemGroup(placement: .navigationBarTrailing) {
                            Button {
                                reloadToken += 1
                            } label: {
                                Image(systemName: "arrow.clockwise")
                            }
                            Button {
                                if let url = studioURL { UIApplication.shared.open(url) }
                            } label: {
                                Image(systemName: "safari")
                            }
                            Button {
                                started = false
                            } label: {
                                Image(systemName: "slider.horizontal.3")
                            }
                        }
                    }
            }
        }
        .onAppear { AudioSessionManager.activate() }
    }

    private var setupView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Card {
                    HStack(spacing: 10) {
                        Image(systemName: "dot.radiowaves.left.and.right")
                            .font(.title2)
                            .foregroundStyle(Theme.mint)
                        Text(store.t("Publicação P2P pela câmara do iPhone",
                                     "P2P publishing from the iPhone camera"))
                            .font(.headline)
                    }
                    Text(store.t(
                        "O estúdio abre o VDO.Ninja dentro do app e usa a câmara e o microfone do iPhone. Define um ID ou uma sala para começar.",
                        "The studio opens VDO.Ninja inside the app and uses the iPhone camera and microphone. Set an ID or a room to start."
                    ))
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                }

                Card {
                    SectionTitle(text: store.t("Rápido", "Quick start"))
                    LabeledField(
                        label: store.t("ID de ligação (opcional)", "Stream ID (optional)"),
                        hint: store.t("Ex.: meu-stream-7f3a", "e.g. my-stream-7f3a")
                    ) {
                        TextField("meu-stream-7f3a", text: vdoBinding(\.streamId))
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                    HStack(spacing: 8) {
                        SecondaryButton(title: store.t("Gerar ID", "Generate ID"), systemImage: "wand.and.stars") {
                            store.updateVdo { $0.streamId = VdoNinja.newRoom() }
                        }
                        SecondaryButton(title: store.t("Colar link", "Paste link"), systemImage: "doc.on.clipboard") {
                            pasteLink()
                        }
                    }
                    LabeledField(
                        label: store.t("Sala (opcional)", "Room (optional)"),
                        hint: store.t("Agrupa vários convidados.", "Groups several guests.")
                    ) {
                        TextField("irl-casa-9f2", text: vdoBinding(\.room))
                            .autocorrectionDisabled()
                            .textInputAutocapitalization(.never)
                    }
                    LabeledField(label: store.t("Senha (opcional)", "Password (optional)")) {
                        SecureField("••••••", text: vdoBinding(\.password))
                    }
                    PrimaryButton(title: store.t("Abrir estúdio", "Open studio"), systemImage: "play.fill", enabled: !targetId.isEmpty) {
                        started = true
                    }
                    if !targetId.isEmpty {
                        Text(store.t("Toque em Abrir estúdio para carregar o VDO.Ninja.",
                                     "Tap Open studio to load VDO.Ninja."))
                            .font(.caption2)
                            .foregroundStyle(Theme.muted)
                    }
                }

                Card {
                    Text(store.t("Dica", "Tip"))
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.mint)
                    Text(store.t(
                        "Para transmitir para a Twitch/YouTube, abre a publicação VDO.Ninja no OBS ou usa o link de visualização noutro dispositivo.",
                        "To stream to Twitch/YouTube, open the VDO.Ninja feed in OBS or use the view link on another device."
                    ))
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
        }
        .pageBackground()
    }

    private var studioView: some View {
        ZStack(alignment: .top) {
            Color.black.ignoresSafeArea()

            if let url = studioURL {
                StudioWebView(
                    url: url,
                    reloadToken: reloadToken,
                    isLoading: $isLoading,
                    loadError: $loadError
                )
                .ignoresSafeArea(edges: .bottom)
            }

            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Circle()
                        .fill(targetId.isEmpty ? Theme.muted : Theme.mint)
                        .frame(width: 8, height: 8)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(targetId)
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                        Text(isLoading
                             ? store.t("A carregar…", "Loading…")
                             : store.t("Estúdio P2P", "P2P studio"))
                            .font(.caption2)
                            .foregroundStyle(Theme.muted)
                    }
                    Spacer()
                    if loadError != nil {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(Theme.danger)
                    }
                    Button {
                        reloadToken += 1
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.body.weight(.semibold))
                    }
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(.ultraThinMaterial)

                if let loadError {
                    Text(loadError)
                        .font(.caption2)
                        .foregroundStyle(Theme.danger)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 6)
                        .background(.ultraThinMaterial)
                }
                Spacer()
                chatOverlay
            }
        }
    }

    /// PT: Últimas mensagens do chat sobre o vídeo (não intercetam toques).
    /// EN: Latest chat messages over the video (they do not intercept taps).
    @ViewBuilder
    private var chatOverlay: some View {
        if store.settings.chat.showOverlay && chat.status == .connected {
            let recent = chat.messages.filter { !$0.isSystem }.suffix(4)
            if !recent.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    ForEach(Array(recent)) { message in
                        HStack(alignment: .top, spacing: 5) {
                            Text(message.displayName + ":")
                                .fontWeight(.bold)
                                .foregroundStyle(message.colorHex.flatMap { Color(hex: $0) } ?? Theme.mint)
                            Text(message.text)
                                .foregroundStyle(.white)
                        }
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.black.opacity(0.55))
                        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 12)
                .padding(.bottom, 12)
                .allowsHitTesting(false)
            }
        }
    }

    private func vdoBinding(_ keyPath: WritableKeyPath<VdoSettings, String>) -> Binding<String> {
        Binding(
            get: { store.settings.vdoNinja[keyPath: keyPath] },
            set: { value in store.updateVdo { $0[keyPath: keyPath] = value } }
        )
    }

    private func pasteLink() {
        guard let raw = UIPasteboard.general.string,
              let parsed = VdoNinja.parseLink(raw) else { return }
        store.updateVdo {
            if !parsed.streamId.isEmpty { $0.streamId = parsed.streamId }
            if !parsed.room.isEmpty { $0.room = parsed.room }
            if !parsed.password.isEmpty { $0.password = parsed.password }
        }
    }
}

/// PT: Wrapper do WKWebView com permissões de câmara/microfone automáticas.
/// EN: WKWebView wrapper with automatic camera/microphone permissions.
struct StudioWebView: UIViewRepresentable {
    let url: URL
    let reloadToken: Int
    @Binding var isLoading: Bool
    @Binding var loadError: String?

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.allowsInlineMediaPlayback = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.defaultWebpagePreferences.preferredContentMode = .mobile
        configuration.websiteDataStore = .default()

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
        webView.allowsBackForwardNavigationGestures = false

        context.coordinator.lastReloadToken = reloadToken
        context.coordinator.lastRequestedURL = url
        webView.load(URLRequest(url: url))
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        context.coordinator.parent = self
        if context.coordinator.lastReloadToken != reloadToken {
            context.coordinator.lastReloadToken = reloadToken
            context.coordinator.lastRequestedURL = url
            webView.load(URLRequest(url: url))
            return
        }
        // PT: Compara com o último URL pedido, não com o URL atual do WebView:
        // PT: redirecionamentos internos do VDO.Ninja não devem recarregar a página.
        // EN: Compare with the last requested URL, not the WebView's current URL:
        // EN: VDO.Ninja internal redirects must not reload the page.
        if context.coordinator.lastRequestedURL != url {
            context.coordinator.lastRequestedURL = url
            webView.load(URLRequest(url: url))
        }
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: StudioWebView
        var lastReloadToken = -1
        var lastRequestedURL: URL?

        init(parent: StudioWebView) {
            self.parent = parent
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            parent.isLoading = true
            parent.loadError = nil
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            parent.isLoading = false
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            parent.isLoading = false
            parent.loadError = error.localizedDescription
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            // PT: -999 é um cancelamento normal durante recargas.
            // EN: -999 is a normal cancellation during reloads.
            if (error as NSError).code == NSURLErrorCancelled { return }
            parent.isLoading = false
            parent.loadError = error.localizedDescription
        }

        /// PT: O iOS pede autorização ao delegate; o app concede para o
        /// PT: VDO.Ninja poder publicar.
        /// EN: iOS asks the delegate for authorization; the app grants it so
        /// EN: VDO.Ninja can publish.
        func webView(
            _ webView: WKWebView,
            requestMediaCapturePermissionFor origin: WKSecurityOrigin,
            initiatedByFrame frame: WKFrameInfo,
            type: WKMediaCaptureType,
            decisionHandler: @escaping (WKPermissionDecision) -> Void
        ) {
            decisionHandler(.grant)
        }

        /// PT: Mantém links `target=_blank` dentro do estúdio.
        /// EN: Keeps `target=_blank` links inside the studio.
        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
                webView.load(URLRequest(url: url))
            }
            return nil
        }

        func webView(
            _ webView: WKWebView,
            runJavaScriptAlertPanelWithMessage message: String,
            initiatedByFrame frame: WKFrameInfo,
            completionHandler: @escaping () -> Void
        ) {
            completionHandler()
        }
    }
}
