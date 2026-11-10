/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karooroutegraph.R
import de.timklge.karooroutegraph.Tile
import de.timklge.karooroutegraph.TileDownloadService
import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.gpx.GpxFileManager
import de.timklge.karooroutegraph.amap.routing.RoutePlannerViewModel
import de.timklge.karooroutegraph.datatypes.minimap.MinimapViewModelProvider
import de.timklge.karooroutegraph.datatypes.minimap.getOSMZoomLevelToFit
import de.timklge.karooroutegraph.datatypes.minimap.getRequiredTiles
import de.timklge.karooroutegraph.datatypes.minimap.latToTileY
import de.timklge.karooroutegraph.datatypes.minimap.lonToTileX
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerScreen(
    onBack: () -> Unit,
    viewModel: RoutePlannerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val fileManager = koinInject<GpxFileManager>()
    var routeName by remember { mutableStateOf("AMap bicycle route") }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val gpxSavedMessage = stringResource(R.string.gpx_saved)
    val shareGpxTitle = stringResource(R.string.share_gpx)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.amap_route_planner)) })
        },
    ) { contentPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.route_origin),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = state.useCurrentLocation,
                        onCheckedChange = viewModel::setUseCurrentLocation,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.use_current_location))
                }
                if (!state.useCurrentLocation) {
                    CoordinateFields(
                        longitude = state.originLongitude,
                        latitude = state.originLatitude,
                        onLongitudeChanged = viewModel::setOriginLongitude,
                        onLatitudeChanged = viewModel::setOriginLatitude,
                    )
                }

                Text(
                    text = stringResource(R.string.route_destination),
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = state.destinationQuery,
                    onValueChange = viewModel::setDestinationQuery,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.destination_search)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.searchRegion,
                    onValueChange = viewModel::setSearchRegion,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.search_region_optional)) },
                    singleLine = true,
                )
                Button(
                    onClick = viewModel::searchDestinations,
                    enabled = !state.isSearching && !state.isPlanning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.search_destination))
                    }
                }

                state.searchResults.forEach { place ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectDestination(place) },
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(place.name, style = MaterialTheme.typography.titleSmall)
                            place.address?.let {
                                Text(
                                    text = it,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%.6f, %.6f (GCJ-02)",
                                    place.coordinate.longitude,
                                    place.coordinate.latitude,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }

                CoordinateFields(
                    longitude = state.destinationLongitude,
                    latitude = state.destinationLatitude,
                    onLongitudeChanged = viewModel::setDestinationLongitude,
                    onLatitudeChanged = viewModel::setDestinationLatitude,
                )

                Button(
                    onClick = viewModel::planRoutes,
                    enabled = !state.isPlanning && !state.isSearching,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isPlanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(stringResource(R.string.plan_bicycle_routes))
                    }
                }

                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                state.routes.forEachIndexed { index, route ->
                    val selected = state.selectedRouteIndex == index
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectRoute(index) },
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { viewModel.selectRoute(index) },
                            )
                            Column {
                                Text(
                                    text = stringResource(
                                        R.string.route_candidate,
                                        index + 1,
                                    ),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(formatRouteSummary(route))
                            }
                        }
                    }
                }

                state.selectedRoute?.let { route ->
                    Text(
                        text = stringResource(R.string.route_preview),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    RoutePreview(
                        route = route,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.route_name)) },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                runCatching {
                                    val bytes = viewModel.exportSelectedRoute(routeName)
                                    fileManager.saveToDownloads(routeName, bytes)
                                }.onSuccess {
                                    exportMessage = gpxSavedMessage
                                }.onFailure {
                                    exportMessage = it.message
                                }
                            },
                        ) {
                            Text(stringResource(R.string.save_gpx))
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                runCatching {
                                    val bytes = viewModel.exportSelectedRoute(routeName)
                                    val shareIntent = fileManager.createShareIntent(routeName, bytes)
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            shareGpxTitle,
                                        )
                                    )
                                }.onFailure {
                                    exportMessage = it.message
                                }
                            },
                        ) {
                            Text(stringResource(R.string.share_gpx))
                        }
                    }
                    Text(
                        text = stringResource(R.string.gpx_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                exportMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            FixedBackButton(
                modifier = Modifier.align(Alignment.BottomStart),
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CoordinateFields(
    longitude: String,
    latitude: String,
    onLongitudeChanged: (String) -> Unit,
    onLatitudeChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = longitude,
            onValueChange = onLongitudeChanged,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.longitude)) },
            keyboardOptions = KeyboardOptions.Default,
            singleLine = true,
        )
        OutlinedTextField(
            value = latitude,
            onValueChange = onLatitudeChanged,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.latitude)) },
            keyboardOptions = KeyboardOptions.Default,
            singleLine = true,
        )
    }
}

@Composable
private fun RoutePreview(
    route: AmapPlannedRoute,
    modifier: Modifier = Modifier,
    coordinateConverter: CoordinateConverter = koinInject(),
    tileDownloadService: TileDownloadService = koinInject(),
    minimapViewModelProvider: MinimapViewModelProvider = koinInject(),
) {
    val tileUpdate by minimapViewModelProvider.viewModelFlow.collectAsStateWithLifecycle()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val wgs84Points = remember(route.points) {
        route.points.map { point ->
            coordinateConverter.convert(point, CoordinateSystem.WGS84)
        }
    }
    val lineString = remember(wgs84Points) {
        LineString.fromLngLats(
            wgs84Points.map { Point.fromLngLat(it.longitude, it.latitude) }
        )
    }
    val viewport = remember(lineString, canvasSize) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || wgs84Points.size < 2) {
            null
        } else {
            val zoom = floor(
                lineString.getOSMZoomLevelToFit(
                    screenWidthPx = canvasSize.width,
                    screenHeightPx = canvasSize.height,
                    paddingPx = 36,
                    tileSizePx = ROUTE_PREVIEW_TILE_SIZE,
                )
            ).toInt().coerceIn(1, 19)
            val center = Point.fromLngLat(
                wgs84Points.minOf { it.longitude } +
                    (wgs84Points.maxOf { it.longitude } - wgs84Points.minOf { it.longitude }) / 2.0,
                wgs84Points.minOf { it.latitude } +
                    (wgs84Points.maxOf { it.latitude } - wgs84Points.minOf { it.latitude }) / 2.0,
            )
            RoutePreviewViewport(
                zoom = zoom,
                center = center,
                tiles = getRequiredTiles(
                    zoomLevel = zoom.toFloat(),
                    widthPx = canvasSize.width,
                    heightPx = canvasSize.height,
                    mapCenter = center,
                    tileSizePx = ROUTE_PREVIEW_TILE_SIZE,
                ),
            )
        }
    }
    val tileBitmaps by produceState<Map<Tile, Bitmap>>(
        initialValue = emptyMap(),
        viewport,
        tileUpdate.lastTileDownloadedAt,
    ) {
        val requiredTiles = viewport?.tiles.orEmpty()
        value = withContext(Dispatchers.IO) {
            requiredTiles.mapNotNull { tile ->
                tileDownloadService.getTileIfAvailableInstantly(tile)?.let { tile to it }
            }.toMap()
        }
        requiredTiles.firstOrNull { it !in value }?.let(tileDownloadService::queueTileDownload)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFF202124)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it },
        ) {
            val currentViewport = viewport ?: return@Canvas
            val centerTileX = lonToTileX(currentViewport.center.longitude(), currentViewport.zoom)
            val centerTileY = latToTileY(currentViewport.center.latitude(), currentViewport.zoom)
            val tileSize = ROUTE_PREVIEW_TILE_SIZE.toFloat()

            tileBitmaps.forEach { (tile, bitmap) ->
                val left = size.width / 2f + ((tile.x - centerTileX) * tileSize).toFloat()
                val top = size.height / 2f + ((tile.y - centerTileY) * tileSize).toFloat()
                drawImage(
                    image = bitmap.asImageBitmap(),
                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                    dstSize = IntSize(
                        ROUTE_PREVIEW_TILE_SIZE.roundToInt(),
                        ROUTE_PREVIEW_TILE_SIZE.roundToInt(),
                    ),
                    filterQuality = FilterQuality.Medium,
                )
            }

            fun screenPoint(index: Int): Offset {
                val point = wgs84Points[index]
                return Offset(
                    x = size.width / 2f +
                        ((lonToTileX(point.longitude, currentViewport.zoom) - centerTileX) *
                            tileSize).toFloat(),
                    y = size.height / 2f +
                        ((latToTileY(point.latitude, currentViewport.zoom) - centerTileY) *
                            tileSize).toFloat(),
                )
            }

            val path = Path()
            wgs84Points.indices.forEach { index ->
                val screen = screenPoint(index)
                if (index == 0) path.moveTo(screen.x, screen.y)
                else path.lineTo(screen.x, screen.y)
            }
            drawPath(
                path = path,
                color = Color(0xFFFFD600),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(Color(0xFF4CAF50), radius = 6.dp.toPx(), center = screenPoint(0))
            drawCircle(
                Color(0xFFF44336),
                radius = 6.dp.toPx(),
                center = screenPoint(wgs84Points.lastIndex),
            )
        }
        Text(
            text = "© OpenStreetMap contributors",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall,
        )
        if (viewport != null && tileBitmaps.isEmpty()) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .height(28.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

private data class RoutePreviewViewport(
    val zoom: Int,
    val center: Point,
    val tiles: Set<Tile>,
)

private const val ROUTE_PREVIEW_TILE_SIZE = 256.0

private fun formatRouteSummary(route: AmapPlannedRoute): String {
    val distance = if (route.distanceMeters >= 1000) {
        String.format(Locale.US, "%.1f km", route.distanceMeters / 1000.0)
    } else {
        "${route.distanceMeters} m"
    }
    val duration = route.durationSeconds?.let {
        val hours = it / 3600
        val minutes = (it % 3600) / 60
        if (hours > 0) "${hours} h ${minutes} min" else "${minutes} min"
    } ?: "duration unavailable"
    return "$distance · $duration"
}
