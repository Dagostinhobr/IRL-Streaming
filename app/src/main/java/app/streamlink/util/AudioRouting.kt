package app.streamlink.util

import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * O WebView/WebRTC escolhe o microfone pelo dispositivo de comunicação do
 * sistema. Se uma versão anterior tiver deixado um dispositivo inválido
 * forçado, o `getUserMedia` falha com `NotReadableError`; por isso limpamos o
 * estado antes de abrir o estúdio do VDO.Ninja.
 */
object AudioRouting {

    fun clearPreferredInput(context: Context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { manager.clearCommunicationDevice() }
        }
    }
}
