/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.api

import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.net.URLEncoder
import java.util.Locale

class AmapApiService(
    private val apiKey: String,
    private val transport: AmapTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    suspend fun planBicycling(
        origin: GeoCoordinate,
        destination: GeoCoordinate,
        alternativeRoutes: Int = 3,
    ): List<AmapPlannedRoute> {
        require(origin.coordinateSystem == CoordinateSystem.GCJ02) {
            "AMap bicycling origin must use GCJ-02"
        }
        require(destination.coordinateSystem == CoordinateSystem.GCJ02) {
            "AMap bicycling destination must use GCJ-02"
        }
        require(alternativeRoutes in 1..3) {
            "AMap supports between 1 and 3 alternative routes"
        }
        checkKey()

        val response = request(
            path = "/v5/direction/bicycling",
            parameters = mapOf(
                "key" to apiKey,
                "origin" to origin.toAmapParameter(),
                "destination" to destination.toAmapParameter(),
                "alternative_route" to alternativeRoutes.toString(),
                "show_fields" to "cost,polyline",
                "output" to "json",
            ),
        )

        val payload = decode<AmapBicyclingResponse>(response.body)
        validateApiStatus(payload.status, payload.info, payload.infocode)
        val paths = payload.route?.paths.orEmpty()
        if (paths.isEmpty()) {
            throw AmapInvalidResponseException("Successful response did not contain a route")
        }

        return paths.mapIndexed { index, path ->
            val steps = path.steps.map { step ->
                AmapRouteStep(
                    instruction = step.instruction,
                    roadName = step.roadName,
                    distanceMeters = step.stepDistance?.toLongOrNull(),
                    points = parsePolyline(step.polyline),
                )
            }
            val points = steps
                .flatMap(AmapRouteStep::points)
                .fold(mutableListOf<GeoCoordinate>()) { accumulated, point ->
                    if (accumulated.lastOrNull() != point) accumulated.add(point)
                    accumulated
                }
            if (points.size < 2) {
                throw AmapInvalidResponseException("Route ${index + 1} has no usable polyline")
            }
            AmapPlannedRoute(
                id = "amap-route-${index + 1}",
                distanceMeters = path.distance?.toLongOrNull()
                    ?: throw AmapInvalidResponseException(
                        "Route ${index + 1} has no distance"
                    ),
                durationSeconds = path.cost?.duration?.toLongOrNull()
                    ?: path.duration?.toLongOrNull(),
                points = points,
                steps = steps,
            )
        }
    }

    suspend fun searchPlaces(
        keywords: String,
        region: String? = null,
        pageNumber: Int = 1,
        pageSize: Int = 20,
    ): List<AmapPlace> {
        require(keywords.isNotBlank()) { "Search keywords must not be blank" }
        require(keywords.length <= 80) { "AMap search keywords must not exceed 80 characters" }
        require(pageNumber > 0) { "Page number must be positive" }
        require(pageSize in 1..25) { "Page size must be between 1 and 25" }
        checkKey()

        val parameters = buildMap {
            put("key", apiKey)
            put("keywords", keywords)
            put("page_num", pageNumber.toString())
            put("page_size", pageSize.toString())
            put("show_fields", "business")
            put("output", "json")
            if (!region.isNullOrBlank()) put("region", region)
        }
        val response = request("/v5/place/text", parameters)
        val payload = decode<AmapPlaceSearchResponse>(response.body)
        validateApiStatus(payload.status, payload.info, payload.infocode)

        return payload.pois.mapNotNull { place ->
            val coordinate = parseCoordinate(place.location) ?: return@mapNotNull null
            val id = place.id ?: return@mapNotNull null
            val name = place.name ?: return@mapNotNull null
            AmapPlace(
                id = id,
                name = name,
                coordinate = coordinate,
                address = place.address.asDisplayString(),
                type = place.type,
                province = place.pname,
                city = place.cityname,
                district = place.adname,
            )
        }
    }

    private suspend fun request(
        path: String,
        parameters: Map<String, String>,
    ): AmapHttpResponse {
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val response = transport.get("$BASE_URL$path?$query")
        when {
            response.statusCode == 429 ->
                throw AmapRateLimitException(null, "HTTP 429")
            response.statusCode !in 200..299 ->
                throw AmapServiceException(null, "HTTP ${response.statusCode}")
        }
        return response
    }

    private inline fun <reified T> decode(body: String): T {
        return try {
            json.decodeFromString<T>(body)
        } catch (error: SerializationException) {
            throw AmapInvalidResponseException("Response JSON could not be decoded", error)
        } catch (error: IllegalArgumentException) {
            throw AmapInvalidResponseException("Response contained invalid values", error)
        }
    }

    private fun validateApiStatus(status: String, info: String?, infoCode: String?) {
        if (status == "1" && infoCode == "10000") return
        val detail = info ?: "Unknown error"
        if (infoCode == "20803") {
            throw AmapServiceException(
                infoCode,
                "The selected endpoints exceed AMap's bicycling range. " +
                    "Try a closer endpoint or split the trip into multiple routes.",
            )
        }
        if (infoCode in RATE_LIMIT_CODES) {
            throw AmapRateLimitException(infoCode, detail)
        }
        throw AmapServiceException(infoCode, detail)
    }

    private fun parsePolyline(polyline: String?): List<GeoCoordinate> {
        if (polyline.isNullOrBlank()) return emptyList()
        return polyline.split(';').mapNotNull(::parseCoordinate)
    }

    private fun parseCoordinate(value: String?): GeoCoordinate? {
        val parts = value?.split(',') ?: return null
        if (parts.size != 2) return null
        val longitude = parts[0].toDoubleOrNull() ?: return null
        val latitude = parts[1].toDoubleOrNull() ?: return null
        return try {
            GeoCoordinate(longitude, latitude, CoordinateSystem.GCJ02)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun checkKey() {
        if (apiKey.isBlank()) throw AmapMissingKeyException()
    }

    private fun GeoCoordinate.toAmapParameter(): String =
        String.format(Locale.US, "%.6f,%.6f", longitude, latitude)

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JsonElement?.asDisplayString(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull?.takeIf(String::isNotBlank)
            is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .filter(String::isNotBlank)
                .joinToString()
                .takeIf(String::isNotBlank)
            else -> null
        }
    }

    private companion object {
        const val BASE_URL = "https://restapi.amap.com"
        val RATE_LIMIT_CODES = setOf(
            "10003", "10004", "10010", "10014", "10015", "10019",
            "10020", "10021", "10029", "10044", "10045", "40000",
        )
    }
}
