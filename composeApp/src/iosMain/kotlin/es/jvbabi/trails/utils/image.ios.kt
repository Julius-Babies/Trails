package es.jvbabi.trails.utils

import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.skia.Image
import androidx.compose.ui.graphics.asComposeImageBitmap

actual fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
    return if (bytes != null && bytes.isNotEmpty()) {
        Image.makeFromEncoded(bytes).asComposeImageBitmap()
    } else {
        null
    }
}