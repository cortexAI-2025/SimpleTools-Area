package com.simpletools.area.core.database

import androidx.room.TypeConverter
import com.simpletools.area.core.geometry.GeoPoint

class Converters {
    @TypeConverter
    fun pointsToString(points: List<GeoPoint>): String = encodePoints(points)

    @TypeConverter
    fun stringToPoints(s: String): List<GeoPoint> = decodePoints(s)

    @TypeConverter
    fun typeToString(t: MeasureType): String = t.name

    @TypeConverter
    fun stringToType(s: String): MeasureType = runCatching { MeasureType.valueOf(s) }.getOrDefault(MeasureType.MAP)

    companion object {
        /** Double.toString est un aller-retour exact : aucune perte de précision. */
        fun encodePoints(points: List<GeoPoint>): String =
            points.joinToString(";") { "${it.lat},${it.lon}" }

        fun decodePoints(s: String): List<GeoPoint> =
            if (s.isBlank()) emptyList()
            else s.split(';').mapNotNull {
                val p = it.split(',')
                val lat = p.getOrNull(0)?.toDoubleOrNull()
                val lon = p.getOrNull(1)?.toDoubleOrNull()
                if (lat != null && lon != null) GeoPoint(lat, lon) else null
            }
    }
}
