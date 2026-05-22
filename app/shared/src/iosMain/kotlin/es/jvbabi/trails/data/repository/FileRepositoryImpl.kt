package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.FileRepository
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask

class IosFileRepository : FileRepository {

    private fun documentsDir(): String {
        val urls = NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        return requireNotNull((urls.firstOrNull() as? NSURL)?.path)
    }

    private fun resolvePath(fileName: String): String {
        val dir = documentsDir()
        val fullPath = "$dir/$fileName"
        val parentDir = fullPath.substringBeforeLast("/", missingDelimiterValue = "")
        if (parentDir.isNotEmpty()) {
            SystemFileSystem.createDirectories(Path(parentDir), mustCreate = false)
        }
        return fullPath
    }

    override suspend fun writeFile(fileName: String, content: ByteArray) {
        val path = resolvePath(fileName)
        SystemFileSystem.sink(Path(path)).buffered().use { it.write(content) }
    }

    override suspend fun getFileSink(fileName: String): Sink {
        val path = resolvePath(fileName)
        return SystemFileSystem.sink(Path(path)).buffered()
    }

    override suspend fun readFile(fileName: String): ByteArray? {
        val path = resolvePath(fileName)
        if (!SystemFileSystem.exists(Path(path))) return null
        return SystemFileSystem.source(Path(path)).buffered().use { it.readByteArray() }
    }

    override suspend fun hasFile(fileName: String): Boolean {
        val path = resolvePath(fileName)
        return SystemFileSystem.exists(Path(path))
    }
}
