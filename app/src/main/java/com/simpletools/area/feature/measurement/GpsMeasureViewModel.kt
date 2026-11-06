package com.simpletools.area.feature.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.PolygonError
import com.simpletools.area.core.geometry.PolygonResult
import com.simpletools.area.core.location.GpsRecorder
import com.simpletools.area.core.location.LocationSource
import com.simpletools.area.core.location.RecorderState
import com.simpletools.area.core.location.StartResult
import com.simpletools.area.core.location.TrackFilter
import com.simpletools.area.core.settings.AppSettings
import com.simpletools.area.core.settings.SettingsStore
import com.simpletools.area.feature.library.ParcelRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GpsUiEvent {
    data object None : GpsUiEvent
    data object PermissionDenied : GpsUiEvent
    data object GpsDisabled : GpsUiEvent
    data class Invalid(val error: PolygonError) : GpsUiEvent
}

/** Pilote la session GPS (recorder partagé avec le service de premier plan). */
class GpsMeasureViewModel(
    private val recorder: GpsRecorder,
    private val repo: ParcelRepository,
    private val settingsStore: SettingsStore,
    private val locationSource: LocationSource,
    private val startService: () -> Unit,
    private val stopService: () -> Unit,
) : ViewModel() {

    val state: StateFlow<RecorderState> = recorder.state
    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _event = MutableStateFlow<GpsUiEvent>(GpsUiEvent.None)
    val event: StateFlow<GpsUiEvent> = _event

    private val _result = MutableStateFlow<PolygonResult.Valid?>(null)
    /** Résultat valide après « Terminer » (en attente d'enregistrement). */
    val result: StateFlow<PolygonResult.Valid?> = _result

    fun hasPermission() = locationSource.hasPermission()

    fun consumeEvent() { _event.value = GpsUiEvent.None }

    fun start() {
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            recorder.configure(TrackFilter(maxAccuracyM = s.gpsMaxAccuracyM, minDistanceM = s.gpsMinDistanceM))
            _result.value = null
            when (recorder.start(locationSource.hasPermission(), locationSource.isGpsEnabled())) {
                StartResult.STARTED -> { startService(); _event.value = GpsUiEvent.None }
                StartResult.PERMISSION_DENIED -> _event.value = GpsUiEvent.PermissionDenied
                StartResult.GPS_DISABLED -> _event.value = GpsUiEvent.GpsDisabled
            }
        }
    }

    fun pause() = recorder.pause()
    fun resume() = recorder.resume()

    fun finish() {
        when (val r = recorder.finish()) {
            is PolygonResult.Valid -> _result.value = r
            is PolygonResult.Invalid -> _event.value = GpsUiEvent.Invalid(r.error)
            null -> {}
        }
        stopService()
    }

    fun discard() {
        stopService(); recorder.reset(); _result.value = null; _event.value = GpsUiEvent.None
    }

    suspend fun save(name: String, notes: String, unit: AreaUnitChoice): Long? {
        val r = _result.value ?: return null
        val id = repo.save(
            ParcelEntity(
                name = name, createdAt = 0, updatedAt = 0, type = MeasureType.GPS, points = r.points,
                areaM2 = r.areaM2, perimeterM = r.perimeterM, displayUnit = unit.name, notes = notes,
            ),
        )
        recorder.reset(); _result.value = null
        return id
    }
}
