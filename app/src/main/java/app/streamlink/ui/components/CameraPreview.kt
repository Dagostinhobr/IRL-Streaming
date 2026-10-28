package app.streamlink.ui.components

import android.view.SurfaceView
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onReady: (SurfaceView) -> Unit
) {
    val reported = remember { mutableStateOf(false) }
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                keepScreenOn = true
            }
        },
        modifier = modifier,
        update = { view ->
            if (!reported.value) {
                reported.value = true
                view.post { onReady(view) }
            }
        }
    )
}

/**
 * Pinch para zoom e toque para focar. Consome os eventos para não conflitar
 * com o preview/encoder.
 */
fun Modifier.cameraGestures(
    onTap: (Offset) -> Unit,
    onZoom: (Float) -> Unit
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        var pressed = true
        while (pressed) {
            val event = awaitPointerEvent()
            val zoom = event.calculateZoom()
            if (zoom != 1f) {
                onZoom(zoom)
                moved = true
            }
            if (event.calculatePan().getDistance() > 8f) {
                moved = true
            }
            if (event.changes.any { it.pressed }) {
                event.changes.forEach { it.consume() }
            }
            pressed = event.changes.any { it.pressed }
        }
        if (!moved) {
            onTap(down.position)
        }
    }
}
