# GLACIA v0.3.24 — Nicegram-inspired compact core UI

## Source of truth for the redesign
Two screenshots supplied by the user (24 October 2026):
- GLACIA: giant top hero, "Unread" heading, three stacked filter rows,
  tall rounded chat cards, cyan 99 badges, no visible message timestamps,
  oversized 4-tab nav.
- Nicegram reference: compact dark-neutral header, small search, single
  horizontal filter strip, dense chat rows, real timestamps, uncapped unread
  counters, and 5 evenly spaced bottom tabs.

## Implemented in this draft
- Compact chat-home layout and dark neutral charcoal background, without
  replacing the user's existing conversation/call UI.
- Brand remains **GLACIA**, with a right-side menu for New Group/Channel
  and Manage Folders (the corresponding TDLib features are unchanged).
- One horizontally scrollable tab strip: All, Unread, Personal, Groups,
  real Telegram cloud folders, Archived. No repeated Main/folder/filter rows.
- Dense chat rows with a real TDLib avatar, title, one-line preview, actual
  device-local last-message timestamp, mute/pin indicator and **actual unread
  count**, not a hardcoded "99" maximum. Long press for Pin/Archive controls.
- 5-item bottom navigation: Chats, Contacts, GLACIA (existing Discover),
  Settings, Profile. Separate Profile view uses the *existing, real*
  account screen and editing actions.
- Preserves existing chat search, scroll, archive, TDLib syncing, full
  conversation UI, voice/video calling, background audio and notifications.
- **No adverts**, paid features, tracking, third-party chat data, or
  newly requested Android permissions.

## First checkout — Windows PowerShell
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/fix/nicegram-inspired-compact-core-ui
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Branch already present
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch fix/nicegram-inspired-compact-core-ui
git pull --ff-only origin fix/nicegram-inspired-compact-core-ui
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected installed version: **0.3.24**. Preserve root
`local.properties` with the existing `glacia.experimentalNativeCalls=true`
option and credentials. Never uninstall or clear the existing session.

## Device QA / keep PR draft until accepted
1. GLACIA home resembles the provided Nicegram reference in information
   density, **not** its exact branding: header, search, single filter strip,
   compact rows, charcoal colors, five nav icons.
2. In dark mode and light mode, verify no clipped text on a small display
   (e.g., 360 dp), large font setting, landscape, system keyboard.
3. Check a high-unread-count chat (e.g. 362 or 1407): actual count displayed.
   Telegram-muted chats have muted icon/neutral badge.
4. Compare last-message timestamps with official Telegram on the same
   Android timezone. Today h:mm AM/PM; yesterday/older date, no fabricated
   times for empty chats.
5. Tap individual chats. Long press to pin/archive. Verify group/folder
   and Archived navigation and search return correct contents.
6. Verify Settings, Profile, Contacts and Discover tabs and all native
   calling/media/reaction/background-notification features still work.
7. Review CI for :app:assembleDebug, :app:testDebugUnitTest and experimental
   native-calling compile before merge.

This is **UI work on top of the experimental Phase 4.1–4.5 branch**.
Parent PR #28 remains draft until real-device Telegram QA.
