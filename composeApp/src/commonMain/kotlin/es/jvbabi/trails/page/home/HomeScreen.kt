package es.jvbabi.trails.page.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.compose.camera.CameraPosition
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
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import trails.composeapp.generated.resources.Res
import trails.composeapp.generated.resources.settings
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.maplibre.compose.location.Location as MapLibreLocation

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {}
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { contentPadding ->
        Box(Modifier.fillMaxSize()) {

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

            if (mapStyleJson != null) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = BaseStyle.Json(mapStyleJson!!),
                    cameraState = cameraState,
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

            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopEnd)
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.settings),
                            contentDescription = "Settings"
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (state.isConnectedToServer) Color.Green else Color.Red)
                    )

                    Text(
                        text = if (state.isConnectedToServer) "Verbunden mit Trails Server" else "Nicht verbunden mit Trails Server",
                    )
                }
            }
        }
    }
}
