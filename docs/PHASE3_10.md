# GLACIA Phase 3.10 — v0.3.15 (Liquid Glass Calling)

> **Experimental**, separate draft PR #18 based on draft Phase 3.9 native PR #17.
> The user has verified live two-way Telegram audio and video on Phase 3.9.
> That is **not** equivalent to certifying the new UI on Android devices.

## Implemented

- Replaces the small Android Compose calling dialog with a full-screen
  Midnight Glass call surface: subtle aurora gradients, identity, explicit
  Answer / Hang up controls, large accessible mic, speaker and camera buttons.
- Video calls show decoded real remote frames edge-to-edge and a **real**
  local camera frame overlay (frames are mirrored locally). Both come from
  the existing NTgCalls pipeline; there is no second independent camera capture.
- Front/back camera cycle uses `NTgCalls.getMediaDevices().camera` and
  `setStreamSources(..., CAPTURE, ...)`. A missing second camera or failure is
  shown safely without tearing down the call. Flip has NOT been tested on device.
- Shows a 1-second-resolution call duration after native `CONNECTED` using
  Android's monotonic `elapsedRealtime`, not since user pressed Call.
- Call overlay cannot be dismissed by accidental Back/outside tap. The Hang up
  button remains the explicit way to end an ongoing Telegram call.
- TDLib authentication, signaling, audio/video encoding, ICE servers, and key
  exchange were deliberately preserved from the previously tested Phase 3.9.

## Windows / Android Studio

PowerShell, first checkout:

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-10-liquid-glass-calls
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

If branch already exists:

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-10-liquid-glass-calls
git pull --ff-only origin feature/phase-3-10-liquid-glass-calls
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For Android Studio's Run button, root `local.properties` must contain:

```properties
glacia.experimentalNativeCalls=true
```

Preserve its existing `sdk.dir`, Telegram API ID/hash and other private
settings; never commit or share the file. The debug APK may be installed over
the existing app. Never uninstall/clear data while testing. Production
release builds keep native calling disabled. Check installed version:

```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```

Expect `versionName=0.3.15`.

## Two-account device QA (merge gates)

- [ ] GLACIA -> official Telegram, outgoing voice: both hear audio; correct
  timer, mute/unmute, speaker toggle, hangup, no ghost dialog.
- [ ] Official Telegram -> GLACIA, incoming voice: Answer and Decline respond;
  permission denial does not silently answer.
- [ ] Outgoing and incoming video: moving decoded remote camera feed fills the
  backdrop, and moving local camera appears in the floating YOU thumbnail.
- [ ] Flip camera twice; remote party sees actual front/rear image swap.
  No hanging/black frame, audio dropout, camera stuck after hangup.
- [ ] Portrait, landscape, Android edge-to-edge / dynamic font size and
  display scaling: buttons remain visible and nav/system bars usable.
- [ ] Back and outside taps cannot silently terminate a call; Hang up works.
- [ ] Incoming call while viewing another chat; call end cleans up video renderers
  and allows placing another call without camera/mic conflict.
- [ ] OnePlus Nord 3 real device: verify local capture pipeline, hardware camera
  enumeration and frame rotation, speaker audio with Bluetooth connected.
- [ ] Wi-Fi vs mobile data call duration and reconnection behavior.
- [ ] Regress Phase 3.9 sending voice messages, attachments, notifications,
  login reuse and normal chat navigation.

## Explicit limitations

- Bluetooth headset-specific audio routing, foreground ongoing call service,
  background wake-up/answer, camera/mic interruptions, picture-in-picture,
  screen sharing, polished video quality adaptation, and Telecom integration
  are **not** included here. Do not advertise them as working.
- Local preview and camera flip are new and require actual Android hardware QA;
  a CI compile cannot verify hardware camera behavior.
- NTgCalls native dependency licensing/provenance/security review remains open.
- Do not merge to `main` without both parent PR #17 and this PR passing QA.

Only filtered `GLACIA-VoIP` diagnostic stages may be shared; never share
unfiltered Logcat, credentials, call keys or account-session files.
