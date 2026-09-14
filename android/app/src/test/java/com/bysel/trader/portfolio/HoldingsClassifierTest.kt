package com.bysel.trader.portfolio

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldingsClassifierTest {

    @Test
    fun listedStockIsEquity() {
        assertEquals(
            HoldingAssetClass.EQUITY,
            HoldingsClassifier.classify("RELIANCE", "Reliance Industries"),
        )
        assertEquals(
            HoldingAssetClass.EQUITY,
            HoldingsClassifier.classify("GOLDIAM", "Goldiam International"),
        )
    }

    @Test
    fun beesAndCatalogTickersAreEtfs() {
        assertEquals(HoldingAssetClass.ETF, HoldingsClassifier.classify("NIFTYBEES"))
        assertEquals(HoldingAssetClass.ETF, HoldingsClassifier.classify("GOLDBEES"))
        assertEquals(
            HoldingAssetClass.ETF,
            HoldingsClassifier.classify("MON100", etfSymbols = setOf("MON100")),
        )
        assertEquals(
            HoldingAssetClass.ETF,
            HoldingsClassifier.classify("SETFNIF50", "Nippon India ETF Nifty 50"),
        )
    }

    @Test
    fun infIsinIsMutualFund() {
        assertEquals(
            HoldingAssetClass.MUTUAL_FUND,
            HoldingsClassifier.classify(
                symbol = "INF090I01239",
                name = "HDFC Flexi Cap Direct Growth",
                isin = "INF090I01239",
            ),
        )
        assertEquals(
            HoldingAssetClass.MUTUAL_FUND,
            HoldingsClassifier.classify("AXISBLUECHIP", "Axis Bluechip Fund Growth Direct"),
        )
    }

    @Test
    fun optionExpiryIsFno() {
        assertEquals(
            HoldingAssetClass.FNO,
            HoldingsClassifier.classify("NIFTY24SEPCE25000"),
        )
    }
}
