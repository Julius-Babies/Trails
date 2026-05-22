package es.jvbabi.trails.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image

actual fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
    return if (bytes != null && bytes.isNotEmpty()) {
        Image.makeFromEncoded(bytes).asImageBitmap()
    } else {
        null
    }
}
