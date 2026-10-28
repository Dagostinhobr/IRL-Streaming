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
    // PT: A câmera principal ativa é uma câmera USB (UVC).
    // EN: The active main camera is a USB (UVC) camera.
    val usbCamera: Boolean = false,
    val zoom: Float = 1f,
    val maxZoom: Float = 4f,
    val resolutionLabel: String = "",
    val orientation: VideoOrientation = VideoOrientation.PORTRAIT,
    val reconnectAttempt: Int = 0,
    // PT: A janela PiP com a segunda câmera está ativa no vídeo.
    // EN: The PiP window with the second camera is active in the video.
    val dualCameraActive: Boolean = false,
    // PT: Motivo pelo qual o modo PiP não pôde ser ativado.
    // EN: Reason why PiP mode could not be enabled.
    val dualCameraError: String? = null
) {
    val isLive: Boolean
        get() = status == StreamStatus.LIVE

    val isBusy: Boolean
        get() = status == StreamStatus.CONNECTING || status == StreamStatus.RECONNECTING
}
