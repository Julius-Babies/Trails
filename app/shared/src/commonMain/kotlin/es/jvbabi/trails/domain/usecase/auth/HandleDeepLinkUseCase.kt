package es.jvbabi.trails.domain.usecase.auth

import io.ktor.http.Url

class HandleDeepLinkUseCase(
    private val loginUseCase: LoginUseCase,
) {
    suspend operator fun invoke(url: Url) {
        if (url.protocol.name != "trailsapp") return
        if (url.host != "application") return

        if (url.segments.size < 2) return

        val trailsHost = url.segments[0]
        val action = url.segments[1]

        when (action) {
            "auth" -> {
                val authAction = url.segments[2]
                when (authAction) {
                    "redirect" -> {
                        val token = url.parameters["token"] ?: return
                        loginUseCase(token, trailsHost)
                    }
                }
            }
        }
    }
}