package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.DeviceRepository
import java.util.Locale

class AndroidDeviceRepository : DeviceRepository {
    override fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            "${manufacturer.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} ${model}"
        }
    }
}