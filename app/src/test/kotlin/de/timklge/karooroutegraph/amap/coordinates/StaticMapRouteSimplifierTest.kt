/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.coordinates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticMapRouteSimplifierTest {
    @Test
    fun retainsEveryPointWhenAlreadyBelowLimit() {
        val coordinates = listOf(
            coordinate(114.0, 22.0),
            coordinate(114.1, 22.1),
            coordinate(114.2, 22.2),
        )

        val simplified = StaticMapRouteSimplifier.simplify(
            coordinates = coordinates,
            zoom = 12,
            maxPoints = 10,
        )

        assertEquals(coordinates.map { it.longitude to it.latitude }, simplified)
    }

    @Test
    fun preservesSharpTurnWhileRespectingPointLimit() {
        val coordinates = buildList {
            repeat(150) { index -> add(coordinate(114.0 + index * 0.0001, 22.0)) }
            add(coordinate(114.015, 22.03))
            repeat(150) { index -> add(coordinate(114.015 + index * 0.0001, 22.0)) }
        }

        val simplified = StaticMapRouteSimplifier.simplify(
            coordinates = coordinates,
            zoom = 14,
            maxPoints = 20,
        )

        assertTrue(simplified.size <= 20)
        assertEquals(coordinates.first().longitude to coordinates.first().latitude, simplified.first())
        assertEquals(coordinates.last().longitude to coordinates.last().latitude, simplified.last())
        assertTrue(simplified.contains(114.015 to 22.03))
    }

    private fun coordinate(longitude: Double, latitude: Double) = GeoCoordinate(
        longitude = longitude,
        latitude = latitude,
        coordinateSystem = CoordinateSystem.GCJ02,
    )
}
