package com.simpletools.area.feature.projects

import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.core.geometry.PlanePoint
import com.simpletools.area.core.geometry.PolygonResult

internal data class ProjectParcel(
    val name: String,
    val category: String,
    val boundary: List<GeoPoint>,
    val exclusions: List<List<GeoPoint>> = emptyList(),
) {
    val netAreaM2: Double by lazy { GeoMath.areaM2(boundary) - exclusions.sumOf(GeoMath::areaM2) }
    val fenceM: Double by lazy { GeoMath.perimeterM(boundary) }
    val perimeterM: Double by lazy { fenceM + exclusions.sumOf { GeoMath.perimeterM(it) } }
}

internal data class AreaProject(val id: String, val name: String, val parcels: List<ProjectParcel> = emptyList()) {
    val areaM2 get() = parcels.sumOf(ProjectParcel::netAreaM2)
    val perimeterM get() = parcels.sumOf(ProjectParcel::perimeterM)
}

internal fun projectParcel(name: String, category: String, outer: List<GeoPoint>, holes: List<List<GeoPoint>>): ProjectParcel {
    require(name.isNotBlank() && name.length <= 200 && category.length <= 100)
    require(holes.size <= 32)
    val boundary = validRing(outer)
    val exclusions = holes.map(::validRing)
    if (exclusions.isNotEmpty()) {
        val center = GeoMath.centerOf(boundary)
        val plane = GeoMath.projectEqualArea(boundary, center)
        val projectedHoles = exclusions.map { GeoMath.projectEqualArea(it, center) }
        projectedHoles.forEachIndexed { i, hole ->
            require(hole.all { inside(it, plane) } && !intersect(hole, plane))
            require(projectedHoles.take(i).none { other ->
                intersect(hole, other) || inside(hole.first(), other) || inside(other.first(), hole)
            })
        }
    }
    return ProjectParcel(name, category, boundary, exclusions).also { require(it.netAreaM2 > 0) }
}

private fun validRing(points: List<GeoPoint>): List<GeoPoint> {
    require(points.size in 3..5_000)
    return (GeoMath.validatePolygon(points) as? PolygonResult.Valid)?.points ?: throw IllegalArgumentException("Invalid ring")
}

private fun intersect(a: List<PlanePoint>, b: List<PlanePoint>) = a.indices.any { i ->
    b.indices.any { j -> GeoMath.segmentsIntersect(a[i], a[(i + 1) % a.size], b[j], b[(j + 1) % b.size]) }
}

private fun inside(point: PlanePoint, ring: List<PlanePoint>): Boolean {
    var inside = false
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        if ((a.y > point.y) != (b.y > point.y) && point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x) {
            inside = !inside
        }
    }
    return inside
}
