import Foundation

/// PT: Idioma da interface. / EN: Interface language.
enum AppLanguage: String, Codable, CaseIterable, Identifiable {
    case pt
    case en

    var id: String { rawValue }

    var nativeName: String {
        switch self {
        case .pt: return "Português"
        case .en: return "English"
        }
    }
}

/// PT: Preferências da integração VDO.Ninja. / EN: VDO.Ninja integration settings.
struct VdoSettings: Codable, Equatable {
    var host: String = "https://vdo.ninja"
    var streamId: String = ""
    var room: String = ""
    var label: String = ""
    var password: String = ""
    var autostart: Bool = true
    var cleanOutput: Bool = true
    var stereo: Bool = true
    var audioOnly: Bool = false
    var forceH264: Bool = true
    var videoBitrateKbps: Int = 2_500
    var audioBitrateKbps: Int = 128
}

/// PT: Preferências do chat da Twitch. / EN: Twitch chat settings.
struct ChatSettings: Codable, Equatable {
    var channel: String = ""
    var username: String = ""
    var oauthToken: String = ""
    var showOverlay: Bool = true
}

/// PT: Protocolo de ingest usado pelo encoder nativo.
/// EN: Ingest protocol used by the native encoder.
enum RtmpProtocol: String, Codable, CaseIterable, Identifiable {
    case rtmp
    case rtmps

    var id: String { rawValue }

    var label: String { rawValue.uppercased() }
}

/// PT: Resoluções disponíveis no encoder nativo (base horizontal, como no Android).
/// EN: Resolutions available in the native encoder (landscape base, as on Android).
enum NativeResolution: String, Codable, CaseIterable, Identifiable {
    case sd480
    case hd720
    case fullHd1080

    var id: String { rawValue }

    var width: Int {
        switch self {
        case .sd480: return 854
        case .hd720: return 1280
        case .fullHd1080: return 1920
        }
    }

    var height: Int {
        switch self {
        case .sd480: return 480
        case .hd720: return 720
        case .fullHd1080: return 1080
        }
    }

    var label: String {
        switch self {
        case .sd480: return "480p"
        case .hd720: return "720p"
        case .fullHd1080: return "1080p"
        }
    }
}

/// PT: Codec de vídeo suportado pelo RTMP nativo.
/// EN: Video codec supported by the native RTMP encoder.
enum NativeCodec: String, Codable, CaseIterable, Identifiable {
    case h264
    case hevc

    var id: String { rawValue }

    var label: String {
        switch self {
        case .h264: return "H.264"
        case .hevc: return "H.265 (HEVC)"
        }
    }
}

/// PT: Orientação do vídeo enviado para o servidor.
/// EN: Orientation of the video sent to the server.
enum NativeOrientation: String, Codable, CaseIterable, Identifiable {
    case portrait
    case landscape

    var id: String { rawValue }
}

/// PT: Destino RTMP/RTMPS. A URL guarda a aplicação e a stream key é enviada
/// PT: separadamente, tal como no Android.
/// EN: RTMP/RTMPS target. The URL holds the application and the stream key is
/// EN: sent separately, as on Android.
struct RtmpTarget: Codable, Equatable, Identifiable {
    var id: UUID = UUID()
    var name: String
    var url: String
    var streamKey: String = ""
    /// PT: Campo mantido para paridade com o Android; o envio de backup não é
    /// PT: implementado no encoder iOS.
    /// EN: Field kept for Android parity; backup publishing is not implemented
    /// EN: in the iOS encoder.
    var backupURL: String = ""

    var protocolKind: RtmpProtocol {
        url.lowercased().hasPrefix("rtmps") ? .rtmps : .rtmp
    }

    var badge: String { protocolKind.label }

    /// PT: URL final usada apenas para exibição. A chave nunca é mostrada.
    /// EN: Final URL used only for display. The key is never shown.
    var displayEndpoint: String {
        let base = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if base.isEmpty { return "" }
        return streamKey.isEmpty ? base : "\(base)/••••••••"
    }

    /// PT: URL de ligação (aplicação RTMP), sem a chave.
    /// EN: Connection URL (RTMP application), without the key.
    var connectionURL: String {
        let base = url.trimmingCharacters(in: .whitespacesAndNewlines)
        return base.hasSuffix("/") ? String(base.dropLast()) : base
    }

    /// PT: Nome publicado no servidor (stream key).
    /// EN: Name published on the server (stream key).
    var publishName: String {
        streamKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static let defaults: [RtmpTarget] = [
        RtmpTarget(name: "Twitch", url: "rtmp://live.twitch.tv/app"),
        RtmpTarget(name: "YouTube", url: "rtmp://a.rtmp.youtube.com/live2"),
        RtmpTarget(name: "Kick", url: "rtmps://fa723fc1b171.global-contribute.live-video.net:443/app")
    ]
}

/// PT: Preferências do encoder nativo RTMP/RTMPS.
/// EN: Native RTMP/RTMPS encoder settings.
struct NativeSettings: Codable, Equatable {
    var targets: [RtmpTarget] = RtmpTarget.defaults
    var selectedTargetId: UUID?
    var resolution: NativeResolution = .hd720
    var fps: Int = 30
    var videoBitrateKbps: Int = 3_500
    var codec: NativeCodec = .h264
    var orientation: NativeOrientation = .portrait
    var cameraOnFront: Bool = false
    var audioBitrateKbps: Int = 128
    var stereo: Bool = true
    var autoReconnect: Bool = true

    /// PT: Destino escolhido, com fallback para o primeiro da lista.
    /// EN: Selected target, falling back to the first one in the list.
    func selectedTarget() -> RtmpTarget? {
        targets.first { $0.id == selectedTargetId } ?? targets.first
    }

    /// PT: Garante um ID válido mesmo depois de importar preferências antigas.
    /// EN: Ensures a valid ID even after importing older settings.
    mutating func normalizeSelection() {
        if selectedTarget() == nil {
            selectedTargetId = targets.first?.id
        } else if selectedTargetId == nil {
            selectedTargetId = targets.first?.id
        }
    }
}

/// PT: Preferências gerais da app iOS. / EN: General iOS app settings.
struct AppSettings: Codable, Equatable {
    var language: AppLanguage = .pt
    var vdoNinja = VdoSettings()
    var native = NativeSettings()
    var chat = ChatSettings()
    var keepScreenOn: Bool = true
}

extension AppSettings {
    /// PT: Decodifica preferências antigas sem falhar quando falta um campo novo.
    /// EN: Decodes older settings without failing when a new field is missing.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        language = try container.decodeIfPresent(AppLanguage.self, forKey: .language) ?? .pt
        vdoNinja = try container.decodeIfPresent(VdoSettings.self, forKey: .vdoNinja) ?? VdoSettings()
        native = try container.decodeIfPresent(NativeSettings.self, forKey: .native) ?? NativeSettings()
        chat = try container.decodeIfPresent(ChatSettings.self, forKey: .chat) ?? ChatSettings()
        keepScreenOn = try container.decodeIfPresent(Bool.self, forKey: .keepScreenOn) ?? true
        native.normalizeSelection()
    }
}
