package com.simpletools.area.core.location

import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.geometry.PolygonResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RecorderStatus { IDLE, RECORDING, PAUSED, FINISHED }

/** Résultat d'une tentative de démarrage. */
enum class StartResult { STARTED, PERMISSION_DENIED, GPS_DISABLED }

data class RecorderState(
    val status: RecorderStatus = RecorderStatus.IDLE,
    val points: List<GeoPoint> = emptyList(),
    val lastAccuracyM: Float? = null,
    val lastFix: GeoPoint? = null,
    val distanceM: Double = 0.0,
    /** Surface si le tracé fermé est un polygone valide (≥ 3 points, sans auto-intersection). */
    val areaM2: Double? = null,
    val perimeterM: Double? = null,
    val rejected: Int = 0,
    val lastReject: RejectReason? = null,
    val providerEnabled: Boolean = true,
    val lastError: GpsErrorHint? = null,
)

enum class GpsErrorHint { SELF_INTERSECTING }

/**
 * Machine d'état d'enregistrement GPS, indépendante d'Android (testable en JVM).
 * Les fixes sont filtrées par [TrackFilter] ; en pause elles servent uniquement à afficher la précision.
 */
class GpsRecorder(initialFilter: TrackFilter = TrackFilter()) {
    private var filter = initialFilter
    private var lastAcceptedTime: Long? = null
    private val _state = MutableStateFlow(RecorderState())
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    fun configure(filter: TrackFilter) {
        this.filter = filter
    }

    fun start(permissionGranted: Boolean, providerEnabled: Boolean): StartResult {
        if (!permissionGranted) return StartResult.PERMISSION_DENIED
        if (!providerEnabled) {
            _state.update { it.copy(providerEnabled = false) }
            return StartResult.GPS_DISABLED
        }
        lastAcceptedTime = null
        _state.value = RecorderState(status = RecorderStatus.RECORDING, providerEnabled = true)
        return StartResult.STARTED
    }

    fun pause() = _state.update { if (it.status == RecorderStatus.RECORDING) it.copy(status = RecorderStatus.PAUSED) else it }

    fun resume() = _state.update { if (it.status == RecorderStatus.PAUSED) it.copy(status = RecorderStatus.RECORDING) else it }

    fun setProviderEnabled(enabled: Boolean) = _state.update { it.copy(providerEnabled = enabled) }

    fun onFix(fix: GpsFix) {
        _state.update { s ->
            if (s.status != RecorderStatus.RECORDING && s.status != RecorderStatus.PAUSED) return@update s
            val base = s.copy(
                lastAccuracyM = fix.accuracyM,
                lastFix = if (fix.point.isValid) fix.point else s.lastFix,
                providerEnabled = true,
            )
            if (s.status == RecorderStatus.PAUSED) return@update base
            when (val d = filter.evaluate(s.points.lastOrNull(), lastAcceptedTime, fix)) {
                is TrackFilter.Decision.Reject -> base.copy(rejected = s.rejected + 1, lastReject = d.reason)
                TrackFilter.Decision.Accept -> {
                    lastAcceptedTime = fix.timeMs
                    val pts = s.points + fix.point
                    withGeometry(base.copy(points = pts, lastReject = null))
                }
            }
        }
    }

    private fun withGeometry(s: RecorderState): RecorderState {
        val dist = GeoMath.pathLengthM(s.points)
        if (s.points.size < 3) return s.copy(distanceM = dist, areaM2 = null, perimeterM = null, lastError = null)
        return when (val r = GeoMath.validatePolygon(s.points)) {
            is PolygonResult.Valid -> s.copy(distanceM = dist, areaM2 = r.areaM2, perimeterM = r.perimeterM, lastError = null)
            is PolygonResult.Invalid -> s.copy(
                distanceM = dist, areaM2 = null, perimeterM = null,
                lastError = GpsErrorHint.SELF_INTERSECTING,
            )
        }
    }

    /** Termine l'enregistrement et ferme le polygone. */
    fun finish(): PolygonResult? {
        val s = _state.value
        if (s.status != RecorderStatus.RECORDING && s.status != RecorderStatus.PAUSED) return null
        _state.update { it.copy(status = RecorderStatus.FINISHED) }
        return GeoMath.validatePolygon(s.points)
    }

    fun reset() {
        lastAcceptedTime = null
        _state.value = RecorderState()
    }
}
