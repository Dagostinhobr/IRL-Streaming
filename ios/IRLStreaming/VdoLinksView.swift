import SwiftUI
import UIKit

/// PT: Configuração do VDO.Ninja e links de publicação/visualização.
/// EN: VDO.Ninja configuration and publish/view links.
struct VdoLinksView: View {
    @EnvironmentObject private var store: SettingsStore
    @State private var toast: String?

    private var vdo: VdoSettings { store.settings.vdoNinja }
    private var pushURL: String { VdoNinja.pushURL(vdo) }
    private var viewURL: String { VdoNinja.viewURL(vdo) }
    private var hasTarget: Bool { !VdoNinja.pushId(vdo).isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    identityCard
                    qualityCard
                    linksCard
                    helpCard
                }
                .padding(16)
            }
            .pageBackground()
            .navigationTitle(store.t("Links", "Links"))
            .alert("", isPresented: Binding(get: { toast != nil }, set: { if !$0 { toast = nil } })) {
                Button("OK", role: .cancel) { toast = nil }
            } message: {
                Text(toast ?? "")
            }
        }
    }

    // MARK: - Identity

    private var identityCard: some View {
        Card {
            SectionTitle(text: store.t("Identidade", "Identity"))

            LabeledField(label: store.t("Servidor", "Server")) {
                TextField("https://vdo.ninja", text: vdoBinding(\.host))
                    .keyboardType(.URL)
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            }

            LabeledField(
                label: store.t("ID de ligação (opcional)", "Stream ID (optional)"),
                hint: store.t("Quando preenchido, tem prioridade sobre a sala.",
                              "When filled in, it takes priority over the room.")
            ) {
                TextField("meu-stream-7f3a", text: vdoBinding(\.streamId))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            }
            HStack(spacing: 8) {
                SecondaryButton(title: store.t("Gerar novo ID", "Generate new ID"), systemImage: "wand.and.stars") {
                    store.updateVdo { $0.streamId = VdoNinja.newRoom() }
                }
                SecondaryButton(title: store.t("Copiar ID", "Copy ID"), systemImage: "doc.on.doc") {
                    copy(vdo.streamId, message: store.t("ID copiado", "ID copied"))
                }
            }

            LabeledField(
                label: store.t("Sala (opcional)", "Room (optional)"),
                hint: store.t("Agrupa convidados: ?push=SALA e ?view=SALA.",
                              "Groups guests: ?push=ROOM and ?view=ROOM.")
            ) {
                TextField("irl-casa-9f2", text: vdoBinding(\.room))
                    .autocorrectionDisabled()
                    .textInputAutocapitalization(.never)
            }
            HStack(spacing: 8) {
                SecondaryButton(title: store.t("Gerar nova sala", "Generate new room"), systemImage: "wand.and.stars") {
                    store.updateVdo { $0.room = VdoNinja.newRoom() }
                }
                SecondaryButton(title: store.t("Copiar sala", "Copy room"), systemImage: "doc.on.doc") {
                    copy(vdo.room, message: store.t("Sala copiada", "Room copied"))
                }
            }

            LabeledField(label: store.t("Nome exibido (opcional)", "Display name (optional)")) {
                TextField(store.t("Ex.: Câmara da rua", "e.g. Street camera"), text: vdoBinding(\.label))
            }

            LabeledField(label: store.t("Senha (opcional)", "Password (optional)")) {
                SecureField("••••••••", text: vdoBinding(\.password))
            }

            SecondaryButton(title: store.t("Colar link do VDO.Ninja e preencher", "Paste VDO.Ninja link and fill in"),
                            systemImage: "doc.on.clipboard") {
                pasteLink()
            }
        }
    }

    // MARK: - Quality

    private var qualityCard: some View {
        Card {
            SectionTitle(text: store.t("Qualidade", "Quality"))

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

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(store.t("Bitrate de áudio", "Audio bitrate")).font(.subheadline)
                    Spacer()
                    Text(vdo.audioBitrateKbps == 0
                         ? store.t("Desligado", "Off")
                         : "\(vdo.audioBitrateKbps) kbps")
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(Theme.mint)
                }
                Slider(value: intBinding(\.audioBitrateKbps), in: 0...320, step: 16)
                    .tint(Theme.mint)
            }

            ToggleRow(title: store.t("Estéreo", "Stereo"),
                      subtitle: store.t("Envia dois canais de áudio.", "Sends two audio channels."),
                      isOn: vdoBinding(\.stereo))
            ToggleRow(title: store.t("Iniciar automaticamente", "Start automatically"),
                      subtitle: store.t("Pede câmara/microfone ao abrir o estúdio.",
                                        "Requests camera/microphone when the studio opens."),
                      isOn: vdoBinding(\.autostart))
            ToggleRow(title: store.t("Saída limpa", "Clean output"),
                      subtitle: store.t("Esconde cabeçalho e controlos do VDO.Ninja.",
                                        "Hides the VDO.Ninja header and controls."),
                      isOn: vdoBinding(\.cleanOutput))
            ToggleRow(title: store.t("Somente áudio", "Audio only"),
                      subtitle: store.t("Publica sem vídeo (podcast/rádio).",
                                        "Publishes without video (podcast/radio)."),
                      isOn: vdoBinding(\.audioOnly))
            ToggleRow(title: store.t("Forçar H.264", "Force H.264"),
                      subtitle: store.t("Maior compatibilidade com OBS e browsers.",
                                        "Better compatibility with OBS and browsers."),
                      isOn: vdoBinding(\.forceH264))
        }
    }

    // MARK: - Links

    private var linksCard: some View {
        Card {
            SectionTitle(text: store.t("Links", "Links"))

            if !hasTarget {
                Text(store.t("Define um ID ou uma sala para gerar os links.",
                             "Set an ID or a room to generate the links."))
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
            } else {
                LinkBlock(
                    title: store.t("Publicar (celular/convidado)", "Publish (phone/guest)"),
                    url: pushURL,
                    copyTitle: store.t("Copiar link", "Copy link"),
                    shareTitle: store.t("Partilhar", "Share"),
                    onCopy: { copy(pushURL, message: store.t("Link de publicação copiado", "Publish link copied")) }
                )

                Divider().background(Theme.stroke)

                LinkBlock(
                    title: store.t("Assistir", "Watch"),
                    url: viewURL,
                    copyTitle: store.t("Copiar link", "Copy link"),
                    shareTitle: store.t("Partilhar", "Share"),
                    onCopy: { copy(viewURL, message: store.t("Link de visualização copiado", "View link copied")) }
                )
            }
        }
    }

    private var helpCard: some View {
        Card {
            SectionTitle(text: store.t("Como funciona", "How it works"))
            Text(store.t(
                "O link de publicação abre o VDO.Ninja no telemóvel que vai filmar. O link de visualização pode ser aberto no OBS, noutro telemóvel ou no computador. Não é necessário configurar RTMP.",
                "The publish link opens VDO.Ninja on the phone that will film. The view link can be opened in OBS, on another phone or on a computer. No RTMP setup is required."
            ))
            .font(.footnote)
            .foregroundStyle(Theme.muted)
            Text(store.t(
                "A senha protege a ligação. Usa exemplos fictícios nos testes e não partilhes links com a senha em público.",
                "The password protects the connection. Use fictional examples while testing and do not share links with the password in public."
            ))
            .font(.footnote)
            .foregroundStyle(Theme.muted)
        }
    }

    // MARK: - Helpers

    private func vdoBinding<T>(_ keyPath: WritableKeyPath<VdoSettings, T>) -> Binding<T> {
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

    private func copy(_ text: String, message: String) {
        guard !text.isEmpty else { return }
        UIPasteboard.general.string = text
        toast = message
    }

    private func pasteLink() {
        guard let raw = UIPasteboard.general.string, !raw.isEmpty else {
            toast = store.t("A área de transferência está vazia.", "The clipboard is empty.")
            return
        }
        guard let parsed = VdoNinja.parseLink(raw) else {
            toast = store.t("Link inválido. Exemplo: https://vdo.ninja/?view=meu-stream-7f3a&p=senha-ficticia",
                            "Invalid link. Example: https://vdo.ninja/?view=my-stream-7f3a&p=fake-password")
            return
        }
        store.updateVdo {
            $0.host = parsed.host
            $0.streamId = parsed.streamId
            $0.room = parsed.room
            $0.password = parsed.password
        }
        toast = store.t("Link aplicado.", "Link applied.")
    }
}

/// PT: Bloco com link selecionável, copiar e partilhar.
/// EN: Block with a selectable link, copy and share.
private struct LinkBlock: View {
    let title: String
    let url: String
    let copyTitle: String
    let shareTitle: String
    let onCopy: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.caption.weight(.medium))
                .foregroundStyle(Theme.muted)

            Text(url.isEmpty ? "—" : url)
                .font(.caption.monospaced())
                .foregroundStyle(url.isEmpty ? Theme.muted : Color.primary)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(10)
                .background(Color.black.opacity(0.28))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))

            HStack(spacing: 8) {
                SecondaryButton(title: copyTitle, systemImage: "doc.on.doc", action: onCopy)
                if let link = URL(string: url), !url.isEmpty {
                    ShareLink(item: link) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.up")
                            Text(shareTitle)
                        }
                        .font(.footnote.weight(.semibold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.white.opacity(0.06))
                        .foregroundStyle(Theme.mint)
                        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                    }
                }
            }
            .disabled(url.isEmpty)
        }
    }
}
