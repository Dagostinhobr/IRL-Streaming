package app.streamlink.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Câmera e microfone são obrigatórios para transmitir. A permissão de notificação é
 * solicitada junto, mas negá-la não impede o streaming (apenas esconde a notificação).
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
