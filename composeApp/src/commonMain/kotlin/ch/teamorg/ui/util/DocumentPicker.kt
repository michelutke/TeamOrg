package ch.teamorg.ui.util

import androidx.compose.runtime.Composable

/** Opens a document picker for the given extensions; returns bytes + filename, or null result when cancelled. */
@Composable
expect fun rememberDocumentPickerLauncher(extensions: List<String>, onResult: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit
