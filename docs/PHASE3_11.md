# GLACIA Phase 3.11 — v0.3.16 (call lifecycle foundation)

> **Draft/experimental Android call lifecycle work.**
> This does NOT guarantee incoming calls after GLACIA is killed, full-screen
> OS-level incoming call UI, or uninterrupted mobile data/Wi-Fi handover.

## Implemented

- An explicit `GlaciaCallService` Android foreground service, declared with
  `microphone|camera` and Android 14+ specific service permissions.
- The service starts **only** after a visible user action (Call or Answer) and
  granted runtime microphone/camera permissions, never from a background
  TDLib or FCM callback. Android 12+ and Android 14+ restrict background
  starts and while-in-use microphone/camera permissions.
- The service displays a **privacy-preserving ongoing call notification** with
  an Open GLACIA tap target and explicit **Hang up** action. The ongoing
  notification remains low-importance; no fake system dialer integration.
- `GlaciaPushRuntime` uses a new `callServiceOwners` reference count to keep
  the **same TDLib client/database** alive when the UI is no longer attached.
  No second TDLib session is launched.
- Service is `START_NOT_STICKY`. It stops after TDLib reports the call ended,
  and cleans up on abort/rejection. Startup without an acknowledged call times
  out after 45 seconds, rather than capturing indefinitely.
- Best-effort incoming call high-priority notification whenever an already
  running TDLib client receives an incoming `UpdateCall` while the UI is
  backgrounded. Notification only shows call type; not caller identity,
  message text or account details; lock-screen visibility is SECRET. It
  opens GLACIA to manually answer/decline. This is intentionally **not**
  a full-screen intent, auto-answer or background microphone capture.
- Existing two-way NTgCalls audio/video, encrypted call negotiation, camera
  preview/flip and full-screen Phase 3.10 Liquid Glass UI remain unchanged.

## NOT implemented / limitations

- **Process-death incoming calling**: Android does not keep TDLib alive
  reliably after the process dies. Optional FCM can sometimes start TDLib
  briefly for a push; it is not an independently audited VoIP push/registration
  system. Force-stopped apps do not receive push until manually opened.
- **Android full-screen incoming call intent**: not implemented. Android 14+
  limits this special permission; users may deny it. Current behavior is
  an ordinary high-priority heads-up notification with tap-to-open.
- Full Android Telecom/ConnectionService integration, Bluetooth interruption
  handling, phone-call audio focus, Doze and OEM battery exemptions are pending.
- Camera+microphone foreground service must start while Activity is visible.
  This milestone doesn't and must not attempt to bypass Android while-in-use
  restrictions by creating a microphone/camera service from a background
  callback.
- No TURN media failover code was changed. Wi-Fi/mobile data interruption or
  ICE recovery is **not certified** by unit tests.
- App-process kill can terminate the call; the non-sticky service is not an
  always-on daemon. Background execution policy varies across OnePlus/OEMs.
- CI compilation is not device certification; keep PR draft until both
  existing calling and background checks pass.

## Windows / Android Studio — copy & paste

Initial checkout (only once):

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-11-call-lifecycle
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Already have the branch:

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-11-call-lifecycle
git pull --ff-only origin feature/phase-3-11-call-lifecycle
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

For Android Studio's **Run** button, keep this line in the existing root
`local.properties` (do not overwrite other SDK/Telegram API settings):

```properties
glacia.experimentalNativeCalls=true
```

If running Gradle CLI without that local line, use quoted PowerShell flag:
`"-Pglacia.experimentalNativeCalls=true"`.

Check installed debug version:

```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```

Expected `versionName=0.3.16`. Never uninstall or clear GLACIA data as
part of upgrading.

## Device QA (two separate test Telegram accounts)

- [ ] Voice outgoing/incoming works; both sides hear audio with GLACIA
  foregrounded; check mute, speaker, hangup, repeat.
- [ ] Video outgoing/incoming works, moving remote and local camera preview,
  camera flip in both directions; test camera permission denial.
- [ ] Start from GLACIA foreground → press Home during an established call;
  ongoing low-priority GLACIA notification stays visible, both parties keep
  hearing audio, tap notification returns to the same call.
- [ ] Swipe GLACIA out of Recents with ongoing call; observe OEM policy.
  Validate no duplicate login/TDLib database opens; some OEMs may terminate
  the process, which is NOT supported yet.
- [ ] Ongoing notification **Hang up** actually ends call remotely and the
  foreground service/notification disappear; no microphone/camera indicator
  remains after cleanup.
- [ ] An incoming call while UI backgrounded AND TDLib already running gives
  a privacy-safe heads-up alert; tap returns to GLACIA and answers normally.
  **Do not claim** it works from force-stop or a cold process.
- [ ] Deny Android 13+ notification permission, revoke mic/camera before call,
  turn off app notifications/channel, and verify no crash or stealth capture.
- [ ] Android 14/15/16 foreground service restrictions with screen locked,
  screen off/on, battery optimizations and permission changes.
- [ ] Bluetooth route, Wi-Fi → cellular, reconnect, airplane-mode and
  competing system calls; record any drop with sanitized `GLACIA-VoIP` logs.
- [ ] Confirm TDLib message notifications, message actions, media/voice notes,
  auth/session reuse and Liquid Glass navigation unchanged.

Only share `GLACIA-VoIP` and `GLACIA-CallSvc` filtered log tags, not raw
TDLib logs, call keys, users, tokens or private `local.properties`.
