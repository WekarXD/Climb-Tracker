package com.climbtracker.data

import android.app.Application
import com.climbtracker.core.tracker.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GymSearchTest {

    @Test
    fun readsSportsCentresFoundByName() {
        val places = GymSearch.parsePhoton(
            """{"features":[
              {"geometry":{"coordinates":[-3.9001,40.5123],"type":"Point"},"type":"Feature",
               "properties":{"osm_type":"N","osm_key":"leisure","osm_value":"sports_centre","name":"Roco Norte",
                             "street":"Calle Mayor","housenumber":"3","city":"Villa","country":"País"}},
              {"geometry":{"coordinates":[-3.7,40.3],"type":"Point"},"type":"Feature",
               "properties":{"osm_type":"W","osm_key":"leisure","osm_value":"sports_centre","name":"Roco Sur"}},
              {"geometry":{"coordinates":[-3.1,40.1],"type":"Point"},"type":"Feature","properties":{"osm_type":"N"}}
            ]}""",
        )
        assertEquals(listOf("Roco Norte", "Roco Sur"), places.map { it.name })
        assertEquals("Calle Mayor 3, Villa", places[0].detail)
        assertEquals("", places[1].detail)
        assertEquals(40.5123, places[0].point.latitude, 1e-9)
        assertEquals(-3.9001, places[0].point.longitude, 1e-9)
    }

    @Test
    fun searchesByNameOnlyAmongSportsCentresAndNearestFirst() {
        val url = GymSearch.nameUrl("roco norte", GeoPoint(40.42, -3.7))
        assertTrue(url, "q=roco+norte" in url)
        assertTrue(url, "osm_tag=leisure%3Asports_centre" in url)
        assertTrue(url, "lat=40.42000" in url && "lon=-3.70000" in url)
        assertTrue("lat=" !in GymSearch.nameUrl("roco", null))
    }

    @Test
    fun keepsOnlySportsCentresAndClimbingPlacesFromAGeneralSearch() {
        val places = GymSearch.parseNominatim(
            """[
              {"category":"leisure","type":"sports_centre","name":"Roco Norte","display_name":"Roco Norte, Calle Mayor, Villa, Provincia, 28000","lat":"40.5123","lon":"-3.9001","extratags":{"sport":"climbing"}},
              {"category":"historic","type":"memorial","name":"Roco","display_name":"Roco, Plaza, Villa","lat":"40.1","lon":"-3.1","extratags":null},
              {"category":"leisure","type":"fitness_centre","name":"Roco Sur","display_name":"Roco Sur, Otra Villa","lat":"40.3","lon":"-3.7","extratags":{"sport":"climbing;fitness"}},
              {"category":"leisure","type":"sports_centre","name":"Polideportivo","display_name":"Polideportivo, Villa","lat":"40.2","lon":"-3.2"}
            ]""",
        )
        assertEquals(listOf("Roco Norte", "Roco Sur", "Polideportivo"), places.map { it.name })
        assertEquals("Calle Mayor, Villa, Provincia", places[0].detail)
        assertEquals(40.5123, places[0].point.latitude, 1e-9)
    }

    @Test
    fun theGeneralSearchPrefersPlacesAroundTheMap() {
        val url = GymSearch.exactNameUrl("roco norte", GeoPoint(40.0, -3.0))
        assertTrue(url, "q=roco+norte" in url && "extratags=1" in url)
        assertTrue(url, "viewbox=-4.50000,41.50000,-1.50000,38.50000" in url)
        assertTrue("viewbox" !in GymSearch.exactNameUrl("roco", null))
    }

    @Test
    fun resultsFromTwoSearchesAreJoinedWithoutRepeats() {
        val a = Place("Roco Norte", "Villa", GeoPoint(40.5, -3.9))
        val same = Place("roco norte", "", GeoPoint(40.50004, -3.90003))
        val b = Place("Roco Sur", "", GeoPoint(40.3, -3.7))
        assertEquals(listOf(a, b), GymSearch.merge(listOf(a), listOf(same, b)))
    }

    @Test
    fun anAreaSearchThatRanOutOfTimeIsAFailureNotAnEmptyAnswer() {
        val answer = """{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 1 after 29 seconds."}"""
        assertThrows(IOException::class.java) { GymSearch.parseOverpass(answer) }
    }

    @Test
    fun readsGymsFoundInAnArea() {
        val places = GymSearch.parseOverpass(
            """{"elements":[
              {"type":"node","lat":40.5,"lon":-3.9,"tags":{"name":"Roco Norte","addr:street":"Calle Mayor","addr:housenumber":"3","addr:city":"Villa"}},
              {"type":"way","center":{"lat":40.3,"lon":-3.7},"tags":{"name":"Roco Sur"}},
              {"type":"node","lat":40.1,"lon":-3.1,"tags":{"sport":"climbing"}},
              {"type":"way","center":{"lat":40.50004,"lon":-3.90003},"tags":{"name":"Roco Norte"}}
            ]}""",
        )
        assertEquals(listOf("Roco Norte", "Roco Sur"), places.map { it.name })
        assertEquals("Calle Mayor 3, Villa", places[0].detail)
        assertEquals("", places[1].detail)
        assertEquals(40.3, places[1].point.latitude, 1e-9)
    }

    @Test
    fun rejectsAnAnswerThatIsNotData() {
        assertThrows(IOException::class.java) { GymSearch.parsePhoton("<html>busy</html>") }
        assertThrows(IOException::class.java) { GymSearch.parseOverpass("<html>busy</html>") }
    }

    @Test
    fun asksOnlyForNamedClimbingPlacesInTheBox() {
        val query = GymSearch.overpassQuery(40.0, -4.0, 41.0, -3.0)
        assertTrue(query, "(40.00000,-4.00000,41.00000,-3.00000)" in query)
        assertTrue(query, "climbing" in query && "\"name\"" in query)
    }
}
