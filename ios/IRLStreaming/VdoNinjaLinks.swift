import Foundation

/// PT: Monta os links do VDO.Ninja a partir das preferências locais.
/// PT: O VDO.Ninja aceita sala (`?push=SALA`) ou ID de ligação (`?push=ID`).
/// PT: Quando o ID está preenchido, tem prioridade sobre a sala.
///
/// EN: Builds the VDO.Ninja links from the local preferences.
/// EN: VDO.Ninja accepts a room (`?push=ROOM`) or a connection ID (`?push=ID`).
/// EN: When the ID is filled in, it takes priority over the room.
enum VdoNinja {
    static let defaultHost = "https://vdo.ninja"
    private static let alphabet = Array("abcdefghijklmnopqrstuvwxyz0123456789")

    /// PT: Gera uma sala/ID aleatório de 9 caracteres.
    /// EN: Generates a random 9-character room/ID.
    static func newRoom(length: Int = 9) -> String {
        String((0..<length).compactMap { _ in alphabet.randomElement() })
    }

    static func effectiveHost(_ settings: VdoSettings) -> String {
        let host = settings.host.trimmingCharacters(in: .whitespacesAndNewlines)
        return host.isEmpty ? defaultHost : host
    }

    /// PT: Identificador usado nos links: ID de ligação ou, se vazio, a sala.
    /// EN: Identifier used in the links: connection ID or, if empty, the room.
    static func pushId(_ settings: VdoSettings) -> String {
        let id = settings.streamId.trimmingCharacters(in: .whitespacesAndNewlines)
        return id.isEmpty ? settings.room.trimmingCharacters(in: .whitespacesAndNewlines) : id
    }

    private static func components(_ settings: VdoSettings) -> URLComponents? {
        URLComponents(string: effectiveHost(settings))
    }

    /// PT: Link que o convidado/câmera abre para publicar via WebRTC (P2P).
    /// EN: The link the guest/camera opens to publish over WebRTC (P2P).
    static func pushURL(_ settings: VdoSettings) -> String {
        guard var components = components(settings) else { return "" }
        var items: [URLQueryItem] = [URLQueryItem(name: "push", value: pushId(settings))]
        let label = settings.label.trimmingCharacters(in: .whitespacesAndNewlines)
        if !label.isEmpty { items.append(URLQueryItem(name: "label", value: label)) }
        let password = settings.password.trimmingCharacters(in: .whitespacesAndNewlines)
        if !password.isEmpty { items.append(URLQueryItem(name: "password", value: password)) }
        if settings.autostart { items.append(URLQueryItem(name: "autostart", value: "1")) }
        if settings.cleanOutput { items.append(URLQueryItem(name: "cleanoutput", value: "1")) }
        if settings.stereo { items.append(URLQueryItem(name: "stereo", value: "1")) }
        if settings.audioOnly {
            items.append(URLQueryItem(name: "novideo", value: "1"))
        } else {
            items.append(URLQueryItem(name: "videobitrate", value: String(settings.videoBitrateKbps)))
            // PT: Seleciona "Share your Camera" e esconde a partilha de ecrã.
            // EN: Selects "Share your Camera" and hides screen sharing.
            items.append(URLQueryItem(name: "webcam", value: "1"))
        }
        if settings.audioBitrateKbps > 0 {
            items.append(URLQueryItem(name: "audiobitrate", value: String(settings.audioBitrateKbps)))
        }
        if settings.forceH264 { items.append(URLQueryItem(name: "codec", value: "h264")) }
        components.queryItems = (components.queryItems ?? []) + items
        return components.string ?? ""
    }

    /// PT: Link do estúdio interno (WebView). `audiodevice=1` seleciona o
    /// PT: microfone padrão automaticamente e esconde o menu de escolha.
    /// EN: Link used by the embedded studio (WebView). `audiodevice=1` selects
    /// EN: the default microphone automatically and hides the picker.
    static func studioURL(_ settings: VdoSettings) -> String {
        guard var components = URLComponents(string: pushURL(settings)) else { return "" }
        var items = components.queryItems ?? []
        items.removeAll { $0.name == "cleanoutput" }
        items.append(URLQueryItem(name: "audiodevice", value: "1"))
        items.append(URLQueryItem(name: "cleanoutput", value: "1"))
        components.queryItems = items
        return components.string ?? ""
    }

    /// PT: Link para assistir à publicação via P2P.
    /// EN: Link to watch the publication via P2P.
    static func viewURL(_ settings: VdoSettings) -> String {
        guard var components = components(settings) else { return "" }
        var items: [URLQueryItem] = [
            URLQueryItem(name: "view", value: pushId(settings)),
            URLQueryItem(name: "autoplay", value: "1")
        ]
        let password = settings.password.trimmingCharacters(in: .whitespacesAndNewlines)
        if !password.isEmpty { items.append(URLQueryItem(name: "p", value: password)) }
        if settings.cleanOutput { items.append(URLQueryItem(name: "cleanoutput", value: "1")) }
        if settings.audioOnly { items.append(URLQueryItem(name: "novideo", value: "1")) }
        components.queryItems = (components.queryItems ?? []) + items
        return components.string ?? ""
    }

    /// PT: Dados extraídos de um link do VDO.Ninja.
    /// EN: Data extracted from a VDO.Ninja link.
    struct ParsedLink {
        let host: String
        let streamId: String
        let room: String
        let password: String
    }

    /// PT: Interpreta um link existente, aceitando URL completa, query solta,
    /// PT: parâmetros sem `?` ou apenas o ID.
    /// EN: Parses an existing link, accepting a full URL, a loose query,
    /// EN: parameters without `?`, or just the ID.
    static func parseLink(_ raw: String) -> ParsedLink? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }

        if !text.contains("://") && !text.hasPrefix("?") && !text.contains("=") {
            return ParsedLink(host: defaultHost, streamId: text, room: "", password: "")
        }

        let withScheme: String
        if text.lowercased().hasPrefix("http://") || text.lowercased().hasPrefix("https://") {
            withScheme = text
        } else if text.hasPrefix("?") {
            withScheme = defaultHost + "/" + text
        } else if text.contains("=") && !text.split(separator: "?").first!.contains(".") {
            withScheme = defaultHost + "/?" + text
        } else {
            withScheme = "https://" + text
        }

        guard let url = URL(string: withScheme),
              let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return nil
        }

        func query(_ name: String) -> String {
            (components.queryItems?.first { $0.name == name }?.value ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }

        let streamId = query("view").isEmpty ? query("push") : query("view")
        let room = query("room")
        let password = query("password").isEmpty ? query("p") : query("password")
        guard !streamId.isEmpty || !room.isEmpty else { return nil }

        let scheme = components.scheme ?? "https"
        let authority = components.host ?? "vdo.ninja"
        return ParsedLink(host: "\(scheme)://\(authority)", streamId: streamId, room: room, password: password)
    }
}
