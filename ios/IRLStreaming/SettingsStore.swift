import Combine
import Foundation

/// PT: Fonte de verdade das preferências, persistida em UserDefaults.
/// EN: Single source of truth for settings, persisted in UserDefaults.
@MainActor
final class SettingsStore: ObservableObject {
    @Published var settings: AppSettings

    private let storageKey = "irl_streaming_settings_v1"

    init() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode(AppSettings.self, from: data) {
            settings = decoded
        } else {
            settings = AppSettings()
        }
    }

    /// PT: Devolve o texto no idioma selecionado. / EN: Returns text in the selected language.
    func t(_ pt: String, _ en: String) -> String {
        settings.language == .en ? en : pt
    }

    func update(_ transform: (inout AppSettings) -> Void) {
        var copy = settings
        transform(&copy)
        settings = copy
        save()
    }

    func updateVdo(_ transform: (inout VdoSettings) -> Void) {
        update { $0.vdoNinja.apply(transform) }
    }

    func updateChat(_ transform: (inout ChatSettings) -> Void) {
        update { $0.chat.apply(transform) }
    }

    /// PT: Repõe todas as preferências. / EN: Resets all preferences.
    func reset() {
        settings = AppSettings()
        save()
    }

    private func save() {
        guard let data = try? JSONEncoder().encode(settings) else { return }
        UserDefaults.standard.set(data, forKey: storageKey)
    }
}

private extension VdoSettings {
    mutating func apply(_ transform: (inout VdoSettings) -> Void) {
        transform(&self)
    }
}

private extension ChatSettings {
    mutating func apply(_ transform: (inout ChatSettings) -> Void) {
        transform(&self)
    }
}
