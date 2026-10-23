# GLACIA 0.3.7 — Smart Notifications + Liquid Glass

**Development branch:** `feature/phase-3-6-smart-notifications`. Based on Phase 3.5; not merged to main.

## Implemented in the branch

### 1. Notification tap → exact chat
Each TDLib `UpdateNotificationGroup` includes its `chatId`. Android `PendingIntent` now has a unique request code per TDLib group and a chat-id extra. `MainActivity.onNewIntent` and cold Activity startup both forward the target to Compose. The existing TDLib session fetches the chat if absent from the main chat list and navigates to it after it becomes available. A read-only channel still obeys Telegram permissions.

### 2. GLACIA brand
Custom white-on-transparent ice-star `ic_glacia_notification` replaces the Android stock mail icon in both real and test alerts. A midnight-blue adaptive launcher icon, with a cyan GLACIA star and monochrome variant, replaces the default Android icon.

### 3. Per-chat grouping
TDLib still owns notification policies, mute/delay and notification-group lifecycle. GLACIA now posts a separate Android notification identified by TDLib group ID *and* chat ID, preserving independent tap targets. An Android summary groups two or more active notification groups without swallowing individual chat notifications. New messages on an already visible chat are suppressed to avoid duplicate heads-up banners. When TDLib clears a notification group, that Android child is canceled.

### 4. Mute/unmute individual chats
Open a conversation and tap the bell icon at the top right. GLACIA calls TDLib `SetChatNotificationSettings` with a chat-level override, so the mute is backed by Telegram rather than only filtering Android alerts. Tap again to unmute. Custom chat-level settings may override default group rules; the UI currently shows explicit chat-level mute state, not the effective inherited setting. Telegram Saved Messages may reject changes.

### 5. Optional FCM background push *integration scaffold* (not production-ready)
Firebase SDK, `GlaciaFirebaseMessagingService`, token caching, TDLib `RegisterDevice` and TDLib `ProcessPushNotification` are wired for a **single local Telegram account**. Firebase is intentionally **disabled** for CI/demo builds without a matching `app/google-services.json`. The application checks `BuildConfig.FCM_CONFIGURED` before requesting tokens and processing data-only payloads. Push payloads and private tokens are never written to logs or notifications. The TDLib client is shared between Activity and Firebase callback to avoid concurrent access to the encrypted TDLib database.

**This does NOT mean reliable process-death push is finished.** It requires project provisioning and **real FCM–Telegram end-to-end delivery testing**, correct Firebase sender/project authorization for Telegram's push transport, correct data-only encrypted payloads, and lifecycle QA on actual devices. `FirebaseMessagingService` has a short execution window; current bounded TDLib push processing is a first integration, **not a guarantee of completion after process death**. Force-stopping an Android app blocks FCM until the user launches it again. OS battery restrictions/Doze, private encryption and OEM behavior can delay or prevent push. Do not rely on this implementation for urgent or security-critical messages.

To activate a local Firebase build:
1. Create or gain authorized access to a Firebase Android project with **package name `dev.glacia.app`** and FCM enabled. Check compatibility with Telegram's push sender/project requirements; creating a random Firebase project alone does not establish Telegram-to-FCM delivery.
2. Download its `google-services.json` into **`app/google-services.json`** (ignored by Git). Never commit Google service-account keys, FCM server tokens, or the Telegram API hash. **Do not** put FCM service-account credentials inside the mobile app.
3. Keep your private Telegram API ID and hash in root `local.properties`. Build your configured APK locally.
4. In Settings, check **PUSH STATUS**. A successful TDLib device registration is **not proof** that push actually arrives while the process is killed.
5. Test fresh data-only encrypted Telegram pushes with the process in background, then after Android OS kills the process (not force-stop). Confirm TDLib handles the payload, honors Telegram mutes, and posts only validated group alerts. If a push arrives but TDLib fails to finish within the callback window, a robust follow-up implementation must use a correctly designed Android background job/foreground sync strategy.

### 6. Design polish
Chat bubbles use subtle two-tone gradients, differentiated rounded tails and animated content-size changes. Tab selection transitions smoothly. Existing dark/light themes and media viewers remain intact.

## QA gate (two accounts, real phone/emulator)

- Tap notifications from two different conversations (while app foreground/background and after Activity recreation), confirm each opens its own correct chat.
- Trigger notifications from several different chats: verify separate child notifications and summary count; clearing a Telegram notification group removes only its matching Android child.
- Verify normal/silent/high-priority channel behavior, Android 13+ runtime permission and GLACIA icon (status bar should display ice-star, not generic mail).
- Open a chat and toggle **Mute**; send test messages from a second account. Confirm Telegram's mute settings and Android behavior agree, including after relaunch.
- Check app opening from a notification for a chat not already loaded in Chats.
- In Android Studio, test Midnight Glass, Frosted Daylight, different display/font sizes, media playback and no duplicate alerts while actively viewing the same conversation.
- If Firebase project configured, verify token registration and actual push *after process death* separately. This item is not covered by CI and must pass before claiming reliable background delivery.

### Install / test (Windows PowerShell)

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-6-smart-notifications
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

If local branch exists, switch to it and `git pull --ff-only origin feature/phase-3-6-smart-notifications`. Keep the installed package and TDLib session; do not uninstall or clear data.

**Public CI builds** contain neither usable Telegram API credentials nor the private Firebase config. Android CI verifies compilation and JVM tests, **not** actual FCM/TDLib notification arrival.
