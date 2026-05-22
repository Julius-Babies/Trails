@file:OptIn(ExperimentalForeignApi::class)

package es.jvbabi.trails.page.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import es.jvbabi.trails.page.home.HomeState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView

@Composable
actual fun Map(
    state: HomeState,
    onDeviceClick: (HomeState.HomeDevice) -> Unit,
) {
    UIKitView(
        factory = {
            MKMapView().apply {
                state.ownLocation?.let { loc ->
                    val center = CLLocationCoordinate2DMake(loc.latitude, loc.longitude)
                    val region = MKCoordinateRegionMakeWithDistance(center, 1000.0, 1000.0)
                    setRegion(region, animated = false)
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            state.ownLocation?.let { loc ->
                val center = CLLocationCoordinate2DMake(loc.latitude, loc.longitude)
                val region = MKCoordinateRegionMakeWithDistance(center, 1000.0, 1000.0)
                view.setRegion(region, animated = true)
            }
        },
        onRelease = { view ->
            view.removeFromSuperview()
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true
        )
    )
}
