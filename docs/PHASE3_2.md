# GLACIA Phase 3.2 — Real video and voice playback (experimental)

Incremental draft branch: `feature/phase-3-2-playback`, based on `feature/phase-3-1-media`. Version `0.3.3`.

## What was built

- TDLib captures the original media `File` ID from `MessageVideo.video.video`, `MessageVoiceNote.voiceNote.voice` and `MessageAudio.audio.audio`.
- Video, voice notes and audio now offer an **explicit Download** button for the original media. Original files are **never downloaded just by opening the chat**; small thumbnails remain separate.
- File transfer progress/retry comes from the existing TDLib-managed download state. Cached, complete originals show a **Play** button.
- Tap **Play video** to launch the Media3 ExoPlayer 1.11.1 native video viewer with playback/seek controls; **Play audio** opens an audio control dialog with play/pause/seek.
- Every player is scoped to the dialog and released when dismissed. Only canonical app-private TDLib files are eligible for playback.
- Both Midnight Glass and Frosted Daylight themes remain intact. No storage permission, background playback service, or external media URL.

## Device test plan

1. From the Android Studio terminal (`D:\telegram\Telegram`):
   ```powershell
   git fetch origin
   git switch --track origin/feature/phase-3-2-playback
   .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
   ```
   If the branch already exists, switch directly to it and pull `--ff-only`.
2. Keep your untracked Telegram API ID/hash in `local.properties`. Build and run your locally configured APK **over** the existing installation; do not uninstall or Clear Data.
3. Open a chat containing an MP4/H.264 video. Tap Download video, confirm progress, then Play video. Verify video/audio output, pause/seek and dismissal.
4. Open a chat containing an OGG/Opus voice note. Download then Play audio, test pause and scrubbing.
5. Test in airplane mode with a previously downloaded clip, then test retry with an uncached clip.
6. Test changing conversations, exiting the dialog and returning to the home screen. Video/audio must not keep playing after leaving its dialog.
7. Test unsupported audio codecs and very large videos: show errors without crashing.
8. Retest sending/receiving text, JPG-as-document View/Save, login persistence and dark/light themes.

## Known limitations and safety

- Requires full original download before playback. Streaming a partially downloaded TDLib file and adaptive variants are **not** implemented.
- Voice **recording**, video-note playback, image/video sending, read receipts, typing indicators, notifications, calls, global search and background media controls are **not** implemented yet.
- Some codec/container formats may not be supported by the device; Media3 error handling is best-effort.
- Native TDLib packaging and private session management must be audited before production use.
- No private API credentials are uploaded to CI. Public GitHub Actions artifacts are built with placeholders and cannot authenticate.
- Test using conversations where you have permission to access and retain media. Never publicly upload GLACIA's TDLib database, downloaded private files, or configured APK.

Do not merge this draft until Android CI and hands-on device tests pass.
