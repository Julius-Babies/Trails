package es.jvbabi.trails.data

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class RemoteRepository(
    val baseUrl: Url,
) {
    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                isLenient = true
                ignoreUnknownKeys = true
            })
        }

        defaultRequest {
            url(baseUrl.toString())
            userAgent("Trails Federation Client")
        }
    }
}

class RemoteRepositoryStore {
    private val repositories = mutableMapOf<String, RemoteRepository>()

    /** [host] is a bare federation host (e.g. `example.com`); federation always
     * talks HTTPS to the other server. */
    fun get(host: String): RemoteRepository {
        return repositories.getOrPut(host) {
            RemoteRepository(URLBuilder(protocol = URLProtocol.HTTPS, host = host).build())
        }
    }
}