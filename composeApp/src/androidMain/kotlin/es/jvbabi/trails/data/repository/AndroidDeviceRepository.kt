package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.DeviceRepository

class AndroidDeviceRepository : DeviceRepository {
    override fun getDeviceModel(): String {
        val model = android.os.Build.MODEL
        if (model == "sdk_gphone64_arm64") return "bluejay" // TODO: remove for prod, just a test to make it behave like a real device
        return model
    }

    override fun getManufacturer(): String {
        return android.os.Build.MANUFACTURER
    }
}
