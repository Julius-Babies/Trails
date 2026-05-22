package es.jvbabi.trails.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import es.jvbabi.trails.data.database.TrailsDatabase
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import org.koin.mp.KoinPlatformTools

actual fun getDatabaseBuilder(): RoomDatabase.Builder<TrailsDatabase> {
    val context = KoinPlatformTools.defaultContext().get().get<Context>()

    return Room.databaseBuilder<TrailsDatabase>(
        context = context,
        name = context.getDatabasePath("trails.db").absolutePath
    )
}

actual fun HttpClientConfig<CIOEngineConfig>.configureHttpClient() {
}
