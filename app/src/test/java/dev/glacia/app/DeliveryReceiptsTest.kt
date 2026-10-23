package dev.glacia.app

import dev.glacia.app.data.DeliveryReceipts
import dev.glacia.app.data.DeliveryState
import dev.glacia.app.data.Message
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryReceiptsTest {
    private fun message(
        id: Long = 10L, outgoing: Boolean = true,
        sending: Boolean = false, failed: Boolean = false
    ) = Message(id, 77L, "hello", "12:00", outgoing, sending = sending, failed = failed)

    @Test fun neverShowTicksOnIncomingMessages() {
        assertEquals(DeliveryState.NONE, DeliveryReceipts.status(message(outgoing = false), 99L))
    }

    @Test fun onlyServerOutboxAcknowledgementCountsAsRead() {
        assertEquals(DeliveryState.SENT, DeliveryReceipts.status(message(10L), 0L))
        assertEquals(DeliveryState.SENT, DeliveryReceipts.status(message(11L), 10L))
        assertEquals(DeliveryState.READ, DeliveryReceipts.status(message(10L), 10L))
        assertEquals(DeliveryState.READ, DeliveryReceipts.status(message(9L), 10L))
    }

    @Test fun failedAndPendingNeverCountAsDeliveredOrRead() {
        assertEquals(DeliveryState.FAILED, DeliveryReceipts.status(message(failed = true), 99L))
        assertEquals(DeliveryState.SENDING, DeliveryReceipts.status(message(sending = true), 99L))
        assertEquals(DeliveryState.SENDING, DeliveryReceipts.status(message(-1L), 99L))
    }
}
