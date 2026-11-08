import Combine
import Foundation

/// PT: Fonte de verdade das preferências, persistida em UserDefaults.
/// EN: Single source of truth for settings, persisted in UserDefaults.
@MainActor
final class SettingsStore: ObservableObject {
    @Published var settings: AppSettings

    /// PT: Os testes de interface usam uma chave própria para não apagarem as
    /// PT: preferências reais do utilizador.
    /// EN: UI tests use their own key so they never erase the user's real
    /// EN: preferences.
    private var storageKey: String {
        isUITest ? "irl_streaming_settings_uitest" : "irl_streaming_settings_v1"
    }

    private var isUITest: Bool {
        ProcessInfo.processInfo.arguments.contains("-uiTest")
    }

    init() {
        let arguments = ProcessInfo.processInfo.arguments
        let uiTest = arguments.contains("-uiTest")
        let key = uiTest ? "irl_streaming_settings_uitest" : "irl_streaming_settings_v1"
        if uiTest && arguments.contains("-resetSettings") {
            UserDefaults.standard.removeObject(forKey: key)
        }
        if let data = UserDefaults.standard.data(forKey: key),
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

    func updateNative(_ transform: (inout NativeSettings) -> Void) {
        update { $0.native.apply(transform) }
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

private extension NativeSettings {
    mutating func apply(_ transform: (inout NativeSettings) -> Void) {
        transform(&self)
    }
}
