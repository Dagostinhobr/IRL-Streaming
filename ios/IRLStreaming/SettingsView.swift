import SwiftUI

/// PT: Preferências gerais, idioma e informação da app.
/// EN: General preferences, language and app information.
struct SettingsView: View {
    @EnvironmentObject private var store: SettingsStore
    @State private var showReset = false

    private var version: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.8.0"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "1"
        return "\(short) (\(build))"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(store.t("Idioma", "Language"), selection: languageBinding) {
                        ForEach(AppLanguage.allCases) { language in
                            Text(language.nativeName).tag(language)
                        }
                    }
                    .pickerStyle(.segmented)
                } header: {
                    Text(store.t("Idioma", "Language"))
                } footer: {
                    Text(store.t(
                        "Muda imediatamente todo o texto da app.",
                        "Immediately changes all text in the app."
                    ))
                }

                Section {
                    Toggle(isOn: binding(\.keepScreenOn)) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(store.t("Manter o ecrã ligado", "Keep the screen on"))
                            Text(store.t("Recomendado durante transmissões.",
                                         "Recommended while streaming."))
                                .font(.caption2)
                                .foregroundStyle(Theme.muted)
                        }
                    }
                    .tint(Theme.mint)
                } header: {
                    Text(store.t("Geral", "General"))
                }

                Section {
                    Text(store.t(
                        "As definições, links e tokens ficam guardados apenas neste iPhone. A app não recolhe estatísticas nem envia dados para servidores próprios.",
                        "Settings, links and tokens are stored only on this iPhone. The app collects no analytics and sends no data to its own servers."
                    ))
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                    Text(store.t(
                        "O VDO.Ninja e a Twitch são serviços externos e têm as suas próprias políticas. Usa senhas e tokens fictícios em exemplos e testes públicos.",
                        "VDO.Ninja and Twitch are external services with their own policies. Use fictional passwords and tokens in public examples and tests."
                    ))
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                } header: {
                    Text(store.t("Privacidade", "Privacy"))
                }

                Section {
                    HStack {
                        Text(store.t("Versão", "Version"))
                        Spacer()
                        Text(version).foregroundStyle(Theme.muted)
                    }
                    if let url = URL(string: "https://github.com/Dagostinhobr/IRL-Streaming") {
                        Link(destination: url) {
                            HStack {
                                Text(store.t("Repositório", "Repository"))
                                Spacer()
                                Image(systemName: "arrow.up.right.square")
                                    .foregroundStyle(Theme.muted)
                            }
                        }
                    }
                    Link(destination: URL(string: "https://vdo.ninja")!) {
                        HStack {
                            Text("VDO.Ninja")
                            Spacer()
                            Image(systemName: "arrow.up.right.square")
                                .foregroundStyle(Theme.muted)
                        }
                    }
                } header: {
                    Text(store.t("Sobre", "About"))
                }

                Section {
                    Button(role: .destructive) {
                        showReset = true
                    } label: {
                        Text(store.t("Repor definições", "Reset settings"))
                    }
                }
            }
            .tint(Theme.mint)
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(store.t("Ajustes", "Settings"))
            .alert(store.t("Repor definições?", "Reset settings?"), isPresented: $showReset) {
                Button(store.t("Cancelar", "Cancel"), role: .cancel) {}
                Button(store.t("Repor", "Reset"), role: .destructive) {
                    store.reset()
                }
            } message: {
                Text(store.t(
                    "Remove o idioma, os links e as credenciais guardadas neste iPhone.",
                    "Removes the language, links and credentials stored on this iPhone."
                ))
            }
        }
    }

    private var languageBinding: Binding<AppLanguage> {
        Binding(
            get: { store.settings.language },
            set: { value in store.update { $0.language = value } }
        )
    }

    private func binding<T>(_ keyPath: WritableKeyPath<AppSettings, T>) -> Binding<T> {
        Binding(
            get: { store.settings[keyPath: keyPath] },
            set: { value in store.update { $0[keyPath: keyPath] = value } }
        )
    }
}
