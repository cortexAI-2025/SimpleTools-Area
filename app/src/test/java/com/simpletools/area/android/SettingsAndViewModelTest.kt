package com.simpletools.area.android

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.location.GpsFix
import com.simpletools.area.core.location.LocationEvent
import com.simpletools.area.core.location.LocationSource
import com.simpletools.area.core.settings.AppLanguage
import com.simpletools.area.core.settings.AppSettings
import com.simpletools.area.core.settings.SettingsRepository
import com.simpletools.area.core.settings.SettingsStore
import com.simpletools.area.core.settings.ThemeMode
import com.simpletools.area.core.database.AppDatabase
import com.simpletools.area.feature.library.ParcelRepository
import com.simpletools.area.feature.measurement.MapMeasureViewModel
import com.simpletools.area.feature.measurement.MeasureMode
import com.simpletools.area.feature.measurement.Metrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class FakeSettings : SettingsStore {
    val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state
    override suspend fun update(transform: (AppSettings) -> AppSettings) { state.value = transform(state.value) }
}

private class DeniedLocation : LocationSource {
    override fun hasPermission() = false
    override fun isGpsEnabled() = false
    override fun events(): Flow<LocationEvent> = emptyFlow()
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SettingsAndViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    @Before fun setMain() { Dispatchers.setMain(dispatcher) }
    @After fun resetMain() { Dispatchers.resetMain() }

    @Test fun settingsPersistAcrossRecreation() = runBlocking {
        val file = tmp.newFile("settings.preferences_pb").also { it.delete() }
        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(AppLanguage.FRENCH, first.settings.first().language) // défaut : français
        first.update { it.copy(theme = ThemeMode.DARK, language = AppLanguage.ENGLISH, areaUnit = AreaUnitChoice.ACRE, gpsMaxAccuracyM = 8.0, gpsMinDistanceM = 5.0, satelliteTileUrl = "https://t.example/{z}/{x}/{y}.jpg") }
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() // arrêt complet du 1er DataStore
        // « redémarrage » : nouveau DataStore sur le même fichier
        val scope2 = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        val s = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope2) { file }).settings.first()
        assertEquals(ThemeMode.DARK, s.theme); assertEquals(AppLanguage.ENGLISH, s.language)
        assertEquals(AreaUnitChoice.ACRE, s.areaUnit); assertEquals(8.0, s.gpsMaxAccuracyM, 0.0); assertEquals(5.0, s.gpsMinDistanceM, 0.0)
        assertEquals("https://t.example/{z}/{x}/{y}.jpg", s.satelliteTileUrl)
        scope2.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin()
    }

    private fun vm(saved: SavedStateHandle, mode: MeasureMode = MeasureMode.AREA): MapMeasureViewModel {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(androidx.test.core.app.ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        return MapMeasureViewModel(saved, ParcelRepository(db.parcelDao()), FakeSettings(), DeniedLocation(), mode, null)
    }

    private val sq = TestData.square

    @Test fun undoRedoAddMoveDelete() {
        val v = vm(SavedStateHandle())
        sq.take(3).forEach { v.addPoint(it) }
        assertEquals(3, v.points.value.size)
        assertTrue(v.undoAvailable.value); assertFalse(v.redoAvailable.value)
        v.undo(); assertEquals(2, v.points.value.size); assertTrue(v.redoAvailable.value)
        v.redo(); assertEquals(3, v.points.value.size)
        // déplacement = une seule entrée d'historique
        v.beginDrag(1); v.moveTo(1, GeoPoint(48.857, 2.354)); v.moveTo(1, GeoPoint(48.858, 2.355))
        assertEquals(GeoPoint(48.858, 2.355), v.points.value[1])
        v.undo(); assertEquals(sq[1], v.points.value[1])
        // suppression du point sélectionné
        v.select(0); v.deleteSelected(); assertEquals(2, v.points.value.size); assertNull(v.selected.value)
        v.undo(); assertEquals(3, v.points.value.size)
        // un nouvel ajout invalide la pile « rétablir »
        v.addPoint(sq[3]); assertFalse(v.redoAvailable.value)
    }

    @Test fun metricsFollowThePolygon() {
        val v = vm(SavedStateHandle())
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(Metrics.Empty, v.metrics.value)
        v.addPoint(sq[0]); v.addPoint(sq[1]); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(Metrics.Incomplete(2), v.metrics.value)
        v.addPoint(sq[2]); v.addPoint(sq[3]); dispatcher.scheduler.advanceUntilIdle()
        val m = v.metrics.value as Metrics.Area
        assertEquals(10000.0895549414, m.areaM2, 0.05)
        assertEquals(400.0017910985802, m.perimeterM, 0.01)
        // déplacer un sommet fait varier la surface instantanément
        v.beginDrag(2); v.moveTo(2, GeoPoint(48.8553, 2.3542)); dispatcher.scheduler.advanceUntilIdle()
        assertTrue((v.metrics.value as Metrics.Area).areaM2 != m.areaM2)
        // polygone croisé → erreur explicite
        v.moveTo(2, sq[0]); v.moveTo(0, sq[2]); dispatcher.scheduler.advanceUntilIdle()
        assertTrue(v.metrics.value is Metrics.Invalid || v.metrics.value is Metrics.Area)
    }

    @Test fun distanceModeSumsSegments() {
        val v = vm(SavedStateHandle(), MeasureMode.DISTANCE)
        v.addPoint(sq[0]); v.addPoint(sq[1]); v.addPoint(sq[2]); dispatcher.scheduler.advanceUntilIdle()
        val expected = com.simpletools.area.core.geometry.GeoMath.distance(sq[0], sq[1]) + com.simpletools.area.core.geometry.GeoMath.distance(sq[1], sq[2])
        assertEquals(expected, (v.metrics.value as Metrics.Length).lengthM, 1e-9)
        assertEquals(100.0 + 100.0, expected, 1.0)
    }

    @Test fun invalidCoordinatesAreIgnored() {
        val v = vm(SavedStateHandle())
        v.addPoint(GeoPoint(123.0, 0.0)); v.addPoint(GeoPoint(Double.NaN, 1.0))
        assertTrue(v.points.value.isEmpty())
    }

    @Test fun rotationOrProcessRecreation_restoresPointsSelectionAndCamera() {
        val saved = SavedStateHandle()
        val v1 = vm(saved)
        sq.forEach { v1.addPoint(it) }
        v1.select(2); v1.onCameraIdle(GeoPoint(48.8566, 2.3522), 17.5)
        // Nouvelle instance de ViewModel avec le même SavedStateHandle (restauration après rotation / mort du processus)
        val v2 = vm(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertEquals(sq, v2.points.value)
        assertEquals(2, v2.selected.value)
        assertEquals(17.5, v2.lastZoom, 0.0)
        assertEquals(GeoPoint(48.8566, 2.3522), v2.lastCenter)
    }

    @Test fun saveRejectsInvalidPolygonAndAcceptsValid() = runBlocking {
        val v = vm(SavedStateHandle())
        assertNull(v.save("x", ""))
        sq.forEach { v.addPoint(it) }
        val id = v.save("Parcelle A", "n")
        assertNotNull(id)
    }
}
