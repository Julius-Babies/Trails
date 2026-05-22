package es.jvbabi.trails.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
    return if (bytes != null && bytes.isNotEmpty()) {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } else {
        null
    }
}
