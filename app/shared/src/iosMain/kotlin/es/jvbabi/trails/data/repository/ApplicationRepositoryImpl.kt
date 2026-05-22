@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.ApplicationRepository
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.NSObject

class IosApplicationRepository : ApplicationRepository {

    override fun getApplicationForegroundState(): Flow<Boolean> = callbackFlow {
        trySend(true)

        val observer = object : NSObject() {
            @ObjCAction
            fun didBecomeActive() { trySend(true) }

            @ObjCAction
            fun willResignActive() { trySend(false) }
        }

        NSNotificationCenter.defaultCenter.addObserver(
            observer = observer,
            selector = NSSelectorFromString("didBecomeActive"),
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
        )
        NSNotificationCenter.defaultCenter.addObserver(
            observer = observer,
            selector = NSSelectorFromString("willResignActive"),
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
        )

        awaitClose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
        }
    }.distinctUntilChanged()
}
