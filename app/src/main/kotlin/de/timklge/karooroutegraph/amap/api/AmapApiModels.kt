/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.api

import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class AmapBicyclingResponse(
    val status: String,
    val info: String? = null,
    val infocode: String? = null,
    val count: String? = null,
    val route: AmapRouteResponse? = null,
)

@Serializable
internal data class AmapRouteResponse(
    val origin: String? = null,
    val destination: String? = null,
    val paths: List<AmapPathResponse> = emptyList(),
)

@Serializable
internal data class AmapPathResponse(
    val distance: String? = null,
    val duration: String? = null,
    val cost: AmapCostResponse? = null,
    val steps: List<AmapStepResponse> = emptyList(),
)

@Serializable
internal data class AmapCostResponse(val duration: String? = null)

@Serializable
internal data class AmapStepResponse(
    val instruction: String? = null,
    val orientation: String? = null,
    @SerialName("road_name") val roadName: String? = null,
    @SerialName("step_distance") val stepDistance: String? = null,
    val polyline: String? = null,
)

@Serializable
internal data class AmapPlaceSearchResponse(
    val status: String,
    val info: String? = null,
    val infocode: String? = null,
    val count: String? = null,
    val pois: List<AmapPlaceResponse> = emptyList(),
)

@Serializable
internal data class AmapPlaceResponse(
    val id: String? = null,
    val name: String? = null,
    val location: String? = null,
    val type: String? = null,
    val address: JsonElement? = null,
    val pname: String? = null,
    val cityname: String? = null,
    val adname: String? = null,
)

data class AmapRouteStep(
    val instruction: String?,
    val roadName: String?,
    val distanceMeters: Long?,
    val points: List<GeoCoordinate>,
)

data class AmapPlannedRoute(
    val id: String,
    val distanceMeters: Long,
    val durationSeconds: Long?,
    val points: List<GeoCoordinate>,
    val steps: List<AmapRouteStep>,
)

data class AmapPlace(
    val id: String,
    val name: String,
    val coordinate: GeoCoordinate,
    val address: String?,
    val type: String?,
    val province: String?,
    val city: String?,
    val district: String?,
)
