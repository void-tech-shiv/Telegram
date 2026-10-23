package dev.glacia.app.data

/**
 * The native ntgcalls v3.0 P2P connector JSON-parses every non-null
 * customParameters string. TDLib can provide an empty string to mean
 * "no extra parameters"; passing "" to JSON::parse throws before ICE starts.
 *
 * This helper is deliberately pure Kotlin so its behavior is JVM-testable.
 * Never put call JSON, ICE credentials, or connection endpoints in logs.
 */
internal object NativeCallNegotiation {
    fun customParametersOrNull(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * In CallStateReady the protocol versions belong to the OTHER party.
     * Supply only mutually supported versions to ntgcalls' match_version().
     * Keep the library's order and do not hardcode protocol version strings.
     */
    fun compatibleVersions(local: List<String>, peer: List<String>): List<String> {
        val remote = peer.filter { it.isNotBlank() }.toSet()
        return local.filter { it.isNotBlank() && it in remote }.distinct()
    }
}
