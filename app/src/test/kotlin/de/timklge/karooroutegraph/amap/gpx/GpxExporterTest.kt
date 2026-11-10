/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.gpx

import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GpxExporterTest {
    private val converter = CoordinateConverter()
    private val exporter = GpxExporter(converter)

    @Test
    fun exportsUtf8Gpx11TrackInWgs84WithoutInventedData() {
        val originalWgs84 = listOf(
            GeoCoordinate(116.397389, 39.908722, CoordinateSystem.WGS84),
            GeoCoordinate(116.407389, 39.918722, CoordinateSystem.WGS84),
        )
        val route = AmapPlannedRoute(
            id = "route-1",
            distanceMeters = 1500,
            durationSeconds = 420,
            points = originalWgs84.map(converter::wgs84ToGcj02),
            steps = emptyList(),
        )

        val xml = exporter.export(
            route,
            GpxExportOptions(
                routeName = "北京 & 骑行",
                description = "路线 <测试>",
            ),
        ).toString(Charsets.UTF_8)

        assertTrue(xml.startsWith("""<?xml version="1.0" encoding="UTF-8"?>"""))
        assertTrue(xml.contains("""version="1.1""""))
        assertTrue(xml.contains("<name>北京 &amp; 骑行</name>"))
        assertTrue(xml.contains("<desc>路线 &lt;测试&gt;</desc>"))
        assertEquals(2, "<trkpt ".toRegex().findAll(xml).count())
        assertFalse(xml.contains("<ele>"))
        assertFalse(xml.contains("<rtept"))
        assertTrue(xml.contains("""lat="39.90872200" lon="116.39738900""""))
    }
}
