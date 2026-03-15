package com.bysel.trader.data.api

import android.util.Log
import com.bysel.trader.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.UUID

class RequestMetadataInterceptor : Interceptor {
    companion object {
        private const val TAG = "RequestMetadata"
        private const val TRACE_HEADER = "X-Trace-Id"
        private const val CLIENT_VERSION_HEADER = "X-Client-Version"
        private const val CLIENT_PLATFORM_HEADER = "X-Client-Platform"
        private const val SLOW_REQUEST_THRESHOLD_MS = 1200L
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val traceId = originalRequest.header(TRACE_HEADER)?.trim().takeUnless { it.isNullOrBlank() }
            ?: "trc-${UUID.randomUUID().toString().replace("-", "").take(16)}"

        val request = originalRequest.newBuilder()
            .header(TRACE_HEADER, traceId)
            .header(CLIENT_VERSION_HEADER, BuildConfig.VERSION_NAME)
            .header(CLIENT_PLATFORM_HEADER, "android")
            .build()

        val startedAtNs = System.nanoTime()

        return try {
            val response = chain.proceed(request)
            val durationMs = (System.nanoTime() - startedAtNs) / 1_000_000
            val serverTraceId = response.header(TRACE_HEADER) ?: traceId

            if (durationMs >= SLOW_REQUEST_THRESHOLD_MS) {
                Log.w(
                    TAG,
                    "Slow request ${request.method} ${request.url.encodedPath} ${durationMs}ms traceId=$serverTraceId"
                )
            }

            response
        } catch (io: IOException) {
            val message = io.message ?: "Network request failed"
            throw IOException("$message (traceId=$traceId)", io)
        }
    }
}