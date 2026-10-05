package com.climbtracker.data

import com.climbtracker.core.tracker.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Somewhere found on the map. [detail] is its address, as far as it is known. */
data class Place(val name: String, val detail: String, val point: GeoPoint)

/**
 * Looks gyms up in OpenStreetMap data. By name there are two services, because neither is enough
 * alone: Nominatim answers at once but only finds whole names, and Photon finds part of a name
 * but can take half a minute. By map area, Overpass lists the places mapped as climbing.
 * Both throw IOException when there is no connection or the service does not answer properly.
 */
class GymSearch(private val userAgent: String) {

    /** Sports centres and climbing places called [query]; quick, but misses names that only contain it. */
    suspend fun byExactName(query: String, near: GeoPoint?): List<Place> = withContext(Dispatchers.IO) {
        parseNominatim(request(exactNameUrl(query, near), null))
    }

    /** Sports centres whose name matches, those closest to [near] first. */
    suspend fun byName(query: String, near: GeoPoint?): List<Place> = withContext(Dispatchers.IO) {
        parsePhoton(request(nameUrl(query, near), null))
    }

    /** Climbing places with a name inside the box. */
    suspend fun inArea(south: Double, west: Double, north: Double, east: Double): List<Place> = withContext(Dispatchers.IO) {
        parseOverpass(request(OVERPASS, "data=" + URLEncoder.encode(overpassQuery(south, west, north, east), "UTF-8")))
    }

    private fun request(url: String, body: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 45_000
            // Both services ask every application to identify itself.
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val PHOTON = "https://photon.komoot.io/api/?limit=20&osm_tag="

        fun nameUrl(query: String, near: GeoPoint?): String {
            val bias = if (near == null) "" else String.format(Locale.US, "&lat=%.5f&lon=%.5f", near.latitude, near.longitude)
            return PHOTON + URLEncoder.encode("leisure:sports_centre", "UTF-8") + "&q=" + URLEncoder.encode(query, "UTF-8") + bias
        }
        private const val OVERPASS = "https://overpass-api.de/api/interpreter"

        fun overpassQuery(south: Double, west: Double, north: Double, east: Double): String {
            val box = String.format(Locale.US, "(%.5f,%.5f,%.5f,%.5f)", south, west, north, east)
            // Crags are tagged as climbing too; those carry a "natural" tag and gyms do not.
            return """[out:json][timeout:20];nwr["sport"~"climbing"]["name"]["natural"!~"."]$box;out center tags 60;"""
        }

        private const val NOMINATIM = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=40&layer=poi&extratags=1&q="

        /** How far around the map, in degrees, the general search looks first. */
        private const val NEARBY_DEGREES = 1.5

        fun exactNameUrl(query: String, near: GeoPoint?): String {
            val bias = if (near == null) {
                ""
            } else {
                String.format(
                    Locale.US, "&viewbox=%.5f,%.5f,%.5f,%.5f",
                    near.longitude - NEARBY_DEGREES, near.latitude + NEARBY_DEGREES,
                    near.longitude + NEARBY_DEGREES, near.latitude - NEARBY_DEGREES,
                )
            }
            return NOMINATIM + URLEncoder.encode(query, "UTF-8") + bias
        }

        fun parseNominatim(text: String): List<Place> = parsing {
            val array = JSONArray(text)
            List(array.length()) { array.getJSONObject(it) }.mapNotNull { o ->
                // This search returns any kind of place; only gyms are wanted.
                val sportsCentre = o.optString("category") == "leisure" && o.optString("type") == "sports_centre"
                val climbing = "climbing" in (o.optJSONObject("extratags")?.optString("sport").orEmpty())
                if (!sportsCentre && !climbing) return@mapNotNull null
                val parts = o.optString("display_name").split(", ").filter { it.isNotBlank() }
                val name = o.optString("name").ifBlank { parts.firstOrNull().orEmpty() }
                if (name.isBlank()) return@mapNotNull null
                Place(name, parts.drop(1).take(3).joinToString(", "), GeoPoint(o.getString("lat").toDouble(), o.getString("lon").toDouble()))
            }
        }

        /** [first] followed by what [second] adds; the same gym found twice is kept once. */
        fun merge(first: List<Place>, second: List<Place>): List<Place> = (first + second).distinctBy(::identity)

        // The same gym is often mapped twice, as a point and as its building.
        private fun identity(place: Place) =
            Triple(place.name.lowercase(), Math.round(place.point.latitude * 1000), Math.round(place.point.longitude * 1000))

        fun parsePhoton(text: String): List<Place> = parsing {
            val features = JSONObject(text).getJSONArray("features")
            List(features.length()) { features.getJSONObject(it) }.mapNotNull { feature ->
                val properties = feature.optJSONObject("properties") ?: return@mapNotNull null
                val name = properties.optString("name").ifBlank { return@mapNotNull null }
                // GeoJSON puts the longitude first.
                val at = feature.getJSONObject("geometry").getJSONArray("coordinates")
                val street = listOf(properties.optString("street"), properties.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
                val detail = listOf(street, properties.optString("city")).filter { it.isNotBlank() }.joinToString(", ")
                Place(name, detail, GeoPoint(at.getDouble(1), at.getDouble(0)))
            }
        }

        fun parseOverpass(text: String): List<Place> = parsing {
            val answer = JSONObject(text)
            val elements = answer.getJSONArray("elements")
            // A search that ran out of time answers with nothing found and a remark saying so.
            if (elements.length() == 0 && "error" in answer.optString("remark")) throw IOException(answer.optString("remark"))
            List(elements.length()) { elements.getJSONObject(it) }.mapNotNull { o ->
                val tags = o.optJSONObject("tags") ?: return@mapNotNull null
                val name = tags.optString("name").ifBlank { return@mapNotNull null }
                // Nodes carry their position; areas come with the centre worked out.
                val at = if (o.has("lat")) o else o.optJSONObject("center") ?: return@mapNotNull null
                val street = listOf(tags.optString("addr:street"), tags.optString("addr:housenumber")).filter { it.isNotBlank() }.joinToString(" ")
                val detail = listOf(street, tags.optString("addr:city")).filter { it.isNotBlank() }.joinToString(", ")
                Place(name, detail, GeoPoint(at.getDouble("lat"), at.getDouble("lon")))
            }.distinctBy(::identity)
        }

        private fun <T> parsing(block: () -> T): T = try {
            block()
        } catch (e: JSONException) {
            throw IOException("Unexpected answer", e)
        } catch (e: NumberFormatException) {
            throw IOException("Unexpected answer", e)
        }
    }
}
