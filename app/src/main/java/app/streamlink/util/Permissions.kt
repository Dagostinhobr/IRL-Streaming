package app.streamlink.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * PT: Câmera e microfone são obrigatórios para transmitir. A permissão de notificação é
 * PT: solicitada junto, mas negá-la não impede o streaming (apenas esconde a notificação).
 *
 * EN: Camera and microphone are required to stream. The notification permission is
 * EN: requested as well, but denying it does not prevent streaming (it only hides the notification).
 */
fun requiredPermissions(): Array<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()

fun hasStreamPermissions(context: Context): Boolean =
    isGranted(context, Manifest.permission.CAMERA) &&
        isGranted(context, Manifest.permission.RECORD_AUDIO)

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
