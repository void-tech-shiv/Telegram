# GLACIA — Liquid Glass Telegram Client

Independent Android Telegram client developed in **Kotlin + Jetpack Compose + TDLib**. Midnight Glass and Frosted Daylight themes, TDLib Telegram session, real chats/messaging, images/documents, Media3 video/voice playback, Discover search, unread filters, read receipts and typing indicators.

## Experimental developer build: v0.3.20 (Phase 3.14 — stickers / GIFs)

**Branch:** `feature/phase-3-14-stickers-gifs` · **Status:** draft, phone QA required.

- Real TDLib recent-sticker and saved-GIF picker in conversations;
  explicitly send a chosen sticker or saved GIF without any new server.
- Incoming static WEBP stickers display previews; TGS/video stickers use
  a safe thumbnail fallback. GIF messages show preview with explicit
  Download/Play; no auto-download of original GIFs.
- The existing Telegram reaction 400 fix, calling engine and background
  notifications remain intact. Sticker catalog search and animated custom
  emoji are **not yet implemented**; see `docs/PHASE3_14.md`.

## Previous experimental bugfix: v0.3.19 (Telegram reaction 400)

**Branch:** `fix/reaction-eligibility-telegram-400` · **Status:** draft PR #24; Android-device QA required.

- Fixed an invalid emoji action menu on video/voice call history events:
  Telegram code 400 was caused by trying to react to a service event.
- Standard message emoji choices are now loaded from Telegram's
  `GetMessageAvailableReactions` rather than being guessed from a fixed grid.
- Existing calling, message delivery, chat archive/pin and search code
  remain untouched. See `docs/REACTION_400_FIX.md` for Windows PowerShell
  commands, expected behavior and regression tests.

## Previous experimental developer build: v0.3.18 (Phase 3.13 — Telegram feature expansion)

**Branch:** `feature/phase-3-13-chat-management-search` · **Status:** draft, two-account device QA required.

- Added Telegram-synced **Main/Archived** chat views, archive/unarchive
  and pin/unpin actions with per-chat feedback and safe errors.
- Added **Search in chat** backed by TDLib, paginated remote history,
  stale-response protection and tap-to-jump to matching message context.
- Preserved existing two-way voice/video, Liquid Glass full-screen calls,
  background audio and notification Hang up. No new native media changes.
- Telegram has many more features; this is a first parity batch, **not**
  complete Telegram parity. See `docs/PHASE3_13.md` for feature roadmap,
  exact Windows commands and the manual device test matrix.

## Previous experimental developer build: v0.3.17 (Phase 3.12 — network awareness)

**Branch:** `feature/phase-3-12-network-awareness` · **Status:** draft / device QA required.

- Real-device validation confirmed that Phase 3.11 background audio continues
  and the notification **Hang up** action works. Lock-screen tests remain pending.
- Track Android default network while a real call exists, with safe network
  transition/loss hints in Liquid Glass calling UI. Clean up observer on call
  end or logout. Unit tests cover stale event order and Wi-Fi↔mobile changes.
- **Not seamless WebRTC network recovery:** Android connectivity observation
  alone does not restart ICE or guarantee two-way audio survives switchovers.
  The existing TDLib/NTgCalls media implementation is unchanged.
- See `docs/PHASE3_12.md` for PowerShell checkout/build/install and test matrix.

## Previous experimental developer build: v0.3.16 (Phase 3.11 — ongoing call lifecycle)

**Branch:** `feature/phase-3-11-call-lifecycle` · **Status:** draft, real-device background testing required.

- Existing two-way TDLib/NTgCalls media is preserved. After a **visible user**
  initiates/answers a call with permissions granted, Android starts a typed
  microphone/camera foreground service and ongoing notification with Hang up.
- The service owns the same TDLib process session while the UI backgrounds,
  rather than opening another database/client. It is not a boot daemon.
- Incoming-call heads-up alerts are **best effort while TDLib is already
  alive**; tapping opens GLACIA. Calls after process kill/force-stop, full-screen
  system incoming UI, automatic push wake and network handover are **NOT guaranteed**.
- See `docs/PHASE3_11.md` for limitations, Android 14+ permission boundaries
  and exact PowerShell checkout/build/install + two-device QA.

## Previous experimental developer build: v0.3.15 (Phase 3.10 — full-screen calling UI)

**Branch:** `feature/phase-3-10-liquid-glass-calls` · **Status:** draft PR #18; review before merging.

- The user confirmed real two-way Phase 3.9 audio and video on device. This
  branch **preserves that working engine** and replaces the call popup with a
  full-screen Liquid Glass overlay, remote video background, mirrored local
  video preview, camera flip, and a monotonic call timer.
- Mute/speaker/answer/hangup remain wired to real TDLib/NTgCalls calls.
- **New camera preview and flip still need real-device QA.** An optional
  Android 12+ Bluetooth route button now selects already-connected headsets,
  requesting permission only when tapped; device QA is still required.
  Background/ongoing-call service and native dependency review remain open.
- See `docs/PHASE3_10.md` for exact Windows Git/build/install instructions
  and the full two-account device test matrix.

## Previous experimental developer build: v0.3.14 (Phase 3.9 — native voice/video work)

**Branch:** `feature/phase-3-9-native-media-bootstrap` · **Status:** unverified experimental calling; keep draft. **Normal builds keep calling disabled and use external official Telegram handoff.**

### Native media integration (EXPERIMENTAL)
- Adds NTgCalls 3.0.0/WebRTC for true microphone capture, speaker playback, optional camera capture and remote-frame rendering.
- Adds real outgoing/incoming TDLib CreateCall/AcceptCall and native encrypted media/signaling connection work.
- **Android Studio Run:** opt into experimental calls by adding `glacia.experimentalNativeCalls=true` to the existing root `local.properties`, then Sync/Build/Run. Alternatively use a quoted `"-Pglacia.experimentalNativeCalls=true"` with Gradle and install that specific APK; IDE Run without the local flag will rebuild with calls OFF.
- Only debug builds can enable native calls; release builds always disable them. Microphone/camera permission and two-account phone testing are still required.
- **Build passing ≠ two-way calls verified**; no production claims until remote audio/video is demonstrated on two actual accounts and security/device QA passes.
- See `docs/PHASE3_9_NATIVE.md` for testing requirements and safe build commands. If you still see the official Telegram handoff dialog, the running APK has native calling OFF.
- **Instant hang-up on answering:** v0.3.14 buffers early TDLib signaling, stops discarding on transient ICE `DISCONNECTED`, and adds redacted `GLACIA-VoIP` diagnostic stages. Real calls still require device QA.
- **Immediate `ice_connect` error:** v0.3.14 stops passing blank custom Telegram JSON to ntgcalls' JSON parser and restricts media negotiation to mutually supported protocols. Also fixes the remote audio playback slot. See `docs/PHASE3_9_NATIVE.md`.

## Previous developer build: v0.3.10 (Phase 3.9 — incoming-call foundation)

**Branch:** `feature/phase-3-9-incoming-call-foundation` · **Status:** experimental draft, not merged or production certified.

### Incoming-call foundation in v0.3.10
- Detect real incoming 1:1 voice/video `UpdateCall` invitations while GLACIA is active, show caller and call type.
- **Decline** an incoming invitation using Telegram's actual `DiscardCall` method; **Ignore** leaves it unanswered.
- Open the official Telegram app to answer manually, even for contacts without a username.
- Caller-name lookup, in-flight decline handling, stale-update protection and new reducer unit tests.
- **Native in-app answer/outgoing audio/video is NOT implemented.** No tgcalls/WebRTC engine, camera stream, or voice transport is present. See `docs/PHASE3_9.md`.

### Message actions and calls in v0.3.9
- Long-press a message to Reply / Forward / Edit / Delete (for me or everyone) using Telegram's permission rules.
- Add or remove common emoji reactions; counts refresh from TDLib.
- For private contacts with a public username, the call icon opens their profile **in the official Telegram app** to start voice/video calls externally.
- **Native GLACIA VoIP not implemented.** TDLib call signaling needs a separate tested native tgcalls/WebRTC media engine. See `docs/PHASE3_8.md`.

### Media uploads and recording in v0.3.8
- 📎 Send JPG/PNG/WebP photos, short videos and arbitrary documents via Android system picker and TDLib.
- 🎤 Record and send OGG/Opus voice notes on Android 10+; Android 8–9 records AAC/M4A, sent as a document.
- 🔐 App-private outbox, user-initiated microphone permission, capped attachments and no broad storage permission.
- ⚠️ Voice encoding support and actual delivery need real-device verification; private outbox cleanup is not yet automated.

### Smart notifications from v0.3.7
- Tap an alert → corresponding chat, including after Activity cold start
- GLACIA ice-star status / adaptive launcher icon
- Distinct Android notification child per Telegram conversation plus group summary
- Chat-header bell button → Telegram-backed per-chat mute/unmute
- Gradient Liquid Glass message bubbles, animated bubble resizing / tab selection
- **Optional Firebase Cloud Messaging + TDLib push integration scaffold** (see `docs/PHASE3_6.md`); *not* reliable after process death until Firebase project/sender setup and real-device cold-start testing pass. Force-stop cannot be bypassed.

### Build locally

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-13-chat-management-search
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest "-Pglacia.experimentalNativeCalls=true"
```

If you already have the branch, switch to it and pull `--ff-only`. Keep `telegram.apiId` and `telegram.apiHash` in root `local.properties`; don't commit credentials. Run the locally built APK over the existing GLACIA installation to retain your TDLib login data. Public CI APK cannot sign in because it contains placeholder credentials.

Firebase is off by default. A Firebase/Telegram-compatible FCM project for `dev.glacia.app` can be configured with a local ignored `app/google-services.json`. A successful Firebase registration alone **does not prove** background message delivery.

### Remaining work

Reliable background push after process death, upload cleanup, full message-content search, native voice/video media calling, and production security audit remain outstanding.

See `docs/PHASE3_9.md` for call limitations / device QA, `docs/PHASE3_7.md` for media/device QA, and `docs/PHASE3_6.md` for push limitations; Cherrygram is credited below as UI/product inspiration only, without copying GPL-licensed source code.

---
## Open-source inspiration

Thanks to [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) for inspiration around customizable chat workflows and modular feature organization. Cherrygram is based on the Telegram Android app and is distributed under **GPLv2-or-later**. **No Cherrygram source files or assets have been copied or merged** into GLACIA; adaptations are written independently on TDLib + Compose. Directly incorporating Cherrygram source would require a separate licensing/attribution review.

GLACIA is not affiliated with Telegram or the Cherrygram developers. This is not a production-stable client yet; audit the packaged TDLib native dependency and test with a separate account before relying on it for sensitive chats.
