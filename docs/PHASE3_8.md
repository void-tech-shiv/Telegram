# GLACIA Phase 3.8 — v0.3.9 · Message Actions and Calls

> Experimental branch: `feature/phase-3-8-message-actions-calls` based on Phase 3.7. This pull request remains a draft until device QA.

## Implemented in this branch

- **Long-press any message** to open a permission-aware action dialog backed by TDLib `GetMessageProperties`. The server may disallow actions for a particular message/chat.
- **Reply:** preview the quoted message above the composer; outgoing text uses TDLib `InputMessageReplyToMessage`. Cancel removes reply mode.
- **Forward:** select a destination from existing chats and send via `ForwardMessages`. Telegram may prevent forwarding of protected messages or into read-only chats.
- **Edit:** edit *your own text* where Telegram grants permission. The message is only updated on server success; history updates also keep it fresh.
- **Delete:** explicitly choose "Delete only for me" or "Delete for everyone" where Telegram allows it; never conflate the two.
- **Reactions:** send or remove common emoji reactions with TDLib's reaction API; server reaction counts update existing message bubbles and rejected reactions surface as errors.
- Preserves working live chats, sent/read checks, media sending, Opus voice notes, notifications, and Midnight/Frosted themes.

## Voice/video calling — external handoff only

The call icon in a private chat is explicitly labeled **Open contact in Telegram for voice/video call**. On tap, GLACIA resolves the user's public username using TDLib and opens the contact's profile in the installed official Telegram application (falling back to a browser/handler). The user must manually start the voice/video call there.

**No in-app call audio/video is claimed here.** TDLib's `CreateCall` / `AcceptCall` functions implement signaling, not the media transport, microphone/receiver pipeline, secure key exchange integration and call connection stack. A tested, audited tgcalls/WebRTC native engine, TURN/ICE networking, ongoing-call Android services/notification integration, incoming call UI and hardware-device QA are necessary for genuine in-app calling. Do not ship a fake "connecting" UI that rings a recipient without an audio path. Contacts without public usernames cannot use this limited handoff.

## Test matrix

1. Two Telegram accounts: long-press an incoming text → Reply → send; inspect the reply relationship in official Telegram.
2. Long-press your own text → Edit → new text; confirm official Telegram displays the edited content and it persists after restart.
3. Long-press an incoming message → Forward → different chat; verify destination and sender attribution.
4. Delete a disposable test message, first **for me**, then a second one **for everyone**. Verify from both clients that scopes differ as expected.
5. Add 👍, switch to ❤️, remove a chosen reaction, check counts update on both clients. Some chats restrict permitted emoji; rejections must not crash the app.
6. Long-press protected/group/channel/secret messages; unavailable actions should be absent or return a clear TDLib error.
7. In a contact with public Telegram username, open the external call shortcut, then start the voice/video call in official Telegram. Verify UI never misstates that GLACIA itself placed an in-app call.
8. Retest voice note recording, documents, video playback, notification banners, app resume, both glass themes and typing receipts.

## Build locally

```powershell
cd D:\telegram\Telegram
git fetch origin
git switch --track origin/feature/phase-3-8-message-actions-calls
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

If already on the branch, run `git pull --ff-only origin feature/phase-3-8-message-actions-calls`. Keep local private Telegram API ID/hash out of Git and preserve existing TDLib storage; do not uninstall or clear app data.

**Known limitations:** Native call transport, native group calls, reactions beyond standard emoji, advanced multi-select forwarding, media captions editing, reply to cross-topic message, and message-content search are not yet implemented.
