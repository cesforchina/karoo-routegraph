/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.gpx

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import de.timklge.karooroutegraph.amap.share.PhoneGpxShareActivity
import java.io.File
import java.io.FileOutputStream

class GpxFileManager(private val context: Context) {
    fun createShareIntent(fileName: String, content: ByteArray): Intent {
        val file = writeToShareCache(fileName, content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = GPX_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createPhoneShareIntent(fileName: String, content: ByteArray): Intent {
        val file = writeToShareCache(fileName, content)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            file,
        )
        return PhoneGpxShareActivity.createIntent(context, uri, file.name)
    }

    fun saveToDownloads(fileName: String, content: ByteArray): Uri {
        val safeName = sanitizeFileName(fileName)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, GPX_MIME_TYPE)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/KarooRouteGraph",
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = requireNotNull(
                context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                )
            ) { "Unable to create GPX file in Downloads" }

            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(content)
                } ?: error("Unable to open GPX output stream")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                context.contentResolver.delete(uri, null, null)
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val directory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "KarooRouteGraph",
            )
            check(directory.exists() || directory.mkdirs()) {
                "Unable to create GPX download directory"
            }
            val file = File(directory, safeName)
            FileOutputStream(file).use { it.write(content) }
            Uri.fromFile(file)
        }
    }

    private fun writeToShareCache(fileName: String, content: ByteArray): File {
        val directory = File(context.cacheDir, "shared_routes")
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create GPX share directory"
        }
        return File(directory, sanitizeFileName(fileName)).also { file ->
            FileOutputStream(file).use { it.write(content) }
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        val base = fileName
            .removeSuffix(".gpx")
            .replace(Regex("""[^A-Za-z0-9._\-\u4e00-\u9fff]+"""), "_")
            .trim('_')
            .take(80)
            .ifBlank { "amap-route" }
        return "$base.gpx"
    }

    private companion object {
        const val GPX_MIME_TYPE = "application/gpx+xml"
    }
}
