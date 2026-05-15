package es.jvbabi.trails.domain.repository

interface DeviceRepository {
    fun getDeviceModel(): String
    fun getManufacturer(): String
}