# GLACIA — Liquid Glass Telegram Client

Independent Android Telegram client developed in **Kotlin + Jetpack Compose + TDLib**. Midnight Glass and Frosted Daylight themes, TDLib Telegram session, real chats/messaging, images/documents, Media3 video/voice playback, Discover search, unread filters, read receipts and typing indicators.

## Current developer build: v0.3.8 (Phase 3.7)

**Branch:** `feature/phase-3-7-media-compose` · **Status:** experimental draft, not merged or production certified.

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
git switch --track origin/feature/phase-3-7-media-compose
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

If you already have the branch, switch to it and pull `--ff-only`. Keep `telegram.apiId` and `telegram.apiHash` in root `local.properties`; don't commit credentials. Run the locally built APK over the existing GLACIA installation to retain your TDLib login data. Public CI APK cannot sign in because it contains placeholder credentials.

Firebase is off by default. A Firebase/Telegram-compatible FCM project for `dev.glacia.app` can be configured with a local ignored `app/google-services.json`. A successful Firebase registration alone **does not prove** background message delivery.

### Remaining work

Reliable background push after process death, upload cleanup, full message-content search, reactions/calls, and production security audit remain outstanding.

See `docs/PHASE3_7.md` for media/device QA, and `docs/PHASE3_6.md` for push limitations; Cherrygram is credited below as UI/product inspiration only, without copying GPL-licensed source code.

---
## Open-source inspiration

Thanks to [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) for inspiration around customizable chat workflows and modular feature organization. Cherrygram is based on the Telegram Android app and is distributed under **GPLv2-or-later**. **No Cherrygram source files or assets have been copied or merged** into GLACIA; adaptations are written independently on TDLib + Compose. Directly incorporating Cherrygram source would require a separate licensing/attribution review.

GLACIA is not affiliated with Telegram or the Cherrygram developers. This is not a production-stable client yet; audit the packaged TDLib native dependency and test with a separate account before relying on it for sensitive chats.
