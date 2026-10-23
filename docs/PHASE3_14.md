# GLACIA v0.3.20 — Phase 3.14, first stickers/GIF batch

> Experimental feature increment. A working GLACIA v0.3.19 device
> already confirmed the Telegram reaction-code-400 fix.
> **Not full Telegram stickers/GIF/animated emoji parity.**
> The earlier working voice/video and background calls remain untouched.

## New features
- TDLib **recent stickers** using `GetRecentStickers(false)` and
  **saved Telegram GIFs** using `GetSavedAnimations`.
- Compose Liquid Glass sticker/GIF chooser, triggered from the message
  composer. Shows real Telegram media from the signed-in account,
  not placeholder stickers or third-party services.
- Sticker/GIF messages are sent only after user taps, using
  `InputMessageSticker` / `InputMessageAnimation` and TDLib file IDs.
- Existing message timeline now recognizes incoming/outgoing
  `MessageSticker` and `MessageAnimation` types. WEBP sticker previews
  display as still images. Animated/video stickers use safe thumbnails
  instead of incorrectly decoding TGS/WebM vector/video as PNG.
- GIF previews are lightweight; the original GIF/video is downloaded **on
  user request** through the same TDLib media download and player path as
  ordinary video. No automatic full-size GIF downloads or autoplay.
- Picker lists capped to 60 recents + 40 saved GIFs, and cleared on logout.
  No search terms/keys or separate web backend.

## Explicit limits / next work
- **Sticker search, installed pack selection, favorite packs, sending
  arbitrary stickers, animated .TGS/WebM sticker playback, custom emoji
  entities, animated emoji effects and GIF bot search** are not yet included.
- If the signed-in Telegram account has no recent stickers/saved GIFs,
  the chooser displays an empty state. Add items from official Telegram
  and press Refresh.
- Some non-WEBP thumbnails may not be available. Empty previews are not
  a signal that full sticker or GIF file downloads occurred.
- No claims about premium-only paid media or third-party GIF catalog rights.
- Telegram-native permission failures display their numeric error code;
  nothing is transmitted before explicit tap.

## Android Studio / PowerShell, first checkout
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-14-stickers-gifs
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Branch already exists
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-14-stickers-gifs
git pull --ff-only origin feature/phase-3-14-stickers-gifs
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep `glacia.experimentalNativeCalls=true` in existing private root
`local.properties`; do not delete SDK or Telegram API configuration.
Do **not** uninstall/clear app storage; the new APK upgrades over v0.3.19.

```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```
Expected: `versionName=0.3.20`.

## Device QA / keep draft until checked
1. With 2 Telegram test accounts send a WEBP sticker from official
   Telegram → GLACIA and verify static image. Try TGS/WebM sticker:
   thumbnail fallback appears, no crashes or forced video decoder.
2. Send a recent sticker from GLACIA picker → official Telegram.
   Confirm correct visual sticker and emoji label.
3. Save a GIF from official Telegram, reopen picker, view its thumbnail,
   send it from GLACIA and play a received GIF by explicit Download/Play.
4. Blank recents/saved GIF list, offline TDLib, denied media send permissions,
   quick open/close/logout and repeated send: no crashes or UI stalls.
5. Confirm reactions fix, text, photo, document, voice notes,
   2-way voice/video and ongoing background call notification Hang up
   work unchanged.
6. Confirm Android build and unit tests, and that no raw Telegram session or
   authorization data is committed or logged.

**Do not merge into main** before independent real-device QA and review
of parent draft branches.
