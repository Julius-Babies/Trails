package es.jvbabi.trails.domain.repository

import io.ktor.http.URLBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface TrailsServerRepository {

    fun connect()

    val isConnected: StateFlow<Boolean>

    fun getBaseUrl(): Flow<URLBuilder?>
}