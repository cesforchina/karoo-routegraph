/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.coordinates

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class CoordinateSystem { WGS84, GCJ02 }

data class GeoCoordinate(
    val longitude: Double,
    val latitude: Double,
    val coordinateSystem: CoordinateSystem,
) {
    init {
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
    }
}

/**
 * Converts between GPS WGS84 coordinates and the GCJ-02 coordinates used by AMap.
 *
 * AMap provides an official WGS84-to-GCJ-02 service, but does not provide the inverse conversion.
 * This local implementation remains available offline. The transform is only applied inside the
 * commonly used mainland-China coverage bounds.
 */
class CoordinateConverter {
    fun convert(coordinate: GeoCoordinate, target: CoordinateSystem): GeoCoordinate {
        if (coordinate.coordinateSystem == target) return coordinate
        return when (target) {
            CoordinateSystem.GCJ02 -> wgs84ToGcj02(coordinate)
            CoordinateSystem.WGS84 -> gcj02ToWgs84(coordinate)
        }
    }

    fun wgs84ToGcj02(coordinate: GeoCoordinate): GeoCoordinate {
        require(coordinate.coordinateSystem == CoordinateSystem.WGS84) {
            "Expected a WGS84 coordinate"
        }
        if (!isInsideMainlandChinaBounds(coordinate.longitude, coordinate.latitude)) {
            return coordinate.copy(coordinateSystem = CoordinateSystem.GCJ02)
        }
        val (longitudeOffset, latitudeOffset) = offset(
            coordinate.longitude,
            coordinate.latitude,
        )
        return GeoCoordinate(
            longitude = coordinate.longitude + longitudeOffset,
            latitude = coordinate.latitude + latitudeOffset,
            coordinateSystem = CoordinateSystem.GCJ02,
        )
    }

    fun gcj02ToWgs84(coordinate: GeoCoordinate): GeoCoordinate {
        require(coordinate.coordinateSystem == CoordinateSystem.GCJ02) {
            "Expected a GCJ-02 coordinate"
        }
        if (!isInsideMainlandChinaBounds(coordinate.longitude, coordinate.latitude)) {
            return coordinate.copy(coordinateSystem = CoordinateSystem.WGS84)
        }

        var longitude = coordinate.longitude
        var latitude = coordinate.latitude
        repeat(INVERSE_ITERATIONS) {
            val converted = wgs84ToGcj02(
                GeoCoordinate(longitude, latitude, CoordinateSystem.WGS84)
            )
            val longitudeError = converted.longitude - coordinate.longitude
            val latitudeError = converted.latitude - coordinate.latitude
            longitude -= longitudeError
            latitude -= latitudeError
            if (abs(longitudeError) < INVERSE_TOLERANCE_DEGREES &&
                abs(latitudeError) < INVERSE_TOLERANCE_DEGREES
            ) {
                return GeoCoordinate(longitude, latitude, CoordinateSystem.WGS84)
            }
        }
        return GeoCoordinate(longitude, latitude, CoordinateSystem.WGS84)
    }

    fun isInsideMainlandChinaBounds(longitude: Double, latitude: Double): Boolean =
        longitude in MIN_LONGITUDE..MAX_LONGITUDE &&
            latitude in MIN_LATITUDE..MAX_LATITUDE

    private fun offset(longitude: Double, latitude: Double): Pair<Double, Double> {
        var latitudeOffset = transformLatitude(longitude - 105.0, latitude - 35.0)
        var longitudeOffset = transformLongitude(longitude - 105.0, latitude - 35.0)
        val radians = latitude / 180.0 * PI
        var magic = sin(radians)
        magic = 1 - ECCENTRICITY * magic * magic
        val squareRootMagic = sqrt(magic)
        latitudeOffset = latitudeOffset * 180.0 /
            ((SEMI_MAJOR_AXIS * (1 - ECCENTRICITY)) / (magic * squareRootMagic) * PI)
        longitudeOffset = longitudeOffset * 180.0 /
            (SEMI_MAJOR_AXIS / squareRootMagic * cos(radians) * PI)
        return longitudeOffset to latitudeOffset
    }

    private fun transformLatitude(x: Double, y: Double): Double {
        var result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y +
            0.1 * x * y + 0.2 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        result += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        result += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return result
    }

    private fun transformLongitude(x: Double, y: Double): Double {
        var result = 300.0 + x + 2.0 * y + 0.1 * x * x +
            0.1 * x * y + 0.1 * sqrt(abs(x))
        result += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        result += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        result += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return result
    }

    private companion object {
        const val PI = 3.1415926535897932384626
        const val SEMI_MAJOR_AXIS = 6378245.0
        const val ECCENTRICITY = 0.00669342162296594323
        const val MIN_LONGITUDE = 72.004
        const val MAX_LONGITUDE = 137.8347
        const val MIN_LATITUDE = 0.8293
        const val MAX_LATITUDE = 55.8271
        const val INVERSE_ITERATIONS = 10
        const val INVERSE_TOLERANCE_DEGREES = 1e-8
    }
}
