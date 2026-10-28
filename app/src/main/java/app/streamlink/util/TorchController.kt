package app.streamlink.util

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log

/**
 * PT: Lanterna para o modo em que a câmera é controlada pelo WebView (VDO.Ninja).
 *
 * PT: Quando o navegador já abriu a câmera traseira, o `setTorchMode` do
 * PT: CameraManager costuma continuar disponível. É usado como alternativa quando
 * PT: a faixa de vídeo do WebRTC não expõe a capacidade `torch`.
 *
 * EN: Torch for the mode where the camera is controlled by the WebView (VDO.Ninja).
 *
 * EN: When the browser has already opened the back camera, CameraManager's
 * EN: `setTorchMode` usually remains available. It is used as a fallback when
 * EN: the WebRTC video track does not expose the `torch` capability.
 */
class TorchController(context: Context) {

    private val manager = context.getSystemService(CameraManager::class.java)
    private var enabled = false

    fun setTorch(on: Boolean): Boolean {
        val cameraId = backCameraWithFlash() ?: return false
        return try {
            manager.setTorchMode(cameraId, on)
            enabled = on
            true
        } catch (error: CameraAccessException) {
            Log.w(TAG, "Lanterna indisponível", error)
            false
        } catch (error: Exception) {
            Log.w(TAG, "Falha ao controlar a lanterna", error)
            false
        }
    }

    fun isEnabled(): Boolean = enabled

    private fun backCameraWithFlash(): String? {
        return try {
            manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (error: Exception) {
            Log.w(TAG, "Não foi possível listar as câmeras", error)
            null
        }
    }

    private companion object {
        const val TAG = "TorchController"
    }
}
