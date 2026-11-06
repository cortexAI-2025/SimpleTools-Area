package com.simpletools.area.core.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

enum class ShapeError { NOT_A_NUMBER, NON_POSITIVE, TRIANGLE_INEQUALITY, POLYGON_INVALID, TOO_FEW_POINTS, SELF_INTERSECTING }

sealed interface ShapeResult {
    /** [areaM2] est toujours en m², [perimeterM] en m (null si non défini). */
    data class Ok(val areaM2: Double, val perimeterM: Double?) : ShapeResult
    data class Error(val error: ShapeError) : ShapeResult
}

/**
 * Surfaces de formes planes. Les dimensions sont données dans [unit] ;
 * les résultats sont convertis en m².
 */
object ShapeCalculator {
    private fun check(vararg v: Double): ShapeError? {
        for (x in v) {
            if (x.isNaN() || x.isInfinite()) return ShapeError.NOT_A_NUMBER
            if (x <= 0.0) return ShapeError.NON_POSITIVE
        }
        return null
    }

    private fun ok(area: Double, perimeter: Double?, u: LengthUnit) =
        ShapeResult.Ok(area * u.meters * u.meters, perimeter?.let { it * u.meters })

    fun rectangle(w: Double, h: Double, u: LengthUnit = LengthUnit.METER): ShapeResult =
        check(w, h)?.let { ShapeResult.Error(it) } ?: ok(w * h, 2 * (w + h), u)

    fun square(side: Double, u: LengthUnit = LengthUnit.METER): ShapeResult = rectangle(side, side, u)

    fun triangleBaseHeight(base: Double, height: Double, u: LengthUnit = LengthUnit.METER): ShapeResult =
        check(base, height)?.let { ShapeResult.Error(it) } ?: ok(base * height / 2, null, u)

    /** Formule de Héron. */
    fun triangleSides(a: Double, b: Double, c: Double, u: LengthUnit = LengthUnit.METER): ShapeResult {
        check(a, b, c)?.let { return ShapeResult.Error(it) }
        if (a + b <= c || a + c <= b || b + c <= a) return ShapeResult.Error(ShapeError.TRIANGLE_INEQUALITY)
        val s = (a + b + c) / 2
        return ok(sqrt(s * (s - a) * (s - b) * (s - c)), a + b + c, u)
    }

    fun circle(radius: Double, u: LengthUnit = LengthUnit.METER): ShapeResult =
        check(radius)?.let { ShapeResult.Error(it) } ?: ok(PI * radius * radius, 2 * PI * radius, u)

    fun trapezoid(a: Double, b: Double, height: Double, u: LengthUnit = LengthUnit.METER): ShapeResult =
        check(a, b, height)?.let { ShapeResult.Error(it) } ?: ok((a + b) / 2 * height, null, u)

    /** Polygone plan défini par des coordonnées (x, y) exprimées dans [u]. */
    fun polygon(points: List<PlanePoint>, u: LengthUnit = LengthUnit.METER): ShapeResult {
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) return ShapeResult.Error(ShapeError.NOT_A_NUMBER)
        val cleaned = ArrayList<PlanePoint>()
        for (p in points) if (cleaned.isEmpty() || cleaned.last() != p) cleaned.add(p)
        if (cleaned.size > 1 && cleaned.first() == cleaned.last()) cleaned.removeAt(cleaned.lastIndex)
        if (cleaned.size < 3) return ShapeResult.Error(ShapeError.TOO_FEW_POINTS)
        if (GeoMath.hasSelfIntersection(cleaned)) return ShapeResult.Error(ShapeError.SELF_INTERSECTING)
        val area = abs(GeoMath.shoelace(cleaned))
        if (area == 0.0) return ShapeResult.Error(ShapeError.POLYGON_INVALID)
        var per = 0.0
        for (i in cleaned.indices) {
            val a = cleaned[i]; val b = cleaned[(i + 1) % cleaned.size]
            per += sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
        }
        return ok(area, per, u)
    }
}

/** Polygone régulier à [n] côtés de longueur [side] (dans [u]). */
fun ShapeCalculator.regularPolygon(n: Int, side: Double, u: LengthUnit = LengthUnit.METER): ShapeResult {
    if (side.isNaN() || side.isInfinite()) return ShapeResult.Error(ShapeError.NOT_A_NUMBER)
    if (side <= 0.0) return ShapeResult.Error(ShapeError.NON_POSITIVE)
    if (n < 3) return ShapeResult.Error(ShapeError.TOO_FEW_POINTS)
    val area = n * side * side / (4 * kotlin.math.tan(PI / n))
    return ShapeResult.Ok(area * u.meters * u.meters, n * side * u.meters)
}
