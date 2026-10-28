import SwiftUI
import UIKit

/// PT: Chat da Twitch com leitura anónima e envio autenticado.
/// EN: Twitch chat with anonymous reading and authenticated sending.
struct ChatView: View {
    @EnvironmentObject private var store: SettingsStore
    @EnvironmentObject private var chat: TwitchChatClient
    @State private var draft = ""
    @State private var showConfig = false
    @State private var didAutoConnect = false

    private var settings: ChatSettings { store.settings.chat }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if chat.channel.isEmpty {
                    emptyState
                } else {
                    header
                    Divider().background(Theme.stroke)
                    messageList
                    Divider().background(Theme.stroke)
                    composer
                }
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle(store.t("Chat", "Chat"))
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    if !chat.channel.isEmpty {
                        Button {
                            if chat.status == .connected {
                                chat.disconnect()
                            } else {
                                chat.connect(settings: settings)
                            }
                        } label: {
                            Image(systemName: chat.status == .connected ? "stop.circle" : "bolt.horizontal.circle")
                        }
                        Button {
                            chat.clear()
                        } label: {
                            Image(systemName: "trash")
                        }
                    }
                    Button {
                        showConfig = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                }
            }
            .sheet(isPresented: $showConfig) {
                ChatConfigView()
                    .environmentObject(store)
                    .environmentObject(chat)
            }
            .task {
                guard !didAutoConnect else { return }
                didAutoConnect = true
                if !chat.channel.isEmpty && chat.status == .disconnected {
                    chat.connect(settings: settings)
                }
            }
        }
    }

    private var emptyState: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Card {
                    HStack(spacing: 10) {
                        Image(systemName: "bubble.left.and.bubble.right.fill")
                            .font(.title2)
                            .foregroundStyle(Theme.mint)
                        Text(store.t("Chat da Twitch", "Twitch chat"))
                            .font(.headline)
                    }
                    Text(store.t(
                        "Lê o chat de um canal sem conta. Para enviar mensagens, adiciona o teu utilizador e um token OAuth com as permissões chat:read e chat:edit.",
                        "Reads a channel chat without an account. To send messages, add your username and an OAuth token with chat:read and chat:edit permissions."
                    ))
                    .font(.subheadline)
                    .foregroundStyle(Theme.muted)
                    PrimaryButton(title: store.t("Configurar canal", "Configure channel"), systemImage: "gearshape") {
                        showConfig = true
                    }
                }
                Card {
                    SectionTitle(text: store.t("Sobre o token", "About the token"))
                    Text(store.t(
                        "Gera um token em twitchtokengenerator.com com as permissões de chat. O token fica apenas neste iPhone (UserDefaults) e nunca é enviado para outro servidor.",
                        "Generate a token at twitchtokengenerator.com with chat permissions. The token stays on this iPhone (UserDefaults) and is never sent to another server."
                    ))
                    .font(.footnote)
                    .foregroundStyle(Theme.muted)
                }
            }
            .padding(16)
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(statusColor)
                .frame(width: 8, height: 8)
            VStack(alignment: .leading, spacing: 1) {
                Text("#\(chat.channel)")
                    .font(.subheadline.weight(.semibold))
                Text(statusText)
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
            }
            Spacer()
            if !chat.canSend {
                Text(store.t("Só leitura", "Read only"))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Theme.muted)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.white.opacity(0.06))
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var messageList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 8) {
                    ForEach(chat.messages) { message in
                        ChatRow(message: message)
                            .id(message.id)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .onChange(of: chat.messages.count) { _ in
                if let last = chat.messages.last {
                    withAnimation(.easeOut(duration: 0.15)) {
                        proxy.scrollTo(last.id, anchor: .bottom)
                    }
                }
            }
        }
    }

    private var composer: some View {
        HStack(spacing: 8) {
            TextField(chat.canSend
                      ? store.t("Escrever mensagem…", "Write a message…")
                      : store.t("Adiciona um token para enviar", "Add a token to send"),
                      text: $draft)
                .textFieldStyle(.plain)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.black.opacity(0.28))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .disabled(!chat.canSend)
                .onSubmit(send)

            Button(action: send) {
                Image(systemName: "paperplane.fill")
                    .font(.body.weight(.semibold))
                    .frame(width: 40, height: 40)
                    .background(canSubmit ? Theme.mint : Theme.mint.opacity(0.2))
                    .foregroundStyle(canSubmit ? Color.black : Color.black.opacity(0.4))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }
            .disabled(!canSubmit)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private var canSubmit: Bool {
        chat.canSend && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var statusColor: Color {
        switch chat.status {
        case .connected: return Theme.mint
        case .connecting: return .yellow
        case .disconnected: return Theme.muted
        }
    }

    private var statusText: String {
        switch chat.status {
        case .connected: return store.t("Ligado", "Connected")
        case .connecting: return store.t("A ligar…", "Connecting…")
        case .disconnected: return store.t("Desligado", "Disconnected")
        }
    }

    private func send() {
        guard chat.sendMessage(draft) else { return }
        draft = ""
    }
}

private struct ChatRow: View {
    let message: ChatMessage

    var body: some View {
        if message.isSystem {
            Text(message.text)
                .font(.caption2)
                .foregroundStyle(Theme.muted)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            HStack(alignment: .top, spacing: 6) {
                Text(message.displayName)
                    .font(.caption.weight(.bold))
                    .foregroundStyle(message.colorHex.flatMap { Color(hex: $0) } ?? Theme.mint)
                Text(message.text)
                    .font(.subheadline)
                    .textSelection(.enabled)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// PT: Configuração do canal e das credenciais de chat.
/// EN: Channel and chat credentials configuration.
struct ChatConfigView: View {
    @EnvironmentObject private var store: SettingsStore
    @EnvironmentObject private var chat: TwitchChatClient
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField(store.t("canal (sem #)", "channel (without #)"), text: binding(\.channel))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    TextField(store.t("utilizador da Twitch", "Twitch username"), text: binding(\.username))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    SecureField(store.t("token OAuth (opcional)", "OAuth token (optional)"), text: binding(\.oauthToken))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                } header: {
                    Text(store.t("Canal", "Channel"))
                } footer: {
                    Text(store.t(
                        "Sem token o chat funciona em modo de leitura. O token é guardado apenas neste iPhone.",
                        "Without a token the chat works in read-only mode. The token is stored only on this iPhone."
                    ))
                }

                Section {
                    Toggle(store.t("Mostrar chat sobre o estúdio", "Show chat over the studio"),
                           isOn: binding(\.showOverlay))
                        .tint(Theme.mint)
                }
            }
            .navigationTitle(store.t("Chat da Twitch", "Twitch chat"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(store.t("Fechar", "Close")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(store.t("Ligar", "Connect")) {
                        chat.connect(settings: store.settings.chat)
                        dismiss()
                    }
                    .disabled(store.settings.chat.channel.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }

    private func binding<T>(_ keyPath: WritableKeyPath<ChatSettings, T>) -> Binding<T> {
        Binding(
            get: { store.settings.chat[keyPath: keyPath] },
            set: { value in store.updateChat { $0[keyPath: keyPath] = value } }
        )
    }
}

extension Color {
    /// PT: Converte `#RRGGBB` numa cor. / EN: Converts `#RRGGBB` into a color.
    init?(hex: String) {
        var value = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("#") { value.removeFirst() }
        guard value.count == 6, let number = Int(value, radix: 16) else { return nil }
        self.init(
            red: Double((number >> 16) & 0xFF) / 255,
            green: Double((number >> 8) & 0xFF) / 255,
            blue: Double(number & 0xFF) / 255
        )
    }
}
