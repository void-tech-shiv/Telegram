# GLACIA Phase 3.9: experimental native 1:1 voice + video (v0.3.11)

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

## Security / reliability warnings

1. The NTgCalls JNI binaries and packaged third-party dependencies require **manual provenance, license (LGPL-3.0), ABI and vulnerability review** before shipping.
2. **Experimental media engine is not yet certified** to work with official Telegram or avoid native crashes on all devices. A successful Android compile is not evidence of end-to-end two-way media. Run the device matrix below.
3. Full Android 14+/15+/16 ongoing-call foreground services, Telecom/ConnectionService, Bluetooth headset negotiation, audio focus interruption, camera flip and persistent call notifications are **not implemented**. Do not rely on calls in background.
4. For video, basic capture from the first camera and remote playback are wired. Local camera preview, front/back switch, adaptive bitrates and network recovery are not done. Remote rendering path has not been exercised on device.
5. This PR is a dedicated experimental branch based on Phase 3.9 incoming call PR #16, itself a draft. Do not merge to main or enable in a public APK until regressions + privacy review pass.

## Build and test on Windows / Android Studio

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-9-native-media-bootstrap
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -Pglacia.experimentalNativeCalls=true
```

Already checked out:
```powershell
git switch feature/phase-3-9-native-media-bootstrap
git pull --ff-only origin feature/phase-3-9-native-media-bootstrap
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest -Pglacia.experimentalNativeCalls=true
```

**Important:** in Android Studio's usual Run button, the Gradle property must also be set in root `gradle.properties` **locally** (ignored if it contains private settings) with:
```properties
glacia.experimentalNativeCalls=true
```
Alternatively launch the opt-in Gradle task above and install its debug APK over the existing app via `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Do not uninstall GLACIA or clear app data; no private Telegram API ID/hash is committed. The CI APK is unconfigured and not capable of signing in.

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
