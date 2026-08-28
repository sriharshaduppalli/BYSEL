package com.bysel.trader.data.fno

import com.bysel.trader.data.models.FuturesContract
import com.bysel.trader.data.models.FuturesContractsResponse
import com.bysel.trader.data.models.FuturesTicketPreviewRequest
import com.bysel.trader.data.models.FuturesTicketPreviewResponse
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Local educational futures board when Cloud Run still 404s on a missing live spot.
 */
object TeachingFutures {
    private val lotHints = mapOf(
        "NIFTY" to 50,
        "BANKNIFTY" to 25,
        "FINNIFTY" to 65,
        "RELIANCE" to 250,
        "TCS" to 150,
        "INFY" to 300,
        "SBIN" to 750,
    )

    fun build(symbol: String): FuturesContractsResponse {
        val raw = symbol.trim().uppercase()
        val spot = EDUCATIONAL_SPOT[raw] ?: 1_000.0
        val lotSize = lotHints[raw] ?: lotSizeFor(spot)
        val seed = raw.sumOf { it.code }
        val today = Calendar.getInstance()
        val contracts = listOf(7, 14, 28).mapIndexed { index, days ->
            val expiryCal = Calendar.getInstance().apply {
                timeInMillis = today.timeInMillis
                add(Calendar.DAY_OF_YEAR, days)
            }
            val idx = index + 1
            val carry = 0.0012 * idx + 0.0004 * ((seed + idx) % 3)
            val last = round((spot * (1 + carry)) * 100.0) / 100.0
            val oiChange = ((seed * 31 + idx * 173) % 3200) - 1600
            val marginPct = min(0.22, 0.11 + (abs(oiChange) / 12_000.0) + (idx * 0.01))
            FuturesContract(
                contractSymbol = "${raw}-${formatExpiryLabel(expiryCal)}-FUT",
                expiry = formatIso(expiryCal),
                lotSize = lotSize,
                last = last,
                pctChange = idx * 0.08,
                oi = max(5_000, (seed * 97 + idx * 431) % 95_000 + 8_000),
                oiChange = oiChange,
                volume = max(500, (seed * 19 + idx * 257) % 18_000 + 2_500),
                basis = round((last - spot) * 100.0) / 100.0,
                marginPct = round(marginPct * 10_000.0) / 10_000.0,
                marginPerLot = round(last * lotSize * marginPct * 100.0) / 100.0,
            )
        }
        return FuturesContractsResponse(
            symbol = raw,
            spot = round(spot * 100.0) / 100.0,
            generatedAt = utcNow(),
            contracts = contracts,
            source = "synthetic",
            notes = listOf(
                "Teaching futures board — live exchange list unavailable.",
                "Contract metrics are indicative — validate against broker RMS before execution.",
                "Margin preview excludes span spikes and intraday leverage changes.",
                "Spot is illustrative because a live print was unavailable.",
            ),
        )
    }

    fun preview(
        request: FuturesTicketPreviewRequest,
        board: FuturesContractsResponse = build(request.symbol),
    ): FuturesTicketPreviewResponse {
        val selected = board.contracts.firstOrNull { it.expiry == request.expiry.trim() }
            ?: board.contracts.minByOrNull { abs(it.expiry.compareTo(request.expiry.trim())) }
            ?: build(request.symbol).contracts.first()
        val lots = request.lots.coerceAtLeast(1)
        val side = request.side.trim().uppercase().ifBlank { "BUY" }
        val reference = if (request.orderType.equals("LIMIT", ignoreCase = true) &&
            (request.limitPrice ?: 0.0) > 0.0
        ) {
            request.limitPrice!!
        } else {
            selected.last
        }
        val quantity = selected.lotSize * lots
        val notional = round(reference * quantity * 100.0) / 100.0
        val margin = round(selected.marginPerLot * lots * 100.0) / 100.0
        val charges = round(max(20.0, notional * 0.00018) * 100.0) / 100.0
        val buffer = round(margin * (if (side == "SELL") 0.85 else 0.75) * 100.0) / 100.0
        return FuturesTicketPreviewResponse(
            contractSymbol = selected.contractSymbol,
            symbol = board.symbol,
            expiry = selected.expiry,
            side = side,
            lots = lots,
            lotSize = selected.lotSize,
            quantity = quantity,
            referencePrice = round(reference * 100.0) / 100.0,
            notionalValue = notional,
            estimatedMargin = margin,
            estimatedCharges = charges,
            maxLossBuffer = buffer,
            notes = listOf(
                "Preview assumes normal volatility and current indicative margin percentages.",
                "Use broker confirmation before placing live futures orders.",
            ),
        )
    }

    private fun lotSizeFor(spot: Double): Int {
        if (spot <= 0.0) return 100
        val raw = kotlin.math.round(120_000.0 / spot).toInt()
        return max(10, ((raw + 4) / 5) * 5)
    }

    private fun formatIso(cal: Calendar): String {
        return String.format(
            Locale.US,
            "%04d-%02d-%02d",
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun formatExpiryLabel(cal: Calendar): String {
        val format = SimpleDateFormat("ddMMMyy", Locale.US)
        return format.format(cal.time).uppercase(Locale.US)
    }

    private fun utcNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
