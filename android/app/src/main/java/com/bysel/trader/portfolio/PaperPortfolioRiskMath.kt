package com.bysel.trader.portfolio

import com.bysel.trader.data.importbook.ImportedBook
import com.bysel.trader.data.importbook.ImportedHolding
import com.bysel.trader.data.models.Holding
import com.bysel.trader.data.models.PaperByselScoreBlend
import com.bysel.trader.data.models.PaperConcentration
import com.bysel.trader.data.models.PaperHistoryGap
import com.bysel.trader.data.models.PaperPortfolioRisk
import com.bysel.trader.data.models.PaperSectorSpread
import com.bysel.trader.data.models.PaperSectorWeight
import com.bysel.trader.data.models.PaperWhatIf
import com.bysel.trader.data.models.PortfolioHealthScore
import com.bysel.trader.data.models.Quote
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Phase 1.3 paper risk math. Mirrors backend `portfolio_risk.py`.
 * No fake 1Y drawdown, realized vol, or correlation matrix.
 */
object PaperPortfolioRiskMath {

    const val WHAT_IF_LABEL =
        "Illustration, not a forecast. Conservative beta = 1 on equity value " +
            "(cash ignored). Actual names move differently from Nifty."
    const val HISTORY_NOTE =
        "Needs more history — volatility and 1Y max drawdown are omitted until we have real series."
    const val DISCLAIMER =
        "Educational paper metrics — not investment advice and not a forecast. " +
            "BYSEL Score is value-weighted from names that already have a score; missing names are skipped."
    const val IMPORT_NOTE =
        "Import a broker CSV or CAS extract on Portfolio — read-only, live marks when the session is open."
    const val CONCENTRATION_HINT = "Higher = more of the book in one name"
    const val SECTOR_SPREAD_HINT = "Higher = more spread across sectors (1 − HHI)"

    data class Position(
        val symbol: String,
        val qty: Int,
        val last: Double,
        val value: Double,
        val invested: Double,
        val dayPnl: Double,
        val dayPnlAvailable: Boolean,
        val sector: String,
        val score: Double?,
        val weightPct: Double = 0.0,
    )

    fun holdingMark(holding: Holding, quote: Quote?): Double {
        val live = quote?.last ?: 0.0
        if (live > 0.0) return live
        if (holding.last > 0.0) return holding.last
        return max(holding.avgPrice, 0.0)
    }

    fun dayPnlRupees(
        qty: Int,
        last: Double,
        pctChange: Double? = null,
        prevClose: Double? = null,
    ): Pair<Double, Boolean> {
        if (qty <= 0 || last <= 0.0) return 0.0 to false
        val close = prevClose ?: 0.0
        if (close > 0.0) return round2(qty * (last - close)) to true
        if (pctChange == null) return 0.0 to false
        val denom = 1.0 + (pctChange / 100.0)
        if (denom <= 0.0) return 0.0 to false
        val prev = last / denom
        return round2(qty * (last - prev)) to true
    }

    fun positionWeights(values: List<Double>): List<Double> {
        val total = values.sumOf { max(it, 0.0) }
        if (total <= 0.0) return List(values.size) { 0.0 }
        return values.map { max(it, 0.0) / total * 100.0 }
    }

    fun concentrationFromWeights(
        weightsPct: List<Double>,
        symbols: List<String>,
    ): PaperConcentration {
        val paired = weightsPct.zip(symbols)
            .map { it.first to it.second.uppercase() }
            .sortedWith(compareByDescending<Pair<Double, String>> { it.first }.thenBy { it.second })
        val top1 = paired.firstOrNull()?.first ?: 0.0
        val top1Symbol = paired.firstOrNull()?.second.orEmpty()
        val top5 = paired.take(5).sumOf { it.first }
        return PaperConcentration(
            top1Pct = round2(top1),
            top1Symbol = top1Symbol,
            top5Pct = round2(min(top5, 100.0)),
            gauge = min(100.0, max(top1, 0.0)).roundToInt(),
            gaugeLabel = "Largest name as % of book",
            gaugeHint = CONCENTRATION_HINT,
        )
    }

    fun sectorMix(weightsPct: List<Double>, sectors: List<String>): List<PaperSectorWeight> {
        val buckets = linkedMapOf<String, Double>()
        weightsPct.zip(sectors).forEach { (weight, sector) ->
            val name = sector.ifBlank { "Other" }
            buckets[name] = (buckets[name] ?: 0.0) + max(weight, 0.0)
        }
        return buckets.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .filter { it.value > 0.0 }
            .map { PaperSectorWeight(name = it.key, weightPct = round2(it.value)) }
    }

    fun herfindahlHhi(weightsPct: List<Double>): Double {
        val total = weightsPct.sumOf { max(it, 0.0) }
        if (total <= 0.0) return 0.0
        return weightsPct.sumOf { val w = max(it, 0.0) / total; w * w }
    }

    fun sectorSpreadFromMix(sectors: List<PaperSectorWeight>): PaperSectorSpread {
        val weights = sectors.map { it.weightPct }
        val hhi = herfindahlHhi(weights)
        val gauge = min(100.0, max((1.0 - hhi) * 100.0, 0.0)).roundToInt()
        return PaperSectorSpread(
            sectorCount = weights.count { it > 0.0 },
            hhi = (hhi * 10_000.0).roundToInt() / 10_000.0,
            gauge = gauge,
            gaugeLabel = "Sector spread",
            gaugeHint = SECTOR_SPREAD_HINT,
        )
    }

    fun niftyWhatIf(equityValue: Double, shockPct: Double, beta: Double = 1.0): Double {
        if (equityValue <= 0.0) return 0.0
        return round2(equityValue * (shockPct / 100.0) * beta)
    }

    fun valueWeightedScore(values: List<Double>, scores: List<Double?>): PaperByselScoreBlend {
        var covered = 0.0
        var weighted = 0.0
        var scoredCount = 0
        var missingCount = 0
        val total = values.sumOf { max(it, 0.0) }
        values.zip(scores).forEach { (rawValue, score) ->
            val value = max(rawValue, 0.0)
            if (score == null) {
                if (value > 0.0) missingCount += 1
                return@forEach
            }
            covered += value
            weighted += value * score
            scoredCount += 1
        }
        if (covered <= 0.0 || scoredCount == 0) {
            return PaperByselScoreBlend(
                valueWeighted = null,
                scoredCount = 0,
                missingCount = missingCount,
                coveredValuePct = 0.0,
                note = "No BYSEL Score on these holdings yet — skipped rather than guessed.",
            )
        }
        return PaperByselScoreBlend(
            valueWeighted = (weighted / covered).roundToInt(),
            scoredCount = scoredCount,
            missingCount = missingCount,
            coveredValuePct = if (total > 0.0) round2(covered / total * 100.0) else 0.0,
            note = "Value-weighted from holdings that already have a BYSEL Score. Missing names skipped.",
        )
    }

    fun empty(message: String = "No paper holdings yet. Risk gauges appear after your first practice buy."): PaperPortfolioRisk {
        return PaperPortfolioRisk(
            empty = true,
            disclaimer = DISCLAIMER,
            importNote = IMPORT_NOTE,
            message = message,
            volatility = PaperHistoryGap(available = false, note = HISTORY_NOTE),
            maxDrawdown = PaperHistoryGap(available = false, note = HISTORY_NOTE),
            whatIf = PaperWhatIf(label = WHAT_IF_LABEL),
            concentration = PaperConcentration(gaugeHint = CONCENTRATION_HINT),
            sectorSpread = PaperSectorSpread(gaugeHint = SECTOR_SPREAD_HINT),
        )
    }

    fun fromHoldings(
        holdings: List<Holding>,
        quotes: List<Quote>,
        scores: Map<String, Int> = emptyMap(),
        health: PortfolioHealthScore? = null,
        importNote: String = IMPORT_NOTE,
    ): PaperPortfolioRisk {
        val quoteBy = quotes.associateBy { it.symbol.uppercase() }
        val scoreBy = scores.mapKeys { it.key.uppercase() }
        val healthSectors = sectorLookupFromHealth(health)
        val rows = holdings.mapNotNull { holding ->
            val symbol = holding.symbol.trim().uppercase()
            if (symbol.isEmpty() || holding.qty <= 0) return@mapNotNull null
            val quote = quoteBy[symbol]
            val last = holdingMark(holding, quote)
            val value = last * holding.qty
            val invested = if (holding.avgPrice > 0.0) holding.avgPrice * holding.qty else 0.0
            val (dayPnl, dayOk) = dayPnlRupees(
                qty = holding.qty,
                last = last,
                pctChange = quote?.pctChange,
                prevClose = quote?.prevClose?.takeIf { it > 0.0 },
            )
            Position(
                symbol = symbol,
                qty = holding.qty,
                last = last,
                value = value,
                invested = invested,
                dayPnl = dayPnl,
                dayPnlAvailable = dayOk && quote != null,
                sector = healthSectors[symbol] ?: HOLDING_SECTOR_MAP[symbol] ?: "Other",
                score = scoreBy[symbol]?.toDouble(),
            )
        }
        if (rows.isEmpty()) return empty()

        val values = rows.map { it.value }
        val totalValue = values.sum()
        val costRows = rows.filter { it.invested > 0.0 }
        val totalInvested = costRows.sumOf { it.invested }
        val weights = positionWeights(values)
        val weightedRows = rows.mapIndexed { index, row -> row.copy(weightPct = round2(weights[index])) }
        val dayAvailable = weightedRows.any { it.dayPnlAvailable }
        val dayPnl = if (dayAvailable) round2(weightedRows.filter { it.dayPnlAvailable }.sumOf { it.dayPnl }) else 0.0
        val dayPct = if (dayAvailable && totalValue > 0.0) round2(dayPnl / totalValue * 100.0) else 0.0
        val healthMix = sectorWeightsFromHealth(health)
        val sectors = healthMix.ifEmpty {
            sectorMix(weights, weightedRows.map { it.sector })
        }
        return PaperPortfolioRisk(
            empty = false,
            totalValue = round2(totalValue),
            totalInvested = round2(totalInvested),
            totalPnl = if (costRows.isNotEmpty()) {
                round2(costRows.sumOf { it.value } - totalInvested)
            } else {
                0.0
            },
            dayPnl = dayPnl,
            dayPnlPercent = dayPct,
            dayPnlAvailable = dayAvailable,
            byselScore = valueWeightedScore(values, weightedRows.map { it.score }),
            concentration = concentrationFromWeights(weights, weightedRows.map { it.symbol }),
            sectors = sectors,
            sectorSpread = sectorSpreadFromMix(sectors),
            whatIf = PaperWhatIf(
                beta = 1.0,
                equityValue = round2(totalValue),
                niftyDown5 = niftyWhatIf(totalValue, -5.0),
                niftyDown10 = niftyWhatIf(totalValue, -10.0),
                label = WHAT_IF_LABEL,
            ),
            volatility = PaperHistoryGap(available = false, note = HISTORY_NOTE),
            maxDrawdown = PaperHistoryGap(available = false, note = HISTORY_NOTE),
            disclaimer = DISCLAIMER,
            importNote = importNote,
            holdingsCount = weightedRows.size,
        )
    }

    fun importedAsHoldings(rows: List<ImportedHolding>, quotes: List<Quote>): List<Holding> {
        val quoteBy = quotes.associateBy { it.symbol.uppercase() }
        return rows.mapNotNull { row ->
            val symbol = row.symbol.trim().uppercase()
            if (symbol.isEmpty() || row.qty <= 0) return@mapNotNull null
            val quote = quoteBy[symbol]
            val last = when {
                quote != null && quote.last > 0.0 -> quote.last
                row.lastMark > 0.0 -> row.lastMark
                else -> row.avgPrice
            }
            Holding(
                symbol = symbol,
                qty = row.qty,
                avgPrice = row.avgPrice,
                last = last,
                pnl = if (row.avgPrice > 0.0) (last - row.avgPrice) * row.qty else 0.0,
            )
        }
    }

    fun mergePaperAndImported(
        paper: List<Holding>,
        imported: List<Holding>,
    ): Pair<List<Holding>, Int> {
        val paperKeys = paper.map { it.symbol.trim().uppercase() }.toSet()
        val extra = imported.filter { it.symbol.trim().uppercase() !in paperKeys }
        return (paper + extra) to (imported.size - extra.size)
    }

    fun importNoteFor(book: ImportedBook?, overlapIgnored: Int = 0): String {
        if (book == null || book.rows.isEmpty()) return IMPORT_NOTE
        val overlap = if (overlapIgnored > 0) {
            " $overlapIgnored imported name(s) already in paper were not double-counted."
        } else {
            ""
        }
        return "${book.sourceLabel}: ${book.rows.size} names, read-only. " +
            "Marks use live quotes when NSE is open. Not mixed into the paper wallet.$overlap"
    }

    fun preferRemote(local: PaperPortfolioRisk, remote: PaperPortfolioRisk?): PaperPortfolioRisk {
        if (remote == null) return local
        if (local.empty) return remote
        if (remote.empty) return local
        val remoteScore = remote.byselScore.valueWeighted
        val blendedScore = if (remoteScore != null) remote.byselScore else local.byselScore
        val sectors = if (remote.sectors.isNotEmpty()) remote.sectors else local.sectors
        return remote.copy(
            byselScore = blendedScore,
            sectors = sectors,
            sectorSpread = if (remote.sectors.isNotEmpty()) remote.sectorSpread else local.sectorSpread,
            dayPnl = if (remote.dayPnlAvailable) remote.dayPnl else local.dayPnl,
            dayPnlPercent = if (remote.dayPnlAvailable) remote.dayPnlPercent else local.dayPnlPercent,
            dayPnlAvailable = remote.dayPnlAvailable || local.dayPnlAvailable,
            importNote = local.importNote.ifBlank { remote.importNote },
        )
    }

    fun sectorWeightsFromHealth(health: PortfolioHealthScore?): List<PaperSectorWeight> {
        val alloc = health?.sectorAllocation ?: return emptyList()
        return alloc.mapNotNull { (name, payload) ->
            val weight = numberFrom(payload["weight"]) ?: return@mapNotNull null
            if (weight <= 0.0) null else PaperSectorWeight(name = name, weightPct = round2(weight))
        }.sortedWith(compareByDescending<PaperSectorWeight> { it.weightPct }.thenBy { it.name })
    }

    private fun sectorLookupFromHealth(health: PortfolioHealthScore?): Map<String, String> {
        val alloc = health?.sectorAllocation ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        alloc.forEach { (sector, payload) ->
            val stocks = payload["stocks"] as? List<*> ?: return@forEach
            stocks.forEach { symbol ->
                val key = symbol?.toString()?.trim()?.uppercase().orEmpty()
                if (key.isNotEmpty()) out[key] = sector
            }
        }
        return out
    }

    private fun numberFrom(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    /** Same map as backend `portfolio_scorer.SECTOR_MAP` — unmapped names are Other. */
    val HOLDING_SECTOR_MAP: Map<String, String> = mapOf(
        "HDFCBANK" to "Banking", "ICICIBANK" to "Banking", "SBIN" to "Banking",
        "KOTAKBANK" to "Banking", "AXISBANK" to "Banking", "INDUSINDBK" to "Banking",
        "PNB" to "Banking", "BANKBARODA" to "Banking", "CANBK" to "Banking",
        "IDFCFIRSTB" to "Banking", "FEDERALBNK" to "Banking", "BANDHANBNK" to "Banking",
        "AUBANK" to "Banking", "RBLBANK" to "Banking", "YESBANK" to "Banking",
        "BAJFINANCE" to "NBFC", "BAJAJFINSV" to "NBFC", "HDFCLIFE" to "Insurance",
        "SBILIFE" to "Insurance", "ICICIPRULI" to "Insurance", "ICICIGI" to "Insurance",
        "MUTHOOTFIN" to "NBFC", "CHOLAFIN" to "NBFC", "MANAPPURAM" to "NBFC",
        "LICHSGFIN" to "NBFC", "PEL" to "NBFC", "SHRIRAMFIN" to "NBFC",
        "TCS" to "IT", "INFY" to "IT", "WIPRO" to "IT", "HCLTECH" to "IT",
        "TECHM" to "IT", "LTIM" to "IT", "MPHASIS" to "IT", "COFORGE" to "IT",
        "PERSISTENT" to "IT", "LTTS" to "IT", "HAPPSTMNDS" to "IT",
        "MOSCHIP" to "Semiconductor", "KAYNES" to "Semiconductor", "SYRMA" to "Semiconductor",
        "DIXON" to "Semiconductor", "AVALON" to "Semiconductor", "CYIENTDLM" to "Semiconductor",
        "CGPOWER" to "Semiconductor", "TATAELXSI" to "Semiconductor", "CYIENT" to "Semiconductor",
        "RIR" to "Semiconductor", "PGEL" to "Semiconductor", "CENTUM" to "Semiconductor",
        "SPELS" to "Semiconductor",
        "SUNPHARMA" to "Pharma", "DRREDDY" to "Pharma", "CIPLA" to "Pharma",
        "DIVISLAB" to "Pharma", "LUPIN" to "Pharma", "AUROPHARMA" to "Pharma",
        "BIOCON" to "Pharma", "TORNTPHARM" to "Pharma", "ALKEM" to "Pharma",
        "IPCALAB" to "Pharma", "LAURUSLABS" to "Pharma", "GLENMARK" to "Pharma",
        "APOLLOHOSP" to "Healthcare", "MAXHEALTH" to "Healthcare", "FORTIS" to "Healthcare",
        "TMPV" to "Auto", "TMCV" to "Auto", "MARUTI" to "Auto", "BAJAJ-AUTO" to "Auto",
        "HEROMOTOCO" to "Auto", "EICHERMOT" to "Auto", "TVSMOTOR" to "Auto",
        "ASHOKLEY" to "Auto", "MOTHERSON" to "Auto", "BHARATFORG" to "Auto",
        "MRF" to "Auto", "BALKRISIND" to "Auto", "BOSCHLTD" to "Auto",
        "RELIANCE" to "Energy", "ONGC" to "Energy", "BPCL" to "Energy",
        "IOC" to "Energy", "NTPC" to "Power", "POWERGRID" to "Power",
        "TATAPOWER" to "Power", "ADANIGREEN" to "Power", "ADANIENT" to "Energy",
        "GAIL" to "Energy", "PETRONET" to "Energy", "COALINDIA" to "Mining",
        "VEDL" to "Mining", "NMDC" to "Mining", "HINDPETRO" to "Energy",
        "TATASTEEL" to "Metals", "JSWSTEEL" to "Metals", "HINDALCO" to "Metals",
        "SAIL" to "Metals", "NATIONALUM" to "Metals", "JINDALSTEL" to "Metals",
        "APLAPOLLO" to "Metals",
        "HINDUNILVR" to "FMCG", "ITC" to "FMCG", "NESTLEIND" to "FMCG",
        "BRITANNIA" to "FMCG", "DABUR" to "FMCG", "MARICO" to "FMCG",
        "COLPAL" to "FMCG", "GODREJCP" to "FMCG", "TATACONSUM" to "FMCG",
        "VBL" to "FMCG", "UBL" to "FMCG", "RADICO" to "FMCG",
        "LT" to "Infra", "ADANIPORTS" to "Infra", "IRCON" to "Infra",
        "RVNL" to "Infra", "NBCC" to "Infra", "NCC" to "Infra",
        "KEC" to "Infra", "ULTRACEMCO" to "Cement", "AMBUJACEM" to "Cement",
        "SHREECEM" to "Cement", "DALMIACEM" to "Cement", "ACC" to "Cement",
        "DLF" to "Real Estate", "GODREJPROP" to "Real Estate",
        "OBEROIRLTY" to "Real Estate", "PRESTIGE" to "Real Estate",
        "BRIGADE" to "Real Estate", "LODHA" to "Real Estate", "SOBHA" to "Real Estate",
        "HAL" to "Defence", "BEL" to "Defence", "BDL" to "Defence",
        "MAZDOCK" to "Defence", "COCHINSHIP" to "Defence",
        "BHARTIARTL" to "Telecom", "IDEA" to "Telecom",
        "TITAN" to "Consumer", "TRENT" to "Consumer", "HAVELLS" to "Consumer",
        "VOLTAS" to "Consumer", "CROMPTON" to "Consumer", "BLUESTARLT" to "Consumer",
        "BATAINDIA" to "Consumer", "PAGEIND" to "Consumer",
        "PIDILITIND" to "Chemicals", "ASIANPAINT" to "Chemicals",
        "BERGERPAINTS" to "Chemicals", "SRF" to "Chemicals",
        "AARTI" to "Chemicals", "DEEPAKNTR" to "Chemicals",
        "NAVINFLUOR" to "Chemicals", "CLEAN" to "Chemicals",
    )
}
