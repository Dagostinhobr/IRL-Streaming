package app.streamlink.stream

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
 * Segunda câmera usada na janela PiP.
 *
 * Abre uma câmera independente do encoder (o RootEncoder cuida da principal) e
 * envia os frames para a Surface fornecida pelo filtro de sobreposição. Exige
 * que o aparelho suporte duas câmeras simultâneas.
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

    /** Tamanho escolhido para a saída da segunda câmera (paisagem). */
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
            onError("Serviço de câmera indisponível.")
            return
        }
        val characteristics = runCatching {
            manager.getCameraCharacteristics(cameraId)
        }.getOrNull() ?: run {
            onError("Câmera $cameraId não encontrada.")
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
                    ERROR_CAMERA_IN_USE -> "A câmera já está em uso por outro app."
                    ERROR_MAX_CAMERAS_IN_USE -> "O aparelho não permite duas câmeras ao mesmo tempo."
                    ERROR_CAMERA_DISABLED -> "Câmera desativada pelo sistema."
                    ERROR_CAMERA_DEVICE -> "Falha na câmera."
                    ERROR_CAMERA_SERVICE -> "Serviço de câmera falhou."
                    else -> "Erro $error ao abrir a câmera."
                }
                Log.w(TAG, "Erro na câmera $cameraId: $reason")
                onError(reason)
            }
        }

        if (appContext.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            onError("Permissão de câmera não concedida.")
            return
        }
        try {
            manager.openCamera(cameraId, callback, handler)
            runningId = cameraId
        } catch (t: Throwable) {
            Log.e(TAG, "Não foi possível abrir a câmera $cameraId", t)
            onError(t.message ?: "Não foi possível abrir a segunda câmera.")
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
            onError("Não foi possível preparar a segunda câmera.")
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
                    onError("Falha ao iniciar a segunda câmera.")
                }
            }

            override fun onConfigureFailed(created: CameraCaptureSession) {
                created.close()
                session = null
                onError("O aparelho recusou a segunda câmera.")
            }
        }

        try {
            camera.createCaptureSession(listOf(surface), sessionCallback, handler)
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao criar a sessão da segunda câmera", t)
            onError("Falha ao criar a sessão da segunda câmera.")
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
        // Prefere 16:9 em torno de 720p; evita resoluções gigantes na janelinha.
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
