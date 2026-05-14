package es.jvbabi.trails.config

import io.ktor.http.URLBuilder

class ApplicationConfig(
    baseUrl: String = "https://trails.werkbank.space"
) {
    val url = URLBuilder(baseUrl)
}