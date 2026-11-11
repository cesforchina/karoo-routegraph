/*
 * Copyright 2026 timklge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package de.timklge.karooroutegraph.amap.api

import de.timklge.karooroutegraph.KarooRouteGraphExtension
import de.timklge.karooroutegraph.KarooSystemServiceProvider
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AmapHttpResponse(val statusCode: Int, val body: String)

fun interface AmapTransport {
    suspend fun get(url: String): AmapHttpResponse
}

/**
 * Uses the Karoo's own network connection. The KarooSystemService HTTP relay can exit through an
 * overseas Hammerhead endpoint, which makes domestic AMap keys hit error 10045 and also imposes a
 * response-size ceiling on long routes.
 */
class DirectAmapTransport(
    timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : AmapTransport {
    private val client = OkHttpClient.Builder()
        .callTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun get(url: String): AmapHttpResponse = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", KarooRouteGraphExtension.TAG)
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                AmapHttpResponse(
                    statusCode = response.code,
                    body = response.body.string(),
                )
            }
        } catch (error: IOException) {
            throw AmapNetworkException(error.message ?: "direct request failed", error)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}

/**
 * Legacy relay transport retained for devices without direct networking.
 */
class KarooAmapTransport(
    private val karooSystemServiceProvider: KarooSystemServiceProvider,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : AmapTransport {
    override suspend fun get(url: String): AmapHttpResponse {
        try {
            return withTimeout(timeoutMillis) {
                callbackFlow {
                    val listenerId = karooSystemServiceProvider.karooSystemService.addConsumer(
                        OnHttpResponse.MakeHttpRequest(
                            method = "GET",
                            url = url,
                            waitForConnection = false,
                            headers = mapOf(
                                "User-Agent" to KarooRouteGraphExtension.TAG,
                                "Accept" to "application/json",
                            ),
                        ),
                        onError = { error -> close(AmapNetworkException(error)) },
                    ) { event: OnHttpResponse ->
                        val complete = event.state as? HttpResponseState.Complete
                            ?: return@addConsumer
                        complete.error?.let {
                            close(AmapNetworkException(it))
                            return@addConsumer
                        }
                        trySendBlocking(
                            AmapHttpResponse(
                                statusCode = complete.statusCode,
                                body = (complete.body ?: ByteArray(0)).toString(Charsets.UTF_8),
                            )
                        )
                        close()
                    }
                    awaitClose {
                        karooSystemServiceProvider.karooSystemService.removeConsumer(listenerId)
                    }
                }.single()
            }
        } catch (error: TimeoutCancellationException) {
            throw AmapTimeoutException(timeoutMillis, error)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
    }
}

sealed class AmapException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class AmapMissingKeyException :
    AmapException("AMap Web Service API key is not configured")

class AmapNetworkException(val detail: String, cause: Throwable? = null) :
    AmapException("AMap network request failed: $detail", cause)

class AmapTimeoutException(timeoutMillis: Long, cause: Throwable? = null) :
    AmapException("AMap request timed out after $timeoutMillis ms", cause)

class AmapRateLimitException(val infoCode: String?, detail: String) :
    AmapException("AMap rate limit exceeded ($infoCode): $detail")

class AmapServiceException(val infoCode: String?, detail: String) :
    AmapException("AMap service error ($infoCode): $detail")

class AmapInvalidResponseException(detail: String, cause: Throwable? = null) :
    AmapException("Invalid AMap response: $detail", cause)
