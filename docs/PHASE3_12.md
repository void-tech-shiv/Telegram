# GLACIA Phase 3.12 — v0.3.17 · Network Awareness (increment 1)

> Experimental milestone. Existing Phase 3.9/3.10 two-way native calls and
> Phase 3.11 ongoing-call notification/background ownership are preserved.
> **This is observation and diagnosis, NOT implemented ICE restart/handover.**

## New features in this increment

- Watches Android's current default network with
  `ConnectivityManager.registerDefaultNetworkCallback` while a real TDLib
  native call exists; unregisters on end or logout.
- A small, non-invasive call-screen status explains `Checking network…`,
  `Network changing…`, `Waiting for network…` or
  `Network may be limited` as needed.
- A replacement network doesn't accidentally get marked disconnected when
  a late `onLost` event for the old default network arrives. Late capability
  events are ignored too.
- No SSID, IP address, carrier, location, ICE credentials or Telegram account
  data are logged, saved or exposed. No location permissions are required.
- Android `ACCESS_NETWORK_STATE` permission. Five pure Kotlin unit tests
  cover online/default replacement, stale loss/capabilities, offline and
  limited networks. There is no new network socket or battery polling loop.
- **No change** to previously working microphone capture, camera capture,
  connection creation, encryption, WebRTC stream descriptions, or TDLib session.

## Explicit limitations

An Android default network becoming available does NOT mean ICE recovered.
The call screen may show a network transition while real two-way audio still
works (e.g., two radios overlap), or show connectivity while the peer remains
unreachable. No silent re-dial, no forced hangup, and no automatic native ICE
restart are attempted. NTgCalls 3.0.0 does not expose a verified recovery
contract in this integration; native recovery needs separate audit and
two-phone tests before enabling it. Do not claim seamless handover.

Phase 3.11 lock-screen, force-stop and Android battery restriction tests
remain pending separately.

## Windows / Android Studio — PowerShell

First checkout:

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-12-network-awareness
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Branch already exists:

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-12-network-awareness
git pull --ff-only origin feature/phase-3-12-network-awareness
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep `glacia.experimentalNativeCalls=true` in the existing root
`local.properties`; do not overwrite or share Telegram API credentials
or `sdk.dir`. Debug-only native calling is off otherwise. The version should
be `versionName=0.3.17`:

```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```

## Two-Android-account real-device gates

1. Begin voice call over Wi-Fi with one GLACIA test account and another
   Telegram test account. Verify two-way audio, then switch the GLACIA phone
   from Wi-Fi to mobile data (cellular data MUST be enabled first).
2. Watch `Network changing…` / warning. Record whether the native call
   survived, reconnected on its own, or actually lost audio. A warning alone
   is **not** proof media failed or recovered.
3. Reverse mobile-data → Wi-Fi. Test short airplane mode toggles separately:
   Android may terminate an active call and no recovery guarantee exists.
4. Repeat with video and moving local/remote frames; check Hang up still
   ends both sides and clears notification + camera/mic indicators.
5. Verify old network lost callbacks do not cause persistent offline hints
   after the replacement network is available.
6. Screen lock, background/foreground, Bluetooth and two consecutive calls
   on the same app process (registration/unregistration).
7. Regression: Telegram text, voice notes, notifications, auth reuse.
8. Keep draft until physical-phone QA; compile/tests alone don't certify
   network handover.

Only share sanitized `GLACIA-VoIP` / `GLACIA-CallSvc` diagnostic stages.
Never share full TDLib debug logs, call keys, user identifiers or private
Telegram session files.
