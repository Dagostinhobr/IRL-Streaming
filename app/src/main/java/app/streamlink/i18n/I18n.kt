package app.streamlink.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import app.streamlink.data.AppLanguage
import app.streamlink.data.ChatProvider
import app.streamlink.data.PipPosition
import app.streamlink.data.TargetKind
import app.streamlink.data.VideoOrientation

/**
 * PT: Idioma atual da composição. Muda instantaneamente quando o utilizador
 * escolhe outro idioma nas configurações.
 * EN: Current composition language. Changes instantly when the user selects
 * another language in settings.
 */
val LocalLanguage = staticCompositionLocalOf { AppLanguage.PT }

/**
 * PT: Devolve o texto no idioma ativo.
 * EN: Returns the text in the active language.
 */
@Composable
fun tr(pt: String, en: String): String =
    if (LocalLanguage.current == AppLanguage.EN) en else pt

/**
 * PT: Tradução fora de composables (ViewModel, serviço, notificações).
 * EN: Translation outside composables (ViewModel, service, notifications).
 */
object L10n {
    @Volatile
    var language: AppLanguage = AppLanguage.PT
        private set

    fun update(language: AppLanguage) {
        this.language = language
    }

    fun t(pt: String, en: String): String =
        if (language == AppLanguage.EN) en else pt
}

@Composable
fun VideoOrientation.localizedLabel(): String = tr(label, labelEn)

@Composable
fun ChatProvider.localizedLabel(): String = tr(label, labelEn)

@Composable
fun TargetKind.localizedLabel(): String = tr(label, labelEn)

@Composable
fun PipPosition.localizedLabel(): String = tr(label, labelEn)

/** PT: Rótulo localizado para uso fora de composables. / EN: Localised label outside composables. */
fun VideoOrientation.localizedLabelText(): String = L10n.t(label, labelEn)

fun ChatProvider.localizedLabelText(): String = L10n.t(label, labelEn)

fun TargetKind.localizedLabelText(): String = L10n.t(label, labelEn)
