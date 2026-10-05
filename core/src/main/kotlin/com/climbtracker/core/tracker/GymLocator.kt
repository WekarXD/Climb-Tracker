package com.climbtracker.core.tracker

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A position on the globe, in degrees. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/** Works out which known place the phone is at. */
object GymLocator {
    /** How far from a gym's stored position the phone still counts as being there. */
    const val RADIUS_METERS = 400.0

    private const val EARTH_RADIUS_METERS = 6_371_000.0

    /** Great-circle distance between two points. */
    fun distanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** The place closest to [here] among those within [radius] metres, or null if none is. */
    fun <T> nearest(places: List<Pair<T, GeoPoint>>, here: GeoPoint, radius: Double = RADIUS_METERS): T? =
        places.map { it.first to distanceMeters(it.second, here) }
            .filter { it.second <= radius }
            .minByOrNull { it.second }
            ?.first
}
