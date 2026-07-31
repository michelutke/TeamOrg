package ch.teamorg.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberImagePickerLauncher(onResult: (bytes: ByteArray, ext: String) -> Unit): () -> Unit {
    val delegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>
            ) {
                picker.dismissViewControllerAnimated(true, null)
                val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                    ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage])
                    as? platform.UIKit.UIImage ?: return
                val data: NSData = UIImageJPEGRepresentation(image, 0.85) ?: return
                val bytes = ByteArray(data.length.toInt())
                bytes.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
                onResult(bytes, "jpg")
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
            }
        }
    }

    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            picker, animated = true, completion = null
        )
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCameraCaptureLauncher(onResult: (bytes: ByteArray, ext: String) -> Unit): (() -> Unit)? {
    val delegate = remember {
        object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>
            ) {
                picker.dismissViewControllerAnimated(true, null)
                val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                    ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage])
                    as? platform.UIKit.UIImage ?: return
                val data: NSData = UIImageJPEGRepresentation(image, 0.85) ?: return
                val bytes = ByteArray(data.length.toInt())
                bytes.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
                }
                onResult(bytes, "jpg")
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
            }
        }
    }

    if (!UIImagePickerController.isSourceTypeAvailable(
            UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        )
    ) return null

    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = delegate
        UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
            picker, animated = true, completion = null
        )
    }
}
