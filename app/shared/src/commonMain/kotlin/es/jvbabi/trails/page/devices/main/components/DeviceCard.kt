package es.jvbabi.trails.page.devices.main.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import es.jvbabi.trails.domain.model.Device
import es.jvbabi.trails.domain.model.Snapshot
import es.jvbabi.trails.domain.model.User
import es.jvbabi.trails.domain.repository.BatteryState
import es.jvbabi.trails.domain.repository.Location
import es.jvbabi.trails.page.home.HomeState
import es.jvbabi.trails.ui.components.BatteryIcon
import es.jvbabi.trails.ui.components.BatteryOrientation
import es.jvbabi.trails.utils.rememberBitmapFromBytes
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import nl.jacobras.humanreadable.HumanReadable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

@Composable
fun DeviceCard(
    modifier: Modifier = Modifier,
    device: HomeState.HomeDevice,
    isThisDevice: Boolean,
    colors: DeviceCardDefaults.DeviceCardColors = DeviceCardDefaults.colors(),
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
        ) {
            var rememberedImage by remember { mutableStateOf(device.image) }

            LaunchedEffect(device.image) {
                if (device.image != null) {
                    rememberedImage = device.image
                }
            }

            AnimatedContent(
                targetState = device.image != null,
            ) { hasImage ->
                val bitmap = rememberBitmapFromBytes(rememberedImage)
                if (!hasImage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
            }
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = device.device.displayName + " von " + device.device.owner.username,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = device.device.friendlyName + " (" + device.device.model + ")",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = buildString {
                    if (isThisDevice) {
                        append("Dieses Gerät")
                        return@buildString
                    }

                    if (device.snapshot == null) {
                        append("Noch nie gesehen")
                        return@buildString
                    }
                    val instant = device.snapshot.time.toInstant(TimeZone.currentSystemDefault())
                    if (Clock.System.now().minus(instant) <= 1.minutes) {
                        append("Eben gerade gesehen")
                        return@buildString
                    }

                    append("Zuletzt ")
                    append(HumanReadable.timeAgo(instant))
                    append(" gesehen")
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (device.snapshot?.batteryState != null) BatteryIcon(
            percentage = device.snapshot.batteryState.percentage,
            isCharging = device.snapshot.batteryState.isCharging,
            orientation = BatteryOrientation.Right,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .height(24.dp)
                .width(12.dp),
        )
    }
}

data object DeviceCardDefaults {
    data class DeviceCardColors(
        val background: Color,
    )

    @Composable
    fun colors(
        background: Color = MaterialTheme.colorScheme.surfaceVariant,
    ) = DeviceCardColors(
        background = background
    )
}


@Composable
@Preview
fun DeviceCardPreview() {
    val device = Device(
        id = Uuid.random(),
        manufacturer = "Google",
        model = "panther",
        friendlyName = "Pixel 7",
        displayName = "Google Pixel 7",
        owner = User(
            id = Uuid.random(),
            homeserver = "trailsdevelopment.jvbabi.es",
            username = "test.user"
        ),
        batteryState = Device.BatteryState.Shared(73, true),
    )
    DeviceCard(
        device = HomeState.HomeDevice(
            device = device,
            image = null,
            snapshot = Snapshot(
                device = device,
                time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()),
                batteryState = BatteryState(73, true),
                location = Location(
                    latitude = 40.4168,
                    longitude = -3.7038,
                    bearing = 7f,
                    bearingAccuracy = null,
                    locationAccuracy = 4f,
                    time = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                )
            )
        ),
        onClick = {},
        isThisDevice = true,
    )
}