package es.jvbabi.trails.page.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import es.jvbabi.trails.page.home.HomeState
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapOptions
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.ComposeMapInitOptions
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.annotation.generated.PolygonAnnotation
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DeviceMarker(
    imageBytes: ByteArray?,
    onClick: () -> Unit,
) {
    val arrowShape = remember {
        GenericShape { size, _ ->
            moveTo(size.width / 2f, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(64.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // No ripple on the outer Box
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                // Ripple is drawn here, clipped to CircleShape
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(),
                )
        )

        val bitmap = rememberBitmapFromBytes(imageBytes)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(8.dp, 8.dp)
                .clip(arrowShape)
                .background(Color.White)
        )
    }
}

@Preview
@Composable
fun DeviceMarkerPreview() {
    DeviceMarker(
        imageBytes = null,
        onClick = {},
    )
}

@Composable
actual fun Map(
    state: HomeState,
    onDeviceClick: (HomeState.HomeDevice) -> Unit,
) {
    val mapViewportState = rememberMapViewportState { }

    var hasAutoFitted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        mapViewportState.flyTo(
            cameraOptions = cameraOptions {
                center(Point.fromLngLat(10.4515, 51.1657))
                zoom(6.0)
            },
            MapAnimationOptions.mapAnimationOptions { duration(0) }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MapboxMap(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = {
                MapboxStandardStyle()
            },
            composeMapInitOptions = ComposeMapInitOptions(
                mapOptions = MapOptions.Builder().build(),
                textureView = true,
            ),
        ) {
            MapEffect(Unit) { mapView ->
                mapView.location.updateSettings {
                    locationPuck = createDefault2DPuck(withBearing = true)
                    puckBearingEnabled = true
                    puckBearing = PuckBearing.HEADING
                    enabled = true
                }
            }

            MapEffect(state.devices, state.ownLocation) { mapView ->
                if (hasAutoFitted) return@MapEffect

                val points = mutableListOf<Point>()
                state.ownLocation?.let { loc ->
                    points.add(Point.fromLngLat(loc.longitude, loc.latitude))
                }
                state.devices.forEach { device ->
                    val loc = device.snapshot.location
                    points.add(Point.fromLngLat(loc.longitude, loc.latitude))
                }

                if (points.isEmpty()) return@MapEffect

                val cameraOptions = mapView.mapboxMap.cameraForCoordinates(
                    coordinates = points,
                    coordinatesPadding = EdgeInsets(300.0, 300.0, 300.0, 300.0),
                )
                mapViewportState.flyTo(
                    cameraOptions = cameraOptions,
                    MapAnimationOptions.mapAnimationOptions { duration(1500) }
                )
                hasAutoFitted = true
            }

            val loc = state.ownLocation

            if (loc != null) {
                val bearing = loc.bearing.toDouble()
                val conePoints = remember(loc, bearing) {
                    val spreadRad = 20.0 * PI / 180.0
                    val distance = 0.0025
                    val bearingRad = bearing * PI / 180.0
                    val leftBearing = bearingRad - spreadRad
                    val rightBearing = bearingRad + spreadRad

                    val origin = Point.fromLngLat(loc.longitude, loc.latitude)
                    val left = Point.fromLngLat(
                        loc.longitude + distance * sin(leftBearing),
                        loc.latitude + distance * cos(leftBearing)
                    )
                    val right = Point.fromLngLat(
                        loc.longitude + distance * sin(rightBearing),
                        loc.latitude + distance * cos(rightBearing)
                    )

                    listOf(origin, left, right)
                }

                PolygonAnnotation(points = listOf(conePoints)) {
                    fillColor = Color(0xFF4A90D9).copy(alpha = 0.25f)
                }
            }

            state.devices.forEach { device ->
                val position = device.snapshot.location

                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(Point.fromLngLat(position.longitude, position.latitude))
                        allowOverlap(true)
                        annotationAnchor {
                            anchor(ViewAnnotationAnchor.BOTTOM)
                        }
                    }
                ) {
                    DeviceMarker(
                        imageBytes = device.image,
                        onClick = {
                            Logger.i { "Map device clicked: ${device.device.displayName}" }
                            onDeviceClick(device)
                        },
                    )
                }
            }
        }
    }
}
