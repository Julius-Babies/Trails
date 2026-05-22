@file:OptIn(ExperimentalForeignApi::class)

package es.jvbabi.trails

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPopoverArrowDirectionAny
import platform.UIKit.UIScreen
import platform.UIKit.popoverPresentationController

actual fun openUrl(url: String) {
    val nsUrl = NSURL(string = url)
    val safariViewController = SFSafariViewController(nsUrl)

    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController

    rootViewController?.presentViewController(
        viewControllerToPresent = safariViewController,
        animated = true,
        completion = null
    )
}

actual fun shareUrl(url: String, title: String?) {
    val itemsToShare = mutableListOf<Any>(NSURL.URLWithString(url)!!)

    val activityViewController = UIActivityViewController(
        activityItems = itemsToShare,
        applicationActivities = null
    )

    val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController

    activityViewController.popoverPresentationController?.apply {
        val viewBounds = rootViewController?.view?.bounds ?: UIScreen.mainScreen.bounds

        sourceView = rootViewController?.view
        sourceRect = CGRectMake(
            x = viewBounds.useContents { size.width } / 2,
            y = viewBounds.useContents { size.height },
            width = 0.0,
            height = 0.0
        )
        permittedArrowDirections = UIPopoverArrowDirectionAny
    }

    rootViewController?.presentViewController(
        viewControllerToPresent = activityViewController,
        animated = true,
        completion = null
    )
}

actual fun getClipboardText(): String? {
    val pasteboard = UIPasteboard.generalPasteboard
    return pasteboard.string
}

@Composable
actual fun dynamicTheme(dark: Boolean): ColorScheme {
    return MaterialTheme.colorScheme
}