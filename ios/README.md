# iOS app — IRL Streaming

Native SwiftUI app with two publishing paths:

- **Live / RTMP**: native capture and encoding (H.264/HEVC + AAC) sent to any
  RTMP or RTMPS ingest server, with local recording, multiple targets and
  automatic reconnection.
- **P2P**: the VDO.Ninja studio running inside an embedded `WKWebView`, with
  publish/view links and a basic Twitch chat overlay.

- **Bundle identifier:** `app.streamlink.ios` (change it if your team cannot
  register it; see the troubleshooting note below)
- **Deployment target:** iOS 16.0
- **Version:** 0.9.0 (build 1)
- **Project generator:** [XcodeGen](https://github.com/yonaskolb/XcodeGen)
- **RTMP stack:** [HaishinKit](https://github.com/shogo4405/HaishinKit.swift)
  2.2.5 (`HaishinKit` + `RTMPHaishinKit` products)

> **Status:** built with Xcode 27.1 and tested on a physical iPhone 13. Native
> RTMP publishing, local recording, mute, camera switching, zoom, reconnection
> and VDO.Ninja P2P publishing were verified on the device.

## Structure

| File | Purpose |
|---|---|
| `project.yml` | XcodeGen spec (target, Info.plist, signing, scheme, packages) |
| `IRLStreaming/IRLStreamingApp.swift` | App entry point, audio session and engine injection |
| `IRLStreaming/RootView.swift` | Tab navigation (Live, P2P, Links, Chat, Settings) |
| `IRLStreaming/RtmpEngine.swift` | Native RTMP/RTMPS engine: capture, encoder, targets, recording, reconnection, preview |
| `IRLStreaming/RtmpView.swift` | Live studio: camera preview, controls, quality sheet, lifecycle |
| `IRLStreaming/RtmpTargetsView.swift` | RTMP/RTMPS target list (create, edit, select, delete) |
| `IRLStreaming/StudioView.swift` | Embedded VDO.Ninja studio (`WKWebView`) |
| `IRLStreaming/VdoLinksView.swift` | Stream ID/room, quality and publish/view links |
| `IRLStreaming/ChatView.swift` | Twitch chat UI, overlay and configuration |
| `IRLStreaming/TwitchChatClient.swift` | Twitch IRC over WebSocket client |
| `IRLStreaming/SettingsView.swift` | Language, keep screen on, privacy, reset |
| `IRLStreaming/Models.swift` | Codable settings models (VDO.Ninja, native/RTMP, chat) |
| `IRLStreaming/SettingsStore.swift` | `UserDefaults` persistence |
| `IRLStreaming/VdoNinjaLinks.swift` | VDO.Ninja URL builder and link parser |
| `IRLStreaming/UI.swift` | Shared theme and components |

## Live (RTMP/RTMPS)

The **Live** tab owns the native camera and microphone. It offers:

- **Targets** (list button): named ingest URL + stream key, validated for
  `rtmp://` and `rtmps://`; the selected target is used by the stream button.
- **Quality** (sliders button): 480p/720p/1080p, 24/30/60 fps, H.264/HEVC,
  portrait/landscape, video bitrate, audio bitrate, mono/stereo and automatic
  reconnection.
- **Controls**: mute, front/rear camera, torch, recording and zoom.
- **Status**: live indicator, elapsed time, measured bitrate and FPS.
- **Reconnection**: up to 5 attempts with exponential backoff (2–16 s). A fresh
  RTMP stream is created for each attempt so codec headers are never reused from
  a previous connection.
- **Local recording**: MP4 written to the app's `Documents` folder while
  publishing; the path is shown when the recording is saved.

The final RTMP URL is `url/streamKey` (for example
`rtmp://ingest.example.com/live` + `my-stream-key`). Never publish real keys in
screenshots, issues or documentation.

### Lifecycle

iOS does not allow background camera capture or arbitrary background
networking. The app therefore:

- stops publishing and releases the camera/microphone when it goes to the
  background;
- releases capture when you switch to the **P2P** tab, so the VDO.Ninja studio
  can use the camera;
- re-creates the preview when you return to **Live**.

Keep the app in the foreground while streaming.

## Generate the Xcode project

```bash
brew install xcodegen   # once
cd ios
xcodegen generate
open IRLStreaming.xcodeproj
```

`IRLStreaming.xcodeproj` is committed and can be opened directly. Keep
`project.yml` as the source of truth and regenerate after adding or moving files.

To check that the project builds without opening Xcode:

```bash
cd ios
xcodebuild -project IRLStreaming.xcodeproj -scheme IRLStreaming \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build
```

## Run on an iPhone

1. Open `IRLStreaming.xcodeproj` in Xcode 15 or newer.
2. Select the **IRLStreaming** target → **Signing & Capabilities** and pick your
   Apple team. If the bundle identifier is already taken, change it to something
   unique (for example `app.streamlink.ios.yourname`).
3. Connect the iPhone, enable **Developer Mode** (iOS 16+: Settings → Privacy &
   Security → Developer Mode), then select the device and press **Run**.
4. Allow camera and microphone access on first launch.

With a **free Apple ID** the signature expires after 7 days and the app must be
reinstalled from Xcode. A paid Apple Developer account enables TestFlight and
longer installs.

To archive: **Product → Archive**, then distribute through Xcode Organizer or
TestFlight. An unsigned `.ipa` is not part of the repository.

### Install from the command line

Useful once the Apple team is configured in Xcode. Replace the team ID, the
bundle identifier and the device UDID with your own values:

```bash
cd ios
xcodebuild -project IRLStreaming.xcodeproj -scheme IRLStreaming \
  -configuration Debug -sdk iphoneos \
  -destination 'id=YOUR_DEVICE_UDID' \
  -allowProvisioningUpdates \
  DEVELOPMENT_TEAM=YOUR_TEAM_ID \
  PRODUCT_BUNDLE_IDENTIFIER=app.streamlink.ios.yourname \
  CODE_SIGN_STYLE=Automatic build

xcrun devicectl device install app --device YOUR_DEVICE_UDID \
  ~/Library/Developer/Xcode/DerivedData/IRLStreaming-*/Build/Products/Debug-iphoneos/IRLStreaming.app
```

If Xcode reports that `app.streamlink.ios` is not available for your team, add
a unique suffix in **Signing & Capabilities** or through
`PRODUCT_BUNDLE_IDENTIFIER` as shown above. Free teams still need to trust the
developer certificate on the device (**Settings → General → VPN & Device
Management → Developer App**).

## VDO.Ninja links

The app uses the same conventions as the Android version:

- Publish: `https://vdo.ninja/?push=ID&password=...`
- View: `https://vdo.ninja/?view=ID&p=...`
- Room: fill **Room** to use `?push=ROOM` / `?view=ROOM` when no stream ID is set.
- Paste an existing link in **Links → Paste VDO.Ninja link and fill in**.

Examples in the UI are fictional (`meu-stream-7f3a`, `irl-casa-9f2`). Use your own
values and avoid sharing links that contain a real password.

## Twitch chat

- **Read-only:** set the channel in **Chat → gear icon**. No account is needed.
- **Sending:** add your Twitch username and an OAuth token with `chat:read` and
  `chat:edit`. The token is stored only in `UserDefaults` on the device.
- The overlay is controlled by **Chat → gear icon → Show chat over the studio**.

## Known limitations

- **No background streaming:** iOS suspends camera capture and networking when
  the app leaves the foreground. Keep the app open while publishing.
- **No SRT, WHIP or RTSP** on iOS; only RTMP and RTMPS.
- **No UVC camera support** and **no two-camera picture-in-picture**; those
  require additional AVFoundation work and hardware support.
- **No backup target failover** (the Android app has a backup URL; iOS switches
  manually).
- A free Apple ID signature expires after **7 days**; TestFlight and long-lived
  installs require a paid Apple Developer account.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `xcodegen: command not found` | `brew install xcodegen` |
| “Signing for IRLStreaming requires a development team” | Select your team in **Signing & Capabilities** |
| Camera/microphone prompt never appears | Check **Settings → Privacy & Security → Camera/Microphone → IRL Streaming**; reinstall if needed |
| VDO.Ninja shows a black preview | Allow camera access, check the stream ID, and make sure no other app is using the camera |
| Chat stays “Connecting…” | Verify the channel name (without `#`) and the network; a wrong token only affects sending |
| RTMP stays “Connecting…” or fails immediately | Check the ingest URL (`rtmp://`/`rtmps://`), the port and the stream key; confirm the phone can reach the server |
| RTMP drops and reconnects | Expected on Wi-Fi changes; the app retries 5 times. Check the ingest server limits and the measured bitrate |
| Black preview in **Live** | The selected camera may be covered or in use; switch camera or close other camera apps. Capture is re-created when you return to the tab |
| Package resolution fails after changing the project | Run `xcodebuild -resolvePackageDependencies`; if a stale checkout remains, delete `~/Library/Developer/Xcode/DerivedData/IRLStreaming-*/SourcePackages` and resolve again |
