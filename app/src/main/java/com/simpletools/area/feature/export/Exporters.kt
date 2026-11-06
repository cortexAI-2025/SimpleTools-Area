package com.simpletools.area.feature.export

import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.GeoPoint
import java.util.Locale

/** Exports texte (CSV, GeoJSON). Indépendants d'Android → testables en JVM. */
object Exporters {
    fun toCsv(parcel: ParcelEntity): String {
        val sb = StringBuilder("index,latitude,longitude\r\n")
        parcel.points.forEachIndexed { i, p ->
            sb.append(i + 1).append(',').append(fmt(p.lat)).append(',').append(fmt(p.lon)).append("\r\n")
        }
        return sb.toString()
    }

    fun toGeoJson(parcel: ParcelEntity): String {
        val geometry = if (parcel.type == MeasureType.DISTANCE || parcel.points.size < 3) {
            """{"type":"LineString","coordinates":[${parcel.points.joinToString(",") { coord(it) }}]}"""
        } else {
            val ring = closedCcwRing(parcel.points)
            """{"type":"Polygon","coordinates":[[${ring.joinToString(",") { coord(it) }}]]}"""
        }
        val props = buildString {
            append("{")
            append("\"name\":").append(jsonString(parcel.name)).append(',')
            append("\"type\":").append(jsonString(parcel.type.name)).append(',')
            append("\"area_m2\":").append(fmt(parcel.areaM2)).append(',')
            append("\"perimeter_m\":").append(fmt(parcel.perimeterM)).append(',')
            append("\"created_at\":").append(parcel.createdAt).append(',')
            append("\"notes\":").append(jsonString(parcel.notes))
            append("}")
        }
        return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":$props,"geometry":$geometry}]}"""
    }

    /** Anneau fermé, orienté anti-horaire (RFC 7946 §3.1.6). */
    internal fun closedCcwRing(points: List<GeoPoint>): List<GeoPoint> {
        val plane = GeoMath.projectEqualArea(points)
        val ccw = if (GeoMath.shoelace(plane) < 0) points.reversed() else points
        return ccw + ccw.first()
    }

    private fun coord(p: GeoPoint) = "[${fmt(p.lon)},${fmt(p.lat)}]" // GeoJSON : [lon, lat]

    private fun fmt(v: Double): String = String.format(Locale.ROOT, "%s", v.toBigDecimalPlain())

    private fun Double.toBigDecimalPlain(): String = java.math.BigDecimal(this.toString()).toPlainString()

    internal fun jsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
        }
        return sb.append('"').toString()
    }
}
