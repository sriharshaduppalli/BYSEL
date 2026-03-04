package com.bysel.trader.data.auth

import com.bysel.trader.BuildConfig
import com.bysel.trader.data.api.BYSELApiService
import com.bysel.trader.data.models.AuthResponse
import com.bysel.trader.data.models.RefreshTokenRequest
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object AuthTokenRefresher {
    private val refreshClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val refreshService: BYSELApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.MARKET_REST_URL)
            .client(refreshClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BYSELApiService::class.java)
    }

    suspend fun refresh(refreshToken: String): AuthResponse {
        return refreshService.refreshToken(RefreshTokenRequest(refreshToken = refreshToken))
    }
}
