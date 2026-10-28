package app.streamlink.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Persistência simples e síncrona em SharedPreferences.
 * O JSON completo do AppSettings é salvo a cada alteração.
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
            // Garante que sempre exista um destino selecionado válido.
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
                // Formato antigo/corrompido: começa do zero em vez de travar o app.
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
