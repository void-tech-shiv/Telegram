package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionPolicyTest {
    @Test fun rejectsMissingOrInvalidSavedMediaIds() {
        assertFalse(ExpressionPolicy.canSend(null))
        assertFalse(ExpressionPolicy.canSend(ExpressionOption(0, null, "")))
        assertFalse(ExpressionPolicy.canSend(ExpressionOption(-8, null, "GIF", animated = true)))
    }

    @Test fun stickerAndGifIdsMustBeValid() {
        assertTrue(ExpressionPolicy.canSend(ExpressionOption(51, 52, "👍")))
        assertTrue(ExpressionPolicy.canSend(ExpressionOption(83, 84, "GIF", 3, true)))
    }

    @Test fun stickerEmojiIsBoundedToAvoidUntrustedHugeLabels() {
        assertEquals("abc", ExpressionPolicy.normalizedEmoji("abc"))
        assertEquals(16, ExpressionPolicy.normalizedEmoji("✨".repeat(50)).length)
    }
}
