package es.jvbabi.trails

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
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

@Composable
actual fun dynamicTheme(dark: Boolean): ColorScheme {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return MaterialTheme.colorScheme
    val context = KoinPlatformTools.defaultContext().get().get<Context>(named(KOIN_ACTIVITY_CONTEXT))
    return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
}

actual fun shareUrl(url: String, title: String?) {
    val context = KoinPlatformTools.defaultContext().get().get<Context>(named(KOIN_ACTIVITY_CONTEXT))
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        title?.let { putExtra(Intent.EXTRA_TITLE, title) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val chooser = Intent.createChooser(intent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    context.startActivity(chooser)
}