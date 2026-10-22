# GLACIA — Phase 2

Kotlin/Compose Android Telegram client with dark/light Liquid Glass design.

**Phase 2 branch:** TDLib native integration, phone verification, two-step password handling, and a **read-only live Main chat list**. Real messaging, history, media, notifications, email-based login and account creation are **not implemented**. No demo messages are sent or shown as live messages.

## Local setup

1. Get your own API credentials from https://my.telegram.org/apps.
2. Put these values in untracked `local.properties` at the project root (alongside `sdk.dir`):

```properties
telegram.apiId=123456
telegram.apiHash=your_own_private_hash
```

3. Open in Android Studio and run `./gradlew :app:assembleDebug`.
4. The public CI builds without credentials and therefore opens the setup-required screen.
5. Enter your own phone in international format, the code Telegram delivers, and 2FA password when required.

**Do not commit or send API credentials, login codes or passwords.** `BuildConfig` values are embedded in APKs, not secret storage; don't publicly distribute a personally configured APK.

See [Phase 2 implementation and limits](docs/PHASE2.md).

## Current scope
- Material3 Compose + Haze 2.0.1 Liquid Glass dark/light themes
- Live TDLib session with private app database and lifecycle updates
- Phone/code/2FA auth and live Main chat list titles/previews/unread badges
- Read-only message view placeholder so users cannot mistake demo content for actual conversations
- Explicit unsupported branches for email, registration and additional auth flows
- Phase 3: chat histories, real sends, live updates, downloads and multiple accounts

GLACIA is independent and not affiliated with Telegram. Native TDLib core is community packaged: verify security/provenance before using a sensitive account.
