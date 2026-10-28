package app.streamlink.stream

import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.util.Log
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.CustomAudioEffect
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.sources.audio.AudioSource
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Microfone do RootEncoder com medição de nível para o VU meter.
 *
 * O [MicrophoneSource] original não expõe a amplitude do PCM, por isso esta
 * classe embrulha o microfone, mede cada bloco em [inputPCMData] e repassa o
 * frame intacto para o encoder — o áudio transmitido não é alterado.
 *
 * O monitoramento pode começar antes da transmissão (durante o preview), para
 * que o medidor funcione no ecrã principal sem estar no ar. Quando o encoder
 * chama [start], apenas ligamos o callback; o `AudioRecord` não é recriado.
 */
class MeteredMicrophoneSource(
    audioSource: Int = MediaRecorder.AudioSource.DEFAULT
) : AudioSource(), GetMicrophoneData {

    private val microphone = MicrophoneSource(audioSource)

    /** Maior nível (0..1) observado desde a última leitura pela interface. */
    @Volatile
    private var peakSinceRead: Float = 0f

    /** RMS do último bloco, usado apenas como fallback de leitura. */
    @Volatile
    private var lastRms: Float = 0f

    /**
     * Prepara o `AudioRecord` com os parâmetros do encoder. Se o microfone já
     * estiver em execução (monitoramento do preview), ele é liberado antes de
     * recriar, evitando dois `AudioRecord` simultâneos.
     */
    override fun create(
        sampleRate: Int,
        isStereo: Boolean,
        echoCanceler: Boolean,
        noiseSuppressor: Boolean
    ): Boolean {
        if (microphone.isRunning()) {
            runCatching { microphone.stop() }
        }
        return microphone.init(sampleRate, isStereo, echoCanceler, noiseSuppressor)
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        startCapture()
    }

    /**
     * Liga a captura para alimentar o VU meter mesmo sem transmissão ativa.
     * O encoder só recebe os frames depois que [start] define o callback.
     */
    fun startMonitoring() {
        startCapture()
    }

    /**
     * O encoder parou de consumir áudio (fim da transmissão/gravação), mas o
     * medidor continua a funcionar no preview: apenas soltamos o callback.
     */
    override fun stop() {
        getMicrophoneData = null
        peakSinceRead = 0f
        lastRms = 0f
    }

    /** Encerra a captura de facto (release do encoder / saída da tela). */
    fun stopMonitoring() {
        getMicrophoneData = null
        peakSinceRead = 0f
        lastRms = 0f
        runCatching { microphone.stop() }
    }

    override fun isRunning(): Boolean = microphone.isRunning()

    override fun release() {
        getMicrophoneData = null
        peakSinceRead = 0f
        lastRms = 0f
        runCatching { microphone.release() }
        runCatching { microphone.stop() }
    }

    override fun inputPCMData(frame: Frame) {
        measure(frame)
        getMicrophoneData?.inputPCMData(frame)
    }

    /** Devolve o pico desde a última chamada e zera os acumuladores. */
    fun consumePeak(): Float {
        val value = maxOf(peakSinceRead, lastRms)
        peakSinceRead = 0f
        lastRms = 0f
        return value
    }

    fun setPreferredDevice(deviceInfo: AudioDeviceInfo?): Boolean =
        microphone.setPreferredDevice(deviceInfo)

    fun mute() = microphone.mute()

    fun unMute() = microphone.unMute()

    fun isMuted(): Boolean = microphone.isMuted()

    fun setAudioEffect(effect: CustomAudioEffect) = microphone.setAudioEffect(effect)

    var microphoneVolume: Float
        get() = microphone.microphoneVolume
        set(value) {
            microphone.microphoneVolume = value
        }

    private fun startCapture() {
        if (microphone.isRunning()) return
        runCatching { microphone.start(this) }
            .onFailure { Log.w(TAG, "Não foi possível iniciar o microfone para o VU meter", it) }
    }

    /**
     * PCM de 16 bits little-endian. Calcula pico e RMS normalizados (0..1).
     * O pico dá resposta imediata; o RMS evita que o medidor fique "colado".
     */
    private fun measure(frame: Frame) {
        val buffer = frame.buffer
        if (buffer.isEmpty()) return
        val start = frame.offset.coerceIn(0, buffer.size)
        val end = (frame.offset + frame.size).coerceIn(start, buffer.size)

        var peak = 0
        var sumSquares = 0.0
        var samples = 0
        var index = start
        while (index + 1 < end) {
            val low = buffer[index].toInt() and 0xFF
            val high = buffer[index + 1].toInt()
            val sample = (high shl 8) or low
            val magnitude = abs(sample)
            if (magnitude > peak) peak = magnitude
            sumSquares += sample.toDouble() * sample
            samples++
            index += 2
        }
        if (samples == 0) return

        val rms = (sqrt(sumSquares / samples) / 32768.0).toFloat().coerceIn(0f, 1f)
        val peakLevel = (peak / 32768f).coerceIn(0f, 1f)
        lastRms = rms
        if (peakLevel > peakSinceRead) peakSinceRead = peakLevel
    }

    private companion object {
        const val TAG = "MeteredMicrophone"
    }
}
