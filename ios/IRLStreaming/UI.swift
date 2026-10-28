import SwiftUI

/// PT: Paleta e componentes visuais partilhados.
/// EN: Shared palette and visual components.
enum Theme {
    static let mint = Color(red: 0.353, green: 0.941, blue: 0.722)
    static let background = Color(red: 0.035, green: 0.047, blue: 0.059)
    static let card = Color(red: 0.078, green: 0.098, blue: 0.118)
    static let stroke = Color.white.opacity(0.08)
    static let muted = Color(red: 0.58, green: 0.64, blue: 0.70)
    static let danger = Color(red: 0.96, green: 0.42, blue: 0.42)
}

struct Card<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.card)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Theme.stroke, lineWidth: 1)
        )
    }
}

struct SectionTitle: View {
    let text: String
    var body: some View {
        Text(text.uppercased())
            .font(.caption.weight(.semibold))
            .tracking(1.2)
            .foregroundStyle(Theme.muted)
    }
}

struct PrimaryButton: View {
    let title: String
    var systemImage: String? = nil
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
            }
            .font(.subheadline.weight(.semibold))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 11)
            .background(enabled ? Theme.mint : Theme.mint.opacity(0.25))
            .foregroundStyle(enabled ? Color.black : Color.black.opacity(0.45))
            .clipShape(RoundedRectangle(cornerRadius: 11, style: .continuous))
        }
        .disabled(!enabled)
    }
}

struct SecondaryButton: View {
    let title: String
    var systemImage: String? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                if let systemImage { Image(systemName: systemImage) }
                Text(title)
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

struct LabeledField<Field: View>: View {
    let label: String
    let hint: String?
    let field: Field

    init(label: String, hint: String? = nil, @ViewBuilder field: () -> Field) {
        self.label = label
        self.hint = hint
        self.field = field()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption.weight(.medium))
                .foregroundStyle(Theme.muted)
            field
                .textFieldStyle(.plain)
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.black.opacity(0.28))
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            if let hint {
                Text(hint)
                    .font(.caption2)
                    .foregroundStyle(Theme.muted)
            }
        }
    }
}

/// PT: Linha de ajuste com interruptor.
/// EN: Toggle row.
struct ToggleRow: View {
    let title: String
    var subtitle: String? = nil
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.subheadline)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption2)
                        .foregroundStyle(Theme.muted)
                }
            }
        }
        .tint(Theme.mint)
    }
}

extension View {
    /// PT: Fundo escuro padrão das páginas. / EN: Default dark page background.
    func pageBackground() -> some View {
        self
            .scrollContentBackground(.hidden)
            .background(Theme.background.ignoresSafeArea())
    }
}
