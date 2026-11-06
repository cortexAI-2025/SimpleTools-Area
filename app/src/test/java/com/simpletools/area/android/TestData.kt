package com.simpletools.area.android

import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.GeoPoint

object TestData {
    val square = listOf(
        GeoPoint(48.8566, 2.3522), GeoPoint(48.856599992, 2.353562739),
        GeoPoint(48.855700768, 2.353562739), GeoPoint(48.85570076, 2.352200024),
    )

    fun parcel(name: String = "Champ test", points: List<GeoPoint> = square, notes: String = "") = ParcelEntity(
        name = name, createdAt = 0, updatedAt = 0, type = MeasureType.MAP, points = points,
        areaM2 = 10000.0895549414, perimeterM = 400.0017910985802, displayUnit = "HECTARE", notes = notes,
    )
}
