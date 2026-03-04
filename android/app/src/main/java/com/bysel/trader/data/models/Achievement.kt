package com.bysel.trader.data.models

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null
)
