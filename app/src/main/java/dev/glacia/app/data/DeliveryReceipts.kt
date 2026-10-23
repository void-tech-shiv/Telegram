package dev.glacia.app.data

/** TDLib acknowledgements are authoritative: do not infer "read" from local UI state. */
enum class DeliveryState { NONE, SENDING, FAILED, SENT, READ }

object DeliveryReceipts {
    fun status(message: Message, lastReadOutboxId: Long): DeliveryState = when {
        !message.outgoing -> DeliveryState.NONE
        message.failed -> DeliveryState.FAILED
        message.sending -> DeliveryState.SENDING
        message.id <= 0L -> DeliveryState.SENDING
        lastReadOutboxId > 0L && message.id <= lastReadOutboxId -> DeliveryState.READ
        else -> DeliveryState.SENT
    }
}
