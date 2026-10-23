package dev.glacia.app.data

object DocumentKind {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif")

    /** Telegram documents are files even when the filename ends in .jpg. */
    fun isImage(mime: String?, name: String?): Boolean {
        val media = mime.orEmpty().lowercase()
        if (media.startsWith("image/")) return true
        val ext = name.orEmpty().substringAfterLast('.', "").lowercase()
        return ext in imageExtensions && (media.isEmpty() || media == "application/octet-stream")
    }

    fun safeName(name: String?): String {
        val base = name.orEmpty().substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\p{Cntrl}]"), "_").take(120)
        return base.ifBlank { "Telegram document" }
    }
}
