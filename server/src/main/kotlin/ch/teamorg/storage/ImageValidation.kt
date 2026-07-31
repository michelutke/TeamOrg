package ch.teamorg.storage

/**
 * Image validation for user uploads.
 *
 * A declared `Content-Type` is attacker-controlled, so it decides nothing. The file's
 * leading bytes decide both whether the upload is accepted and which extension it is
 * stored under — that is what stops an HTML or SVG payload from being written as
 * `something.png` and later served back as active content.
 */
enum class ImageKind(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
}

object ImageValidation {
    /** Largest upload accepted for avatars and club logos. */
    const val MAX_BYTES: Int = 2 * 1024 * 1024

    /**
     * Returns the image kind implied by the file's magic bytes, or null when the bytes are
     * not one of the accepted raster formats.
     */
    fun detect(bytes: ByteArray): ImageKind? = when {
        bytes.startsWith(0xFF, 0xD8, 0xFF) -> ImageKind.JPEG
        bytes.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> ImageKind.PNG
        bytes.isWebp() -> ImageKind.WEBP
        else -> null
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        return prefix.withIndex().all { (index, expected) -> this[index] == expected.toByte() }
    }

    /** RIFF container with a WEBP fourcc: "RIFF" ???? "WEBP". */
    private fun ByteArray.isWebp(): Boolean {
        if (size < 12) return false
        val riff = String(this, 0, 4, Charsets.US_ASCII)
        val webp = String(this, 8, 4, Charsets.US_ASCII)
        return riff == "RIFF" && webp == "WEBP"
    }
}
