package es.jvbabi.trails.page.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import es.jvbabi.trails.page.home.HomeState
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckColors
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import trails.composeapp.generated.resources.Res
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.maplibre.compose.location.Location as MapLibreLocation

expect fun getRenderOptions(): RenderOptions

val LocalCameraState = staticCompositionLocalOf<CameraState> {
    error("No CameraState provided")
}

val LocalMapSize = staticCompositionLocalOf {
    IntSize.Zero
}

@Composable
fun MapOverlay(
    cameraState: CameraState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it }
    ) {
        CompositionLocalProvider(
            LocalCameraState provides cameraState,
            LocalMapSize provides mapSize,
            content = content,
        )
    }
}

@Composable
fun MapContent(
    position: Position,
    anchorOffsetX: Float = 0.5f,
    anchorOffsetY: Float = 1.0f,
    content: @Composable () -> Unit,
) {
    val cameraState = LocalCameraState.current
    val mapSize = LocalMapSize.current
    val density = LocalDensity.current.density
    val projection = cameraState.projection
    val cameraPosition = cameraState.position

    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    if (mapSize == IntSize.Zero || projection == null) return

    val screenDpOffset = remember(position, cameraPosition, projection) {
        projection.screenLocationFromPosition(position)
    }

    val screenPxOffset = Offset(
        x = screenDpOffset.x.value * density,
        y = screenDpOffset.y.value * density,
    )

    val adjustedOffset = if (contentSize != IntSize.Zero) {
        IntOffset(
            x = (screenPxOffset.x - contentSize.width * anchorOffsetX).toInt(),
            y = (screenPxOffset.y - contentSize.height * anchorOffsetY).toInt(),
        )
    } else {
        IntOffset(screenPxOffset.x.toInt(), screenPxOffset.y.toInt())
    }

    Box(
        modifier = Modifier
            .offset { adjustedOffset }
            .onSizeChanged { contentSize = it }
    ) {
        content()
    }
}

@Composable
fun Map(
    state: HomeState,
    onDeviceClick: (HomeState.HomeDevice) -> Unit,
) {
    val locationFlow = remember { MutableStateFlow<MapLibreLocation?>(null) }
    val locationProvider = remember {
        object : LocationProvider {
            override val location = locationFlow
        }
    }

    LaunchedEffect(state.ownLocation) {
        state.ownLocation?.let {
            locationFlow.value = MapLibreLocation(
                position = Position(latitude = it.latitude, longitude = it.longitude),
                accuracy = 0.0,
                bearing = it.bearing.toDouble(),
                bearingAccuracy = 30.0,
                speed = null,
                speedAccuracy = null,
                timestamp = TimeSource.Monotonic.markNow()
            )
        }
    }

    var mapStyleJson by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        mapStyleJson = Res.readBytes("files/mapstyle.json").decodeToString()
    }

    val locationPuckState = rememberUserLocationState(locationProvider)
    val cameraState = rememberCameraState()

    var initialCameraMoved by remember { mutableStateOf(false) }

    LaunchedEffect(state.ownLocation) {
        if (!initialCameraMoved) {
            state.ownLocation?.let { loc ->
                cameraState.animateTo(
                    finalPosition = CameraPosition(
                        target = Position(latitude = loc.latitude, longitude = loc.longitude),
                        zoom = 13.0
                    ),
                    duration = 1.seconds
                )
                initialCameraMoved = true
            }
        }
    }

    val arrowShape = remember {
        GenericShape { size, _ ->
            moveTo(size.width / 2f, size.height)
            lineTo(0f, 0f)
            lineTo(size.width, 0f)
            close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (mapStyleJson != null) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                baseStyle = BaseStyle.Json(mapStyleJson!!),
                cameraState = cameraState,
                options = MapOptions(
                    renderOptions = getRenderOptions()
                )
            ) {
                val loc = locationPuckState.location
                if (loc != null) {
                    key(loc) {
                        if (loc.bearing != null) {
                            val bearing = loc.bearing!!

                            val fovImageVector = remember(loc) {
                                val cx = 30f
                                val cy = 30f
                                ImageVector.Builder(
                                    defaultWidth = 60.dp,
                                    defaultHeight = 60.dp,
                                    viewportWidth = 60f,
                                    viewportHeight = 60f,
                                    autoMirror = false,
                                ).apply {
                                    path(
                                        fill = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF4A90D9).copy(alpha = 1f),
                                                Color(0xFF4A90D9).copy(alpha = 0f),
                                            ),
                                            start = Offset(cx, cy),
                                            end = Offset(cx, 0f),
                                        ),
                                    ) {
                                        moveTo(cx, cy)
                                        lineTo(0f, 0f)
                                        lineTo(60f, 0f)
                                        close()
                                    }
                                }.build()
                            }

                            val fovPainter = rememberVectorPainter(fovImageVector)

                            val fovFeatures = remember(loc) {
                                FeatureCollection(
                                    Feature<Point, JsonObject>(
                                        geometry = Point(loc.position),
                                        properties = buildJsonObject { },
                                    )
                                )
                            }
                            val fovSource = rememberGeoJsonSource(GeoJsonData.Features(fovFeatures))

                            SymbolLayer(
                                id = "fov-cone",
                                source = fovSource,
                                iconImage = image(fovPainter),
                                iconAnchor = const(SymbolAnchor.Center),
                                iconRotate = const(bearing.toFloat()),
                                iconRotationAlignment = const(IconRotationAlignment.Map),
                                iconAllowOverlap = const(true),
                            )
                        }

                        LocationPuck(
                            idPrefix = "home-puck",
                            locationState = locationPuckState,
                            cameraState = cameraState,
                            showBearing = false,
                            showBearingAccuracy = false,
                            colors = LocationPuckColors(
                                dotFillColorCurrentLocation = Color(0xFF4A90D9),
                                dotStrokeColor = Color.White,
                            ),
                        )
                    }
                }
            }
        }

        MapOverlay(cameraState = cameraState) {
            state.devices.forEach { device ->
                key(device.device.id) {
                    MapContent(
                        position = Position(
                            latitude = device.snapshot.location.latitude,
                            longitude = device.snapshot.location.longitude,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(32.dp)
                                .clickable {
                                    Logger.i { "Map device clicked: ${device.device.displayName}" }
                                    onDeviceClick(device)
                                },
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )

                            val bitmap = rememberBitmapFromBytes(device.image)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(8.dp, 6.dp)
                                    .offset(y = 22.dp)
                                    .clip(arrowShape)
                                    .background(Color.White)
                            )
                        }
                    }
                }
            }
        }
    }
}
