package com.climbtracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.climbtracker.core.tracker.GeoPoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Where the map comes from: drawn on the phone from OpenFreeMap's data, or OpenStreetMap's own pictures. */
private const val VECTOR = true

/** Credit the map must show. */
const val MAP_CREDIT = "OpenFreeMap © OpenMapTiles · © OpenStreetMap"

private const val OPEN_FREE_MAP = "https://tiles.openfreemap.org/styles/"

/**
 * OpenStreetMap's map as pictures, washed out to pale greys so that roads and names stay
 * readable but only the gyms stand out; turned over to dark greys for the dark theme.
 */
private fun pictureStyle(dark: Boolean): String {
    val background = if (dark) "#201e1c" else "#f1f0ee"
    val paint = if (dark) {
        """"raster-saturation":-0.8,"raster-brightness-min":0.75,"raster-brightness-max":0.12"""
    } else {
        """"raster-saturation":-0.8,"raster-brightness-min":0.4,"raster-brightness-max":1"""
    }
    return """{"version":8,
        "sources":{"osm":{"type":"raster","tiles":["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],"tileSize":256,"maxzoom":19}},
        "layers":[{"id":"background","type":"background","paint":{"background-color":"$background"}},
                  {"id":"osm","type":"raster","source":"osm","paint":{$paint}}]}"""
}

private fun style(dark: Boolean): Style.Builder = if (VECTOR) {
    Style.Builder().fromUri(OPEN_FREE_MAP + if (dark) "dark" else "positron")
} else {
    Style.Builder().fromJson(pictureStyle(dark))
}

/** A marker on the map. [own] ones are the user's gyms; [key] says what it stands for. */
data class MapPin(val point: GeoPoint, val title: String, val own: Boolean, val key: Any)

class MapBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** Lets the screen move the map and ask what it shows. */
class GymMapState {
    internal var map: MapLibreMap? = null

    /** Where to show the map when it is created, if [moveTo] was called before that. */
    internal var start: Pair<GeoPoint, Double> = GeoPoint(40.0, -3.7) to 5.0

    fun moveTo(point: GeoPoint, zoom: Double = 14.0) {
        start = point to zoom
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(point.latitude, point.longitude), zoom - ZOOM_SHIFT), 600)
    }

    fun center(): GeoPoint? = map?.cameraPosition?.target?.let { GeoPoint(it.latitude, it.longitude) }

    fun bounds(): MapBounds? = map?.projection?.visibleRegion?.latLngBounds?.let {
        MapBounds(it.latitudeSouth, it.longitudeWest, it.latitudeNorth, it.longitudeEast)
    }

    internal companion object {
        /** This map counts zoom levels from tiles twice as large as those the rest of the app thinks in. */
        const val ZOOM_SHIFT = 1.0
    }
}

/** A plain map pin: a drop of [colour] with a white centre, its tip at the bottom middle. */
private fun pinBitmap(colour: Int, density: Float): Bitmap {
    val width = 26 * density
    val height = 34 * density
    val radius = width / 2
    val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
    canvas.drawCircle(radius, radius, radius, paint)
    canvas.drawPath(
        Path().apply {
            moveTo(radius * 0.14f, radius * 1.5f)
            lineTo(radius * 1.86f, radius * 1.5f)
            lineTo(radius, height)
            close()
        },
        paint,
    )
    canvas.drawCircle(radius, radius, radius * 0.4f, paint.apply { color = Color.WHITE })
    return bitmap
}

private const val PINS = "pins"
private const val OWN = "own"
private const val FOUND = "found"

/** What the map is showing, kept so that a new style can be dressed again with it. */
private class Shown(var pins: List<MapPin> = emptyList(), var dark: Boolean = false, var ownPin: Bitmap? = null, var foundPin: Bitmap? = null)

private fun features(pins: List<MapPin>): FeatureCollection = FeatureCollection.fromFeatures(
    pins.mapIndexed { i, pin ->
        Feature.fromGeometry(Point.fromLngLat(pin.point.longitude, pin.point.latitude)).apply {
            addNumberProperty("i", i)
            addStringProperty("icon", if (pin.own) OWN else FOUND)
        }
    },
)

/** Loads the style for the theme and puts the pins on it. */
private fun dress(map: MapLibreMap, shown: Shown) {
    map.setStyle(style(shown.dark)) { loaded ->
        shown.ownPin?.let { loaded.addImage(OWN, it) }
        shown.foundPin?.let { loaded.addImage(FOUND, it) }
        loaded.addSource(GeoJsonSource(PINS, features(shown.pins)))
        loaded.addLayer(
            SymbolLayer(PINS, PINS).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }
}

/** Map with [pins]. The caller shows [MAP_CREDIT]. */
@Composable
fun GymMap(state: GymMapState, pins: List<MapPin>, onPin: (MapPin) -> Unit, modifier: Modifier = Modifier) {
    val currentOnPin = rememberUpdatedState(onPin)
    val density = LocalContext.current.resources.displayMetrics.density
    val dark = LocalPalette.current.dark
    val ink = Ink.toArgb()
    val shown = remember { Shown() }
    val view = remember { arrayOfNulls<MapView>(1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapLibre.getInstance(context)
            MapView(context).apply {
                onCreate(null)
                // Created with the screen already in view, so it is started here and not by the lifecycle.
                onStart()
                onResume()
                view[0] = this
                getMapAsync { map ->
                    state.map = map
                    map.uiSettings.apply {
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                        isCompassEnabled = false
                        isLogoEnabled = false
                        isAttributionEnabled = false
                    }
                    map.setMinZoomPreference(2.0)
                    val (point, zoom) = state.start
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(point.latitude, point.longitude)).zoom(zoom - GymMapState.ZOOM_SHIFT).build()
                    map.addOnMapClickListener { where ->
                        val hit = map.queryRenderedFeatures(map.projection.toScreenLocation(where), PINS).firstOrNull()
                        val pin = hit?.getNumberProperty("i")?.toInt()?.let { shown.pins.getOrNull(it) }
                        if (pin != null) currentOnPin.value(pin)
                        pin != null
                    }
                    dress(map, shown)
                }
            }
        },
        update = {
            val restyle = shown.dark != dark || shown.ownPin == null
            shown.pins = pins
            shown.dark = dark
            if (restyle) {
                shown.ownPin = pinBitmap(Terracotta.toArgb(), density)
                shown.foundPin = pinBitmap(ink, density)
            }
            val map = state.map
            if (map != null) {
                if (restyle) dress(map, shown) else map.style?.getSourceAs<GeoJsonSource>(PINS)?.setGeoJson(features(pins))
            }
        },
        onRelease = { map ->
            state.map = null
            view[0] = null
            map.onPause()
            map.onStop()
            map.onDestroy()
        },
    )

    // The map stops loading and drawing while the app is in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view[0]?.onStart()
                Lifecycle.Event.ON_RESUME -> view[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> view[0]?.onPause()
                Lifecycle.Event.ON_STOP -> view[0]?.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
