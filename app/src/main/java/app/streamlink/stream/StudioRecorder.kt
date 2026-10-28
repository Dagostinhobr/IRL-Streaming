package app.streamlink.stream

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PT: Grava no armazenamento do app o stream local do estúdio P2P.
 *
 * PT: O WebView usa `MediaRecorder` sobre a faixa de câmera/microfone e envia os
 * PT: blocos em base64. Os blocos são apenas concatenados no ficheiro: o
 * PT: MediaRecorder já produz um contentor completo (MP4 no Chromium atual, WebM
 * PT: como alternativa).
 *
 * EN: Records the local P2P studio stream into the app's storage.
 *
 * EN: The WebView uses `MediaRecorder` on the camera/microphone track and sends
 * EN: the chunks in base64. The chunks are simply concatenated into the file:
 * EN: MediaRecorder already produces a complete container (MP4 in current
 * EN: Chromium, WebM as a fallback).
 */
class StudioRecorder(private val context: Context) {

    private var output: OutputStream? = null
    private var file: File? = null
    private var mime: String = ""

    @Synchronized
    fun start(mimeType: String): File? {
        stop()
        val extension = when {
            mimeType.contains("mp4", ignoreCase = true) -> "mp4"
            mimeType.contains("webm", ignoreCase = true) -> "webm"
            else -> "mp4"
        }
        return try {
            val dir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
            val name = "irl_vdo_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) +
                ".$extension"
            val target = File(dir, name)
            file = target
            mime = mimeType
            output = FileOutputStream(target)
            target
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao criar o ficheiro de gravação", error)
            file = null
            output = null
            null
        }
    }

    @Synchronized
    fun append(base64: String) {
        val stream = output ?: return
        runCatching {
            stream.write(Base64.decode(base64, Base64.DEFAULT))
            stream.flush()
        }.onFailure {
            Log.e(TAG, "Falha ao gravar bloco do estúdio", it)
        }
    }

    // PT: Fecha o ficheiro e devolve o caminho final, se existir.
    // EN: Closes the file and returns the final path, if it exists.
    @Synchronized
    fun stop(): File? {
        val stream = output
        output = null
        val finished = file
        file = null
        mime = ""
        runCatching {
            stream?.flush()
            stream?.close()
        }.onFailure {
            Log.w(TAG, "Falha ao fechar a gravação", it)
        }
        return finished?.takeIf { it.exists() && it.length() > 0 }
    }

    fun currentFile(): File? = file

    fun currentMime(): String = mime

    private companion object {
        const val TAG = "StudioRecorder"
    }
}
