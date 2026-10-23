package dev.glacia.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Android's active DEFAULT network observer for an ongoing 1:1 call.
 * It never restarts/dials a call, binds sockets, handles ICE credentials,
 * logs addresses, or keeps a process alive. Registration exists only while
 * the call exists and is removed on hangup/logout.
 *
 * Android docs: avoid synchronous network queries inside callbacks.
 * onAvailable can precede capabilities and stale onLost can arrive after
 * a replacement network is already current.
 */
internal class CallNetworkTracker(
    context: Context,
    private val onChanged: (CallNetworkState) -> Unit
) {
    private val connectivity =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
            as ConnectivityManager
    private val lock = Any()
    private var active = false
    private var state = CallNetworkState()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publish { CallNetworkReducer.available(it, network.networkHandle) }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            publish {
                CallNetworkReducer.capabilities(
                    it,
                    network.networkHandle,
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                )
            }
        }

        override fun onLost(network: Network) {
            publish { CallNetworkReducer.lost(it, network.networkHandle) }
        }
    }

    private fun publish(update: (CallNetworkState) -> CallNetworkState) {
        val next: CallNetworkState
        synchronized(lock) {
            if (!active) return
            next = update(state)
            if (next == state) return
            state = next
        }
        onChanged(next)
    }

    fun start() {
        synchronized(lock) {
            if (active) return
            active = true
            state = CallNetworkState()
            onChanged(state)
            try {
                connectivity.registerDefaultNetworkCallback(callback)
            } catch (error: RuntimeException) {
                active = false
                Log.w("GLACIA-VoIP", "network_observer_unavailable type=${error.javaClass.simpleName}")
                onChanged(CallNetworkState(null, CallNetworkCondition.LIMITED))
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!active) return
            active = false
            state = CallNetworkState()
            try {
                connectivity.unregisterNetworkCallback(callback)
            } catch (_: RuntimeException) {
                // Already unregistered/permission revoked; no impact to call.
            }
        }
    }
}
