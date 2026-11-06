package com.simpletools.area.core.geometry

/** Coordonnée WGS84 en degrés décimaux. */
data class GeoPoint(val lat: Double, val lon: Double) {
    val isValid: Boolean
        get() = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
}

/** Point plan en mètres (projection locale). */
data class PlanePoint(val x: Double, val y: Double)
