# iOS app — IRL Streaming (MVP)

Native SwiftUI app focused on the **VDO.Ninja P2P studio**. It publishes the
iPhone camera and microphone through an embedded `WKWebView`, generates the
publish/view links, and includes a basic Twitch chat.

- **Bundle identifier:** `app.streamlink.ios`
- **Deployment target:** iOS 16.0
- **Version:** 0.8.0 (build 1)
- **Project generator:** [XcodeGen](https://github.com/yonaskolb/XcodeGen)

## Structure

| File | Purpose |
|---|---|
| `project.yml` | XcodeGen spec (target, Info.plist, signing, scheme) |
| `IRLStreaming/IRLStreamingApp.swift` | App entry point and audio session |
| `IRLStreaming/RootView.swift` | Tab navigation (Studio, Links, Chat, Settings) |
| `IRLStreaming/StudioView.swift` | Embedded VDO.Ninja studio (`WKWebView`) |
| `IRLStreaming/VdoLinksView.swift` | Stream ID/room, quality and publish/view links |
| `IRLStreaming/ChatView.swift` | Twitch chat UI, overlay and configuration |
| `IRLStreaming/TwitchChatClient.swift` | Twitch IRC over WebSocket client |
| `IRLStreaming/SettingsView.swift` | Language, keep screen on, privacy, reset |
| `IRLStreaming/Models.swift` | Codable settings models |
| `IRLStreaming/SettingsStore.swift` | `UserDefaults` persistence |
| `IRLStreaming/VdoNinjaLinks.swift` | VDO.Ninja URL builder and link parser |
| `IRLStreaming/UI.swift` | Shared theme and components |

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

- Background streaming is not supported by iOS for WebRTC; keep the app open.
- No native RTMP/SRT/WHIP encoder, no local recording and no UVC camera support.
- A future version can add native WebRTC (for example with Google WebRTC or
  LiveKit) and ReplayKit if background audio/video becomes a requirement.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `xcodegen: command not found` | `brew install xcodegen` |
| “Signing for IRLStreaming requires a development team” | Select your team in **Signing & Capabilities** |
| Camera/microphone prompt never appears | Check **Settings → Privacy & Security → Camera/Microphone → IRL Streaming**; reinstall if needed |
| VDO.Ninja shows a black preview | Allow camera access, check the stream ID, and make sure no other app is using the camera |
| Chat stays “Connecting…” | Verify the channel name (without `#`) and the network; a wrong token only affects sending |
