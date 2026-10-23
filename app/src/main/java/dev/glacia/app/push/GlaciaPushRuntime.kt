package dev.glacia.app.push

import android.content.Context
import dev.glacia.app.data.TdlibSession

/**
 * One TDLib client per Android process. The activity and data-only FCM callback
 * must never concurrently open the same TDLib database from different clients.
 */
object GlaciaPushRuntime {
    private var shared: TdlibSession? = null
    private var uiOwners = 0
    private var pushOwners = 0

    @Synchronized fun attachUi(context: Context): TdlibSession {
        uiOwners++
        return get(context)
    }

    @Synchronized private fun get(context: Context): TdlibSession =
        shared ?: TdlibSession(context.applicationContext).also { shared = it }

    @Synchronized fun detachUi(session: TdlibSession) {
        uiOwners = (uiOwners - 1).coerceAtLeast(0)
        if (uiOwners == 0 && pushOwners == 0 && shared === session) {
            session.close()
            shared = null
        }
    }

    /** Only invoked on the Firebase worker thread, never the Android main thread. */
    fun receivePush(context: Context, payload: String) {
        val session = synchronized(this) {
            pushOwners++
            get(context)
        }
        try {
            session.start()
            session.processPushPayload(payload)
        } finally {
            synchronized(this) {
                pushOwners = (pushOwners - 1).coerceAtLeast(0)
                if (uiOwners == 0 && pushOwners == 0 && shared === session) {
                    session.close()
                    shared = null
                }
            }
        }
    }

    @Synchronized fun registerTokenIfRunning(token: String) {
        shared?.rememberPushToken(token)
    }
}
