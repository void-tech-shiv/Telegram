# GLACIA — The Glass Messenger

An **original Android Jetpack Compose Liquid Glass interface** intended to become a Telegram client through TDLib.

**Current status: Phase 1 UI-only demo.** There is NO Telegram authorization, chat sync, sending to Telegram, notification delivery or TDLib binary in this commit. All names and messages are fictional. The composer only adds messages to local, in-memory demo state (lost on app restart).

## Included

- Adaptive Midnight Glass / Frosted Daylight / Follow System modes, persisted via DataStore
- Real backdrop/refraction glass on floating header, bottom navigation and composer via Haze 2
- Aurora gradient background, demo chat list, live local search, chat bubbles and mock composer
- Chats / Discover / Settings tabs with back navigation
- Repository and TelegramGateway interfaces prepared for later real TDLib adapter
- Android CI to compile and attach a debug APK on every push

## Stack

- Kotlin 2.4.20, Android Gradle Plugin 8.13.2, Gradle 8.13, JDK 17
- Android SDK 36, minSdk 26, targetSdk 36
- Jetpack Compose BOM 2026.09.00, Material3
- Haze 2.0.1 core + haze-glass
- Preferences DataStore 1.2.1
- No backend, no secrets, no Telegram credentials

## Build

1. Install Android Studio with Android SDK 36 and JDK 17.
2. Clone: git clone https://github.com/void-tech-shiv/Telegram.git
3. Open the folder in Android Studio. This repo includes Gradle wrapper scripts and jar; sync Gradle.
4. Run the :app configuration on Android 8.0+ or use ./gradlew :app:assembleDebug
5. The debug APK is at app/build/outputs/apk/debug/app-debug.apk.

Or in GitHub Actions, open **Android demo CI**, run workflow, then download the **GLACIA-phase1-debug-apk** artifact.

## Architecture

- app/data/ChatModels.kt — UI models + repository contract
- app/data/TelegramGateway.kt — typed Phase 2 TDLib boundary (no implementation)
- app/settings/ThemePreferences.kt — DataStore persistence
- app/ui/GlaciaTheme.kt — adaptive colors
- app/ui/GlaciaApp.kt — chat list, chat details, search, navigation and Haze surfaces
- app/src/test — JVM unit tests

## Roadmap

- **Phase 1:** Offline UI foundation (this commit)
- **Phase 2:** Integrate TDLib, credentials from a private local file, authorization state machine and authenticated chat list
- **Phase 3:** Real conversations, message media, notifications and multiple-account architecture
- **Phase 4:** Hardening, accessibility, performance and Play Store compliance

## Security and trademarks

GLACIA is not affiliated with Telegram. Do not ship credentials, phone numbers, database files or authorization tokens into git. Telegram Secret Chats require particular handling; hidden UI panels are not equivalent to E2EE. Audit TDLib handling and third-party dependency versions before any live-account login. Honor Telegram API terms and attribution requirements.

## Phase 1 QA checklist

- Switch dark/light/system, force-stop and reopen: choice persists.
- Search demo chats, open a chat, send a local-only message, press Back.
- Switch navigation tabs and open Settings.
- Rotate device, test large fonts and accessibility contrast.
- Confirm no Telegram login or network permission exists yet.
