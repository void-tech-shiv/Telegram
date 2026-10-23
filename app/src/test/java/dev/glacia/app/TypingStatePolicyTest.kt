package dev.glacia.app

import dev.glacia.app.data.TypingStatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingStatePolicyTest {
    @Test fun recentActivityIsShown() {
        assertTrue(TypingStatePolicy.isActive(6000L, 2500L))
    }

    @Test fun cancelledOrExpiredActivityIsHidden() {
        assertFalse(TypingStatePolicy.isActive(6000L, 6000L))
        assertFalse(TypingStatePolicy.isActive(6000L, 7000L))
    }
}
