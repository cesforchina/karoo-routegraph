/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.api

import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AmapApiServiceTest {
    private val origin = GeoCoordinate(116.466485, 39.995197, CoordinateSystem.GCJ02)
    private val destination = GeoCoordinate(116.46424, 40.020642, CoordinateSystem.GCJ02)

    @Test
    fun parsesCandidateRouteAndCompletePolyline() = runBlocking {
        var requestedUrl = ""
        val transport = AmapTransport { url ->
            requestedUrl = url
            AmapHttpResponse(
                200,
                """
                {
                  "status":"1",
                  "info":"OK",
                  "infocode":"10000",
                  "count":"1",
                  "route":{
                    "origin":"116.466485,39.995197",
                    "destination":"116.464240,40.020642",
                    "paths":[{
                      "distance":"3210",
                      "cost":{"duration":"900"},
                      "steps":[
                        {
                          "instruction":"向北骑行",
                          "road_name":"示例路",
                          "step_distance":"1000",
                          "polyline":"116.466485,39.995197;116.465000,40.000000"
                        },
                        {
                          "instruction":"继续骑行",
                          "step_distance":"2210",
                          "polyline":"116.465000,40.000000;116.464240,40.020642"
                        }
                      ]
                    }]
                  }
                }
                """.trimIndent(),
            )
        }
        val service = AmapApiService("test-key", transport)

        val routes = service.planBicycling(origin, destination)

        assertEquals(1, routes.size)
        assertEquals(3210, routes.single().distanceMeters)
        assertEquals(900, routes.single().durationSeconds)
        assertEquals(3, routes.single().points.size)
        assertEquals(CoordinateSystem.GCJ02, routes.single().points.first().coordinateSystem)
        assertTrue(requestedUrl.contains("/v5/direction/bicycling"))
        assertTrue(requestedUrl.contains("show_fields=cost%2Cnavi%2Cpolyline"))
    }

    @Test
    fun mapsAmapQuotaErrorsToRateLimitException() {
        val service = AmapApiService(
            apiKey = "test-key",
            transport = AmapTransport {
                AmapHttpResponse(
                    200,
                    """{"status":"0","info":"ACCESS_TOO_FREQUENT","infocode":"10004"}""",
                )
            },
        )

        val error = assertFailsWith<AmapRateLimitException> {
            runBlocking { service.planBicycling(origin, destination) }
        }

        assertEquals("10004", error.infoCode)
    }

    @Test
    fun rejectsMissingKeyBeforeNetworkRequest() {
        val service = AmapApiService(
            apiKey = "",
            transport = AmapTransport {
                error("Transport must not be called")
            },
        )

        assertFailsWith<AmapMissingKeyException> {
            runBlocking { service.planBicycling(origin, destination) }
        }
    }
}
