package dev.glacia.app.data

/**
 * No IP addresses, SSIDs, cell identifiers or native ICE credentials.
 * The network ID is an opaque local handle used only to reject stale events.
 * This is network OBSERVATION, not proof of WebRTC connectivity/recovery.
 */
internal enum class CallNetworkCondition {
    CHECKING, CHANGING, WIFI, CELLULAR, OTHER, LIMITED, OFFLINE
}

internal data class CallNetworkState(
    val activeNetwork: Long? = null,
    val condition: CallNetworkCondition = CallNetworkCondition.CHECKING
) {
    val hint: String?
        get() = when (condition) {
            CallNetworkCondition.CHECKING -> "Checking network…"
            CallNetworkCondition.CHANGING -> "Network changing…"
            CallNetworkCondition.LIMITED -> "Network may be limited"
            CallNetworkCondition.OFFLINE -> "Waiting for network…"
            CallNetworkCondition.WIFI, CallNetworkCondition.CELLULAR, CallNetworkCondition.OTHER -> null
        }
}

/** Pure reducer to make callback reordering and network replacement testable. */
internal object CallNetworkReducer {
    fun available(old: CallNetworkState, network: Long): CallNetworkState =
        if (old.activeNetwork == network) old
        else CallNetworkState(
            network,
            if (old.activeNetwork == null && old.condition == CallNetworkCondition.CHECKING)
                CallNetworkCondition.CHECKING
            else CallNetworkCondition.CHANGING
        )

    fun capabilities(
        old: CallNetworkState,
        network: Long,
        hasInternet: Boolean,
        validated: Boolean,
        wifi: Boolean,
        cellular: Boolean
    ): CallNetworkState {
        if (old.activeNetwork != network) return old
        val status = if (!hasInternet || !validated) CallNetworkCondition.LIMITED
            else if (wifi) CallNetworkCondition.WIFI
            else if (cellular) CallNetworkCondition.CELLULAR
            else CallNetworkCondition.OTHER
        return old.copy(condition = status)
    }

    fun lost(old: CallNetworkState, network: Long): CallNetworkState =
        if (old.activeNetwork != network) old
        else CallNetworkState(null, CallNetworkCondition.OFFLINE)
}
