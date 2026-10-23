# GLACIA Phase 3 — Live messaging (increment 1)

This branch builds on `feature/phase-2-tdlib-auth`. It adds basic **real** Telegram text conversations to the Android app while retaining both Liquid Glass themes.

## What works in this increment

- Open a real chat and request its latest 40 messages through TDLib's `GetChatHistory`.
- Load older pages manually, deduplicating overlapping page boundaries.
- Receive TDLib updates for new messages, confirmed/failed sends, message edits, and deletions.
- Send a real plain-text Telegram message by tapping Send. This is **not** a mock send.
- Show an in-chat error when history or sending fails; no OTP or credentials are written to logs.
- Render non-text message types as descriptive placeholders, not as actual images, audio players or attachments.
- Immutable snapshots to Compose, bounded (latest 300) messages per active chat.
- JVM tests for reducer invariants (ordering, deduplication, ID replacements, deletions, bounds).

## Test on your Android device/emulator

1. Stay on the feature branch, pull, and sync Gradle.
2. Configure your private `local.properties` with Telegram `apiId` and `apiHash` from the Phase 2 instructions. These must NEVER be checked into Git.
3. Build `./gradlew :app:assembleDebug :app:testDebugUnitTest` (Windows `gradlew.bat`).
4. Run over the existing GLACIA installation (same application ID). **Do not clear app data**: your TDLib session lives in app-private storage.
5. Open a personal test chat, check latest messages, scroll to the oldest and use **Load earlier messages**, and send a short test message.
6. Confirm that the text appears in official Telegram or another client, and that replies appear in GLACIA without a restart.
7. Reopen the app, switch between Midnight Glass and Frosted Daylight, test keyboard, back navigation, and connection loss.

Sending is live: only use a test conversation/account until you've verified app behavior. A crash log may contain message content; redact message text and personal identifiers before sharing.

## Limits and release gates

- **Not a complete Telegram client yet.** Voice/video calls, notifications, image/video download/rendering, uploading, reactions, sticker rendering, group topics, chat permission UX, auto pagination and full-text global search remain out of scope.
- Threaded discussion/forum topics, protected chats and certain channel permissions may reject text sends; errors should be displayed.
- This feature has not been confirmed on the user's Android device. Keep the PR as draft until CI and device tests pass.
- Telegram secret chats are not automatically equivalent to cloud chats; use TDLib's supported secret-chat capabilities and audit native dependency provenance before handling sensitive conversations.
- Do not publish any debug APK compiled with your private personal API hash. Public CI builds without user credentials.
