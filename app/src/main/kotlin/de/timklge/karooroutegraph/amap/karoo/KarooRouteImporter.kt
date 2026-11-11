/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.karoo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import io.hammerhead.datamodels.routes.Elevation
import io.hammerhead.datamodels.routes.Route
import io.hammerhead.datamodels.routes.Waypoint
import io.hammerhead.geomodule.Coordinates
import java.util.Date
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Opens a populated route in Karoo's native route editor.
 *
 * Hammerhead does not expose route creation through karoo-ext. Karoo 4.217.1 does export a route
 * creation activity, but its Parcelable contract is private. Keep this path explicitly version
 * gated; GPX export remains the supported fallback.
 */
class KarooRouteImporter(
    private val context: Context,
    private val coordinateConverter: CoordinateConverter,
) {
    fun createImportIntent(route: AmapPlannedRoute, routeName: String): Intent {
        val version = installedRoutesVersion()
            ?: error("Karoo Routes is not installed on this device")
        check(version.startsWith(SUPPORTED_ROUTES_VERSION)) {
            "Karoo Routes $version has not been verified for direct import. Save or share the GPX instead."
        }

        val points = route.points.map {
            coordinateConverter.convert(it, CoordinateSystem.WGS84)
        }
        require(points.size >= 2) { "The selected route has fewer than two points" }

        val encodedPolyline = encodePolyline(points.map { it.latitude to it.longitude })
        val first = points.first()
        val last = points.last()
        val parcel = Route(
            id = UUID.randomUUID().toString(),
            name = routeName.trim().ifBlank { "AMap bicycle route" }.take(100),
            source = "AMap",
            distance = route.distanceMeters.toDouble(),
            elevation = Elevation(),
            startLocation = Coordinates(first.latitude, first.longitude),
            endLocation = Coordinates(last.latitude, last.longitude),
            waypoints = listOf(
                Waypoint(first.latitude, first.longitude, 0),
                Waypoint(last.latitude, last.longitude, points.lastIndex),
            ),
            startLocationName = "Start",
            endLocationName = "Destination",
            createdAt = Date(),
            summaryPolyline = encodedPolyline,
            routePolyline = encodedPolyline,
        )

        return Intent(ACTION_CREATE_ROUTE).apply {
            setClassName(ROUTES_PACKAGE, ROUTE_CREATION_ACTIVITY)
            putExtra(EXTRA_ROUTE, parcel)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    @Suppress("DEPRECATION")
    private fun installedRoutesVersion(): String? = try {
        context.packageManager.getPackageInfo(ROUTES_PACKAGE, 0).versionName
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun encodePolyline(points: List<Pair<Double, Double>>): String = buildString {
        var previousLatitude = 0L
        var previousLongitude = 0L
        points.forEach { (latitude, longitude) ->
            val latitudeValue = (latitude * POLYLINE_SCALE).roundToLong()
            val longitudeValue = (longitude * POLYLINE_SCALE).roundToLong()
            appendEncoded(latitudeValue - previousLatitude)
            appendEncoded(longitudeValue - previousLongitude)
            previousLatitude = latitudeValue
            previousLongitude = longitudeValue
        }
    }

    private fun StringBuilder.appendEncoded(delta: Long) {
        var value = (delta shl 1) xor (delta shr 63)
        while (value >= 0x20) {
            append(((0x20L or (value and 0x1fL)).toInt() + 63).toChar())
            value = value shr 5
        }
        append((value.toInt() + 63).toChar())
    }

    private companion object {
        const val ROUTES_PACKAGE = "io.hammerhead.routesapp"
        const val ROUTE_CREATION_ACTIVITY =
            "io.hammerhead.routesapp.routeCreation.RouteCreationActivity"
        const val ACTION_CREATE_ROUTE = "io.hammerhead.intent.action.CREATE_ROUTE"
        const val EXTRA_ROUTE = "route"
        const val SUPPORTED_ROUTES_VERSION = "4.217.1"
        const val POLYLINE_SCALE = 100_000.0
    }
}
