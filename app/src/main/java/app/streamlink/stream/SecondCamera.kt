package app.streamlink.stream

import app.streamlink.i18n.L10n
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface

/**
 * PT: Segunda câmera usada na janela PiP.
 *
 * PT: Abre uma câmera independente do encoder (o RootEncoder cuida da principal) e
 * PT: envia os frames para a Surface fornecida pelo filtro de sobreposição. Exige
 * PT: que o aparelho suporte duas câmeras simultâneas.
 *
 * EN: Second camera used in the PiP window.
 *
 * EN: Opens a camera independent from the encoder (RootEncoder handles the main
 * EN: one) and sends the frames to the Surface provided by the overlay filter.
 * EN: Requires the device to support two cameras at the same time.
 */
class SecondCamera(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val thread = HandlerThread("PipCamera").apply { start() }
    private val handler = Handler(thread.looper)

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var runningId: String? = null
    private var closed = false

    // PT: Tamanho escolhido para a saída da segunda câmera (paisagem).
    // EN: Chosen output size for the second camera (landscape).
    var outputSize: Size? = null
        private set

    val isRunning: Boolean get() = session != null

    fun start(
        cameraId: String,
        surface: Surface,
        surfaceTexture: SurfaceTexture?,
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (runningId == cameraId && session != null) return
        stop()
        closed = false
        val manager = manager ?: run {
            onError(L10n.t("Serviço de câmera indisponível.", "Camera service unavailable."))
            return
        }
        val characteristics = runCatching {
            manager.getCameraCharacteristics(cameraId)
        }.getOrNull() ?: run {
            onError(L10n.t("Câmera $cameraId não encontrada.", "Camera $cameraId not found."))
            return
        }

        val size = chooseSize(characteristics)
        outputSize = size
        if (size != null) {
            runCatching { surfaceTexture?.setDefaultBufferSize(size.width, size.height) }
        }

        val callback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                if (closed) {
                    camera.close()
                    return
                }
                device = camera
                createSession(camera, surface, characteristics, onStarted, onError)
            }

            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                device = null
                session = null
                runningId = null
            }

            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                device = null
                session = null
                runningId = null
                val reason = when (error) {
                    ERROR_CAMERA_IN_USE -> L10n.t("A câmera já está em uso por outro app.", "The camera is already in use by another app.")
                    ERROR_MAX_CAMERAS_IN_USE -> L10n.t("O aparelho não permite duas câmeras ao mesmo tempo.", "The device does not allow two cameras at the same time.")
                    ERROR_CAMERA_DISABLED -> L10n.t("Câmera desativada pelo sistema.", "Camera disabled by the system.")
                    ERROR_CAMERA_DEVICE -> L10n.t("Falha na câmera.", "Camera failure.")
                    ERROR_CAMERA_SERVICE -> L10n.t("Serviço de câmera falhou.", "Camera service failed.")
                    else -> L10n.t("Erro $error ao abrir a câmera.", "Error $error opening the camera.")
                }
                Log.w(TAG, "Erro na câmera $cameraId: $reason")
                onError(reason)
            }
        }

        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError(L10n.t("Permissão de câmera não concedida.", "Camera permission not granted."))
            return
        }
        try {
            manager.openCamera(cameraId, callback, handler)
            runningId = cameraId
        } catch (t: Throwable) {
            Log.e(TAG, "Não foi possível abrir a câmera $cameraId", t)
            onError(t.message ?: L10n.t("Não foi possível abrir a segunda câmera.", "Could not open the second camera."))
        }
    }

    private fun createSession(
        camera: CameraDevice,
        surface: Surface,
        characteristics: CameraCharacteristics,
        onStarted: () -> Unit,
        onError: (String) -> Unit
    ) {
        val request = runCatching {
            camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                val afModes = characteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
                )
                if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) {
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
                }
                val awbModes = characteristics.get(
                    CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
                )
                if (awbModes?.contains(CaptureRequest.CONTROL_AWB_MODE_AUTO) == true) {
                    set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                }
            }
        }.getOrElse {
            onError(L10n.t("Não foi possível preparar a segunda câmera.", "Could not prepare the second camera."))
            return
        }

        @Suppress("DEPRECATION")
        val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(created: CameraCaptureSession) {
                if (closed) {
                    created.close()
                    return
                }
                session = created
                runCatching {
                    created.setRepeatingRequest(request.build(), null, handler)
                }.onSuccess {
                    onStarted()
                }.onFailure {
                    Log.e(TAG, "Falha ao iniciar a segunda câmera", it)
                    onError(L10n.t("Falha ao iniciar a segunda câmera.", "Failed to start the second camera."))
                }
            }

            override fun onConfigureFailed(created: CameraCaptureSession) {
                created.close()
                session = null
                onError(L10n.t("O aparelho recusou a segunda câmera.", "The device refused the second camera."))
            }
        }

        try {
            camera.createCaptureSession(listOf(surface), sessionCallback, handler)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao criar a sessão da segunda câmera", t)
            onError(L10n.t("Falha ao criar a sessão da segunda câmera.", "Failed to create the second camera session."))
        }
    }

    fun stop() {
        closed = true
        runCatching { session?.stopRepeating() }
        runCatching { session?.close() }
        runCatching { device?.close() }
        session = null
        device = null
        runningId = null
    }

    fun release() {
        stop()
        thread.quitSafely()
    }

    private fun chooseSize(characteristics: CameraCharacteristics): Size? {
        val map = characteristics.get(
            CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
        ) ?: return null
        val sizes = map.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
        if (sizes.isEmpty()) return null
        // PT: Prefere 16:9 em torno de 720p; evita resoluções gigantes na janelinha.
        // EN: Prefers 16:9 around 720p; avoids huge resolutions in the small window.
        return sizes
            .filter { it.width >= it.height }
            .minByOrNull { size ->
                val aspectPenalty = kotlin.math.abs(
                    size.width.toFloat() / size.height - 16f / 9f
                ) * 1000
                kotlin.math.abs(size.width - 1280) + kotlin.math.abs(size.height - 720) +
                    aspectPenalty.toInt()
            }
            ?: sizes.minByOrNull { it.width * it.height }
    }

    companion object {
        private const val TAG = "SecondCamera"
    }
}
