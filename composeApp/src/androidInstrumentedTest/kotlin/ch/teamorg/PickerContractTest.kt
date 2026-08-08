package ch.teamorg

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pickers in ImagePicker.android.kt and DocumentPicker.android.kt rely on
 * ActivityResultContracts, whose createIntent implementations branch on API level.
 * These assertions pin the Intent each contract produces on the device under test.
 *
 * Note: intentionally does NOT assert resolveActivity(...) != null — package
 * visibility filtering on API 30+ can null that out for reasons unrelated to the
 * API floor, which would make this test lie.
 */
@RunWith(AndroidJUnit4::class)
class PickerContractTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun getContent_producesGetContentIntentForImages() {
        val intent = ActivityResultContracts.GetContent().createIntent(context, "image/*")

        assertEquals(Intent.ACTION_GET_CONTENT, intent.action)
        assertEquals("image/*", intent.type)
        assertTrue(
            "GetContent must request an openable document",
            intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true
        )
    }

    @Test
    fun openDocument_producesOpenDocumentIntentWithSpreadsheetMimeTypes() {
        val mimeTypes = arrayOf(
            "text/csv",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        )

        val intent = ActivityResultContracts.OpenDocument().createIntent(context, mimeTypes)

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertEquals("*/*", intent.type)
        assertArrayEquals(mimeTypes, intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
    }

    @Test
    fun takePicturePreview_producesCameraStillImageIntent() {
        val intent = ActivityResultContracts.TakePicturePreview().createIntent(context, null)

        assertEquals("android.media.action.IMAGE_CAPTURE", intent.action)
    }
}
