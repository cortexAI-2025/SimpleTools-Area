package com.simpletools.area.core.geometry

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Calculs géodésiques sur l'ellipsoïde WGS84.
 *
 * Méthode (voir docs/GEOMETRY.md) :
 *  - Surface : projection azimutale équivalente de Lambert (formules ellipsoïdales de Snyder,
 *    "Map Projections – A Working Manual", §24), centrée sur le polygone. La projection conserve
 *    exactement les aires : la formule du lacet (shoelace) appliquée aux coordonnées projetées
 *    donne donc la surface sur l'ellipsoïde pour un polygone dont les côtés sont les segments
 *    de la projection (écart avec les géodésiques négligeable pour des parcelles).
 *  - Distance : formule inverse de Vincenty (précision ~0,5 mm), repli Haversine si non convergente.
 *
 * La précision numérique de ces calculs est sans rapport avec la précision réelle d'une mesure GPS
 * (typiquement 3 à 15 m sur un smartphone).
 */
object GeoMath {
    const val WGS84_A = 6378137.0
    const val WGS84_F = 1.0 / 298.257223563
    private const val WGS84_B = WGS84_A * (1 - WGS84_F)
    private val E2 = WGS84_F * (2 - WGS84_F)
    private val E = sqrt(E2)
    private const val MIN_SEGMENT_M = 0.001

    private fun rad(d: Double) = Math.toRadians(d)

    // ---------------------------------------------------------------- distance

    /** Distance géodésique en mètres entre deux points (Vincenty, repli Haversine). */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        if (a == b) return 0.0
        return vincenty(a, b) ?: haversine(a, b)
    }

    fun haversine(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371008.8
        val dLat = rad(b.lat - a.lat)
        val dLon = rad(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    private fun vincenty(p1: GeoPoint, p2: GeoPoint): Double? {
        val l = rad(p2.lon - p1.lon)
        val u1 = atan((1 - WGS84_F) * tan(rad(p1.lat)))
        val u2 = atan((1 - WGS84_F) * tan(rad(p2.lat)))
        val sinU1 = sin(u1); val cosU1 = cos(u1)
        val sinU2 = sin(u2); val cosU2 = cos(u2)
        var lambda = l
        var iter = 0
        var sinSigma: Double; var cosSigma: Double; var sigma: Double
        var cosSqAlpha: Double; var cos2SigmaM: Double
        do {
            val sinL = sin(lambda); val cosL = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinL).let { it * it } +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosL).let { it * it },
            )
            if (sinSigma == 0.0) return 0.0
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosL
            sigma = atan2(sinSigma, cosSigma)
            val sinAlpha = cosU1 * cosU2 * sinL / sinSigma
            cosSqAlpha = 1 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha != 0.0) cosSigma - 2 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = WGS84_F / 16 * cosSqAlpha * (4 + WGS84_F * (4 - 3 * cosSqAlpha))
            val prev = lambda
            lambda = l + (1 - c) * WGS84_F * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM)))
            if (abs(lambda - prev) < 1e-12) break
            iter++
        } while (iter < 200)
        if (iter >= 200) return null
        val uSq = cosSqAlpha * (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_B * WGS84_B)
        val bigA = 1 + uSq / 16384 * (4096 + uSq * (-768 + uSq * (320 - 175 * uSq)))
        val bigB = uSq / 1024 * (256 + uSq * (-128 + uSq * (74 - 47 * uSq)))
        val deltaSigma = bigB * sinSigma * (
            cos2SigmaM + bigB / 4 * (
                cosSigma * (-1 + 2 * cos2SigmaM * cos2SigmaM) -
                    bigB / 6 * cos2SigmaM * (-3 + 4 * sinSigma * sinSigma) * (-3 + 4 * cos2SigmaM * cos2SigmaM)
                )
            )
        return WGS84_B * bigA * (sigma - deltaSigma)
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
        return abs(shoelace(projectEqualArea(points)))
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
        val area = abs(shoelace(plane))
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

    private fun segmentsIntersect(a: PlanePoint, b: PlanePoint, c: PlanePoint, d: PlanePoint): Boolean {
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
