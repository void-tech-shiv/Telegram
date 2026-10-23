package dev.glacia.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.glacia.app.data.DocumentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class PendingDocumentSave(val sourcePath: String)

/** Save through the system picker. No broad filesystem permission or FileProvider exposure. */
@Composable
fun rememberSaveDocument(): (String, String?, String?) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingDocumentSave?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val selected = pending
        pending = null
        val uri: Uri? = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null && selected != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    runCatching {
                        val original = checkedTdlibFile(context, selected.sourcePath)
                            ?: error("Source file is not in TDLib private storage")
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            original.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("Unable to create output file")
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (saved) "Saved to selected location" else "Couldn't save file",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    return { localPath, name, mime ->
        if (checkedTdlibFile(context, localPath) == null) {
            Toast.makeText(context, "File unavailable or not in private storage", Toast.LENGTH_SHORT).show()
        } else {
            val safeName = DocumentKind.safeName(name)
            val extension = safeName.substringAfterLast('.', "").lowercase()
            val detected = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            val safeMime = mime?.takeIf {
                it.matches(Regex("[a-zA-Z0-9.+-]+/[a-zA-Z0-9.+-]+"))
            } ?: detected ?: "application/octet-stream"
            pending = PendingDocumentSave(localPath)
            picker.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (safeMime == "application/octet-stream" && detected != null) detected else safeMime
                putExtra(Intent.EXTRA_TITLE, safeName)
            })
        }
    }
}

private fun checkedTdlibFile(context: Context, localPath: String): File? = runCatching {
    val candidate = File(localPath).canonicalFile
    val allowed = listOf(
        context.filesDir.canonicalFile,
        context.getDir("tdlib", Context.MODE_PRIVATE).canonicalFile
    )
    candidate.takeIf { file ->
        file.isFile && allowed.any {
            file.path.startsWith(it.path + File.separator)
        }
    }
}.getOrNull()
