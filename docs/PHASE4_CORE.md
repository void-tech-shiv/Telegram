# GLACIA Phase 4.0 · Telegram Core Foundation · v0.3.22

## Why this comes before new media features
The user explicitly prioritized **Settings, Contacts, Profile and Chats**
over stickers/GIF feature expansion. This change ships a real core-UX batch
and keeps all prior messaging, calling, notifications and media code intact.

## This batch (implemented, but requires Android hardware QA)

**Settings:** A proper account section appears above the existing theme,
notification permission, notification diagnostics and push state controls.
Accurate app version replaces outdated hardcoded "v0.3.9" text. A confirmed
"Log out of this device" action uses Telegram's TDLib `LogOut` and leaves
other sessions alone.

**My profile:** `GetMe` and `GetUserFullInfo` show the signed-in account's
real Telegram name, public username, avatar and bio. `SetName` and
`SetBio` let the user edit first/last name and bio with length limits and
server-side error feedback. Changes refresh from Telegram after writes rather
than locally fabricating success.

**Contacts:** A fourth navigation tab lists **Telegram's cloud contacts** via
`GetContacts` and `GetUser`. Local name/username filtering; real avatars
through the existing app-private TDLib file store; tap a contact to request
`CreatePrivateChat` and enter the existing GLACIA chat timeline. **No Android
READ_CONTACTS permission** or bulk upload of the phone's address book is used.

**Chats:** Existing Main/Archived chat lists, pinning, message search, private
chats, media, replies, notifications and 1:1 voice/video calls are preserved.
Contacts create/reuse ordinary private chats via TDLib and route through the
same current conversation UI. No duplicate TDLib database connection.

**Privacy:** Phone/account details are held only in memory and are not logged
or written to preference files. Stale network callbacks are ignored after
account change/logout. Profile requests are guarded against cross-account
race conditions.

## Known limitations / planned next batches

This is **not** all Telegram settings/contact/profile functionality:
- Username editing, avatar upload/remove, QR/share links, multi-account switch
- Fine-grained Telegram privacy settings (last seen, phone visibility, calls),
  blocked-user manager, active device sessions, 2FA & cloud password changes
- Add/edit/delete Telegram contacts, optional explicit phone address-book sync
- Chat information/about, pinned messages, group/channel administration, folders,
  invite links, Saved Messages entry, new group/channel creation
- Expanded accessibility, Android contact import consent and full parity
- Pending parent PR dependency/provenance and device regression testing

Future batches should prioritize the above **core** features before more
sticker/GIF embellishments.

## Windows / Android Studio

First-time checkout:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-4-core-account-contacts
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Branch already exists:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-4-core-account-contacts
git pull --ff-only origin feature/phase-4-core-account-contacts
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Keep existing private root `local.properties` unchanged except for
`glacia.experimentalNativeCalls=true` if native calling is enabled.
**Do not delete app data, uninstall the old APK or disclose Telegram API
secrets.** Version check:

```powershell
adb shell dumpsys package dev.glacia.app | Select-String "versionName"
```
Expected `versionName=0.3.22`.

## Real-device merge gates
- [ ] Login reuse: real account name, avatar, public username, and bio in Settings.
- [ ] Edit name + bio; verify on official Telegram. Re-open GLACIA, confirm
  server changes. Empty first name and 70+ char bios rejected locally.
- [ ] Contacts tab: Telegram cloud contacts load; search by name/username,
  open an existing/new 1:1 conversation, send and receive messages.
- [ ] Verify **no Android contacts permission prompt** and no phonebook upload.
- [ ] Offline/no contacts/user without username; errors, progress and empty states.
- [ ] Settings theme, app notification permission, test notification still work.
- [ ] Logout dialog Cancel doesn't log out; confirmed logout returns to auth;
  another Telegram device remains logged in. No cross-account data flashes.
- [ ] Regression: archive/pin/search, sticker/GIF send/display, calls (incoming,
  outgoing, 2-way audio/video), Home background audio and Hang up notification.
- [ ] GitHub Android compile + JVM tests + experimental native-call compilation.
- [ ] Review all parent draft PRs before merging to main.
