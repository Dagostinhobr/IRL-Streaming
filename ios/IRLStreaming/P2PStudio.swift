import Combine
import Foundation
import WebKit

/// PT: Avisos gerados pela ponte JavaScript do estúdio P2P.
/// EN: Notices raised by the P2P studio JavaScript bridge.
enum StudioNotice: Equatable {
    case torchUnavailable
    case recordingUnavailable
    case recordingFailed(String)
}

/// PT: Câmara ativa no estúdio P2P. A letra é mostrada no botão de troca.
/// EN: Active camera in the P2P studio. The letter is shown on the switch button.
enum P2PCameraFacing: Equatable {
    case front
    case back
    case unknown

    /// PT: Letra curta usada no botão: F de front, B de back.
    /// EN: Short letter used on the button: F for front, B for back.
    var letter: String {
        switch self {
        case .front: return "F"
        case .back: return "B"
        case .unknown: return "?"
        }
    }

    var name: String {
        switch self {
        case .front: return "front"
        case .back: return "back"
        case .unknown: return "unknown"
        }
    }
}

/// PT: Controlador da câmara dentro do WebView do VDO.Ninja.
///
/// PT: O WebRTC continua a ser negociado pela página, mas o app passa a ter os
/// PT: mesmos controlos do modo Ao vivo: microfone, troca de câmara, lanterna e
/// PT: gravação. Os comandos são executados por JavaScript na própria página.
///
/// EN: Controller for the camera inside the VDO.Ninja WebView.
///
/// EN: WebRTC is still negotiated by the page, but the app now exposes the same
/// EN: controls as the Live mode: microphone, camera switch, torch and
/// EN: recording. Commands run as JavaScript inside the page itself.
final class P2PStudioController: ObservableObject {
    weak var webView: WKWebView?

    @Published var micLevel: Double = 0
    @Published var muted = false
    @Published var torchOn = false
    @Published var recording = false
    @Published var recordPath: String?
    @Published var notice: StudioNotice?
    @Published var cameraFacing: P2PCameraFacing = .unknown

    private let recorder = StudioRecorder()

    func attach(_ webView: WKWebView) {
        self.webView = webView
    }

    func detach() {
        if torchOn {
            evaluate("window.__irlSetTorch&&window.__irlSetTorch(false)")
            torchOn = false
        }
        webView = nil
    }

    /// PT: Fecha o estúdio. Se houver uma gravação em curso, o ficheiro é
    /// PT: finalizado e o caminho fica em `recordPath` em vez de ser apagado.
    /// EN: Closes the studio. If a recording is in progress the file is
    /// EN: finalised and its path is published through `recordPath` instead of
    /// EN: being discarded.
    func reset() {
        if torchOn {
            evaluate("window.__irlSetTorch&&window.__irlSetTorch(false)")
        }

        var savedPath: String?
        if recording {
            evaluate("window.__irlStopRecording&&window.__irlStopRecording()")
            savedPath = recorder.stop()
        } else {
            recorder.discard()
        }

        muted = false
        torchOn = false
        recording = false
        micLevel = 0
        recordPath = savedPath
        notice = nil
        cameraFacing = .unknown
        evaluate("window.__irlAbortRecording&&window.__irlAbortRecording()")
        evaluate("window.__irlSetMuted&&window.__irlSetMuted(false)")
    }

    func flipCamera() {
        // PT: A lanterna pertence à câmara traseira; desliga-a ao trocar para
        // PT: evitar ficar acesa sem vídeo correspondente.
        // EN: The torch belongs to the rear camera; turn it off when switching
        // EN: so it does not stay on without matching video.
        if torchOn {
            evaluate("window.__irlSetTorch&&window.__irlSetTorch(false)")
            torchOn = false
        }
        evaluate("window.__irlFlipCamera&&window.__irlFlipCamera()")
    }

    func toggleMute() {
        let next = !muted
        muted = next
        evaluate("window.__irlSetMuted&&window.__irlSetMuted(\(next))")
    }

    /// PT: A lanterna é pedida à própria track do WebRTC (`torch` constraint).
    /// PT: O iOS 17.4+ já suporta esta constraint e, ao contrário de mexer no
    /// PT: AVCaptureDevice por fora, não interrompe a sessão de captura nem
    /// PT: deixa o preview preto. O estado só muda quando o JavaScript confirma.
    ///
    /// EN: The torch is requested from the WebRTC track itself (the `torch`
    /// EN: constraint). iOS 17.4+ supports it and, unlike configuring
    /// EN: AVCaptureDevice externally, it does not interrupt the capture
    /// EN: session or black out the preview. The state only changes when
    /// EN: JavaScript confirms it.
    func toggleTorch() {
        evaluate("window.__irlSetTorch&&window.__irlSetTorch(\(!torchOn))")
    }

    func toggleRecording() {
        if recording {
            evaluate("window.__irlStopRecording&&window.__irlStopRecording()")
        } else {
            recordPath = nil
            evaluate("window.__irlStartRecording&&window.__irlStartRecording()")
        }
    }

    /// PT: Processa mensagens vindas do script injetado.
    /// EN: Handles messages coming from the injected script.
    func handle(_ body: Any) {
        guard let message = body as? [String: Any],
              let type = message["type"] as? String else { return }

        switch type {
        case "mic":
            if let value = message["value"] as? Double {
                micLevel = min(1, max(0, value))
            } else if let value = message["value"] as? NSNumber {
                micLevel = min(1, max(0, value.doubleValue))
            }
        case "muted":
            if let value = message["value"] as? Bool { muted = value }
        case "camera":
            let facing = (message["facing"] as? String ?? "").lowercased()
            let label = (message["label"] as? String ?? "").lowercased()
            let next: P2PCameraFacing
            if facing.contains("environment") || facing.contains("back")
                || label.contains("back") || label.contains("rear") {
                next = .back
            } else if facing.contains("user") || facing.contains("front")
                || label.contains("front") {
                next = .front
            } else {
                next = .unknown
            }
            if next != cameraFacing {
                cameraFacing = next
                // PT: A track mudou de câmara; a lanterna da anterior fica off.
                // EN: The track changed camera; the previous torch turns off.
                torchOn = false
            }
        case "torch":
            torchOn = message["on"] as? Bool ?? false
        case "torchUnavailable":
            torchOn = false
            notice = .torchUnavailable
        case "recStart":
            let mime = message["mime"] as? String ?? "video/mp4"
            if recorder.start(mime: mime) == nil {
                evaluate("window.__irlAbortRecording&&window.__irlAbortRecording()")
                notice = .recordingUnavailable
            } else {
                recording = true
            }
        case "recChunk":
            if let data = message["data"] as? String {
                recorder.append(data)
            }
        case "recStop":
            let path = recorder.stop()
            recording = false
            recordPath = path
            if path == nil { notice = .recordingFailed("empty") }
        case "recordingUnavailable":
            recording = false
            notice = .recordingUnavailable
        case "recError":
            recorder.discard()
            recording = false
            notice = .recordingFailed(message["message"] as? String ?? "unknown")
        default:
            break
        }
    }

    private func evaluate(_ script: String) {
        webView?.evaluateJavaScript(script, completionHandler: nil)
    }
}

/// PT: Grava o stream local do WebRTC para um ficheiro em Documentos.
/// PT: Os pedaços chegam em base64 da página e são escritos numa fila serial.
/// EN: Writes the local WebRTC stream to a file in Documents.
/// EN: Chunks arrive as base64 from the page and are written on a serial queue.
final class StudioRecorder {
    private let queue = DispatchQueue(label: "app.streamlink.p2p-recorder")
    private var handle: FileHandle?
    private var url: URL?

    func start(mime: String) -> String? {
        let pathExtension = mime.contains("mp4") ? "mp4" : (mime.contains("webm") ? "webm" : "mov")
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let name = "p2p_\(Self.timestamp()).\(pathExtension)"
        let target = directory.appendingPathComponent(name)

        guard FileManager.default.createFile(atPath: target.path, contents: nil),
              let file = try? FileHandle(forWritingTo: target) else {
            return nil
        }

        queue.sync {
            handle = file
            url = target
        }
        return target.path
    }

    func append(_ base64: String) {
        queue.async { [weak self] in
            guard let self, let data = Data(base64Encoded: base64) else { return }
            try? self.handle?.write(contentsOf: data)
        }
    }

    func stop() -> String? {
        var path: String?
        queue.sync {
            try? handle?.close()
            handle = nil
            path = url?.path
            url = nil
        }
        return path
    }

    func discard() {
        queue.sync {
            try? handle?.close()
            handle = nil
            if let url {
                try? FileManager.default.removeItem(at: url)
            }
            url = nil
        }
    }

    private static func timestamp() -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyyMMdd_HHmmss"
        return formatter.string(from: Date())
    }
}

/// PT: Script injectado no estúdio P2P.
///
/// PT: Esconde a interface do VDO.Ninja, liga o nível do microfone ao app e
/// PT: expõe as funções usadas pelos botões nativos.
///
/// EN: Script injected into the P2P studio.
///
/// EN: Hides the VDO.Ninja interface, connects the microphone level to the app
/// EN: and exposes the functions used by the native buttons.
enum P2PStudioScript {
    static let bridge = #"""
    (function() {
      if (window.__irlStudio) {
        if (window.__irlHideUi) window.__irlHideUi();
        return;
      }
      window.__irlStudio = true;

      function post(message) {
        try {
          if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.irlNative) {
            window.webkit.messageHandlers.irlNative.postMessage(message);
          }
        } catch (e) {}
      }

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
        } catch (e) {}
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

      /// PT: Informa o app se a câmara ativa é frontal ou traseira. O VDO.Ninja
      /// PT: pode trocar a track sem mudar o objeto do stream, por isso o estado
      /// PT: é comparado e enviado periodicamente.
      /// EN: Tells the app whether the active camera is front or back. VDO.Ninja
      /// EN: can swap the track without changing the stream object, so the state
      /// EN: is compared and sent periodically.
      window.__irlCameraSignature = '';
      window.__irlReportCamera = function() {
        try {
          var stream = window.__irlLocalStream();
          var track = stream ? stream.getVideoTracks()[0] : null;
          if (!track) return;
          var settings = track.getSettings ? track.getSettings() : {};
          var facing = settings.facingMode || '';
          var label = track.label || '';
          var signature = facing + '|' + label;
          if (signature === window.__irlCameraSignature) return;
          window.__irlCameraSignature = signature;
          post({type: 'camera', facing: facing, label: label});
        } catch (e) {}
      };

      window.__irlFlipCamera = function() {
        try {
          var button = document.getElementById('flipcamerabutton');
          if (button) { button.click(); return true; }
          var nodes = document.querySelectorAll('button, a, div[role="button"], [onclick]');
          for (var i = 0; i < nodes.length; i++) {
            var label = (nodes[i].getAttribute('aria-label') || nodes[i].title || nodes[i].innerText || '').toLowerCase();
            if (label.indexOf('flip') >= 0 || label.indexOf('switch camera') >= 0 || label.indexOf('trocar') >= 0) {
              nodes[i].click();
              return true;
            }
          }
        } catch (e) {}
        return false;
      };

      window.__irlSetMuted = function(muted) {
        try {
          var stream = window.__irlLocalStream();
          if (!stream) return false;
          stream.getAudioTracks().forEach(function(track) { track.enabled = !muted; });
          post({type: 'muted', value: !!muted});
          return true;
        } catch (e) { return false; }
      };

      window.__irlSetTorch = function(on) {
        try {
          var stream = window.__irlLocalStream();
          var track = stream ? stream.getVideoTracks()[0] : null;
          if (!track) { post({type: 'torchUnavailable'}); return false; }
          var caps = track.getCapabilities ? track.getCapabilities() : {};
          if (!caps || !caps.torch) { post({type: 'torchUnavailable'}); return false; }
          track.applyConstraints({advanced: [{torch: !!on}]}).then(function() {
            post({type: 'torch', on: !!on});
          }).catch(function() {
            post({type: 'torchUnavailable'});
          });
          return true;
        } catch (e) {
          post({type: 'torchUnavailable'});
          return false;
        }
      };

      window.__irlStartRecording = function() {
        try {
          if (window.__irlRecording) return true;
          if (!window.MediaRecorder) { post({type: 'recordingUnavailable'}); return false; }
          var stream = window.__irlLocalStream();
          if (!stream) { post({type: 'recordingUnavailable'}); return false; }
          var mimes = [
            'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
            'video/mp4',
            'video/webm;codecs=h264,opus',
            'video/webm'
          ];
          var mime = '';
          for (var i = 0; i < mimes.length; i++) {
            if (MediaRecorder.isTypeSupported(mimes[i])) { mime = mimes[i]; break; }
          }
          var options = {videoBitsPerSecond: 6000000};
          if (mime) options.mimeType = mime;
          var recorder;
          try {
            recorder = new MediaRecorder(stream, options);
          } catch (e) {
            recorder = new MediaRecorder(stream);
            mime = recorder.mimeType || 'video/mp4';
          }
          window.__irlRecording = recorder;
          window.__irlPendingWrites = 0;
          window.__irlStopped = false;
          window.__irlStopPosted = false;
          function postStopWhenFlushed() {
            if (window.__irlStopped && window.__irlPendingWrites === 0 && !window.__irlStopPosted) {
              window.__irlStopPosted = true;
              post({type: 'recStop'});
            }
          }
          post({type: 'recStart', mime: mime || 'video/mp4'});
          recorder.ondataavailable = function(event) {
            if (!event.data || !event.data.size) return;
            window.__irlPendingWrites++;
            var reader = new FileReader();
            reader.onloadend = function() {
              try {
                var data = String(reader.result || '');
                var marker = data.indexOf(';base64,');
                var base64 = marker >= 0 ? data.substring(marker + 8) : data.substring(data.indexOf(',') + 1);
                for (var pos = 0; pos < base64.length; pos += 32000) {
                  post({type: 'recChunk', data: base64.substring(pos, pos + 32000)});
                }
              } catch (e) {
                post({type: 'recError', message: String(e)});
              }
              window.__irlPendingWrites--;
              postStopWhenFlushed();
            };
            reader.readAsDataURL(event.data);
          };
          recorder.onerror = function(event) {
            post({type: 'recError', message: String(event.error || event)});
          };
          recorder.onstop = function() {
            window.__irlRecording = null;
            window.__irlStopped = true;
            postStopWhenFlushed();
          };
          recorder.start(1000);
          return true;
        } catch (e) {
          post({type: 'recError', message: String(e)});
          return false;
        }
      };

      window.__irlStopRecording = function() {
        try {
          var recorder = window.__irlRecording;
          if (!recorder) return false;
          if (recorder.state !== 'inactive') recorder.stop();
          return true;
        } catch (e) {
          return false;
        }
      };

      window.__irlAbortRecording = function() {
        try {
          var recorder = window.__irlRecording;
          window.__irlRecording = null;
          if (recorder && recorder.state !== 'inactive') {
            recorder.onstop = null;
            recorder.stop();
          }
        } catch (e) {}
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
              post({type: 'mic', value: Math.sqrt(sum / samples.length)});
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
          /start publishing/i,
          /iniciar transmiss/i
        ];
        var timer = setInterval(function() {
          tries++;
          window.__irlHideUi();
          window.__irlAttachMeter();
          var nodes = document.querySelectorAll('button, a, div[role="button"], .btn, [onclick]');
          for (var i = 0; i < nodes.length; i++) {
            var element = nodes[i];
            if (element.offsetParent === null || element.disabled) continue;
            var text = (element.innerText || element.textContent || '').trim();
            if (!text) continue;
            for (var j = 0; j < patterns.length; j++) {
              if (patterns[j].test(text)) {
                element.click();
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

      window.__irlHideUi();
      window.__irlAutostart();
      window.__irlAttachMeter();
      window.__irlReportCamera();
      if (!window.__irlKeepAlive) {
        window.__irlKeepAlive = setInterval(function() {
          window.__irlHideUi();
          window.__irlAttachMeter();
          window.__irlReportCamera();
        }, 800);
      }
    })();
    """#
}
