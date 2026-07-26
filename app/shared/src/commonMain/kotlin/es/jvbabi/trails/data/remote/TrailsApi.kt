package es.jvbabi.trails.data.remote

import es.jvbabi.trails.api.v1.entity.ActiveShare
import es.jvbabi.trails.api.v1.entity.Device
import es.jvbabi.trails.api.v1.entity.Share
import es.jvbabi.trails.api.v1.entity.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid

/**
 * Schlanker Client für die generische Entity-REST-API (`/api/v1/<entity>/{id}`).
 * Löst z.B. nach dem Redeem die Kette ActiveShare -> Share -> Device -> User auf,
 * damit die Objekte lokal gespeichert werden können.
 */
class TrailsApi(
    private val httpClient: HttpClient,
) {
    suspend fun getDevice(host: String, id: Uuid): Device = get(host, "devices", id.toString())

    suspend fun getShare(host: String, id: Uuid): Share = get(host, "share", id.toString())

    suspend fun getActiveShare(host: String, id: Uuid): ActiveShare = get(host, "active-shares", id.toString())

    suspend fun getUser(host: String, id: Uuid): User = get(host, "users", id.toString())

    private suspend inline fun <reified T> get(host: String, vararg path: String): T {
        val url = URLBuilder("https://$host").apply {
            appendPathSegments("api", "v1", *path)
        }.buildString()

        val response = httpClient.get(url)
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, response.bodyAsText())
        }
        return response.body()
    }
}

class ApiException(
    val statusCode: Int,
    val responseBody: String,
) : Exception("API request failed with status $statusCode: $responseBody")
