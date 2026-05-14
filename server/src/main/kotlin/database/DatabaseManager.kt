package es.jvbabi.trails.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class DatabaseManager {
    val database = Database.connect("jdbc:sqlite:database.db")

    init {
        transaction(db = database) {
            SchemaUtils.create(Users)
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