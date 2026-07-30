package es.jvbabi.trails.domain.repository

interface TrailsAppRepository {
    suspend fun getLatestVersion(): String?
    suspend fun getDownloadLinkForLatestVersion(): String?
    fun getCurrentVersion(): String
}