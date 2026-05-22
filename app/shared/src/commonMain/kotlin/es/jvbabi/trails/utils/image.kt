package es.jvbabi.trails.utils

import androidx.compose.ui.graphics.ImageBitmap

expect fun rememberBitmapFromBytes(bytes: ByteArray?): ImageBitmap?