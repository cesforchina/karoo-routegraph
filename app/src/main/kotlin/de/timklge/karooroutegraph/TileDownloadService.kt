/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.timklge.karooroutegraph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import de.timklge.karooroutegraph.datatypes.minimap.MinimapViewModelProvider
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sinh

class TileDownloadService(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val minimapViewModelProvider: MinimapViewModelProvider,
    private val localMapTileRenderer: LocalMapTileRenderer,
    context: Context,
) {
    data class CachedTile(val lastAccessed: Instant, val bitmap: Bitmap)

    private data class AmapStaticCacheKey(
        val location: String,
        val zoom: Int,
        val width: Int,
        val height: Int,
    )

    private val cacheDir = File(context.cacheDir, "tiles")
    private val inMemoryCache = mutableMapOf<Tile, CachedTile>()
    private val mutex = Mutex()
    private val maxCacheSize = 50
    private val maxCacheAge = Duration.ofMinutes(30)
    private val tileEndpoints = listOf(
        "https://tile.openstreetmap.org",
        "https://tile.openstreetmap.de",
    )

    @Volatile
    private var preferredTileEndpointIndex = 0

    private var lastAmapRequestStartedAtMillis = 0L
    private val amapRequestMutex = Mutex()
    private val directHttpClient = OkHttpClient()
    private val amapStaticMemoryCache =
        object : LinkedHashMap<AmapStaticCacheKey, Bitmap>(
            AMAP_MEMORY_CACHE_SIZE,
            0.75f,
            true,
        ) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<AmapStaticCacheKey, Bitmap>?,
            ): Boolean = size > AMAP_MEMORY_CACHE_SIZE
        }

    // Map gestures can invalidate a whole zoom level at once. Keeping only the latest
    // pending request prevents obsolete local-render jobs from blocking the visible viewport.
    private val downloadQueue: Channel<Tile> = Channel(Channel.CONFLATED)

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        startDownloadJob()
    }

    private var downloadJob: Job? = null

    fun startDownloadJob() {
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            for (tile in downloadQueue) {
                try {
                    val downloadedTile = getTileIfAvailableInstantly(tile)
                    if (downloadedTile != null) {
                        Log.d(KarooRouteGraphExtension.TAG, "Tile $tile already in cache")
                        continue
                    }
                    val fetchedBitmap = fetchTileFromNetwork(tile)

                    // Save to caches (file and memory)
                    mutex.withLock {
                        val tileFile = getTileFile(tile)
                        saveTileToFile(fetchedBitmap, tileFile)
                        gcCache() // Clean up memory cache before adding new item
                        inMemoryCache[tile] = CachedTile(Instant.now(), fetchedBitmap)
                        Log.d(KarooRouteGraphExtension.TAG, "Fetched tile ${tile}, saved to file and added to memory cache")
                    }

                    minimapViewModelProvider.update {
                        it.copy(lastTileDownloadedAt = Instant.now())
                    }
                } catch (e: Exception) {
                    Log.e(KarooRouteGraphExtension.TAG, "Error downloading tile $tile", e)
                }
            }
        }
    }

    private fun getTileFile(tile: Tile): File {
        return File(
            cacheDir,
            "${tile.source.value}-${tile.style.value}-${tile.z}-${tile.x}-${tile.y}.png",
        )
    }

    suspend fun getTileIfAvailableInstantly(tile: Tile): Bitmap? {
        mutex.withLock {
            val cachedTile = inMemoryCache[tile]
            if (cachedTile != null) {
                // Update last accessed time in memory cache
                inMemoryCache[tile] = cachedTile.copy(lastAccessed = Instant.now())
                return cachedTile.bitmap
            }
        }

        // Check file system cache (outside memory cache lock initially for read)
        val tileFile = getTileFile(tile)
        if (tileFile.exists()) {
            try {
                val bitmap = loadTileFromFile(tileFile)
                if (bitmap != null) {
                    // Add to memory cache (needs lock)
                    mutex.withLock {
                        // Double check if another thread added it while we were reading the file
                        if (!inMemoryCache.containsKey(tile)) {
                            gcCache() // Make space if needed before adding
                            inMemoryCache[tile] = CachedTile(Instant.now(), bitmap)
                            Log.d(KarooRouteGraphExtension.TAG, "Loaded tile $tile from file cache into memory")
                        } else {
                            // Already in memory, update access time
                            inMemoryCache[tile] = inMemoryCache[tile]!!.copy(lastAccessed = Instant.now())
                        }
                        // Return the bitmap from the (potentially updated) memory cache entry
                        return inMemoryCache[tile]?.bitmap
                    }
                } else {
                    // File exists but couldn't be decoded, delete it
                    Log.w(KarooRouteGraphExtension.TAG, "Could not decode tile file ${tileFile.path}, deleting.")
                    tileFile.delete()
                }
            } catch (e: IOException) {
                Log.e(KarooRouteGraphExtension.TAG, "Error reading tile file ${tileFile.path}", e)
                // Optionally delete corrupted file: tileFile.delete()
            }
        }

        return null // Not found in memory or file cache
    }

    fun queueTileDownload(tile: Tile) {
        // If not available instantly, add to download queue
        downloadQueue.trySendBlocking(tile)
    }

    suspend fun getAmapStaticMap(
        centerLongitude: Double,
        centerLatitude: Double,
        zoom: Int,
        width: Int,
        height: Int,
    ): Bitmap {
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank()) {
            throw IOException("AMap Web Service API key is not configured")
        }
        require(zoom in 1..AMAP_MAX_ZOOM) {
            "AMap static maps support zoom levels 1 through $AMAP_MAX_ZOOM"
        }
        val requestWidth = width.coerceIn(1, AMAP_MAX_IMAGE_SIZE)
        val requestHeight = height.coerceIn(1, AMAP_MAX_IMAGE_SIZE)
        val location = String.format(
            Locale.US,
            "%.6f,%.6f",
            centerLongitude,
            centerLatitude,
        )
        val cacheKey = AmapStaticCacheKey(
            location = location,
            zoom = zoom,
            width = requestWidth,
            height = requestHeight,
        )
        return amapRequestMutex.withLock {
            getCachedAmapStaticMap(cacheKey)?.let { return@withLock it }

            awaitAmapRequestSlot()
            Log.d(
                KarooRouteGraphExtension.TAG,
                "AMap static map requested=${requestWidth}x$requestHeight, " +
                    "canvas=${width}x$height, zoom=$zoom, scale=1",
            )
            val url = buildString {
                append("$AMAP_STATIC_MAP_ENDPOINT?")
                append("key=${BuildConfig.AMAP_WEB_API_KEY}")
                append("&location=$location&zoom=$zoom")
                append("&size=$requestWidth*$requestHeight&scale=1")
            }
            fetchAmapBitmapDirect(url).also { bitmap ->
                amapStaticMemoryCache[cacheKey] = bitmap
                saveTileToFile(bitmap, getAmapStaticCacheFile(cacheKey))
            }
        }
    }

    private fun getCachedAmapStaticMap(key: AmapStaticCacheKey): Bitmap? {
        amapStaticMemoryCache[key]?.let {
            Log.d(KarooRouteGraphExtension.TAG, "AMap static map memory cache hit: $key")
            return it
        }

        val file = getAmapStaticCacheFile(key)
        if (!file.isFile) return null
        if (System.currentTimeMillis() - file.lastModified() > AMAP_DISK_CACHE_MAX_AGE.toMillis()) {
            file.delete()
            return null
        }
        val bitmap = loadTileFromFile(file)
        if (bitmap == null) {
            file.delete()
            return null
        }
        amapStaticMemoryCache[key] = bitmap
        Log.d(KarooRouteGraphExtension.TAG, "AMap static map disk cache hit: $key")
        return bitmap
    }

    private fun getAmapStaticCacheFile(key: AmapStaticCacheKey): File {
        val locationPart = key.location.replace(',', '_')
        return File(
            cacheDir,
            "amap-static-${key.zoom}-$locationPart-${key.width}x${key.height}.png",
        )
    }

    private suspend fun fetchTileFromNetwork(tile: Tile): Bitmap = when (tile.source) {
        MapSource.OSM -> fetchOsmTile(tile)
        MapSource.AMAP_STATIC -> fetchAmapStaticTile(tile)
        MapSource.LOCAL_MAP -> localMapTileRenderer.render(tile)
            ?: throw IOException("No local .map data covers tile ${tile.z}/${tile.x}/${tile.y}")
    }

    private suspend fun fetchOsmTile(tile: Tile): Bitmap {
        var lastError: Throwable? = null
        tileEndpoints.indices.forEach { offset ->
            val endpointIndex = (preferredTileEndpointIndex + offset) % tileEndpoints.size
            val endpoint = tileEndpoints[endpointIndex]
            try {
                return fetchBitmap("$endpoint/${tile.z}/${tile.x}/${tile.y}.png").also {
                    preferredTileEndpointIndex = endpointIndex
                }
            } catch (error: Throwable) {
                lastError = error
                Log.w(
                    KarooRouteGraphExtension.TAG,
                    "Tile endpoint failed for ${tile.z}/${tile.x}/${tile.y}: $endpoint",
                    error,
                )
            }
        }
        throw IOException("All OpenStreetMap tile endpoints failed", lastError)
    }

    private suspend fun fetchAmapStaticTile(tile: Tile): Bitmap {
        if (BuildConfig.AMAP_WEB_API_KEY.isBlank()) {
            throw IOException("AMap Web Service API key is not configured")
        }
        require(tile.z in 1..AMAP_MAX_ZOOM) {
            "AMap static maps support zoom levels 1 through $AMAP_MAX_ZOOM"
        }
        val tileCount = 2.0.pow(tile.z)
        val centerX = tile.x + 0.5
        val centerY = tile.y + 0.5
        val longitude = centerX / tileCount * 360.0 - 180.0
        val latitude = Math.toDegrees(
            atan(sinh(PI * (1.0 - 2.0 * centerY / tileCount)))
        )
        val location = String.format(Locale.US, "%.6f,%.6f", longitude, latitude)
        val url = "$AMAP_STATIC_MAP_ENDPOINT?" +
            "key=${BuildConfig.AMAP_WEB_API_KEY}&location=$location&zoom=${tile.z}" +
            "&size=256*256&scale=1"
        return amapRequestMutex.withLock {
            awaitAmapRequestSlot()
            fetchAmapBitmapDirect(url)
        }
    }

    private suspend fun awaitAmapRequestSlot() {
        val elapsed = SystemClock.elapsedRealtime() - lastAmapRequestStartedAtMillis
        if (elapsed < AMAP_REQUEST_INTERVAL_MILLIS) {
            delay(AMAP_REQUEST_INTERVAL_MILLIS - elapsed)
        }
        lastAmapRequestStartedAtMillis = SystemClock.elapsedRealtime()
    }

    private suspend fun fetchBitmap(url: String): Bitmap =
        callbackFlow {
            Log.d(KarooRouteGraphExtension.TAG, "Http tile request started")

            val listenerId = karooSystemServiceProvider.karooSystemService.addConsumer(
                OnHttpResponse.MakeHttpRequest(
                    "GET",
                    url,
                    waitForConnection = false,
                    headers = mapOf("User-Agent" to KarooRouteGraphExtension.TAG),
                ),
            ) { event: OnHttpResponse ->
                val complete = event.state as? HttpResponseState.Complete
                    ?: return@addConsumer
                val error = complete.error
                if (error != null) {
                    close(IOException(error))
                    return@addConsumer
                }

                val body = complete.body ?: ByteArray(0)
                val bitmap = BitmapFactory.decodeByteArray(body, 0, body.size)
                if (bitmap == null) {
                    val detail = body.toString(Charsets.UTF_8)
                        .replace(BuildConfig.AMAP_WEB_API_KEY, "***")
                        .take(300)
                    Log.e(
                        KarooRouteGraphExtension.TAG,
                        "Map image response could not be decoded: HTTP ${complete.statusCode}, $detail",
                    )
                    close(IOException("Map image response was not decodable: $detail"))
                } else {
                    Log.d(
                        KarooRouteGraphExtension.TAG,
                        "Map image decoded=${bitmap.width}x${bitmap.height}, " +
                            "density=${bitmap.density}, bytes=${body.size}, " +
                            "config=${bitmap.config}",
                    )
                    trySendBlocking(bitmap)
                    close()
                }
            }
            awaitClose {
                karooSystemServiceProvider.karooSystemService.removeConsumer(listenerId)
            }
        }.single()

    private suspend fun fetchAmapBitmapDirect(url: String): Bitmap =
        withContext(Dispatchers.IO) {
            Log.d(KarooRouteGraphExtension.TAG, "Direct AMap image request started")
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", KarooRouteGraphExtension.TAG)
                .build()
            directHttpClient.newCall(request).execute().use { response ->
                val body = response.body.bytes()
                val bitmap = BitmapFactory.decodeByteArray(body, 0, body.size)
                if (!response.isSuccessful || bitmap == null) {
                    val detail = body.toString(Charsets.UTF_8)
                        .replace(BuildConfig.AMAP_WEB_API_KEY, "***")
                        .take(300)
                    Log.e(
                        KarooRouteGraphExtension.TAG,
                        "Direct AMap image failed: HTTP ${response.code}, $detail",
                    )
                    throw IOException(
                        "AMap image request failed: HTTP ${response.code}, $detail"
                    )
                }
                Log.d(
                    KarooRouteGraphExtension.TAG,
                    "Direct AMap image decoded=${bitmap.width}x${bitmap.height}, " +
                        "density=${bitmap.density}, bytes=${body.size}, config=${bitmap.config}",
                )
                bitmap
            }
        }

    private fun saveTileToFile(bitmap: Bitmap, file: File) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Log.v(KarooRouteGraphExtension.TAG, "Saved tile to ${file.path}")
        } catch (e: IOException) {
            Log.e(KarooRouteGraphExtension.TAG, "Error saving tile to ${file.path}", e)
            // Attempt to delete partially written file
            file.delete()
        }
    }

    private fun loadTileFromFile(file: File): Bitmap? {
        return try {
            FileInputStream(file).use { fis ->
                BitmapFactory.decodeStream(fis)
            }.also {
                if (it != null) Log.v(KarooRouteGraphExtension.TAG, "Loaded tile from ${file.path}")
                else Log.w(KarooRouteGraphExtension.TAG, "Failed to decode bitmap from ${file.path}")
            }
        } catch (e: IOException) {
            Log.e(KarooRouteGraphExtension.TAG, "Error loading tile from ${file.path}", e)
            null
        }
    }


    private fun gcCache() {
        // Assumes this function is called within a mutex lock
        val now = Instant.now()
        val expiryTime = now.minus(maxCacheAge)

        // Remove entries older than maxCacheAge
        val iterator = inMemoryCache.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.lastAccessed.isBefore(expiryTime)) {
                iterator.remove()
                Log.d(KarooRouteGraphExtension.TAG, "GC Cache: Removed expired tile ${entry.key}")
            }
        }

        // If cache still exceeds max size, remove the oldest entries
        if (inMemoryCache.size > maxCacheSize) {
            // Sort entries by lastAccessed time (oldest first)
            val sortedEntries = inMemoryCache.entries.sortedBy { it.value.lastAccessed }
            val entriesToRemove = inMemoryCache.size - maxCacheSize

            // Remove the oldest entries
            for (i in 0 until entriesToRemove) {
                val entryToRemove = sortedEntries[i]
                inMemoryCache.remove(entryToRemove.key)
                Log.d(KarooRouteGraphExtension.TAG, "GC Cache: Removed oldest tile ${entryToRemove.key} due to size limit")
            }
        }
    }

    private companion object {
        const val AMAP_STATIC_MAP_ENDPOINT = "https://restapi.amap.com/v3/staticmap"
        const val AMAP_MAX_ZOOM = 17
        const val AMAP_MAX_IMAGE_SIZE = 1024
        const val AMAP_REQUEST_INTERVAL_MILLIS = 334L
        const val AMAP_MEMORY_CACHE_SIZE = 12
        val AMAP_DISK_CACHE_MAX_AGE: Duration = Duration.ofDays(7)
    }
}
