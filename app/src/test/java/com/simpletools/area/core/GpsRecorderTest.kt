package com.simpletools.area.core

import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.geometry.PolygonError
import com.simpletools.area.core.geometry.PolygonResult
import com.simpletools.area.core.location.GpsFix
import com.simpletools.area.core.location.GpsRecorder
import com.simpletools.area.core.location.RecorderStatus
import com.simpletools.area.core.location.RejectReason
import com.simpletools.area.core.location.StartResult
import com.simpletools.area.core.location.TrackFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsRecorderTest {
    // ~ 0,00009° de latitude ≈ 10 m
    private fun fix(dLat: Double, dLon: Double, acc: Float? = 5f, t: Long) = GpsFix(45.0 + dLat, 5.0 + dLon, acc, t)
    private fun started(filter: TrackFilter = TrackFilter()) = GpsRecorder(filter).also {
        assertEquals(StartResult.STARTED, it.start(permissionGranted = true, providerEnabled = true))
    }

    @Test fun permissionDenied_doesNotStart() {
        val r = GpsRecorder()
        assertEquals(StartResult.PERMISSION_DENIED, r.start(permissionGranted = false, providerEnabled = true))
        assertEquals(RecorderStatus.IDLE, r.state.value.status)
    }

    @Test fun gpsUnavailable_doesNotStart_andIsReported() {
        val r = GpsRecorder()
        assertEquals(StartResult.GPS_DISABLED, r.start(permissionGranted = true, providerEnabled = false))
        assertEquals(RecorderStatus.IDLE, r.state.value.status)
        assertEquals(false, r.state.value.providerEnabled)
    }

    @Test fun providerLossDuringRecording_isFlagged_thenRecovers() {
        val r = started()
        r.setProviderEnabled(false)
        assertEquals(false, r.state.value.providerEnabled)
        r.onFix(fix(0.0, 0.0, t = 1000))
        assertEquals(true, r.state.value.providerEnabled)
    }

    @Test fun poorAccuracyIsNotARecordedMeasure() {
        val r = started(TrackFilter(maxAccuracyM = 10.0))
        r.onFix(fix(0.0, 0.0, acc = 25f, t = 1000))
        r.onFix(fix(0.0, 0.0, acc = null, t = 2000))
        assertTrue(r.state.value.points.isEmpty())
        assertEquals(2, r.state.value.rejected)
        r.onFix(fix(0.0, 0.0, acc = 8f, t = 3000))
        assertEquals(1, r.state.value.points.size)
        assertEquals(8f, r.state.value.lastAccuracyM)
    }

    @Test fun minimumDistanceThresholdIsConfigurable() {
        val r = started(TrackFilter(minDistanceM = 5.0))
        r.onFix(fix(0.0, 0.0, t = 0))
        r.onFix(fix(0.00002, 0.0, t = 1000)) // ~2,2 m → ignoré
        assertEquals(1, r.state.value.points.size)
        assertEquals(RejectReason.TOO_CLOSE, r.state.value.lastReject)
        r.configure(TrackFilter(minDistanceM = 1.0))
        r.onFix(fix(0.00002, 0.0, t = 2000)) // accepté avec seuil 1 m
        assertEquals(2, r.state.value.points.size)
    }

    @Test fun outlierJumpIsRejected() {
        val r = started()
        r.onFix(fix(0.0, 0.0, t = 0))
        r.onFix(fix(0.01, 0.0, t = 1000)) // ~1,1 km en 1 s
        assertEquals(1, r.state.value.points.size)
        assertEquals(RejectReason.OUTLIER_JUMP, r.state.value.lastReject)
    }

    @Test fun pauseIgnoresPositionsButTracksAccuracy_resumeContinues() {
        val r = started()
        r.onFix(fix(0.0, 0.0, t = 0))
        r.pause()
        r.onFix(fix(0.0002, 0.0, acc = 4f, t = 10_000))
        assertEquals(1, r.state.value.points.size)
        assertEquals(4f, r.state.value.lastAccuracyM)
        assertEquals(RecorderStatus.PAUSED, r.state.value.status)
        r.resume()
        r.onFix(fix(0.0002, 0.0, t = 20_000))
        assertEquals(2, r.state.value.points.size)
    }

    @Test fun walkingARectangle_finishCloseAndMeasure() {
        val r = started(TrackFilter(minDistanceM = 3.0))
        // rectangle ≈ 100 m (N-S) × 100 m (E-O), un point tous les ~10 m
        val step = 0.00009
        val lonStep = 0.000127
        var t = 0L
        fun go(dLat: Double, dLon: Double) { r.onFix(fix(dLat, dLon, t = t)); t += 8000 }
        for (i in 0..10) go(0.0, i * lonStep)
        for (i in 1..10) go(i * step, 10 * lonStep)
        for (i in 1..10) go(10 * step, (10 - i) * lonStep)
        for (i in 1..9) go((10 - i) * step, 0.0)
        val s = r.state.value
        assertTrue(s.points.size > 30)
        assertTrue("distance ${s.distanceM}", s.distanceM in 350.0..400.0)
        val area = s.areaM2!!
        assertTrue("aire $area", area in 9000.0..11000.0)
        val res = r.finish() as PolygonResult.Valid
        assertEquals(RecorderStatus.FINISHED, r.state.value.status)
        assertEquals(area, res.areaM2, 1e-6)
        assertTrue(res.perimeterM in 380.0..420.0)
        // un enregistrement terminé n'accepte plus de positions
        val n = r.state.value.points.size
        r.onFix(fix(0.5, 0.5, t = t + 1000))
        assertEquals(n, r.state.value.points.size)
    }

    @Test fun finishWithTooFewPointsIsInvalid() {
        val r = started()
        r.onFix(fix(0.0, 0.0, t = 0))
        r.onFix(fix(0.0002, 0.0, t = 10_000))
        val res = r.finish()
        assertEquals(PolygonResult.Invalid(PolygonError.TOO_FEW_POINTS), res)
    }

    @Test fun selfCrossingTrackIsFlaggedNotMeasured() {
        val r = started(TrackFilter(minDistanceM = 1.0, maxSpeedMps = 1000.0))
        val pts = listOf(0.0 to 0.0, 0.001 to 0.001, 0.0 to 0.001, 0.001 to 0.0)
        pts.forEachIndexed { i, (a, b) -> r.onFix(fix(a, b, t = i * 100_000L)) }
        assertNull(r.state.value.areaM2)
        assertEquals(com.simpletools.area.core.location.GpsErrorHint.SELF_INTERSECTING, r.state.value.lastError)
    }

    @Test fun stateSurvivesUiRecreation_singletonRecorder() {
        // La session vit dans un objet process (GpsSession) : un nouvel écran (rotation) relit le même état.
        val r = started()
        r.onFix(fix(0.0, 0.0, t = 0)); r.onFix(fix(0.0002, 0.0, t = 10_000))
        val reader = r.state // « nouveau ViewModel » après rotation
        assertEquals(2, reader.value.points.size)
        assertEquals(RecorderStatus.RECORDING, reader.value.status)
        r.reset()
        assertEquals(RecorderStatus.IDLE, reader.value.status)
        assertTrue(reader.value.points.isEmpty())
    }

    @Test fun invalidFixIsRejected() {
        val r = started()
        r.onFix(GpsFix(200.0, 0.0, 5f, 0))
        assertTrue(r.state.value.points.isEmpty())
        assertEquals(RejectReason.INVALID, r.state.value.lastReject)
        assertEquals(GeoPoint(0.0, 0.0).isValid, true)
    }
}
