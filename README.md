# GLACIA — Liquid Glass Telegram Client

A Kotlin + Jetpack Compose third-party Telegram client backed by TDLib with dark/light Liquid Glass themes.

## Current milestone: Phase 3 (draft / experimental)

**Available on `feature/phase-3-messaging`:**
- Telegram phone / verification code / two-step password authorization
- Real Main chat list, search across loaded chat titles and previews
- Chat history, older-message pages, and real-time message updates
- Real plain-text sending, delivery/error status, and message deduplication
- Media message *labels* (actual display / downloading is not yet implemented)
- Midnight Glass, Frosted Daylight, Follow System appearance

**This is an early development build, not a full Telegram replacement.** Calls, media viewing/upload, push notifications, global search and group-topic support are unfinished. Open a test conversation before trusting it for everyday communication.

## Local Android build

Requirements: Android Studio, JDK 17, API 37 Android SDK, and a device or emulator.

1. Clone `https://github.com/void-tech-shiv/Telegram.git`.
2. Checkout `feature/phase-3-messaging`.
3. Obtain Telegram API ID/hash from `https://my.telegram.org/apps`.
4. Create / edit **untracked** `local.properties` at project root and preserve your existing SDK path:

```properties
telegram.apiId=YOUR_NUMERIC_ID
telegram.apiHash=YOUR_PRIVATE_API_HASH
```

5. Gradle Sync; run `./gradlew :app:assembleDebug :app:testDebugUnitTest` (or `gradlew.bat` on Windows).
6. Use Android Studio Run. Upgrade over existing GLACIA without deleting app data if preserving your TDLib session.

**Never commit your API hash or share a personally configured APK.** The API hash is packaged in the app at build time and is extractable. Public GitHub Actions builds have placeholder credentials by design.

## Project layout

- `app/data/TdlibSession.kt` — authorization, chat headers and TDLib client lifecycle
- `app/data/TdlibMessages.kt` — chat history, real sends and live TDLib events
- `app/data/MessageReducer.kt` — deterministic message merge/delete logic
- `app/ui/GlaciaApp.kt` — adaptive Liquid Glass navigation and chat list
- `app/ui/ConversationScreen.kt` — live message bubbles and composer
- `app/settings/ThemePreferences.kt` — DataStore theme settings
- `app/src/test/` — JVM unit tests
- `docs/PHASE2.md`, `docs/PHASE3.md` — implementation notes / test checklists

GLACIA is independent and not affiliated with Telegram. Native TDLib core comes from a community packaging project; verify the binary provenance and security posture before handling sensitive accounts.
