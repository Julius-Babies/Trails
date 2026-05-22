package es.jvbabi.trails

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import es.jvbabi.trails.data.repository.IosApplicationRepository
import es.jvbabi.trails.data.repository.IosBackgroundServiceRepository
import es.jvbabi.trails.data.repository.IosDeviceRepository
import es.jvbabi.trails.data.repository.IosFileRepository
import es.jvbabi.trails.di.initKoin
import es.jvbabi.trails.domain.repository.ApplicationRepository
import es.jvbabi.trails.domain.repository.BackgroundServiceRepository
import es.jvbabi.trails.domain.repository.DeviceRepository
import es.jvbabi.trails.domain.repository.FileRepository
import es.jvbabi.trails.domain.usecase.auth.HandleDeepLinkUseCase
import io.ktor.http.Url
import org.koin.compose.koinInject
import org.koin.dsl.module
import platform.UIKit.UIViewController
import dev.icerock.moko.permissions.ios.PermissionsController as IosPermissionsController

var deepLinkUrl: String? by mutableStateOf(null)

@Suppress("unused") // Used in SwiftUI
fun MainViewController(url: String = ""): UIViewController {
    initKoin {
        modules(module {
            single<PermissionsController> { IosPermissionsController() }
            single<BackgroundServiceRepository> { IosBackgroundServiceRepository() }
            single<DeviceRepository> { IosDeviceRepository() }
            single<FileRepository> { IosFileRepository() }
            single<ApplicationRepository> { IosApplicationRepository() }
        })
    }

    if (url.isNotEmpty()) {
        deepLinkUrl = url
    }

    return ComposeUIViewController {
        val handleDeepLinkUseCase = koinInject<HandleDeepLinkUseCase>()

        LaunchedEffect(deepLinkUrl) {
            val url = deepLinkUrl ?: return@LaunchedEffect
            println("[DeepLink] processing: $url")
            handleDeepLinkUseCase(Url(url))
            println("[DeepLink] done: $url")
            deepLinkUrl = null
        }

        BindEffect(koinInject<PermissionsController>())
        App()
    }
}

@Suppress("unused") // Used in SwiftUI
fun updateView(url: String) {
    println("[DeepLink] updateView: $url")
    deepLinkUrl = url
}