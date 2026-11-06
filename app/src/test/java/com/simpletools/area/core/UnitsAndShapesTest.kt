package com.simpletools.area.core

import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.AreaUnitChoice
import com.simpletools.area.core.geometry.LengthUnit
import com.simpletools.area.core.geometry.PlanePoint
import com.simpletools.area.core.geometry.ShapeCalculator
import com.simpletools.area.core.geometry.ShapeError
import com.simpletools.area.core.geometry.ShapeResult
import com.simpletools.area.core.geometry.UnitFormat
import com.simpletools.area.core.geometry.regularPolygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class UnitsAndShapesTest {
    @Test fun areaConversions_exactFactors() {
        assertEquals(1.25, AreaUnit.HECTARE.fromSquareMeters(12_500.0), 1e-12)
        assertEquals(0.0125, AreaUnit.SQUARE_KILOMETER.fromSquareMeters(12_500.0), 1e-12)
        assertEquals(1.0, AreaUnit.ACRE.fromSquareMeters(4046.8564224), 1e-12)
        assertEquals(10.763910416709722, AreaUnit.SQUARE_FOOT.fromSquareMeters(1.0), 1e-9) // 1 m² = 10,7639104 ft²
        assertEquals(10_000.0, AreaUnit.HECTARE.toSquareMeters(1.0), 0.0)
        assertEquals(1_000_000.0, AreaUnit.SQUARE_KILOMETER.toSquareMeters(1.0), 0.0)
        assertEquals(100.0, AreaUnit.HECTARE.fromSquareMeters(AreaUnit.SQUARE_KILOMETER.toSquareMeters(1.0)), 1e-9)
    }

    @Test fun lengthConversions() {
        assertEquals(1.5, LengthUnit.KILOMETER.fromMeters(1500.0), 0.0)
        assertEquals(304.8, LengthUnit.FOOT.toMeters(1000.0), 1e-9)
        assertEquals(2500.0, LengthUnit.CENTIMETER.fromMeters(25.0), 1e-9)
    }

    @Test fun roundTripConversion() {
        for (u in AreaUnit.entries) assertEquals(12345.678, u.toSquareMeters(u.fromSquareMeters(12345.678)), 1e-9)
    }

    @Test fun autoUnit_and_formatting_fr() {
        val fr = Locale.FRANCE
        assertEquals(AreaUnit.SQUARE_METER, UnitFormat.autoAreaUnit(500.0))
        assertEquals(AreaUnit.HECTARE, UnitFormat.autoAreaUnit(12_500.0))
        assertEquals(AreaUnit.SQUARE_KILOMETER, UnitFormat.autoAreaUnit(2_500_000.0))
        val s = UnitFormat.formatArea(12_500.0, AreaUnit.HECTARE, fr)
        assertEquals("1,25 ha", s)
        assertTrue(UnitFormat.formatArea(12_500.0, AreaUnit.SQUARE_METER, fr).replace(' ', ' ').replace(' ', ' ').startsWith("12 500 m²"))
        assertEquals("480 m", UnitFormat.formatLength(480.0, LengthUnit.METER, fr))
        assertEquals("1,5 km", UnitFormat.formatLength(1500.0, UnitFormat.autoLengthUnit(1500.0), fr))
        assertEquals("1.25 ha", UnitFormat.formatArea(12_500.0, AreaUnitChoice.HECTARE, Locale.US))
    }

    private fun ok(r: ShapeResult) = r as ShapeResult.Ok

    @Test fun rectangleSquareTriangleCircleTrapezoid() {
        assertEquals(60.0, ok(ShapeCalculator.rectangle(10.0, 6.0)).areaM2, 1e-12)
        assertEquals(32.0, ok(ShapeCalculator.rectangle(10.0, 6.0)).perimeterM!!, 1e-12)
        assertEquals(25.0, ok(ShapeCalculator.square(5.0)).areaM2, 1e-12)
        assertEquals(24.0, ok(ShapeCalculator.triangleBaseHeight(8.0, 6.0)).areaM2, 1e-12)
        assertEquals(6.0, ok(ShapeCalculator.triangleSides(3.0, 4.0, 5.0)).areaM2, 1e-12)
        assertEquals(Math.PI * 4, ok(ShapeCalculator.circle(2.0)).areaM2, 1e-12)
        assertEquals(30.0, ok(ShapeCalculator.trapezoid(4.0, 6.0, 6.0)).areaM2, 1e-12)
        assertEquals(10_000.0, ok(ShapeCalculator.rectangle(100.0, 100.0)).areaM2, 1e-9)
    }

    @Test fun unitsAreAppliedToDimensions() {
        assertEquals(1.0, ok(ShapeCalculator.rectangle(100.0, 100.0, LengthUnit.CENTIMETER)).areaM2, 1e-12)
        assertEquals(1_000_000.0, ok(ShapeCalculator.square(1.0, LengthUnit.KILOMETER)).areaM2, 1e-6)
        assertEquals(0.09290304, ok(ShapeCalculator.square(1.0, LengthUnit.FOOT)).areaM2, 1e-12)
    }

    @Test fun regularPolygon() {
        assertEquals(Math.sqrt(3.0) / 4 * 4, ok(ShapeCalculator.regularPolygon(3, 2.0)).areaM2, 1e-9)
        assertEquals(16.0, ok(ShapeCalculator.regularPolygon(4, 4.0)).areaM2, 1e-9)
        assertEquals(ShapeResult.Error(ShapeError.TOO_FEW_POINTS), ShapeCalculator.regularPolygon(2, 1.0))
    }

    @Test fun polygonByCoordinates_concave() {
        val l = listOf(PlanePoint(0.0, 0.0), PlanePoint(4.0, 0.0), PlanePoint(4.0, 2.0), PlanePoint(2.0, 2.0), PlanePoint(2.0, 4.0), PlanePoint(0.0, 4.0))
        val r = ok(ShapeCalculator.polygon(l))
        assertEquals(12.0, r.areaM2, 1e-12)
        assertEquals(16.0, r.perimeterM!!, 1e-12)
    }

    @Test fun validationErrors() {
        assertEquals(ShapeResult.Error(ShapeError.NON_POSITIVE), ShapeCalculator.rectangle(-1.0, 2.0))
        assertEquals(ShapeResult.Error(ShapeError.NON_POSITIVE), ShapeCalculator.circle(0.0))
        assertEquals(ShapeResult.Error(ShapeError.NOT_A_NUMBER), ShapeCalculator.square(Double.NaN))
        assertEquals(ShapeResult.Error(ShapeError.NOT_A_NUMBER), ShapeCalculator.square(Double.POSITIVE_INFINITY))
        assertEquals(ShapeResult.Error(ShapeError.TRIANGLE_INEQUALITY), ShapeCalculator.triangleSides(1.0, 2.0, 10.0))
        val bow = listOf(PlanePoint(0.0, 0.0), PlanePoint(1.0, 1.0), PlanePoint(1.0, 0.0), PlanePoint(0.0, 1.0))
        assertEquals(ShapeResult.Error(ShapeError.SELF_INTERSECTING), ShapeCalculator.polygon(bow))
        assertEquals(ShapeResult.Error(ShapeError.TOO_FEW_POINTS), ShapeCalculator.polygon(listOf(PlanePoint(0.0, 0.0), PlanePoint(1.0, 1.0))))
    }
}
