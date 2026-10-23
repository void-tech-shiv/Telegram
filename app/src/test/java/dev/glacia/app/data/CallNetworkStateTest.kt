package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallNetworkStateTest {
    @Test fun noNetworkThenNewWifi() {
        val initial = CallNetworkState()
        val available = CallNetworkReducer.available(initial, 11L)
        assertEquals(CallNetworkCondition.CHECKING, available.condition)
        val connected = CallNetworkReducer.capabilities(
            available, 11L, hasInternet = true, validated = true,
            wifi = true, cellular = false
        )
        assertEquals(CallNetworkCondition.WIFI, connected.condition)
        assertNull(connected.hint)
    }

    @Test fun newDefaultNetworkDoesNotGetClosedByOldNetworkLost() {
        val wifi = CallNetworkReducer.capabilities(
            CallNetworkReducer.available(CallNetworkState(), 11L),
            11L, true, true, true, false
        )
        val switching = CallNetworkReducer.available(wifi, 22L)
        assertEquals(CallNetworkCondition.CHANGING, switching.condition)
        assertEquals(switching, CallNetworkReducer.lost(switching, 11L))
        val cellular = CallNetworkReducer.capabilities(switching, 22L, true, true, false, true)
        assertEquals(CallNetworkCondition.CELLULAR, cellular.condition)
    }

    @Test fun outdatedCapabilitiesCannotOverwriteNewDefaultNetwork() {
        val next = CallNetworkReducer.available(
            CallNetworkReducer.available(CallNetworkState(), 4L), 5L
        )
        assertEquals(
            next,
            CallNetworkReducer.capabilities(next, 4L, true, true, true, false)
        )
    }

    @Test fun lostDefaultNetworkGivesWaitingHintAndRecoveryCanResume() {
        val first = CallNetworkReducer.available(CallNetworkState(), 1L)
        val lost = CallNetworkReducer.lost(first, 1L)
        assertEquals(CallNetworkCondition.OFFLINE, lost.condition)
        assertEquals("Waiting for network…", lost.hint)
        val next = CallNetworkReducer.available(lost, 2L)
        assertEquals(CallNetworkCondition.CHANGING, next.condition)
    }

    @Test fun unvalidatedOrCaptivePortalDoesNotClaimOnline() {
        val a = CallNetworkReducer.available(CallNetworkState(), 10L)
        val limited = CallNetworkReducer.capabilities(a, 10L, true, false, true, false)
        assertEquals(CallNetworkCondition.LIMITED, limited.condition)
        assertEquals("Network may be limited", limited.hint)
        assertEquals(a, CallNetworkReducer.lost(a, 99L))
    }
}
