package com.simpletools.area.core

import com.simpletools.area.core.geometry.LengthUnit
import com.simpletools.area.core.geometry.ShapeError
import com.simpletools.area.core.geometry.ShapeResult
import com.simpletools.area.core.maps.MapStyles
import com.simpletools.area.feature.calculator.Shape
import com.simpletools.area.feature.calculator.compute
import com.simpletools.area.feature.calculator.parseNumber
import com.simpletools.area.feature.calculator.parsePlanePoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorParsingTest {
    @Test fun parseNumber_acceptsCommaAndRejectsGarbage() {
        assertEquals(12.5, parseNumber("12,5")!!, 0.0)
        assertEquals(12.5, parseNumber(" 12.5 ")!!, 0.0)
        assertNull(parseNumber("abc")); assertNull(parseNumber("")); assertNull(parseNumber("NaN")); assertNull(parseNumber("Infinity"))
    }

    @Test fun parsePlanePoints_formats() {
        assertEquals(4, parsePlanePoints("0 0\n10;0\n10,5 5,5\n0 5")!!.size)
        assertNull(parsePlanePoints("0 0\n1 2 3"))
        assertNull(parsePlanePoints("0 0\nx y"))
    }

    @Test fun computeFromUserInput() {
        val r = compute(Shape.RECTANGLE, listOf("12,5", "8", ""), "", LengthUnit.METER) as ShapeResult.Ok
        assertEquals(100.0, r.areaM2, 1e-9)
        assertEquals(ShapeResult.Error(ShapeError.NOT_A_NUMBER), compute(Shape.CIRCLE, listOf("abc", "", ""), "", LengthUnit.METER))
        assertEquals(ShapeResult.Error(ShapeError.NON_POSITIVE), compute(Shape.SQUARE, listOf("-3", "", ""), "", LengthUnit.METER))
        val poly = compute(Shape.POLYGON, emptyList(), "0 0\n10 0\n10 5\n0 5", LengthUnit.METER) as ShapeResult.Ok
        assertEquals(50.0, poly.areaM2, 1e-9)
        val reg = compute(Shape.REGULAR, listOf("6", "2", ""), "", LengthUnit.METER) as ShapeResult.Ok
        assertEquals(3 * Math.sqrt(3.0) / 2 * 4, reg.areaM2, 1e-9) // hexagone de côté 2
        assertTrue(compute(Shape.REGULAR, listOf("2.5", "2", ""), "", LengthUnit.METER) is ShapeResult.Error)
    }

    @Test fun tileTemplateValidation() {
        assertTrue(MapStyles.isValidTileTemplate("https://tiles.example.org/{z}/{x}/{y}.png"))
        assertFalse(MapStyles.isValidTileTemplate("http://tiles.example.org/{z}/{x}/{y}.png"))
        assertFalse(MapStyles.isValidTileTemplate("https://tiles.example.org/tiles.png"))
        assertFalse(MapStyles.isValidTileTemplate("https://x/{z}/{x}/{y}\"injection"))
        assertNotNull(MapStyles.styleJson(false, null)); assertTrue(MapStyles.styleJson(true, null).contains("openstreetmap"))
        assertTrue(MapStyles.styleJson(false, "https://t.example/{z}/{x}/{y}.jpg").contains("t.example"))
        assertFalse(MapStyles.styleJson(false, "javascript:alert(1)").contains("javascript"))
    }
}
