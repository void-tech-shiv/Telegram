# GLACIA Phase 3.4 — Read receipts and foreground message viewing

Branch `feature/phase-3-4-read-receipts` (based on `feature/phase-3-3-cherry-inspired`). Version `0.3.5`. **Experimental — draft PR.**

## What changed

- Calls TDLib `OpenChat` while the conversation screen is open, and `CloseChat` when leaving it.
- Foreground-only acknowledgement: the chat must be visible while the activity is resumed. Only incoming messages that cover at least half their item within the current scroll viewport are passed through `ViewMessages`, with a short 320 ms debounce.
- TDLib's `UpdateChatReadInbox` is the only authority for changing the chat's unread badge. Opening a chat does not instantly force the unread badge to zero.
- Outgoing message status now reflects the TDLib acknowledgement state: **Sending**, **Failed**, **✓ sent** or **✓✓ read**. Read state comes from the chat's server-driven `lastReadOutboxMessageId` and `UpdateChatReadOutbox`, never an invented local timer.
- Already acknowledged visible-message ranges are deduplicated to avoid excessive TDLib traffic. Marking a chat unread again permits a new acknowledgement.
- Basic unit tests verify incoming messages never show ticks and failed/pending messages are never misreported as read.

## Device tests — required before merge

1. On a second Telegram client, send several messages into a GLACIA test chat. The unread badge should increase.
2. Open the GLACIA conversation at the bottom; only messages genuinely appearing on screen should trigger TDLib read acknowledgements. Return to Chats and verify the badge follows server updates.
3. Scroll through an old conversation, including a long text and an oversized media card. Confirm offscreen messages aren't immediately marked viewed.
4. Put GLACIA into the background while another client sends messages. Messages must **not** be acknowledged as viewed by GLACIA while in background.
5. Send a text message from GLACIA. It should show Sending, then ✓; after the recipient's Telegram client reads it, ✓✓ if TDLib exposes that read state for this chat.
6. Test a group/channel where delivery information may differ, an offline reconnect, switching light/dark theme and session persistence.

## Limitations

- This increment does not implement typing indicators, push notifications, media sending, global message-content search, per-member group read receipts or secret-chat parity.
- TDLib's chat-level outbox marker is not evidence every recipient in a group read a message. Avoid presenting group check marks as per-user delivery.
- Without a background notification service, GLACIA may miss messages when the process is stopped; that is future work.
- Keep private Telegram API credentials, local TDLib session files and personal chat screenshots out of public builds and bug reports.
