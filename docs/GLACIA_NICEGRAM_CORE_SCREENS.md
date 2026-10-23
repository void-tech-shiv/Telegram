# GLACIA v0.3.25 — compact Telegram core UI (reference screenshots)

## Visual target
References supplied in the conversation show Nicegram Android on 24 October
2026: charcoal canvas, grouped settings rows with colorful icons, large centered
Telegram profile portrait, three real account actions, searchable recent-status
contacts, simple hub shortcuts and a five-item bottom navigation.

**Inspired by layout and density, not branding or proprietary media.**
The app stays GLACIA. No third-party logos, exact artwork, premium-payment
claims, paid promotion, blockchain wallet, anonymous-account promises or fake
AI tools are introduced.

## Implemented
- **Settings:** compact dark/light sectioned menus with real account link,
  chat appearance theme, Telegram privacy/blocked-user rules, active sessions,
  notifications, chat-folder dialog, diagnostics and official Telegram FAQ.
  Real functionality is delegated to existing working TDLib/API and Android
  settings controls. Search filters the settings menu locally.
- **Profile:** large live TDLib avatar + account name/public @username, actual
  Telegram ID, own phone number and bio. Set Photo performs MIME-constrained
  user-initiated JPEG/PNG upload (max 5 MB); Edit Info reuses existing
  username/name/bio editor; Settings navigates to actual settings.
- **Contacts:** dense contact list with actual photo, online/last-seen status
  and server-backed sorting. Live TDLib `UpdateUserStatus` refreshes UI.
  Real search, add by public @username, explicit delete confirmation via
  long press, Saved Messages, and Android Share-sheet Invite Friends.
  No Android address-book permission, SMS read or unauthorized contact sync.
- **GLACIA hub:** functional shortcuts for Discover Telegram, Saved Messages,
  Contacts, Folders, Group/Channel creation, Privacy & Sessions, Profile and
  confirmed per-device logout. Unlike reference Nicegram, no irrelevant ads
  or fake crypto/premium/tools tiles.
- **Chat list:** inherits PR #29's compact charcoal chat layout, real last
  message date/unread counts and the five GLACIA tabs.
- Core two-way voice/video calling, Android background-call notification,
  message synchronization, media and auth session remain unchanged.

## Explicit scope exclusions
- Stories/posts and "Add post" aren't added: Telegram backend support and
  moderation/privacy controls are not implemented in GLACIA.
- Nicegram premium, Telegram Stars, Telegram Business, promotional affiliate
  listings, AI Tokens, market predictions, anonymous registrations and
  cryptowallets are third-party features and must not be simulated as working.
- Full Telegram-local media auto-download policy, granular sound choices,
  device contact imports, contact-specific call history and complete
  role/ban management remain future features.

## Windows PowerShell
First checkout:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-4-6-nicegram-core-screens
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Existing branch:
```powershell
cd D:\telegram\Telegram
git fetch origin
git switch feature/phase-4-6-nicegram-core-screens
git pull --ff-only origin feature/phase-4-6-nicegram-core-screens
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Expected installed version: `0.3.25`. Keep existing root
`local.properties`, including the experimental calling opt-in. Do NOT
uninstall GLACIA, clear app data, commit TDLib sessions, or expose API keys.

## Real Android acceptance gates
- [ ] Compile both demo/JVM tests and experimental-calls debug Android build.
- [ ] Settings screen grouped rows fit 360dp and user-adjusted font sizes;
  search filters rows. Account/theme/privacy/notifications/devices/folders
  all open actual controls, no decorative dead-end row.
- [ ] Settings account refresh/name/bio/username changes synchronize with
  official Telegram on a second phone.
- [ ] Profile loads actual Telegram photo, name, ID, phone, username and bio.
  Set Photo lets user choose an image and reflects Telegram after upload.
- [ ] Contacts sort live online/last-seen correctly when permitted; when
  hidden, don't invent times. Search opens chat, add/remove works with
  confirmation and Saved Messages appears on official Telegram.
- [ ] Middle GLACIA tab shortcuts navigate to actual screens; optional
  public search returns actual TDLib results, logout requires confirmation.
- [ ] Back navigation from nested panels, five tabs, Android rotation/IME,
  portrait/landscape, light theme and scroll performance.
- [ ] Existing two-way voice/video, ongoing background mic/camera, notification
  Hang up, reactions, archives and sticker/media playback regressions.

Draft PR stacks on PR #29; don't merge to main before its parent and phone QA.
