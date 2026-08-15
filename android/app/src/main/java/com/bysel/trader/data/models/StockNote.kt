package com.bysel.trader.data.models

import com.google.gson.annotations.SerializedName

data class StockNoteDto(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("text") val text: String = "",
    @SerializedName("updatedAt") val updatedAt: Long = 0L,
)

data class StockNotesListResponse(
    @SerializedName("notes") val notes: List<StockNoteDto> = emptyList(),
)

data class StockNoteUpsertRequest(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("text") val text: String,
)

data class StockNoteDeleteResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("symbol") val symbol: String = "",
)

data class StockNoteRecord(
    val text: String,
    val updatedAt: Long,
)
