package es.jvbabi.trails.data.remote

import es.jvbabi.trails.api.v1.entity.ActiveShare
import es.jvbabi.trails.api.v1.entity.Device
import es.jvbabi.trails.api.v1.entity.Share
import es.jvbabi.trails.api.v1.entity.User
import es.jvbabi.trails.api.v1.me.RegisterUserShareRequest
import es.jvbabi.trails.api.v1.me.UserShareResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.uuid.Uuid

/**
 * Client for the REST API. The public entity reads (`/api/v1/<entity>/{id}`) resolve, for
 * example after a redeem, the chain ActiveShare -> Share -> Device -> User. The
 * `/me/shares` routes back up redeemed shares to the signed-in user's account.
 */
class TrailsApi(
    private val httpClient: HttpClient,
) {
    suspend fun getDevice(host: String, id: Uuid): Device = get(host, "devices", id.toString())

    suspend fun getShare(host: String, id: Uuid): Share = get(host, "share", id.toString())

    suspend fun getActiveShare(host: String, id: Uuid): ActiveShare = get(host, "active-shares", id.toString())

    suspend fun getUser(host: String, id: Uuid): User = get(host, "users", id.toString())

    suspend fun getUserShares(host: String, token: String): List<UserShareResponse> =
        get(host, "me", "shares", token = token)

    suspend fun registerUserShare(host: String, token: String, request: RegisterUserShareRequest) {
        val response = httpClient.post(urlFor(host, "me", "shares", "register")) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.requireSuccess()
    }

    private suspend inline fun <reified T> get(host: String, vararg path: String, token: String? = null): T {
        val response = httpClient.get(urlFor(host, *path)) {
            if (token != null) bearerAuth(token)
        }
        response.requireSuccess()
        return response.body()
    }

    private fun urlFor(host: String, vararg path: String): String =
        URLBuilder("https://$host").apply {
            appendPathSegments("api", "v1", *path)
        }.buildString()

    private suspend fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) throw ApiException(status.value, bodyAsText())
    }
}

class ApiException(
    val statusCode: Int,
    val responseBody: String,
) : Exception("API request failed with status $statusCode: $responseBody")
