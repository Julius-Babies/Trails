package es.jvbabi.trails

import es.jvbabi.trails.api.installAuthentication
import es.jvbabi.trails.api.installCallLogging
import es.jvbabi.trails.api.installContentNegotiation
import es.jvbabi.trails.api.installWebsocket
import es.jvbabi.trails.auth.installAuthentikt
import es.jvbabi.trails.di.installKoin
import es.jvbabi.trails.routes.installRouting
import io.ktor.server.application.*
import org.slf4j.LoggerFactory

fun Application.rootModule(
    applicationLaunchConfig: ApplicationLaunchConfig,
) {
    val logger = LoggerFactory.getLogger("ApplicationInit")
    logger.info("Starting application")
    logger.info("Storage at ${applicationLaunchConfig.storageDirectory}")
    installKoin(applicationLaunchConfig)
    installWebsocket()
    installCallLogging()
    installContentNegotiation()
    installAuthentication()
    installAuthentikt()
    installRouting()
}
