import SwiftUI

/// PT: Gestão dos destinos RTMP/RTMPS do encoder nativo.
/// EN: Management of the native encoder RTMP/RTMPS targets.
struct RtmpTargetsView: View {
    @EnvironmentObject private var store: SettingsStore
    @Environment(\.dismiss) private var dismiss

    @State private var showEditor = false
    @State private var editing: RtmpTarget?
    @State private var deleteCandidate: RtmpTarget?

    private var native: NativeSettings { store.settings.native }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text(store.t(
                        "Guarda várias URLs de ingest (Twitch, YouTube, Kick ou servidor próprio) e troca com um toque. A stream key nunca aparece no ecrã principal.",
                        "Keep several ingest URLs (Twitch, YouTube, Kick or your own server) and switch with a tap. The stream key never appears on the main screen."
                    ))
                    .font(.caption)
                    .foregroundStyle(Theme.muted)
                    .listRowBackground(Color.clear)
                }

                Section {
                    if native.targets.isEmpty {
                        Text(store.t("Nenhum destino configurado.", "No target configured."))
                            .foregroundStyle(Theme.muted)
                            .listRowBackground(Theme.card)
                    }
                    ForEach(native.targets) { target in
                        targetRow(target)
                            .listRowBackground(Theme.card)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    deleteCandidate = target
                                } label: {
                                    Label(store.t("Remover", "Remove"), systemImage: "trash")
                                }
                                Button {
                                    editing = target
                                    showEditor = true
                                } label: {
                                    Label(store.t("Editar", "Edit"), systemImage: "pencil")
                                }
                                .tint(Theme.mint)
                            }
                    }
                } header: {
                    Text(store.t("Destinos", "Targets"))
                } footer: {
                    Text(store.t(
                        "RTMP / RTMPS: informa a URL da aplicação e a stream key em campos separados. Exemplos fictícios: rtmp://live.example.test/app.",
                        "RTMP / RTMPS: provide the application URL and the stream key in separate fields. Fictional examples: rtmp://live.example.test/app."
                    ))
                    .font(.caption2)
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(store.t("Destinos", "Targets"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.t("Fechar", "Close")) { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        editing = nil
                        showEditor = true
                    } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel(store.t("Adicionar destino", "Add target"))
                }
            }
            .sheet(isPresented: $showEditor) {
                RtmpTargetEditor(initial: editing) { saved in
                    save(saved)
                    showEditor = false
                }
                .environmentObject(store)
            }
            .alert(
                store.t("Remover destino?", "Remove target?"),
                isPresented: Binding(get: { deleteCandidate != nil }, set: { if !$0 { deleteCandidate = nil } })
            ) {
                Button(store.t("Cancelar", "Cancel"), role: .cancel) { deleteCandidate = nil }
                Button(store.t("Remover", "Remove"), role: .destructive) {
                    if let target = deleteCandidate { delete(target) }
                    deleteCandidate = nil
                }
            } message: {
                Text(store.t(
                    "O destino será removido. A transmissão atual não é afetada.",
                    "The target will be removed. The current stream is not affected."
                ))
            }
        }
    }

    private func targetRow(_ target: RtmpTarget) -> some View {
        Button {
            store.updateNative {
                $0.selectedTargetId = target.id
                $0.normalizeSelection()
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: target.id == native.selectedTarget()?.id
                      ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(target.id == native.selectedTarget()?.id ? Theme.mint : Theme.muted)
                VStack(alignment: .leading, spacing: 3) {
                    Text(target.name)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.primary)
                    Text(target.displayEndpoint)
                        .font(.caption2)
                        .foregroundStyle(Theme.muted)
                        .lineLimit(1)
                }
                Spacer()
                Text(target.badge)
                    .font(.caption2.weight(.bold))
                    .padding(.horizontal, 7)
                    .padding(.vertical, 3)
                    .background(Theme.mint.opacity(0.16))
                    .foregroundStyle(Theme.mint)
                    .clipShape(Capsule())
            }
        }
        .buttonStyle(.plain)
    }

    private func save(_ target: RtmpTarget) {
        store.updateNative { settings in
            if let index = settings.targets.firstIndex(where: { $0.id == target.id }) {
                settings.targets[index] = target
            } else {
                settings.targets.append(target)
                settings.selectedTargetId = target.id
            }
            settings.normalizeSelection()
        }
    }

    private func delete(_ target: RtmpTarget) {
        store.updateNative { settings in
            settings.targets.removeAll { $0.id == target.id }
            if settings.selectedTargetId == target.id {
                settings.selectedTargetId = settings.targets.first?.id
            }
            settings.normalizeSelection()
        }
    }
}

/// PT: Editor de um destino RTMP/RTMPS. / EN: RTMP/RTMPS target editor.
struct RtmpTargetEditor: View {
    @EnvironmentObject private var store: SettingsStore
    @Environment(\.dismiss) private var dismiss

    let initial: RtmpTarget?
    let onSave: (RtmpTarget) -> Void

    @State private var name: String
    @State private var url: String
    @State private var streamKey: String
    @State private var backupURL: String
    @State private var revealKey = false

    init(initial: RtmpTarget?, onSave: @escaping (RtmpTarget) -> Void) {
        self.initial = initial
        self.onSave = onSave
        _name = State(initialValue: initial?.name ?? "")
        _url = State(initialValue: initial?.url ?? "rtmp://")
        _streamKey = State(initialValue: initial?.streamKey ?? "")
        _backupURL = State(initialValue: initial?.backupURL ?? "")
    }

    private var urlIsValid: Bool {
        let value = url.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return value.hasPrefix("rtmp://") || value.hasPrefix("rtmps://")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(store.t("Ex.: Twitch", "e.g. Twitch"), text: $name)
                    TextField("rtmp://live.example.test/app", text: $url)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    if !urlIsValid {
                        Text(store.t("A URL deve começar por rtmp:// ou rtmps://.",
                                     "The URL must start with rtmp:// or rtmps://."))
                            .font(.caption2)
                            .foregroundStyle(Theme.danger)
                    }
                } header: {
                    Text(store.t("Servidor", "Server"))
                } footer: {
                    Text(store.t(
                        "Coloca a URL da aplicação, sem a stream key. Exemplo fictício: rtmp://live.example.test/app",
                        "Use the application URL without the stream key. Fictional example: rtmp://live.example.test/app"
                    ))
                }

                Section {
                    HStack {
                        if revealKey {
                            TextField(store.t("Stream key", "Stream key"), text: $streamKey)
                        } else {
                            SecureField(store.t("Stream key", "Stream key"), text: $streamKey)
                        }
                        Button {
                            revealKey.toggle()
                        } label: {
                            Image(systemName: revealKey ? "eye.slash" : "eye")
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.muted)
                    }
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                } header: {
                    Text(store.t("Autenticação", "Authentication"))
                } footer: {
                    Text(store.t(
                        "A chave é guardada localmente e enviada apenas ao servidor de destino.",
                        "The key is stored locally and sent only to the target server."
                    ))
                }

                Section {
                    TextField(store.t("URL de backup (opcional)", "Backup URL (optional)"), text: $backupURL)
                        .keyboardType(.URL)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                } footer: {
                    Text(store.t(
                        "Campo reservado para paridade com o Android. A publicação de backup ainda não está ativa no iOS.",
                        "Field reserved for Android parity. Backup publishing is not active on iOS yet."
                    ))
                }
            }
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(initial == nil
                             ? store.t("Novo destino", "New target")
                             : store.t("Editar destino", "Edit target"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.t("Cancelar", "Cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(store.t("Guardar", "Save")) {
                        var target = initial ?? RtmpTarget(name: "", url: "")
                        target.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        if target.name.isEmpty {
                            target.name = store.t("Destino", "Target")
                        }
                        target.url = url.trimmingCharacters(in: .whitespacesAndNewlines)
                        target.streamKey = streamKey.trimmingCharacters(in: .whitespacesAndNewlines)
                        target.backupURL = backupURL.trimmingCharacters(in: .whitespacesAndNewlines)
                        onSave(target)
                        dismiss()
                    }
                    .disabled(!urlIsValid)
                }
            }
        }
    }
}
