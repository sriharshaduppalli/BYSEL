package com.bysel.trader.data.api

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Records successful round-trips so UI copy can distinguish a sleeping host
 * from a slow Yahoo/quote path on an already-awake Render instance.
 */
class ServerReachabilityInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.encodedPath
        if (path == "/health" || path == "/warmup") {
            ServerReachability.markWakeStarted()
        }
        val startedAtNs = System.nanoTime()
        val response = chain.proceed(chain.request())
        if (response.isSuccessful) {
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNs)
            ServerReachability.markSuccess(elapsedMs)
        }
        return response
    }
}
