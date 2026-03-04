package com.bysel.trader.data.auth

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isAuthEndpoint = path.startsWith("/auth/")

        if (isAuthEndpoint) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()

        AuthSessionManager.getAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.header("Authorization", "Bearer $it") }

        AuthSessionManager.getUserId()
            ?.let { builder.header("user_id", it.toString()) }

        return chain.proceed(builder.build())
    }
}
