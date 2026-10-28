import Foundation

enum ChatStatus {
    case disconnected
    case connecting
    case connected
}

struct ChatMessage: Identifiable, Equatable {
    let id: String
    let user: String
    let displayName: String
    let text: String
    let colorHex: String?
    let isSystem: Bool
}

/// PT: Cliente de chat da Twitch via IRC sobre WebSocket.
/// PT: Leitura anónima sem token; envio requer utilizador + token OAuth.
/// EN: Twitch chat client over IRC via WebSocket.
/// EN: Anonymous reading without a token; sending requires a username + OAuth token.
/// EN: Reference: https://dev.twitch.tv/docs/chat/irc/
@MainActor
final class TwitchChatClient: ObservableObject {
    @Published private(set) var messages: [ChatMessage] = []
    @Published private(set) var status: ChatStatus = .disconnected

    private var socket: URLSessionWebSocketTask?
    private var receiveTask: Task<Void, Never>?
    private var settings = ChatSettings()
    private var closedByUser = false

    private static let url = URL(string: "wss://irc-ws.chat.twitch.tv:443")!

    var channel: String {
        settings.channel.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "#", with: "")
            .lowercased()
    }

    var canSend: Bool {
        !settings.oauthToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !settings.username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    func connect(settings: ChatSettings) {
        disconnect()
        self.settings = settings
        guard !channel.isEmpty else {
            appendSystem("Configure a channel first.")
            return
        }
        closedByUser = false
        status = .connecting

        let task = URLSession.shared.webSocketTask(with: Self.url)
        socket = task
        task.resume()

        let pass = passValue
        let nick = loginNick
        let join = channel
        receiveTask = Task { [weak self] in
            guard let self else { return }
            await self.sendRaw("CAP REQ :twitch.tv/tags twitch.tv/commands")
            await self.sendRaw("PASS \(pass)")
            await self.sendRaw("NICK \(nick)")
            await self.sendRaw("JOIN #\(join)")
            await self.receiveLoop(task)
        }
    }

    func disconnect() {
        closedByUser = true
        receiveTask?.cancel()
        receiveTask = nil
        socket?.cancel(with: .normalClosure, reason: nil)
        socket = nil
        status = .disconnected
    }

    @discardableResult
    func sendMessage(_ text: String) -> Bool {
        guard canSend, socket != nil else { return false }
        let clean = text
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\r", with: " ")
            .replacingOccurrences(of: "\n", with: " ")
        guard !clean.isEmpty else { return false }
        Task { await sendRaw("PRIVMSG #\(channel) :\(clean)") }
        return true
    }

    func clear() {
        messages.removeAll()
    }

    // MARK: - Socket

    private func sendRaw(_ command: String) async {
        try? await socket?.send(.string(command))
    }

    private func receiveLoop(_ task: URLSessionWebSocketTask) async {
        while !Task.isCancelled {
            do {
                let message = try await task.receive()
                switch message {
                case .string(let text):
                    handle(text)
                case .data(let data):
                    if let text = String(data: data, encoding: .utf8) { handle(text) }
                @unknown default:
                    break
                }
            } catch {
                if closedByUser { return }
                status = .disconnected
                appendSystem("Chat disconnected.")
                return
            }
        }
    }

    private func handle(_ raw: String) {
        for line in raw.components(separatedBy: "\r\n") where !line.isEmpty {
            handleLine(line)
        }
    }

    private func handleLine(_ line: String) {
        if line.hasPrefix("PING") {
            Task { await sendRaw("PONG :tmi.twitch.tv") }
            return
        }
        if line.contains(" 001 ") {
            status = .connected
            return
        }
        if line.hasPrefix(":tmi.twitch.tv NOTICE") {
            let text = line.components(separatedBy: " :").last ?? line
            if text.localizedCaseInsensitiveContains("authentication failed") ||
                text.localizedCaseInsensitiveContains("login unsuccessful") {
                appendSystem("Twitch login failed: \(text)")
            }
            return
        }

        var tags: [String: String] = [:]
        var rest = line
        if line.hasPrefix("@"), let space = line.firstIndex(of: " ") {
            let rawTags = line[line.index(after: line.startIndex)..<space]
            tags = parseTags(String(rawTags))
            rest = String(line[line.index(after: space)...])
        }

        if rest.contains(" PRIVMSG ") {
            handlePrivmsg(tags: tags, line: rest)
        } else if rest.contains(" CLEARMSG "), let id = tags["target-msg-id"] {
            messages.removeAll { $0.id == id }
        } else if rest.contains(" CLEARCHAT ") {
            let target = rest.components(separatedBy: " :").last?.trimmingCharacters(in: .whitespaces) ?? ""
            if target.isEmpty {
                messages.removeAll { !$0.isSystem }
            } else {
                messages.removeAll { $0.user.lowercased() == target.lowercased() }
            }
        }
    }

    private func handlePrivmsg(tags: [String: String], line: String) {
        guard line.hasPrefix(":"), let marker = line.range(of: " PRIVMSG ") else { return }
        let prefix = line[line.index(after: line.startIndex)..<marker.lowerBound]
        let login = prefix.components(separatedBy: "!").first ?? "twitch"
        let after = line[marker.upperBound...]
        guard let messageStart = after.range(of: " :") else { return }
        let text = String(after[messageStart.upperBound...])
        let displayName = tags["display-name"].flatMap { $0.isEmpty ? nil : $0 } ?? login

        append(ChatMessage(
            id: tags["id"] ?? UUID().uuidString,
            user: login,
            displayName: displayName,
            text: text,
            colorHex: tags["color"].flatMap { $0.range(of: "^#[0-9a-fA-F]{6}$", options: .regularExpression) != nil ? $0 : nil },
            isSystem: false
        ))
    }

    private func parseTags(_ raw: String) -> [String: String] {
        var map: [String: String] = [:]
        for pair in raw.components(separatedBy: ";") {
            let parts = pair.components(separatedBy: "=")
            guard parts.count >= 2, !parts[0].isEmpty else { continue }
            map[parts[0]] = unescape(parts.dropFirst().joined(separator: "="))
        }
        return map
    }

    private func unescape(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\\s", with: " ")
            .replacingOccurrences(of: "\\:", with: ";")
            .replacingOccurrences(of: "\\\\", with: "\\")
            .replacingOccurrences(of: "\\r", with: "")
            .replacingOccurrences(of: "\\n", with: "")
    }

    private var loginNick: String {
        canSend
            ? settings.username.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            : "justinfan\(Int.random(in: 10000...99999))"
    }

    private var passValue: String {
        let token = settings.oauthToken.trimmingCharacters(in: .whitespacesAndNewlines)
        if token.isEmpty { return "SCHMOOPIIE" }
        return token.hasPrefix("oauth:") ? token : "oauth:\(token)"
    }

    private func appendSystem(_ text: String) {
        append(ChatMessage(
            id: UUID().uuidString,
            user: "system",
            displayName: "IRL Streaming",
            text: text,
            colorHex: nil,
            isSystem: true
        ))
    }

    private func append(_ message: ChatMessage) {
        messages.append(message)
        if messages.count > 300 {
            messages.removeFirst(messages.count - 300)
        }
    }
}
