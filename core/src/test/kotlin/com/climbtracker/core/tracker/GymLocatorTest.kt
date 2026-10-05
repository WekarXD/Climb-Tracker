package com.climbtracker.core.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GymLocatorTest {

    private val here = GeoPoint(40.4168, -3.7038)

    /** A point [metres] north of [here]. */
    private fun north(metres: Double) = GeoPoint(here.latitude + metres / 111_195.0, here.longitude)

    @Test
    fun measuresDistanceOverTheGlobe() {
        assertEquals(111_195.0, GymLocator.distanceMeters(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0)), 100.0)
        assertEquals(0.0, GymLocator.distanceMeters(here, here), 0.001)
        assertEquals(250.0, GymLocator.distanceMeters(here, north(250.0)), 1.0)
    }

    @Test
    fun picksTheClosestPlaceInRange() {
        val places = listOf("lejos" to north(300.0), "cerca" to north(80.0))
        assertEquals("cerca", GymLocator.nearest(places, here))
    }

    @Test
    fun picksNothingWhenEverythingIsOutOfRange() {
        assertNull(GymLocator.nearest(listOf("otro" to north(5000.0)), here))
        assertNull(GymLocator.nearest(emptyList<Pair<String, GeoPoint>>(), here))
    }

    @Test
    fun aWiderRadiusReachesFurther() {
        assertEquals("otro", GymLocator.nearest(listOf("otro" to north(1500.0)), here, radius = 2000.0))
    }
}
