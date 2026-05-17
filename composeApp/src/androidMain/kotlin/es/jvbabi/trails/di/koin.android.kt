package es.jvbabi.trails.di

import android.annotation.SuppressLint
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import es.jvbabi.trails.data.database.TrailsDatabase
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.cio.CIOEngineConfig
import org.koin.mp.KoinPlatformTools
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

actual fun getDatabaseBuilder(): RoomDatabase.Builder<TrailsDatabase> {
    val context = KoinPlatformTools.defaultContext().get().get<Context>()

    return Room.databaseBuilder<TrailsDatabase>(
        context = context,
        name = context.getDatabasePath("trails.db").absolutePath
    )
}

@SuppressLint("CustomX509TrustManager")
class TrailsTrustManager : X509TrustManager {

    private val delegate: X509TrustManager by lazy {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as KeyStore?)
        factory.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
            ?: throw IllegalStateException("Kein X509TrustManager gefunden")
    }

    private fun shouldBypassValidation(certificates: Array<out X509Certificate>?): Boolean {
        if (certificates.isNullOrEmpty()) return false

        return certificates.any { cert ->
            val dn = cert.subjectDN.name
            dn.contains("trails.werkbank.space", ignoreCase = true)
        }
    }

    override fun checkServerTrusted(certificates: Array<out X509Certificate>?, authType: String?) {
        if (shouldBypassValidation(certificates)) {
            return
        }

        delegate.checkServerTrusted(certificates, authType)
    }

    override fun checkClientTrusted(certificates: Array<out X509Certificate>?, authType: String?) {
        delegate.checkClientTrusted(certificates, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
}

actual fun HttpClientConfig<CIOEngineConfig>.configureHttpClient() {
}
