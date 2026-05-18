package es.jvbabi.trails

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.types.path
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlin.io.path.absolutePathString

fun main(args: Array<String>) {
    runBlocking {
        AppCommand().main(args)
    }
}


class AppCommand: SuspendingCliktCommand("server") {

    val staticWebPath by option("--static-web-path", help = "Path to serve static web content from")
        .path(mustExist = true, mustBeReadable = true)

    override suspend fun run() {
        embeddedServer(
            factory = Netty,
            port = 8080,
            host = "0.0.0.0",
            module = { rootModule(staticWebPath?.absolutePathString()) }
        ).start(wait = true)
    }
}