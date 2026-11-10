/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.routing

import de.timklge.karooroutegraph.amap.api.AmapApiService
import de.timklge.karooroutegraph.amap.api.AmapPlace
import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate

data class BicycleRouteRequest(
    val origin: GeoCoordinate,
    val destination: GeoCoordinate,
    val alternativeRoutes: Int = 3,
)

class AmapRoutePlanner(
    private val apiService: AmapApiService,
    private val coordinateConverter: CoordinateConverter,
) {
    suspend fun plan(request: BicycleRouteRequest): List<AmapPlannedRoute> {
        val origin = coordinateConverter.convert(request.origin, CoordinateSystem.GCJ02)
        val destination = coordinateConverter.convert(request.destination, CoordinateSystem.GCJ02)
        return apiService.planBicycling(
            origin = origin,
            destination = destination,
            alternativeRoutes = request.alternativeRoutes,
        )
    }

    suspend fun searchDestinations(
        keywords: String,
        region: String? = null,
    ): List<AmapPlace> = apiService.searchPlaces(
        keywords = keywords,
        region = region,
    )
}
