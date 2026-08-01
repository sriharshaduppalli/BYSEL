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
            .addInterceptor(RequestMetadataInterceptor())
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenRefreshAuthenticator())
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = loggingLevel
            })
            // Market/trading + heatmap share this client. Render free-tier cold starts
            // often exceed 25s, so keep enough headroom for heatmap wake-ups.
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // Pinning is optional until release pins are provided via BuildConfig env values.
        buildCertificatePinner()?.let { pinner ->
            builder.certificatePinner(pinner)
        }

        builder.build()
    }

    /**
     * Dedicated client for AI endpoints.
     *
     * Render free-tier cold starts often exceed 30–60s. The shared [httpClient] uses a 25s
     * callTimeout, which is why the first AI chat message used to fail with a generic
     * "couldn't process that" error.
     */
    val aiHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Dedicated client for auth endpoints.
     *
     * Render free-tier cold starts often exceed the shared 25s callTimeout, which
     * surfaces as "timeout" on Login/Register even when credentials are valid.
     */
    val authHttpClient: OkHttpClient by lazy {
        httpClient.newBuilder()
            .callTimeout(90, TimeUnit.SECONDS)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val gson by lazy { SafeGsonFactory.create() }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val aiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(aiHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    private val authRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(authHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    val apiService: BYSELApiService by lazy {
        retrofit.create(BYSELApiService::class.java)
    }

    val aiApiService: BYSELApiService by lazy {
        aiRetrofit.create(BYSELApiService::class.java)
    }

    val authApiService: BYSELApiService by lazy {
        authRetrofit.create(BYSELApiService::class.java)
    }
}
