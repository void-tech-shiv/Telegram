package dev.glacia.app.data

/**
 * Hide likely one-time login credentials in chat-list previews and on-device
 * chat-list search. Message bodies remain available after opening the chat.
 * This is best-effort redaction, not a substitute for protecting screenshots.
 */
object ChatPreviewPrivacy {
    private val loginCode = Regex(
        """(?i)\b(?:login|verification|authentication|sign[\s-]*in|security|telegram)\s+(?:code|otp)\b[^\n]{0,35}?\b\d{4,8}\b"""
    )
    private val oneTimePasscode = Regex(
        """(?i)\b(?:otp|one[\s-]*time\s+(?:passcode|password))\b[^\n]{0,35}?\b\d{4,8}\b"""
    )

    fun safePreview(original: String): String =
        if (loginCode.containsMatchIn(original) || oneTimePasscode.containsMatchIn(original)) {
            "🔒 Verification message hidden"
        } else original
}
