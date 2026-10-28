package app.streamlink.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import app.streamlink.stream.StudioRecorder
import app.streamlink.ui.StreamViewModel
import app.streamlink.ui.StudioCommand
import app.streamlink.util.AudioRouting
import app.streamlink.util.hasStreamPermissions
import app.streamlink.util.requiredPermissions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

/**
 * PT: Estúdio P2P do VDO.Ninja embutido na tela principal.
 *
 * PT: O WebView fica por baixo dos controles do app. Assim a câmera e o microfone
 * PT: são do VDO.Ninja (WebRTC), mas gravar, ocultar a tela, lanterna, troca de
 * PT: câmera e encerrar continuam a ser controlados pela interface nativa.
 *
 * EN: VDO.Ninja P2P studio embedded in the main screen.
 *
 * EN: The WebView sits beneath the app controls. This way the camera and microphone
 * EN: belong to VDO.Ninja (WebRTC), but recording, hiding the screen, torch, camera
 * EN: switching and stopping remain controlled by the native UI.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VdoNinjaEmbedded(
    url: String,
    viewModel: StreamViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webView by remember(url) { mutableStateOf<WebView?>(null) }
    val recorder = remember(context) { StudioRecorder(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    LaunchedEffect(Unit) {
        // PT: Liberta qualquer dispositivo de comunicação forçado por versões
        // PT: anteriores. O WebRTC usa o microfone padrão do sistema, o mesmo que
        // PT: o VDO.Ninja escolhe com `audiodevice=1`.
        // EN: Releases any communication device forced by previous
        // EN: versions. WebRTC uses the system default microphone, the same one
        // EN: VDO.Ninja chooses with `audiodevice=1`.
        AudioRouting.clearPreferredInput(context)
        if (!hasStreamPermissions(context)) {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    LaunchedEffect(webView) {
        val view = webView ?: return@LaunchedEffect
        viewModel.studioCommands.collect { command ->
            val script = when (command) {
                StudioCommand.FlipCamera -> "window.__irlFlipCamera&&window.__irlFlipCamera()"
                StudioCommand.StartRecording -> "window.__irlStartRecording&&window.__irlStartRecording()"
                StudioCommand.StopRecording -> "window.__irlStopRecording&&window.__irlStopRecording()"
                is StudioCommand.SetMuted ->
                    "window.__irlSetMuted&&window.__irlSetMuted(${command.muted})"
                is StudioCommand.SetTorch ->
                    "window.__irlSetTorch&&window.__irlSetTorch(${command.on})"
            }
            view.evaluateJavascript(script, null)
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
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
                    // PT: A interface nativa fica por cima. Desativar só a entrada
                    // PT: de toques evita que a view do WebView engula os eventos
                    // PT: destinados aos botões do app; a página e a câmera
                    // PT: continuam a funcionar normalmente.
                    // EN: The native UI sits on top. Disabling only touch input
                    // EN: prevents the WebView view from swallowing events
                    // EN: meant for the app buttons; the page and camera
                    // EN: keep working normally.
                    isEnabled = false
                    isClickable = false
                    isFocusable = false
                    isLongClickable = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                            super.onPageFinished(view, loadedUrl)
                            view?.evaluateJavascript(STUDIO_BRIDGE_JS, null)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            grantWebPermissions(context, request)
                        }
                    }
                    addJavascriptInterface(
                        StudioBridge(
                            recorder = recorder,
                            viewModel = viewModel,
                            mainHandler = mainHandler
                        ),
                        "IRLNative"
                    )
                    loadUrl(url)
                    webView = this
                }
            }
        )
    }

    // PT: A chave é Unit de propósito: uma mudança de `webView` (null → instância)
    // PT: não deve destruir a página que acabou de ser criada. O valor mais recente
    // PT: é lido no momento em que o composable sai de facto da composição.
    // EN: The key is Unit on purpose: a change of `webView` (null → instance)
    // EN: must not destroy the page that was just created. The latest value
    // EN: is read at the moment the composable actually leaves the composition.
    val currentWebView by rememberUpdatedState(webView)
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                currentWebView?.evaluateJavascript(
                    "window.__irlStopRecording&&window.__irlStopRecording()",
                    null
                )
            }
            runCatching {
                currentWebView?.stopLoading()
                currentWebView?.removeAllViews()
                currentWebView?.destroy()
            }
            AudioRouting.clearPreferredInput(context)
        }
    }
}

/**
 * PT: Ponte JavaScript → Kotlin. Os métodos correm na thread do JavaScript do
 * PT: WebView; as alterações de estado são reenviadas para a main thread.
 *
 * EN: JavaScript → Kotlin bridge. The methods run on the WebView's JavaScript
 * EN: thread; state changes are forwarded to the main thread.
 */
private class StudioBridge(
    private val recorder: StudioRecorder,
    private val viewModel: StreamViewModel,
    private val mainHandler: Handler
) {

    @JavascriptInterface
    fun onRecordingStarting(mime: String): String {
        val file = recorder.start(mime)
        return file?.absolutePath.orEmpty()
    }

    @JavascriptInterface
    fun onRecordingStarted(path: String) {
        mainHandler.post { viewModel.onWebRecordingStarted(path) }
    }

    @JavascriptInterface
    fun onRecordingChunk(base64: String) {
        recorder.append(base64)
    }

    @JavascriptInterface
    fun onRecordingStopped() {
        val file = recorder.stop()
        mainHandler.post { viewModel.onWebRecordingStopped(file?.absolutePath) }
    }

    @JavascriptInterface
    fun storageError(): String =
        app.streamlink.i18n.L10n.t(
            "sem espaço ou permissão de armazenamento",
            "no space or storage permission"
        )

    @JavascriptInterface
    fun onRecordingError(message: String) {
        recorder.stop()
        mainHandler.post { viewModel.onWebRecordingError(message) }
    }

    @JavascriptInterface
    fun onMicLevel(linear: Float) {
        mainHandler.post { viewModel.onWebMicLevel(linear) }
    }

    @JavascriptInterface
    fun onTorchState(on: Boolean) {
        mainHandler.post { viewModel.onWebTorchState(on) }
    }

    @JavascriptInterface
    fun onTorchUnavailable() {
        mainHandler.post { viewModel.onWebTorchUnavailable() }
    }
}

/**
 * PT: Script injectado depois de cada `onPageFinished`.
 *
 * PT: - esconde os controlos do VDO.Ninja e faz o vídeo preencher o ecrã;
 * PT: - liga o Medidor de nível ao stream local (VU meter nativo);
 * PT: - expõe funções para trocar de câmera, silenciar, lanterna e gravar.
 *
 * EN: Script injected after each `onPageFinished`.
 *
 * EN: - hides the VDO.Ninja controls and makes the video fill the screen;
 * EN: - connects the level meter to the local stream (native VU meter);
 * EN: - exposes functions to switch camera, mute, torch and record.
 */
private const val STUDIO_BRIDGE_JS = """
(function() {
  if (!window.__irlStudio) {
    window.__irlStudio = true;

    window.__irlHideUi = function() {
      try {
        var style = document.getElementById('irl-hide-ui');
        if (!style) {
          style = document.createElement('style');
          style.id = 'irl-hide-ui';
          (document.head || document.documentElement).appendChild(style);
        }
        style.textContent =
          'body{margin:0!important;overflow:hidden!important;background:#000!important}' +
          '#controlButtons,#subControlButtons,#top,#bottom,#header,#footer,#menu,' +
          '#mainmenu,#sidebar,#settings,#audioMenu,#videoMenu3,#recordLocalbutton,' +
          '#recordLocalScreenbutton,#startRecordingButton,#controls_blank,' +
          '#controls_directors_blank,#refreshVideoButton{display:none!important}' +
          '#videosource{position:fixed!important;inset:0!important;width:100vw!important;' +
          'height:100vh!important;max-width:none!important;max-height:none!important;' +
          'object-fit:cover!important;transform:none!important}';
      } catch(e) {}
    };

    window.__irlLocalStream = function() {
      var video = document.getElementById('videosource');
      if (video && video.srcObject) return video.srcObject;
      var videos = document.querySelectorAll('video');
      for (var i = 0; i < videos.length; i++) {
        if (videos[i].srcObject) return videos[i].srcObject;
      }
      return null;
    };

    window.__irlFlipCamera = function() {
      try {
        var button = document.getElementById('flipcamerabutton');
        if (button) { button.click(); return true; }
      } catch(e) {}
      return false;
    };

    window.__irlSetMuted = function(muted) {
      try {
        var stream = window.__irlLocalStream();
        if (!stream) return false;
        stream.getAudioTracks().forEach(function(track) { track.enabled = !muted; });
        return true;
      } catch(e) { return false; }
    };

    window.__irlSetTorch = function(on) {
      try {
        var stream = window.__irlLocalStream();
        if (!stream) { IRLNative.onTorchUnavailable(); return false; }
        var track = stream.getVideoTracks()[0];
        if (!track) { IRLNative.onTorchUnavailable(); return false; }
        var caps = track.getCapabilities ? track.getCapabilities() : {};
        if (!caps || !caps.torch) { IRLNative.onTorchUnavailable(); return false; }
        track.applyConstraints({advanced:[{torch: !!on}]}).then(function() {
          IRLNative.onTorchState(!!on);
        }).catch(function() {
          IRLNative.onTorchUnavailable();
        });
        return true;
      } catch(e) {
        IRLNative.onTorchUnavailable();
        return false;
      }
    };

    window.__irlStartRecording = function() {
      try {
        if (window.__irlRecording) return true;
        var stream = window.__irlLocalStream();
        if (!stream) return false;
        var mimes = [
          'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
          'video/mp4',
          'video/webm;codecs=h264,opus',
          'video/webm;codecs=vp8,opus',
          'video/webm'
        ];
        var mime = '';
        for (var i = 0; i < mimes.length; i++) {
          if (window.MediaRecorder && MediaRecorder.isTypeSupported(mimes[i])) {
            mime = mimes[i];
            break;
          }
        }
        var options = {videoBitsPerSecond: 6000000};
        if (mime) options.mimeType = mime;
        var rec;
        try {
          rec = new MediaRecorder(stream, options);
        } catch (e) {
          rec = new MediaRecorder(stream);
          mime = rec.mimeType || 'video/webm';
        }
        var path = IRLNative.onRecordingStarting(mime || 'video/webm');
        if (!path) {
          IRLNative.onRecordingError(IRLNative.storageError());
          return false;
        }
        var pending = 0;
        var stopping = false;
        var finish = function() {
          if (stopping && pending === 0) {
            stopping = false;
            IRLNative.onRecordingStopped();
          }
        };
        rec.ondataavailable = function(event) {
          if (!event.data || !event.data.size) return;
          pending++;
          var reader = new FileReader();
          reader.onloadend = function() {
            try {
              var data = String(reader.result || '');
              // PT: O tipo MIME pode conter vírgulas (ex.: `avc1.42E01E,mp4a.40.2`),
              // por isso o delimitador seguro é `;base64,` e não a primeira vírgula.
              // EN: The MIME type may contain commas (e.g. `avc1.42E01E,mp4a.40.2`),
              // so the safe delimiter is `;base64,`, not the first comma.
              var marker = data.indexOf(';base64,');
              var base64 = marker >= 0
                ? data.substring(marker + 8)
                : data.substring(data.indexOf(',') + 1);
              // PT: A ponte JS→Kotlin não lida bem com strings muito grandes.
              // 32000 é múltiplo de 4, pelo que cada parte é base64 válida.
              // EN: The JS→Kotlin bridge does not handle very large strings well.
              // 32000 is a multiple of 4, so each chunk is valid base64.
              for (var pos = 0; pos < base64.length; pos += 32000) {
                IRLNative.onRecordingChunk(base64.substring(pos, pos + 32000));
              }
            } catch (e) {
              IRLNative.onRecordingError(String(e));
            }
            pending--;
            finish();
          };
          reader.readAsDataURL(event.data);
        };
        rec.onerror = function(event) {
          IRLNative.onRecordingError(String(event.error || event));
        };
        rec.onstop = function() {
          window.__irlRecording = null;
          stopping = true;
          finish();
        };
        window.__irlRecording = rec;
        rec.start(1000);
        IRLNative.onRecordingStarted(path);
        return true;
      } catch (e) {
        IRLNative.onRecordingError(String(e));
        return false;
      }
    };

    window.__irlStopRecording = function() {
      try {
        var rec = window.__irlRecording;
        if (!rec) return false;
        if (rec.state !== 'inactive') rec.stop();
        return true;
      } catch (e) {
        IRLNative.onRecordingError(String(e));
        return false;
      }
    };

    window.__irlAttachMeter = function() {
      try {
        var stream = window.__irlLocalStream();
        if (!stream || window.__irlMeterStream === stream) return;
        window.__irlMeterStream = stream;
        var AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtx) return;
        if (!window.__irlAudioCtx) window.__irlAudioCtx = new AudioCtx();
        var ctx = window.__irlAudioCtx;
        if (ctx.state === 'suspended' && ctx.resume) ctx.resume();
        var source = ctx.createMediaStreamSource(stream);
        var analyser = ctx.createAnalyser();
        analyser.fftSize = 512;
        source.connect(analyser);
        var samples = new Uint8Array(analyser.fftSize);
        if (window.__irlMeterTimer) clearInterval(window.__irlMeterTimer);
        window.__irlMeterTimer = setInterval(function() {
          try {
            analyser.getByteTimeDomainData(samples);
            var sum = 0;
            for (var i = 0; i < samples.length; i++) {
              var value = (samples[i] - 128) / 128;
              sum += value * value;
            }
            IRLNative.onMicLevel(Math.sqrt(sum / samples.length));
          } catch (e) {}
        }, 100);
      } catch (e) {}
    };

    window.__irlAutostart = function() {
      if (window.__irlAutostartDone) return;
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
        window.__irlHideUi();
        window.__irlAttachMeter();
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
              window.__irlAutostartDone = true;
              return;
            }
          }
        }
        if (tries > 60) {
          clearInterval(timer);
          window.__irlAutostartDone = true;
        }
      }, 500);
    };
  }

  window.__irlHideUi();
  window.__irlAutostart();
  window.__irlAttachMeter();
  if (!window.__irlKeepAlive) {
    window.__irlKeepAlive = setInterval(function() {
      window.__irlHideUi();
      window.__irlAttachMeter();
    }, 800);
  }
})();
"""

/**
 * PT: Concede câmera/microfone à página apenas se o app já tem as permissões.
 * EN: Grants camera/microphone to the page only if the app already has the permissions.
 */
private fun grantWebPermissions(context: Context, request: PermissionRequest) {
    val camera = request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
    val microphone = request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)

    val cameraOk = !camera || androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val microphoneOk = !microphone || androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    if (cameraOk && microphoneOk) {
        request.grant(request.resources)
    } else {
        request.deny()
    }
}
