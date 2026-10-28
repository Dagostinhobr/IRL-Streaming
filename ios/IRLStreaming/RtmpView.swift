import HaishinKit
import SwiftUI
import UIKit

/// PT: Estúdio nativo com preview da câmara, controlos e publicação RTMP/RTMPS.
/// EN: Native studio with camera preview, controls and RTMP/RTMPS publishing.
struct RtmpView: View {
    @EnvironmentObject private var store: SettingsStore
    @EnvironmentObject private var chat: TwitchChatClient
    @EnvironmentObject private var engine: RtmpEngine
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL

    @State private var showTargets = false
    @State private var showQuality = false
    @State private var permissionDenied = false
    @State private var toast: String?
    @State private var zoom: Double = 1
    @State private var backgroundNotice = false
    @State private var permissionsReady = false

    private var native: NativeSettings { store.settings.native }
    private var target: RtmpTarget? { native.selectedTarget() }
    private var state: RtmpState { engine.state }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                RtmpPreviewView(engine: engine)
                    .ignoresSafeArea()

                if permissionDenied {
                    permissionCard
                } else {
                    overlay
                }
            }
            .navigationTitle(store.t("Ao vivo", "Live"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button {
                        showTargets = true
                    } label: {
                        Image(systemName: "server.rack")
                    }
                    .accessibilityLabel(store.t("Destinos", "Targets"))
                    Button {
                        showQuality = true
                    } label: {
                        Image(systemName: "slider.horizontal.3")
                    }
                    .accessibilityLabel(store.t("Qualidade", "Quality"))
                }
            }
            .sheet(isPresented: $showTargets) {
                RtmpTargetsView()
                    .environmentObject(store)
            }
            .sheet(isPresented: $showQuality) {
                RtmpQualityView()
                    .environmentObject(store)
            }
            .alert("", isPresented: Binding(get: { toast != nil }, set: { if !$0 { toast = nil } })) {
                Button("OK", role: .cancel) { toast = nil }
            } message: {
                Text(toast ?? "")
            }
            .alert(store.t("Transmissão parada", "Stream stopped"), isPresented: $backgroundNotice) {
                Button("OK", role: .cancel) { backgroundNotice = false }
            } message: {
                Text(store.t(
                    "O iOS suspende a câmara quando a app vai para segundo plano. A transmissão foi encerrada; volta a iniciar quando estiveres em primeiro plano.",
                    "iOS suspends the camera when the app goes to the background. The stream was stopped; start it again while the app is in the foreground."
                ))
            }
        }
        .task {
            engine.language = store.settings.language
            let granted = await engine.requestPermissions()
            permissionDenied = !granted
            permissionsReady = true
            if granted {
                engine.activate(settings: native)
            }
        }
        .onChange(of: store.settings.native) { settings in
            engine.language = store.settings.language
            engine.applySettings(settings)
        }
        .onChange(of: store.settings.language) { language in
            engine.language = language
        }
        .onChange(of: engine.state.frontCamera) { front in
            store.updateNative { $0.cameraOnFront = front }
        }
        .onChange(of: engine.state.recordPath) { path in
            guard let path else { return }
            toast = store.t("Gravação guardada em:\n\(path)", "Recording saved at:\n\(path)")
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .active:
                if permissionsReady && !permissionDenied {
                    engine.activate(settings: native)
                }
            case .background:
                let wasStreaming = engine.state.status.isLive
                    || engine.state.status.isBusy
                    || engine.state.recording
                engine.suspend()
                if wasStreaming {
                    backgroundNotice = true
                }
            default:
                break
            }
        }
        .onAppear {
            if permissionsReady && !permissionDenied {
                engine.activate(settings: native)
            }
        }
        .onDisappear {
            // PT: Ao trocar de aba a câmara é libertada para o estúdio P2P.
            // EN: When switching tabs the camera is released for the P2P studio.
            engine.suspend()
        }
    }

    // MARK: - Overlay

    private var overlay: some View {
        VStack(spacing: 0) {
            topBar
            Spacer()
            chatOverlay
            bottomPanel
        }
    }

    private var topBar: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 7) {
                    statusDot
                    Text(statusText)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(statusColor)
                    if state.recording {
                        Label(store.t("REC", "REC"), systemImage: "record.circle")
                            .font(.caption2.weight(.bold))
                            .foregroundStyle(Theme.danger)
                    }
                }
                Text(target?.name ?? store.t("Sem destino", "No target"))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.white)
                Text(target?.displayEndpoint ?? store.t("Configura um destino RTMP.", "Configure an RTMP target."))
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
                    .lineLimit(1)
            }
            .padding(10)
            .background(.ultraThinMaterial)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            Spacer()

            if state.status.isLive || state.status.isBusy {
                statsCard
            }
        }
        .padding(.horizontal, 12)
        .padding(.top, 6)
    }

    private var statusDot: some View {
        Circle()
            .fill(statusColor)
            .frame(width: 8, height: 8)
    }

    private var statusText: String {
        switch state.status {
        case .idle: return store.t("Inativo", "Idle")
        case .ready: return store.t("Pronto", "Ready")
        case .connecting: return store.t("A ligar…", "Connecting…")
        case .live: return store.t("NO AR", "LIVE")
        case .reconnecting:
            return store.t("A reconectar (\(state.reconnectAttempt)/5)…",
                           "Reconnecting (\(state.reconnectAttempt)/5)…")
        case .error: return store.t("Erro", "Error")
        }
    }

    private var statusColor: Color {
        switch state.status {
        case .live: return Theme.danger
        case .connecting, .reconnecting: return .orange
        case .error: return Theme.danger
        case .ready, .idle: return Theme.mint
        }
    }

    private var statsCard: some View {
        VStack(alignment: .trailing, spacing: 3) {
            Text(formatBitrate(state.bitrateBps))
                .font(.caption.monospacedDigit().weight(.semibold))
                .foregroundStyle(.white)
            Text("\(state.fps) FPS · \(state.resolutionLabel)")
                .font(.caption2.monospacedDigit())
                .foregroundStyle(Theme.muted)
            TimelineView(.periodic(from: .now, by: 1)) { context in
                Text(durationText(at: context.date))
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(Theme.mint)
            }
        }
        .padding(10)
        .background(.ultraThinMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    @ViewBuilder
    private var chatOverlay: some View {
        if store.settings.chat.showOverlay && chat.status == .connected {
            let recent = chat.messages.filter { !$0.isSystem }.suffix(3)
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
                .padding(.bottom, 8)
                .allowsHitTesting(false)
            }
        }
    }

    private var bottomPanel: some View {
        VStack(spacing: 10) {
            if let error = state.error, state.status == .error {
                Text(error)
                    .font(.caption2)
                    .foregroundStyle(Theme.danger)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(8)
                    .background(Color.black.opacity(0.65))
                    .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            }

            micMeter

            HStack(spacing: 10) {
                control("mic.slash.fill", active: state.muted,
                        label: state.muted ? store.t("Ativar som", "Unmute") : store.t("Silenciar", "Mute")) {
                    engine.toggleMute()
                }
                control("camera.rotate.fill", active: false,
                        label: store.t("Trocar câmara", "Switch camera")) {
                    engine.toggleCamera()
                }
                control("flashlight.off.fill", active: state.torchOn,
                        label: store.t("Lanterna", "Torch")) {
                    engine.toggleTorch()
                }
                control(state.recording ? "stop.circle.fill" : "record.circle", active: state.recording,
                        label: store.t("Gravar", "Record")) {
                    if state.status.isLive || state.recording {
                        engine.toggleRecording()
                    } else {
                        toast = store.t(
                            "Inicia a transmissão antes de gravar.",
                            "Start the stream before recording."
                        )
                    }
                }
                control("slider.horizontal.3", active: false,
                        label: store.t("Qualidade", "Quality")) {
                    showQuality = true
                }
            }

            if state.status.isLive || state.status == .ready {
                HStack(spacing: 8) {
                    Image(systemName: "minus.magnifyingglass")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                    Slider(value: $zoom, in: 1...max(1, state.maxZoom))
                        .tint(Theme.mint)
                        .onChange(of: zoom) { value in
                            engine.setZoom(value)
                        }
                    Image(systemName: "plus.magnifyingglass")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                }
            }

            Button {
                if state.status.isLive || state.status.isBusy || state.status == .error {
                    engine.stop()
                    engine.restorePreview(settings: native)
                } else {
                    guard let target else {
                        showTargets = true
                        return
                    }
                    engine.start(target: target, settings: native)
                }
            } label: {
                HStack(spacing: 8) {
                    if state.isBusy {
                        ProgressView().tint(.black)
                    } else {
                        Image(systemName: state.status.isLive ? "stop.fill" : "dot.radiowaves.left.and.right")
                    }
                    Text(state.status.isLive
                         ? store.t("Parar transmissão", "Stop stream")
                         : state.isBusy
                            ? store.t("A ligar…", "Connecting…")
                            : store.t("Iniciar transmissão", "Start stream"))
                }
                .font(.subheadline.weight(.bold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(state.status.isLive ? Theme.danger : Theme.mint)
                .foregroundStyle(.black)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            }
            .disabled(state.isBusy)
        }
        .padding(12)
        .background(.ultraThinMaterial)
    }

    private var micMeter: some View {
        GeometryReader { geometry in
            ZStack(alignment: .leading) {
                Capsule().fill(Color.white.opacity(0.12))
                Capsule()
                    .fill(state.muted ? Theme.muted : Theme.mint)
                    .frame(width: max(2, geometry.size.width * CGFloat(state.micLevel)))
            }
        }
        .frame(height: 4)
    }

    private func control(_ systemImage: String, active: Bool, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .font(.body.weight(.semibold))
                .frame(maxWidth: .infinity)
                .frame(height: 40)
                .background(active ? Theme.danger.opacity(0.85) : Color.white.opacity(0.10))
                .foregroundStyle(active ? .white : .white)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .accessibilityLabel(label)
    }

    private var permissionCard: some View {
        VStack(spacing: 14) {
            Image(systemName: "video.slash.fill")
                .font(.largeTitle)
                .foregroundStyle(Theme.danger)
            Text(store.t("Câmara e microfone necessários", "Camera and microphone required"))
                .font(.headline)
            Text(store.t(
                "Autoriza o acesso em Ajustes para usar o encoder RTMP nativo.",
                "Allow access in Settings to use the native RTMP encoder."
            ))
            .font(.footnote)
            .foregroundStyle(Theme.muted)
            .multilineTextAlignment(.center)
            Button {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    openURL(url)
                }
            } label: {
                Text(store.t("Abrir Ajustes", "Open Settings"))
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 18)
                    .padding(.vertical, 10)
                    .background(Theme.mint)
                    .foregroundStyle(.black)
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
        }
        .padding(24)
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .padding(24)
    }

    private func durationText(at date: Date) -> String {
        guard let startedAt = state.startedAt else { return "00:00" }
        let seconds = max(0, Int(date.timeIntervalSince(startedAt)))
        return String(format: "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }

    private func formatBitrate(_ bps: Int) -> String {
        if bps >= 1_000_000 {
            return String(format: "%.1f Mbps", Double(bps) / 1_000_000)
        }
        return "\(max(0, bps / 1_000)) kbps"
    }
}

// MARK: - Quality

/// PT: Ajustes de qualidade aplicados ao encoder nativo.
/// EN: Quality settings applied to the native encoder.
struct RtmpQualityView: View {
    @EnvironmentObject private var store: SettingsStore
    @Environment(\.dismiss) private var dismiss

    private var native: NativeSettings { store.settings.native }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Card {
                        SectionTitle(text: store.t("Vídeo", "Video"))
                        Picker(store.t("Resolução", "Resolution"), selection: binding(\.resolution)) {
                            ForEach(NativeResolution.allCases) { resolution in
                                Text(resolution.label).tag(resolution)
                            }
                        }
                        .pickerStyle(.segmented)

                        HStack {
                            Text(store.t("FPS", "FPS")).font(.subheadline)
                            Spacer()
                            Picker("FPS", selection: binding(\.fps)) {
                                Text("15").tag(15)
                                Text("30").tag(30)
                                Text("60").tag(60)
                            }
                            .pickerStyle(.segmented)
                            .frame(width: 180)
                        }

                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(store.t("Bitrate de vídeo", "Video bitrate")).font(.subheadline)
                                Spacer()
                                Text("\(native.videoBitrateKbps) kbps")
                                    .font(.caption.monospacedDigit())
                                    .foregroundStyle(Theme.mint)
                            }
                            Slider(value: intBinding(\.videoBitrateKbps), in: 500...8_000, step: 250)
                                .tint(Theme.mint)
                        }

                        HStack {
                            Text(store.t("Codec", "Codec")).font(.subheadline)
                            Spacer()
                            Picker("Codec", selection: binding(\.codec)) {
                                ForEach(NativeCodec.allCases) { codec in
                                    Text(codec.label).tag(codec)
                                }
                            }
                            .pickerStyle(.menu)
                        }

                        HStack {
                            Text(store.t("Orientação", "Orientation")).font(.subheadline)
                            Spacer()
                            Picker("Orientation", selection: binding(\.orientation)) {
                                Text(store.t("Retrato", "Portrait")).tag(NativeOrientation.portrait)
                                Text(store.t("Paisagem", "Landscape")).tag(NativeOrientation.landscape)
                            }
                            .pickerStyle(.segmented)
                            .frame(width: 190)
                        }

                        Text(store.t(
                            "Alterações de resolução, orientação ou codec são aplicadas no próximo início do preview.",
                            "Resolution, orientation or codec changes are applied on the next preview start."
                        ))
                        .font(.caption2)
                        .foregroundStyle(Theme.muted)
                    }

                    Card {
                        SectionTitle(text: store.t("Áudio", "Audio"))
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(store.t("Bitrate de áudio", "Audio bitrate")).font(.subheadline)
                                Spacer()
                                Text("\(native.audioBitrateKbps) kbps")
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

                    Card {
                        SectionTitle(text: store.t("Ligação", "Connection"))
                        ToggleRow(
                            title: store.t("Reconectar automaticamente", "Reconnect automatically"),
                            subtitle: store.t("Até 5 tentativas com espera crescente.", "Up to 5 attempts with increasing delay."),
                            isOn: binding(\.autoReconnect)
                        )
                        Text(store.t(
                            "O RTMP nativo exige a app em primeiro plano. Ao bloquear o iPhone ou mudar de app, a transmissão é encerrada.",
                            "Native RTMP requires the app in the foreground. Locking the iPhone or switching apps stops the stream."
                        ))
                        .font(.caption2)
                        .foregroundStyle(Theme.muted)
                    }
                }
                .padding(16)
            }
            .pageBackground()
            .navigationTitle(store.t("Qualidade", "Quality"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(store.t("Concluir", "Done")) { dismiss() }
                }
            }
        }
    }

    private func binding<T>(_ keyPath: WritableKeyPath<NativeSettings, T>) -> Binding<T> {
        Binding(
            get: { store.settings.native[keyPath: keyPath] },
            set: { value in store.updateNative { $0[keyPath: keyPath] = value } }
        )
    }

    private func intBinding(_ keyPath: WritableKeyPath<NativeSettings, Int>) -> Binding<Double> {
        Binding(
            get: { Double(store.settings.native[keyPath: keyPath]) },
            set: { value in store.updateNative { $0[keyPath: keyPath] = Int(value) } }
        )
    }
}
