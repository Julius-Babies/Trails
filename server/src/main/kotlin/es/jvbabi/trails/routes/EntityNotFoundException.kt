package es.jvbabi.trails.routes

/**
 * Thrown by the `ApplicationCall.getEntity()` helpers in the item packages when an entity
 * requested via a path parameter does not exist. The StatusPages plugin
 * ([es.jvbabi.trails.api.installStatusPages]) translates it into a 404.
 */
class EntityNotFoundException(message: String) : RuntimeException(message)
