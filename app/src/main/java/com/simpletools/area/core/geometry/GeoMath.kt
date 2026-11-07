package com.simpletools.area.core.geometry

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.PolygonArea

/** Distances et aires géodésiques WGS84. Projection locale réservée au dessin et à la topologie. */
object GeoMath {
    const val WGS84_A = 6378137.0
    const val WGS84_F = 1.0 / 298.257223563
    private val E2 = WGS84_F * (2 - WGS84_F)
    private val E = sqrt(E2)
    private const val MIN_SEGMENT_M = 0.001

    private fun rad(d: Double) = Math.toRadians(d)

    // ---------------------------------------------------------------- distance

    /** Distance sur la géodésique la plus courte, y compris près des antipodes. */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        require(a.isValid && b.isValid)
        return Geodesic.WGS84.Inverse(a.lat, a.lon, b.lat, b.lon).s12
    }

    // ---------------------------------------------------------------- projection

    private fun qOf(phi: Double): Double {
        val s = sin(phi)
        return (1 - E2) * (s / (1 - E2 * s * s) - (1 / (2 * E)) * ln((1 - E * s) / (1 + E * s)))
    }

    /** Longitude moyenne circulaire (robuste au passage de l'antiméridien). */
    internal fun centerOf(points: List<GeoPoint>): GeoPoint {
        var sx = 0.0; var sy = 0.0; var lat = 0.0
        for (p in points) {
            sx += cos(rad(p.lon)); sy += sin(rad(p.lon)); lat += p.lat
        }
        val lon = if (abs(sx) < 1e-12 && abs(sy) < 1e-12) points.first().lon
        else Math.toDegrees(atan2(sy, sx))
        return GeoPoint(lat / points.size, lon)
    }

    /** Projection azimutale équivalente de Lambert (ellipsoïdale) centrée sur [center]. Unités : mètres. */
    fun projectEqualArea(points: List<GeoPoint>, center: GeoPoint = centerOf(points)): List<PlanePoint> {
        val phi0 = rad(center.lat)
        val lam0 = rad(center.lon)
        val qp = qOf(Math.PI / 2)
        val rq = WGS84_A * sqrt(qp / 2)
        val beta0 = asin(qOf(phi0) / qp)
        val s0 = sin(phi0)
        val d = WGS84_A * (cos(phi0) / sqrt(1 - E2 * s0 * s0)) / (rq * cos(beta0))
        val sinB0 = sin(beta0); val cosB0 = cos(beta0)
        return points.map { p ->
            val phi = rad(p.lat)
            var dl = rad(p.lon) - lam0
            while (dl > Math.PI) dl -= 2 * Math.PI
            while (dl < -Math.PI) dl += 2 * Math.PI
            val beta = asin((qOf(phi) / qp).coerceIn(-1.0, 1.0))
            val sinB = sin(beta); val cosB = cos(beta)
            val denom = 1 + sinB0 * sinB + cosB0 * cosB * cos(dl)
            val b = rq * sqrt(2 / max(denom, 1e-15))
            PlanePoint(
                x = b * d * cosB * sin(dl),
                y = (b / d) * (cosB0 * sinB - sinB0 * cosB * cos(dl)),
            )
        }
    }

    // ---------------------------------------------------------------- area / perimeter

    /** Aire absolue (m²) d'un polygone déjà validé/nettoyé. */
    fun areaM2(points: List<GeoPoint>): Double {
        if (points.size < 3) return 0.0
        return abs(signedAreaM2(points))
    }

    fun signedAreaM2(points: List<GeoPoint>): Double {
        require(points.all { it.isValid })
        val polygon = PolygonArea(Geodesic.WGS84, false)
        points.forEach { polygon.AddPoint(it.lat, it.lon) }
        return polygon.Compute(false, true).area
    }

    /** Périmètre (m). Si [closed], inclut le segment dernier → premier. */
    fun perimeterM(points: List<GeoPoint>, closed: Boolean = true): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until points.size - 1) sum += distance(points[i], points[i + 1])
        if (closed && points.size > 2) sum += distance(points.last(), points.first())
        return sum
    }

    /** Longueur d'une polyligne ouverte (m). */
    fun pathLengthM(points: List<GeoPoint>): Double = perimeterM(points, closed = false)

    internal fun shoelace(p: List<PlanePoint>): Double {
        var s = 0.0
        for (i in p.indices) {
            val a = p[i]; val b = p[(i + 1) % p.size]
            s += a.x * b.y - b.x * a.y
        }
        return s / 2
    }

    // ---------------------------------------------------------------- validation

    /** Nettoie (supprime doublons consécutifs et point de fermeture répété) puis valide un polygone. */
    fun validatePolygon(input: List<GeoPoint>): PolygonResult {
        if (input.any { !it.isValid }) return PolygonResult.Invalid(PolygonError.INVALID_COORDINATE)
        val cleaned = ArrayList<GeoPoint>()
        for (p in input) {
            if (cleaned.isEmpty() || distance(cleaned.last(), p) >= MIN_SEGMENT_M) cleaned.add(p)
        }
        while (cleaned.size > 1 && distance(cleaned.last(), cleaned.first()) < MIN_SEGMENT_M) {
            cleaned.removeAt(cleaned.lastIndex)
        }
        if (cleaned.size < 3) return PolygonResult.Invalid(PolygonError.TOO_FEW_POINTS)
        val plane = projectEqualArea(cleaned)
        if (allCollinear(plane)) return PolygonResult.Invalid(PolygonError.ZERO_AREA)
        if (hasSelfIntersection(plane)) return PolygonResult.Invalid(PolygonError.SELF_INTERSECTING)
        val area = areaM2(cleaned)
        if (area < 1e-6) return PolygonResult.Invalid(PolygonError.ZERO_AREA)
        return PolygonResult.Valid(
            points = cleaned,
            areaM2 = area,
            perimeterM = perimeterM(cleaned, true),
            removedPoints = input.size - cleaned.size,
        )
    }

    /** Tous les points sur une même droite (tolérance 1 µm de distance à la droite). */
    private fun allCollinear(p: List<PlanePoint>): Boolean {
        val a = p[0]
        val b = p.drop(1).maxByOrNull { hypot2(it, a) } ?: return true
        val len = sqrt(hypot2(b, a))
        if (len == 0.0) return true
        return p.all { abs(cross(a, b, it)) / len < 1e-6 }
    }

    private fun hypot2(u: PlanePoint, v: PlanePoint) = (u.x - v.x) * (u.x - v.x) + (u.y - v.y) * (u.y - v.y)

    internal fun hasSelfIntersection(p: List<PlanePoint>): Boolean {
        val n = p.size
        for (i in 0 until n) {
            val a1 = p[i]; val a2 = p[(i + 1) % n]
            for (j in i + 1 until n) {
                // segments adjacents : ne se touchent qu'à leur extrémité commune
                if (j == i + 1 || (i == 0 && j == n - 1)) {
                    if (collinearOverlap(p[i], p[(i + 1) % n], p[j], p[(j + 1) % n], j == i + 1)) return true
                    continue
                }
                if (segmentsIntersect(a1, a2, p[j], p[(j + 1) % n])) return true
            }
        }
        return false
    }

    private fun cross(o: PlanePoint, a: PlanePoint, b: PlanePoint) =
        (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    private fun onSegment(a: PlanePoint, b: PlanePoint, p: PlanePoint) =
        p.x >= min(a.x, b.x) - 1e-9 && p.x <= max(a.x, b.x) + 1e-9 &&
            p.y >= min(a.y, b.y) - 1e-9 && p.y <= max(a.y, b.y) + 1e-9

    internal fun segmentsIntersect(a: PlanePoint, b: PlanePoint, c: PlanePoint, d: PlanePoint): Boolean {
        val eps = 1e-9
        val d1 = cross(a, b, c); val d2 = cross(a, b, d)
        val d3 = cross(c, d, a); val d4 = cross(c, d, b)
        if (((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) &&
            ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))
        ) return true
        if (abs(d1) <= eps && onSegment(a, b, c)) return true
        if (abs(d2) <= eps && onSegment(a, b, d)) return true
        if (abs(d3) <= eps && onSegment(c, d, a)) return true
        if (abs(d4) <= eps && onSegment(c, d, b)) return true
        return false
    }

    /** Pour deux segments adjacents partageant un sommet : vrai s'ils se recouvrent (demi-tour). */
    private fun collinearOverlap(a: PlanePoint, b: PlanePoint, c: PlanePoint, d: PlanePoint, shareBC: Boolean): Boolean {
        // adjacents : (a,b) et (c,d) avec b==c (ou d==a pour le bouclage)
        val shared: PlanePoint; val p1: PlanePoint; val p2: PlanePoint
        if (shareBC) { shared = b; p1 = a; p2 = d } else { shared = a; p1 = b; p2 = c }
        val v1x = p1.x - shared.x; val v1y = p1.y - shared.y
        val v2x = p2.x - shared.x; val v2y = p2.y - shared.y
        val crossV = v1x * v2y - v1y * v2x
        val dot = v1x * v2x + v1y * v2y
        val scale = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
        return scale > 0 && abs(crossV) <= 1e-9 * scale && dot > 0
    }
}

enum class PolygonError { INVALID_COORDINATE, TOO_FEW_POINTS, SELF_INTERSECTING, ZERO_AREA }

sealed interface PolygonResult {
    data class Valid(
        val points: List<GeoPoint>,
        val areaM2: Double,
        val perimeterM: Double,
        /** Nombre de points supprimés (doublons / point de fermeture répété). */
        val removedPoints: Int,
    ) : PolygonResult

    data class Invalid(val error: PolygonError) : PolygonResult
}
