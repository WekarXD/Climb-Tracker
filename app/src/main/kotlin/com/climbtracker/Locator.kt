package com.climbtracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.climbtracker.core.tracker.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Where the phone is, and how many metres off that may be. */
data class Fix(val point: GeoPoint, val accuracy: Double)

/** Reads the phone's position, used only to tell which gym it is at. Nothing is sent anywhere. */
class Locator(private val context: Context) {

    val permitted: Boolean
        get() = PERMISSIONS.any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Null without permission, with location switched off, or with no fix within [timeoutMillis]. */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMillis: Long = 4000): Fix? {
        if (!permitted) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return try {
            val providers = manager.getProviders(true)
            val known = providers.mapNotNull { manager.getLastKnownLocation(it) }
            known.filter { ageMillis(it) < FRESH_MILLIS }.minByOrNull { it.accuracy }?.let { return it.toFix() }

            val provider = PREFERRED.firstOrNull { it in providers }
            // The compat call also asks for a new fix on Android 8 to 10, which lack getCurrentLocation.
            val fresh = if (provider != null) {
                withTimeoutOrNull(timeoutMillis) {
                    suspendCancellableCoroutine<Location?> { continuation ->
                        val signal = CancellationSignal()
                        continuation.invokeOnCancellation { signal.cancel() }
                        LocationManagerCompat.getCurrentLocation(manager, provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }
                }
            } else {
                null
            }
            // Better an older position than none: gyms do not move, and people do not get far in a while.
            (fresh ?: known.filter { ageMillis(it) < STALE_MILLIS }.minByOrNull { ageMillis(it) })?.toFix()
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun ageMillis(location: Location) = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

    private fun Location.toFix() = Fix(GeoPoint(latitude, longitude), if (hasAccuracy()) accuracy.toDouble() else 0.0)

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        private val PREFERRED = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        private const val FRESH_MILLIS = 2 * 60_000L
        private const val STALE_MILLIS = 30 * 60_000L
    }
}
