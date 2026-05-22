@file:OptIn(ExperimentalForeignApi::class)

package es.jvbabi.trails.di

import androidx.room.Room
import androidx.room.RoomDatabase
import es.jvbabi.trails.data.database.TrailsDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual fun getDatabaseBuilder(): RoomDatabase.Builder<TrailsDatabase> {
    val dbFilePath = documentDirectory() + "/trails.db"
    return Room.databaseBuilder<TrailsDatabase>(
        name = dbFilePath,
    )
}

private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}