@file:OptIn(ExperimentalForeignApi::class)

package es.jvbabi.trails.page.home.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import es.jvbabi.trails.page.home.HomeState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIView
import platform.darwin.NSObject

private class MapDelegate(
    private val onUserDragStart: () -> Unit,
) : NSObject(), MKMapViewDelegateProtocol {
    override fun mapView(mapView: MKMapView, regionWillChangeAnimated: Boolean) {
        // A region change is user-initiated when a gesture recognizer on the map's
        // content view has just begun.
        val contentView = mapView.subviews.firstOrNull() as? UIView ?: return
        val userInitiated = contentView.gestureRecognizers.orEmpty().any { recognizer ->
            (recognizer as? UIGestureRecognizer)?.state == UIGestureRecognizerStateBegan
        }
        if (userInitiated) onUserDragStart()
    }
}

@Composable
actual fun Map(
    state: HomeState,
    onDeviceClick: (HomeState.HomeDevice) -> Unit,
    onUserDragStart: () -> Unit,
) {
    // Held across recompositions since MKMapView.delegate is a weak reference.
    val delegate = remember { MapDelegate(onUserDragStart) }

    UIKitView(
        factory = {
            MKMapView().apply {
                setDelegate(delegate)
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
            view.setDelegate(null)
            view.removeFromSuperview()
        },
        properties = UIKitInteropProperties(
            isInteractive = true,
            isNativeAccessibilityEnabled = true
        )
    )
}
