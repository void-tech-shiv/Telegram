# GLACIA Phase 3.5 — Live typing and best-effort Android alerts

**Version:** 0.3.6 · **Branch:** `feature/phase-3-5-typing-notifications` · **PR:** draft, not merged.

## Implemented

- **Incoming typing:** TDLib `UpdateChatAction` updates a transient per-chat indicator in the conversation header; canceled actions immediately disappear, and stale activity expires after 6 seconds. Basic group chats show a generic "Someone is typing…" label rather than guessing the sender.
- **Outgoing typing:** TDLib `SendChatAction` is dispatched only after the user starts entering a message, with 450 ms debounce and four-second repetition while composing. It cancels on empty draft, sending, leaving the chat, or backgrounding. Draft text never leaves GLACIA via typing signals.
- **Android notifications:** TDLib's `UpdateNotificationGroup` (rather than raw `UpdateNewMessage`) determines message notifications. GLACIA posts a generic alert only when the app activity is backgrounded, the notification is newly added, and OS notification permission is granted. Group removal is reflected by canceling the Android notification.
- **Privacy by default:** No sender name, message text, phone number, OTP or photo appears in the Android notification. Lock-screen visibility is SECRET. Silent Telegram notification groups use a separate quiet Android notification channel.
- **Settings:** Android 13+ runtime `POST_NOTIFICATIONS` permission is requested only after the user taps **Allow notifications**. The settings page scrolls on small screens.
- Midnight/Frosted Glass themes, authorization, chat filters, media playback and read receipts stay unchanged.

## Critical limitations

**This does not provide reliable background push.** GLACIA presently has no persistent Android foreground service, Firebase Cloud Messaging transport, reboot start, or TDLib push receiver. Android may kill the process, so new-message alerts only work **while GLACIA's TDLib process remains alive** (typically after temporarily switching to another app). Do not rely on these notifications for important messages.

Notification text is generic, and tapping it opens GLACIA rather than a targeted conversation. Notification sound selection is limited to quiet/default channel behavior rather than full Telegram custom-sound parity. Notifications from a completed TDLib startup snapshot are intentionally not replayed as new alerts. Full background delivery, profile-sensitive controls and device-specific battery optimization QA remain follow-on work.

## Android Studio instructions

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-5-typing-notifications
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

For an existing local branch, use `git switch feature/phase-3-5-typing-notifications` then `git pull --ff-only origin feature/phase-3-5-typing-notifications`.

Preserve your private `local.properties`, TDLib database and Telegram session. Do not uninstall or clear data. Public CI artifacts intentionally have placeholder Telegram API credentials and cannot sign into Telegram.

## Two-device QA gate

1. Test incoming typing from a second Telegram device with GLACIA's private chat open. Header should show **typing…**, then disappear after cancel or a few seconds of inactivity.
2. Type from GLACIA: the second client should see a typing indicator. It should stop shortly after you clear/send the draft or exit/background GLACIA.
3. Test group typing indicators with multiple users. GLACIA must not claim a specific sender unless correctly resolved.
4. Settings → Allow notifications (Android 13+). Leave GLACIA running, then switch to another app. Send a new message from the second device; verify the generic Android alert. Muted chats should follow TDLib's notification-group behavior. Tapping an alert should only open GLACIA.
5. Test denying notification permission and switching from app → home; no unauthorized notification should appear. Revoke OS notification permission and repeat.
6. Read/delete the message from another Telegram client; verify corresponding TDLib notification group removal. Test short background intervals and force-stopping the app; alert delivery after force-stop is **not supported**.
7. Retest read receipts, photo/doc Save, video playback and both Liquid Glass themes.

**Merge only after CI is green and real Android device tests are complete.**

## 0.3.6.1 notification hotfix (October 23, 2026)

**Root cause:** TDLib disables its notification-group API unless `notification_group_count_max` is configured to a positive value (the default is 0). The original Phase 3.5 subscribed to `UpdateNotificationGroup` without setting the option. The app therefore showed no OS alerts even with the Android permission granted.

Fixes:
- After `AuthorizationStateReady`, set TDLib `notification_group_count_max=15`, on every authorized startup. Log only success/error code into the in-app Settings state; never expose private message data.
- Show `Telegram notification events enabled` or a short setup error in Settings, along with number of received `UpdateNotificationGroup` callbacks.
- **Send test notification** posts a local Android alert for OS permission/channel diagnosis, even while GLACIA is in the foreground. It does *not* send a Telegram message.
- **Android notification settings** opens the platform's per-app notification settings so permissions and channels can be inspected.
- Settings dynamically rechecks actual OS notification availability when returning to GLACIA.

Manual test:
1. Install the locally configured v0.3.6.1 build over the existing app. Never clear TDLib data to troubleshoot notifications.
2. In GLACIA → Settings, verify **Telegram notification events enabled** and that Android notification permission is granted.
3. Tap **Send test notification**. Check Android's notification shade. A test notification there confirms Android channel/permission functionality, not Telegram delivery.
4. Go to the Android **home screen**, leaving GLACIA running in the background. Send a fresh message from another Telegram account to an unmuted chat. Check the shade; inspect the `TDLib notification updates received` diagnostic on returning.
5. No Android message notification is posted while GLACIA is foregrounded by design; messages update in the chat UI instead.
6. Android channels with DEFAULT or LOW importance may not produce a heads-up banner, even if the notification is present in the shade. Check OS app/channel importance and Do Not Disturb settings.
7. Force-stopping, swiping away on some OEMs, or OS killing the process may prevent notifications; FCM/persistent background delivery has **not** been implemented.
