package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeCallNegotiationTest {
    @Test fun emptyCustomParametersAreOmittedInsteadOfParsedAsJson() {
        assertNull(NativeCallNegotiation.customParametersOrNull(null))
        assertNull(NativeCallNegotiation.customParametersOrNull(""))
        assertNull(NativeCallNegotiation.customParametersOrNull(" \t\n "))
    }

    @Test fun validParametersArePreservedAndWhitespaceTrimmed() {
        assertEquals(
            "{\"enableAGC\":true}",
            NativeCallNegotiation.customParametersOrNull("  {\"enableAGC\":true}  ")
        )
    }

    @Test fun protocolIntersectionKeepsNativeOrderAndDoesNotAdvertiseUnsupportedVersions() {
        assertEquals(
            listOf("13.0.0", "12.0.0"),
            NativeCallNegotiation.compatibleVersions(
                listOf("13.0.0", "12.0.0", "9.0.0", "13.0.0"),
                listOf("12.0.0", "13.0.0", "17.0.0")
            )
        )
    }

    @Test fun unsupportedPeerVersionsDoNotSilentlyFallback() {
        assertTrue(
            NativeCallNegotiation.compatibleVersions(
                listOf("13.0.0", "12.0.0"),
                listOf("16.0.0")
            ).isEmpty()
        )
        assertTrue(NativeCallNegotiation.compatibleVersions(emptyList(), emptyList()).isEmpty())
    }
}
