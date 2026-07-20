package es.jvbabi.trails.data.repository

import es.jvbabi.trails.domain.repository.AnalyticsRepository
import es.jvbabi.trails.domain.repository.Span
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.tracing.Tracer
import io.opentelemetry.kotlin.tracing.Span as KotlinOtlpSpan

class AnalyticsRepositoryImpl(
    private val openTelemetry: OpenTelemetry,
) : AnalyticsRepository {

    private val tracer: Tracer by lazy {
        openTelemetry.tracerProvider.getTracer("trails")
    }

    override fun trackEvent(event: String, properties: Map<String, String?>) {
        val span = tracer.startSpan(name = event)
        properties.forEach { (key, value) ->
            value?.let { span.setStringAttribute(key, it) }
        }
        span.end()
    }

    override fun startSpan(name: String): Span {
        val span = tracer.startSpan(name = name)
        return OtlpSpan(span, tracer, openTelemetry)
    }
}

class OtlpSpan(
    private val entity: KotlinOtlpSpan,
    private val tracer: Tracer,
    private val openTelemetry: OpenTelemetry,
) : Span {
    override val id: String = entity.spanContext.spanId

    override fun addEvent(event: String) {
        entity.addEvent(event)
    }

    override fun setProperty(key: String, value: String?) {
        value?.let { entity.setStringAttribute(key, it) }
    }

    override fun createChild(name: String): Span {
        val parentContext = openTelemetry.context.implicit().storeSpan(entity)
        val childSpan = tracer.startSpan(
            name = name,
            parentContext = parentContext
        )
        return OtlpSpan(childSpan, tracer, openTelemetry)
    }
}
