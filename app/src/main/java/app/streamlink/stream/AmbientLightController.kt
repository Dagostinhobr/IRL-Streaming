package app.streamlink.stream

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.log10

/**
 * PT: Lê o sensor de luz ambiente e converte a leitura num valor de brilho.
 *
 * PT: O valor é suavizado para evitar que uma sombra passageira faça a imagem
 * PT: piscar. Ambientes escuros recebem brilho positivo; ambientes muito claros
 * PT: recebem brilho negativo, para não estourar os realces.
 *
 * EN: Reads the ambient light sensor and converts the reading into a brightness
 * EN: value. The value is smoothed so a passing shadow does not make the image
 * EN: flicker. Dark environments get positive brightness; very bright ones get
 * EN: negative brightness, to avoid blowing out the highlights.
 */
class AmbientLightController(
    context: Context,
    private val onBrightness: (Float) -> Unit
) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private var listening = false
    private var hasReading = false

    /**
     * PT: Último brilho calculado (pode ser lido a qualquer momento pela engine).
     * EN: Last computed brightness (can be read at any time by the engine).
     */
    @Volatile
    var currentBrightness: Float = 0f
        private set

    val isAvailable: Boolean
        get() = sensor != null

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val lux = event.values.firstOrNull()?.takeIf { it.isFinite() } ?: return
            val sample = lux.coerceIn(0.1f, 10_000f)
            val smoothed = if (hasReading) {
                smoothedLux * SMOOTHING + sample * (1f - SMOOTHING)
            } else {
                sample
            }
            smoothedLux = smoothed
            hasReading = true
            val target = brightnessForLux(smoothed)
            if (abs(target - currentBrightness) >= MIN_CHANGE) {
                currentBrightness = target
                onBrightness(target)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        val manager = sensorManager ?: return
        val light = sensor ?: return
        if (listening) return
        manager.registerListener(listener, light, SensorManager.SENSOR_DELAY_NORMAL)
        listening = true
    }

    fun stop() {
        if (!listening) return
        sensorManager?.unregisterListener(listener)
        listening = false
    }

    private companion object {
        // PT: Suavização exponencial: 75% do valor anterior + 25% da leitura.
        // EN: Exponential smoothing: 75% of the previous value + 25% of the reading.
        const val SMOOTHING = 0.75f

        // PT: Só avisa a engine quando o brilho muda o suficiente para se notar.
        // EN: Only notifies the engine when brightness changes enough to be seen.
        const val MIN_CHANGE = 0.01f

        /**
         * PT: Converte lux em brilho (-1 a 1) por interpolação logarítmica:
         * PT: ~1 lux → 0.45, ~1000 lux → -0.2.
         * EN: Converts lux into brightness (-1 to 1) with logarithmic
         * EN: interpolation: ~1 lux → 0.45, ~1000 lux → -0.2.
         */
        fun brightnessForLux(lux: Float): Float {
            val minLux = 1f
            val maxLux = 1_000f
            val clamped = lux.coerceIn(minLux, maxLux)
            val t = (log10(clamped) - log10(minLux)) / (log10(maxLux) - log10(minLux))
            return (0.45f - 0.65f * t).coerceIn(-0.35f, 0.45f)
        }
    }

    /**
     * PT: Última leitura suavizada, em lux.
     * EN: Last smoothed reading, in lux.
     */
    private var smoothedLux = 100f
}
