package com.bysel.trader.data.api

import com.bysel.trader.data.models.*
import retrofit2.http.*

interface BYSELApiService {
    @GET("/quotes")
    suspend fun getQuotes(@Query("symbols") symbols: String): List<Quote>

    @GET("/holdings")
    suspend fun getHoldings(): List<Holding>

    @POST("/order")
    suspend fun placeOrder(@Body order: Order): OrderResponse

    @DELETE("/alert/{id}")
    suspend fun deleteAlert(@Path("id") alertId: Int): Map<String, String>

    @GET("/health")
    suspend fun healthCheck(): Map<String, String>
}
