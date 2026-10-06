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
import androidx.compose.ui.platform.LocalResources
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
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Credit the map shows at all times, and the whole of it, shown when that is touched. */
const val MAP_CREDIT_SHORT = "© OpenStreetMap"
const val MAP_CREDIT = "OpenFreeMap © OpenMapTiles · © OpenStreetMap"

/** The map is drawn on the phone from OpenFreeMap's data, starting from its plainest styles. */
private const val OPEN_FREE_MAP = "https://tiles.openfreemap.org/styles/"

private fun style(dark: Boolean): Style.Builder = Style.Builder().fromUri(OPEN_FREE_MAP + if (dark) "dark" else "positron")

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
private class Shown(
    var pins: List<MapPin> = emptyList(),
    var dark: Boolean = false,
    var language: String = "en",
    var ownPin: Bitmap? = null,
    var foundPin: Bitmap? = null,
)

private fun features(pins: List<MapPin>): FeatureCollection = FeatureCollection.fromFeatures(
    pins.mapIndexed { i, pin ->
        Feature.fromGeometry(Point.fromLngLat(pin.point.longitude, pin.point.latitude)).apply {
            addNumberProperty("i", i)
            addStringProperty("icon", if (pin.own) OWN else FOUND)
        }
    },
)

/** The data every layer is drawn from, as OpenFreeMap's styles name it. */
private const val DATA = "openmaptiles"

private fun oneOf(property: String, vararg values: String): Expression =
    Expression.any(*values.map { Expression.eq(Expression.get(property), Expression.literal(it)) }.toTypedArray())

private fun name(language: String): Expression =
    Expression.coalesce(Expression.get("name:$language"), Expression.get("name:latin"), Expression.get("name"))

/**
 * What the plain styles leave out and helps to find one's way to a gym: meadows, the names of
 * parks, summits, and places worth a visit. Woods and parks, which they barely tint, get a
 * shade that can be seen.
 */
private fun enrich(style: Style, dark: Boolean, language: String) {
    val meadow = FillLayer("ct_meadow", DATA).apply {
        sourceLayer = "landcover"
        setFilter(oneOf("class", "grass", "wetland"))
        setProperties(PropertyFactory.fillColor(if (dark) "#22271f" else "#e9eee1"))
    }
    if (style.getLayer("landcover_wood") != null) style.addLayerAbove(meadow, "landcover_wood") else style.addLayer(meadow)
    (style.getLayer("landcover_wood") as? FillLayer)?.setProperties(PropertyFactory.fillColor(if (dark) "#1f271f" else "#dde6d6"), PropertyFactory.fillOpacity(1f))
    (style.getLayer("park") as? FillLayer)?.setProperties(PropertyFactory.fillColor(if (dark) "#1d241d" else "#e4ecdd"), PropertyFactory.fillOpacity(1f))

    fun label(id: String, layer: String, from: Float, size: Float, text: Expression, filter: Expression? = null) = SymbolLayer(id, DATA).apply {
        sourceLayer = layer
        minZoom = from
        if (filter != null) setFilter(filter)
        setProperties(
            PropertyFactory.textField(text),
            PropertyFactory.textFont(arrayOf("Noto Sans Italic")),
            PropertyFactory.textSize(size),
            PropertyFactory.textMaxWidth(8f),
            PropertyFactory.textHaloWidth(1.2f),
        )
        style.addLayer(this)
    }
    label("ct_park", "park", 8f, 11f, name(language))
    label("ct_peak", "mountain_peak", 10f, 11f, Expression.concat(Expression.literal("▲ "), name(language)))
    label(
        "ct_sight", "poi", 12f, 11f, name(language),
        oneOf("class", "attraction", "museum", "castle", "park", "campsite", "stadium"),
    )
}

/**
 * Fits the style to the app: place names in the language of the app where the map has them,
 * quieter lettering, and for the dark theme the warm dark tones of the app, with streets that
 * can be told from the ground once close enough to matter.
 */
private fun tune(style: Style, dark: Boolean, language: String) {
    for (layer in style.layers) {
        when (layer) {
            is SymbolLayer -> {
                val field = layer.textField
                val current = field.expression?.toString() ?: field.value?.toString().orEmpty()
                // Road numbers and the like are left as they are.
                if ("name" in current && !layer.id.startsWith("ct_")) layer.setProperties(PropertyFactory.textField(name(language)))
                layer.setProperties(
                    PropertyFactory.textColor(if (dark) "#cfc7be" else "#5c554e"),
                    PropertyFactory.textHaloColor(if (dark) "#1c1a18" else "#ffffff"),
                )
            }
            is BackgroundLayer -> if (dark) layer.setProperties(PropertyFactory.backgroundColor("#1c1a18"))
            is FillLayer -> if (dark && !layer.id.startsWith("ct_") && layer.id != "park" && layer.id != "landcover_wood") {
                layer.setProperties(
                    PropertyFactory.fillColor(
                        when (layer.sourceLayer) {
                            "water" -> "#10161b"
                            "building" -> "#2d2925"
                            else -> "#23201d"
                        },
                    ),
                )
            }
            is LineLayer -> if (dark) {
                when (layer.sourceLayer) {
                    // Seen from afar the road network would cover the country: it comes up with the zoom.
                    "transportation" -> layer.setProperties(
                        PropertyFactory.lineColor(
                            Expression.interpolate(
                                Expression.linear(), Expression.zoom(),
                                Expression.stop(5, Expression.rgb(46, 42, 39)),
                                Expression.stop(11, Expression.rgb(94, 88, 81)),
                            ),
                        ),
                    )
                    "boundary" -> layer.setProperties(PropertyFactory.lineColor("#7d746a"))
                    "waterway" -> layer.setProperties(PropertyFactory.lineColor("#1b2a35"))
                    else -> layer.setProperties(PropertyFactory.lineColor("#3b3632"))
                }
            }
            else -> Unit
        }
    }
}

/** Loads the style for the theme and puts the pins on it. */
private fun dress(map: MapLibreMap, shown: Shown) {
    map.setStyle(style(shown.dark)) { loaded ->
        enrich(loaded, shown.dark, shown.language)
        tune(loaded, shown.dark, shown.language)
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
    val language = LocalResources.current.textLocale().language
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
            shown.language = language
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
