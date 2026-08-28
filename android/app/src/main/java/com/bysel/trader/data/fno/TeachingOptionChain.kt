package com.bysel.trader.data.fno

import com.bysel.trader.data.models.OptionChainResponse
import com.bysel.trader.data.models.OptionContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Local educational board used when the live teaching chain is unreachable.
 * Mirrors the server fallback so Options → Learn the chain never shows HTTP 404.
 */
internal val EDUCATIONAL_SPOT = mapOf(
    "NIFTY" to 24_500.0,
    "NIFTY50" to 24_500.0,
    "BANKNIFTY" to 55_000.0,
    "NIFTYBANK" to 55_000.0,
    "FINNIFTY" to 26_500.0,
    "MIDCPNIFTY" to 13_000.0,
    "NIFTYNXT50" to 69_000.0,
)

object TeachingOptionChain {
    fun build(symbol: String, expiry: String): OptionChainResponse {
        val raw = symbol.trim().uppercase()
        val spot = EDUCATIONAL_SPOT[raw] ?: 1_000.0
        val step = when {
            spot >= 1_000 -> 50.0
            spot >= 300 -> 20.0
            else -> 10.0
        }
        val atm = round(spot / step) * step
        val seed = "$raw:$expiry".sumOf { it.code }
        val contracts = (-5..5).map { index ->
            val strike = atm + (index * step)
            val strikeSeed = seed + (strike * 10).toInt()
            val callIv = max(0.12, min(0.55, 0.20 + (max(index, 0) * 0.012) + ((strikeSeed % 17) / 1000.0)))
            val putIv = max(0.12, min(0.60, 0.23 + (max(-index, 0) * 0.015) + (((strikeSeed + 11) % 19) / 1000.0)))
            val iv = (callIv + putIv) / 2.0
            val callOi = max(250, 15_000 - (abs(index) * 780) + (strikeSeed % 500))
            val putOi = max(250, 16_200 - (abs(index) * 720) + ((strikeSeed + 37) % 500))
            OptionContract(
                strike = strike,
                callLtp = max(0.5, (iv * 80.0) - (abs(index) * 6.0)),
                putLtp = max(0.5, (iv * 90.0) - (abs(index) * 5.0)),
                callOi = callOi,
                putOi = putOi,
                callOiChange = (strikeSeed % 240) - 120,
                putOiChange = ((strikeSeed + 77) % 240) - 120,
                impliedVolatility = iv,
                callDelta = max(0.05, min(0.95, 0.50 - (index * 0.07))),
                putDelta = -max(0.05, min(0.95, 0.50 + (index * 0.07))),
                gamma = 0.0012,
                theta = -8.0,
                vega = 12.0,
                callIv = callIv,
                putIv = putIv,
            )
        }
        val putOi = contracts.sumOf { it.putOi }.toDouble()
        val callOi = contracts.sumOf { it.callOi }.toDouble().coerceAtLeast(1.0)
        return OptionChainResponse(
            symbol = raw,
            expiry = expiry,
            spot = spot,
            generatedAt = utcNow(),
            contracts = contracts,
            source = "synthetic",
            pcr = putOi / callOi,
            ivSkew = (contracts.last().putIv ?: 0.0) - (contracts.first().callIv ?: 0.0),
            atmIv = contracts.firstOrNull { it.strike == atm }?.impliedVolatility
                ?: contracts.minByOrNull { abs(it.strike - spot) }?.impliedVolatility,
            notes = listOf(
                "Teaching chain — live exchange chain unavailable.",
                "PCR / IV skew are computed on this educational board — verify with your broker.",
                "Spot is illustrative because a live index print was unavailable.",
            ),
        )
    }

    fun isMissingLiveSpot(error: Exception): Boolean {
        val raw = error.message.orEmpty()
        val http = (error as? retrofit2.HttpException)?.code()
        return http == 404 ||
            raw.contains("HTTP 404", ignoreCase = true) ||
            raw.contains("live spot", ignoreCase = true) ||
            raw.contains("quote not found", ignoreCase = true) ||
            raw.contains("no futures contract", ignoreCase = true)
    }

    private fun utcNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
