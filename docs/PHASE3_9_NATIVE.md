# GLACIA Phase 3.9: experimental native 1:1 voice + video (v0.3.14)

> **Not production-ready, not verified end to end on two real Telegram accounts.**
> Native calling is **OFF** in normal builds. No claim of reliable two-way audio or video is made without device verification.
> The previous external official Telegram handoff is preserved in default builds.

## Implemented

- Build includes `io.github.pytgcalls:ntgcalls:3.0.0` (WebRTC-based Android native AAR); no downloaded hand-crafted binary or bundled API secrets.
- Native protocol negotiation: `NTgCalls.getProtocol()` → TDLib `CallProtocol`.
- Opt-in `CreateCall` (outgoing voice/video) and `AcceptCall` (incoming) using the existing authorized TDLib client **after** on-device native preflight succeeds.
- Real call states (pending, DH/key exchange, ready, native connection changes, hangup), Telegram call-server mapping, native `createP2pCall`, CAPTURE/PLAYBACK audio sources and optional camera, TDLib-supplied E2E key via `skipExchange`, `connectP2p`.
- Bidirectional per-call signaling loop: NTgCalls callback → TDLib `SendCallSignalingData`; TDLib `UpdateNewCallSignalingData` → native `sendSignalingData`.
- Remote decoded video frames to a Compose AndroidView/WebRTC `SurfaceViewRenderer` (I420) in the in-app call dialog.
- Mic/camera runtime permission requests, mute/unmute, basic speaker route toggle, hangup/decline.
- One call at a time; stale server updates cannot overwrite another active call; all messages, keys and sensitive native buffers stay out of logs.
- The parent GLACIA message history, account authorization and package name are unchanged.

## ICE setup fix in v0.3.14 (native transport)

A phone test showed the specific UI error `Native call setup failed at
ice_connect`, not a mere transient connection timeout. Reading the pinned
ntgcalls 3.0.0 source revealed that `P2PCall::connect` calls
`json::parse(*custom_parameters)` for any **non-null string**. Telegram's
TDLib can supply a blank `callStateReady.customParameters`; passing `""`
throws synchronously while starting ICE. This version converts blank/whitespace
parameters to **null**, retaining valid peer-provided JSON unchanged.

`callStateReady.protocol.libraryVersions` describes the remote client's
versions. This version explicitly computes its intersection with the native
library's actual versions before calling `connectP2p`. An incompatible
protocol fails at `protocol_match` instead of an ambiguous `ice_connect`.

In addition, ntgcalls' incoming RTP audio receiver is attached to the
**Playback/Microphone** media slot. GLACIA incorrectly populated
Playback/Speaker, which disabled remote audio reception even if connection
succeeded. This is now corrected; the audio description still uses
`is_microphone=false` to direct PCM to the Android speaker device.

Pure Kotlin unit tests cover blank vs present custom JSON and protocol
intersection. `GLACIA-VoIP` records only numeric server/version counts and
whether custom JSON was present — never the JSON, endpoints, keys or credentials.

**Developer build instructions (PowerShell):**

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-9-native-media-bootstrap
git pull --ff-only origin feature/phase-3-9-native-media-bootstrap
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```

Use `glacia.experimentalNativeCalls=true` in the existing root
`local.properties`. Expect `versionName=0.3.14`. Do not uninstall the app
or expose `local.properties`.

On a test device, try a voice call between **two different test accounts**.
If native setup still fails, capture ONLY these sanitized diagnostics:

```powershell
adb logcat -c
# Reproduce one failing call in GLACIA
adb logcat -d -s "GLACIA-VoIP:V" "*:S" | Select-Object -Last 80
```

A passing CI build cannot verify live ICE, two-way sound or video; keep
PR #17 draft until on-device tests pass.

## Phase 3.9 call drops immediately after pickup — v0.3.14

The original client could end a call as soon as NTgCalls briefly reported
`DISCONNECTED` during ICE negotiation. In addition, TDLib packets arriving
before `CallStateReady` were discarded, causing the WebRTC handshake to fail.
The experimental v0.3.14 branch no longer auto-hangs up on a transient native
disconnect and buffers up to 32 early signaling packets in memory, flushing
them only after native initialization. Buffers are dropped on call end; media
bytes, encryption keys, users, endpoints and signaling payloads are not logged.

Native setup now reports safe, predefined stages such as `create_p2p`,
`audio_capture`, `audio_playback`, `key_exchange`, `audio_routing` or
`ice_connect`. Telegram `callStateError` displays its numeric TDLib code.
This makes a failed call diagnosable without exposing private account data.

**OnePlus / Android Studio debug procedure:**

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-9-native-media-bootstrap
git pull --ff-only origin feature/phase-3-9-native-media-bootstrap
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```

Expect v0.3.14 and two header call controls in GLACIA. Keep
`glacia.experimentalNativeCalls=true` in **root local.properties** so an
Android Studio Run rebuild does not disable them.

Make ONE test voice call between distinct test accounts; if still dropping,
capture **only GLACIA's sanitized stage/state diagnostics** (not full device or
TDLib logs):

```powershell
adb logcat -d -s "GLACIA-VoIP:V" "*:S" | Select-Object -Last 70
```

Share that filtered text and the exact UI error, not raw Telegram API
credentials, user/session files, or an unfiltered Logcat. Native C++ crashes
may require separate local tombstone investigation; sanitize before sharing.

A passing CI build does not certify two-way voice/video, background calling,
Bluetooth routing or call recovery. Keep PR #17 draft pending real-device QA.

## Security / reliability warnings

1. The NTgCalls JNI binaries and packaged third-party dependencies require **manual provenance, license (LGPL-3.0), ABI and vulnerability review** before shipping.
2. **Experimental media engine is not yet certified** to work with official Telegram or avoid native crashes on all devices. A successful Android compile is not evidence of end-to-end two-way media. Run the device matrix below.
3. Full Android 14+/15+/16 ongoing-call foreground services, Telecom/ConnectionService, Bluetooth headset negotiation, audio focus interruption, camera flip and persistent call notifications are **not implemented**. Do not rely on calls in background.
4. For video, basic capture from the first camera and remote playback are wired. Local camera preview, front/back switch, adaptive bitrates and network recovery are not done. Remote rendering path has not been exercised on device.
5. This PR is a dedicated experimental branch based on Phase 3.9 incoming call PR #16, itself a draft. Do not merge to main or enable in a public APK until regressions + privacy review pass.

## Android Studio Run fix (v0.3.12)

If your chat call button still offers **Open Telegram**, the running APK has
`BuildConfig.EXPERIMENTAL_NATIVE_CALLS=false`. This can happen because the
normal Android Studio Run button **does not inherit** an earlier terminal
`-Pglacia.experimentalNativeCalls=true` flag or because an older APK remains
installed. Do not delete user data to troubleshoot this.

Enable experimental calling **only for local debug APKs** by adding this line
to the project's existing **root `local.properties`** (next to `settings.gradle.kts`):

```properties
glacia.experimentalNativeCalls=true
```

Keep the rest of `local.properties` unchanged, especially private Telegram
API credentials and `sdk.dir`. In Android Studio choose **Sync Project with Gradle Files**,
then **Build > Rebuild Project**, connect the phone, and click **Run app**.
The debug APK will now expose separate voice/video call buttons; the release
APK always keeps native calling OFF regardless of this local setting. If the
property is absent or false, the external handoff stays available.

Verify installed version using `adb shell dumpsys package dev.glacia.app` and
confirm that `versionName=0.3.13` appears after Run. For additional debugging
inspect the generated `app/build/generated/source/buildConfig/debug/dev/glacia/app/BuildConfig.java`
for `EXPERIMENTAL_NATIVE_CALLS = true`; if your AGP uses a different generated
location, use Android Studio's Find in Files.

**Do not modify or reset unrelated local `gradle.properties`,
`gradle/wrapper/gradle-wrapper.properties`, `settings.gradle.kts` without
reviewing the local diffs.**

## Build and test on Windows / Android Studio

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-9-native-media-bootstrap
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest "-Pglacia.experimentalNativeCalls=true"
```

Already checked out:
```powershell
git switch feature/phase-3-9-native-media-bootstrap
git pull --ff-only origin feature/phase-3-9-native-media-bootstrap
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest "-Pglacia.experimentalNativeCalls=true"
```


**PowerShell troubleshooting:** Some PowerShell versions split an unquoted `-Pglacia.experimentalNativeCalls=true` at the dot, making Gradle search for a nonexistent task `.experimentalNativeCalls=true`. Keep the entire flag in quotes as shown, or use the PowerShell stop-parsing token:

```powershell
.\gradlew.bat --% :app:assembleDebug :app:testDebugUnitTest -Pglacia.experimentalNativeCalls=true
```

The initial setup commands use `git switch --track` only once; after that switch and pull the existing branch. Preserve uncommitted changes in `gradle.properties`, `settings.gradle.kts`, and the wrapper, unless reviewed separately.

**Important:** for Android Studio's usual Run button, use root `local.properties` (**never commit this file**) with:
```properties
glacia.experimentalNativeCalls=true
```
Alternatively launch the opt-in Gradle task above and install its debug APK over the existing app via `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Note that clicking Android Studio Run afterwards rebuilds the app: use `local.properties` if you want the opt-in to persist. Do not uninstall GLACIA or clear app data; no private Telegram API ID/hash is committed. The CI APK is unconfigured and not capable of signing in.

To turn it off, delete that local property or set `glacia.experimentalNativeCalls=false`, rebuild, reinstall.

## Real-device acceptance matrix — REQUIRE two disposable Telegram accounts

- [ ] Both Android debug and JVM test tasks pass; native .so loads with no UnsatisfiedLinkError or process crash.
- [ ] Verify local native protocol version matches compatible versions from TDLib/Telegram; do not blindly hardcode them.
- [ ] Voice: outgoing GLACIA → official Telegram answers → **both parties hear each other**; check connection state, earpiece/speaker, mic mute, hangup on either side.
- [ ] Voice: official Telegram → GLACIA incoming → grant mic permission → answer → both sides hear each other. Reject, time out and denial must not create ghost calls.
- [ ] Video: outgoing/incoming; remote receives camera frames and local receives visible remote I420 video; check camera permission denial and audio-only mode.
- [ ] Wi-Fi ↔ mobile data, NAT/relay restrictions, call interruptions, phone screen lock, orientation change, Android 14+ microphone/camera indicators.
- [ ] Real hardware particularly MediaTek/OnePlus (audio route change and stop/close races), tablet, and ABI differences; inspect Logcat tombstones without including user keys/data.
- [ ] Test parallel missed/ended calls, FCM process restart, app background/foreground and logout mid-call.
- [ ] Regression: TDLib login reuse, text/send/edit/delete/reactions, voice notes, photo/document send, notifications, themes.
- [ ] Data minimization: call keys, passwords, server credentials and signaling payload never enter debug logs, analytics or screenshots.
- [ ] Verify clean-build and license obligations for NTgCalls native AAR and its WebRTC components.

**A native `CONNECTED` callback alone does not pass any audio or video test. Real remote audio/video is required.**
