package app.streamlink.ui

/**
 * Comandos enviados da interface nativa para o estúdio P2P dentro do WebView.
 * O VDO.Ninja continua a ser o dono da câmera enquanto transmite; por isso
 * trocar de câmera, gravar e ligar a lanterna são pedidos ao JavaScript da
 * própria página em vez de usar o encoder nativo.
 */
sealed interface StudioCommand {
    data object FlipCamera : StudioCommand
    data object StartRecording : StudioCommand
    data object StopRecording : StudioCommand
    data class SetMuted(val muted: Boolean) : StudioCommand
    data class SetTorch(val on: Boolean) : StudioCommand
}
