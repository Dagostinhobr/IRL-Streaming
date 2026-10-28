package app.streamlink.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.streamlink.i18n.tr
import app.streamlink.ui.theme.Ink
import app.streamlink.ui.theme.TextPrimary
import app.streamlink.util.AudioRouting
import app.streamlink.util.hasStreamPermissions
import app.streamlink.util.requiredPermissions

/**
 * PT: Estúdio P2P do VDO.Ninja rodando no WebView do próprio app.
 *
 * PT: O WebRTC é negociado pelo navegador (peer-to-peer), e o app apenas concede as
 * PT: permissões de câmera/microfone solicitadas pela página. Dessa forma o microfone
 * PT: USB configurado como padrão no Android também é utilizado.
 *
 * EN: VDO.Ninja P2P studio running in the app's own WebView.
 *
 * EN: WebRTC is negotiated by the browser (peer-to-peer), and the app only grants the
 * EN: camera/microphone permissions requested by the page. This way the USB
 * EN: microphone set as default in Android is also used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VdoNinjaWebScreen(
    url: String,
    title: String,
    onEnter: () -> Unit = {},
    onExit: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        // PT: Liberta o microfone nativo (VU meter) antes de o WebView captar.
        // PT: Não forçamos um AudioDeviceInfo do encoder aqui: no WebView o
        // PT: WebRTC usa o microfone padrão do sistema (USB/fone têm prioridade),
        // PT: que é o mesmo que o VDO.Ninja seleciona com `audiodevice=1`.
        // EN: Releases the native microphone (VU meter) before the WebView captures.
        // EN: We do not force an encoder AudioDeviceInfo here: in the WebView
        // EN: WebRTC uses the system default microphone (USB/headset take priority),
        // EN: which is the same one VDO.Ninja selects with `audiodevice=1`.
        onEnter()
        // PT: Limpa qualquer dispositivo de comunicação forçado anteriormente: um
        // PT: id guardado que já não existe faz o WebRTC falhar com NotReadableError.
        // EN: Clears any previously forced communication device: a stored
        // EN: id that no longer exists makes WebRTC fail with NotReadableError.
        AudioRouting.clearPreferredInput(context)
        if (!hasStreamPermissions(context)) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        containerColor = Ink,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Voltar", "Back"))
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = tr("Recarregar", "Reload"))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Ink,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary,
                    actionIconContentColor = TextPrimary
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            userAgentString = "$userAgentString IRLStreaming/1.0"
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                super.onPageFinished(view, loadedUrl)
                                loading = false
                                view?.evaluateJavascript(AUTOSTART_JS, null)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) {
                                grantWebPermissions(context, request)
                            }
                        }
                        loadUrl(url)
                        webView = this
                    }
                }
            )

            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = TextPrimary
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AudioRouting.clearPreferredInput(context)
            onExit()
            webView?.apply {
                stopLoading()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}

/**
 * PT: Fallback de autostart: o VDO.Ninja às vezes exibe a tela "Share your Camera"
 * PT: mesmo com &autostart&webcam. Este script clica no botão assim que ele aparece,
 * PT: repetindo por até ~20 segundos (o React pode renderizar depois do onPageFinished).
 *
 * EN: Autostart fallback: VDO.Ninja sometimes shows the "Share your Camera" screen
 * EN: even with &autostart&webcam. This script clicks the button as soon as it appears,
 * EN: repeating for up to ~20 seconds (React may render after onPageFinished).
 */
private const val AUTOSTART_JS = """
(function() {
  if (window.__irlAutostart) return;
  window.__irlAutostart = true;
  var tries = 0;
  var patterns = [
    /share your camera/i,
    /compartilhar (a )?c[aâ]mera/i,
    /start camera/i,
    /iniciar c[aâ]mera/i,
    /start publishing/i
  ];
  var timer = setInterval(function() {
    tries++;
    var nodes = document.querySelectorAll('button, a, div[role="button"], .btn, [onclick]');
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.offsetParent === null || el.disabled) continue;
      var text = (el.innerText || el.textContent || '').trim();
      if (!text) continue;
      for (var j = 0; j < patterns.length; j++) {
        if (patterns[j].test(text)) {
          el.click();
          clearInterval(timer);
          return;
        }
      }
    }
    if (tries > 40) clearInterval(timer);
  }, 500);
})();
"""

/**
 * PT: Concede câmera/microfone à página apenas se o app já tem as permissões.
 * EN: Grants camera/microphone to the page only if the app already has the permissions.
 */
private fun grantWebPermissions(context: Context, request: PermissionRequest) {
    val camera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
    val microphone = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

    val cameraOk = !camera || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    val microphoneOk = !microphone || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    if (cameraOk && microphoneOk) {
        request.grant(request.resources)
    } else {
        request.deny()
    }
}
