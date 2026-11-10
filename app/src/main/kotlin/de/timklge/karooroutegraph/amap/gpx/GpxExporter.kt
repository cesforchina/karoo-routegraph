/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.gpx

import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import java.util.Locale

data class GpxExportOptions(
    val routeName: String,
    val description: String? = null,
)

class GpxExporter(
    private val coordinateConverter: CoordinateConverter,
) {
    fun export(
        route: AmapPlannedRoute,
        options: GpxExportOptions,
    ): ByteArray {
        require(options.routeName.isNotBlank()) { "Route name must not be blank" }
        require(route.points.size >= 2) { "At least two route points are required" }

        val description = options.description
            ?: "AMap bicycle route, distance ${route.distanceMeters} m"
        val xml = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="Karoo RouteGraph" """ +
                    """xmlns="http://www.topografix.com/GPX/1/1" """ +
                    """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                    """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 """ +
                    """http://www.topografix.com/GPX/1/1/gpx.xsd">"""
            )
            appendLine("  <metadata>")
            appendLine("    <name>${escapeXml(options.routeName)}</name>")
            appendLine("    <desc>${escapeXml(description)}</desc>")
            appendLine("  </metadata>")
            appendLine("  <trk>")
            appendLine("    <name>${escapeXml(options.routeName)}</name>")
            appendLine("    <desc>${escapeXml(description)}</desc>")
            appendLine("    <trkseg>")
            route.points.forEach { point ->
                val wgs84 = coordinateConverter.convert(point, CoordinateSystem.WGS84)
                appendLine(
                    String.format(
                        Locale.US,
                        "      <trkpt lat=\"%.8f\" lon=\"%.8f\" />",
                        wgs84.latitude,
                        wgs84.longitude,
                    )
                )
            }
            appendLine("    </trkseg>")
            appendLine("  </trk>")
            appendLine("</gpx>")
        }
        return xml.toByteArray(Charsets.UTF_8)
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        value.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&apos;"
                    else -> character
                }
            )
        }
    }
}
