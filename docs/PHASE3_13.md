# GLACIA Phase 3.13 — v0.3.18: Telegram feature parity, first batch

> **Experimental draft** based on Phase 3.12. Telegram has a large and evolving
> feature surface; it cannot honestly be called 100% feature-complete yet.
> We are building prioritized native TDLib features incrementally and testing
> each feature against a real Telegram account and Android device.

## Implemented in this batch

- **Main and Archived chat lists.** Main/Archived chips; separate Telegram-synced
  TDLib chat list positions, not a fake local-only hide. Load archived chats
  from Telegram at authorization and on entering the archive tab.
- **Archive / Unarchive** in each conversation's menu. TDLib
  `SetChatChatList`; don't optimistically claim success. Waiting state is
  per-chat; errors show Telegram numeric code without exposing user data.
- **Pin / Unpin chats** via TDLib `ToggleChatIsPinned`, respecting Telegram
  authorization/limits and server updates, ordered before regular chats.
- **Search in chat** from the conversation toolbar. Debounced server-backed
  `SearchChatMessages` search, 30 results per page (max 200 in memory),
  Telegram result previews and count, more results, stale-response suppression.
  Selecting a result requests a bounded history window around the matching
  message through TDLib and scrolls the conversation to it once loaded.
- Search terms/results only in memory, cleared on logout/close and when the
  search dialog is dismissed. No sensitive queries/identifiers logged.
- **Existing full-screen 1:1 voice/video calls, media, background voice and
  ongoing call notification preserved** from earlier phases; no edits to
  NTgCalls native signaling/ICE logic.

## Real-device acceptance matrix

1. Two Telegram test accounts: pin/unpin a private conversation. Confirm
   sorting changes and remains synced across an official Telegram client.
   Repeat in archive; expect Telegram limits/errors to show safely.
2. Archive a private chat from Main; it should disappear from Main and
   appear in Archived. Unarchive and verify it returns. Test group/channel,
   mute, unread and switching tabs rapidly.
3. After restarting GLACIA, confirm archived chat entries are loaded again.
   On a chat being moved back by Telegram after a new message, follow the
   server's new list position rather than overwriting it locally.
4. Search for a word in an active chat; verify server history results are
   returned even if not included in initially loaded 40 messages.
   Select a result, verify jump to actual message/context. Scroll older
   history and search again.
5. Rapidly change the search query while TDLib responses are in flight.
   Old results must not replace the latest query; close search and reopen,
   private query must not persist.
6. Load more results until exhausted. Verify no duplicates. Search empty,
   emoji, mixed languages, secret chat (TDLib may reject non-empty search),
   offline account and rate-limit errors.
7. Regression: text and media send, replies/forwards/deletes/reactions, read
   receipts, voice/video calling foreground + background and notification
   Hang up still work as before.
8. Do not mark PR ready until Android CI and two-account device QA succeed.

## Feature-parity roadmap (not yet promised as complete)

### Messaging & media
- **Next:** sticker and GIF search/send/display, animated emoji,
  video notes, photos/albums, polls/quiz creation and votes.
- Rich text entities, link previews, message scheduling, silent sending,
  saved messages, message translation, media gallery, downloads and downloads
  manager, search filters, pinned messages, drafts and chat folders.
- Group/channel moderation (admin tools, topics/forum, invite links,
  slow mode, permissions, bans) and deep bot commands/inline keyboards.

### Calls
- Group voice/video calls and live streams, PiP, reliable network recovery
  (ICE restart), OS Telecom/lockscreen incoming UI, robust Android notification
  delivery after process death, Bluetooth reconnection and call-history UI.
- Continue device-specific privacy/security/permission testing before
  advertising production native calling.

### Accounts, privacy and social
- Stories, reactions and story privacy, profiles, avatars, contacts sync,
  privacy & security controls, devices/sessions, 2FA/password changes,
  user blocking/reporting, cloud chat backups.
- Secret chat creation and lifecycle with end-to-end encryption support:
  TDLib and Telegram may have special limits; never imply normal cloud chats
  are secret chats.
- Multi-account switch, local passcode/biometric lock, data usage,
  storage management, accessibility, theme customization, localization,
  Telegram Premium features where supported/licensed.

### Engineering prerequisites
- TDLib version/NTgCalls binary provenance and license audit, Android 14+
  background calling compliance, encrypted session storage, automated UI +
  instrumentation tests, robust offline queue and full multi-device QA.
- **No private API ID/hash, login code, encryption key or session database
  should be committed into GitHub.** No backend or paid server is needed for
  the TDLib features implemented in this batch.

## PowerShell — First checkout

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-13-chat-management-search
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## PowerShell — If the branch already exists

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-3-13-chat-management-search
git pull --ff-only origin feature/phase-3-13-chat-management-search
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Important:** Keep root `local.properties` unchanged except for intentional
local debug opt-in `glacia.experimentalNativeCalls=true`. Keep existing
`sdk.dir` and Telegram API settings. Never commit `local.properties`
or uninstall/clear app data just to upgrade. Check installed version with
`adb shell dumpsys package dev.glacia.app | Select-String "versionName"`
(expected v0.3.18).
