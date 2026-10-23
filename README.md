# GLACIA — Liquid Glass Telegram Client

Independent Android Telegram client developed in **Kotlin + Jetpack Compose + TDLib**. Midnight Glass and Frosted Daylight themes, TDLib Telegram session, real chats/messaging, images/documents, Media3 video/voice playback, Discover search, unread filters, read receipts and typing indicators.

## Current developer build: v0.3.7 (Phase 3.6)

**Branch:** `feature/phase-3-6-smart-notifications` · **Status:** experimental draft, not merged or production certified.

### Smart notifications in v0.3.7
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
git switch --track origin/feature/phase-3-6-smart-notifications
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

If you already have the branch, switch to it and pull `--ff-only`. Keep `telegram.apiId` and `telegram.apiHash` in root `local.properties`; don't commit credentials. Run the locally built APK over the existing GLACIA installation to retain your TDLib login data. Public CI APK cannot sign in because it contains placeholder credentials.

Firebase is off by default. A Firebase/Telegram-compatible FCM project for `dev.glacia.app` can be configured with a local ignored `app/google-services.json`. A successful Firebase registration alone **does not prove** background message delivery.

### Remaining work

Reliable background push after process death, full attachment sending and voice recording, actual global *message-content* search, reactions/calls and production security audit are still outstanding.

See `docs/PHASE3_6.md` for full device QA and privacy notes; Cherrygram is credited below as UI/product inspiration only, without copying GPL-licensed source code.

---
## Open-source inspiration

Thanks to [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) for inspiration around customizable chat workflows and modular feature organization. Cherrygram is based on the Telegram Android app and is distributed under **GPLv2-or-later**. **No Cherrygram source files or assets have been copied or merged** into GLACIA; adaptations are written independently on TDLib + Compose. Directly incorporating Cherrygram source would require a separate licensing/attribution review.

GLACIA is not affiliated with Telegram or the Cherrygram developers. This is not a production-stable client yet; audit the packaged TDLib native dependency and test with a separate account before relying on it for sensitive chats.
