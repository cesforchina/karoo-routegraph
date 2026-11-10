/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.timklge.karooroutegraph.LocationViewModelProvider
import de.timklge.karooroutegraph.amap.api.AmapPlace
import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import de.timklge.karooroutegraph.amap.gpx.GpxExportOptions
import de.timklge.karooroutegraph.amap.gpx.GpxExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutePlannerUiState(
    val useCurrentLocation: Boolean = true,
    val originLongitude: String = "",
    val originLatitude: String = "",
    val destinationLongitude: String = "",
    val destinationLatitude: String = "",
    val destinationSystem: CoordinateSystem = CoordinateSystem.WGS84,
    val destinationQuery: String = "",
    val searchRegion: String = "",
    val searchResults: List<AmapPlace> = emptyList(),
    val routes: List<AmapPlannedRoute> = emptyList(),
    val selectedRouteIndex: Int? = null,
    val isSearching: Boolean = false,
    val isPlanning: Boolean = false,
    val errorMessage: String? = null,
) {
    val selectedRoute: AmapPlannedRoute?
        get() = selectedRouteIndex?.let(routes::getOrNull)
}

class RoutePlannerViewModel(
    private val routePlanner: AmapRoutePlanner,
    private val locationViewModelProvider: LocationViewModelProvider,
    private val gpxExporter: GpxExporter,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RoutePlannerUiState())
    val state = mutableState.asStateFlow()

    fun setUseCurrentLocation(value: Boolean) {
        mutableState.update { it.copy(useCurrentLocation = value, errorMessage = null) }
    }

    fun setOriginLongitude(value: String) {
        mutableState.update { it.copy(originLongitude = value, errorMessage = null) }
    }

    fun setOriginLatitude(value: String) {
        mutableState.update { it.copy(originLatitude = value, errorMessage = null) }
    }

    fun setDestinationLongitude(value: String) {
        mutableState.update {
            it.copy(
                destinationLongitude = value,
                destinationSystem = CoordinateSystem.WGS84,
                errorMessage = null,
            )
        }
    }

    fun setDestinationLatitude(value: String) {
        mutableState.update {
            it.copy(
                destinationLatitude = value,
                destinationSystem = CoordinateSystem.WGS84,
                errorMessage = null,
            )
        }
    }

    fun setDestinationQuery(value: String) {
        mutableState.update { it.copy(destinationQuery = value, errorMessage = null) }
    }

    fun setSearchRegion(value: String) {
        mutableState.update { it.copy(searchRegion = value, errorMessage = null) }
    }

    fun searchDestinations() {
        val snapshot = mutableState.value
        if (snapshot.destinationQuery.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Enter a destination to search") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(isSearching = true, errorMessage = null) }
            try {
                val results = routePlanner.searchDestinations(
                    keywords = snapshot.destinationQuery.trim(),
                    region = snapshot.searchRegion.trim().ifBlank { null },
                )
                mutableState.update {
                    it.copy(
                        searchResults = results,
                        isSearching = false,
                        errorMessage = if (results.isEmpty()) "No destinations found" else null,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = error.message ?: "Destination search failed",
                    )
                }
            }
        }
    }

    fun selectDestination(place: AmapPlace) {
        mutableState.update {
            it.copy(
                destinationQuery = place.name,
                destinationLongitude = place.coordinate.longitude.toString(),
                destinationLatitude = place.coordinate.latitude.toString(),
                destinationSystem = place.coordinate.coordinateSystem,
                searchResults = emptyList(),
                routes = emptyList(),
                selectedRouteIndex = null,
                errorMessage = null,
            )
        }
    }

    fun planRoutes() {
        val snapshot = mutableState.value
        val origin = try {
            if (snapshot.useCurrentLocation) {
                val position = locationViewModelProvider.viewModelFlow.value
                    ?: error("Current GPS position is not available")
                GeoCoordinate(
                    longitude = position.longitude(),
                    latitude = position.latitude(),
                    coordinateSystem = CoordinateSystem.WGS84,
                )
            } else {
                parseCoordinate(
                    longitude = snapshot.originLongitude,
                    latitude = snapshot.originLatitude,
                    system = CoordinateSystem.WGS84,
                    label = "origin",
                )
            }
        } catch (error: IllegalStateException) {
            mutableState.update { it.copy(errorMessage = error.message) }
            return
        } catch (error: IllegalArgumentException) {
            mutableState.update { it.copy(errorMessage = error.message) }
            return
        }

        val destination = try {
            parseCoordinate(
                longitude = snapshot.destinationLongitude,
                latitude = snapshot.destinationLatitude,
                system = snapshot.destinationSystem,
                label = "destination",
            )
        } catch (error: IllegalArgumentException) {
            mutableState.update { it.copy(errorMessage = error.message) }
            return
        }

        viewModelScope.launch {
            mutableState.update {
                it.copy(
                    isPlanning = true,
                    routes = emptyList(),
                    selectedRouteIndex = null,
                    errorMessage = null,
                )
            }
            try {
                val routes = routePlanner.plan(
                    BicycleRouteRequest(
                        origin = origin,
                        destination = destination,
                    )
                )
                mutableState.update {
                    it.copy(
                        routes = routes,
                        selectedRouteIndex = routes.indices.firstOrNull(),
                        isPlanning = false,
                        errorMessage = if (routes.isEmpty()) "No bicycle route found" else null,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update {
                    it.copy(
                        isPlanning = false,
                        errorMessage = error.message ?: "Route planning failed",
                    )
                }
            }
        }
    }

    fun selectRoute(index: Int) {
        mutableState.update {
            if (index in it.routes.indices) {
                it.copy(selectedRouteIndex = index, errorMessage = null)
            } else {
                it
            }
        }
    }

    fun exportSelectedRoute(routeName: String): ByteArray {
        val route = mutableState.value.selectedRoute
            ?: error("Select a route before exporting")
        return gpxExporter.export(
            route,
            GpxExportOptions(routeName = routeName.ifBlank { "AMap bicycle route" }),
        )
    }

    private fun parseCoordinate(
        longitude: String,
        latitude: String,
        system: CoordinateSystem,
        label: String,
    ): GeoCoordinate {
        val parsedLongitude = longitude.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Enter a valid $label longitude")
        val parsedLatitude = latitude.trim().toDoubleOrNull()
            ?: throw IllegalArgumentException("Enter a valid $label latitude")
        return try {
            GeoCoordinate(parsedLongitude, parsedLatitude, system)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("The $label coordinate is outside valid bounds")
        }
    }
}
