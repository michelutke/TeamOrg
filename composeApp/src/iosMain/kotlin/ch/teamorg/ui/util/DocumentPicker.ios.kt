package ch.teamorg.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberDocumentPickerLauncher(extensions: List<String>, onResult: (bytes: ByteArray, fileName: String) -> Unit): () -> Unit {
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                controller.dismissViewControllerAnimated(true, null)
                val didStartAccessing = url.startAccessingSecurityScopedResource()
                try {
                    val data = NSData.dataWithContentsOfURL(url) ?: return
                    val bytes = ByteArray(data.length.toInt())
                    bytes.usePinned { pinned ->
                        platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
                    }
                    val fileName = url.lastPathComponent ?: "file"
                    onResult(bytes, fileName)
                } finally {
                    if (didStartAccessing) url.stopAccessingSecurityScopedResource()
                }
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                controller.dismissViewControllerAnimated(true, null)
            }
        }
    }

    return {
        val types = utTypesFor(extensions)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            picker, animated = true, completion = null
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun utTypesFor(extensions: List<String>): List<UTType> {
    val types = mutableListOf<UTType>()
    extensions.forEach { ext ->
        when (ext.lowercase()) {
            "xlsx" -> UTType.typeWithIdentifier("org.openxmlformats.spreadsheetml.sheet")?.let { types.add(it) }
            "csv" -> UTType.typeWithIdentifier("public.comma-separated-values-text")?.let { types.add(it) }
        }
    }
    return types.ifEmpty { listOfNotNull(UTType.typeWithIdentifier("public.data")) }
}
