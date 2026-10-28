# IRL Streaming — live streaming for Android and iOS

An **independent** live streaming project, inspired by
[Moblin](https://moblin.app/) (iOS). The Android app delivers the core IRL
streaming workflow: camera + microphone → encoder → RTMP/RTMPS/SRT/WHIP, with
chat, local recording, multiple targets, VDO.Ninja (P2P) and `moblin://` link
import. The iOS app is a new **MVP focused on the VDO.Ninja P2P studio**.

> **Status:** Android beta (v0.8.0) and iOS MVP (v0.8.0). Moblin has years of
> development and dozens of advanced features (scenes, widgets, SRTLA, remote
> OBS, Apple Watch). See the [Differences from Moblin](#differences-from-moblin)
> section before expecting 1:1 parity.

**Download (Android):** [GitHub Releases](https://github.com/Dagostinhobr/IRL-Streaming/releases) —
`IRL-Streaming-0.8.0.apk` (signed release) or `IRL-Streaming-0.8.0-debug.apk` (test build).

The iOS app is not distributed yet; it is built from source in Xcode. See
[iOS app (MVP)](#ios-app-mvp).

> **Name:** the app appears as **IRL Streaming** in the launcher. The internal
> Android package identifier remains `app.streamlink` to preserve existing
> installs and upgrades. The iOS bundle identifier is `app.streamlink.ios`.

> **Language:** the interface is available in **Portuguese** and **English** on
> both platforms. Change it at any time in **Settings → Language**.


---

## Implemented features

| Area | Feature |
|---|---|
| Streaming | RTMP, RTMPS, SRT, RTSP and UDP (via [RootEncoder](https://github.com/pedroSG94/RootEncoder)) |
| WebRTC | Native WHIP (experimental) and the VDO.Ninja P2P studio inside the app (WebView) |
| VDO.Ninja | Room **or stream ID** (`?push=ID&password=...` / `?view=ID&p=...`), link import, in-app P2P studio and P2P target |
| Video | H.264/AVC and H.265/HEVC, 480p/720p/1080p, 24/30/60 fps, configurable bitrate |
| Audio | AAC, 64–192 kbps, 44.1/48 kHz, mono/stereo, echo cancellation and noise suppression |
| VU meter | Microphone level meter on the main screen (−60 to 0 dB scale, fast attack and smooth decay) |
| Microphone | Real-time input selection: automatic (built-in), **USB**, wired headset, Bluetooth; the USB camera microphone is chosen automatically |
| Camera | Rear/front, camera list selection, **USB UVC camera over OTG**, pinch zoom, zoom presets, tap to focus, torch |
| Orientation | **Automatic from the sensor** (portrait when upright, landscape when sideways) or locked to portrait/landscape, with an on-screen button and live switching |
| Controls | Mute, black screen (stealth), keep screen on |
| Connection | Live status, uptime, actual bitrate, actual FPS, dropped frames, automatic reconnection (5 attempts) |
| Recording | Local MP4 recorded alongside the stream, in `Android/data/app.streamlink/files/recordings` |
| Targets | Named URL list with protocol, stream key and backup URL; switch with a tap |
| Chat | Twitch over IRC/WebSocket: anonymous reading, sending with OAuth, badges, colours, message removal, sub/raid alerts |
| Extra chat | Video overlay, timestamps, TTS (read messages aloud), history clearing |
| Integration | `moblin://`, `streamlink://` and `irlstream://` deep links to import targets |
| Background | Foreground service with a persistent notification and an "End" button |
| Persistence | All settings in SharedPreferences/JSON |

### Usage shortcuts

- **Tap** the preview → manual focus.
- **Pinch** → zoom; `1x/2x/3x/5x/8x` chips → quick zoom.
- **VU meter** (above the target chip) → shows the microphone signal in real
  time; it turns red with "MUTED" when the microphone is muted.
- **Centre button** → start/stop the stream.
- **Target chip** (above the button) → switch target.
- **Chat icon** (side bar or footer) → full chat.
- **Orientation button** (side bar, rotation icon) → cycles
  **Auto → Portrait → Landscape**. In **Auto**, the video follows the sensor:
  upright publishes in portrait (720×1280, Instagram style) and sideways in
  landscape (1280×720, YouTube style). In P2P the change is applied live without
  stopping the stream; on RTMP/SRT targets the new resolution takes effect on the
  next live stream.

---

## VDO.Ninja (P2P)

**Settings → VDO.Ninja** (or **Targets → VDO.Ninja**):

1. Under **Identification**, enter a **stream ID** (e.g. `meu-stream-7f3a`) or
   generate a **room** (e.g. `sala-exemplo-42`). The ID takes priority over the room.
2. Fill in the **password** if the stream is protected (e.g. `minha-senha-ficticia`).
   The **Paste VDO.Ninja link and fill in** button imports ID, room, password and
   host from an existing link such as
   `https://vdo.ninja/?view=meu-stream-7f3a&p=minha-senha-ficticia`.
3. **P2P links**:
   - **Publish** — `https://vdo.ninja/?push=YOUR_ID&password=YOUR_PASSWORD` to send
     camera/mic.
   - **Watch** — `https://vdo.ninja/?view=YOUR_ID&p=YOUR_PASSWORD` to receive.
4. **Use as target (P2P)** → creates the `VDO.Ninja (P2P)` target. When you tap the
   stream button on the main screen, the app opens the WebRTC studio with that
   link. This is the correct P2P path — **it does not use WHIP**.
5. **Open studio** → opens the VDO.Ninja studio in the app's own WebView and
   publishes over WebRTC (P2P), using the device camera and microphone. The link
   already includes `&autostart&webcam&audiodevice=1`: publishing starts on its
   own and the default microphone is selected automatically, **without asking you
   to choose again**. If the page takes a while to render, the app clicks "Share
   your Camera" automatically.
6. **Open player** → watches the ID/room without leaving the app.

> The IDs, rooms, passwords and links shown in this documentation are **fictional**
> and serve only as examples. Replace them with your own data.

The P2P studio is the **recommended and validated** path for streaming via
VDO.Ninja: WebRTC is negotiated by VDO.Ninja itself, and the app only grants the
camera/microphone permissions.

### Native WHIP (experimental)

The app can also publish the native camera via **WHIP** (WebRTC), keeping the
quality and controls of the RTMP stream:

- Endpoint: `https://whip.vdo.ninja/TOKEN` (token = stream ID or room).
- Return link for OBS: `https://vdo.ninja/?whipview=TOKEN`.
- The corresponding button is **Use as WHIP target (experimental)** — separate
  from the P2P target so the two paths are not confused.

> **Current limitation:** the `whip.vdo.ninja` service returned **HTTP 504** in every
> publishing test, and the WHIP client did not complete ICE against local servers.
> Until this is stabilised, treat this option as experimental and use the **P2P
> studio** to go live. The UI indicates this limitation.

### USB microphone in the P2P studio

Connect the USB microphone (or audio interface) **before** opening the studio.
Android then uses it as the default WebRTC input and the studio opens with it
already selected. For native RTMP/WHIP streaming, choose the device in
**Settings → Audio → Microphone**.

> WebView/WebRTC needs the normal `MODIFY_AUDIO_SETTINGS` permission to select the
> capture device; without it VDO.Ninja failed with `NotReadableError` and did not
> publish audio.

### USB camera (UVC)

The app recognises USB cameras connected over OTG and lists them in
**Settings → Camera → Main camera**. The camera does not need to be exposed by
Android Camera2: UVC video is converted into the same OpenGL/encoder pipeline as
the native camera.

- When you connect the camera, Android asks for permission to access the USB
  device; accept once. The app detects the connection and refreshes the list
  automatically.
- The USB camera appears as **USB camera** with the product name.
- If the camera has a built-in microphone (UAC), the app selects that microphone
  automatically when the input is set to **Automatic**.
- The audio input can be changed at any time, including during the stream: tap the
  microphone meter on the main screen or use **Settings → Audio → Microphone**. The
  switch is applied in real time.
- With a USB camera, zoom, torch and PiP are unavailable (UVC limitations).
- If the camera is disconnected, the app falls back to the native camera and the
  built-in microphone on the next preparation.

### Two-camera PiP

The app composites **two cameras into the same video**: the window is part of the
stream and the recording (it is not Android's system PiP). The main camera stays
full screen with focus, zoom, torch and lens switching; the second camera is drawn
by a custom OpenGL filter (`PipFilterRender`) over the video, with:

- selectable main lens and window lens (rear/front);
- position (corners or centre), size (20/30/40%) and rotation (0/90/180/270);
- a shortcut to enable/disable it on the main screen and in **Settings → PiP with two cameras**.

Requirement: the device must advertise two concurrent cameras in
`CameraManager.getConcurrentCameraIds()` (Android 11+). The app detects this and
disables the option when unsupported. The Redmi Note 9 Pro used in testing does
not support it (empty list; `MAX_CAMERAS_IN_USE`), so the interface shows the
warning and the mode stays disabled. The composition was validated on the same
device by injecting a test pattern into the window Surface (position, size, mask
and rotation).

If the second camera fails (for example, another app is using the camera), the app
removes the window and shows the reason without interrupting the main stream.

An alternative that works on any device: use a **second phone** as a camera via
VDO.Ninja (`?push=ROOM`) and place that source as a PiP in OBS.

---

## iOS app (MVP)

The iOS app lives in [`ios/`](ios/) and is a native SwiftUI project generated
with [XcodeGen](https://github.com/yonaskolb/XcodeGen). It targets **iOS 16+**,
uses the bundle identifier `app.streamlink.ios`, and focuses on the VDO.Ninja
P2P workflow:

| Area | iOS MVP |
|---|---|
| Studio | Embedded `WKWebView` publishing to VDO.Ninja with the iPhone camera and microphone |
| Links | Stream ID or room, password, display name, publish and view links, copy/share, paste-to-fill |
| Quality | Video/audio bitrate, stereo, autostart, clean output, audio-only, force H.264 |
| Chat | Twitch IRC over WebSocket: anonymous reading, OAuth sending, overlay over the studio |
| Settings | Portuguese/English, keep screen on, privacy notes, reset |
| Persistence | `UserDefaults` (settings, links and tokens stay on the device) |

**Not included in the iOS MVP:** native RTMP/SRT/WHIP encoding, local recording,
UVC cameras, multi-camera PiP and background streaming. iOS also suspends
WebRTC when the app goes to the background, so keep it in the foreground while
publishing.

### Build and install on an iPhone

Requirements: macOS with **Xcode 15 or newer**, an Apple ID, and an iPhone
running iOS 16+.

1. Install Xcode from the App Store and run it once to install the platform.
2. Generate the project (only needed after changing `ios/project.yml` or adding
   files):

   ```bash
   cd ios
   xcodegen generate
   ```

   XcodeGen can be installed with `brew install xcodegen`.
3. Open `ios/IRLStreaming.xcodeproj` in Xcode.
4. Select the **IRLStreaming** target → **Signing & Capabilities** → choose your
   personal team. Change the bundle identifier if Xcode asks for a unique one.
5. Connect the iPhone, enable **Developer Mode** on the device, select it as the
   run destination and press **Run**.
6. On first launch, allow camera and microphone access.

A free Apple ID signs the app for **7 days** and needs a reinstall afterwards. A
paid Apple Developer account (99 USD/year) enables TestFlight and longer-lived
installs. There is no `.ipa` in the repository because builds must be signed
with your own Apple account.

The generated `ios/IRLStreaming.xcodeproj` is committed, so step 2 is only
needed after changing `project.yml` or adding/removing source files.

See [`ios/README.md`](ios/README.md) for the project structure and
troubleshooting.

---

## Building

### Requirements

- Android Studio (Ladybug or newer) **or** JDK 17 + Android SDK 35
- Android 8.0 (API 26) or newer
- A physical phone to test camera/encoder (emulators do not have a good camera)
- For iOS: macOS, Xcode 15+ and iOS 16+ (see [iOS app (MVP)](#ios-app-mvp))

### In Android Studio

1. Open the project folder.
2. Wait for Gradle to sync (RootEncoder comes from JitPack; the first build downloads dependencies).
3. Connect the phone with USB debugging and click **Run**.

### From the command line

```bash
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Distribution APK (release)

The release build uses the keystore configured in `keystore.properties`
(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without that file the
release compiles but is not signed.

```bash
./gradlew :app:assembleRelease
# APK at app/build/outputs/apk/release/app-release.apk
```

To generate a new keystore:

```bash
keytool -genkeypair -v -keystore .keys/irl-streaming.jks -alias irlstreaming \
  -keyalg RSA -keysize 4096 -validity 10000
```

> **Warning:** the keystore is the app's identity. Keep the `.jks` file and the
> passwords; without them you cannot update existing installations. `.gitignore`
> already excludes `.keys/`, `keystore.properties` and `dist/`.

To serve the download on the local network:

```bash
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/IRL-Streaming-0.8.0.apk
cp app/build/outputs/apk/debug/app-debug.apk dist/IRL-Streaming-0.8.0-debug.apk
python3 -m http.server 8080 --bind 0.0.0.0 --directory dist
# open http://YOUR_IP:8080 on the phone
```

---

## Quick setup

### 1. Target

Open **Targets** (cast icon) → choose or create a target:

| Platform | Ingest URL | Stream key |
|---|---|---|
| Twitch | `rtmp://live.twitch.tv/app` | your key (Twitch Dashboard → Settings → Stream) |
| YouTube | `rtmp://a.rtmp.youtube.com/live2` | key from YouTube Studio |
| Kick | `rtmps://fa723fc1b171.global-contribute.live-video.net:443/app` | Kick key |
| Custom SRT | `srt://host:port?streamid=...` | leave empty |
| WHIP | `https://whip.vdo.ninja/TOKEN` | leave empty |

> For RTMP the final URL is `url/streamKey`. For SRT/RTSP/WHIP with an embedded key,
> paste the full URL and leave the stream key empty.

### 2. Twitch chat

**Settings → Chat**:

- **Channel**: channel name (without `#`).
- **Reading**: works without login (anonymous mode).
- **Sending**: provide the **username** and an **OAuth token** with the
  `chat:read chat:edit` scopes. Generate it at <https://twitchapps.com/tmi/>
  (not an official Twitch service; use at your own risk) or through official OAuth.

The chat connects automatically when the live stream starts.

### 3. Import from Moblin

The app accepts the Moblin format:

```
moblin://?%7B%22streams%22%3A%5B%7B%22name%22%3A%22Casa%22%2C%22url%22%3A%22rtmp%3A%2F%2Flive.twitch.tv%2Fapp%2FCHAVE%22%7D%5D%7D
```

Decoded:

```json
{ "streams": [ { "name": "Casa", "url": "rtmp://live.twitch.tv/app/CHAVE" } ] }
```

Open the link on the phone (it must be registered in the browser/Telegram/etc.) or
use `adb shell am start -a android.intent.action.VIEW -d "moblin://?..."`.
Each stream becomes a target and the first one is selected.

---

## Architecture

```
app/src/main/java/app/streamlink/
├── MainActivity.kt              # entry point, deep links, notification "End" button
├── data/
│   ├── Models.kt                # AppSettings, StreamTarget, VdoNinjaSettings, enums, defaults
│   └── SettingsRepository.kt    # JSON persistence (SharedPreferences + StateFlow)
├── stream/
│   ├── StreamEngine.kt          # StreamBase: WhipStream (WebRTC) or GenericStream (RTMP/SRT/...)
│   ├── StreamUiState.kt         # observable live stream state
│   └── StreamService.kt         # foreground service + notification
├── chat/
│   ├── ChatModels.kt
│   ├── TwitchChatClient.kt      # IRC over WebSocket (tags, commands, clearmsg)
│   └── ChatManager.kt           # history, reconnection, moderation
├── i18n/
│   └── I18n.kt                  # Portuguese/English UI layer (Compose + non-Compose)
├── ui/
│   ├── StreamViewModel.kt       # orchestrates engine + chat + TTS + settings + VDO.Ninja
│   ├── StreamLinkApp.kt         # navigation (Home, Settings, Targets, VDO.Ninja, WebView)
│   ├── screens/                 # Home, Settings, Targets, VdoNinja, VdoNinjaWeb
│   ├── components/              # preview, gestures, chat, Material 3 components
│   └── theme/
└── util/                        # VdoNinja (links), AudioDevices (USB), deep link, permissions
```

Stack: **Kotlin + Jetpack Compose + Material 3 + Camera2/RootEncoder + OkHttp + WebView**.
No telemetry, no own server: video goes straight to the ingest server you configure
(or directly to the guest, in the case of P2P).

### Permissions

| Permission | Purpose |
|---|---|
| `CAMERA` | Video capture |
| `RECORD_AUDIO` | Microphone |
| `INTERNET` | Send the stream, connect to chat and VDO.Ninja |
| `FOREGROUND_SERVICE` + `..._CAMERA` + `..._MICROPHONE` | Keep the live stream running in the background |
| `POST_NOTIFICATIONS` | Live stream notification (Android 13+) |
| `WAKE_LOCK` | Prevent suspension during streaming |

---

## Differences from Moblin

Implemented in this beta: the capture/encoder core, targets, Twitch chat,
recording, statistics, reconnection, TTS, deep links, VDO.Ninja (P2P in the
WebView) and USB microphone selection.

**Not implemented yet** (suggested roadmap, in order of impact):

1. **SRTLA / bonding** of multiple connections (Wi-Fi + mobile). RootEncoder supports
   plain SRT; SRTLA requires a specific client (e.g. libsrt + SRTLA) and a compatible
   server (BELABOX).
2. **RIST** and stable WHIP against the VDO.Ninja service.
3. **Scenes and widgets** (text, image, map, on-screen chat, scoreboard, alerts, LUTs,
   video filters). This is most of Moblin's code.
4. **Kick, YouTube and SOOP integrations** (chat, viewers, title/category, alerts).
5. **OBS WebSocket** and web remote control.
6. **Migration to CameraX** with multi-camera and real-time video source switching.
7. **Background streaming with audio** (frozen video), as in Moblin.
8. **UVC camera, gimbal, Apple Watch (Wear OS equivalent), chat reader with
   BTTV/FFZ/7TV emotes, full moderation.**
9. **Multiple simultaneous targets** (currently switching is manual, one at a time).
10. **Instrumented encoder tests** on real devices.

Moblin is open source (Swift, iOS): <https://github.com/eerimoq/moblin>.
If you want real parity, the path is to contribute/port specific modules, not to
rewrite everything at once.

---

## Troubleshooting

| Symptom | Likely cause / solution |
|---|---|
| "Video/audio configuration not supported" | Resolution/FPS/codec not supported by the camera. Lower it to 720p30/H.264. |
| Connection drops when the screen locks | check that the live notification is active; disable battery optimisation for the app. |
| Chat does not connect | check the channel name; test without a token first; some channels require login to read. |
| Cannot send messages | username + OAuth token with `chat:edit`; the token must belong to the same user. |
| Upside-down video | tap the orientation button on the main screen and choose Portrait/Landscape, or use Auto to follow the sensor. |
| APK will not install | uninstall the previous version (different signatures) or allow "install unknown apps". |
| VDO.Ninja studio asks "Share your Camera" | tap the button and allow camera/microphone; the app already grants the Android permissions. |
| WHIP returns an error/504 | VDO.Ninja experimental service unavailable — use the P2P studio. |
| USB microphone does not appear | connect it before opening Settings and tap "Refresh list"; check the OTG cable. |

---

## Legal notice

IRL Streaming is an independent project, not affiliated with Moblin, Twitch,
YouTube, Kick, VDO.Ninja, Meta or Google. "Moblin" is mentioned only for link
format compatibility. Respect each platform's terms of service and streaming laws
(privacy, image rights, driving while streaming, etc.).

Code licence for this repository: MIT (suggested). Dependencies keep their own
licences, including RootEncoder (Apache 2.0).
