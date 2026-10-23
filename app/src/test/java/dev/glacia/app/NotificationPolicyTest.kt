package dev.glacia.app

import dev.glacia.app.data.NotificationPolicy
import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    @Test fun openChatSuppressesItsOwnBanner() {
        assertFalse(NotificationPolicy.shouldShow(true, 123L, 123L, false))
        assertTrue(NotificationPolicy.shouldShow(true, 123L, 456L, false))
        assertTrue(NotificationPolicy.shouldShow(false, 123L, 123L, false))
    }

    @Test fun explicitMuteAlwaysWins() {
        assertFalse(NotificationPolicy.shouldShow(true, null, 123L, true))
        assertFalse(NotificationPolicy.shouldShow(false, null, 123L, true))
    }

    @Test fun differentChatsGetDistinctNotificationTags() {
        assertNotEquals(
            NotificationPolicy.notificationTag(111L, 1),
            NotificationPolicy.notificationTag(222L, 1)
        )
        assertEquals("glacia.chat.42", NotificationPolicy.groupKey(42L))
    }
}
