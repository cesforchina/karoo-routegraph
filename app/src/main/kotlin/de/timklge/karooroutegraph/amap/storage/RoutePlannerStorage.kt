/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.storage

import android.content.Context
import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SearchEndpoint { ORIGIN, DESTINATION }

data class RouteSearchHistoryEntry(
    val id: String,
    val endpoint: SearchEndpoint,
    val query: String,
    val region: String,
    val savedAt: Long,
)

data class SavedPlannedRoute(
    val id: String,
    val name: String,
    val savedAt: Long,
    val route: AmapPlannedRoute,
)

class RoutePlannerStorage(context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "amap_route_planner.json")
    private val lock = Mutex()
    private var snapshot = loadSnapshot()

    private val mutableHistory = MutableStateFlow(snapshot.history.map(HistoryRecord::toModel))
    val history = mutableHistory.asStateFlow()

    private val mutableSavedRoutes = MutableStateFlow(snapshot.routes.map(SavedRouteRecord::toModel))
    val savedRoutes = mutableSavedRoutes.asStateFlow()

    suspend fun addSearch(endpoint: SearchEndpoint, query: String, region: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) return
        update { current ->
            val record = HistoryRecord(
                id = UUID.randomUUID().toString(),
                endpoint = endpoint.name,
                query = normalizedQuery,
                region = region.trim(),
                savedAt = System.currentTimeMillis(),
            )
            current.copy(
                history = listOf(record) + current.history
                    .filterNot {
                        it.endpoint == record.endpoint &&
                            it.query.equals(record.query, ignoreCase = true) &&
                            it.region.equals(record.region, ignoreCase = true)
                    }
                    .take(MAX_HISTORY - 1)
            )
        }
    }

    suspend fun deleteSearch(id: String) {
        update { it.copy(history = it.history.filterNot { entry -> entry.id == id }) }
    }

    suspend fun clearSearchHistory() {
        update { it.copy(history = emptyList()) }
    }

    suspend fun saveRoute(name: String, route: AmapPlannedRoute) {
        val safeName = name.trim().ifBlank { "AMap bicycle route" }
        val record = SavedRouteRecord.from(
            id = UUID.randomUUID().toString(),
            name = safeName,
            route = route,
        )
        update { current ->
            current.copy(
                routes = listOf(record) + current.routes
                    .filterNot { it.name.equals(safeName, ignoreCase = true) }
            )
        }
    }

    suspend fun deleteRoute(id: String) {
        update { it.copy(routes = it.routes.filterNot { route -> route.id == id }) }
    }

    private suspend fun update(transform: (StorageSnapshot) -> StorageSnapshot) {
        withContext(Dispatchers.IO) {
            lock.withLock {
                val updated = transform(snapshot)
                val temporary = File(file.parentFile, "${file.name}.tmp")
                temporary.writeText(json.encodeToString(updated))
                if (!temporary.renameTo(file)) {
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                }
                snapshot = updated
                mutableHistory.value = updated.history.map(HistoryRecord::toModel)
                mutableSavedRoutes.value = updated.routes.map(SavedRouteRecord::toModel)
            }
        }
    }

    private fun loadSnapshot(): StorageSnapshot =
        runCatching {
            if (file.isFile) json.decodeFromString<StorageSnapshot>(file.readText())
            else StorageSnapshot()
        }.getOrElse { StorageSnapshot() }

    private companion object {
        const val MAX_HISTORY = 20
    }
}

@Serializable
private data class StorageSnapshot(
    val history: List<HistoryRecord> = emptyList(),
    val routes: List<SavedRouteRecord> = emptyList(),
)

@Serializable
private data class HistoryRecord(
    val id: String,
    val endpoint: String,
    val query: String,
    val region: String,
    val savedAt: Long,
) {
    fun toModel() = RouteSearchHistoryEntry(
        id = id,
        endpoint = runCatching { SearchEndpoint.valueOf(endpoint) }
            .getOrDefault(SearchEndpoint.DESTINATION),
        query = query,
        region = region,
        savedAt = savedAt,
    )
}

@Serializable
private data class SavedRouteRecord(
    val id: String,
    val name: String,
    val savedAt: Long,
    val distanceMeters: Long,
    val durationSeconds: Long? = null,
    val points: List<SavedPoint>,
) {
    fun toModel() = SavedPlannedRoute(
        id = id,
        name = name,
        savedAt = savedAt,
        route = AmapPlannedRoute(
            id = "saved-$id",
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            points = points.map(SavedPoint::toCoordinate),
            steps = emptyList(),
        ),
    )

    companion object {
        fun from(id: String, name: String, route: AmapPlannedRoute) = SavedRouteRecord(
            id = id,
            name = name,
            savedAt = System.currentTimeMillis(),
            distanceMeters = route.distanceMeters,
            durationSeconds = route.durationSeconds,
            points = route.points.map(SavedPoint::from),
        )
    }
}

@Serializable
private data class SavedPoint(
    val longitude: Double,
    val latitude: Double,
    val coordinateSystem: String,
) {
    fun toCoordinate() = GeoCoordinate(
        longitude = longitude,
        latitude = latitude,
        coordinateSystem = runCatching { CoordinateSystem.valueOf(coordinateSystem) }
            .getOrDefault(CoordinateSystem.GCJ02),
    )

    companion object {
        fun from(point: GeoCoordinate) = SavedPoint(
            longitude = point.longitude,
            latitude = point.latitude,
            coordinateSystem = point.coordinateSystem.name,
        )
    }
}
