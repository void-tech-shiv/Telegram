package dev.glacia.app.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * SAF media must be copied into app-private storage before TDLib can read it.
 * No broad storage permissions or external media servers.
 */
class MediaOutbox(
    private val context: Context,
    private val client: () -> Client?,
    private val messages: TdlibMessages
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    private val outbox by lazy {
        File(context.filesDir, "outgoing-media").apply { mkdirs() }
    }

    private fun send(chatId: Long, input: TdApi.InputMessageContent) {
        val active = client() ?: run {
            messages.reportError(chatId, "Not connected to Telegram.")
            _busy.value = false
            return
        }
        active.send(TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = input
        }) { result ->
            when (result) {
                is TdApi.Message -> messages.addOutgoing(result)
                is TdApi.Error -> messages.reportError(
                    chatId, "Media not sent (Telegram code ${result.code})."
                )
            }
            _busy.value = false
        }
    }

    /**
     * Images <= 10 MiB (TDLib photo limit) and other attachments <= 100 MiB.
     * Reject streams that lie about their reported size while copying.
     */
    fun sendPicked(chatId: Long, uri: Uri, caption: String = "") {
        if (_busy.value) return
        _busy.value = true
        executor.execute {
            var file: File? = null
            try {
                val type = context.contentResolver.getType(uri)?.lowercase().orEmpty()
                val kind = when {
                    type == "image/jpeg" || type == "image/png" || type == "image/webp" -> "photo"
                    type.startsWith("video/") -> "video"
                    else -> "document"
                }
                val maxSize = if (kind == "photo") 10L * 1024 * 1024
                    else 100L * 1024 * 1024
                val display = context.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val n = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val z = cursor.getColumnIndex(OpenableColumns.SIZE)
                        Pair(
                            if (n >= 0) cursor.getString(n) else null,
                            if (z >= 0 && !cursor.isNull(z)) cursor.getLong(z) else null
                        )
                    } else null
                }
                if (display?.second != null && display.second!! > maxSize) {
                    throw IllegalArgumentException("File exceeds the upload limit.")
                }
                val safeName = (display?.first ?: "attachment")
                    .substringAfterLast('/').substringAfterLast('\\')
                    .replace(Regex("[^a-zA-Z0-9_.-]"), "_")
                    .takeLast(75).ifBlank { "attachment" }
                file = File(outbox, "${UUID.randomUUID()}_$safeName")
                var transferred = 0L
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file!!.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            transferred += read
                            if (transferred > maxSize) {
                                throw IllegalArgumentException("File exceeds the upload limit.")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw IllegalStateException("Cannot open the selected file.")
                if (transferred == 0L) throw IllegalArgumentException("Selected file is empty.")
                val localFile = TdApi.InputFileLocal(file!!.absolutePath)
                val text = TdApi.FormattedText().apply { this.text = caption.take(900) }
                val message = when (kind) {
                    "photo" -> TdApi.InputMessagePhoto().apply {
                        photo = localFile
                        this.caption = text
                    }
                    "video" -> TdApi.InputMessageVideo().apply {
                        video = localFile
                        this.caption = text
                    }
                    else -> TdApi.InputMessageDocument().apply {
                        document = localFile
                        this.caption = text
                    }
                }
                send(chatId, message)
                // Keep source until TDLib finishes uploading. Do not delete here.
            } catch (e: Exception) {
                file?.delete()
                messages.reportError(chatId, "Attachment failed: ${e.message ?: "Cannot read selected file"}")
                _busy.value = false
            }
        }
    }

    fun sendRecorded(chatId: Long, path: String, durationSeconds: Int, isOpus: Boolean) {
        if (_busy.value) return
        val file = File(path)
        // The recorder created this file within our private outgoing directory.
        val canonical = runCatching { file.canonicalFile }.getOrNull()
        if (canonical == null || !canonical.path.startsWith(outbox.canonicalPath + File.separator) ||
            !canonical.isFile || canonical.length() <= 0L) {
            messages.reportError(chatId, "Recorded audio is unavailable.")
            return
        }
        _busy.value = true
        val localFile = TdApi.InputFileLocal(canonical.absolutePath)
        val content = if (isOpus) {
            TdApi.InputMessageVoiceNote().apply {
                voiceNote = localFile
                duration = durationSeconds.coerceAtLeast(1)
                waveform = byteArrayOf()
            }
        } else {
            // API 26-28 can record AAC/M4A but not native Opus voice notes.
            // Send as an ordinary audio file rather than mislabel as OGG/Opus.
            TdApi.InputMessageDocument().apply { document = localFile }
        }
        send(chatId, content)
    }
}
