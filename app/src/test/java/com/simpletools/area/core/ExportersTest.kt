package com.simpletools.area.core

import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.feature.export.Exporters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportersTest {
    // sens horaire volontairement (le GeoJSON doit être réorienté en anti-horaire)
    private val parcel = ParcelEntity(
        id = 1, name = "Champ \"Nord\"\nA", createdAt = 1_700_000_000_000, updatedAt = 1_700_000_000_000,
        type = MeasureType.MAP,
        points = listOf(GeoPoint(45.0, 5.0), GeoPoint(45.001, 5.0), GeoPoint(45.001, 5.001), GeoPoint(45.0, 5.001)),
        areaM2 = 7800.5, perimeterM = 355.2, displayUnit = "AUTO", notes = "a\\b",
    )

    @Test fun csv_hasHeaderAndOneLinePerPoint() {
        val lines = Exporters.toCsv(parcel).trim().split("\r\n")
        assertEquals("index,latitude,longitude", lines[0])
        assertEquals("1,45.0,5.0", lines[1])
        assertEquals("3,45.001,5.001", lines[3])
        assertEquals(5, lines.size)
    }

    @Test fun geojson_polygonClosedCounterClockwise_lonLatOrder() {
        val g = Exporters.toGeoJson(parcel)
        assertTrue(g.startsWith("""{"type":"FeatureCollection""""))
        assertTrue(g.contains(""""type":"Polygon""""))
        // anneau fermé : premier == dernier, 5 positions, [lon,lat]
        val ring = Exporters.closedCcwRing(parcel.points)
        assertEquals(5, ring.size); assertEquals(ring.first(), ring.last())
        assertTrue(g.contains("[5.0,45.0]"))
        // le tracé d'origine est horaire (nord, est, sud) : l'anneau exporté est inversé (anti-horaire)
        assertEquals(GeoPoint(45.0, 5.001), ring[0]); assertEquals(GeoPoint(45.001, 5.001), ring[1])
        assertEquals(GeoPoint(45.0, 5.0), ring[3])
        assertTrue(g.contains(""""area_m2":7800.5"""))
    }

    @Test fun geojson_escapesStrings() {
        val g = Exporters.toGeoJson(parcel)
        assertTrue(g.contains("""\"Nord\"\nA"""))
        assertTrue(g.contains("""a\\b"""))
    }

    @Test fun geojson_distanceIsLineString() {
        val d = parcel.copy(type = MeasureType.DISTANCE, points = parcel.points.take(2), areaM2 = 0.0)
        assertTrue(Exporters.toGeoJson(d).contains(""""type":"LineString""""))
    }
}
