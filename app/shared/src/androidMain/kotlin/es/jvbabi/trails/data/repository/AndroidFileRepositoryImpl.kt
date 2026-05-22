package es.jvbabi.trails.data.repository

import android.content.Context
import es.jvbabi.trails.domain.repository.FileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.io.FileOutputStream

class AndroidFileRepositoryImpl(
    private val context: Context,
): FileRepository {

    override suspend fun writeFile(fileName: String, content: ByteArray) {
        val targetDir = context.getExternalFilesDir(null)!!
        val targetFile = targetDir.resolve(fileName)
        targetFile.parentFile!!.mkdirs()
        targetFile.writeBytes(content)
    }

    override suspend fun getFileSink(fileName: String): Sink {
        val targetDir = context.getExternalFilesDir(null)!!
        val targetFile = targetDir.resolve(fileName)
        targetFile.parentFile!!.mkdirs()
        return withContext(Dispatchers.IO) {
            FileOutputStream(targetFile).asSink()
        }.buffered()
    }

    override suspend fun readFile(fileName: String): ByteArray? {
        val targetDir = context.getExternalFilesDir(null)!!
        val targetFile = targetDir.resolve(fileName)
        return if (targetFile.exists()) {
            targetFile.readBytes()
        } else {
            null
        }
    }

    override suspend fun hasFile(fileName: String): Boolean {
        val targetDir = context.getExternalFilesDir(null)!!
        val targetFile = targetDir.resolve(fileName)
        return targetFile.exists()
    }
}