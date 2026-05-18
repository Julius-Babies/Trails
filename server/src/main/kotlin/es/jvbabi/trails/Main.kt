package es.jvbabi.trails

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.types.path
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun main(args: Array<String>) {
    runBlocking {
        AppCommand().main(args)
    }
}


class AppCommand: SuspendingCliktCommand("server") {

    val staticWebPath by option("--static-web-path", help = "Path to serve static web content from")
        .path(mustExist = true, mustBeReadable = true)

    val storageDirectory by option("--storage-directory", help = "Path to store data in")
        .path(mustExist = true, mustBeWritable = true, mustBeReadable = true)
        .default(File("./data").toPath())

    override suspend fun run() {

        val applicationLaunchConfig = ApplicationLaunchConfig(
            staticWebPath = staticWebPath?.let { File(it.absolutePathString()) },
            storageDirectory = File(storageDirectory.absolutePathString()),
        )

        embeddedServer(
            factory = Netty,
            port = 8080,
            host = "0.0.0.0",
            module = { rootModule(applicationLaunchConfig) }
        ).start(wait = true)
    }
}

data class ApplicationLaunchConfig(
    val staticWebPath: File? = null,
    val storageDirectory: File = File("./data"),
)