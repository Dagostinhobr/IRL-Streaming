package app.streamlink.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import app.streamlink.i18n.L10n
import android.util.Log
import java.util.Locale

/**
 * PT: Capacidades de câmera relevantes para o modo PiP (duas câmeras ao mesmo tempo).
 *
 * PT: O Android só garante duas câmeras simultâneas quando o aparelho anuncia a
 * PT: combinação em [CameraManager.getConcurrentCameraIds] (API 30+).
 *
 * EN: Camera capabilities relevant to PiP mode (two cameras at the same time).
 *
 * EN: Android only guarantees two simultaneous cameras when the device advertises
 * EN: the combination in [CameraManager.getConcurrentCameraIds] (API 30+).
 */
object CameraCapabilities {

    private const val TAG = "CameraCapabilities"

    data class CameraInfo(
        val id: String,
        val facing: Int,
        val label: String,
        val detail: String,
        /**
         * PT: Câmera USB (UVC), que não é aberta pelo Camera2 nativo.
         * EN: USB (UVC) camera, which is not opened by native Camera2.
         */
        val usb: Boolean = false,
        /**
         * PT: Nome do dispositivo USB, usado para localizar a câmera UVC.
         * EN: USB device name, used to locate the UVC camera.
         */
        val deviceName: String = ""
    ) {
        val isFront: Boolean get() = facing == CameraCharacteristics.LENS_FACING_FRONT
    }

    /**
     * PT: Câmeras USB UVC ligadas por OTG, reconhecidas pelo host Android.
     * EN: UVC USB cameras connected via OTG, recognized by the Android host.
     */
    fun usbCameras(context: Context): List<CameraInfo> {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return runCatching {
            manager.deviceList.values
                .filter { it.isVideoCamera() }
                .sortedBy { it.deviceName }
                .map { device ->
                    val name = cleanUsbName(device.productName)
                        ?: cleanUsbName(device.manufacturerName)
                        ?: L10n.t("Dispositivo UVC", "UVC device")
                    CameraInfo(
                        id = USB_PREFIX + device.deviceName,
                        facing = CameraCharacteristics.LENS_FACING_EXTERNAL,
                        label = L10n.t("Câmera USB", "USB camera"),
                        detail = "$name · USB",
                        usb = true,
                        deviceName = device.deviceName
                    )
                }
        }.getOrDefault(emptyList())
    }

    /**
     * PT: Câmeras nativas + USB para a seleção da câmera principal.
     * EN: Native + USB cameras for selecting the main camera.
     */
    fun selectableCameras(context: Context): List<CameraInfo> =
        cameras(context) + usbCameras(context)

    /**
     * PT: Alguns aparelhos devolvem `\u0000` no nome do produto USB.
     * EN: Some devices return `\u0000` in the USB product name.
     */
    private fun cleanUsbName(value: String?): String? = value
        ?.filter { !it.isISOControl() }
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    /**
     * PT: Uma interface de vídeo (UVC) é identificada pela classe USB 14. Alguns
     * PT: dispositivos expõem apenas a interface de streaming; por isso percorremos
     * PT: todas as interfaces.
     *
     * EN: A video interface (UVC) is identified by USB class 14. Some
     * EN: devices expose only the streaming interface; therefore we traverse
     * EN: all interfaces.
     */
    private fun UsbDevice.isVideoCamera(): Boolean = runCatching {
        (0 until interfaceCount).any { index ->
            val iface = getInterface(index)
            iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO
        }
    }.getOrDefault(false)

    fun cameras(context: Context): List<CameraInfo> {
        val manager = manager(context) ?: return emptyList()
        val backCount = manager.cameraIdList.count { id ->
            runCatching {
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
            }.getOrNull() == CameraCharacteristics.LENS_FACING_BACK
        }
        var backIndex = 0
        return runCatching {
            manager.cameraIdList.mapNotNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: -1
                val focal = characteristics
                    .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    ?.firstOrNull()
                val base = when (facing) {
                    CameraCharacteristics.LENS_FACING_BACK ->
                        if (backCount > 1) L10n.t("Traseira ${++backIndex}", "Rear ${backIndex}") else L10n.t("Traseira", "Rear")
                    CameraCharacteristics.LENS_FACING_FRONT -> L10n.t("Frontal", "Front")
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> L10n.t("Externa", "External")
                    else -> L10n.t("Câmera $id", "Camera $id")
                }
                val detail = buildList {
                    add("ID $id")
                    if (focal != null) add(String.format(Locale.US, "%.1f mm", focal))
                }.joinToString(" · ")
                CameraInfo(id = id, facing = facing, label = base, detail = detail)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * PT: Combinações de câmeras que o sistema permite abrir simultaneamente.
     * EN: Camera combinations the system allows opening simultaneously.
     */
    fun concurrentCombinations(context: Context): List<Set<String>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = manager(context) ?: return emptyList()
        return runCatching {
            manager.concurrentCameraIds.map { it.toSet() }.toList()
        }.getOrDefault(emptyList())
    }

    /**
     * PT: Existe alguma combinação com pelo menos duas câmeras?
     * EN: Is there any combination with at least two cameras?
     */
    fun supportsDualCamera(context: Context): Boolean =
        concurrentCombinations(context).any { it.size >= 2 }

    /**
     * PT: O par informado pode ser aberto ao mesmo tempo neste aparelho?
     * EN: Can the given pair be opened at the same time on this device?
     */
    fun supportsPair(context: Context, firstId: String, secondId: String): Boolean {
        if (firstId.isBlank() || secondId.isBlank() || firstId == secondId) return false
        return concurrentCombinations(context).any { combo ->
            combo.contains(firstId) && combo.contains(secondId)
        }
    }

    /**
     * PT: Câmera padrão (traseira) ou a primeira disponível.
     * EN: Default camera (rear) or the first available one.
     */
    fun defaultCamera(context: Context, front: Boolean = false): CameraInfo? {
        val cameras = cameras(context)
        val wanted = if (front) CameraCharacteristics.LENS_FACING_FRONT
        else CameraCharacteristics.LENS_FACING_BACK
        return cameras.firstOrNull { it.facing == wanted } ?: cameras.firstOrNull()
    }

    /**
     * PT: Primeira câmera diferente da principal, para usar na janela PiP.
     * EN: First camera different from the main one, to use in the PiP window.
     */
    fun defaultPipCamera(context: Context, mainId: String): CameraInfo? =
        cameras(context).firstOrNull { it.id != mainId }

    /**
     * PT: Resolve o par principal/PiP a partir das preferências, caindo para as
     * PT: câmeras padrão quando os IDs salvos não existem mais ou não formam um par
     * PT: simultâneo.
     *
     * EN: Resolves the main/PiP pair from the preferences, falling back to the
     * EN: default cameras when the saved IDs no longer exist or do not form a
     * EN: simultaneous pair.
     */
    fun resolvePair(
        context: Context,
        mainId: String,
        pipId: String
    ): Pair<CameraInfo, CameraInfo>? {
        val cameras = cameras(context)
        if (cameras.size < 2) return null
        val byId = cameras.associateBy { it.id }
        val combos = concurrentCombinations(context).filter { it.size >= 2 }

        // PT: 1) O par salvo é válido e simultâneo?
        // EN: 1) Is the saved pair valid and simultaneous?
        if (mainId.isNotBlank() && pipId.isNotBlank() && mainId != pipId) {
            val pairAllowed = combos.any { it.contains(mainId) && it.contains(pipId) }
            val main = byId[mainId]
            val pip = byId[pipId]
            if (pairAllowed && main != null && pip != null) return main to pip
        }

        // PT: 2) A câmera principal escolhida tem alguma parceira simultânea?
        // EN: 2) Does the chosen main camera have any simultaneous partner?
        if (mainId.isNotBlank()) {
            val main = byId[mainId]
            val partnerId = combos.firstOrNull { it.contains(mainId) }
                ?.firstOrNull { it != mainId }
            val partner = partnerId?.let { byId[it] }
            if (main != null && partner != null) return main to partner
        }

        // PT: 3) Qualquer combinação simultânea válida.
        // EN: 3) Any valid simultaneous combination.
        combos.firstOrNull()?.let { combo ->
            val ids = combo.toList()
            val first = byId[ids[0]]
            val second = byId[ids[1]]
            if (first != null && second != null) return first to second
        }

        // PT: 4) Último recurso: principal preferida + primeira câmera diferente.
        // EN: 4) Last resort: preferred main camera + first different camera.
        val main = byId[mainId]
            ?: cameras.firstOrNull { it.facing == CameraCharacteristics.LENS_FACING_BACK }
            ?: cameras.first()
        val pip = byId[pipId]?.takeIf { it.id != main.id }
            ?: cameras.firstOrNull { it.id != main.id }
            ?: return null
        return main to pip
    }

    fun logCapabilities(context: Context) {
        val cameras = cameras(context)
        val combos = concurrentCombinations(context)
        Log.i(TAG, "câmeras=${cameras.map { "${it.id}:${it.label}" }}")
        Log.i(TAG, "combinações simultâneas=$combos suportaDual=${supportsDualCamera(context)}")
    }

    private fun manager(context: Context): CameraManager? =
        context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    const val USB_PREFIX = "usb:"

    fun isUsbCameraId(id: String): Boolean = id.startsWith(USB_PREFIX)

    fun usbDeviceName(id: String): String = id.removePrefix(USB_PREFIX)
}
