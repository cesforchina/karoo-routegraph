/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import de.timklge.karooroutegraph.R
import de.timklge.karooroutegraph.theme.AppTheme
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.concurrent.thread

class PhoneGpxShareActivity : ComponentActivity() {
    private var downloadUrl by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var wasDownloaded by mutableStateOf(false)
    private val serverLock = Any()
    private var server: GpxTransferServer? = null
    private var destroyed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = IntentCompat.getParcelableExtra(intent, EXTRA_GPX_URI, Uri::class.java)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        if (uri == null) {
            finish()
            return
        }

        setContent {
            AppTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = getString(R.string.phone_share_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    when {
                        errorMessage != null -> Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        downloadUrl == null -> CircularProgressIndicator()
                        else -> {
                            val url = downloadUrl.orEmpty()
                            Image(
                                bitmap = createQrBitmap(url).asImageBitmap(),
                                contentDescription = getString(R.string.phone_share_qr_description),
                                modifier = Modifier.size(260.dp),
                            )
                            Text(
                                text = getString(R.string.phone_share_instructions),
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = url,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            if (wasDownloaded) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = getString(R.string.phone_share_downloaded),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = ::finish,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(getString(R.string.close))
                    }
                }
            }
        }

        thread(name = "gpx-share-start") {
            runCatching {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error(getString(R.string.phone_share_read_failed))
                val address = findLocalIpv4Address()
                    ?: error(getString(R.string.phone_share_no_wifi))
                val transferServer = GpxTransferServer(
                    content = bytes,
                    fileName = fileName,
                    onDownloaded = {
                        runOnUiThread { wasDownloaded = true }
                    },
                )
                val canStart = synchronized(serverLock) {
                    if (destroyed) {
                        false
                    } else {
                        server = transferServer
                        true
                    }
                }
                if (!canStart) {
                    transferServer.close()
                    return@runCatching
                }
                transferServer.start()
                runOnUiThread {
                    downloadUrl =
                        "http://${address.hostAddress}:${transferServer.port}/${transferServer.token}.gpx"
                }
            }.onFailure {
                runOnUiThread {
                    errorMessage = it.message ?: getString(R.string.failed)
                }
            }
        }
    }

    override fun onDestroy() {
        synchronized(serverLock) {
            destroyed = true
            server?.close()
            server = null
        }
        super.onDestroy()
    }

    private fun createQrBitmap(value: String): Bitmap {
        val matrix = QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            480,
            480,
            mapOf(EncodeHintType.MARGIN to 1),
        )
        val pixels = IntArray(matrix.width * matrix.height) { index ->
            val x = index % matrix.width
            val y = index / matrix.width
            if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(
            pixels,
            matrix.width,
            matrix.height,
            Bitmap.Config.RGB_565,
        )
    }

    private fun findLocalIpv4Address(): Inet4Address? {
        val candidates = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { network ->
                network.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .filterNot { it.isLoopbackAddress || it.isLinkLocalAddress }
                    .map { network.name to it }
            }
        return candidates
            .sortedBy { (name, _) -> if (name.startsWith("wlan")) 0 else 1 }
            .firstOrNull()
            ?.second
    }

    companion object {
        private const val EXTRA_GPX_URI = "gpx_uri"
        private const val EXTRA_FILE_NAME = "file_name"

        fun createIntent(context: Context, uri: Uri, fileName: String): Intent =
            Intent(context, PhoneGpxShareActivity::class.java).apply {
                putExtra(EXTRA_GPX_URI, uri)
                putExtra(EXTRA_FILE_NAME, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}

private class GpxTransferServer(
    private val content: ByteArray,
    fileName: String,
    private val onDownloaded: () -> Unit,
) {
    private val executor = Executors.newCachedThreadPool()
    private val serverSocket = ServerSocket(0)
    private val encodedFileName = URLEncoder.encode(
        fileName.ifBlank { "route.gpx" },
        Charsets.UTF_8.name(),
    ).replace("+", "%20")

    val port: Int get() = serverSocket.localPort
    val token: String = ByteArray(16)
        .also(SecureRandom()::nextBytes)
        .joinToString("") { "%02x".format(Locale.US, it) }

    fun start() {
        executor.execute {
            while (!serverSocket.isClosed) {
                runCatching { serverSocket.accept() }
                    .onSuccess { socket ->
                        executor.execute { handle(socket) }
                    }
                    .onFailure {
                        if (!serverSocket.isClosed) close()
                    }
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use {
            it.soTimeout = 5_000
            val input = BufferedInputStream(it.getInputStream())
            val requestLine = readAsciiLine(input)
            while (readAsciiLine(input).isNotEmpty()) {
                // Consume headers.
            }

            val requestedPath = requestLine
                .split(' ')
                .getOrNull(1)
                ?.substringBefore('?')
            val output = BufferedOutputStream(it.getOutputStream())
            if (requestLine.startsWith("GET ") && requestedPath == "/$token.gpx") {
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/gpx+xml\r\n")
                    append("Content-Length: ${content.size}\r\n")
                    append("Content-Disposition: attachment; filename*=UTF-8''$encodedFileName\r\n")
                    append("Cache-Control: no-store\r\n")
                    append("Connection: close\r\n\r\n")
                }
                output.write(headers.toByteArray(Charsets.US_ASCII))
                output.write(content)
                output.flush()
                onDownloaded()
            } else {
                val message = "Route not found"
                val headers = buildString {
                    append("HTTP/1.1 404 Not Found\r\n")
                    append("Content-Type: text/plain; charset=utf-8\r\n")
                    append("Content-Length: ${message.toByteArray().size}\r\n")
                    append("Connection: close\r\n\r\n")
                    append(message)
                }
                output.write(headers.toByteArray(Charsets.UTF_8))
                output.flush()
            }
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val result = StringBuilder()
        while (true) {
            val next = input.read()
            if (next == -1 || next == '\n'.code) break
            if (next != '\r'.code) result.append(next.toChar())
            if (result.length > 8_192) break
        }
        return result.toString()
    }

    fun close() {
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }
}
