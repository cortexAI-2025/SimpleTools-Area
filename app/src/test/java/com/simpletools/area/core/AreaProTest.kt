package com.simpletools.area.core

import com.simpletools.area.core.billing.offlineReceiptIsFresh
import com.simpletools.area.core.billing.verifyReceipt
import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.geometry.PolygonResult
import com.simpletools.area.core.location.GpsFix
import com.simpletools.area.core.location.GpsRecorder
import com.simpletools.area.feature.projects.AreaProject
import com.simpletools.area.feature.projects.divide
import com.simpletools.area.feature.projects.projectKml
import com.simpletools.area.feature.projects.projectParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class AreaProTest {
    private fun ring(min: Double, max: Double) =
        listOf(GeoPoint(min, min), GeoPoint(min, max), GeoPoint(max, max), GeoPoint(max, min))

    @Test fun netAreaAndPerimetersIncludeCorrectBoundaries() {
        val outer = ring(0.0, 0.01)
        val hole = ring(0.001, 0.002)
        val parcel = projectParcel("A & B", "Culture", outer, listOf(hole))
        assertEquals(GeoMath.areaM2(outer) - GeoMath.areaM2(hole), parcel.netAreaM2, 1e-6)
        assertEquals(GeoMath.perimeterM(outer), parcel.fenceM, 1e-6)
        assertEquals(parcel.fenceM + GeoMath.perimeterM(hole), parcel.perimeterM, 1e-6)
        val project = AreaProject("id", "Project", listOf(parcel, parcel))
        assertEquals(parcel.netAreaM2 * 2, project.areaM2, 1e-6)
        assertEquals(parcel.perimeterM * 2, project.perimeterM, 1e-6)
        val kml = projectKml(project)
        assertTrue(kml.contains("<innerBoundaryIs>"))
        assertTrue(kml.contains("A &amp; B"))
    }

    @Test fun divisionReturnsTwoEqualBandsAcrossAntimeridian() {
        val outer = listOf(GeoPoint(10.0, 179.99), GeoPoint(10.0, -179.99),
            GeoPoint(10.01, -179.99), GeoPoint(10.01, 179.99))
        val parcel = projectParcel("Area", "", outer, emptyList())
        val division = parcel.divide(2)
        assertEquals(parcel.netAreaM2 / 2, division.targetAreaM2, 1e-6)
        assertEquals(180.0, kotlin.math.abs(division.cutLongitudes.single()), 1e-7)
    }

    @Test fun invalidExclusionsAreRejected() {
        val outer = ring(0.0, 0.01)
        val hole = ring(0.001, 0.002)
        assertThrows(IllegalArgumentException::class.java) { projectParcel("Outside", "", outer, listOf(ring(0.009, 0.012))) }
        assertThrows(IllegalArgumentException::class.java) { projectParcel("Touching", "", outer, listOf(ring(0.0, 0.002))) }
        assertThrows(IllegalArgumentException::class.java) { projectParcel("Overlap", "", outer, listOf(hole, ring(0.0015, 0.003))) }
        assertThrows(IllegalArgumentException::class.java) { projectParcel("Nested", "", outer, listOf(hole, ring(0.0012, 0.0018))) }
    }

    @Test fun openGpsRouteDoesNotClosePolygon() {
        val recorder = GpsRecorder()
        val points = listOf(GeoPoint(48.0, 2.0), GeoPoint(48.0001, 2.0), GeoPoint(48.0001, 2.00015))
        recorder.start(permissionGranted = true, providerEnabled = true, openTrack = true)
        points.forEachIndexed { index, point ->
            recorder.onFix(GpsFix(point.lat, point.lon, 4f, 1000L + index * 20_000L))
        }
        val result = recorder.finish() as PolygonResult.Valid
        assertEquals(points, result.points)
        assertEquals(0.0, result.areaM2, 0.0)
        assertEquals(GeoMath.pathLengthM(points), result.perimeterM, 1e-6)
        assertTrue(result.perimeterM < GeoMath.perimeterM(points))
    }

    @Test fun geodesicDoesNotFallBackToSphereAtAntipodes() {
        assertEquals(20003931.458625447, GeoMath.distance(GeoPoint(0.0, 0.0), GeoPoint(0.0, 180.0)), 0.001)
    }

    @Test fun offlineLicenseWindowAndTamperedReceipts() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
        val data = """{"productId":"simpletools_area_pro_lifetime","purchaseState":0}"""
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private)
            update(data.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        assertTrue(verifyReceipt(data, signature, publicKey))
        assertFalse(verifyReceipt(data + " ", signature, publicKey))
        assertFalse(verifyReceipt(data, signature, ""))
        val last = 100_000L
        val expiry = last + 30L * 24 * 60 * 60 * 1000
        assertTrue(offlineReceiptIsFresh(last, expiry - 1))
        assertFalse(offlineReceiptIsFresh(last, expiry))
        assertFalse(offlineReceiptIsFresh(last, last - 1))
    }
}
