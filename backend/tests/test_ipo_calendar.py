import sys
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.database.db import IPOModel
from app.ipo_calendar import (
    classify_issue_status,
    is_practice_demo,
    merge_nse_issues,
    parse_nse_date,
    parse_price_band,
    refresh_ipo_calendar,
)


TODAY = date(2026, 9, 27)


def test_parse_nse_dates_and_price_bands():
    assert parse_nse_date("25-Sep-2026") == "2026-09-25"
    assert parse_nse_date("24-SEP-2026") == "2026-09-24"
    assert parse_nse_date("-") is None
    assert parse_price_band("Rs.290 to Rs.302") == (290.0, 302.0)
    assert parse_price_band("Rs.1,250 to Rs.1,320") == (1250.0, 1320.0)
    assert parse_price_band("-") == (None, None)


def test_status_follows_the_issue_window():
    assert classify_issue_status("2026-09-25", "2026-09-29", TODAY) == "OPEN"
    assert classify_issue_status("2026-10-06", "2026-10-08", TODAY) == "UPCOMING"
    assert classify_issue_status("2026-09-22", "2026-09-24", TODAY) == "CLOSED"


def test_merge_prefers_current_issues_and_keeps_recent_past():
    current = [
        {
            "companyName": "Runwal Enterprises Limited",
            "symbol": "RUNWALENTR",
            "issueStartDate": "25-Sep-2026",
            "issueEndDate": "29-Sep-2026",
            "issuePrice": "Rs.290 to Rs.302",
            "status": "Active",
            "category": "Total",
        },
        {
            "companyName": "Runwal Retail",
            "symbol": "RUNWALENTR",
            "issueStartDate": "25-Sep-2026",
            "issueEndDate": "29-Sep-2026",
            "issuePrice": "Rs.1 to Rs.2",
            "category": "Retail",
        },
    ]
    upcoming = [
        {
            "companyName": "Runwal Enterprises Limited",
            "symbol": "RUNWALENTR",
            "issueStartDate": "25-Sep-2026",
            "issueEndDate": "29-Sep-2026",
            "issuePrice": "Rs.1 to Rs.2",
            "status": "Active",
        },
        {
            "companyName": "Future Foods Limited",
            "symbol": "FUTUREFOOD",
            "issueStartDate": "06-Oct-2026",
            "issueEndDate": "08-Oct-2026",
            "issuePrice": "Rs.90 to Rs.95",
            "status": "Forthcoming",
        },
    ]
    past = [
        {
            "company": "Varmora Granito Limited",
            "symbol": "VARMORA",
            "ipoStartDate": "22-SEP-2026",
            "ipoEndDate": "24-SEP-2026",
            "priceRange": "Rs.140 to Rs.148",
            "listingDate": "-",
            "securityType": "EQ",
        }
    ]
    rows = {row["symbol"]: row for row in merge_nse_issues(current, upcoming, past, TODAY)}
    assert rows["RUNWALENTR"]["status"] == "OPEN"
    assert rows["RUNWALENTR"]["price_band_min"] == 290.0
    assert rows["RUNWALENTR"]["ipo_id"] == "NSE-RUNWALENTR"
    assert rows["FUTUREFOOD"]["status"] == "UPCOMING"
    assert rows["VARMORA"]["status"] == "CLOSED"
    assert rows["VARMORA"]["listing_date"] is None
    assert is_practice_demo("IPO-DEMO-OPEN", "Acme Infra Limited (demo)")
    assert not is_practice_demo("NSE-RUNWALENTR", "Runwal Enterprises Limited")


def test_refresh_upserts_live_rows_and_keeps_last_list_when_fetch_fails(monkeypatch):
    engine = create_engine("sqlite:///:memory:")
    IPOModel.__table__.create(engine)
    db = sessionmaker(bind=engine)()
    db.add(
        IPOModel(
            ipo_id="NSE-OLD",
            company_name="Old Issue",
            symbol="OLD",
            status="OPEN",
            issue_open_date="2026-09-01",
            issue_close_date="2026-09-03",
            price_band_min=10,
            price_band_max=12,
            lot_size=1,
        )
    )
    db.commit()

    def _fake_fetch(today=None):
        return (
            [
                {
                    "companyName": "Runwal Enterprises Limited",
                    "symbol": "RUNWALENTR",
                    "issueStartDate": "25-Sep-2026",
                    "issueEndDate": "29-Sep-2026",
                    "issuePrice": "Rs.290 to Rs.302",
                    "category": "Total",
                }
            ],
            [],
            [],
        )

    monkeypatch.setattr("app.ipo_calendar.fetch_nse_issue_lists", _fake_fetch)
    monkeypatch.setattr("app.ipo_calendar._cache_at", 0.0)
    assert refresh_ipo_calendar(db, force=True, today=TODAY) is True
    saved = db.query(IPOModel).filter(IPOModel.ipo_id == "NSE-RUNWALENTR").one()
    assert saved.status == "OPEN"
    assert saved.price_band_max == 302.0
    old = db.query(IPOModel).filter(IPOModel.ipo_id == "NSE-OLD").one()
    assert old.status == "CLOSED"

    def _boom(today=None):
        raise RuntimeError("nse down")

    monkeypatch.setattr("app.ipo_calendar.fetch_nse_issue_lists", _boom)
    assert refresh_ipo_calendar(db, force=True, today=TODAY) is False
    assert db.query(IPOModel).filter(IPOModel.ipo_id == "NSE-RUNWALENTR").count() == 1
    db.close()
    import app.ipo_calendar as ipo_calendar

    ipo_calendar._cache_at = 0.0
