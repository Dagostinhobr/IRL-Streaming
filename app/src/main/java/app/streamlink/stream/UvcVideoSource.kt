package app.streamlink.stream

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Looper
import android.util.Log
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.pedro.encoder.input.sources.video.VideoSource
import com.serenegiant.usb.Size
import org.webrtc.EglBase
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import kotlin.math.abs

/**
 * PT: Fonte de vídeo para câmeras USB UVC, compatível com o RootEncoder.
 *
 * PT: O RootEncoder entrega um [SurfaceTexture] ao iniciar a pré-visualização. A
 * PT: biblioteca UVC desenha os frames diretamente nessa textura, pelo que a
 * PT: câmera USB passa pelo mesmo pipeline OpenGL/encoder das câmeras nativas.
 *
 * PT: A permissão de USB é pedida automaticamente pelo Android quando o
 * PT: dispositivo é selecionado pela primeira vez.
 *
 * EN: Video source for UVC USB cameras, compatible with RootEncoder.
 *
 * EN: RootEncoder provides a [SurfaceTexture] when starting the preview. The
 * EN: UVC library draws the frames directly into that texture, so the
 * EN: USB camera goes through the same OpenGL/encoder pipeline as native cameras.
 *
 * EN: USB permission is requested automatically by Android when the
 * EN: device is selected for the first time.
 */
class UvcVideoSource(
    context: Context,
    private val wantedDeviceName: String
) : VideoSource() {

    private val helper = CameraHelper()
    private var uvcTexture: SurfaceTexture? = null
    private var selectedDevice: UsbDevice? = null
    private var previewWanted = false
    private var cameraRunning = false
    private var targetWidth = 1280
    private var targetHeight = 720

    // PT: Segunda textura usada para publicar a câmara USB no VDO.Ninja nativo.
    // PT: A libUVCCamera desenha o mesmo frame na textura do preview e nesta,
    // PT: sem abrir a câmara duas vezes.
    // EN: Second texture used to publish the USB camera to native VDO.Ninja.
    // EN: libUVCCamera draws the same frame into the preview texture and this
    // EN: one, without opening the camera twice.
    private var webrtcHelper: SurfaceTextureHelper? = null
    private var webrtcTexture: SurfaceTexture? = null
    private var webrtcSink: VideoSink? = null
    private var webrtcSurfaceAdded = false

    // PT: Brilho e contraste pedidos pela app (aplicados nos controlos UVC).
    // EN: Brightness and contrast requested by the app (applied to UVC controls).
    @Volatile private var imageBrightness = 0f
    @Volatile private var imageContrast = 1f
    @Volatile private var appliedBrightnessPercent = Int.MIN_VALUE
    @Volatile private var appliedContrastPercent = Int.MIN_VALUE

    init {
        helper.setStateCallback(object : ICameraHelper.StateCallback {
            override fun onAttach(device: UsbDevice) {
                if (matches(device) && selectedDevice == null) {
                    select(device)
                }
            }

            override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
                if (matches(device)) {
                    openCamera(device)
                }
            }

            override fun onCameraOpen(device: UsbDevice) {
                Log.i(TAG, "Câmera USB aberta: ${device.productName}")
                cameraRunning = true
                applyImageControls()
                if (previewWanted) startUvcPreview()
            }

            override fun onCameraClose(device: UsbDevice) {
                cameraRunning = false
            }

            override fun onDeviceClose(device: UsbDevice) {
                cameraRunning = false
            }

            override fun onDetach(device: UsbDevice) {
                if (matches(device)) {
                    cameraRunning = false
                    selectedDevice = null
                }
            }

            override fun onCancel(device: UsbDevice) {
                Log.w(TAG, "Permissão USB recusada para ${device.productName}")
            }

            override fun onError(device: UsbDevice, e: CameraException) {
                Log.e(TAG, "Erro na câmera USB: ${e.message}", e)
            }
        })

        // PT: Se a câmera já estava ligada quando o app abriu, seleciona-a já.
        // EN: If the camera was already on when the app opened, selects it right away.
        bestDevice()?.let { select(it) }
    }

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        targetWidth = width
        targetHeight = height
        bestDevice()?.let { select(it) }
        // PT: A abertura real acontece de forma assíncrona, depois da permissão USB.
        // EN: The actual opening happens asynchronously, after USB permission.
        return true
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        uvcTexture = surfaceTexture
        previewWanted = true
        if (cameraRunning) {
            startUvcPreview()
        } else {
            bestDevice()?.let { select(it) }
        }
    }

    override fun stop() {
        previewWanted = false
        uvcTexture?.let { texture ->
            runCatching { helper.removeSurface(texture) }
        }
        removeWebRtcSurface()
        runCatching { helper.stopPreview() }
        uvcTexture = null
        // PT: O dispositivo USB fica aberto para o preview poder voltar sem
        // PT: renegociar a câmara (ex.: ao regressar dos Ajustes). A câmara só é
        // PT: realmente fechada em [release].
        // EN: The USB device stays open so the preview can return without
        // EN: renegotiating the camera (e.g. coming back from Settings). The
        // EN: camera is only really closed in [release].
    }

    override fun release() {
        previewWanted = false
        cameraRunning = false
        uvcTexture = null
        detachWebRtcSink()
        runCatching { helper.release() }
    }

    override fun isRunning(): Boolean = cameraRunning && previewWanted

    /**
     * PT: Liga uma segunda saída de vídeo (WebRTC) à câmara USB. Deve ser
     * PT: chamada na main thread porque cria uma SurfaceTextureHelper.
     *
     * EN: Attaches a second (WebRTC) video output to the USB camera. Must be
     * EN: called on the main thread because it creates a SurfaceTextureHelper.
     */
    fun attachWebRtcSink(eglContext: EglBase.Context, sink: VideoSink, rotation: Int): Boolean {
        if (Looper.myLooper() == null) return false
        if (webrtcSink != null) {
            updateWebRtcRotation(rotation)
            return true
        }
        val textureHelper = SurfaceTextureHelper.create("uvc-vdoninja", eglContext)
        textureHelper.setTextureSize(targetWidth, targetHeight)
        textureHelper.setFrameRotation(rotation)
        textureHelper.startListening(sink)
        webrtcHelper = textureHelper
        webrtcTexture = textureHelper.surfaceTexture
        webrtcSink = sink
        if (cameraRunning) addWebRtcSurface()
        return true
    }

    fun updateWebRtcRotation(rotation: Int) {
        runCatching { webrtcHelper?.setFrameRotation(rotation) }
    }

    fun detachWebRtcSink() {
        removeWebRtcSurface()
        runCatching { webrtcHelper?.stopListening() }
        runCatching { webrtcHelper?.dispose() }
        webrtcHelper = null
        webrtcTexture = null
        webrtcSink = null
    }

    private fun addWebRtcSurface() {
        if (webrtcSurfaceAdded) return
        val texture = webrtcTexture ?: return
        runCatching { helper.addSurface(texture, false) }
            .onSuccess { webrtcSurfaceAdded = true }
    }

    private fun removeWebRtcSurface() {
        if (!webrtcSurfaceAdded) return
        webrtcTexture?.let { texture -> runCatching { helper.removeSurface(texture) } }
        webrtcSurfaceAdded = false
    }

    /**
     * PT: Aplica brilho e contraste nos controlos de hardware da câmara UVC.
     * PT: Assim o ajuste chega ao preview, ao RTMP e ao WebRTC nativo, porque
     * PT: todos usam os mesmos frames da câmara. Valores neutros repõem o
     * PT: controlo, para não alterar a imagem sem o utilizador pedir.
     *
     * EN: Applies brightness and contrast to the UVC camera's hardware
     * EN: controls. This reaches the preview, RTMP and native WebRTC, since all
     * EN: of them use the same camera frames. Neutral values reset the control,
     * EN: so the image is not changed unless the user asks for it.
     */
    fun applyImageAdjustment(brightness: Float, contrast: Float) {
        imageBrightness = brightness.coerceIn(-1f, 1f)
        imageContrast = contrast.coerceIn(0.5f, 1.5f)
        if (cameraRunning) applyImageControls()
    }

    private fun applyImageControls() {
        runCatching {
            val control = helper.getUVCControl() ?: return
            if (control.isBrightnessEnable()) {
                if (abs(imageBrightness) < 0.01f) {
                    if (appliedBrightnessPercent != NEUTRAL) {
                        control.resetBrightness()
                        appliedBrightnessPercent = NEUTRAL
                    }
                } else {
                    val percent = (50f + imageBrightness * 50f).toInt().coerceIn(0, 100)
                    if (appliedBrightnessPercent == NEUTRAL ||
                        abs(percent - appliedBrightnessPercent) >= CONTROL_STEP
                    ) {
                        control.setBrightnessPercent(percent)
                        appliedBrightnessPercent = percent
                    }
                }
            }
            if (control.isContrastEnable()) {
                if (abs(imageContrast - 1f) < 0.01f) {
                    if (appliedContrastPercent != NEUTRAL) {
                        control.resetContrast()
                        appliedContrastPercent = NEUTRAL
                    }
                } else {
                    val percent = ((imageContrast - 0.5f) * 100f).toInt().coerceIn(0, 100)
                    if (appliedContrastPercent == NEUTRAL ||
                        abs(percent - appliedContrastPercent) >= CONTROL_STEP
                    ) {
                        control.setContrastPercent(percent)
                        appliedContrastPercent = percent
                    }
                }
            }
        }.onFailure { Log.w(TAG, "Falha ao aplicar brilho/contraste UVC", it) }
    }

    private fun select(device: UsbDevice) {
        selectedDevice = device
        runCatching { helper.selectDevice(device) }
            .onFailure { Log.w(TAG, "Falha ao selecionar câmera USB", it) }
    }

    private fun openCamera(device: UsbDevice) {
        runCatching {
            val size = bestSize(helper.supportedSizeList)
            if (size != null) {
                targetWidth = size.width
                targetHeight = size.height
                webrtcHelper?.setTextureSize(size.width, size.height)
                helper.openCamera(size)
            } else {
                helper.openCamera()
            }
        }.onFailure { Log.w(TAG, "Falha ao abrir câmera USB", it) }
    }

    private fun startUvcPreview() {
        if (!cameraRunning) return
        runCatching {
            helper.startPreview()
            uvcTexture?.let { texture -> helper.addSurface(texture, false) }
            addWebRtcSurface()
        }.onFailure { Log.w(TAG, "Falha ao iniciar preview USB", it) }
    }

    /**
     * PT: Escolhe a resolução UVC mais próxima da resolução pedida pelo encoder,
     * PT: dando preferência a formatos com pelo menos a mesma altura.
     *
     * EN: Chooses the UVC resolution closest to the resolution requested by the encoder,
     * EN: preferring formats with at least the same height.
     */
    private fun bestSize(sizes: List<Size>?): Size? {
        val available = sizes?.filter { it.width > 0 && it.height > 0 } ?: return null
        if (available.isEmpty()) return null
        val targetPixels = targetWidth.toLong() * targetHeight
        return available.minByOrNull { size ->
            abs(size.width.toLong() * size.height - targetPixels) +
                if (size.width >= targetWidth && size.height >= targetHeight) 0 else targetPixels / 4
        }
    }

    private fun bestDevice(): UsbDevice? {
        val devices = helper.deviceList ?: return null
        if (devices.isEmpty()) return null
        return devices.firstOrNull { matches(it) } ?: devices.firstOrNull()
    }

    private fun matches(device: UsbDevice): Boolean {
        if (wantedDeviceName.isBlank()) return true
        return device.deviceName == wantedDeviceName ||
            device.productName == wantedDeviceName
    }

    companion object {
        private const val TAG = "UvcVideoSource"
        // PT: Marca "controlo reposto" e passo mínimo entre comandos USB.
        // EN: "Control reset" marker and minimum step between USB commands.
        private const val NEUTRAL = Int.MIN_VALUE
        private const val CONTROL_STEP = 2
    }
}
