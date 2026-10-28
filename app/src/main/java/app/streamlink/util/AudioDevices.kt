package app.streamlink.util

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/**
 * Uma entrada de áudio disponível no aparelho (interna, USB, fone, Bluetooth...).
 * O [id] é o identificador do AudioDeviceInfo e é persistido nas configurações.
 */
data class AudioInputOption(
    val id: Int,
    val label: String,
    val detail: String,
    val isUsb: Boolean,
    val isBuiltIn: Boolean
)

object AudioDevices {

    /** Lista os microfones que podem ser usados pelo encoder, com o interno primeiro. */
    fun list(context: Context): List<AudioInputOption> {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .asSequence()
                .filter { isSupported(it.type) }
                .map { it.toOption() }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<AudioInputOption> { it.isBuiltIn }.thenByDescending { it.isUsb })
                .toList()
        }.getOrDefault(emptyList())
    }

    /** Retorna o dispositivo com o id informado, ou null quando não existe mais. */
    fun find(context: Context, id: Int): AudioDeviceInfo? {
        if (id < 0) return null
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
        }.getOrNull()
    }

    /** Microfone interno, usado quando a opção "Automático" está selecionada. */
    fun builtIn(context: Context): AudioDeviceInfo? {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        return runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        }.getOrNull()
    }

    private fun isSupported(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HDMI -> true

        else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    private fun AudioDeviceInfo.toOption(): AudioInputOption {
        val name = runCatching { productName?.toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() }
        val (label, isUsb) = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microfone do aparelho" to false
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "Microfone USB" to true
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Fone com microfone" to false
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth" to false
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE" to false
            AudioDeviceInfo.TYPE_HDMI -> "HDMI" to false
            else -> "Entrada de áudio" to false
        }
        return AudioInputOption(
            id = id,
            label = label,
            detail = name ?: label,
            isUsb = isUsb,
            isBuiltIn = type == AudioDeviceInfo.TYPE_BUILTIN_MIC
        )
    }
}
