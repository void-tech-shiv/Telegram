package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingCallStateTest {
    @Test fun pendingCallCanBePresentedAsVoiceOrVideo() {
        val voice = IncomingCallState.ringing(null, 12, 24, false)!!
        assertEquals(false, voice.video)
        val video = IncomingCallState.ringing(voice, 12, 24, true)!!
        assertEquals(true, video.video)
        assertEquals("Telegram contact", video.callerName)
    }

    @Test fun staleCallEndCannotDismissNewerIncomingCall() {
        val current = IncomingCallState.ringing(null, 21, 84, true)
        assertEquals(current, IncomingCallState.finished(current, 20))
        assertNull(IncomingCallState.finished(current, 21))
    }

    @Test fun staleNameResolutionCannotRenameNewerCall() {
        val newer = IncomingCallState.ringing(null, 66, 123, false)
        assertEquals(newer, IncomingCallState.named(newer, 65, "Old contact"))
        assertEquals("Actual contact",
            IncomingCallState.named(newer, 66, " Actual contact ")?.callerName)
    }

    @Test fun duplicatePendingPreservesNameAndInvalidPayloadDoesNotReplaceCall() {
        val a = IncomingCallState.named(
            IncomingCallState.ringing(null, 7, 8, false), 7, "A"
        )
        assertEquals("A", IncomingCallState.ringing(a, 7, 8, false)?.callerName)
        assertEquals(a, IncomingCallState.ringing(a, 0, 8, false))
        assertEquals(a, IncomingCallState.ringing(a, 99, 0, false))
    }

    @Test fun newCallCannotReuseDifferentUsersName() {
        val a = IncomingCall(7, 8, false, "Old contact")
        assertEquals("Telegram contact",
            IncomingCallState.ringing(a, 7, 9, false)?.callerName)
    }
}
