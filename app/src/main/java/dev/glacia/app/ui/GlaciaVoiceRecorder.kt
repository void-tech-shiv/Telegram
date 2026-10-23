package dev.glacia.app.ui

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.util.UUID

data class RecordedVoice(val path: String, val durationSeconds: Int, val opus: Boolean)

/**
 * User-controlled microphone recording, foreground only. Android 10+ records
 * real OGG/Opus Telegram voice notes; older Android uses AAC as a document.
 */
class GlaciaVoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L
    private val opus = Build.VERSION.SDK_INT >= 29

    fun start(): Boolean {
        if (recorder != null) return false
        val file = File(context.filesDir, "outgoing-media").apply { mkdirs() }
            .resolve("${UUID.randomUUID()}_voice.${if (opus) "ogg" else "m4a"}")
        return try {
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context)
                      else MediaRecorder()
            recorder = rec
            output = file
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(
                if (opus) MediaRecorder.OutputFormat.OGG
                else MediaRecorder.OutputFormat.MPEG_4
            )
            rec.setAudioEncoder(
                if (opus) MediaRecorder.AudioEncoder.OPUS
                else MediaRecorder.AudioEncoder.AAC
            )
            rec.setAudioEncodingBitRate(if (opus) 32_000 else 64_000)
            rec.setAudioSamplingRate(if (opus) 48_000 else 44_100)
            rec.setMaxDuration(60_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (_: Exception) {
            discard()
            false
        }
    }

    fun finish(): RecordedVoice? {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val active = recorder ?: return null
        recorder = null
        return try {
            if (elapsed < 950L) {
                runCatching { active.stop() }
                runCatching { active.release() }
                output?.delete()
                output = null
                null
            } else {
                active.stop()
                active.release()
                val file = output ?: return null
                output = null
                if (file.length() <= 0L) {
                    file.delete()
                    null
                } else RecordedVoice(file.absolutePath, (elapsed / 1000).toInt().coerceAtLeast(1), opus)
            }
        } catch (_: Exception) {
            runCatching { active.release() }
            output?.delete()
            output = null
            null
        }
    }

    fun discard() {
        val active = recorder
        recorder = null
        if (active != null) {
            runCatching { active.stop() }
            runCatching { active.release() }
        }
        output?.delete()
        output = null
    }
}
