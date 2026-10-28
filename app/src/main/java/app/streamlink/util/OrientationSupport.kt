package app.streamlink.util

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import app.streamlink.data.VideoOrientation

/**
 * PT: Regras de orientação do vídeo.
 *
 * PT: - `AUTO`: segue o sensor, como Instagram/YouTube (vertical em pé, horizontal
 * PT:   deitado).
 * PT: - `PORTRAIT`/`LANDSCAPE`: bloqueiam a interface e o vídeo na orientação
 * PT:   escolhida.
 *
 * EN: Video orientation rules.
 *
 * EN: - `AUTO`: follows the sensor, like Instagram/YouTube (vertical upright,
 * EN:   horizontal when lying down).
 * EN: - `PORTRAIT`/`LANDSCAPE`: lock the interface and the video to the chosen
 * EN:   orientation.
 */
object OrientationSupport {

    fun isDeviceLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // PT: Converte o modo escolhido na orientação concreta usada pelo encoder.
    // EN: Converts the chosen mode into the concrete orientation used by the encoder.
    fun resolve(mode: VideoOrientation, deviceLandscape: Boolean): VideoOrientation =
        when (mode) {
            VideoOrientation.AUTO ->
                if (deviceLandscape) VideoOrientation.LANDSCAPE else VideoOrientation.PORTRAIT
            else -> mode
        }

    // PT: Orientação pedida à Activity para cada modo.
    // EN: Orientation requested from the Activity for each mode.
    fun requestedOrientation(mode: VideoOrientation): Int = when (mode) {
        VideoOrientation.AUTO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        VideoOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        VideoOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // PT: Próximo modo ao tocar no botão da tela principal.
    // EN: Next mode when tapping the button on the main screen.
    fun next(mode: VideoOrientation): VideoOrientation = when (mode) {
        VideoOrientation.AUTO -> VideoOrientation.PORTRAIT
        VideoOrientation.PORTRAIT -> VideoOrientation.LANDSCAPE
        VideoOrientation.LANDSCAPE -> VideoOrientation.AUTO
    }

    // PT: Descrição curta para o botão/ícone.
    // EN: Short description for the button/icon.
    fun shortLabel(mode: VideoOrientation): String = when (mode) {
        VideoOrientation.AUTO -> "Auto"
        VideoOrientation.PORTRAIT -> "Vertical"
        VideoOrientation.LANDSCAPE -> "Horizontal"
    }

    fun lockActivity(activity: Activity?, mode: VideoOrientation) {
        activity?.requestedOrientation = requestedOrientation(mode)
    }
}
