/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.routing

import de.timklge.karooroutegraph.amap.api.AmapApiService
import de.timklge.karooroutegraph.amap.api.AmapHttpResponse
import de.timklge.karooroutegraph.amap.api.AmapNetworkException
import de.timklge.karooroutegraph.amap.api.AmapTransport
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertTrue

class AmapRoutePlannerTest {
    @Test
    fun requestsSingleCandidateForLongRoutes() = runBlocking {
        var requestedUrl = ""
        val planner = AmapRoutePlanner(
            apiService = AmapApiService("test-key", AmapTransport { url ->
                requestedUrl = url
                successfulRouteResponse()
            }),
            coordinateConverter = CoordinateConverter(),
        )

        planner.plan(
            BicycleRouteRequest(
                origin = GeoCoordinate(116.4074, 39.9042, CoordinateSystem.GCJ02),
                destination = GeoCoordinate(121.4737, 31.2304, CoordinateSystem.GCJ02),
            )
        )

        assertTrue(requestedUrl.contains("alternative_route=1"))
    }

    @Test
    fun keepsRequestedCandidatesForShortRoutes() = runBlocking {
        var requestedUrl = ""
        val planner = AmapRoutePlanner(
            apiService = AmapApiService("test-key", AmapTransport { url ->
                requestedUrl = url
                successfulRouteResponse()
            }),
            coordinateConverter = CoordinateConverter(),
        )

        planner.plan(
            BicycleRouteRequest(
                origin = GeoCoordinate(116.4074, 39.9042, CoordinateSystem.GCJ02),
                destination = GeoCoordinate(116.4974, 39.9042, CoordinateSystem.GCJ02),
                alternativeRoutes = 3,
            )
        )

        assertTrue(requestedUrl.contains("alternative_route=3"))
    }

    @Test
    fun retriesOneCandidateWhenKarooRejectsLargeResponse() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        val planner = AmapRoutePlanner(
            apiService = AmapApiService("test-key", AmapTransport { url ->
                requestedUrls += url
                if (requestedUrls.size == 1) {
                    throw AmapNetworkException("response_too_large")
                }
                successfulRouteResponse()
            }),
            coordinateConverter = CoordinateConverter(),
        )

        planner.plan(
            BicycleRouteRequest(
                origin = GeoCoordinate(116.4074, 39.9042, CoordinateSystem.GCJ02),
                destination = GeoCoordinate(116.4974, 39.9042, CoordinateSystem.GCJ02),
                alternativeRoutes = 3,
            )
        )

        assertTrue(requestedUrls.size == 2)
        assertTrue(requestedUrls[0].contains("alternative_route=3"))
        assertTrue(requestedUrls[1].contains("alternative_route=1"))
    }

    private fun successfulRouteResponse() = AmapHttpResponse(
        statusCode = 200,
        body = """
            {
              "status":"1",
              "info":"OK",
              "infocode":"10000",
              "route":{
                "paths":[{
                  "distance":"1000",
                  "cost":{"duration":"300"},
                  "steps":[{
                    "step_distance":"1000",
                    "polyline":"116.407400,39.904200;116.417400,39.904200"
                  }]
                }]
              }
            }
        """.trimIndent(),
    )
}
