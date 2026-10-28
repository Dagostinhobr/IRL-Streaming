package app.streamlink.ui

/**
 * PT: Comandos enviados da interface nativa para o estúdio P2P dentro do WebView.
 * PT: O VDO.Ninja continua a ser o dono da câmera enquanto transmite; por isso
 * PT: trocar de câmera, gravar e ligar a lanterna são pedidos ao JavaScript da
 * PT: própria página em vez de usar o encoder nativo.
 *
 * EN: Commands sent from the native interface to the P2P studio inside the WebView.
 * EN: VDO.Ninja remains the owner of the camera while streaming; therefore
 * EN: switching cameras, recording and turning on the torch are requests to the
 * EN: page's own JavaScript instead of using the native encoder.
 */
sealed interface StudioCommand {
    data object FlipCamera : StudioCommand
    data object StartRecording : StudioCommand
    data object StopRecording : StudioCommand
    data class SetMuted(val muted: Boolean) : StudioCommand
    data class SetTorch(val on: Boolean) : StudioCommand
}
