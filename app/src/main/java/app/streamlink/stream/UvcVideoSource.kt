package app.streamlink.stream

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.pedro.encoder.input.sources.video.VideoSource
import com.serenegiant.usb.Size
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
        cameraRunning = false
        uvcTexture?.let { texture ->
            runCatching { helper.removeSurface(texture) }
        }
        runCatching { helper.stopPreview() }
        uvcTexture = null
    }

    override fun release() {
        previewWanted = false
        cameraRunning = false
        uvcTexture = null
        runCatching { helper.release() }
    }

    override fun isRunning(): Boolean = cameraRunning && previewWanted

    private fun select(device: UsbDevice) {
        selectedDevice = device
        runCatching { helper.selectDevice(device) }
            .onFailure { Log.w(TAG, "Falha ao selecionar câmera USB", it) }
    }

    private fun openCamera(device: UsbDevice) {
        runCatching {
            val size = bestSize(helper.supportedSizeList)
            if (size != null) {
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
    }
}
