package com.climbtracker.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.BitmapDrawable
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import org.osmdroid.util.GeoPoint as OsmPoint

/** Credit the map must show. */
const val MAP_CREDIT = "© OpenStreetMap"

/**
 * Washes the map out to pale greys, so that roads and names stay readable but only the gyms
 * stand out.
 */
private val PALE = ColorMatrixColorFilter(
    ColorMatrix().apply {
        setSaturation(0.2f)
        postConcat(
            ColorMatrix(
                floatArrayOf(
                    0.6f, 0f, 0f, 0f, 100f,
                    0f, 0.6f, 0f, 0f, 100f,
                    0f, 0f, 0.6f, 0f, 100f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    },
)
private const val PALE_BACKGROUND = 0xFFF1F0EE.toInt()

/** A marker on the map. [own] ones are the user's gyms; [key] says what it stands for. */
data class MapPin(val point: GeoPoint, val title: String, val own: Boolean, val key: Any)

class MapBounds(val south: Double, val west: Double, val north: Double, val east: Double)

/** Lets the screen move the map and ask what it shows. */
class GymMapState {
    internal var view: MapView? = null

    /** Where to show the map when it is created, if [moveTo] was called before that. */
    internal var start: Pair<GeoPoint, Double> = GeoPoint(40.0, -3.7) to 5.0

    fun moveTo(point: GeoPoint, zoom: Double = 14.0) {
        start = point to zoom
        view?.controller?.animateTo(OsmPoint(point.latitude, point.longitude), zoom, 600L)
    }

    fun center(): GeoPoint? = view?.mapCenter?.let { GeoPoint(it.latitude, it.longitude) }

    fun bounds(): MapBounds? = view?.boundingBox?.let { MapBounds(it.latSouth, it.lonWest, it.latNorth, it.lonEast) }
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

/** Map with [pins]. The caller shows [MAP_CREDIT]. */
@Composable
fun GymMap(state: GymMapState, pins: List<MapPin>, onPin: (MapPin) -> Unit, modifier: Modifier = Modifier) {
    val currentOnPin = rememberUpdatedState(onPin)
    val density = LocalContext.current.resources.displayMetrics.density
    val ownPin = remember { pinBitmap(Terracotta.toArgb(), density) }
    val foundPin = remember { pinBitmap(Ink.toArgb(), density) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            Configuration.getInstance().apply {
                // The tile servers ask every application to identify itself.
                userAgentValue = context.packageName
                osmdroidBasePath = File(context.cacheDir, "osmdroid")
                osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
            }
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                // Drawn larger than the screen needs: each view then shows a less detailed map.
                isTilesScaledToDpi = true
                tilesScaleFactor = 1.3f
                overlayManager.tilesOverlay.apply {
                    setColorFilter(PALE)
                    loadingBackgroundColor = PALE_BACKGROUND
                    loadingLineColor = PALE_BACKGROUND
                }
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                controller.setZoom(state.start.second)
                controller.setCenter(OsmPoint(state.start.first.latitude, state.start.first.longitude))
                state.view = this
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            for (pin in pins) {
                map.overlays.add(
                    Marker(map).apply {
                        position = OsmPoint(pin.point.latitude, pin.point.longitude)
                        title = pin.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        setInfoWindow(null)
                        icon = BitmapDrawable(map.resources, if (pin.own) ownPin else foundPin)
                        setOnMarkerClickListener { _, _ ->
                            currentOnPin.value(pin)
                            true
                        }
                    },
                )
            }
            map.invalidate()
        },
        onRelease = { map ->
            state.view = null
            map.onDetach()
        },
    )

    // The map stops loading tiles while the app is in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.view?.onResume()
                Lifecycle.Event.ON_PAUSE -> state.view?.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}
