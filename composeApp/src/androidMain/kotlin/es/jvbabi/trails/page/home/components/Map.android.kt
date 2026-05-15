package es.jvbabi.trails.page.home.components

import org.maplibre.compose.map.RenderOptions

// Required to allow blurring the map behind the card sheet, as TextureView is the only one that supports it
actual fun getRenderOptions(): RenderOptions {
    return RenderOptions(
        renderMode = RenderOptions.RenderMode.TextureView
    )
}