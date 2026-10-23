package dev.glacia.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Resolves only TDLib-owned, app-private local files.
 * No direct unauthenticated Telegram CDN URLs or external storage permissions.
 */
data class FileTransfer(
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)

class TdlibFileStore(private val client: () -> Client?) {
    private val lock = Any()
    private val requested = mutableSetOf<Int>()
    private val manualRequests = mutableSetOf<Int>()
    private val _transfers = MutableStateFlow<Map<Int, FileTransfer>>(emptyMap())
    val transfers = _transfers.asStateFlow()
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
            if (_paths.value.containsKey(file.id) || !requested.add(file.id)) return
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

    /** Only invoked after a document is explicitly tapped by its user. */
    fun requestDocument(fileId: Int) {
        val active = client() ?: return
        synchronized(lock) {
            if (_paths.value.containsKey(fileId) || !manualRequests.add(fileId)) return
            _transfers.value = _transfers.value + (fileId to FileTransfer(downloading = true))
        }
        val request = TdApi.DownloadFile().apply {
            this.fileId = fileId
            priority = 16
            offset = 0L
            limit = 0L
            synchronous = false
        }
        active.send(request) { response ->
            when (response) {
                is TdApi.File -> accept(response)
                is TdApi.Error -> {
                    synchronized(lock) {
                        manualRequests.remove(fileId)
                        _transfers.value = _transfers.value + (
                            fileId to FileTransfer(error = "Download failed (code ${response.code}). Tap to retry.")
                        )
                    }
                }
            }
        }
    }

    /** TDLib might have the complete original file cached from another client session. */
    fun observeExisting(file: TdApi.File?) {
        if (file != null) accept(file)
    }

    fun onUpdate(update: TdApi.Object) {
        if (update is TdApi.UpdateFile) accept(update.file)
    }

    private fun accept(file: TdApi.File) {
        val local = file.local ?: return
        if (local.isDownloadingCompleted && local.path.isNotBlank()) {
            publish(file.id, local.path)
            synchronized(lock) {
                manualRequests.remove(file.id)
                _transfers.value = _transfers.value - file.id
            }
        } else {
            synchronized(lock) {
                if (file.id in manualRequests) {
                    _transfers.value = _transfers.value + (
                        file.id to FileTransfer(
                            downloading = true,
                            downloadedBytes = local.downloadedSize.toLong(),
                            totalBytes = maxOf(file.size, file.expectedSize).toLong()
                        )
                    )
                }
            }
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
            manualRequests.clear()
            _paths.value = emptyMap()
            _transfers.value = emptyMap()
        }
    }
}
