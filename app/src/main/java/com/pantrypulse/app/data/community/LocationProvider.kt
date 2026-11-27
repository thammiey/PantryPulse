package com.pantrypulse.app.data.community

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LatLon(val lat: Double, val lon: Double)

/** Current device location without Google Play services. Tests replace it with a fixed point. */
interface LocationProvider {
    fun hasPermission(context: Context): Boolean
    suspend fun current(context: Context): LatLon?
    /** False when the phone's location switch is off. Tests keep the default (always on). */
    fun isEnabled(context: Context): Boolean = true
}

class DeviceLocationProvider : LocationProvider {
    override fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isEnabled(context: Context): Boolean =
        androidx.core.location.LocationManagerCompat.isLocationEnabled(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

    @SuppressLint("MissingPermission")
    override suspend fun current(context: Context): LatLon? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        fun lastKnown() = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        lastKnown()?.takeIf { System.currentTimeMillis() - it.time < 30 * 60 * 1000 }?.let { return LatLon(it.latitude, it.longitude) }
        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> providers.firstOrNull() ?: return null
        }
        val fresh = withTimeoutOrNull(15_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                } else {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, { cont.resume(it) }, context.mainLooper)
                }
            }
        }
        return (fresh ?: lastKnown())?.let { LatLon(it.latitude, it.longitude) }
    }
}
