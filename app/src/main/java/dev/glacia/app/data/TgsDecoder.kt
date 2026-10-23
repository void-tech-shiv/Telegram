package dev.glacia.app.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Telegram .tgs is gzipped Lottie JSON, NOT a PNG/JPEG.
 * Refuse oversized compressed/expanded payloads to avoid zip bombs or
 * unbounded allocations from untrusted Telegram media.
 */
internal object TgsDecoder {
    const val MAX_COMPRESSED_BYTES = 1_000_000L
    const val MAX_JSON_BYTES = 2_000_000

    fun readBounded(file: File): String? {
        if (!file.isFile || file.length() !in 1..MAX_COMPRESSED_BYTES) return null
        return runCatching {
            val bytes = ByteArrayOutputStream()
            GZIPInputStream(file.inputStream().buffered()).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (bytes.size() + n > MAX_JSON_BYTES) return null
                    bytes.write(buffer, 0, n)
                }
            }
            bytes.toString(Charsets.UTF_8.name()).takeIf {
                it.trimStart().startsWith("{")
            }
        }.getOrNull()
    }
}
