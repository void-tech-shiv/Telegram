# GLACIA v0.3.23 — Telegram Core Phase 4.1–4.5

**Experimental stacked draft**, based on Phase 4.0 (PR #27). Focus shifted away
from new sticker/GIF features, toward account and chat essentials.

## Implemented in this increment
- **4.1 Profile & contacts:** public username editing, explicitly selected
  JPEG/PNG avatar upload (private cache, 5MB limit), remove selected cloud
  contacts, add by an exact public @username without sharing own phone number
  by default. No `READ_CONTACTS` permission or bulk address-book import.
- **4.2 Security:** real Telegram active sessions, blocked user list (first
  100), block a private-chat peer, unblock selected user and *confirmed*
  revocation of a **different** Telegram login session. Live last-seen,
  phone-number and call audience rules, with confirmed changes preserving
  explicit per-user/per-chat exceptions. Cannot revoke the
  currently active session with the remote-device button. No IPs or tokens
  displayed or logged.
- **4.3 Chats:** chat information from Telegram `GetChat`, most recently
  pinned message from `GetChatPinnedMessage`, mark unread, jump to pinned
  message. Real Telegram-synced folder chips, basic People/Groups/Channels
  folder creation and confirmed folder deletion without leaving chats.
  Existing search, archives, pinning and media remain.
- **4.4 Group/channel admin basics:** read administrator count, rename and
  description edit, and explicitly confirmed adding a Telegram cloud contact
  to a group/channel. Actions are permission-checked by Telegram.
  A non-admin receives server-side error;
  there are no fake success states.
- **4.5 Communities:** create native Telegram supergroups or broadcast
  channels; explicitly generate an invite link after a security warning;
  invite is shown locally only, never auto-published.

## Still not feature-complete — known gates
- Remaining privacy rules (forwarding, invites, voice notes and other granular
  exceptions), full 2-step-password recovery, email changes, biometric local app lock.
- Admin member role promotion/restriction, bans, discussion groups,
  topics, slow mode, live streams and join requests.
- Full flexible Telegram folder editor (custom include/exclude chats, pinned
  folder chats and complete rename/update),
  full pinned-message list, member/profile/gallery detail screens.
- Username multi-alias management, photo history/removal, optional device
  contact synchronization; multi-account switching.
- Non-expiring invite links must be manually revoked in official Telegram
  until a revoke option is audited and implemented here.
- Android device testing required; do not merge this draft solely based on
  compilation.

## Windows PowerShell: checkout + build
First checkout:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-4-1-to-4-5-core
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
Already checked out:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-4-1-to-4-5-core
git pull --ff-only origin feature/phase-4-1-to-4-5-core
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
Expected version `0.3.23`. Preserve private `local.properties`, including
`glacia.experimentalNativeCalls=true` and your Android SDK/API settings.
Never commit API credentials, Telegram sessions or whole TDLib logs, or
uninstall/clear your existing GLACIA data just to update.

## Two-account live Android QA
1. Settings → Profile: change valid public username, test conflict/invalid
   username, select JPEG/PNG avatar and verify in official Telegram.
2. Contacts: add public @username, confirm own phone is not shared; remove
   with confirmation; compare Telegram cloud list from two devices.
3. Security: view active sessions, cancel revoke, then revoke a **test**
   second-device session with confirmation; verify current GLACIA remains
   logged in and test device is logged out. Block/unblock a test user.
4. Chats: inspect private/group/channel, pinned message navigation,
   mark unread, create/delete a People/Groups folder and verify sync on official
   Telegram without leaving or deleting messages.
5. Create a group and a channel; edit title/description and add a consenting
   test contact with creator rights,
   attempt admin edit from a non-admin, generate invite and open it from
   the other Telegram account. Treat invite as sensitive.
6. Regression: native voice/video on both phones, Home background audio,
   Android Hang up notification, search, archive, reactions, stickers/GIFs.
7. If any error occurs, share only **sanitized** error code and feature name,
   never phone, contacts, username/session ID, invite link or auth code.

The parent Phase 4.0 PR and this PR are **draft** until all gates pass.
