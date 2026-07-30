package es.jvbabi.trails.data.repository

import es.jvbabi.trails.BuildKonfig
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class TrailsAppRepositoryImpl(
    private val httpClient: HttpClient,
    private val deviceRepository: DeviceRepository,
): TrailsAppRepository {
    override suspend fun getLatestVersion(): String? {
        val response = httpClient.get("https://api.github.com/repos/Julius-Babies/Trails/releases/latest")
        if (!response.status.isSuccess()) return null
        val body = response.body<GitHubLatestRelease>()
        return body.tag.removePrefix("v")
    }

    /**
     * Picks the APK matching the device's architecture, preferring the device's own ABI order
     * (`Build.SUPPORTED_ABIS` lists the native one first). Falls back to the universal APK when
     * the release ships no split for any supported ABI, and to `null` on platforms that don't
     * install APKs at all.
     */
    override suspend fun getDownloadLinkForLatestVersion(): String? {
        val supportedAbis = deviceRepository.getSupportedAbis()
        if (supportedAbis.isEmpty()) return null

        val response = httpClient.get("https://api.github.com/repos/Julius-Babies/Trails/releases/latest")
        if (!response.status.isSuccess()) return null
        val assets = response.body<GitHubLatestRelease>().assets.filter { it.name.endsWith(".apk") }

        return (supportedAbis + UNIVERSAL_ABI)
            // Assets are named Trails.<version>.android-<abi>-release.apk. Matching the whole
            // segment keeps x86 from also matching the x86_64 build.
            .firstNotNullOfOrNull { abi -> assets.firstOrNull { it.name.contains("android-$abi-release") } }
            ?.downloadUrl
    }

    override fun getCurrentVersion(): String {
        return BuildKonfig.CURRENT_VERSION
    }
}

/** Name of the ABI-independent APK, usable on every device. */
private const val UNIVERSAL_ABI = "universal"

@Serializable
private data class GitHubLatestRelease(
    @SerialName("tag_name") val tag: String,
    @SerialName("assets") val assets: List<Asset>
) {
    @Serializable
    data class Asset(
        @SerialName("name") val name: String,
        @SerialName("browser_download_url") val downloadUrl: String
    )
}