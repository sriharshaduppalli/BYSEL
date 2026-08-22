from app.quality_fundamentals import (
    parse_nse_quote_equity,
    parse_nse_shareholding,
    statements_from_yahoo_quote,
)
from app.market_scanner import evaluate_quality_screen


def _row(end_raw: int, **fields: float) -> dict:
    return {"endDate": {"raw": end_raw}, **{key: {"raw": value} for key, value in fields.items()}}


def test_yahoo_statements_compute_cagr_and_roce():
    payload = {
        "quoteSummary": {
            "result": [
                {
                    "incomeStatementHistory": {
                        "incomeStatementHistory": [
                            _row(1743379200, totalRevenue=170, netIncome=20, ebit=34),
                            _row(1711843200, totalRevenue=140, netIncome=15, ebit=28),
                            _row(1680220800, totalRevenue=120, netIncome=12, ebit=24),
                            _row(1648684800, totalRevenue=100, netIncome=10, ebit=20),
                        ]
                    },
                    "balanceSheetHistory": {
                        "balanceSheetHistory": [
                            _row(1743379200, totalStockholderEquity=80, totalDebt=15, cash=5),
                            _row(1711843200, totalStockholderEquity=80, totalDebt=15, cash=5),
                            _row(1680220800, totalStockholderEquity=80, totalDebt=15, cash=5),
                            _row(1648684800, totalStockholderEquity=80, totalDebt=15, cash=5),
                        ]
                    },
                }
            ]
        }
    }
    out = statements_from_yahoo_quote(payload)
    assert out["statementsChecked"] == 1
    assert out["salesCagrYears"] == 3
    assert 18 <= out["salesCagr"] <= 21
    assert out["profitCagrYears"] == 3
    assert out["profitCagr"] > 15
    assert out["roce"] > 15
    assert out["roceAvgYears"] == 4
    assert out["roceAvg"] > 15


def test_yahoo_statements_skip_short_history():
    payload = {
        "incomeStatementHistory": {
            "incomeStatementHistory": [
                _row(1743379200, totalRevenue=170, netIncome=20, ebit=34),
                _row(1711843200, totalRevenue=140, netIncome=15, ebit=28),
            ]
        },
        "balanceSheetHistory": {"balanceSheetHistory": []},
    }
    out = statements_from_yahoo_quote(payload)
    assert out["statementsChecked"] == 1
    assert "salesCagr" not in out
    assert "roce" not in out


def test_nse_shareholding_and_sector_pe_parse():
    holding = parse_nse_shareholding(
        {
            "data": [
                {
                    "asOnDate": "31-Dec-2025",
                    "promoterAndPromoterGroup": 62.4,
                    "pledgedShares": 0.35,
                }
            ]
        }
    )
    assert holding["promoter"] == 62.4
    assert holding["pledge"] == 0.35
    sector = parse_nse_quote_equity({"metadata": {"pdSectorPe": 27.8}})
    assert sector["nseSectorPe"] == 27.8


def test_quality_screen_applies_statement_and_nse_fields():
    result = evaluate_quality_screen(
        {
            "marketCap": 12_000_000_000,
            "peg": 0.8,
            "pe": 16.0,
            "nseSectorPe": 22.0,
            "roe": 24.0,
            "salesCagr": 18.0,
            "salesCagrYears": 3,
            "profitCagr": 21.0,
            "profitCagrYears": 5,
            "roceAvg": 19.0,
            "roceAvgYears": 5,
            "promoter": 58.0,
            "pledge": 0.2,
            "marginPct": 19.0,
            "priceToSales": 3.5,
            "evEbitda": 11.0,
        },
        sector_pe=40.0,
    )
    by_id = {item["id"]: item for item in result["checks"]}
    assert by_id["pe_vs_sector"]["status"] == "pass"
    assert "NSE" in by_id["pe_vs_sector"]["note"]
    assert "Yahoo" not in by_id["sales"]["note"]
    assert "Yahoo" not in by_id["profit"]["note"]
    assert "sales CAGR" in by_id["sales"]["note"] or "TTM" in by_id["sales"]["note"]
    assert "profit CAGR" in by_id["profit"]["note"] or "TTM" in by_id["profit"]["note"]
    assert by_id["roce"]["status"] == "pass"
    assert by_id["promoter"]["status"] == "pass"
    assert by_id["pledge"]["status"] == "pass"
    assert result["matches"] is True
    assert result["failed"] == 0
