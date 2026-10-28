package app.streamlink.util

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import app.streamlink.data.VideoOrientation

/**
 * Regras de orientação do vídeo.
 *
 * - `AUTO`: segue o sensor, como Instagram/YouTube (vertical em pé, horizontal
 *   deitado).
 * - `PORTRAIT`/`LANDSCAPE`: bloqueiam a interface e o vídeo na orientação
 *   escolhida.
 */
object OrientationSupport {

    fun isDeviceLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** Converte o modo escolhido na orientação concreta usada pelo encoder. */
    fun resolve(mode: VideoOrientation, deviceLandscape: Boolean): VideoOrientation =
        when (mode) {
            VideoOrientation.AUTO ->
                if (deviceLandscape) VideoOrientation.LANDSCAPE else VideoOrientation.PORTRAIT
            else -> mode
        }

    /** Orientação pedida à Activity para cada modo. */
    fun requestedOrientation(mode: VideoOrientation): Int = when (mode) {
        VideoOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    /** Próximo modo ao tocar no botão da tela principal. */
    fun next(mode: VideoOrientation): VideoOrientation = when (mode) {
        VideoOrientation.AUTO -> VideoOrientation.PORTRAIT
        VideoOrientation.PORTRAIT -> VideoOrientation.LANDSCAPE
        VideoOrientation.LANDSCAPE -> VideoOrientation.AUTO
    }

    /** Descrição curta para o botão/ícone. */
    fun shortLabel(mode: VideoOrientation): String = when (mode) {
        VideoOrientation.AUTO -> "Auto"
        VideoOrientation.PORTRAIT -> "Vertical"
        VideoOrientation.LANDSCAPE -> "Horizontal"
    }

    fun lockActivity(activity: Activity?, mode: VideoOrientation) {
        activity?.requestedOrientation = requestedOrientation(mode)
    }
}
