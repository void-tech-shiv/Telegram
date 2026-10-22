# GLACIA — The Glass Messenger

Original **Kotlin + Jetpack Compose** Liquid Glass Telegram client.

## Phase 2: TDLib-powered login and messaging branch

**Development status:** Phase 2 implementation; cloud build and device login still require verification. No production readiness claim. Phase 1 demo remains accessible without any credentials.

- Real [TDLib](https://core.telegram.org/tdlib/) integration via JNI Java bindings
- Phone number, Telegram code, Telegram 2FA password
- Email confirmation, other-device approval, registration states
- Chat list from TDLib and 50 latest messages in selected chats
- Send plain-text messages from real authorized sessions
- Live new-message and chat metadata updates
- Persisted local TDLib database and Keystore-wrapped random database key
- Real account logout, with explicit confirmation
- Both Midnight Glass and Frosted Daylight, plus System theme
- Separate offline demo mode when API credentials are not configured

### Feature scope and limits

This is **not yet a complete Telegram client**. Media display/downloads, notifications, calling, full chat folder/order handling, animated glass performance tuning, multi-account, Secret Chats and message editing/deleting are future phases. Only basic cloud-chat and text-message flows are covered. Some chat types/topics may require additional TDLib request parameters.

### Build locally (credentials required for live Telegram)

1. Obtain your own app **API ID** and **API hash** at [my.telegram.org](https://my.telegram.org).
2. Install Android Studio, JDK 17, SDK 36.
3. Clone this repo, open it in Android Studio.
4. In the root `local.properties` add:

```properties
sdk.dir=/path/to/android/sdk
TELEGRAM_API_ID=YOUR_NUMERIC_API_ID
TELEGRAM_API_HASH=YOUR_API_HASH
```

5. Run `./gradlew :app:assembleDebug` (or `gradlew.bat :app:assembleDebug` on Windows).
6. Install `app/build/outputs/apk/debug/app-debug.apk`.

**Never share your API hash, Telegram login code or 2-step password in a public issue, commit or chat.** `local.properties` and `api-credentials.properties` are gitignored.

> Compiled-in BuildConfig API credentials are extractable from the APK; do not treat APKs containing them as secret. The public GitHub Actions debug APK builds deliberately use API_ID=0 and blank hash, so the workflow exercises only the offline demo UI. To test real login, perform a private local build with credentials. Do not put private credentials in publicly distributed artifacts.

### Architecture

- `app/data/TdlibTelegramGateway.kt`: TDLib JNI bridge, authorization, chat updates, message sending/history.
- `app/data/DatabaseKeyStore.kt`: Android Keystore AES-GCM wrapping of the persistent TDLib database key.
- `app/data/TelegramGateway.kt`: UI-facing session and auth state model.
- `app/ui/GlaciaPhase2Root.kt`: real login and chat screens in a glass UI.
- `app/ui/GlaciaApp.kt`: original Phase 1 offline demo.
- `app/settings/ThemePreferences.kt`: saved appearance setting.
- `.github/workflows/android.yml`: no-credentials CI build and debug APK artifact.

### QA before merging

- Verify Gradle build succeeds and JNI `libtdjni.so` loads on arm64-v8a.
- Verify permissions and no credentials are checked in.
- Confirm logged-out app requests phone number.
- Test code flow, two-step verification, email verification, registration/other-device states.
- Verify messages/chats actually sync; test offline restart with authorized session.
- Confirm Keystore key survives app restart and session recovery.
- Test sign-out, incorrect OTP, flood-wait errors and expired authentication.
- Review accessibility, security, Telegram API terms, performance and privacy.

### License and disclaimer

GLACIA is independent of Telegram; no affiliation or endorsement. TDLib is Boost licensed; the community AAR is maintained by a third party, not Telegram itself. Evaluate native dependency provenance before shipping. Telegram login codes and 2FA should never be collected outside TDLib/Android on-device authorization.
