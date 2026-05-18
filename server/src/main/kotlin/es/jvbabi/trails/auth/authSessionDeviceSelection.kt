package es.jvbabi.trails.auth

import es.jvbabi.authentikt.core.session.Session
import es.jvbabi.authentikt.core.step.plugins.BasePlugin
import es.jvbabi.trails.data.DeviceInformationRepository
import es.jvbabi.trails.database.DatabaseManager
import es.jvbabi.trails.database.Device
import es.jvbabi.trails.database.DeviceType
import es.jvbabi.trails.database.Devices
import es.jvbabi.trails.database.User
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid

/**
 * If the user has authenticated, we need to determine which device they are using. If it's a new device, we need to
 * create it and add it to the user's devices. If it may be already existing, we need to ask the user to select it.
 * Otherwise, we create a new one. If the user has already selected a device, we just return null.
 *
 * @return The next authentication step we would want to go to to get more device information or just null if we're done.
 */
suspend fun authSessionDeviceSelection(session: Session<*>, user: User): BasePlugin<*>? {
    val db = GlobalContext.get().get<DatabaseManager>()
    val deviceSelectionAuthentiktPlugin = GlobalContext.get().get<DeviceSelectionAuthentiktPlugin>()
    val deviceInformationRepository = GlobalContext.get().get<DeviceInformationRepository>()

    val deviceModel = session.publicAttributes[deviceModelAttribute]
    val deviceManufacturer = session.publicAttributes[deviceManufacturerAttribute]
    val authSessionDeviceId = session.attributes[authSessionSelectedDeviceIdAttribute]

    if (authSessionDeviceId != null) return null

    val existingDevices = if (deviceManufacturer != null && deviceModel != null) db.transaction {
        Device
            .find { (Devices.owner eq user.id) and (Devices.model eq deviceModel) and (Devices.manufacturer eq deviceManufacturer) }
            .toList()
    } else emptyList()

    val deviceInformation = if (deviceManufacturer != null && deviceModel != null) {
        deviceInformationRepository.getDeviceInformation(deviceManufacturer, deviceModel)
    } else null

    val manufacturer = deviceInformation?.manufacturer ?: deviceManufacturer ?: "Unknown"
    val friendlyName = deviceInformation?.friendlyName ?: "Unknown"

    if (session.has(deviceSelectionAuthentiktPlugin)) {
        val userSelection = (session.authenticationSteps.last().second as DeviceSelectionAuthentiktState).selectedOption
        when (userSelection) {
            is DeviceSelectionAuthentiktState.UserSelection.Pending -> {
                return deviceSelectionAuthentiktPlugin
            }

            is DeviceSelectionAuthentiktState.UserSelection.NewDevice -> {
                val device = db.transaction {
                    Device.new {
                        this.owner = user
                        this.manufacturer = manufacturer
                        this.model = deviceInformation?.model ?: deviceModel ?: "Unknown"
                        this.friendlyName = friendlyName
                        this.displayName = userSelection.name
                        this.owner = user
                        this.type = DeviceType.Phone
                    }
                }
                session.attributes[authSessionSelectedDeviceIdAttribute] = device.id.value
                return null
            }

            is DeviceSelectionAuthentiktState.UserSelection.Selected -> {
                session.attributes[authSessionSelectedDeviceIdAttribute] = Uuid.parse(userSelection.device.deviceId)
                return null
            }
        }
    }

    if (existingDevices.isEmpty()) {
        val device = db.transaction {
            Device.new {
                this.owner = user
                this.manufacturer = manufacturer
                this.model = deviceInformation?.model ?: deviceModel ?: "Unknown"
                this.friendlyName = friendlyName
                this.displayName = "$manufacturer $friendlyName"
                this.owner = user
                this.type = DeviceType.Phone
            }
        }
        session.attributes[authSessionSelectedDeviceIdAttribute] = device.id.value

        return null
    }

    return deviceSelectionAuthentiktPlugin
}
