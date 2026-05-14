package es.jvbabi.trails

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import org.koin.core.qualifier.named
import org.koin.mp.KoinPlatformTools

actual fun openUrl(url: String) {
    val context = KoinPlatformTools.defaultContext().get().get<Context>(named(KOIN_ACTIVITY_CONTEXT))
    val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
    customTabsIntent.launchUrl(context, url.toUri())
}