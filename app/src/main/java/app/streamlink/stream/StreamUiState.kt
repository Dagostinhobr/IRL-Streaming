package app.streamlink.stream

import app.streamlink.data.VideoOrientation

enum class StreamStatus {
    IDLE,
    CONNECTING,
    LIVE,
    RECONNECTING,
    ERROR
}

data class StreamUiState(
    val status: StreamStatus = StreamStatus.IDLE,
    val bitrateBps: Long = 0L,
    val fps: Int = 0,
    val droppedVideoFrames: Long = 0L,
    val congestionPercent: Float = 0f,
    val startedAt: Long? = null,
    val error: String? = null,
    val reconnecting: Boolean = false,
    val recording: Boolean = false,
    val recordPath: String? = null,
    val muted: Boolean = false,
    val torchOn: Boolean = false,
    val frontCamera: Boolean = false,
    /** A câmera principal ativa é uma câmera USB (UVC). */
    val usbCamera: Boolean = false,
    val zoom: Float = 1f,
    val maxZoom: Float = 4f,
    val resolutionLabel: String = "",
    val orientation: VideoOrientation = VideoOrientation.PORTRAIT,
    val reconnectAttempt: Int = 0,
    /** A janela PiP com a segunda câmera está ativa no vídeo. */
    val dualCameraActive: Boolean = false,
    /** Motivo pelo qual o modo PiP não pôde ser ativado. */
    val dualCameraError: String? = null
) {
    val isLive: Boolean
        get() = status == StreamStatus.LIVE

    val isBusy: Boolean
        get() = status == StreamStatus.CONNECTING || status == StreamStatus.RECONNECTING
}
