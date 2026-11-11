/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.coordinates

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

internal object StaticMapRouteSimplifier {
    private const val DEFAULT_PIXEL_TOLERANCE = 0.75
    private const val MAX_TOLERANCE_SEARCH_STEPS = 20

    fun simplify(
        coordinates: List<GeoCoordinate>,
        zoom: Int,
        maxPoints: Int,
    ): List<Pair<Double, Double>> {
        require(maxPoints >= 2) { "At least two route points must be retained" }
        if (coordinates.size <= maxPoints) {
            return coordinates.map { it.longitude to it.latitude }
        }

        val projected = coordinates.map { coordinate ->
            ProjectedPoint(
                x = longitudeToWorldPixel(coordinate.longitude, zoom),
                y = latitudeToWorldPixel(coordinate.latitude, zoom),
            )
        }

        var lowerTolerance = DEFAULT_PIXEL_TOLERANCE
        var upperTolerance = DEFAULT_PIXEL_TOLERANCE
        var retainedIndices = simplifyIndices(projected, upperTolerance)
        while (retainedIndices.size > maxPoints) {
            lowerTolerance = upperTolerance
            upperTolerance *= 2.0
            retainedIndices = simplifyIndices(projected, upperTolerance)
        }

        repeat(MAX_TOLERANCE_SEARCH_STEPS) {
            val tolerance = (lowerTolerance + upperTolerance) / 2.0
            val candidate = simplifyIndices(projected, tolerance)
            if (candidate.size > maxPoints) {
                lowerTolerance = tolerance
            } else {
                upperTolerance = tolerance
                retainedIndices = candidate
            }
        }

        return retainedIndices.map { index ->
            coordinates[index].longitude to coordinates[index].latitude
        }
    }

    private fun simplifyIndices(
        points: List<ProjectedPoint>,
        tolerance: Double,
    ): List<Int> {
        val retained = BooleanArray(points.size)
        retained[0] = true
        retained[points.lastIndex] = true
        val ranges = ArrayDeque<Pair<Int, Int>>()
        ranges.addLast(0 to points.lastIndex)
        val squaredTolerance = tolerance * tolerance

        while (ranges.isNotEmpty()) {
            val (startIndex, endIndex) = ranges.removeLast()
            var furthestIndex = -1
            var furthestSquaredDistance = squaredTolerance
            for (index in startIndex + 1 until endIndex) {
                val squaredDistance = squaredDistanceToSegment(
                    point = points[index],
                    start = points[startIndex],
                    end = points[endIndex],
                )
                if (squaredDistance > furthestSquaredDistance) {
                    furthestSquaredDistance = squaredDistance
                    furthestIndex = index
                }
            }
            if (furthestIndex >= 0) {
                retained[furthestIndex] = true
                ranges.addLast(startIndex to furthestIndex)
                ranges.addLast(furthestIndex to endIndex)
            }
        }

        return retained.indices.filter(retained::get)
    }

    private fun squaredDistanceToSegment(
        point: ProjectedPoint,
        start: ProjectedPoint,
        end: ProjectedPoint,
    ): Double {
        val segmentX = end.x - start.x
        val segmentY = end.y - start.y
        val squaredLength = segmentX * segmentX + segmentY * segmentY
        if (squaredLength == 0.0) return squaredDistance(point, start)

        val projection = (
            (point.x - start.x) * segmentX +
                (point.y - start.y) * segmentY
            ) / squaredLength
        val nearest = ProjectedPoint(
            x = start.x + projection.coerceIn(0.0, 1.0) * segmentX,
            y = start.y + projection.coerceIn(0.0, 1.0) * segmentY,
        )
        return squaredDistance(point, nearest)
    }

    private fun squaredDistance(first: ProjectedPoint, second: ProjectedPoint): Double {
        val deltaX = first.x - second.x
        val deltaY = first.y - second.y
        return deltaX * deltaX + deltaY * deltaY
    }

    private fun longitudeToWorldPixel(longitude: Double, zoom: Int): Double =
        (longitude + 180.0) / 360.0 * worldSize(zoom)

    private fun latitudeToWorldPixel(latitude: Double, zoom: Int): Double {
        val limitedLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
        val radians = Math.toRadians(limitedLatitude)
        return (
            1.0 -
                ln(tan(radians) + 1.0 / cos(radians)) / PI
            ) / 2.0 * worldSize(zoom)
    }

    private fun worldSize(zoom: Int): Double = 256.0 * 2.0.pow(zoom)

    private data class ProjectedPoint(
        val x: Double,
        val y: Double,
    )
}
