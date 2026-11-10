/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.coordinates

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoordinateConverterTest {
    private val converter = CoordinateConverter()

    @Test
    fun convertsKnownBeijingCoordinateToGcj02() {
        val wgs84 = GeoCoordinate(
            longitude = 116.397389,
            latitude = 39.908722,
            coordinateSystem = CoordinateSystem.WGS84,
        )

        val gcj02 = converter.wgs84ToGcj02(wgs84)

        assertEquals(CoordinateSystem.GCJ02, gcj02.coordinateSystem)
        assertTrue(abs(gcj02.longitude - 116.40363) < 0.0001)
        assertTrue(abs(gcj02.latitude - 39.91012) < 0.0001)
    }

    @Test
    fun inverseConversionRoundTripsWithinOneMeter() {
        val original = GeoCoordinate(
            longitude = 121.473701,
            latitude = 31.230416,
            coordinateSystem = CoordinateSystem.WGS84,
        )

        val restored = converter.gcj02ToWgs84(converter.wgs84ToGcj02(original))

        assertTrue(abs(restored.longitude - original.longitude) < 0.00001)
        assertTrue(abs(restored.latitude - original.latitude) < 0.00001)
    }

    @Test
    fun coordinatesOutsideChinaAreNotOffset() {
        val london = GeoCoordinate(
            longitude = -0.1276,
            latitude = 51.5072,
            coordinateSystem = CoordinateSystem.WGS84,
        )

        val converted = converter.wgs84ToGcj02(london)

        assertEquals(london.longitude, converted.longitude)
        assertEquals(london.latitude, converted.latitude)
        assertEquals(CoordinateSystem.GCJ02, converted.coordinateSystem)
    }
}
