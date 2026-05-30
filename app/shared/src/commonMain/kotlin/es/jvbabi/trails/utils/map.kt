package es.jvbabi.trails.utils

import es.jvbabi.trails.page.home.HomeState
import kotlin.math.*

private const val TILE_SIZE = 512.0
private const val MAX_MERCATOR_LATITUDE = 85.05112878

data class IntPaddingValues(
    val top: Int,
    val bottom: Int,
    val start: Int,
    val end: Int,
)

/**
 * Derives a [MapCamera] that fits all coordinates within the visible viewport,
 * respecting the given [padding] (in dp) on each edge.
 *
 * @param viewportWidthPx  Full viewport width in pixels.
 * @param viewportHeightPx Full viewport height in pixels.
 * @param density          Screen density (dp → px multiplier).
 * @param padding          Insets in dp (top / start / end / bottom).
 * @param pitch            Camera tilt in degrees (default 0).
 * @param bearing          Camera rotation in degrees (default 0).
 * @param defaultZoom      Zoom used when only a single coordinate is present.
 * @param minZoom          Lower zoom clamp.
 * @param maxZoom          Upper zoom clamp.
 */
fun HomeState.FitBounds.toMapCamera(
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    density: Float,
    padding: IntPaddingValues,
    pitch: Double = 0.0,
    bearing: Double = 0.0,
    defaultZoom: Double = 15.0,
    minZoom: Double = 0.0,
    maxZoom: Double = 22.0,
): HomeState.MapCamera? {
    if (coordinates.isEmpty()) return null

    val lats = coordinates.map { it.first.coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE) }
    val lngs = coordinates.map { it.second }

    val minLat = lats.min()
    val maxLat = lats.max()
    val minLng = lngs.min()
    val maxLng = lngs.max()

    // Convert dp padding to pixels
    val paddingTopPx    = padding.top    * density
    val paddingBottomPx = padding.bottom * density
    val paddingStartPx  = padding.start  * density
    val paddingEndPx    = padding.end    * density

    val effectiveWidthPx  = (viewportWidthPx  - paddingStartPx - paddingEndPx).coerceAtLeast(1f)
    val effectiveHeightPx = (viewportHeightPx - paddingTopPx   - paddingBottomPx).coerceAtLeast(1f)

    // Zoom: fit the tightest axis; fall back to defaultZoom for a single point
    val zoom = if (coordinates.size == 1) {
        defaultZoom
    } else {
        val latZoom = latitudeZoom(minLat, maxLat, effectiveHeightPx)
        val lngZoom = longitudeZoom(minLng, maxLng, effectiveWidthPx)
        min(latZoom, lngZoom).coerceIn(minZoom, maxZoom)
    }

    // Geographic center of the raw bounds
    val boundsLat = (minLat + maxLat) / 2.0
    val boundsLng = (minLng + maxLng) / 2.0

    // Shift the camera center so the bounds appear centered within the
    // padded region rather than the full viewport.
    //
    // Positive paddingOffsetX → effective area is shifted right on screen
    //   → camera must move west to re-center bounds inside the padded area
    // Positive paddingOffsetY → effective area is shifted down on screen
    //   → camera must move north (Mercator Y increases northward)
    val paddingOffsetXPx = (paddingStartPx - paddingEndPx) / 2f
    val paddingOffsetYPx = (paddingTopPx   - paddingBottomPx) / 2f

    val centerLng = boundsLng - paddingOffsetXPx * lngDegreesPerPixel(zoom)
    val centerLat = mercatorToLat(
        latToMercator(boundsLat) + paddingOffsetYPx * mercatorUnitsPerPixel(zoom)
    ).coerceIn(-MAX_MERCATOR_LATITUDE, MAX_MERCATOR_LATITUDE)

    return HomeState.MapCamera(
        centerLatitude = centerLat,
        centerLongitude = centerLng,
        zoom = zoom,
        pitch = pitch,
        bearing = bearing,
    )
}

private fun latToMercator(lat: Double): Double =
    ln(tan(PI / 4.0 + (lat * (PI / 180.0)) / 2.0))

private fun mercatorToLat(mercatorY: Double): Double =
    (2.0 * atan(exp(mercatorY)) - PI / 2.0) * (180.0 / PI)

/**
 * Returns the zoom level at which [minLat]..[maxLat] fits in [heightPx].
 * Uses the Mercator projection so that higher latitudes are handled correctly.
 */
private fun latitudeZoom(minLat: Double, maxLat: Double, heightPx: Float): Double {
    val mercatorSpan = latToMercator(maxLat) - latToMercator(minLat)
    return if (mercatorSpan <= 0.0) 0.0
    else log2(heightPx * 2.0 * PI / (TILE_SIZE * mercatorSpan))
}

/**
 * Returns the zoom level at which [minLng]..[maxLng] fits in [widthPx].
 * Handles the antimeridian case (span wrapping 360°).
 */
private fun longitudeZoom(minLng: Double, maxLng: Double, widthPx: Float): Double {
    val lngSpan = (maxLng - minLng).let { if (it <= 0.0) it + 360.0 else it }
    return if (lngSpan <= 0.0) 0.0
    else log2(widthPx * 360.0 / (TILE_SIZE * lngSpan))
}

/** Longitude degrees covered by one pixel at the given zoom level. */
private fun lngDegreesPerPixel(zoom: Double): Double =
    360.0 / (TILE_SIZE * 2.0.pow(zoom))

/** Mercator radians covered by one pixel at the given zoom level. */
private fun mercatorUnitsPerPixel(zoom: Double): Double =
    (2.0 * PI) / (TILE_SIZE * 2.0.pow(zoom))