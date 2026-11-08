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
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var studio = P2PStudioController()
    @State private var reloadToken = 0
    @State private var isLoading = false
    @State private var loadError: String?
    @State private var started = false
    @State private var showQuality = false
    @State private var alert: StudioAlert?
    // PT: URL congelado no momento de abrir/recarregar. Sem isto, mexer nos
    // PT: sliders de qualidade recarregaria a página e cortaria a transmissão.
    // EN: URL frozen when opening/reloading. Without it, moving the quality
    // EN: sliders would reload the page and cut the stream.
    @State private var activeURL: URL?

    /// PT: Avisos do estúdio. Um único alerta evita conflitos entre a
    /// PT: gravação guardada e o aviso de segundo plano.
    /// EN: Studio notices. A single alert avoids conflicts between the saved
    /// EN: recording message and the background notice.
    private enum StudioAlert: Equatable {
        case message(String)
        case background
    }

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
                                reloadStudio()
                            } label: {
                                Image(systemName: "arrow.clockwise")
                            }
                            .accessibilityLabel(store.t("Recarregar", "Reload"))
                            .accessibilityIdentifier("p2p.reload")
                            Button {
                                showQuality = true
                            } label: {
                                Image(systemName: "slider.horizontal.3")
                            }
                            .accessibilityLabel(store.t("Qualidade", "Quality"))
                            .accessibilityIdentifier("p2p.toolbar.quality")
                            Button {
                                if let url = activeURL ?? studioURL { UIApplication.shared.open(url) }
                            } label: {
                                Image(systemName: "safari")
                            }
                            .accessibilityLabel(store.t("Abrir no Safari", "Open in Safari"))
                            .accessibilityIdentifier("p2p.safari")
                            Button {
                                if let saved = stopStudio() {
                                    alert = .message(store.t(
                                        "Gravação guardada em:\n\(saved)",
                                        "Recording saved at:\n\(saved)"
                                    ))
                                }
                            } label: {
                                Image(systemName: "xmark.circle")
                            }
                            .accessibilityLabel(store.t("Encerrar estúdio", "Close studio"))
                            .accessibilityIdentifier("p2p.close")
                        }
                    }
            }
        }
        .sheet(isPresented: $showQuality) {
            P2PQualityView(onApply: { reloadStudio() })
                .environmentObject(store)
        }
        .alert(alertTitle, isPresented: Binding(get: { alert != nil }, set: { if !$0 { alert = nil } })) {
            Button("OK", role: .cancel) { alert = nil }
        } message: {
            Text(alertMessage)
        }
        .onChange(of: studio.recordPath) { path in
            guard let path else { return }
            alert = .message(store.t("Gravação guardada em:\n\(path)", "Recording saved at:\n\(path)"))
            studio.recordPath = nil
        }
        .onChange(of: studio.notice) { notice in
            guard let notice else { return }
            switch notice {
            case .torchUnavailable:
                alert = .message(store.t(
                    "A lanterna só está disponível na câmara traseira.",
                    "The torch is only available on the back camera."
                ))
            case .recordingUnavailable:
                alert = .message(store.t(
                    "A gravação P2P não está disponível neste dispositivo.",
                    "P2P recording is not available on this device."
                ))
            case .recordingFailed:
                alert = .message(store.t(
                    "Não foi possível guardar a gravação P2P.",
                    "Could not save the P2P recording."
                ))
            }
            studio.notice = nil
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                AudioSessionManager.activate()
            case .background:
                if started {
                    if let saved = stopStudio() {
                        alert = .message(store.t(
                            "O estúdio foi encerrado em segundo plano.\nGravação guardada em:\n\(saved)",
                            "The studio was stopped in the background.\nRecording saved at:\n\(saved)"
                        ))
                    } else {
                        alert = .background
                    }
                }
            default:
                break
            }
        }
        .onAppear { AudioSessionManager.activate() }
        .onDisappear { _ = stopStudio() }
    }

    /// PT: Aplica a qualidade atual e recarrega a página deliberadamente.
    /// EN: Applies the current quality and reloads the page on purpose.
    private func reloadStudio() {
        activeURL = studioURL
        reloadToken += 1
    }

    /// PT: Encerra o WebView e liberta a câmara para o modo Ao vivo.
    /// PT: Devolve o caminho da gravação finalizada, se existir.
    /// EN: Closes the WebView and releases the camera for Live mode.
    /// EN: Returns the path of the finalised recording, if any.
    @discardableResult
    private func stopStudio() -> String? {
        guard started else { return nil }
        studio.reset()
        let saved = studio.recordPath
        // PT: Evita o alerta duplicado: o chamador mostra o caminho.
        // EN: Avoids the duplicate alert: the caller displays the path.
        studio.recordPath = nil
        started = false
        return saved
    }

    private var alertTitle: String {
        switch alert {
        case .background:
            return store.t("Transmissão parada", "Stream stopped")
        default:
            return store.t("Estúdio P2P", "P2P studio")
        }
    }

    private var alertMessage: String {
        switch alert {
        case .message(let text):
            return text
        case .background:
            return store.t(
                "O iOS suspende a câmara quando a app vai para segundo plano. O estúdio P2P foi encerrado; volta a abri-lo quando estiveres em primeiro plano.",
                "iOS suspends the camera when the app goes to the background. The P2P studio was stopped; open it again while the app is in the foreground."
            )
        case nil:
            return ""
        }
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
                    Text(store.t(
                        "Durante o estúdio tens os mesmos controlos do Ao vivo: silenciar, trocar de câmara, lanterna, gravar e qualidade.",
                        "While the studio is open you get the same controls as Live: mute, switch camera, torch, record and quality."
                    ))
                    .font(.caption)
                    .foregroundStyle(Theme.mint)
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
                            .accessibilityIdentifier("p2p.streamId")
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
                        guard let url = studioURL else { return }
                        activeURL = url
                        started = true
                    }
                    .accessibilityIdentifier("p2p.open")
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

            if let url = activeURL {
                StudioWebView(
                    url: url,
                    reloadToken: reloadToken,
                    isLoading: $isLoading,
                    loadError: $loadError,
                    controller: studio
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
                    if studio.recording {
                        Label(store.t("REC", "REC"), systemImage: "record.circle")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.danger)
                            .accessibilityIdentifier("p2p.recBadge")
                    }
                    Spacer()
                    if loadError != nil {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundStyle(Theme.danger)
                    }
                    Button {
                        reloadStudio()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.body.weight(.semibold))
                    }
                    .accessibilityLabel(store.t("Recarregar", "Reload"))
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
                controlPanel
            }
        }
    }

    /// PT: Painel nativo sobre o WebView, com os controlos do modo Ao vivo.
    /// EN: Native panel over the WebView, with the Live mode controls.
    private var controlPanel: some View {
        VStack(spacing: 10) {
            micMeter

            HStack(spacing: 10) {
                control(studio.muted ? "mic.slash.fill" : "mic.fill", active: studio.muted,
                        label: studio.muted ? store.t("Ativar som", "Unmute") : store.t("Silenciar", "Mute"),
                        id: "p2p.mute") {
                    studio.toggleMute()
                }
                cameraControl
                control("flashlight.off.fill", active: studio.torchOn,
                        label: store.t("Lanterna", "Torch"),
                        id: "p2p.torch") {
                    studio.toggleTorch()
                }
                control(studio.recording ? "stop.circle.fill" : "record.circle", active: studio.recording,
                        label: store.t("Gravar", "Record"),
                        id: "p2p.record") {
                    studio.toggleRecording()
                }
                control("slider.horizontal.3", active: false,
                        label: store.t("Qualidade", "Quality"),
                        id: "p2p.quality") {
                    showQuality = true
                }
            }
        }
        .padding(12)
        .background(.ultraThinMaterial)
    }

    private var micMeter: some View {
        HStack(spacing: 8) {
            Image(systemName: studio.muted ? "mic.slash.fill" : "mic.fill")
                .font(.caption2)
                .foregroundStyle(studio.muted ? Theme.danger : Theme.muted)
            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Capsule().fill(Color.white.opacity(0.12))
                    Capsule()
                        .fill(studio.muted ? Theme.muted : Theme.mint)
                        .frame(width: max(2, geometry.size.width * CGFloat(studio.muted ? 0 : studio.micLevel)))
                }
            }
            .frame(height: 4)
            if studio.muted {
                Text(store.t("MUDO", "MUTED"))
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(Theme.danger)
            }
        }
    }

    /// PT: O botão da câmara mostra B (back/traseira) ou F (front/frontal),
    /// PT: porque o VDO.Ninja pode alternar entre várias lentes traseiras.
    /// EN: The camera button shows B (back) or F (front), because VDO.Ninja can
    /// EN: cycle through several rear lenses.
    private var cameraControl: some View {
        Button {
            studio.flipCamera()
        } label: {
            VStack(spacing: 0) {
                Image(systemName: "camera.rotate")
                    .font(.system(size: 10, weight: .semibold))
                Text(studio.cameraFacing.letter)
                    .font(.caption.weight(.bold))
            }
            .frame(maxWidth: .infinity)
            .frame(height: 40)
            .background(Color.white.opacity(0.10))
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .accessibilityLabel(store.t("Trocar câmara", "Switch camera"))
        .accessibilityValue(studio.cameraFacing.name)
        .accessibilityIdentifier("p2p.flip")
    }

    private func control(_ systemImage: String, active: Bool, label: String, id: String,
                         action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(active ? Theme.danger.opacity(0.85) : Color.white.opacity(0.10))
                .foregroundStyle(.white)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .accessibilityLabel(label)
        .accessibilityValue(active ? "on" : "off")
        .accessibilityIdentifier(id)
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
    let controller: P2PStudioController

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

        let userContent = configuration.userContentController
        userContent.addUserScript(WKUserScript(
            source: P2PStudioScript.bridge,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: true
        ))
        userContent.add(context.coordinator, name: "irlNative")

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.uiDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .black
        webView.scrollView.backgroundColor = .black
        webView.allowsBackForwardNavigationGestures = false
        webView.allowsLinkPreview = false

        controller.attach(webView)
        context.coordinator.lastReloadToken = reloadToken
        context.coordinator.lastRequestedURL = url
        webView.load(URLRequest(url: url))
        return webView
    }

    static func dismantleUIView(_ uiView: WKWebView, coordinator: Coordinator) {
        uiView.configuration.userContentController.removeScriptMessageHandler(forName: "irlNative")
        uiView.stopLoading()
        coordinator.parent.controller.detach()
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

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
        var parent: StudioWebView
        var lastReloadToken = -1
        var lastRequestedURL: URL?

        init(parent: StudioWebView) {
            self.parent = parent
        }

        /// PT: Recebe o nível do microfone, o estado da lanterna e os pedaços
        /// PT: da gravação vindos do script injetado no VDO.Ninja.
        /// EN: Receives the microphone level, torch state and recording chunks
        /// EN: from the script injected into VDO.Ninja.
        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard message.name == "irlNative" else { return }
            parent.controller.handle(message.body)
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

// MARK: - P2P quality

/// PT: Qualidade aplicada ao estúdio P2P. As alterações são enviadas ao
/// PT: VDO.Ninja no próximo carregamento da página.
/// EN: Quality applied to the P2P studio. Changes are sent to VDO.Ninja on the
/// EN: next page load.
struct P2PQualityView: View {
    @EnvironmentObject private var store: SettingsStore
    @Environment(\.dismiss) private var dismiss
    let onApply: () -> Void

    private var vdo: VdoSettings { store.settings.vdoNinja }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Card {
                        SectionTitle(text: store.t("Vídeo", "Video"))
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(store.t("Bitrate de vídeo", "Video bitrate")).font(.subheadline)
                                Spacer()
                                Text("\(vdo.videoBitrateKbps) kbps")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(Theme.mint)
                            }
                            Slider(value: intBinding(\.videoBitrateKbps), in: 500...8_000, step: 250)
                                .tint(Theme.mint)
                                .disabled(vdo.audioOnly)
                        }
                        ToggleRow(
                            title: store.t("Apenas áudio", "Audio only"),
                            subtitle: store.t("Publica sem enviar vídeo.", "Publishes without sending video."),
                            isOn: binding(\.audioOnly)
                        )
                        ToggleRow(
                            title: "H.264",
                            subtitle: store.t("Maior compatibilidade com o OBS e navegadores.",
                                             "Better compatibility with OBS and browsers."),
                            isOn: binding(\.forceH264)
                        )
                    }

                    Card {
                        SectionTitle(text: store.t("Áudio", "Audio"))
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(store.t("Bitrate de áudio", "Audio bitrate")).font(.subheadline)
                                Spacer()
                                Text("\(vdo.audioBitrateKbps) kbps")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(Theme.mint)
                            }
                            Slider(value: intBinding(\.audioBitrateKbps), in: 32...320, step: 16)
                                .tint(Theme.mint)
                        }
                        ToggleRow(
                            title: store.t("Estéreo", "Stereo"),
                            subtitle: store.t("Envia dois canais quando disponíveis.", "Sends two channels when available."),
                            isOn: binding(\.stereo)
                        )
                    }

                    Text(store.t(
                        "As alterações são aplicadas ao recarregar o estúdio. A câmara e o microfone são interrompidos durante alguns segundos.",
                        "Changes are applied when the studio reloads. Camera and microphone stop for a few seconds."
                    ))
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)

                    PrimaryButton(title: store.t("Aplicar e recarregar", "Apply and reload"), systemImage: "arrow.clockwise") {
                        onApply()
                        dismiss()
                    }
                    .accessibilityIdentifier("p2p.quality.apply")
                }
                .padding(16)
            }
            .pageBackground()
            .navigationTitle(store.t("Qualidade P2P", "P2P quality"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.t("Cancelar", "Cancel")) { dismiss() }
                        .accessibilityIdentifier("p2p.quality.cancel")
                }
            }
        }
    }

    private func binding<T>(_ keyPath: WritableKeyPath<VdoSettings, T>) -> Binding<T> {
        Binding(
            get: { store.settings.vdoNinja[keyPath: keyPath] },
            set: { value in store.updateVdo { $0[keyPath: keyPath] = value } }
        )
    }

    private func intBinding(_ keyPath: WritableKeyPath<VdoSettings, Int>) -> Binding<Double> {
        Binding(
            get: { Double(store.settings.vdoNinja[keyPath: keyPath]) },
            set: { value in store.updateVdo { $0[keyPath: keyPath] = Int(value) } }
        )
    }
}
