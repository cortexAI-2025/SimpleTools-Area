package com.simpletools.area.feature.measurement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.area.core.database.Converters
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.geometry.PolygonError
import com.simpletools.area.core.geometry.PolygonResult
import com.simpletools.area.core.location.LocationEvent
import com.simpletools.area.core.location.LocationSource
import com.simpletools.area.core.maps.CameraRequest
import com.simpletools.area.core.settings.AppSettings
import com.simpletools.area.core.settings.SettingsStore
import com.simpletools.area.feature.library.ParcelRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MeasureMode { AREA, DISTANCE }

sealed interface Metrics {
    data object Empty : Metrics
    /** Surface : moins de 3 points. */
    data class Incomplete(val count: Int) : Metrics
    data class Area(val areaM2: Double, val perimeterM: Double) : Metrics
    data class Length(val lengthM: Double) : Metrics
    data class Invalid(val error: PolygonError) : Metrics
}

/**
 * État du dessin sur carte. Les points survivent à la rotation (ViewModel) et à la mort du processus
 * (SavedStateHandle).
 */
class MapMeasureViewModel(
    private val saved: SavedStateHandle,
    private val repo: ParcelRepository,
    settingsStore: SettingsStore,
    private val locationSource: LocationSource,
    val mode: MeasureMode,
    private val editParcelId: Long?,
) : ViewModel() {

    private val _points = MutableStateFlow(Converters.decodePoints(saved.get<String>(KEY_POINTS) ?: ""))
    val points: StateFlow<List<GeoPoint>> = _points

    private val undoStack = ArrayDeque<List<GeoPoint>>()
    private val redoStack = ArrayDeque<List<GeoPoint>>()
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)
    val undoAvailable: StateFlow<Boolean> get() = _canUndo
    val redoAvailable: StateFlow<Boolean> get() = _canRedo

    private val _selected = MutableStateFlow<Int?>(saved.get<Int>(KEY_SEL))
    val selected: StateFlow<Int?> = _selected

    private val _camera = MutableStateFlow<CameraRequest?>(null)
    val cameraRequest: StateFlow<CameraRequest?> = _camera
    private var cameraId = 0

    /** Dernier centre/zoom de la carte (restauré après rotation). */
    var lastCenter: GeoPoint = GeoPoint(saved.get<Double>(KEY_LAT) ?: 46.6, saved.get<Double>(KEY_LON) ?: 2.4)
        private set
    var lastZoom: Double = saved.get<Double>(KEY_ZOOM) ?: 5.0
        private set

    private val _userLocation = MutableStateFlow<GeoPoint?>(null)
    val userLocation: StateFlow<GeoPoint?> = _userLocation
    private var locationJob: Job? = null

    private val _satellite = MutableStateFlow(saved.get<Boolean>(KEY_SAT) ?: false)
    val satellite: StateFlow<Boolean> = _satellite

    private val _unit = MutableStateFlow<AreaUnitChoice?>(null)

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val areaUnit: StateFlow<AreaUnitChoice> = combine(_unit, settings) { u, s -> u ?: s.areaUnit }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AreaUnitChoice.AUTO)

    val metrics: StateFlow<Metrics> = combine(_points) { arr -> compute(arr[0]) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, compute(_points.value))

    init {
        if (editParcelId != null && _points.value.isEmpty() && saved.get<Boolean>(KEY_LOADED) != true) {
            viewModelScope.launch {
                repo.get(editParcelId)?.let { p ->
                    _points.value = p.points
                    persist()
                    requestFit()
                }
                saved[KEY_LOADED] = true
            }
        }
    }

    private fun compute(pts: List<GeoPoint>): Metrics = when {
        pts.isEmpty() -> Metrics.Empty
        mode == MeasureMode.DISTANCE -> Metrics.Length(GeoMath.pathLengthM(pts))
        pts.size < 3 -> Metrics.Incomplete(pts.size)
        else -> when (val r = GeoMath.validatePolygon(pts)) {
            is PolygonResult.Valid -> Metrics.Area(r.areaM2, r.perimeterM)
            is PolygonResult.Invalid -> Metrics.Invalid(r.error)
        }
    }

    // ------------------------------------------------------------------ édition

    private fun persist() {
        saved[KEY_POINTS] = Converters.encodePoints(_points.value)
        saved[KEY_SEL] = _selected.value
    }

    private fun pushHistory() {
        undoStack.addLast(_points.value)
        if (undoStack.size > 200) undoStack.removeFirst()
        redoStack.clear()
        refreshHistory()
    }

    private fun refreshHistory() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    fun addPoint(p: GeoPoint) {
        if (!p.isValid) return
        pushHistory()
        _points.update { it + p }
        _selected.value = _points.value.lastIndex
        persist()
    }

    fun addAtCenter() = addPoint(lastCenter)

    fun select(i: Int?) { _selected.value = i; persist() }

    fun beginDrag(i: Int) { pushHistory(); _selected.value = i }

    fun moveTo(i: Int, p: GeoPoint) {
        if (!p.isValid) return
        _points.update { l -> if (i in l.indices) l.toMutableList().also { it[i] = p } else l }
        persist()
    }

    fun deleteSelected() {
        val i = _selected.value ?: return
        if (i !in _points.value.indices) return
        pushHistory()
        _points.update { it.toMutableList().also { l -> l.removeAt(i) } }
        _selected.value = null
        persist()
    }

    fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_points.value)
        _points.value = prev
        _selected.value = null
        refreshHistory(); persist()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_points.value)
        _points.value = next
        _selected.value = null
        refreshHistory(); persist()
    }

    fun clear() {
        if (_points.value.isEmpty()) return
        pushHistory()
        _points.value = emptyList(); _selected.value = null; persist()
    }

    // ------------------------------------------------------------------ carte / position

    fun onCameraIdle(center: GeoPoint, zoom: Double) {
        lastCenter = center; lastZoom = zoom
        saved[KEY_LAT] = center.lat; saved[KEY_LON] = center.lon; saved[KEY_ZOOM] = zoom
    }

    private fun requestFit() {
        val pts = _points.value
        _camera.value = if (pts.size >= 2) CameraRequest(++cameraId, fitPoints = pts)
        else pts.firstOrNull()?.let { CameraRequest(++cameraId, target = it) }
    }

    fun hasLocationPermission() = locationSource.hasPermission()

    fun recenterOnUser() {
        _userLocation.value?.let { _camera.value = CameraRequest(++cameraId, target = it) }
    }

    fun zoomToShape() = requestFit()

    fun toggleSatellite() { _satellite.update { !it }; saved[KEY_SAT] = _satellite.value }

    fun setUnit(u: AreaUnitChoice) { _unit.value = u }

    /** Démarre l'écoute de la position (point bleu + recentrage). Sans effet si la permission manque. */
    fun startLocation() {
        if (locationJob?.isActive == true || !locationSource.hasPermission()) return
        var first = _userLocation.value == null && _points.value.isEmpty()
        locationJob = viewModelScope.launch {
            locationSource.events().catch { }.collect { e ->
                if (e is LocationEvent.Fix && e.fix.point.isValid) {
                    _userLocation.value = e.fix.point
                    if (first) { first = false; _camera.value = CameraRequest(++cameraId, target = e.fix.point, zoom = 17.0) }
                }
            }
        }
    }

    fun stopLocation() { locationJob?.cancel(); locationJob = null }

    override fun onCleared() { stopLocation() }

    // ------------------------------------------------------------------ enregistrement

    /** Nom suggéré (nom de la parcelle en édition ou valeur fournie par l'UI). */
    suspend fun existingParcel(): ParcelEntity? = editParcelId?.let { repo.get(it) }

    /** Enregistre la mesure ; renvoie l'identifiant ou null si la géométrie est invalide. */
    suspend fun save(name: String, notes: String): Long? {
        val unit = areaUnit.value.name
        val pts = _points.value
        return when (mode) {
            MeasureMode.AREA -> {
                val r = GeoMath.validatePolygon(pts) as? PolygonResult.Valid ?: return null
                persistParcel(name, notes, MeasureType.MAP, r.points, r.areaM2, r.perimeterM, unit)
            }
            MeasureMode.DISTANCE -> {
                if (pts.size < 2) return null
                persistParcel(name, notes, MeasureType.DISTANCE, pts, 0.0, GeoMath.pathLengthM(pts), unit)
            }
        }
    }

    private suspend fun persistParcel(
        name: String, notes: String, type: MeasureType, pts: List<GeoPoint>, area: Double, per: Double, unit: String,
    ): Long {
        val existing = existingParcel()
        return if (existing != null) {
            repo.update(existing.copy(name = name, notes = notes, points = pts, areaM2 = area, perimeterM = per, displayUnit = unit))
            existing.id
        } else {
            repo.save(ParcelEntity(name = name, createdAt = 0, updatedAt = 0, type = type, points = pts, areaM2 = area, perimeterM = per, displayUnit = unit, notes = notes))
        }
    }

    companion object {
        const val KEY_POINTS = "points"
        const val KEY_SEL = "selected"
        const val KEY_LAT = "cam_lat"
        const val KEY_LON = "cam_lon"
        const val KEY_ZOOM = "cam_zoom"
        const val KEY_SAT = "satellite"
        const val KEY_LOADED = "loaded"
    }
}
