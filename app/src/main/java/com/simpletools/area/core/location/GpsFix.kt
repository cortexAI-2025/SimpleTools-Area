package com.simpletools.area.core.location

import com.simpletools.area.core.geometry.GeoPoint

/** Position GPS brute. [accuracyM] est le rayon d'incertitude estimé à 68 % (null si inconnu). */
data class GpsFix(val lat: Double, val lon: Double, val accuracyM: Float?, val timeMs: Long) {
    val point get() = GeoPoint(lat, lon)
}

enum class RejectReason { INVALID, NO_ACCURACY, POOR_ACCURACY, TOO_CLOSE, OUTLIER_JUMP }
