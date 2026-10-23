# GLACIA Phase 3.14 rendering fix — v0.3.21

## Reproduction from real Android screenshots (24 October 2026)
- Official Telegram renders Hot Cherry TGS/WebM sticker pack and looping GIF.
- GLACIA v0.3.20 sometimes displayed **Unsupported message type**,
  **Preview unavailable**, and a 0%-progress media download. Some static
  sticker thumbnails were visible but sticker animation wasn't.
- The app had mistaken .tgs (gzipped Lottie JSON) and .webm (video sticker)
  media for files that Coil could decode as standard PNG/WebP. Other
  sticker records lacked thumbnails entirely.

## Code changes
- Treat Telegram messages `MessageSticker` and `MessageAnimatedEmoji`
  as real media with original file ID **separate** from thumbnail ID and
  `webp`/`tgs`/`webm` format metadata.
- WEBP: render still sticker via Coil.
- TGS: bounded gzip decompression (max 1 MB compressed, 2 MB expanded)
  then real Lottie animation. Reject corrupt/oversized media without crash.
- WebM: loop video sticker with a muted ExoPlayer scoped to a visible
  Compose item; release on disposal/screen off, with thumbnail fallback.
- Only automatically download **small known-size stickers** (<=1 MB).
  Full GIF original downloads remain user-triggered.
- On missing original/thumbnail, provide a real **Load sticker** control
  and manual retry; GIF Download button allows retry instead of being
  disabled forever on an unchanging 0% status. TDLib errors are surfaced.
- Show useful summaries for `MessageAnimatedEmoji`, `MessageVideoNote`
  and `MessageDice`; unknown events use a neutral service-message label.
- Add installed Telegram sticker pack browser: TDLib
  `GetInstalledStickerSets(StickerTypeRegular)`, lazy `GetStickerSet`
  on selected pack, bounded to 80 pack titles / 120 stickers in a pack.
  Real Telegram files, no fake catalog or external API.
- Existing two-way voice/video, call notifications, reaction-400 fix,
  archival, and search code not changed.

## Limitations / security
- Sticker pack thumbnails are still static previews in the chooser;
  selected chat stickers animate when the original is available.
- GIF messages require explicit original-file Download/Play; this PR
  does **not** auto-loop every received full GIF in the chat timeline.
- Android/OEM WebM decoder support may vary. Fall back to thumbnail
  rather than claim perfect device compatibility.
- Full Telegram parity, all custom emoji effects, search across online
  sticker catalog, sticker store/install/remove pack UX are not included.
- Do not merge draft PR without Android build + two-device QA.

## Windows PowerShell — first time
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/fix/phase-3-14-sticker-gif-rendering
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Existing branch
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch fix/phase-3-14-sticker-gif-rendering
git pull --ff-only origin fix/phase-3-14-sticker-gif-rendering
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep **private** root `local.properties` and
`glacia.experimentalNativeCalls=true` untouched. Never commit credentials,
uninstall GLACIA, or clear TDLib app data.

Expected installed version:
```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```
Expected `versionName=0.3.21`.

## Physical phone QA
1. Send `Hot Cherry` TGS sticker and WebM sticker from official Telegram
   into GLACIA. Tap **Load sticker** if original isn't cached; both should
   display **moving** animation in GLACIA rather than blank preview.
2. Send an ordinary WEBP sticker and an animated emoji. No unsupported
   message types; static WEBP works without video decoder.
3. Open expressions picker → **My packs** → **Hot Cherry** (if installed
   on the logged-in account). Confirm real pack titles and options; tap
   send, remote Telegram receives correct sticker.
4. For GIF: manual download and playback. If stuck at 0%, tap Retry,
   observe byte progress or Telegram failure code instead of endless
   disabled button. Try Wi-Fi and mobile data.
5. Test disabled network, app background, repeated open/close, large
   corrupt TGS, TGS autoplay with scrolling and several WebM stickers.
   No jank, media leak, infinite download, crash or camera interference.
6. Confirm standard Telegram chat, reaction fix, archive/search and
   two-way audio/video and notification Hang up still work.
