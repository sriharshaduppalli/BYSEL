package com.bysel.trader.data.auth

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {
    private val publicAuthPaths = setOf(
        "/auth/register",
        "/auth/login",
        "/auth/refresh",
        "/auth/send-otp",
        "/auth/verify-otp",
        "/auth/firebase-phone",
        "/auth/password-reset/request",
        "/auth/password-reset/confirm"
    )

    private fun shouldAttachAuthHeaders(path: String): Boolean {
        if (!path.startsWith("/auth/")) {
            return true
        }
        return path !in publicAuthPaths
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (!shouldAttachAuthHeaders(path)) {
            return chain.proceed(request)
        }

        val builder = request.newBuilder()

        AuthSessionManager.getAccessToken()
            ?.takeIf { it.isNotBlank() }
            ?.let { builder.header("Authorization", "Bearer $it") }

        // FastAPI converts underscore headers to hyphen by default, so send both
        // forms until all trading routes are JWT-only.
        AuthSessionManager.getUserId()?.let { uid ->
            builder.header("user_id", uid.toString())
            builder.header("user-id", uid.toString())
        }

        return chain.proceed(builder.build())
    }
}
