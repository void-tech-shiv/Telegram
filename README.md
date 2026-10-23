# GLACIA — Liquid Glass Telegram Client

Independent Android Telegram client built from scratch with **Kotlin, Jetpack Compose and TDLib**. Supports Midnight Glass, Frosted Daylight and the system theme.

## Current developer build: v0.3.4 — Phase 3.3

**Branch:** `feature/phase-3-3-cherry-inspired` · **Status:** experimental / draft PR

What is implemented in this branch:
- Telegram authorization, chat list, real messages and text sending
- Chat history pagination, media thumbnails and private TDLib downloads
- Local photo viewer, Save As via Android system picker, original video/audio/voice playback (Media3)
- Chat productivity tabs: **All / Unread / Groups / Private**
- **Real Telegram Discover** using TDLib local chat lookup and public user/group/channel search; debounced input, clickable results
- Kotlin Compose responsive Liquid Glass panels in dark/light

Not yet included: full text-content search across all Telegram messages, push notifications, sending image/video attachments, voice recording, read receipts, typing indicators, reactions, calls and full parity with Telegram. Public search may be restricted by Telegram server/account permissions.

## Quick local build (Windows)

Prerequisites: Android Studio, Java 17, Android SDK with API 37, Android device/emulator.

```powershell
git clone https://github.com/void-tech-shiv/Telegram.git
cd Telegram
git switch --track origin/feature/phase-3-3-cherry-inspired
```

In the project's **untracked** `local.properties` keep your Android SDK path and set **your own** Telegram API credentials from [my.telegram.org/apps](https://my.telegram.org/apps):

```properties
telegram.apiId=YOUR_NUMERIC_API_ID
telegram.apiHash=YOUR_PRIVATE_API_HASH
```

Then build/test:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Use Android Studio's **Run** button to install over your prior GLACIA build. Don't uninstall/clear app data if you need to preserve the existing Telegram session. Never commit `local.properties`, API hash, a configured APK, TDLib database files, or personal message logs. Public CI APKs intentionally have no usable Telegram API credentials.

## Project components

- `app/src/main/java/dev/glacia/app/data/TdlibSession.kt` — authorization, chat state and TDLib lifecycle
- `.../data/TdlibMessages.kt` — history, text sends, updates and media metadata
- `.../data/TdlibDiscovery.kt` — Telegram local/public search and stale-response protection
- `.../data/ChatFilters.kt` — chat filtering rules
- `.../ui/GlaciaApp.kt` — navigation and themes
- `.../ui/TelegramDiscoverScreen.kt` — real Discover UI
- `.../ui/GlaciaPlaybackDialog.kt` — local-only Media3 playback
- `docs/PHASE3_3.md` — licensing notes and Android QA guide

## Open-source inspiration

Thanks to [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) for inspiration around customizable chat workflows and modular feature organization. Cherrygram is based on the Telegram Android app and is distributed under **GPLv2-or-later**. **No Cherrygram source files or assets have been copied or merged** into GLACIA; adaptations are written independently on TDLib + Compose. Directly incorporating Cherrygram source would require a separate licensing/attribution review.

GLACIA is not affiliated with Telegram or the Cherrygram developers. This is not a production-stable client yet; audit the packaged TDLib native dependency and test with a separate account before relying on it for sensitive chats.
