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
import kotlinx.coroutines.withTimeout

data class AmapHttpResponse(val statusCode: Int, val body: String)

fun interface AmapTransport {
    suspend fun get(url: String): AmapHttpResponse
}

/**
 * Routes requests through KarooSystemService, matching the project's existing HTTP path and allowing
 * Karoo to use its companion-provided connection.
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
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}

sealed class AmapException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class AmapMissingKeyException :
    AmapException("AMap Web Service API key is not configured")

class AmapNetworkException(detail: String, cause: Throwable? = null) :
    AmapException("AMap network request failed: $detail", cause)

class AmapTimeoutException(timeoutMillis: Long, cause: Throwable? = null) :
    AmapException("AMap request timed out after $timeoutMillis ms", cause)

class AmapRateLimitException(val infoCode: String?, detail: String) :
    AmapException("AMap rate limit exceeded ($infoCode): $detail")

class AmapServiceException(val infoCode: String?, detail: String) :
    AmapException("AMap service error ($infoCode): $detail")

class AmapInvalidResponseException(detail: String, cause: Throwable? = null) :
    AmapException("Invalid AMap response: $detail", cause)
