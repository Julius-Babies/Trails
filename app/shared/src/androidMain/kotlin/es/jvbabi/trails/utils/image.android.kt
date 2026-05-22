package es.jvbabi.trails.utils

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap? {
    return if (bytes != null && bytes.isNotEmpty()) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } else {
        null
    }
}