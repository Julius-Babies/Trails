package es.jvbabi.trails.utils

import kotlin.math.*

data class Location(
    val latitude: Double,
    val longitude: Double,
)

/**
 * @return Distance in meters
 */
infix fun Location.distanceTo(second: Location): Double {
    val earthRadius = 6_371_000.0

    val first = this

    val latitudeDifference = toRadians(
        second.latitude - first.latitude
    )

    val longitudeDifference = toRadians(
        second.longitude - first.longitude
    )

    val firstLatitude = toRadians(first.latitude)
    val secondLatitude = toRadians(second.latitude)

    val a =
        sin(latitudeDifference / 2).pow(2) +
                cos(firstLatitude) *
                cos(secondLatitude) *
                sin(longitudeDifference / 2).pow(2)

    val angularDistance = 2 * atan2(
        sqrt(a),
        sqrt(1 - a)
    )

    return earthRadius * angularDistance
}

fun toRadians(deg: Double): Double = deg / 180.0 * PI