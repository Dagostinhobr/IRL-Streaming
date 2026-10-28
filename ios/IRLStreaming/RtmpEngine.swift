import AVFoundation
import Foundation
import HaishinKit
import RTMPHaishinKit
import SwiftUI
import VideoToolbox

/// PT: Estado publicado pelo encoder nativo. / EN: State published by the native encoder.
enum RtmpStatus: Equatable {
    case idle
    case ready
    case connecting
    case live
    case reconnecting
    case error

    var isBusy: Bool { self == .connecting || self == .reconnecting }
    var isLive: Bool { self == .live }
}

struct RtmpState: Equatable {
    var status: RtmpStatus = .idle
    var error: String?
    var startedAt: Date?
    var bitrateBps: Int = 0
    var fps: Int = 0
    var micLevel: Float = 0
    var muted = false
    var torchOn = false
    var frontCamera = false
    var recording = false
    var recordPath: String?
    var resolutionLabel = ""
    var reconnectAttempt = 0
    var maxZoom: Double = 1

    var isBusy: Bool { status.isBusy }
}

/// PT: Encoder nativo RTMP/RTMPS baseado em HaishinKit.
/// EN: Native RTMP/RTMPS encoder based on HaishinKit.
///
/// PT: Captura com MediaMixer, codificação H.264/HEVC + AAC e transporte
/// PT: RTMP/RTMPS. Gere destinos, gravação local, mute, zoom, lanterna e
/// PT: estatísticas, mantendo o estúdio P2P existente intacto.
/// EN: Capture with MediaMixer, H.264/HEVC + AAC encoding and RTMP/RTMPS
/// EN: transport. Manages targets, local recording, mute, zoom, torch and
/// EN: statistics while keeping the existing P2P studio intact.
@MainActor
final class RtmpEngine: NSObject, ObservableObject {
    @Published private(set) var state = RtmpState()

    /// PT: Idioma usado nas mensagens do motor. / EN: Language used in engine messages.
    var language: AppLanguage = .pt

    private let mixer = MediaMixer()
    private let connection = RTMPConnection()
    /// PT: O stream é recriado em cada sessão/reconexão para evitar cabeçalhos
    /// PT: de codec obsoletos depois de uma queda de rede.
    /// EN: The stream is recreated for every session/reconnect to avoid stale
    /// EN: codec headers after a network drop.
    private var stream: RTMPStream?
    private let recorder = StreamRecorder()
    private var recordingStream: RTMPStream?
    private let meter = RtmpAudioMeter()
    private var previewView: MTHKView?

    private var pendingSettings = NativeSettings()
    private var preparedSettings: NativeSettings?
    private var pendingTarget: RtmpTarget?
    private var preparationTask: Task<Void, Never>?
    private var statsTask: Task<Void, Never>?
    private var statusTask: Task<Void, Never>?
    private var reconnectTask: Task<Void, Never>?
    private var teardownTask: Task<Void, Never>?
    /// PT: Tarefa que liberta a captura; `configure` espera por ela para não
    /// PT: desanexar uma câmara recém-ligada ao voltar de outra aba.
    /// EN: Task that releases capture; `configure` waits for it so it cannot
    /// EN: detach a freshly attached camera when returning from another tab.
    private var releaseTask: Task<Void, Never>?
    private var intentionalStop = false
    private var capturePrepared = false
    private var isPreparing = false
    private var isRecordingInternal = false

    override init() {
        super.init()
        meter.onLevel = { [weak self] level in
            Task { @MainActor in
                self?.state.micLevel = level
            }
        }
        statusTask = Task { [weak self] in
            guard let self else { return }
            let statuses = await self.connection.status
            for await status in statuses {
                await self.connectionEvent(status)
            }
        }
    }

    // MARK: - Permissions

    /// PT: Pede acesso à câmara e ao microfone. / EN: Requests camera and microphone access.
    func requestPermissions() async -> Bool {
        let video = await AVCaptureDevice.requestAccess(for: .video)
        let audio = await AVCaptureDevice.requestAccess(for: .audio)
        return video && audio
    }

    // MARK: - Capture and preview

    /// PT: Prepara a captura e mostra o preview (sem publicar).
    /// EN: Prepares capture and shows the preview (without publishing).
    func activate(settings: NativeSettings) {
        pendingSettings = settings
        guard !capturePrepared, preparationTask == nil, !isPreparing else {
            if capturePrepared {
                applySettings(settings)
            }
            return
        }
        isPreparing = true
        preparationTask = Task { [weak self] in
            guard let self else { return }
            defer {
                self.isPreparing = false
                self.preparationTask = nil
            }
            do {
                try await self.configure(settings)
            } catch {
                self.fail(error.localizedDescription)
            }
        }
    }

    /// PT: Reanexa o preview depois de parar uma transmissão.
    /// EN: Reattaches the preview after stopping a stream.
    func restorePreview(settings: NativeSettings) {
        pendingSettings = settings
        if !capturePrepared {
            activate(settings: settings)
        }
    }

    /// PT: Aplica definições alteradas no painel de qualidade.
    /// EN: Applies settings changed in the quality panel.
    func applySettings(_ settings: NativeSettings) {
        pendingSettings = settings
        guard capturePrepared else { return }
        Task { [weak self] in
            guard let self else { return }
            await self.applyCodecSettings(settings)
            if self.preparedSettings?.cameraOnFront != settings.cameraOnFront {
                await self.attachCamera(front: settings.cameraOnFront)
            }
            await self.applyMixerSettings(settings)
            self.preparedSettings = settings
        }
    }

    private func configure(_ settings: NativeSettings) async throws {
        await teardownTask?.value
        await releaseTask?.value
        AudioSessionManager.activateForRtmp()

        await applyMixerSettings(settings)
        try await mixer.attachVideo(AVCaptureDevice.default(
            .builtInWideAngleCamera, for: .video,
            position: settings.cameraOnFront ? .front : .back
        ), track: 0) { video in
            video.isVideoMirrored = settings.cameraOnFront
        }
        try await mixer.attachAudio(AVCaptureDevice.default(for: .audio), track: 0)
        await mixer.startRunning()

        if let previewView {
            await mixer.addOutput(previewView)
        }
        // PT: O medidor recebe PCM do mixer, para funcionar também em preview.
        // EN: The meter receives PCM from the mixer, so it also works in preview.
        await mixer.addOutput(meter)

        let video = makeVideoSettings(settings)
        state.resolutionLabel = "\(Int(video.videoSize.width))x\(Int(video.videoSize.height))"
        state.frontCamera = settings.cameraOnFront
        updateZoomRange(front: settings.cameraOnFront)
        capturePrepared = true
        preparedSettings = settings
        if state.status != .connecting && state.status != .reconnecting {
            state.status = .ready
        }
    }

    private func applyMixerSettings(_ settings: NativeSettings) async {
        await mixer.setSessionPreset(sessionPreset(for: settings.resolution))
        await mixer.setVideoOrientation(settings.orientation == .portrait ? .portrait : .landscapeRight)
        try? await mixer.setFrameRate(Double(settings.fps))
        let current = await mixer.audioMixerSettings
        var audio = AudioMixerSettings(
            sampleRate: 44_100,
            channels: settings.stereo ? 2 : 1,
            isMuted: state.muted,
            mainTrack: current.mainTrack,
            tracks: current.tracks
        )
        audio.maximumNumberOfChannels = 2
        await mixer.setAudioMixerSettings(audio)
    }

    private func applyCodecSettings(_ settings: NativeSettings) async {
        let video = makeVideoSettings(settings)
        state.resolutionLabel = "\(Int(video.videoSize.width))x\(Int(video.videoSize.height))"
        guard let stream else { return }
        try? await stream.setVideoSettings(video)
        try? await stream.setAudioSettings(makeAudioSettings(settings))
        await stream.setBitRateStrategy(RtmpStatsMonitor(
            maximumVideoBitRate: settings.videoBitrateKbps * 1000,
            maximumAudioBitRate: settings.audioBitrateKbps * 1000
        ) { [weak self] report in
            Task { @MainActor in
                self?.state.bitrateBps = max(0, report.currentBytesOutPerSecond * 8)
            }
        })
    }

    private func makeVideoSettings(_ settings: NativeSettings) -> VideoCodecSettings {
        let landscape = CGSize(width: settings.resolution.width, height: settings.resolution.height)
        let size = settings.orientation == .portrait
            ? CGSize(width: landscape.height, height: landscape.width)
            : landscape
        return VideoCodecSettings(
            videoSize: size,
            bitRate: max(250, settings.videoBitrateKbps) * 1000,
            profileLevel: settings.codec == .hevc
                ? (kVTProfileLevel_HEVC_Main_AutoLevel as String)
                : (kVTProfileLevel_H264_High_AutoLevel as String),
            scalingMode: .trim,
            bitRateMode: .average,
            maxKeyFrameIntervalDuration: 2,
            dataRateLimits: nil,
            expectedFrameRate: Double(settings.fps)
        )
    }

    private func makeAudioSettings(_ settings: NativeSettings) -> AudioCodecSettings {
        AudioCodecSettings(
            bitRate: max(32, settings.audioBitrateKbps) * 1000,
            downmix: true
        )
    }

    private func sessionPreset(for resolution: NativeResolution) -> AVCaptureSession.Preset {
        switch resolution {
        case .sd480: return .vga640x480
        case .hd720: return .hd1280x720
        case .fullHd1080: return .hd1920x1080
        }
    }

    private func attachCamera(front: Bool) async {
        let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: front ? .front : .back
        ) ?? AVCaptureDevice.default(for: .video)
        do {
            try await mixer.attachVideo(device, track: 0) { video in
                video.isVideoMirrored = front
            }
            state.frontCamera = front
            state.torchOn = false
            preparedSettings?.cameraOnFront = front
            pendingSettings.cameraOnFront = front
            if front {
                await mixer.setTorchEnabled(false)
            }
            updateZoomRange(front: front)
        } catch {
            fail(error.localizedDescription)
        }
    }

    private func updateZoomRange(front: Bool) {
        let device = AVCaptureDevice.default(
            .builtInWideAngleCamera,
            for: .video,
            position: front ? .front : .back
        )
        state.maxZoom = min(Double(device?.activeFormat.videoMaxZoomFactor ?? 1), 10)
    }

    // MARK: - Preview binding

    /// PT: Liga o preview Metal ao mixer. / EN: Binds the Metal preview to the mixer.
    ///
    /// PT: O output é sempre registado (idempotente) para evitar uma corrida
    /// PT: com `configure`, que pode ler `previewView` antes de este existir.
    /// EN: The output is always registered (idempotent) to avoid a race with
    /// EN: `configure`, which may read `previewView` before it exists.
    func attachPreview(_ view: MTHKView) {
        previewView = view
        Task { [weak self] in
            await self?.mixer.addOutput(view)
        }
    }

    func detachPreview(_ view: MTHKView) {
        if previewView === view {
            previewView = nil
        }
        Task { [weak self] in
            await self?.mixer.removeOutput(view)
        }
    }

    // MARK: - Streaming

    /// PT: Liga ao destino e começa a publicar. / EN: Connects to the target and starts publishing.
    func start(target: RtmpTarget, settings: NativeSettings) {
        guard !state.status.isLive, !state.status.isBusy else { return }
        guard let url = URL(string: target.connectionURL),
              let scheme = url.scheme?.lowercased(),
              scheme == "rtmp" || scheme == "rtmps" else {
            fail(localized("URL inválida. Usa rtmp:// ou rtmps://.",
                           "Invalid URL. Use rtmp:// or rtmps://."))
            return
        }
        guard !target.publishName.isEmpty else {
            fail(localized("Define a stream key do destino.",
                           "Set the target stream key."))
            return
        }

        pendingTarget = target
        pendingSettings = settings
        intentionalStop = false
        state.status = .connecting
        state.error = nil
        state.startedAt = nil
        state.reconnectAttempt = 0
        state.bitrateBps = 0
        state.fps = 0

        Task { [weak self] in
            guard let self else { return }
            await self.teardownTask?.value
            if !self.capturePrepared {
                self.isPreparing = true
                do {
                    try await self.configure(settings)
                } catch {
                    self.isPreparing = false
                    self.fail(error.localizedDescription)
                    return
                }
                self.isPreparing = false
            }
            await self.connectAndPublish(target: target)
        }
    }

    /// PT: Cria um stream novo para a sessão atual. Recriar em cada ligação
    /// PT: evita reutilizar sequências de áudio/vídeo de uma ligação anterior.
    /// EN: Creates a fresh stream for the current session. Recreating on every
    /// EN: connection avoids reusing audio/video sequences from a previous one.
    private func prepareStream(_ settings: NativeSettings) async throws {
        if let old = stream {
            await mixer.removeOutput(old)
            stream = nil
        }
        let newStream = RTMPStream(connection: connection)
        try await newStream.setVideoSettings(makeVideoSettings(settings))
        try await newStream.setAudioSettings(makeAudioSettings(settings))
        await newStream.setBitRateStrategy(RtmpStatsMonitor(
            maximumVideoBitRate: settings.videoBitrateKbps * 1000,
            maximumAudioBitRate: settings.audioBitrateKbps * 1000
        ) { [weak self] report in
            Task { @MainActor in
                self?.state.bitrateBps = max(0, report.currentBytesOutPerSecond * 8)
            }
        })
        await mixer.addOutput(newStream)
        stream = newStream
    }

    private func connectAndPublish(target: RtmpTarget) async {
        do {
            try await prepareStream(pendingSettings)
            _ = try await connection.connect(target.connectionURL)
            guard let stream else { return }
            _ = try await stream.publish(target.publishName)
            state.status = .live
            state.startedAt = Date()
            state.reconnectAttempt = 0
            state.error = nil
            startStatsLoop()
        } catch {
            let wasReconnecting = state.status == .reconnecting
            if !intentionalStop, pendingSettings.autoReconnect,
               state.reconnectAttempt < 5, wasReconnecting {
                // PT: Uma tentativa de reconexão falhou; agenda a seguinte.
                // EN: A reconnect attempt failed; schedule the next one.
                await handleDisconnect(reason: error.localizedDescription)
            } else {
                await disconnectQuietly()
                fail(error.localizedDescription)
            }
        }
    }

    /// PT: Encerra a publicação e a gravação. / EN: Stops publishing and recording.
    func stop() {
        intentionalStop = true
        reconnectTask?.cancel()
        reconnectTask = nil
        statsTask?.cancel()
        statsTask = nil
        stopRecording()
        let current = stream
        stream = nil
        teardownTask = Task { [weak self] in
            guard let self else { return }
            if let current {
                await self.mixer.removeOutput(current)
                _ = try? await current.close()
            }
            try? await self.connection.close()
        }
        state.status = capturePrepared ? .ready : .idle
        state.startedAt = nil
        state.bitrateBps = 0
        state.fps = 0
        state.reconnectAttempt = 0
        state.micLevel = 0
        state.error = nil
        pendingTarget = nil
    }

    /// PT: Para tudo e liberta os recursos. / EN: Stops everything and releases resources.
    func shutdown() {
        suspend()
        preferenceTaskCleanup()
        capturePrepared = false
    }

    /// PT: Para a transmissão e liberta a câmara/microfone para outros ecrãs
    /// PT: (por exemplo, o estúdio P2P) ou quando a app vai para segundo plano.
    /// EN: Stops the stream and releases camera/microphone for other screens
    /// EN: (for example, the P2P studio) or when the app goes to the background.
    func suspend() {
        stop()
        releaseTask?.cancel()
        releaseTask = Task { [weak self] in
            guard let self else { return }
            await self.teardownTask?.value
            await self.mixer.stopRunning()
            try? await self.mixer.attachVideo(nil, track: 0)
            try? await self.mixer.attachAudio(nil, track: 0)
        }
        capturePrepared = false
        preparedSettings = nil
        state.status = .idle
        state.resolutionLabel = ""
        state.micLevel = 0
    }

    private func preferenceTaskCleanup() {
        statusTask?.cancel()
        statusTask = nil
        statsTask?.cancel()
        statsTask = nil
        reconnectTask?.cancel()
        reconnectTask = nil
    }

    private func disconnectQuietly() async {
        if let current = stream {
            stream = nil
            await mixer.removeOutput(current)
            _ = try? await current.close()
        }
        try? await connection.close()
    }

    private func connectionEvent(_ status: RTMPStatus) async {
        guard !intentionalStop else { return }
        switch status.code {
        case RTMPConnection.Code.connectClosed.rawValue,
             RTMPConnection.Code.connectFailed.rawValue,
             RTMPConnection.Code.connectRejected.rawValue,
             RTMPConnection.Code.connectIdleTimeOut.rawValue:
            guard state.status.isLive || state.status == .reconnecting else { return }
            await handleDisconnect(reason: status.description)
        default:
            break
        }
    }

    private func handleDisconnect(reason: String?) async {
        guard !intentionalStop else { return }
        stopRecording()
        guard pendingSettings.autoReconnect, state.reconnectAttempt < 5 else {
            await disconnectQuietly()
            fail(reason?.isEmpty == false ? reason! : localized(
                "A ligação ao servidor foi perdida.",
                "The connection to the server was lost."
            ))
            return
        }
        state.reconnectAttempt += 1
        state.status = .reconnecting
        state.error = reason
        state.bitrateBps = 0
        state.fps = 0
        let attempt = state.reconnectAttempt
        reconnectTask?.cancel()
        reconnectTask = Task { [weak self] in
            guard let self else { return }
            let delay = min(pow(2.0, Double(attempt - 1)) * 2, 16)
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard !Task.isCancelled, !self.intentionalStop,
                  let target = self.pendingTarget else { return }
            await self.disconnectQuietly()
            await self.connectAndPublish(target: target)
        }
    }

    private func startStatsLoop() {
        statsTask?.cancel()
        statsTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard let self, !Task.isCancelled else { return }
                let fps = await self.stream?.currentFPS ?? 0
                await MainActor.run {
                    guard self.state.status.isLive else { return }
                    self.state.fps = Int(fps)
                }
            }
        }
    }

    private func fail(_ message: String) {
        state.status = .error
        state.error = message
        state.bitrateBps = 0
        state.fps = 0
    }

    private func localized(_ pt: String, _ en: String) -> String {
        language == .en ? en : pt
    }

    // MARK: - Controls

    func toggleMute() {
        state.muted.toggle()
        let muted = state.muted
        Task { [weak self] in
            guard let self else { return }
            var audio = await self.mixer.audioMixerSettings
            audio.isMuted = muted
            await self.mixer.setAudioMixerSettings(audio)
        }
    }

    func toggleCamera() {
        let front = !state.frontCamera
        preparedSettings?.cameraOnFront = front
        pendingSettings.cameraOnFront = front
        state.frontCamera = front
        Task { [weak self] in
            await self?.attachCamera(front: front)
        }
    }

    func toggleTorch() {
        guard !state.frontCamera else { return }
        state.torchOn.toggle()
        let enabled = state.torchOn
        Task { [weak self] in
            await self?.mixer.setTorchEnabled(enabled)
        }
    }

    func setZoom(_ value: Double) {
        Task { [weak self] in
            guard let self else { return }
            try? await self.mixer.configuration(video: 0) { video in
                guard let device = video.device else { return }
                try device.lockForConfiguration()
                defer { device.unlockForConfiguration() }
                let maxZoom = min(device.activeFormat.videoMaxZoomFactor, 10)
                device.videoZoomFactor = max(1, min(CGFloat(value), maxZoom))
            }
        }
    }

    // MARK: - Recording

    func toggleRecording() {
        if isRecordingInternal {
            stopRecording()
        } else {
            startRecording()
        }
    }

    func startRecording() {
        guard state.status.isLive, !isRecordingInternal, let current = stream else { return }
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let name = "streamlink_\(Self.fileTimestamp()).mp4"
        let url = directory.appendingPathComponent(name)
        state.recordPath = nil
        isRecordingInternal = true
        state.recording = true
        recordingStream = current
        Task { [weak self] in
            guard let self else { return }
            do {
                await current.addOutput(self.recorder)
                try await self.recorder.startRecording(url)
            } catch {
                self.isRecordingInternal = false
                self.state.recording = false
                self.recordingStream = nil
                await current.removeOutput(self.recorder)
                self.fail(error.localizedDescription)
            }
        }
    }

    func stopRecording() {
        guard isRecordingInternal else { return }
        isRecordingInternal = false
        state.recording = false
        let target = recordingStream
        recordingStream = nil
        Task { [weak self] in
            guard let self else { return }
            do {
                let url = try await self.recorder.stopRecording()
                if let target { await target.removeOutput(self.recorder) }
                self.state.recordPath = url.path
            } catch {
                if let target { await target.removeOutput(self.recorder) }
            }
        }
    }

    private static func fileTimestamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        return formatter.string(from: Date())
    }
}

// MARK: - Preview

/// PT: Preview Metal do mixer. / EN: Metal preview of the mixer.
struct RtmpPreviewView: UIViewRepresentable {
    let engine: RtmpEngine

    func makeCoordinator() -> Coordinator {
        Coordinator(engine: engine)
    }

    func makeUIView(context: Context) -> MTHKView {
        let view = MTHKView(frame: .zero)
        view.videoGravity = .resizeAspectFill
        engine.attachPreview(view)
        return view
    }

    func updateUIView(_ uiView: MTHKView, context: Context) {
        uiView.videoGravity = .resizeAspectFill
    }

    static func dismantleUIView(_ uiView: MTHKView, coordinator: Coordinator) {
        coordinator.engine.detachPreview(uiView)
    }

    final class Coordinator {
        let engine: RtmpEngine

        init(engine: RtmpEngine) {
            self.engine = engine
        }
    }
}

// MARK: - Statistics

/// PT: Estratégia que apenas reporta o tráfego de saída (bitrate fixo).
/// EN: Strategy that only reports outgoing traffic (fixed bitrate).
struct RtmpStatsMonitor: StreamBitRateStrategy {
    let mamimumVideoBitRate: Int
    let mamimumAudioBitRate: Int
    let onReport: @Sendable (NetworkMonitorReport) -> Void

    init(
        maximumVideoBitRate: Int,
        maximumAudioBitRate: Int,
        onReport: @escaping @Sendable (NetworkMonitorReport) -> Void
    ) {
        self.mamimumVideoBitRate = maximumVideoBitRate
        self.mamimumAudioBitRate = maximumAudioBitRate
        self.onReport = onReport
    }

    func adjustBitrate(_ event: NetworkMonitorEvent, stream: some StreamConvertible) async {
        switch event {
        case .status(let report), .publishInsufficientBWOccured(let report):
            onReport(report)
        case .reset:
            break
        }
    }
}

// MARK: - Audio meter

/// PT: Medidor de microfone alimentado pelo PCM do mixer.
/// EN: Microphone meter fed by the mixer PCM.
final class RtmpAudioMeter: MediaMixerOutput, @unchecked Sendable {
    var onLevel: ((Float) -> Void)?
    private var lastUpdate = Date.distantPast

    nonisolated var videoTrackId: UInt8? { UInt8.max }
    nonisolated var audioTrackId: UInt8? { UInt8.max }

    nonisolated func mixer(_ mixer: MediaMixer, didOutput sampleBuffer: CMSampleBuffer) {}

    nonisolated func mixer(_ mixer: MediaMixer, didOutput buffer: AVAudioPCMBuffer, when: AVAudioTime) {
        guard buffer.frameLength > 0 else { return }
        let now = Date()
        guard now.timeIntervalSince(lastUpdate) >= 0.05 else { return }
        lastUpdate = now

        var peak: Float = 0
        let channels = Int(buffer.format.channelCount)
        if let data = buffer.floatChannelData {
            for channel in 0..<channels {
                let samples = data[channel]
                for index in 0..<Int(buffer.frameLength) {
                    peak = max(peak, abs(samples[index]))
                }
            }
        } else if let data = buffer.int16ChannelData {
            for channel in 0..<channels {
                let samples = data[channel]
                for index in 0..<Int(buffer.frameLength) {
                    peak = max(peak, abs(Float(samples[index]) / 32_768))
                }
            }
        }
        onLevel?(min(1, peak))
    }

    nonisolated func selectTrack(_ id: UInt8?, mediaType: CMFormatDescription.MediaType) async {}
}
