package dev.glacia.app

import dev.glacia.app.data.ChatPreviewPrivacy
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatPreviewPrivacyTest {
    @Test fun hidesTelegramLoginCodes() {
        assertEquals(
            "🔒 Verification message hidden",
            ChatPreviewPrivacy.safePreview("Login code: 12345. Do not share this code.")
        )
        assertEquals(
            "🔒 Verification message hidden",
            ChatPreviewPrivacy.safePreview("Your verification code is 654321, keep it secret.")
        )
        assertEquals(
            "🔒 Verification message hidden",
            ChatPreviewPrivacy.safePreview("OTP: 456789")
        )
    }

    @Test fun preservesOrdinaryChatText() {
        assertEquals("Hey buddy!", ChatPreviewPrivacy.safePreview("Hey buddy!"))
        assertEquals("📷 Photo", ChatPreviewPrivacy.safePreview("📷 Photo"))
        assertEquals(
            "We discussed a code review for class",
            ChatPreviewPrivacy.safePreview("We discussed a code review for class")
        )
    }
}
