package es.jvbabi.trails.page.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import es.jvbabi.trails.page.home.HomeState
import platform.MapKit.MKMapView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.CoreLocation.CLLocationCoordinate2DMake

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
    )
}
