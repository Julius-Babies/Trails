package es.jvbabi.trails.domain.repository

interface AnalyticsRepository {
    fun trackEvent(event: String, properties: Map<String, String?> = emptyMap())
    fun startSpan(name: String): Span
}

interface Span {
    val id: String
    fun addEvent(event: String)
    fun setProperty(key: String, value: String?)
    fun createChild(name: String): Span
}