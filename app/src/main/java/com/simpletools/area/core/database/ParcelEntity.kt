package com.simpletools.area.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.simpletools.area.core.geometry.GeoPoint

enum class MeasureType { MAP, GPS, DISTANCE }

/** Parcelle / mesure enregistrée localement. Les points sont sérialisés "lat,lon;lat,lon". */
@Entity(tableName = "parcels")
data class ParcelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val type: MeasureType,
    val points: List<GeoPoint>,
    val areaM2: Double,
    val perimeterM: Double,
    /** Nom de AreaUnitChoice. */
    val displayUnit: String,
    val notes: String = "",
)
