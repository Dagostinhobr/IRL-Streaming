package app.streamlink.stream

import android.content.Context
import android.graphics.Matrix
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.streamlink.data.UvcTransformSettings
import app.streamlink.data.VdoNinjaSettings
import app.streamlink.util.VdoNinja
import ninja.vdo.sdk.VdoNinja as VdoNinjaSdk
import ninja.vdo.sdk.core.Protocol
import ninja.vdo.sdk.core.VdoNinjaOptions
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.TextureBufferImpl
import org.webrtc.VideoFrame
import org.webrtc.VideoSource
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PT: Publica a câmara USB (UVC) no VDO.Ninja através do SDK nativo.
 *
 * PT: O WebView do VDO.Ninja só consegue abrir câmaras expostas pelo Camera2;
 * PT: uma câmara UVC não aparece no `getUserMedia`. Para manter a paridade com
 * PT: o modo P2P, os frames da câmara USB são entregues ao libwebrtc e o SDK
 * PT: oficial do VDO.Ninja anuncia o stream na mesma sala/ID.
 *
 * EN: Publishes the USB (UVC) camera to VDO.Ninja through the native SDK.
 *
 * EN: The VDO.Ninja WebView can only open cameras exposed by Camera2; a UVC
 * EN: camera does not appear in `getUserMedia`. To keep parity with P2P mode,
 * EN: USB camera frames are handed to libwebrtc and the official VDO.Ninja SDK
 * EN: announces the stream in the same room/ID.
 */
class VdoUsbPublisher(
    private val context: Context,
    private val uvc: UvcVideoSource,
    private val transformProvider: () -> UvcTransformSettings,
    private val onLive: () -> Unit,
    private val onError: (String) -> Unit
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, THREAD_NAME).apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)

    @Volatile private var vdo: VdoNinjaSdk? = null
    @Volatile private var factory: PeerConnectionFactory? = null
    @Volatile private var eglBase: EglBase? = null
    @Volatile private var videoSource: VideoSource? = null
    @Volatile private var videoTrack: VideoTrack? = null
    @Volatile private var audioSource: AudioSource? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var audioModule: JavaAudioDeviceModule? = null
    @Volatile private var muted = false

    val isActive: Boolean get() = active.get()

    fun start(vdoSettings: VdoNinjaSettings) {
        if (!active.compareAndSet(false, true)) return
        val streamId = VdoNinja.pushId(vdoSettings)
        if (streamId.isBlank()) {
            fail("Informe um ID de ligação ou uma sala do VDO.Ninja.")
            return
        }
        executor.execute {
            try {
                ensureFactoryInitialized(context)
                val egl = EglBase.create()
                eglBase = egl
                val createdFactory = createFactory(context, egl)
                factory = createdFactory

                // PT: O VDO.Ninja acrescenta um sufixo ao ID quando há palavra-passe.
                // PT: Sem palavra-passe configurada usamos a palavra-passe pública
                // PT: padrão, para o ID publicado coincidir com o link do espectador.
                // EN: VDO.Ninja appends a suffix to the ID when a password is set.
                // EN: Without a configured password we use the public default, so
                // EN: the published ID matches the viewer link.
                val password = vdoSettings.password.trim().ifBlank { Protocol.DEFAULT_PASSWORD }
                val sdk = VdoNinjaSdk(
                    context,
                    VdoNinjaOptions(
                        password = password,
                        noPassword = false,
                        label = vdoSettings.label.trim().ifBlank { null },
                        debug = false
                    ),
                    createdFactory,
                    egl
                )
                vdo = sdk
                sdk.connect()
                if (!active.get()) {
                    releaseInternal()
                    return@execute
                }

                val audio = createdFactory.createAudioSource(MediaConstraints())
                audioSource = audio
                val audioTrackCreated = createdFactory.createAudioTrack("vdo-audio", audio)
                audioTrack = audioTrackCreated
                audioTrackCreated.setEnabled(!muted)

                val video = createdFactory.createVideoSource(false)
                videoSource = video
                val videoTrackCreated = createdFactory.createVideoTrack("vdo-video", video)
                videoTrack = videoTrackCreated

                val sink = VideoSink { frame -> deliverFrame(video, frame) }
                val attached = CompletableFuture<Boolean>()
                mainHandler.post {
                    attached.complete(uvc.attachWebRtcSink(egl.eglBaseContext, sink, currentRotation()))
                }
                if (!attached.get(5, TimeUnit.SECONDS)) {
                    throw IllegalStateException("Não foi possível anexar a câmara USB ao WebRTC.")
                }
                video.capturerObserver.onCapturerStarted(true)

                if (!active.get()) {
                    releaseInternal()
                    return@execute
                }
                sdk.publish(
                    tracks = listOf(audioTrackCreated, videoTrackCreated),
                    streamId = streamId,
                    room = vdoSettings.room.trim().ifBlank { null }
                )
                if (active.get()) mainHandler.post { if (active.get()) onLive() }
            } catch (error: Exception) {
                Log.e(TAG, "Falha ao publicar câmara USB no VDO.Ninja", error)
                fail(error.message ?: "Não foi possível publicar a câmara USB no VDO.Ninja.")
            }
        }
    }

    fun setMuted(value: Boolean) {
        muted = value
        runCatching { audioTrack?.setEnabled(!value) }
    }

    fun updateTransform() {
        mainHandler.post {
            runCatching { uvc.updateWebRtcRotation(currentRotation()) }
        }
    }

    fun stop() {
        active.set(false)
        runOnPublisher { releaseInternal() }
    }

    private fun fail(message: String) {
        active.set(false)
        runOnPublisher {
            releaseInternal()
            mainHandler.post { onError(message) }
        }
    }

    /**
     * PT: Liberta o SDK, as tracks e o contexto EGL. Corre sempre na thread do
     * PT: publicador para não bloquear a UI e para o EGL ser libertado depois de
     * PT: a textura WebRTC da câmara USB ser removida.
     *
     * EN: Releases the SDK, tracks and EGL context. Always runs on the publisher
     * EN: thread so the UI is not blocked and EGL is only released after the USB
     * EN: camera's WebRTC texture is detached.
     */
    private fun releaseInternal() {
        val sdk = vdo
        val currentVideo = videoSource
        runCatching { sdk?.stopPublishing() }
        runCatching { sdk?.dispose() }
        // PT: A textura foi criada na main thread; remove-a lá e espera, para o
        // PT: EGL ainda estar válido quando a SurfaceTextureHelper for libertada.
        // EN: The texture was created on the main thread; detach it there and
        // EN: wait, so EGL is still valid when SurfaceTextureHelper is disposed.
        val detached = java.util.concurrent.CountDownLatch(1)
        mainHandler.post {
            runCatching { uvc.detachWebRtcSink() }
            detached.countDown()
        }
        runCatching { detached.await(2, TimeUnit.SECONDS) }
        runCatching { currentVideo?.capturerObserver?.onCapturerStopped() }
        runCatching { videoTrack?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { currentVideo?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { audioModule?.release() }
        runCatching { eglBase?.release() }
        videoSource = null
        videoTrack = null
        audioSource = null
        audioTrack = null
        audioModule = null
        factory = null
        eglBase = null
        vdo = null
        executor.shutdown()
    }

    private fun runOnPublisher(block: () -> Unit) {
        if (Thread.currentThread().name == THREAD_NAME) {
            block()
        } else {
            runCatching { executor.execute(block) }
                .onFailure { executor.shutdown() }
        }
    }

    private fun deliverFrame(source: VideoSource, frame: VideoFrame) {
        if (!active.get()) return
        val transform = transformProvider()
        var forwarded = frame
        var transformedFrame: VideoFrame? = null
        if ((transform.flipHorizontal || transform.flipVertical) && frame.buffer is TextureBufferImpl) {
            runCatching {
                val buffer = frame.buffer as TextureBufferImpl
                val matrix = Matrix()
                matrix.postScale(
                    if (transform.flipHorizontal) -1f else 1f,
                    if (transform.flipVertical) -1f else 1f,
                    buffer.width / 2f,
                    buffer.height / 2f
                )
                val transformed = buffer.applyTransformMatrix(matrix, buffer.width, buffer.height)
                val newFrame = VideoFrame(transformed, frame.rotation, frame.timestampNs)
                transformedFrame = newFrame
                forwarded = newFrame
            }.onFailure { Log.w(TAG, "Falha ao espelhar frame USB", it) }
        }
        runCatching { source.capturerObserver.onFrameCaptured(forwarded) }
            .onFailure { Log.w(TAG, "Falha ao entregar frame USB ao WebRTC", it) }
        transformedFrame?.release()
    }

    private fun currentRotation(): Int =
        ((transformProvider().rotation % 360) + 360) % 360

    private fun createFactory(context: Context, egl: EglBase): PeerConnectionFactory {
        val audioModule = JavaAudioDeviceModule.builder(context)
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        this.audioModule = audioModule
        return PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    companion object {
        private const val TAG = "VdoUsbPublisher"
        private const val THREAD_NAME = "vdo-usb-publisher"
        private val initialized = AtomicBoolean(false)

        private fun ensureFactoryInitialized(context: Context) {
            if (!initialized.compareAndSet(false, true)) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                    .createInitializationOptions()
            )
        }
    }
}
