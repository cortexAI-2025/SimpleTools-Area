package com.simpletools.area.feature.projects

import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import net.sf.geographiclib.Geodesic
import net.sf.geographiclib.GeodesicMask

internal data class ParcelDivision(val targetAreaM2: Double, val cutLongitudes: List<Double>)

/**
 * Equal net-area bands bounded by meridians. Clipping may connect disjoint pieces along the
 * meridian. Those coincident arcs cancel in the signed geodesic area.
 */
internal fun ProjectParcel.divide(parts: Int): ParcelDivision {
    require(parts in 2..20)
    val origin = boundary.first().lon
    fun unwrap(lon: Double) = (lon - origin + 540) % 360 - 180
    val rings = (listOf(boundary) + exclusions).map { ring ->
        ring.mapIndexed { i, from ->
            val to = ring[(i + 1) % ring.size]
            DivisionEdge(from, to, unwrap(from.lon), unwrap(to.lon))
        }
    }
    val left = rings.first().minOf { it.fromX }
    val right = rings.first().maxOf { it.fromX }
    require(right - left in 1e-9..1.0 && rings.flatten().all { it.from.lat in -85.0..85.0 })
    require(boundary.maxOf { it.lat } - boundary.minOf { it.lat } <= 1.0)

    fun areaLeftOf(cut: Double): Double = rings.map { edges ->
        val clipped = buildList {
            edges.forEach { edge ->
                val fromInside = edge.fromX <= cut
                val toInside = edge.toX <= cut
                if (fromInside) add(edge.from)
                if (fromInside != toInside) add(edge.intersection(cut))
            }
        }
        GeoMath.areaM2(clipped)
    }.let { it.first() - it.drop(1).sum() }

    val target = netAreaM2 / parts
    val cuts = (1 until parts).map { part ->
        var low = left
        var high = right
        repeat(36) {
            val cut = (low + high) / 2
            if (areaLeftOf(cut) < part * target) low = cut else high = cut
        }
        (origin + (low + high) / 2 + 540) % 360 - 180
    }
    return ParcelDivision(target, cuts)
}

private class DivisionEdge(val from: GeoPoint, val to: GeoPoint, val fromX: Double, val toX: Double) {
    private val line by lazy { Geodesic.WGS84.InverseLine(from.lat, from.lon, to.lat, to.lon) }

    fun intersection(cut: Double): GeoPoint {
        val increasing = toX > fromX
        var low = 0.0
        var high = line.Distance()
        repeat(35) {
            val distance = (low + high) / 2
            val at = line.Position(distance, GeodesicMask.LONGITUDE or GeodesicMask.LONG_UNROLL)
            val x = fromX + (at.lon2 - from.lon)
            if ((x < cut) == increasing) low = distance else high = distance
        }
        val at = line.Position((low + high) / 2, GeodesicMask.LATITUDE)
        return GeoPoint(at.lat2, (from.lon + cut - fromX + 540) % 360 - 180)
    }
}
