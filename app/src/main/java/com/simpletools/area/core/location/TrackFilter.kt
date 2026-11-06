package com.simpletools.area.core.location

import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint

/**
 * Filtre des positions GPS.
 *  - précision absente ou supérieure à [maxAccuracyM] → rejet (une position imprécise n'est pas une mesure fiable)
 *  - déplacement inférieur à max([minDistanceM], précision de la fix) → rejet (bruit à l'arrêt)
 *  - vitesse implicite supérieure à [maxSpeedMps] → rejet (saut aberrant ; un piéton marche à ~1,5 m/s)
 */
class TrackFilter(
    val maxAccuracyM: Double = 15.0,
    val minDistanceM: Double = 3.0,
    val maxSpeedMps: Double = 12.0,
) {
    sealed interface Decision {
        data object Accept : Decision
        data class Reject(val reason: RejectReason) : Decision
    }

    fun evaluate(last: GeoPoint?, lastTimeMs: Long?, fix: GpsFix): Decision {
        if (!fix.point.isValid) return Decision.Reject(RejectReason.INVALID)
        val acc = fix.accuracyM ?: return Decision.Reject(RejectReason.NO_ACCURACY)
        if (acc.isNaN() || acc <= 0f) return Decision.Reject(RejectReason.NO_ACCURACY)
        if (acc > maxAccuracyM) return Decision.Reject(RejectReason.POOR_ACCURACY)
        if (last == null) return Decision.Accept
        val d = GeoMath.distance(last, fix.point)
        val dtSec = if (lastTimeMs != null) (fix.timeMs - lastTimeMs) / 1000.0 else Double.NaN
        if (dtSec > 0 && d / dtSec > maxSpeedMps && d > 2 * acc) return Decision.Reject(RejectReason.OUTLIER_JUMP)
        if (d < minDistanceM) return Decision.Reject(RejectReason.TOO_CLOSE)
        return Decision.Accept
    }
}
