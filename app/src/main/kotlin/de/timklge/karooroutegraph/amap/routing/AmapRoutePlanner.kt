/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.routing

import de.timklge.karooroutegraph.amap.api.AmapApiService
import de.timklge.karooroutegraph.amap.api.AmapNetworkException
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
        val alternativeRoutes = if (
            origin.distanceTo(destination) >= MAX_DISTANCE_FOR_ALTERNATIVES_METERS
        ) {
            1
        } else {
            request.alternativeRoutes
        }
        return try {
            apiService.planBicycling(
                origin = origin,
                destination = destination,
                alternativeRoutes = alternativeRoutes,
            )
        } catch (error: AmapNetworkException) {
            if (!error.isResponseTooLarge()) throw error
            if (alternativeRoutes == 1) {
                throw AmapNetworkException(
                    "AMap route response is too large for Karoo. " +
                        "Try a shorter route or split the trip into multiple routes.",
                    error,
                )
            }
            apiService.planBicycling(
                origin = origin,
                destination = destination,
                alternativeRoutes = 1,
            )
        }
    }

    suspend fun searchPlaces(
        keywords: String,
        region: String? = null,
    ): List<AmapPlace> = apiService.searchPlaces(
        keywords = keywords,
        region = region,
    )

    private fun AmapNetworkException.isResponseTooLarge(): Boolean =
        message.orEmpty().contains("response_too_large", ignoreCase = true)

    private fun GeoCoordinate.distanceTo(other: GeoCoordinate): Double {
        val earthRadiusMeters = 6_371_000.0
        val latitude1 = Math.toRadians(latitude)
        val latitude2 = Math.toRadians(other.latitude)
        val latitudeDelta = latitude2 - latitude1
        val longitudeDelta = Math.toRadians(other.longitude - longitude)
        val haversine = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
            (kotlin.math.cos(latitude1) * kotlin.math.cos(latitude2) *
                kotlin.math.sin(longitudeDelta / 2).let { it * it })
        return 2 * earthRadiusMeters * kotlin.math.asin(kotlin.math.sqrt(haversine))
    }

    private companion object {
        const val MAX_DISTANCE_FOR_ALTERNATIVES_METERS = 100_000.0
    }
}
