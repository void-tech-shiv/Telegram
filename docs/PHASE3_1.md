# GLACIA Phase 3.1 — Profile photos and visual previews

This experimental feature branch is based on `feature/phase-3-messaging`. It is **not merged** into the working Phase 3 branch.

## Implemented

- Read Telegram `ChatPhotoInfo.small` and request missing images with TDLib `DownloadFile`.
- Observe `UpdateFile` and show the downloaded, app-private image in the chat list and conversation header. If unavailable, keep the existing initials avatar.
- Identify `MessagePhoto` and `MessageVideo` thumbnails in live conversations and download only smaller preview files.
- Render image previews asynchronously with Coil 3.6.3. No unauthenticated CDN requests or external storage permission.
- Replace generic chat-list media descriptions with photo, video, voice, document, sticker and GIF labels.
- Preserve Phase 3 real messaging, dark/light Liquid Glass, message history and search over loaded chats.

## Security and resource limits

- TDLib handles all thumbnail downloads; the UI opens only TDLib-owned local file paths.
- Use existing app-private TDLib databases; do **not** disable Android backup restrictions.
- Preview selection deliberately excludes large photo resolutions (long side over 1024 px).
- Large/original media, video/audio playback, media upload, notifications and full public search are **not enabled**.
- A generated debug APK from public Actions has **no private Telegram API credentials** and cannot log in. Build locally using your own untracked `local.properties`.
- Access to private Telegram messages should remain on your device. Do not share unredacted screenshots, session database files or app-configured APKs.

## Device test

1. Pull `feature/phase-3-1-media` and run Gradle sync.
2. Build `gradlew.bat :app:assembleDebug :app:testDebugUnitTest` on Windows.
3. Run over GLACIA without uninstalling or clearing its app data.
4. Verify avatars load in several real chats, then open a chat with existing photo messages.
5. Verify thumbnails appear, switching tabs and themes does not crash, and sending/receiving **text** still works.
6. Test in airplane mode (previously cached thumbnails should still render), then reconnect.
7. Check both a channel with many images and a chat with missing photos. Avatars must gracefully fall back to initials.

## Planned later increments

- Phase 3.2: typing indicators, read receipts and robust message-state UX.
- Phase 3.3: Telegram global discovery/search, media viewing, controlled downloads, notifications.
- Phase 3.4: performance, accessibility and glass animations after real-device validation.

**Status:** CI and device QA required before merging.

## v0.3.2 document-as-image fix

Telegram can deliver JPG/PNG images as `MessageDocument` rather than `MessagePhoto` when users send images **as files**. This is legitimate Telegram behavior, not a text-message rendering failure.

- Render a document thumbnail when TDLib supplies it, including images uploaded as document files.
- **Download** explicitly requests the full original via TDLib; a lightweight preview never silently triggers original-file downloads.
- After completion, JPEG/PNG image documents can be viewed inside GLACIA. **Save** opens Android's system file picker for JPG, ZIP, PDF and other documents.
- The save flow reads only known, canonical app-private TDLib paths. It writes only to the user-selected destination using Android's Storage Access Framework; no broad storage permission is needed.
- Download progress, an error message, and Retry are shown in the file card when available.
- If an image is sent as a document without a thumbnail, the preview may be unavailable until the full file is explicitly downloaded.
- Video streaming, ZIP archive extraction, auto-export to Downloads, and opening arbitrary downloaded files in third-party apps are **not** part of this increment.

QA: Open a chat with .jpg documents and .zip attachments; confirm thumbnails when available, Download progress, View for downloaded images, Save As for images/ZIP, offline retry behavior, night/day themes and session persistence. Do not clear app data during upgrade.

**Security reminder:** Exported files leave TDLib app-private storage. Open only files you trust, and do not automatically execute downloaded archives or programs.
