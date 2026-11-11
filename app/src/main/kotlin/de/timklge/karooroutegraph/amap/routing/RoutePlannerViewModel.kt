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
import de.timklge.karooroutegraph.amap.storage.RoutePlannerStorage
import de.timklge.karooroutegraph.amap.storage.RouteSearchHistoryEntry
import de.timklge.karooroutegraph.amap.storage.SavedPlannedRoute
import de.timklge.karooroutegraph.amap.storage.SearchEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoutePlannerUiState(
    val useCurrentLocation: Boolean = true,
    val originLongitude: String = "",
    val originLatitude: String = "",
    val originSystem: CoordinateSystem = CoordinateSystem.WGS84,
    val originQuery: String = "",
    val originSearchRegion: String = "",
    val originSearchResults: List<AmapPlace> = emptyList(),
    val destinationLongitude: String = "",
    val destinationLatitude: String = "",
    val destinationSystem: CoordinateSystem = CoordinateSystem.WGS84,
    val destinationQuery: String = "",
    val searchRegion: String = "",
    val searchResults: List<AmapPlace> = emptyList(),
    val routes: List<AmapPlannedRoute> = emptyList(),
    val selectedRouteIndex: Int? = null,
    val isSearchingOrigin: Boolean = false,
    val isSearchingDestination: Boolean = false,
    val isPlanning: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val searchHistory: List<RouteSearchHistoryEntry> = emptyList(),
    val savedRoutes: List<SavedPlannedRoute> = emptyList(),
) {
    val isSearching: Boolean
        get() = isSearchingOrigin || isSearchingDestination

    val selectedRoute: AmapPlannedRoute?
        get() = selectedRouteIndex?.let(routes::getOrNull)
}

class RoutePlannerViewModel(
    private val routePlanner: AmapRoutePlanner,
    private val locationViewModelProvider: LocationViewModelProvider,
    private val gpxExporter: GpxExporter,
    private val storage: RoutePlannerStorage,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RoutePlannerUiState())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            storage.history.collect { history ->
                mutableState.update { it.copy(searchHistory = history) }
            }
        }
        viewModelScope.launch {
            storage.savedRoutes.collect { routes ->
                mutableState.update { it.copy(savedRoutes = routes) }
            }
        }
    }

    fun setUseCurrentLocation(value: Boolean) {
        mutableState.update { it.copy(useCurrentLocation = value, errorMessage = null) }
    }

    fun setOriginQuery(value: String) {
        mutableState.update {
            it.copy(
                originQuery = value,
                originLongitude = "",
                originLatitude = "",
                originSystem = CoordinateSystem.WGS84,
                originSearchResults = emptyList(),
                routes = emptyList(),
                selectedRouteIndex = null,
                errorMessage = null,
            )
        }
    }

    fun setOriginSearchRegion(value: String) {
        mutableState.update { it.copy(originSearchRegion = value, errorMessage = null) }
    }

    fun setDestinationQuery(value: String) {
        mutableState.update {
            it.copy(
                destinationQuery = value,
                destinationLongitude = "",
                destinationLatitude = "",
                destinationSystem = CoordinateSystem.WGS84,
                searchResults = emptyList(),
                routes = emptyList(),
                selectedRouteIndex = null,
                errorMessage = null,
            )
        }
    }

    fun setSearchRegion(value: String) {
        mutableState.update { it.copy(searchRegion = value, errorMessage = null) }
    }

    fun searchOrigins() {
        val snapshot = mutableState.value
        if (snapshot.originQuery.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Enter an origin to search") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(isSearchingOrigin = true, errorMessage = null) }
            try {
                val results = routePlanner.searchPlaces(
                    keywords = snapshot.originQuery.trim(),
                    region = snapshot.originSearchRegion.trim().ifBlank { null },
                )
                mutableState.update {
                    it.copy(
                        originSearchResults = results,
                        isSearchingOrigin = false,
                        errorMessage = if (results.isEmpty()) "No origins found" else null,
                    )
                }
                if (results.isNotEmpty()) {
                    storage.addSearch(
                        SearchEndpoint.ORIGIN,
                        snapshot.originQuery,
                        snapshot.originSearchRegion,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update {
                    it.copy(
                        isSearchingOrigin = false,
                        errorMessage = error.message ?: "Origin search failed",
                    )
                }
            }
        }
    }

    fun searchDestinations() {
        val snapshot = mutableState.value
        if (snapshot.destinationQuery.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Enter a destination to search") }
            return
        }

        viewModelScope.launch {
            mutableState.update { it.copy(isSearchingDestination = true, errorMessage = null) }
            try {
                val results = routePlanner.searchPlaces(
                    keywords = snapshot.destinationQuery.trim(),
                    region = snapshot.searchRegion.trim().ifBlank { null },
                )
                mutableState.update {
                    it.copy(
                        searchResults = results,
                        isSearchingDestination = false,
                        errorMessage = if (results.isEmpty()) "No destinations found" else null,
                    )
                }
                if (results.isNotEmpty()) {
                    storage.addSearch(
                        SearchEndpoint.DESTINATION,
                        snapshot.destinationQuery,
                        snapshot.searchRegion,
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update {
                    it.copy(
                        isSearchingDestination = false,
                        errorMessage = error.message ?: "Destination search failed",
                    )
                }
            }
        }
    }

    fun selectOrigin(place: AmapPlace) {
        mutableState.update {
            it.copy(
                originQuery = place.name,
                originLongitude = place.coordinate.longitude.toString(),
                originLatitude = place.coordinate.latitude.toString(),
                originSystem = place.coordinate.coordinateSystem,
                originSearchResults = emptyList(),
                routes = emptyList(),
                selectedRouteIndex = null,
                errorMessage = null,
            )
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
                    system = snapshot.originSystem,
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
                    ),
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

    fun applySearchHistory(entry: RouteSearchHistoryEntry) {
        when (entry.endpoint) {
            SearchEndpoint.ORIGIN -> {
                setUseCurrentLocation(false)
                setOriginQuery(entry.query)
                setOriginSearchRegion(entry.region)
                setSearchRegion(entry.region)
            }
            SearchEndpoint.DESTINATION -> {
                setDestinationQuery(entry.query)
                setSearchRegion(entry.region)
                setOriginSearchRegion(entry.region)
            }
        }
    }

    fun deleteSearchHistory(id: String) {
        viewModelScope.launch { storage.deleteSearch(id) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { storage.clearSearchHistory() }
    }

    fun saveSelectedRoute(routeName: String) {
        val route = mutableState.value.selectedRoute ?: run {
            mutableState.update { it.copy(errorMessage = "Select a route before saving") }
            return
        }
        viewModelScope.launch {
            runCatching { storage.saveRoute(routeName, route) }
                .onSuccess {
                    mutableState.update {
                        it.copy(statusMessage = "Route saved in RouteGraph", errorMessage = null)
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(errorMessage = error.message ?: "Failed to save route")
                    }
                }
        }
    }

    fun loadSavedRoute(savedRoute: SavedPlannedRoute) {
        mutableState.update {
            it.copy(
                routes = listOf(savedRoute.route),
                selectedRouteIndex = 0,
                errorMessage = null,
                statusMessage = null,
            )
        }
    }

    fun deleteSavedRoute(id: String) {
        viewModelScope.launch { storage.deleteRoute(id) }
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
