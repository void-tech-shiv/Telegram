package dev.glacia.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

class TgsDecoderTest {
    private fun compressed(text: String): File {
        val file = File.createTempFile("glacia-tgs-", ".tgs")
        GZIPOutputStream(file.outputStream()).use {
            it.write(text.toByteArray(Charsets.UTF_8))
        }
        file.deleteOnExit()
        return file
    }

    @Test fun decompressesRealisticSmallLottieJson() {
        val json = """{"v":"5.7.4","fr":30,"ip":0,"op":60,"layers":[]}"""
        assertEquals(json, TgsDecoder.readBounded(compressed(json)))
    }

    @Test fun rejectsBadOrNonJsonMedia() {
        assertNull(TgsDecoder.readBounded(compressed("not a lottie object")))
        val file = File.createTempFile("glacia-bad-", ".tgs")
        file.writeText("not gzip")
        file.deleteOnExit()
        assertNull(TgsDecoder.readBounded(file))
    }

    @Test fun rejectsCompressedBombExpansion() {
        assertNull(TgsDecoder.readBounded(compressed(
            "{" + "a".repeat(TgsDecoder.MAX_JSON_BYTES + 1) + "}"
        )))
    }
}
