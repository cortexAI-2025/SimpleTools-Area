package com.simpletools.area.core.geometry

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

enum class AreaUnit(val symbol: String, val squareMeters: Double) {
    SQUARE_METER("m²", 1.0),
    HECTARE("ha", 10_000.0),
    SQUARE_KILOMETER("km²", 1_000_000.0),
    ACRE("ac", 4046.8564224),
    SQUARE_FOOT("ft²", 0.09290304);

    fun fromSquareMeters(m2: Double) = m2 / squareMeters
    fun toSquareMeters(v: Double) = v * squareMeters
}

enum class LengthUnit(val symbol: String, val meters: Double) {
    METER("m", 1.0),
    KILOMETER("km", 1000.0),
    FOOT("ft", 0.3048),
    CENTIMETER("cm", 0.01);

    fun fromMeters(m: Double) = m / meters
    fun toMeters(v: Double) = v * meters
}

/** Unité choisie par l'utilisateur ; AUTO sélectionne l'unité adaptée à l'ordre de grandeur. */
enum class AreaUnitChoice(val unit: AreaUnit?) {
    AUTO(null),
    SQUARE_METER(AreaUnit.SQUARE_METER),
    HECTARE(AreaUnit.HECTARE),
    SQUARE_KILOMETER(AreaUnit.SQUARE_KILOMETER),
    ACRE(AreaUnit.ACRE),
    SQUARE_FOOT(AreaUnit.SQUARE_FOOT);
}

enum class LengthUnitChoice(val unit: LengthUnit?) {
    AUTO(null), METER(LengthUnit.METER), KILOMETER(LengthUnit.KILOMETER);
}

object UnitFormat {
    /** m² sous 1 ha, hectares jusqu'à 100 ha, km² au-delà. */
    fun autoAreaUnit(m2: Double): AreaUnit = when {
        abs(m2) < 10_000.0 -> AreaUnit.SQUARE_METER
        abs(m2) < 1_000_000.0 -> AreaUnit.HECTARE
        else -> AreaUnit.SQUARE_KILOMETER
    }

    fun autoLengthUnit(m: Double): LengthUnit = if (abs(m) < 1000.0) LengthUnit.METER else LengthUnit.KILOMETER

    fun resolve(choice: AreaUnitChoice, m2: Double): AreaUnit = choice.unit ?: autoAreaUnit(m2)
    fun resolve(choice: LengthUnitChoice, m: Double): LengthUnit = choice.unit ?: autoLengthUnit(m)

    private fun number(value: Double, maxDecimals: Int, locale: Locale): String {
        val nf = NumberFormat.getNumberInstance(locale)
        nf.maximumFractionDigits = maxDecimals
        nf.minimumFractionDigits = 0
        return nf.format(value)
    }

    private fun decimalsFor(unit: AreaUnit, value: Double): Int = when {
        unit == AreaUnit.SQUARE_METER || unit == AreaUnit.SQUARE_FOOT -> if (abs(value) < 100) 2 else 0
        else -> if (abs(value) < 10) 4 else 2
    }

    fun formatArea(m2: Double, unit: AreaUnit, locale: Locale = Locale.getDefault()): String {
        val v = unit.fromSquareMeters(m2)
        return "${number(v, decimalsFor(unit, v), locale)} ${unit.symbol}"
    }

    fun formatArea(m2: Double, choice: AreaUnitChoice, locale: Locale = Locale.getDefault()): String =
        formatArea(m2, resolve(choice, m2), locale)

    fun formatLength(m: Double, unit: LengthUnit, locale: Locale = Locale.getDefault()): String {
        val v = unit.fromMeters(m)
        val dec = if (unit == LengthUnit.KILOMETER) 3 else if (abs(v) < 100) 2 else 1
        return "${number(v, dec, locale)} ${unit.symbol}"
    }

    fun formatLength(m: Double, choice: LengthUnitChoice, locale: Locale = Locale.getDefault()): String =
        formatLength(m, resolve(choice, m), locale)
}
