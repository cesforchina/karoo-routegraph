/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mapsforge.core.model.Tile as MapsforgeTile
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.datastore.MultiMapDataStore
import org.mapsforge.map.layer.cache.InMemoryTileCache
import org.mapsforge.map.layer.renderer.DatabaseRenderer
import org.mapsforge.map.layer.renderer.RendererJob
import org.mapsforge.map.model.DisplayModel
import org.mapsforge.map.reader.MapFile
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes
import org.mapsforge.map.rendertheme.rule.RenderThemeFuture
import java.io.File

class LocalMapTileRenderer(context: Context) {
    private val applicationContext = context.applicationContext
    private val renderMutex = Mutex()
    private val displayModel = DisplayModel().apply {
        setFixedTileSize(TILE_SIZE)
    }
    private val tileCache = InMemoryTileCache(MEMORY_TILE_CAPACITY)
    private val renderThemeFuture: RenderThemeFuture
    private var openMapFiles = emptyList<MapFile>()
    private var mapDataStore: MultiMapDataStore? = null
    private var databaseRenderer: DatabaseRenderer? = null
    private var currentFileSignature: List<String> = emptyList()

    init {
        AndroidGraphicFactory.createInstance(applicationContext)
        renderThemeFuture = RenderThemeFuture(
            AndroidGraphicFactory.INSTANCE,
            MapsforgeThemes.BIKER,
            displayModel,
        )
        Thread(renderThemeFuture, "mapsforge-render-theme").start()
    }

    suspend fun render(tile: Tile): Bitmap? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            if (!hasStoragePermission()) {
                Log.w(KarooRouteGraphExtension.TAG, "Cannot render local map tile without storage permission")
                return@withLock null
            }
            ensureMapFiles()
            val renderer = databaseRenderer ?: return@withLock null
            val dataStore = mapDataStore ?: return@withLock null
            val mapsforgeTile = MapsforgeTile(
                tile.x,
                tile.y,
                tile.z.toByte(),
                TILE_SIZE,
            )
            if (!dataStore.supportsTile(mapsforgeTile)) {
                return@withLock null
            }
            val rendererJob = RendererJob(
                mapsforgeTile,
                dataStore,
                renderThemeFuture,
                displayModel,
                1f,
                false,
                false,
            )
            val renderedTile = renderer.executeJob(rendererJob) ?: return@withLock null
            try {
                AndroidGraphicFactory.getBitmap(renderedTile).copy(Bitmap.Config.ARGB_8888, false)
            } finally {
                renderedTile.decrementRefCount()
            }
        }
    }

    private fun ensureMapFiles() {
        val mapDirectory = File(
            File(Environment.getExternalStorageDirectory(), "offline"),
            "maps",
        )
        val files = mapDirectory.listFiles { file ->
            file.isFile && file.extension.equals("map", ignoreCase = true)
        }?.sortedBy(File::getName).orEmpty()
        val signature = files.map { file ->
            "${file.absolutePath}:${file.length()}:${file.lastModified()}"
        }
        if (signature == currentFileSignature && databaseRenderer != null) return

        closeMapFiles()
        currentFileSignature = signature
        if (files.isEmpty()) {
            Log.w(KarooRouteGraphExtension.TAG, "No local .map files found in ${mapDirectory.absolutePath}")
            return
        }

        val dataStore = MultiMapDataStore(MultiMapDataStore.DataPolicy.DEDUPLICATE)
        val openedFiles = mutableListOf<MapFile>()
        files.forEach { file ->
            try {
                val mapFile = MapFile(file)
                dataStore.addMapDataStore(mapFile, false, false)
                openedFiles += mapFile
            } catch (error: Exception) {
                Log.e(
                    KarooRouteGraphExtension.TAG,
                    "Could not open local map ${file.name}",
                    error,
                )
            }
        }
        if (openedFiles.isEmpty()) {
            dataStore.close()
            return
        }

        openMapFiles = openedFiles
        mapDataStore = dataStore
        databaseRenderer = DatabaseRenderer(
            dataStore,
            AndroidGraphicFactory.INSTANCE,
            tileCache,
            null,
            true,
            false,
            null,
        )
        Log.i(
            KarooRouteGraphExtension.TAG,
            "Local map renderer loaded ${openedFiles.size} .map files",
        )
    }

    private fun closeMapFiles() {
        databaseRenderer?.interruptAndDestroy()
        databaseRenderer = null
        mapDataStore?.close()
        mapDataStore = null
        openMapFiles = emptyList()
        tileCache.purge()
    }

    private fun hasStoragePermission(): Boolean =
        applicationContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TILE_SIZE = 256
        const val MEMORY_TILE_CAPACITY = 48
    }
}
