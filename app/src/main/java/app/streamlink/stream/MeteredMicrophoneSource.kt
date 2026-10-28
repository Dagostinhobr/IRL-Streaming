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
 * PT: Microfone do RootEncoder com medição de nível para o VU meter.
 *
 * PT: O [MicrophoneSource] original não expõe a amplitude do PCM, por isso esta
 * PT: classe embrulha o microfone, mede cada bloco em [inputPCMData] e repassa o
 * PT: frame intacto para o encoder — o áudio transmitido não é alterado.
 *
 * PT: O monitoramento pode começar antes da transmissão (durante o preview), para
 * PT: que o medidor funcione no ecrã principal sem estar no ar. Quando o encoder
 * PT: chama [start], apenas ligamos o callback; o `AudioRecord` não é recriado.
 *
 * EN: RootEncoder microphone with level measurement for the VU meter.
 *
 * EN: The original [MicrophoneSource] does not expose the PCM amplitude, so this
 * EN: class wraps the microphone, measures each block in [inputPCMData] and passes the
 * EN: frame through intact to the encoder — the transmitted audio is not changed.
 *
 * EN: Monitoring can start before streaming (during preview), so
 * EN: the meter works on the main screen while not on air. When the encoder
 * EN: calls [start], we only attach the callback; the `AudioRecord` is not recreated.
 */
class MeteredMicrophoneSource(
    audioSource: Int = MediaRecorder.AudioSource.DEFAULT
) : AudioSource(), GetMicrophoneData {

    private val microphone = MicrophoneSource(audioSource)

    /**
     * PT: Maior nível (0..1) observado desde a última leitura pela interface.
     * EN: Highest level (0..1) observed since the last read by the UI.
     */
    @Volatile
    private var peakSinceRead: Float = 0f

    /**
     * PT: RMS do último bloco, usado apenas como fallback de leitura.
     * EN: RMS of the last block, used only as a read fallback.
     */
    @Volatile
    private var lastRms: Float = 0f

    /**
     * PT: Prepara o `AudioRecord` com os parâmetros do encoder. Se o microfone já
     * PT: estiver em execução (monitoramento do preview), ele é liberado antes de
     * PT: recriar, evitando dois `AudioRecord` simultâneos.
     *
     * EN: Prepares the `AudioRecord` with the encoder parameters. If the microphone is
     * EN: already running (preview monitoring), it is released before
     * EN: recreating, avoiding two simultaneous `AudioRecord` instances.
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
     * PT: Liga a captura para alimentar o VU meter mesmo sem transmissão ativa.
     * PT: O encoder só recebe os frames depois que [start] define o callback.
     *
     * EN: Starts capture to feed the VU meter even without an active stream.
     * EN: The encoder only receives frames after [start] sets the callback.
     */
    fun startMonitoring() {
        startCapture()
    }

    /**
     * PT: O encoder parou de consumir áudio (fim da transmissão/gravação), mas o
     * PT: medidor continua a funcionar no preview: apenas soltamos o callback.
     *
     * EN: The encoder stopped consuming audio (end of stream/recording), but the
     * EN: meter keeps working in preview: we only detach the callback.
     */
    override fun stop() {
        getMicrophoneData = null
        peakSinceRead = 0f
        lastRms = 0f
    }

    /**
     * PT: Encerra a captura de facto (release do encoder / saída da tela).
     * EN: Actually stops capture (encoder release / leaving the screen).
     */
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

    /**
     * PT: Devolve o pico desde a última chamada e zera os acumuladores.
     * EN: Returns the peak since the last call and resets the accumulators.
     */
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
     * PT: PCM de 16 bits little-endian. Calcula pico e RMS normalizados (0..1).
     * PT: O pico dá resposta imediata; o RMS evita que o medidor fique "colado".
     *
     * EN: 16-bit little-endian PCM. Computes normalized peak and RMS (0..1).
     * EN: The peak gives an immediate response; the RMS keeps the meter from getting "stuck".
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
