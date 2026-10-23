# GLACIA Phase 3.7 — v0.3.8 media composition (draft)

Based on `feature/phase-3-6-smart-notifications` (v0.3.7). No existing Telegram session database is changed.

## New features
- Add an Android system document picker to the chat composer. Selected photos (JPEG/PNG/WebP), videos and other files are copied into private app storage with a bounded file size before being sent through TDLib's own `SendMessage` API.
- Explicit audio recording workflow: **microphone → recording → Stop & send** or **discard**. Permission is requested only when the user taps record. Recording cancels when you leave the chat or Android backgrounds the app.
- Android 10+ uses Opus in OGG for native Telegram voice notes. Android 8/9 fallback records AAC in M4A and sends it as a **document**, not a falsely labeled Telegram voice note.
- Upload errors surface in the conversation, and TDLib send results are merged into the message list with existing pending/failure indicators. No cloud server or external storage permission.

## Safety limitations
- A bounded 10 MiB photo and 100 MiB video/file limit is intentionally conservative for this preview build. TDLib/Telegram may reject unsupported MIME, invalid formats, unusual photo dimensions, encrypted/secret chats or channel posting permissions.
- Upload copies remain in app-private outbox while TDLib may be uploading. **File cleanup/retention policy for completed, failed, or abandoned uploads is not implemented yet**. A later patch should reconcile TDLib completion and safe cleanup.
- Recording is limited to the foreground. Do not expect recording or uploads to continue if Android kills the app process.
- This milestone does not implement video capture, Telegram-native photo compression, reply/forward/edit, download gallery, or full background push.

## Device test before merging
1. Send a JPG photo, PNG, short MP4 and a PDF from Android system picker to a second Telegram account; verify each arrives.
2. Check upload error handling offline and when using a channel without send permission.
3. Grant mic access, record >1 second, send OGG/Opus from Android 10+; verify Telegram clients show native voice note and play it.
4. Discard a recording, leave a chat mid-recording, and background the activity; ensure no microphone recording continues.
5. Retest existing read receipts, typing, media playback, notifications, chat filters and both Liquid Glass themes.

Build locally:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-7-media-compose
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

Public CI APK is intentionally unconfigured; use your existing untracked `local.properties` for Telegram API credentials when testing. Keep this PR draft until CI and real-device QA pass.
