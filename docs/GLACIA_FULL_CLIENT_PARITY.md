# GLACIA — Full Telegram client parity and independent branding

## Goal

Build GLACIA as a complete Telegram-compatible Android client with Cherrygram-like discoverability, chat organization, profiles, settings and customization, while keeping GLACIA's own name, assets, navigation identity and functioning Kotlin/Compose/TDLib implementation.

Reference only: https://github.com/arsLan4k1390/Cherrygram

## Architectural decision

GLACIA currently uses Kotlin, Jetpack Compose and TDLib. Cherrygram is derived from the Telegram Android application. **Do not merge Cherrygram's entire source tree into GLACIA or replace existing session/VoIP components.** Port user experiences and implement individual missing capabilities against GLACIA's current TDLib layer, behind tested feature flags where necessary.

## GLACIA-visible branding requirements

- App display name, adaptive launcher icon, splash screen, notification channel names/icons, deep-link labels, Settings/About header, share strings and release notes say GLACIA.
- Replace promotional shortcuts, donor buttons and support/channel destinations from reference projects with GLACIA destinations only when these are actually configured; otherwise remove the shortcut.
- Audit visible localized strings, accessibility labels, widgets, menus and stored preference descriptions.
- Use a GLACIA-specific applicationId and deep-link scheme only after migration testing. Preserve session database and app data during upgrades if applicationId stays unchanged.
- Never rebrand and redistribute upstream copyrighted assets without permission.

## Licensing and source provenance

Cherrygram's repository includes GNU GPL v2. Its derivative Telegram sources and included third-party modules may carry further notices. Any copied/modifed GPL source must retain required copyright notices, license text, source availability and change history. Branding changes do not remove those obligations. No Cherrygram promotional text is required in the GLACIA UI merely because it was used for design reference; mandatory legal notices must remain available in legal/source notices when GPL code is redistributed.

Prefer independent implementation guided by UX behavior rather than copying source/assets. Audit each dependency and submodule before incorporating it.

## Implementation milestones

### M1 — GLACIA shell and navigation
- Unified chats home with All/Unread/Personal/Groups/Channels/Bots filters, archive entry, search, folders and long-press actions.
- Drawer / Settings / Contacts / New Message / Calls / Saved Messages navigation using GLACIA components.
- Material You + Liquid Glass dark/light personalization and animations with accessibility alternatives.

### M2 — Real chats and media
- Profile/avatar/status display, message scrolling/pagination, delivery/read states, typing, replies, forwards, edits, deletion, reactions, pinned messages and chat-level search.
- Upload/download photos, videos, documents, voice messages and stickers/GIFs with progress, retry and permissions.
- Message context menus, sharing and accurate TDLib permission/error handling.

### M3 — Settings and identities
- Account switcher, edit profile/bio/photo/username, contacts import/search, blocked users, privacy/security, connected sessions, 2FA settings where supported.
- Per-chat notification/mute preferences, background delivery behavior, data/storage usage, auto-download and language/theme options.

### M4 — Communities and advanced capabilities
- Group/channel info, participants, roles and permissions; invite links, join requests, topics, channel posting where permitted.
- Search across messages/media, message scheduling, forwarding controls, polls, drafts, emoji and sticker packs.
- Calls: preserve existing tested audio/video path; regression-test foreground/background controls and hang-up notification.

### M5 — Hardening / release
- Android build + unit tests + instrumentation tests; actual two-account device QA and migration/upgrade tests.
- Background/push behavior after process kill, different Android versions, permission denied paths, airplane mode/network switching, battery restrictions.
- Zero credentials in source; reproducible build notes, signing, safe CI, complete licenses and user-facing about page.

## Mandatory regression gates before any merge

1. Existing GLACIA logged-in session survives upgrade; no silent logout or database wipe.
2. Both accounts can send/read messages, receive and group notifications, and handle chat pagination.
3. Voice/video calling, microphone/camera permission flow, audio continuing when Home is pressed and ongoing notification Hang up continue working.
4. Every newly rendered screen uses GLACIA branding; no dangling external promotion links.
5. Check licensing notices before bundling any upstream code.
6. CI checks and real-device QA pass. PR stays draft until the relevant milestone passes.

## Implementation rule

Deliver milestones via small draft PRs. No false 'Telegram parity complete' claims until features are implemented and tested. Do not merge dormant unfinished earlier draft branches by accident.
