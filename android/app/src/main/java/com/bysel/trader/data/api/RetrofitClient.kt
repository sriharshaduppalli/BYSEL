package com.bysel.trader.data.api

import com.bysel.trader.BuildConfig
import com.bysel.trader.data.auth.AuthInterceptor
import com.bysel.trader.data.auth.TokenRefreshAuthenticator
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private val BASE_URL = BuildConfig.MARKET_REST_URL

    private fun normalizePin(pin: String): String? {
        val normalized = pin.trim()
        if (normalized.isEmpty()) {
            return null
        }
        return if (normalized.startsWith("sha256/")) normalized else "sha256/$normalized"
    }

    private fun buildCertificatePinner(): CertificatePinner? {
        val host = BuildConfig.CERT_PIN_HOST.trim()
        if (host.isEmpty()) {
            return null
        }

        val pins = listOfNotNull(
            normalizePin(BuildConfig.CERT_PIN_PRIMARY),
            normalizePin(BuildConfig.CERT_PIN_BACKUP),
        )
        if (pins.isEmpty()) {
            return null
        }

        val builder = CertificatePinner.Builder()
        pins.forEach { pin -> builder.add(host, pin) }
        return builder.build()
    }

    val httpClient: OkHttpClient by lazy {
        val loggingLevel = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenRefreshAuthenticator())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = loggingLevel
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Pinning is optional until release pins are provided via BuildConfig env values.
        buildCertificatePinner()?.let { pinner ->
            builder.certificatePinner(pinner)
        }

        builder.build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: BYSELApiService by lazy {
        retrofit.create(BYSELApiService::class.java)
    }
}
