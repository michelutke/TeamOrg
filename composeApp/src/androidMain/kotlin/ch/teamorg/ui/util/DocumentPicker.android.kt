package ch.teamorg.ui.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberDocumentPickerLauncher(extensions: List<String>, onResult: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val mimeTypes = mimeTypesFor(extensions)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        val fileName = resolveFileName(context, uri)
        onResult(bytes, fileName)
    }
    return { launcher.launch(mimeTypes) }
}

private fun mimeTypesFor(extensions: List<String>): Array<String> {
    val mimes = mutableSetOf<String>()
    extensions.forEach { ext ->
        when (ext.lowercase()) {
            "csv" -> mimes.addAll(listOf("text/csv", "text/comma-separated-values", "text/plain"))
            "xlsx" -> mimes.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        }
    }
    return if (mimes.isEmpty()) arrayOf("*/*") else mimes.toTypedArray()
}

private fun resolveFileName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) return cursor.getString(nameIndex)
    }
    return uri.lastPathSegment ?: "file"
}
