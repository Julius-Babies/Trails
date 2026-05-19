package es.jvbabi.trails.database

import database.DataSnapshots
import es.jvbabi.trails.config.ApplicationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class DatabaseManager: KoinComponent {
    private val applicationConfig by inject<ApplicationConfig>()
    private val databasePath = applicationConfig
        .storage
        .resolve("database.db")
        .absolutePath
    val database = Database.connect(applicationConfig.databaseUrl)

    init {
        transaction(db = database) {
            SchemaUtils.create(Users)
            SchemaUtils.create(Devices, Sessions)
            SchemaUtils.create(DataSnapshots, Shares, ActiveShares)
        }
    }

    suspend fun <T> transaction(block: () -> T): T {
        return withContext(Dispatchers.IO) {
            return@withContext transaction(db = this@DatabaseManager.database) {
                return@transaction block()
            }
        }
    }
}