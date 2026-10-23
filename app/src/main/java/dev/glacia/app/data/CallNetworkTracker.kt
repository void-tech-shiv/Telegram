package dev.glacia.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Android's default network observer for an ongoing 1:1 call. Purely read-only:
 * no socket binding, ICE restart, re-dial, polling or location/network IDs in logs.
 *
 * Android callbacks may arrive out of order and after unregister. A fresh
 * callback object + generation for each call avoids previous-call events
 * leaking into the next call's status.
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
    private var generation = 0L
    private var state = CallNetworkState()
    private var callback: ConnectivityManager.NetworkCallback? = null

    private fun makeCallback(epoch: Long) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            publish(epoch) { CallNetworkReducer.available(it, network.networkHandle) }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            // Capabilities in callback arguments are more reliable than
            // querying ConnectivityManager synchronously from callbacks.
            publish(epoch) {
                CallNetworkReducer.capabilities(
                    it, network.networkHandle,
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                )
            }
        }

        override fun onLost(network: Network) {
            publish(epoch) { CallNetworkReducer.lost(it, network.networkHandle) }
        }
    }

    private fun publish(epoch: Long, update: (CallNetworkState) -> CallNetworkState) {
        val next: CallNetworkState
        synchronized(lock) {
            if (!active || generation != epoch) return
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
            generation++
            state = CallNetworkState()
            onChanged(state)
            val nextCallback = makeCallback(generation)
            try {
                // Serialize registration with stop() so the callback cannot
                // be registered after an almost-simultaneous unregister.
                connectivity.registerDefaultNetworkCallback(nextCallback)
                callback = nextCallback
            } catch (error: RuntimeException) {
                active = false
                generation++
                Log.w("GLACIA-VoIP", "network_observer_unavailable type=${error.javaClass.simpleName}")
                onChanged(CallNetworkState(null, CallNetworkCondition.LIMITED))
            }
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!active) return
            active = false
            generation++
            state = CallNetworkState()
            callback?.let { registered ->
                try {
                    connectivity.unregisterNetworkCallback(registered)
                } catch (_: RuntimeException) {
                    // Already released or permission changed: not fatal.
                }
            }
            callback = null
        }
    }
}
