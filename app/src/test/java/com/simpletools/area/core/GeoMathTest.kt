package com.simpletools.area.core

import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.geometry.PolygonError
import com.simpletools.area.core.geometry.PolygonResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Valeurs de référence : GeographicLib 2.1 (algorithme de Karney, WGS84, arêtes géodésiques),
 * générées par docs/reference/ref.py (voir docs/GEOMETRY.md). Distance : Geoscience Australia,
 * Flinders Peak → Buninyong = 54 972,271 m.
 */
class GeoMathTest {
    private fun rel(expected: Double, actual: Double) = kotlin.math.abs(expected - actual) / expected

    private val paris100 = listOf(
        GeoPoint(48.8566, 2.3522), GeoPoint(48.856599992, 2.353562739),
        GeoPoint(48.855700768, 2.353562739), GeoPoint(48.85570076, 2.352200024),
    )
    private val eq1000 = listOf(
        GeoPoint(0.0, 10.0), GeoPoint(0.0, 10.008983153),
        GeoPoint(-0.009043695, 10.008983153), GeoPoint(-0.009043695, 10.0),
    )
    private val lat70 = listOf(
        GeoPoint(70.0, 20.0), GeoPoint(69.999999519, 20.013093618),
        GeoPoint(69.995517706, 20.013093618), GeoPoint(69.995517225, 20.000002811),
    )

    @Test fun squareArea_paris_100m() {
        assertEquals(10000.0895549414, GeoMath.areaM2(paris100), 0.01)
    }

    @Test fun squareArea_equator_1km() {
        assertEquals(999999.997937684, GeoMath.areaM2(eq1000), 0.05)
    }

    @Test fun squareArea_highLatitude_500m() {
        assertEquals(250026.840104894, GeoMath.areaM2(lat70), 0.05)
    }

    @Test fun rectangleArea() {
        // rectangle ~ 200 m × 50 m près de Lyon, construit par déplacements géodésiques de référence
        val a = GeoPoint(45.75, 4.85)
        val b = GeoPoint(45.75, 4.85 + 200.0 / (111320.0 * Math.cos(Math.toRadians(45.75))))
        val c = GeoPoint(45.75 + 50.0 / 111132.0, b.lon)
        val d = GeoPoint(c.lat, a.lon)
        val area = GeoMath.areaM2(listOf(a, b, c, d))
        assertEquals(10000.0, area, 50.0) // approximation des constructions (±0,5 %)
        // l'aire d'un rectangle doit rester identique quel que soit le sens de parcours
        assertEquals(area, GeoMath.areaM2(listOf(d, c, b, a)), 1e-6)
    }

    @Test fun triangleArea() {
        val tri = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.01, 0.0))
        assertTrue(rel(615453.6071641953, GeoMath.areaM2(tri)) < 1e-5)
        assertTrue(rel(3787.9723816661444, GeoMath.perimeterM(tri)) < 1e-5)
    }

    @Test fun concavePolygon_Lshape() {
        val l = listOf(
            GeoPoint(45.75, 4.85), GeoPoint(45.75, 4.8511), GeoPoint(45.7506, 4.8511),
            GeoPoint(45.7506, 4.8505), GeoPoint(45.7511, 4.8505), GeoPoint(45.7511, 4.85),
        )
        val r = GeoMath.validatePolygon(l) as PolygonResult.Valid
        assertEquals(7870.032011479139, r.areaM2, 0.5)
        assertEquals(415.70605406168386, r.perimeterM, 0.05)
    }

    @Test fun oneDegreeCell_closeToKarney() {
        val cell = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0), GeoPoint(1.0, 1.0), GeoPoint(1.0, 0.0))
        // arêtes droites dans la projection vs géodésiques : écart attendu bien < 0,1 %
        assertTrue(rel(12308778361.469452, GeoMath.areaM2(cell)) < 1e-3)
    }

    @Test fun antimeridianCrossing() {
        val p = listOf(GeoPoint(10.0, 179.9), GeoPoint(10.0, -179.9), GeoPoint(10.2, -179.9), GeoPoint(10.2, 179.9))
        val r = GeoMath.validatePolygon(p) as PolygonResult.Valid
        assertTrue(rel(484932684.776, r.areaM2) < 1e-3)
        // le même polygone décalé loin de l'antiméridien donne la même surface (à la latitude près)
        val shifted = p.map { GeoPoint(it.lat, it.lon - 170.0) }
        assertTrue(rel(r.areaM2, GeoMath.areaM2(shifted)) < 1e-9)
    }

    @Test fun perimeter_closedAndOpen() {
        val sq = eq1000
        assertEquals(3999.999987626, GeoMath.perimeterM(sq, closed = true), 0.01)
        assertEquals(3000.0, GeoMath.perimeterM(sq, closed = false), 0.5)
    }

    @Test fun vincentyReferenceDistance() {
        val flinders = GeoPoint(-(37 + 57 / 60.0 + 3.72030 / 3600), 144 + 25 / 60.0 + 29.52440 / 3600)
        val buninyong = GeoPoint(-(37 + 39 / 60.0 + 10.15610 / 3600), 143 + 55 / 60.0 + 35.38390 / 3600)
        assertEquals(54972.271, GeoMath.distance(flinders, buninyong), 0.002)
        assertEquals(54972.27113920079, GeoMath.distance(flinders, buninyong), 0.0005)
    }

    @Test fun distanceIsSymmetricAndZeroForSamePoint() {
        val a = GeoPoint(48.85, 2.35); val b = GeoPoint(43.30, 5.37)
        assertEquals(GeoMath.distance(a, b), GeoMath.distance(b, a), 1e-6)
        assertEquals(0.0, GeoMath.distance(a, a), 0.0)
    }

    @Test fun invalidCoordinatesRejected() {
        val bad = listOf(GeoPoint(91.0, 0.0), GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        assertEquals(PolygonResult.Invalid(PolygonError.INVALID_COORDINATE), GeoMath.validatePolygon(bad))
        val nan = listOf(GeoPoint(Double.NaN, 0.0), GeoPoint(0.0, 0.0), GeoPoint(0.0, 1.0))
        assertEquals(PolygonResult.Invalid(PolygonError.INVALID_COORDINATE), GeoMath.validatePolygon(nan))
        val lon = listOf(GeoPoint(0.0, 181.0), GeoPoint(0.0, 0.0), GeoPoint(1.0, 1.0))
        assertEquals(PolygonResult.Invalid(PolygonError.INVALID_COORDINATE), GeoMath.validatePolygon(lon))
    }

    @Test fun tooFewPointsAndIdenticalPoints() {
        assertEquals(PolygonResult.Invalid(PolygonError.TOO_FEW_POINTS), GeoMath.validatePolygon(listOf(GeoPoint(1.0, 1.0), GeoPoint(2.0, 2.0))))
        val same = List(5) { GeoPoint(10.0, 10.0) }
        assertEquals(PolygonResult.Invalid(PolygonError.TOO_FEW_POINTS), GeoMath.validatePolygon(same))
    }

    @Test fun duplicatesAndClosingPointAreRemoved() {
        val withDup = paris100 + paris100.first() // point de fermeture répété
        val doubled = listOf(paris100[0], paris100[0]) + paris100.drop(1)
        val r1 = GeoMath.validatePolygon(withDup) as PolygonResult.Valid
        val r2 = GeoMath.validatePolygon(doubled) as PolygonResult.Valid
        assertEquals(4, r1.points.size); assertEquals(1, r1.removedPoints)
        assertEquals(4, r2.points.size); assertEquals(1, r2.removedPoints)
        assertEquals(10000.0895549414, r1.areaM2, 0.01)
    }

    @Test fun collinearPointsGiveZeroArea() {
        val line = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.001), GeoPoint(0.0, 0.002))
        assertEquals(PolygonResult.Invalid(PolygonError.ZERO_AREA), GeoMath.validatePolygon(line))
    }

    @Test fun selfIntersectingBowtieRejected() {
        val bow = listOf(GeoPoint(0.0, 0.0), GeoPoint(0.001, 0.001), GeoPoint(0.0, 0.001), GeoPoint(0.001, 0.0))
        assertEquals(PolygonResult.Invalid(PolygonError.SELF_INTERSECTING), GeoMath.validatePolygon(bow))
    }

    @Test fun veryShortSegmentsHandled() {
        // segment de ~1 cm : conservé, calcul stable
        val tiny = listOf(GeoPoint(45.0, 5.0), GeoPoint(45.0, 5.0000001), GeoPoint(45.0000001, 5.0000001), GeoPoint(45.0000001, 5.0))
        val r = GeoMath.validatePolygon(tiny) as PolygonResult.Valid
        assertTrue(r.areaM2 > 0.0 && r.areaM2 < 0.001)
    }

    @Test fun orientationDoesNotMatter() {
        assertEquals(GeoMath.areaM2(lat70), GeoMath.areaM2(lat70.reversed()), 1e-6)
    }
}
