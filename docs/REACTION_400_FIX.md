# GLACIA v0.3.19 — Telegram reaction 400 fix

## Symptom (two real-device screenshots)
Long-pressing a **📹 Video call** entry opened a message-action dialog
with unconditional emoji buttons. Tapping one returned
`Reaction failed (Telegram code 400)`.

## Root cause
GLACIA offered a hardcoded emoji grid on **every** message, including call
service events and messages where the Telegram chat/server does not accept
those emojis. A successful `GetMessageProperties` response is not proof
reactions are supported. Telegram's contract specifically requires querying
`GetMessageAvailableReactions` for each message to know which types can be
added.

## Fix
- Preserve `MessageCall` as `mediaType="call"` rather than treating it like a
  normal text message. Call-history items **do not offer quick reactions** in
  GLACIA.
- For other sent/live messages, first check standard message properties,
  then request `GetMessageAvailableReactions(chatId, messageId, 8)`.
- Render only Telegram-advertised, non-Premium standard emoji from top,
  recent, and popular lists, plus any user's already-chosen emoji that they
  can remove. Do not expose paid/custom emojis without a proper picker.
- Wait for API response (or show an explanatory state) rather than
  optimistically sending guessed emojis.
- Guard the send path again, with graceful handling if Telegram rejects a
  reaction after the dialog was shown (e.g. permissions changed).
- Ignore stale async responses; clear permissions on logout.
- Regression tests: call entry blocked; pending/failed blocked; unsupported
  emoji blocked; allowed emoji sent; an already-chosen emoji can be removed.
- No changes to working TDLib message send/receive or native voice/video
  signaling, encryption, capture, foreground service or notifications.

## Windows PowerShell: checkout and test

First time:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/fix/reaction-eligibility-telegram-400
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Existing checkout:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch fix/reaction-eligibility-telegram-400
git pull --ff-only origin fix/reaction-eligibility-telegram-400
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep `glacia.experimentalNativeCalls=true` set in the current *private*
root `local.properties`, with the other existing SDK/Telegram settings.
**Do not uninstall or clear GLACIA data**.

Check:
```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```
Expected: `0.3.19`.

## Real device verification

1. Long-press old **📹 Video call** record. Message actions can show Reply
   or Delete where Telegram permits, but should show explanatory text
   instead of reaction emojis. No error 400 should occur from that dialog.
2. Long-press a normal Telegram text message; the dialog must show only
   emojis returned by Telegram, after loading. Add and remove a reaction.
3. Open a restricted channel/group where reactions are disabled. No
   clickable emoji options should appear. No unsafe fallback.
4. Verify normal voice/video calls, background audio, notification Hang up,
   archived/pinned chats and search still work.
5. Try an offline connection and rapid opening/closing of the same dialog.
   Old async callbacks should not resurrect stale options after re-login.

PR remains draft until CI and phone QA succeed.
