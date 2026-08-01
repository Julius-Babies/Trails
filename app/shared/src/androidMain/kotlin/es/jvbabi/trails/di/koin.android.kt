package es.jvbabi.trails.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import es.jvbabi.trails.data.database.TrailsDatabase
import es.jvbabi.trails.data.repository.TrailsAppRepositoryImpl
import es.jvbabi.trails.domain.repository.TrailsAppRepository
import es.jvbabi.trails.domain.usecase.app.CheckAppIsLatestVersionUseCase
import es.jvbabi.trails.domain.usecase.app.GetReleaseChangelogsUseCase
import es.jvbabi.trails.ui.overlay.update_available.UpdateAvailableViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools

actual fun getDatabaseBuilder(): RoomDatabase.Builder<TrailsDatabase> {
    val context = KoinPlatformTools.defaultContext().get().get<Context>()

    return Room.databaseBuilder<TrailsDatabase>(
        context = context,
        name = context.getDatabasePath("trails.db").absolutePath
    )
}

/** Android is the only platform that updates itself, so the whole updater is declared here. */
actual fun platformModule(): Module = module {
    single<TrailsAppRepository> {
        TrailsAppRepositoryImpl(
            httpClient = get(named(KOIN_HTTP_CLIENT_THIRD_PARTY)),
            deviceRepository = get(),
        )
    }

    singleOf(::CheckAppIsLatestVersionUseCase)
    singleOf(::GetReleaseChangelogsUseCase)

    viewModelOf(::UpdateAvailableViewModel)
}
