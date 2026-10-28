package app.streamlink.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * PT: Persistência simples e síncrona em SharedPreferences.
 * PT: O JSON completo do AppSettings é salvo a cada alteração.
 *
 * EN: Simple, synchronous persistence in SharedPreferences.
 * EN: The complete AppSettings JSON is saved on every change.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value).let { settings ->
            // PT: Garante que sempre exista um destino selecionado válido.
            // EN: Ensures there is always a valid selected target.
            if (settings.targets.isNotEmpty() && settings.targets.none { it.id == settings.selectedTargetId }) {
                settings.copy(selectedTargetId = settings.targets.first().id)
            } else {
                settings
            }
        }
        _settings.value = updated
        save(updated)
    }

    fun replace(settings: AppSettings) {
        _settings.value = settings
        save(settings)
    }

    private fun load(): AppSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return AppSettings()
        return runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
            .getOrElse {
                // PT: Formato antigo/corrompido: começa do zero em vez de travar o app.
                // EN: Old/corrupted format: start from scratch instead of crashing the app.
                AppSettings()
            }
    }

    private fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SETTINGS, json.encodeToString(AppSettings.serializer(), settings))
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "streamlink_settings"
        private const val KEY_SETTINGS = "settings_json_v1"
    }
}
