package com.bysel.core.auth

data class SessionBundle(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val userId: Int? = null
)

interface SessionProvider {
    fun currentSession(): SessionBundle?
}
