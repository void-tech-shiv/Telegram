package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Resolves only TDLib-owned, app-private local files.
 * No direct unauthenticated Telegram CDN URLs or external storage permissions.
 */
class TdlibFileStore(private val client: () -> Client?) {
    private val lock = Any()
    private val requested = mutableSetOf<Int>()
    private val _paths = MutableStateFlow<Map<Int, String>>(emptyMap())
    val paths = _paths.asStateFlow()

    /** Queue a bounded-priority thumbnail download once; show it when TDLib reports completion. */
    fun ensure(file: TdApi.File?) {
        if (file == null) return
        val local = file.local
        if (local != null && local.isDownloadingCompleted && local.path.isNotBlank()) {
            publish(file.id, local.path)
            return
        }
        if (local == null || !local.canBeDownloaded) return
        val active = client() ?: return
        synchronized(lock) {
            if (!_paths.value.containsKey(file.id) && !requested.add(file.id)) return
        }
        val download = TdApi.DownloadFile().apply {
            fileId = file.id
            priority = 8
            offset = 0L
            limit = 0L
            synchronous = false
        }
        active.send(download) { response ->
            if (response is TdApi.File) accept(response)
            // Errors do not produce unbounded automatic retries on every recomposition.
        }
    }

    fun onUpdate(update: TdApi.Object) {
        if (update is TdApi.UpdateFile) accept(update.file)
    }

    private fun accept(file: TdApi.File) {
        val local = file.local ?: return
        if (local.isDownloadingCompleted && local.path.isNotBlank()) {
            publish(file.id, local.path)
        }
    }

    private fun publish(fileId: Int, path: String) {
        synchronized(lock) {
            if (_paths.value[fileId] == path) return
            _paths.value = _paths.value + (fileId to path)
        }
    }

    fun clear() {
        synchronized(lock) {
            requested.clear()
            _paths.value = emptyMap()
        }
    }
}
