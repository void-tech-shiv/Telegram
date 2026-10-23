# GLACIA Phase 3.3 — Cherrygram-inspired productivity (v0.3.4)

## Reference and licensing

Design inspiration: [Cherrygram](https://github.com/arsLan4k1390/Cherrygram), a feature-focused fork of Telegram for Android maintained by [arsLan4k1390](https://github.com/arsLan4k1390). Cherrygram's repository includes a **GNU GPL v2-or-later** license and modular chat/menu/preference customization. Its app architecture is a **large fork of Telegram Android** and is not drop-in compatible with GLACIA's Kotlin/Compose/TDLib architecture.

**No source files or assets were copied from Cherrygram.** This implementation is original and uses TDLib public APIs; Cherrygram is credited for the *product ideas*. Any future direct source integration would require a separate explicit GPL/license compatibility and attribution review.

## What's implemented

- **Chat filters** on the Chats tab: All, Unread, Groups and Private, combined with the current on-device chat text search.
- **Real Discover tab** (replaces placeholder): debounced, bounded TDLib `SearchChats` plus `SearchPublicChats`, fetching names and profile photos via `GetChat`.
- Search results are genuine Telegram chats, not mock/fake suggested accounts.
- Open a discovered conversation through the existing GLACIA message view; it uses the same authorized TDLib session and media download store.
- Search ignores responses belonging to earlier queries, deduplicates chat IDs, and avoids leaking raw Telegram server messages.
- Reuses Dark/Light Liquid Glass, TDLib authentication, text messaging and video/audio player.

## How to build / test locally

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-3-cherry-inspired
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

If the branch already exists, use `git switch feature/phase-3-3-cherry-inspired` and `git pull --ff-only origin feature/phase-3-3-cherry-inspired`.

Use your existing private `local.properties` to inject Telegram API ID/hash locally, then **Run** in Android Studio. Do not delete the installed app or clear TDLib storage just to upgrade.

## Device verification

1. Chats → **Unread**: only currently unread chats should remain; **Groups** and **Private** should filter correctly.
2. Type a search term on Chats: the selected category filter must still apply.
3. Discover → enter a public Telegram channel or user name (2+ characters). Wait for results to populate; try typing a new query quickly to verify older results do not overwrite current results.
4. Tap a public channel to open chat history; sending into read-only channels should show Telegram permission errors instead of crashing.
5. Test a private conversation, light and dark theme, returning from conversation via Back, and switch between Discover and Chats.
6. Verify existing video playback, photo-as-document View/Save, and the preserved Telegram login session.

## Known limitations / next work

- This is **chat/account discovery**, *not full-message/global-message content search*.
- Public search results depend on Telegram response availability, network and account permissions. It is **not** an anonymous web crawler.
- Search has a bounded limit per source and does not implement server-side pagination yet.
- Typing indicators, read receipts, reactions, global message search, voice recording, notifications, calls, and advanced Cherrygram configuration panels remain future work.
- Do not log or post Telegram verification codes, private chat data or locally configured APKs. Public CI APK is built with blank API credentials.

**Merge gate:** GitHub Actions compilation + unit tests and actual Android device verification. Keep the PR in draft until tests pass.
