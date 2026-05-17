package es.jvbabi.trails.domain.repository

import kotlinx.coroutines.channels.Channel
import kotlinx.io.Sink

interface FileRepository {
    suspend fun writeFile(fileName: String, content: ByteArray)
    suspend fun getFileSink(fileName: String): Sink
    suspend fun readFile(fileName: String): ByteArray?
    suspend fun hasFile(fileName: String): Boolean
}