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

/// PT: Preferências gerais da app iOS. / EN: General iOS app settings.
struct AppSettings: Codable, Equatable {
    var language: AppLanguage = .pt
    var vdoNinja = VdoSettings()
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
        chat = try container.decodeIfPresent(ChatSettings.self, forKey: .chat) ?? ChatSettings()
        keepScreenOn = try container.decodeIfPresent(Bool.self, forKey: .keepScreenOn) ?? true
    }
}
