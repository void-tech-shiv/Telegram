# Phase 2 — TDLib authorization and real chat-list integration

Phase 2 introduces **real TDLib authorization** and a live, read-only list of Main chats. Dark/light glass appearance is retained. It does **not** implement live chat history, sending, media or notifications. These are intentionally reserved for Phase 3.

## Local credentials (never commit)

1. Obtain an application API ID/hash from https://my.telegram.org/apps.
2. Add the following lines to your **local** `local.properties`:
   ```properties
   telegram.apiId=YOUR_NUMERIC_ID
   telegram.apiHash=YOUR_API_HASH
   ```
3. Gradle builds with placeholders (ID 0, empty hash) when credentials are absent. The login screen then shows a setup explanation.
4. Build on your own computer for a real account. **Never upload an APK containing your personal API hash to a public Actions artifact**. The shared CI APK is always unconfigured.
5. API credentials embedded in a distributable client can be extracted from its APK. For a public release register the app's own credentials and understand Telegram API policy; don't treat `BuildConfig` as a vault.

## Boundaries

- No live chat-history view, no live sends, no persistent notification service.
- Login supports phone, code and Telegram 2FA password. Email and first-time registration states are recognized but deliberately blocked until dedicated flows are built.
- Authorization codes and passwords exist only transiently in Compose state; no logging or analytics.
- Sessions are stored under app-private storage by TDLib; Android backup is disabled.
- This phase uses community-packaged native TDLib binaries; review the upstream build provenance and security posture before logging in with sensitive accounts.
- Demo conversations never appear while logged into Telegram, and the composer cannot send messages in live mode.

## Test checklist

- With no credentials, app starts in SetupRequired and does not attempt login.
- With valid credentials, enter phone (+country), code, optional 2FA password.
- On Ready, show Telegram Main chat titles, previews and unread counts; offline demo chats do not mix with live list.
- Force stop and reopen: TDLib resumes the local authorized session.
- Verify supported Android ABI, dark/light appearance and verify no credentials appear in commits.
- Unsupported email/registration authorization branches render explicit safe explanations.

## Known remaining

- Pending actual Android CI build and real device authorization tests.
- Complete account creation/email/passkey flows are not implemented.
- TDLib update delivery and chat-list pagination need stress tests on large accounts.
- Full production security audit required.
