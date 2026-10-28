package app.streamlink.util

import android.content.Context
import android.media.AudioManager
import android.os.Build

/**
 * PT: O WebView/WebRTC escolhe o microfone pelo dispositivo de comunicação do
 * PT: sistema. Se uma versão anterior tiver deixado um dispositivo inválido
 * PT: forçado, o `getUserMedia` falha com `NotReadableError`; por isso limpamos o
 * PT: estado antes de abrir o estúdio do VDO.Ninja.
 *
 * EN: The WebView/WebRTC chooses the microphone through the system communication
 * EN: device. If a previous version left an invalid device forced, `getUserMedia`
 * EN: fails with `NotReadableError`; that is why we clear the state before
 * EN: opening the VDO.Ninja studio.
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
