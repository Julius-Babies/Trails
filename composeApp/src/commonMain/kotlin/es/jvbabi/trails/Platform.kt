package es.jvbabi.trails

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform