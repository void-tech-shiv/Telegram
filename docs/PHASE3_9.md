# GLACIA Phase 3.9 — v0.3.10 · Incoming call foundation

> **Experimental implementation, not yet a native voice/video client.**
> Branch: `feature/phase-3-9-incoming-call-foundation`. Parent: Phase 3.8 draft branch.
> No changes to user auth, TDLib database location, or app package ID.

## Implemented in this first Phase 3.9 increment

- Subscribe to real TDLib `UpdateCall` events on the existing authorized client, *only* for incoming, pending one-to-one calls.
- Present a foreground Compose dialog distinguishing voice and video and resolving the caller's display name via `GetUser`.
- **Decline** an incoming invitation via TDLib `DiscardCall`. Respect in-flight requests, show only the Telegram error code on failure, and wait for confirmation before clearing a call.
- Ignore an invitation without sending any action (no false implication that ignoring equals declining).
- Open the **official Telegram app**, even for contacts without public usernames. Users must manually answer there; GLACIA can't answer yet.
- Handle cancelled/ended/connected calls without stale UI; a delayed end or name-lookup callback from an older call cannot overwrite a newer pending call.
- Session logout/close removes in-memory call dialog state. Nothing sensitive from calling or user records is persisted or logged.
- Pure Kotlin reducer unit tests cover voice vs video, stale end/name callbacks, duplicated ringing updates, and invalid IDs.

## Explicit non-goals — NOT shipped

- In-app **answer**, outgoing `CreateCall`, microphone audio, camera video, screensharing, encryption/media negotiation or a simulated connecting spinner.
- Actual secure two-way media needs a compatible, independently verified native **tgcalls/WebRTC** engine connected to TDLib's call state and signaling APIs.
- Incoming call OS full-screen notifications, background wake-up, Bluetooth/earpiece routing, Telecom integration or group calls.
- Foreground UI invitation is only observable while GLACIA's TDLib process is alive. Do not rely on it for emergency or time-sensitive calls.
- No CAMERA or foreground microphone service permission is requested for this scaffold; permissions belong to the later audited media layer.

**Why no outgoing native call yet:** starting `CreateCall` without any media transport can ring other people even though the GLACIA user can never talk to them. That is deceptive and potentially disruptive. The existing explicit external call-profile handoff remains.

## Manual device QA (two Telegram accounts)

1. Build and install the debug APK *over* your existing GLACIA install (do not clear app data). Confirm login reuse, sending, recording and notifications still work.
2. With GLACIA foregrounded on phone A and signed into a test Telegram account, place a voice call from account B. Confirm the real incoming voice dialog appears, with accurate caller name.
3. Press **Ignore**. Confirm it doesn't automatically accept, reject or mark the call as completed. The TDLib/server controls eventual timeout.
4. Repeat with a fresh call. Press **Decline** and verify B sees the rejection; GLACIA dialog closes and no duplicate request occurs.
5. Repeat as a **video** call. Confirm video labeling, correct rejection, no camera permission prompt, no camera capture.
6. Use **Open Telegram**. Confirm the official app launches (if installed) without requiring the caller to have a username. The user manually accepts in that app; GLACIA never shows a fake connected state.
7. Cancel a call on B while dialog is visible: dialog must disappear. Race: call B1 ends while call B2 starts: B1's delayed callbacks must not close B2.
8. Check if the official Telegram app is absent and confirm GLACIA shows a safe non-crashing error.
9. Check app background, process death, configuration change, logout/login, and different Android versions. Background alert / answering is **not supported**.
10. Regress message actions, reply/forward/edit/delete/reactions, voice notes, attachment uploads, unread/read receipt behavior and theme change.

## Build / CI

```powershell
git fetch origin
git switch --track origin/feature/phase-3-9-incoming-call-foundation
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Public CI APK is unconfigured and cannot access Telegram; do not upload `local.properties`, authorization codes, session/database files, Firebase credentials or any call key material.

## Proposed next increments

1. Audit the exact `io.github.tdlib-android:core` artifact / TDLib version and native binaries. Choose a reproducible compatible and auditable tgcalls/WebRTC distribution (Android ABIs, license, crypto, SRTP/ICE/TURN, dependency provenance).
2. Implement a real `CallMediaEngine` interface with native peer transport; encode/decode microphone/camera media and pass TDLib `UpdateNewCallSignalingData` to tgcalls. Gate `CreateCall`/`AcceptCall` strictly on operational engine initialization.
3. Add Android runtime mic/camera permission requests, audio focus, earpiece/speaker/Bluetooth route switching, picture-in-picture and lifecycle-safe foreground ongoing call service/notifications as Android OS versions require.
4. Verify device interoperability and encryption fingerprint with the official Telegram client, NAT traversal (mobile data ↔ Wi-Fi), network loss/reconnect, backgrounding, permission denial, call termination, and OS process death.

**Merge gate:** CI assembly/tests **and** real-device QA using two test Telegram accounts. Keep draft until complete. External handoff is not an in-app call.
