/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.timklge.karooroutegraph.LocationViewModelProvider
import de.timklge.karooroutegraph.MapSource
import de.timklge.karooroutegraph.R
import de.timklge.karooroutegraph.Tile
import de.timklge.karooroutegraph.TileDownloadService
import de.timklge.karooroutegraph.amap.api.AmapPlace
import de.timklge.karooroutegraph.amap.api.AmapPlannedRoute
import de.timklge.karooroutegraph.amap.coordinates.CoordinateConverter
import de.timklge.karooroutegraph.amap.coordinates.CoordinateSystem
import de.timklge.karooroutegraph.amap.gpx.GpxFileManager
import de.timklge.karooroutegraph.amap.karoo.KarooRouteImporter
import de.timklge.karooroutegraph.amap.routing.RoutePlannerViewModel
import de.timklge.karooroutegraph.amap.storage.RouteSearchHistoryEntry
import de.timklge.karooroutegraph.amap.storage.SavedPlannedRoute
import de.timklge.karooroutegraph.amap.storage.SearchEndpoint
import de.timklge.karooroutegraph.datatypes.minimap.MinimapViewModelProvider
import de.timklge.karooroutegraph.datatypes.minimap.getOSMZoomLevelToFit
import de.timklge.karooroutegraph.datatypes.minimap.getRequiredTiles
import de.timklge.karooroutegraph.datatypes.minimap.latToTileY
import de.timklge.karooroutegraph.datatypes.minimap.lonToTileX
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import java.util.Locale
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sinh

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerScreen(
    onBack: () -> Unit,
    viewModel: RoutePlannerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    val fileManager = koinInject<GpxFileManager>()
    val karooRouteImporter = koinInject<KarooRouteImporter>()
    var routeName by remember { mutableStateOf("AMap bicycle route") }
    var routePreviewMapSource by remember { mutableStateOf(MapSource.OSM) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var showFullScreenMap by remember { mutableStateOf(false) }
    var historyToDelete by remember { mutableStateOf<RouteSearchHistoryEntry?>(null) }
    var savedRouteToDelete by remember { mutableStateOf<SavedPlannedRoute?>(null) }
    var confirmClearHistory by remember { mutableStateOf(false) }
    val gpxSavedMessage = stringResource(R.string.gpx_saved)
    val changeMapSource: (MapSource) -> Unit = { source ->
        routePreviewMapSource = source
        if (
            source == MapSource.LOCAL_MAP &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    if (showFullScreenMap) {
        state.selectedRoute?.let { route ->
            Dialog(
                onDismissRequest = { showFullScreenMap = false },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF202124)),
                ) {
                    RoutePreview(
                        route = route,
                        mapSource = routePreviewMapSource,
                        modifier = Modifier.fillMaxSize(),
                        onMapSourceChange = changeMapSource,
                    )
                    FilledIconButton(
                        onClick = { showFullScreenMap = false },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    ) {
                        Icon(Icons.Default.Close, stringResource(R.string.close))
                    }
                }
            }
        }
    }

    historyToDelete?.let { entry ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_search_history),
            message = entry.query,
            onConfirm = {
                viewModel.deleteSearchHistory(entry.id)
                historyToDelete = null
            },
            onDismiss = { historyToDelete = null },
        )
    }
    savedRouteToDelete?.let { saved ->
        DeleteConfirmationDialog(
            title = stringResource(R.string.delete_saved_route),
            message = saved.name,
            onConfirm = {
                viewModel.deleteSavedRoute(saved.id)
                savedRouteToDelete = null
            },
            onDismiss = { savedRouteToDelete = null },
        )
    }
    if (confirmClearHistory) {
        DeleteConfirmationDialog(
            title = stringResource(R.string.clear_search_history),
            message = stringResource(R.string.clear_search_history_confirmation),
            onConfirm = {
                viewModel.clearSearchHistory()
                confirmClearHistory = false
            },
            onDismiss = { confirmClearHistory = false },
        )
    }

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
                    .padding(start = 6.dp, top = 6.dp, end = 6.dp, bottom = 70.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.route_origin),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.use_current_location),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Switch(
                                checked = state.useCurrentLocation,
                                onCheckedChange = viewModel::setUseCurrentLocation,
                            )
                        }
                        if (!state.useCurrentLocation) {
                            CompactSearchField(
                                value = state.originQuery,
                                onValueChange = viewModel::setOriginQuery,
                                label = stringResource(R.string.origin_search),
                                isLoading = state.isSearchingOrigin,
                                enabled = !state.isSearching && !state.isPlanning,
                                onSearch = viewModel::searchOrigins,
                            )
                            PlaceSearchResults(
                                places = state.originSearchResults,
                                onSelect = viewModel::selectOrigin,
                            )
                        }
                        Text(
                            text = stringResource(R.string.route_destination),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        CompactSearchField(
                            value = state.destinationQuery,
                            onValueChange = viewModel::setDestinationQuery,
                            label = stringResource(R.string.destination_search),
                            isLoading = state.isSearchingDestination,
                            enabled = !state.isSearching && !state.isPlanning,
                            onSearch = viewModel::searchDestinations,
                        )
                        PlaceSearchResults(
                            places = state.searchResults,
                            onSelect = viewModel::selectDestination,
                        )
                        OutlinedTextField(
                            value = state.searchRegion.ifBlank { state.originSearchRegion },
                            onValueChange = {
                                viewModel.setOriginSearchRegion(it)
                                viewModel.setSearchRegion(it)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.search_region_optional)) },
                            singleLine = true,
                        )
                    }
                }

                if (state.searchHistory.isNotEmpty()) {
                    SearchHistorySection(
                        history = state.searchHistory,
                        onSelect = viewModel::applySearchHistory,
                        onDelete = { historyToDelete = it },
                        onClear = { confirmClearHistory = true },
                    )
                }

                if (state.savedRoutes.isNotEmpty()) {
                    SavedRoutesSection(
                        routes = state.savedRoutes,
                        onSelect = {
                            routeName = it.name
                            viewModel.loadSavedRoute(it)
                        },
                        onDelete = { savedRouteToDelete = it },
                    )
                }

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
                            modifier = Modifier.padding(6.dp),
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
                        mapSource = routePreviewMapSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        onExpand = { showFullScreenMap = true },
                        onMapSourceChange = changeMapSource,
                    )
                    OutlinedTextField(
                        value = routeName,
                        onValueChange = { routeName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.route_name)) },
                        singleLine = true,
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { viewModel.saveSelectedRoute(routeName) },
                    ) {
                        Icon(Icons.Default.Save, null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.save_in_app))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                    context.startActivity(
                                        fileManager.createPhoneShareIntent(routeName, bytes)
                                    )
                                }.onFailure {
                                    exportMessage = it.message
                                }
                            },
                        ) {
                            Text(stringResource(R.string.share_gpx))
                        }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    karooRouteImporter.createImportIntent(route, routeName)
                                )
                            }.onFailure {
                                exportMessage = it.message
                            }
                        },
                    ) {
                        Text(stringResource(R.string.import_to_karoo_experimental))
                    }
                    Text(
                        text = stringResource(R.string.gpx_import_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                state.statusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
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
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isLoading: Boolean,
    enabled: Boolean,
    onSearch: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                IconButton(
                    onClick = onSearch,
                    enabled = enabled && value.isNotBlank(),
                ) {
                    Icon(Icons.Default.Search, stringResource(R.string.search))
                }
            }
        },
    )
}

@Composable
private fun SearchHistorySection(
    history: List<RouteSearchHistoryEntry>,
    onSelect: (RouteSearchHistoryEntry) -> Unit,
    onDelete: (RouteSearchHistoryEntry) -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.search_history),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.DeleteSweep, stringResource(R.string.clear_search_history))
                }
            }
            history.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(entry) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (entry.endpoint == SearchEndpoint.ORIGIN) {
                            stringResource(R.string.route_origin)
                        } else {
                            stringResource(R.string.route_destination)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entry.region.isNotBlank()) {
                            Text(
                                entry.region,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                    IconButton(
                        onClick = { onDelete(entry) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedRoutesSection(
    routes: List<SavedPlannedRoute>,
    onSelect: (SavedPlannedRoute) -> Unit,
    onDelete: (SavedPlannedRoute) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(6.dp)) {
            Text(
                stringResource(R.string.saved_routes),
                style = MaterialTheme.typography.titleSmall,
            )
            routes.forEach { saved ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(saved) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(saved.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            formatRouteSummary(saved.route),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    IconButton(
                        onClick = { onDelete(saved) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PlaceSearchResults(
    places: List<AmapPlace>,
    onSelect: (AmapPlace) -> Unit,
) {
    places.forEach { place ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(place) },
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
}

@Composable
private fun RoutePreview(
    route: AmapPlannedRoute,
    mapSource: MapSource,
    modifier: Modifier = Modifier,
    onExpand: (() -> Unit)? = null,
    onMapSourceChange: (MapSource) -> Unit,
    coordinateConverter: CoordinateConverter = koinInject(),
    tileDownloadService: TileDownloadService = koinInject(),
    minimapViewModelProvider: MinimapViewModelProvider = koinInject(),
    locationViewModelProvider: LocationViewModelProvider = koinInject(),
) {
    val tileUpdate by minimapViewModelProvider.viewModelFlow.collectAsStateWithLifecycle()
    val currentLocation by locationViewModelProvider.viewModelFlow.collectAsStateWithLifecycle()
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoomOffset by remember(route.id) { mutableFloatStateOf(0f) }
    var mapCenter by remember(route.id) { mutableStateOf<Point?>(null) }
    var freeRotation by remember(route.id) { mutableStateOf(true) }
    var rotationDegrees by remember(route.id) { mutableFloatStateOf(0f) }
    var amapStaticError by remember(route.id, mapSource) { mutableStateOf<String?>(null) }
    var amapStaticLoading by remember(route.id) { mutableStateOf(false) }
    var amapBitmapRouteId by remember { mutableStateOf(route.id) }
    val displayCoordinateSystem = when (mapSource) {
        MapSource.OSM,
        MapSource.LOCAL_MAP,
        -> CoordinateSystem.WGS84
        MapSource.AMAP_STATIC -> CoordinateSystem.GCJ02
    }
    val displayPoints = remember(route.points, displayCoordinateSystem) {
        route.points.map { point ->
            coordinateConverter.convert(point, displayCoordinateSystem)
        }
    }
    val displayLocation = remember(currentLocation, displayCoordinateSystem) {
        currentLocation?.let { location ->
            coordinateConverter.convert(
                de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate(
                    longitude = location.longitude(),
                    latitude = location.latitude(),
                    coordinateSystem = CoordinateSystem.WGS84,
                ),
                displayCoordinateSystem,
            )
        }
    }
    val lineString = remember(displayPoints) {
        LineString.fromLngLats(
            displayPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
        )
    }
    val viewport = remember(
        lineString,
        canvasSize,
        zoomOffset,
        mapCenter,
        displayCoordinateSystem,
    ) {
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || displayPoints.size < 2) {
            null
        } else {
            val webMercatorFitZoom = floor(
                lineString.getOSMZoomLevelToFit(
                    screenWidthPx = canvasSize.width,
                    screenHeightPx = canvasSize.height,
                    paddingPx = 36,
                    tileSizePx = ROUTE_PREVIEW_TILE_SIZE,
                )
            ).toInt().coerceIn(1, 19)
            val maxZoom = if (mapSource == MapSource.AMAP_STATIC) 17 else 19
            val sourceFitZoom = if (mapSource == MapSource.AMAP_STATIC) {
                webMercatorFitZoom - 1
            } else {
                webMercatorFitZoom
            }
            val constrainedFitZoom = sourceFitZoom.coerceIn(1, maxZoom)
            val zoom = (constrainedFitZoom + zoomOffset.roundToInt())
                .coerceIn(constrainedFitZoom, maxZoom)
            val center = mapCenter?.let { wgs84Center ->
                val converted = coordinateConverter.convert(
                    de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate(
                        longitude = wgs84Center.longitude(),
                        latitude = wgs84Center.latitude(),
                        coordinateSystem = CoordinateSystem.WGS84,
                    ),
                    displayCoordinateSystem,
                )
                Point.fromLngLat(converted.longitude, converted.latitude)
            } ?: Point.fromLngLat(
                displayPoints.minOf { it.longitude } +
                    (displayPoints.maxOf { it.longitude } - displayPoints.minOf { it.longitude }) / 2.0,
                displayPoints.minOf { it.latitude } +
                    (displayPoints.maxOf { it.latitude } - displayPoints.minOf { it.latitude }) / 2.0,
            )
            RoutePreviewViewport(
                zoom = zoom,
                center = center,
                tiles = if (mapSource == MapSource.AMAP_STATIC) {
                    emptySet()
                } else {
                    getRequiredTiles(
                        zoomLevel = zoom.toFloat(),
                        widthPx = canvasSize.width,
                        heightPx = canvasSize.height,
                        mapCenter = center,
                        tileSizePx = ROUTE_PREVIEW_TILE_SIZE,
                    ).map { tile -> tile.copy(source = mapSource) }.toSet()
                },
            )
        }
    }
    val latestViewport by rememberUpdatedState(viewport)
    val latestRotationDegrees by rememberUpdatedState(rotationDegrees)
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
    val amapGrid = remember(viewport, canvasSize) {
        viewport?.takeIf { mapSource == MapSource.AMAP_STATIC }?.let { currentViewport ->
            AmapStaticGrid.from(currentViewport, canvasSize)
        }
    }
    val amapStaticMaps by produceState<Map<AmapStaticFrameKey, Bitmap>>(
        initialValue = emptyMap(),
        route.id,
        amapGrid,
        mapSource,
    ) {
        if (amapBitmapRouteId != route.id) {
            value = emptyMap()
            amapBitmapRouteId = route.id
        }
        val currentGrid = amapGrid
        if (mapSource != MapSource.AMAP_STATIC || currentGrid == null) {
            amapStaticLoading = false
            amapStaticError = null
            return@produceState
        }
        val targets = currentGrid.frameKeys()
        val targetSet = targets.toSet()
        if (targets.first() in value) {
            value = value.filterKeys { it in targetSet }
        }
        amapStaticLoading = true
        amapStaticError = null
        try {
            delay(AMAP_STATIC_MAP_REQUEST_DEBOUNCE_MILLIS)
            for ((index, key) in targets.withIndex()) {
                if (key in value) continue
                val center = key.centerPoint()
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        tileDownloadService.getAmapStaticMap(
                            centerLongitude = center.longitude(),
                            centerLatitude = center.latitude(),
                            zoom = key.zoom,
                            width = key.width,
                            height = key.height,
                        )
                    }
                }
                val bitmap = result.getOrElse {
                    amapStaticError = it.message ?: "AMap map request failed"
                    break
                }
                value = if (index == 0) {
                    value.filterKeys { it in targetSet } + (key to bitmap)
                } else {
                    value + (key to bitmap)
                }
            }
        } finally {
            amapStaticLoading = false
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color(0xFFF1F3F4)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(route.id, mapSource, freeRotation) {
                    detectTransformGestures { _, pan, zoomChange, rotationChange ->
                        latestViewport?.let { activeViewport ->
                            if (pan != Offset.Zero) {
                                val pannedDisplayCenter = activeViewport.center.pannedBy(
                                    pan = pan.rotatedBy(-latestRotationDegrees),
                                    zoom = activeViewport.zoom,
                                    tileSizePx = if (mapSource == MapSource.AMAP_STATIC) {
                                        AMAP_STATIC_PROJECTION_TILE_SIZE
                                    } else {
                                        ROUTE_PREVIEW_TILE_SIZE
                                    },
                                )
                                val wgs84Center = coordinateConverter.convert(
                                    de.timklge.karooroutegraph.amap.coordinates.GeoCoordinate(
                                        longitude = pannedDisplayCenter.longitude(),
                                        latitude = pannedDisplayCenter.latitude(),
                                        coordinateSystem = displayCoordinateSystem,
                                    ),
                                    CoordinateSystem.WGS84,
                                )
                                mapCenter = Point.fromLngLat(
                                    wgs84Center.longitude,
                                    wgs84Center.latitude,
                                )
                            }
                        }
                        val updatedZoom =
                            (zoomOffset + log2(zoomChange.toDouble()).toFloat())
                                .coerceIn(0f, MAX_ROUTE_PREVIEW_ZOOM_OFFSET)
                        zoomOffset = updatedZoom
                        if (zoomChange < 1f && updatedZoom == 0f) mapCenter = null
                        if (freeRotation && rotationChange != 0f) {
                            rotationDegrees = (rotationDegrees + rotationChange) % 360f
                        }
                    }
                }
                .onSizeChanged { canvasSize = it },
        ) {
            val currentViewport = viewport ?: return@Canvas
            val centerTileX = lonToTileX(currentViewport.center.longitude(), currentViewport.zoom)
            val centerTileY = latToTileY(currentViewport.center.latitude(), currentViewport.zoom)
            val tileSize = if (mapSource == MapSource.AMAP_STATIC) {
                AMAP_STATIC_PROJECTION_TILE_SIZE.toFloat()
            } else {
                ROUTE_PREVIEW_TILE_SIZE.toFloat()
            }

            rotate(rotationDegrees, pivot = center) {
            if (mapSource == MapSource.AMAP_STATIC) {
                amapStaticMaps.forEach { (key, bitmap) ->
                    val frameCenter = key.centerPoint()
                    val scale = 2.0.pow(currentViewport.zoom - key.zoom).toFloat()
                    val frameWidth = key.width * scale
                    val frameHeight = key.height * scale
                    val left = size.width / 2f +
                        ((lonToTileX(frameCenter.longitude(), currentViewport.zoom) - centerTileX) *
                            tileSize).toFloat() - frameWidth / 2f
                    val top = size.height / 2f +
                        ((latToTileY(frameCenter.latitude(), currentViewport.zoom) - centerTileY) *
                            tileSize).toFloat() - frameHeight / 2f
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                        dstSize = IntSize(
                            frameWidth.roundToInt(),
                            frameHeight.roundToInt(),
                        ),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            } else {
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
            }

            fun screenPoint(longitude: Double, latitude: Double): Offset = Offset(
                x = size.width / 2f +
                    ((lonToTileX(longitude, currentViewport.zoom) - centerTileX) *
                        tileSize).toFloat(),
                y = size.height / 2f +
                    ((latToTileY(latitude, currentViewport.zoom) - centerTileY) *
                        tileSize).toFloat(),
            )

            fun screenPoint(index: Int): Offset {
                val point = displayPoints[index]
                return screenPoint(point.longitude, point.latitude)
            }

            run {
                val path = Path()
                displayPoints.indices.forEach { index ->
                    val screen = screenPoint(index)
                    if (index == 0) path.moveTo(screen.x, screen.y)
                    else path.lineTo(screen.x, screen.y)
                }
                drawPath(
                    path = path,
                    color = Color(0xFFFFD600),
                    style = Stroke(
                        width = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawCircle(Color(0xFF4CAF50), radius = 6.dp.toPx(), center = screenPoint(0))
                drawCircle(
                    Color(0xFFF44336),
                    radius = 6.dp.toPx(),
                    center = screenPoint(displayPoints.lastIndex),
                )
                displayLocation?.let { location ->
                    drawCircle(
                        color = Color(0xFF2196F3),
                        radius = 7.dp.toPx(),
                        center = screenPoint(location.longitude, location.latitude),
                    )
                }
            }
            }
        }
        onExpand?.let { expand ->
            FilledIconButton(
                onClick = expand,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(40.dp),
            ) {
                Icon(Icons.Default.Fullscreen, stringResource(R.string.fullscreen_map))
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledIconButton(
                onClick = {
                    onMapSourceChange(
                        when (mapSource) {
                            MapSource.OSM -> MapSource.AMAP_STATIC
                            MapSource.AMAP_STATIC -> MapSource.LOCAL_MAP
                            MapSource.LOCAL_MAP -> MapSource.OSM
                        }
                    )
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.Layers, stringResource(R.string.map_source))
            }
            Text(
                text = when (mapSource) {
                    MapSource.OSM -> stringResource(R.string.map_source_osm)
                    MapSource.AMAP_STATIC -> stringResource(R.string.map_source_amap)
                    MapSource.LOCAL_MAP -> stringResource(R.string.map_source_local)
                },
                modifier = Modifier
                    .background(Color(0xCCFFFFFF))
                    .padding(horizontal = 3.dp),
                color = Color.Black,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilledIconButton(
                onClick = {
                    zoomOffset = (zoomOffset + 1f)
                        .coerceAtMost(MAX_ROUTE_PREVIEW_ZOOM_OFFSET)
                },
                modifier = Modifier.size(40.dp),
            ) {
                Text("+")
            }
            FilledIconButton(
                onClick = {
                    zoomOffset = (zoomOffset - 1f).coerceAtLeast(0f)
                    if (zoomOffset == 0f) mapCenter = null
                },
                enabled = zoomOffset > 0f,
                modifier = Modifier.size(40.dp),
            ) {
                Text("−")
            }
            FilledIconButton(
                onClick = {
                    freeRotation = !freeRotation
                    if (!freeRotation) rotationDegrees = 0f
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Explore,
                    if (freeRotation) {
                        stringResource(R.string.map_free_rotation)
                    } else {
                        stringResource(R.string.map_north_up)
                    },
                    tint = if (freeRotation) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                )
            }
            FilledIconButton(
                onClick = {
                    val target = currentLocation ?: route.points.firstOrNull()?.let { point ->
                        val wgs84 = coordinateConverter.convert(point, CoordinateSystem.WGS84)
                        Point.fromLngLat(wgs84.longitude, wgs84.latitude)
                    }
                    if (target != null) mapCenter = target
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Default.MyLocation, stringResource(R.string.map_center_location))
            }
        }
        Text(
            text = when (mapSource) {
                MapSource.AMAP_STATIC -> "© 高德地图"
                MapSource.OSM -> "© OpenStreetMap contributors"
                MapSource.LOCAL_MAP -> "© OpenStreetMap contributors · offline"
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color(0xCCFFFFFF))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.Black,
            style = MaterialTheme.typography.labelSmall,
        )
        val isMapLoading = viewport != null && when (mapSource) {
            MapSource.OSM,
            MapSource.LOCAL_MAP,
            -> tileBitmaps.isEmpty()
            MapSource.AMAP_STATIC ->
                amapStaticMaps.isEmpty() && amapStaticLoading && amapStaticError == null
        }
        amapStaticError?.let { error ->
            Text(
                text = error,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xDD202124))
                    .padding(8.dp),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isMapLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
                strokeWidth = 2.dp,
            )
        } else if (mapSource == MapSource.AMAP_STATIC && amapStaticLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(20.dp),
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

private data class AmapStaticGrid(
    val zoom: Int,
    val centerGridX: Long,
    val centerGridY: Long,
    val width: Int,
    val height: Int,
) {
    fun frameKeys(): List<AmapStaticFrameKey> = AMAP_STATIC_FRAME_OFFSETS.map { (dx, dy) ->
        AmapStaticFrameKey(
            zoom = zoom,
            gridX = centerGridX + dx,
            gridY = centerGridY + dy,
            width = width,
            height = height,
        )
    }

    companion object {
        fun from(viewport: RoutePreviewViewport, canvasSize: IntSize): AmapStaticGrid {
            val width = canvasSize.width.coerceIn(1, AMAP_STATIC_MAX_IMAGE_SIZE)
            val height = canvasSize.height.coerceIn(1, AMAP_STATIC_MAX_IMAGE_SIZE)
            val worldX = lonToTileX(viewport.center.longitude(), viewport.zoom) *
                AMAP_STATIC_PROJECTION_TILE_SIZE
            val worldY = latToTileY(viewport.center.latitude(), viewport.zoom) *
                AMAP_STATIC_PROJECTION_TILE_SIZE
            return AmapStaticGrid(
                zoom = viewport.zoom,
                centerGridX = floor(worldX / width).toLong(),
                centerGridY = floor(worldY / height).toLong(),
                width = width,
                height = height,
            )
        }
    }
}

private data class AmapStaticFrameKey(
    val zoom: Int,
    val gridX: Long,
    val gridY: Long,
    val width: Int,
    val height: Int,
) {
    fun centerPoint(): Point {
        val tileCount = 2.0.pow(zoom)
        val tileX = ((gridX + 0.5) * width / AMAP_STATIC_PROJECTION_TILE_SIZE)
            .coerceIn(0.0, tileCount)
        val tileY = ((gridY + 0.5) * height / AMAP_STATIC_PROJECTION_TILE_SIZE)
            .coerceIn(0.0, tileCount)
        val longitude = tileX / tileCount * 360.0 - 180.0
        val mercatorLatitude = Math.PI * (1.0 - 2.0 * tileY / tileCount)
        val latitude = Math.toDegrees(atan(sinh(mercatorLatitude)))
        return Point.fromLngLat(longitude, latitude)
    }
}

private val AMAP_STATIC_FRAME_OFFSETS = listOf(
    0L to 0L,
    -1L to 0L,
    1L to 0L,
    0L to -1L,
    0L to 1L,
    -1L to -1L,
    1L to -1L,
    -1L to 1L,
    1L to 1L,
)

private const val ROUTE_PREVIEW_TILE_SIZE = 256.0
private const val AMAP_STATIC_PROJECTION_TILE_SIZE = 512.0
private const val AMAP_STATIC_MAX_IMAGE_SIZE = 1024
private const val MAX_ROUTE_PREVIEW_ZOOM_OFFSET = 8f
private const val AMAP_STATIC_MAP_REQUEST_DEBOUNCE_MILLIS = 350L


private fun Offset.rotatedBy(degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    val cosine = cos(radians).toFloat()
    val sine = sin(radians).toFloat()
    return Offset(
        x = x * cosine - y * sine,
        y = x * sine + y * cosine,
    )
}

private fun Point.pannedBy(pan: Offset, zoom: Int, tileSizePx: Double): Point {
    val tileCount = 2.0.pow(zoom)
    val tileX = (lonToTileX(longitude(), zoom) - pan.x / tileSizePx)
        .coerceIn(0.0, tileCount)
    val tileY = (latToTileY(latitude(), zoom) - pan.y / tileSizePx)
        .coerceIn(0.0, tileCount)
    val longitude = tileX / tileCount * 360.0 - 180.0
    val mercatorLatitude = Math.PI * (1.0 - 2.0 * tileY / tileCount)
    val latitude = Math.toDegrees(atan(sinh(mercatorLatitude)))
    return Point.fromLngLat(longitude, latitude)
}

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
