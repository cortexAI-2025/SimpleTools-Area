package com.simpletools.area.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface LocationEvent {
    data class Fix(val fix: GpsFix) : LocationEvent
    data class ProviderEnabled(val enabled: Boolean) : LocationEvent
}

interface LocationSource {
    fun hasPermission(): Boolean
    fun isGpsEnabled(): Boolean
    fun events(): Flow<LocationEvent>
}

/** Source basée sur LocationManager (fonctionne hors connexion, sans services tiers). */
class AndroidLocationSource(private val context: Context) : LocationSource {
    private val manager get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun isGpsEnabled() = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    override fun events(): Flow<LocationEvent> = callbackFlow {
        if (!hasPermission()) {
            close(SecurityException("Permission de localisation absente"))
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val acc = if (location.hasAccuracy()) location.accuracy else null
                trySend(LocationEvent.Fix(GpsFix(location.latitude, location.longitude, acc, location.time)))
            }
            override fun onProviderEnabled(provider: String) { trySend(LocationEvent.ProviderEnabled(true)) }
            override fun onProviderDisabled(provider: String) { trySend(LocationEvent.ProviderEnabled(false)) }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        awaitClose { manager.removeUpdates(listener) }
    }
}
