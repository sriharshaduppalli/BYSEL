"""Desk figures for Indian Stock Model chat.

Numbers come from a chain already on screen, an exchange chain fetch, the
paper journal, the NAV catalog, or the IPO list. Missing figures stay missing.
"""
from __future__ import annotations

import re
from typing import Any, Mapping

_CHAIN_ASK = re.compile(
    r"\b(open interest|\boi\b|pcr|straddle|max pain|option chain|call oi|put oi|"
    r"this chain|oi buildup|oi build-up|put call ratio)\b",
    re.I,
)
_JOURNAL_ASK = re.compile(
    r"\b((?:my|show|meri|mera)\s+(?:study note|notes|journal)|"
    r"what did i (?:write|note|study)|paper journal)\b",
    re.I,
)
_FUND_ASK = re.compile(
    r"\b(etf expense|expense ratio|nav of|which fund|scheme name|scheme nav)\b",
    re.I,
)
_DEFINITION_ASK = re.compile(
    r"\b(what is|what are|explain|define|definition|meaning of|difference between|how does|how do)\b",
    re.I,
)
_NAMED_MARKET = re.compile(
    r"\b(nifty|bank\s*nifty|sensex|finnifty|midcap)\b",
    re.I,
)
_IPO_ASK = re.compile(
    r"\b(which ipos?|open ipos?|ipos? list|list of ipos?|upcoming ipos?|"
    r"current ipos?|show ipos?|any ipos?|ipo calendar|grey[- ]market|\bgmp\b|price band)\b",
    re.I,
)
_EARNINGS_ASK = re.compile(
    r"\b(result date|earnings date|quarterly result|when is the result|results date)\b",
    re.I,
)
_LEVEL_ASK = re.compile(
    r"\b(this line|drawn line|h-line|horizontal line|my level)\b",
    re.I,
)
_FUND_STOP = frozenset(
    {
        "what", "which", "fund", "funds", "mutual", "nav", "the", "for", "and",
        "show", "tell", "about", "expense", "ratio", "scheme", "name", "etf",
    }
)


def detect_desk_focus(query: str) -> str:
    """Which desk should answer. A bare definition stays on the glossary."""
    text = query or ""
    definition = bool(_DEFINITION_ASK.search(text))
    named = bool(_NAMED_MARKET.search(text)) or bool(re.search(r"\b[A-Z][A-Z0-9]{1,11}\b", text))
    if _CHAIN_ASK.search(text) and (not definition or named):
        return "chain"
    if _JOURNAL_ASK.search(text):
        return "journal"
    if _FUND_ASK.search(text) and not definition:
        return "funds"
    if _IPO_ASK.search(text) and not definition:
        return "ipo"
    if _EARNINGS_ASK.search(text):
        return "earnings"
    if _LEVEL_ASK.search(text):
        return "level"
    return ""


def chain_max_pain(contracts: list[Mapping[str, Any]]) -> float | None:
    """Strike that minimizes writer pain from call and put open interest."""
    usable = [
        row for row in contracts
        if int(row.get("callOi") or 0) or int(row.get("putOi") or 0)
    ]
    if not usable:
        return None

    def pain(candidate: Mapping[str, Any]) -> float:
        strike = float(candidate.get("strike") or 0.0)
        total = 0.0
        for row in usable:
            other = float(row.get("strike") or 0.0)
            total += int(row.get("callOi") or 0) * max(0.0, strike - other)
            total += int(row.get("putOi") or 0) * max(0.0, other - strike)
        return total

    return float(min(usable, key=pain).get("strike") or 0.0)


def atm_straddle(contracts: list[Mapping[str, Any]], spot: float) -> dict[str, float] | None:
    if not contracts or spot <= 0:
        return None
    row = min(contracts, key=lambda item: abs(float(item.get("strike") or 0.0) - spot))
    call = float(row.get("callLtp") or 0.0)
    put = float(row.get("putLtp") or 0.0)
    if call <= 0 and put <= 0:
        return None
    return {
        "strike": float(row.get("strike") or 0.0),
        "call": call,
        "put": put,
        "cost": call + put,
    }


def _same_underlying(left: str, right: str) -> bool:
    a = (left or "").strip().upper()
    b = (right or "").strip().upper()
    if not a or not b:
        return False
    nifty = {"NIFTY", "NIFTY50"}
    if a in nifty and b in nifty:
        return True
    return a == b


def _num_text(value: Any) -> str:
    if value in (None, ""):
        return ""
    try:
        number = float(value)
    except (TypeError, ValueError):
        return str(value).strip()
    if number.is_integer():
        return str(int(number))
    return f"{number:.2f}".rstrip("0").rstrip(".")


def _chain_from_screen(screen: Mapping[str, Any], symbol: str) -> dict[str, Any] | None:
    opt = str(screen.get("option_symbol") or "").strip().upper()
    if not opt and not screen.get("option_expiry"):
        return None
    if symbol and opt and not _same_underlying(opt, symbol):
        return None
    source = str(screen.get("option_source") or "").strip().lower() or "unknown"
    # A teaching chain is not the board. Leave it so the exchange fetch can run.
    if source in {"synthetic", "teaching", "demo", "illustrative"}:
        return None
    return {
        "symbol": opt or symbol,
        "expiry": str(screen.get("option_expiry") or ""),
        "source": source,
        "origin": "screen",
        "pcr": screen.get("option_pcr"),
        "max_pain": screen.get("option_max_pain"),
        "straddle": screen.get("option_straddle"),
        "atm": screen.get("option_atm"),
        "spot": screen.get("option_spot"),
        "loaded": True,
    }


def _fetch_exchange_chain(symbol: str, expiry: str) -> dict[str, Any] | None:
    from .derivatives_data import compute_chain_metrics, fetch_nse_option_chain

    live = fetch_nse_option_chain(symbol, expiry)
    if not live or not live.get("contracts"):
        return None
    contracts = list(live.get("contracts") or [])
    spot = float(live.get("spot") or 0.0)
    metrics = compute_chain_metrics(contracts, spot)
    straddle = atm_straddle(contracts, spot)
    pain = chain_max_pain(contracts)
    return {
        "symbol": symbol,
        "expiry": str(live.get("expiry") or expiry),
        "source": "nse",
        "pcr": metrics.get("pcr"),
        "max_pain": pain,
        "straddle": None if not straddle else f"{straddle['cost']:.2f}",
        "atm": None if not straddle else f"{straddle['strike']:.2f}",
        "spot": spot or None,
        "loaded": True,
    }


def _journal_rows(user_id: int) -> list[dict[str, str]]:
    from .database.db import SessionLocal, TradeJournalModel

    db = SessionLocal()
    try:
        rows = (
            db.query(TradeJournalModel)
            .filter(TradeJournalModel.user_id == int(user_id))
            .order_by(TradeJournalModel.created_at.desc())
            .limit(5)
            .all()
        )
    finally:
        db.close()
    out: list[dict[str, str]] = []
    for row in rows:
        out.append(
            {
                "symbol": str(row.symbol or ""),
                "side": str(row.side or ""),
                "note": str(row.user_note or ""),
            }
        )
    return out


def _fund_rows(query: str) -> list[dict[str, str]]:
    from .routes import _fetch_live_mutual_funds

    tokens = [
        token for token in re.findall(r"[a-z0-9]{3,}", (query or "").lower())
        if token not in _FUND_STOP
    ]
    if not tokens:
        return []
    funds = _fetch_live_mutual_funds()
    matched = []
    for fund in funds:
        name = (fund.schemeName or "").lower()
        if any(token in name for token in tokens[:3]):
            matched.append(fund)
        if len(matched) >= 3:
            break
    return [
        {
            "name": item.schemeName,
            "nav": _num_text(item.nav),
            "nav_date": item.navDate or "",
            "house": item.fundHouse or "",
        }
        for item in matched
    ]


def _ipo_rows() -> list[dict[str, str]]:
    from .database.db import IPOModel, SessionLocal
    from .ipo_calendar import is_practice_demo

    db = SessionLocal()
    try:
        rows = db.query(IPOModel).order_by(IPOModel.issue_open_date.asc()).all()
    finally:
        db.close()
    listed = [
        row for row in rows
        if not is_practice_demo(row.ipo_id, row.company_name)
    ]
    listed.sort(key=lambda row: (0 if str(row.status or "").upper() == "OPEN" else 1, row.issue_open_date or ""))
    out: list[dict[str, str]] = []
    for row in listed[:5]:
        band = ""
        if row.price_band_min or row.price_band_max:
            band = f"{_num_text(row.price_band_min)}–{_num_text(row.price_band_max)}"
        out.append(
            {
                "name": str(row.company_name or ""),
                "status": str(row.status or ""),
                "open": str(row.issue_open_date or ""),
                "close": str(row.issue_close_date or ""),
                "band": band,
                "lot": _num_text(row.lot_size),
            }
        )
    return out


def _earnings_row(symbol: str, screen: Mapping[str, Any]) -> dict[str, Any] | None:
    date = str(screen.get("earnings_date") or "").strip()
    chart_symbol = str(screen.get("chart_symbol") or screen.get("symbol") or "").upper()
    if date and (not symbol or not chart_symbol or _same_underlying(chart_symbol, symbol)):
        return {
            "symbol": chart_symbol or symbol,
            "date": date[:10],
            "illustrative": str(screen.get("earnings_illustrative") or "") in {"1", "true", "True"},
        }
    if not symbol:
        return None
    from .routes.ai_v2 import _fetch_one_earnings_row

    row = _fetch_one_earnings_row(symbol)
    found = str((row or {}).get("nextEarningsDate") or "").strip()
    if not found:
        return None
    return {
        "symbol": symbol,
        "date": found[:10],
        "illustrative": bool((row or {}).get("estimated")),
    }


def build_chat_desk(
    query: str,
    profile: str = "",
    symbol: str = "",
    screen: Mapping[str, Any] | None = None,
    user_id: int | None = None,
) -> dict[str, Any]:
    """Assemble only the desk the question asked for. Other keys stay empty."""
    focus = detect_desk_focus(query)
    screen = screen or {}
    screen_symbol = str(screen.get("symbol") or screen.get("chart_symbol") or "").strip().upper()
    deictic = bool(re.search(r"\b(this|that|current|open)\s+(chain|candle|line|level|chart)\b", query or "", re.I))
    if deictic and screen_symbol:
        symbol = screen_symbol
    else:
        symbol = (symbol or screen_symbol).strip().upper()
    if not symbol:
        low = (query or "").lower()
        if re.search(r"bank\s*nifty", low):
            symbol = "BANKNIFTY"
        elif "sensex" in low:
            symbol = "SENSEX"
        elif "nifty" in low:
            symbol = "NIFTY"
    desk: dict[str, Any] = {
        "focus": focus,
        "timeframe": str(screen.get("timeframe") or ""),
        "level": str(screen.get("chart_level") or ""),
    }
    if focus == "chain":
        packed = _chain_from_screen(screen, symbol)
        if packed is None:
            expiry = str(screen.get("option_expiry") or "")
            target = symbol or str(screen.get("option_symbol") or "")
            if target and not expiry:
                from .fo_contracts import expiry_note, next_expiry_for

                expiry = next_expiry_for(target).isoformat()
                packed_note = expiry_note(target)
            else:
                packed_note = ""
            if target and expiry:
                try:
                    packed = _fetch_exchange_chain(target, expiry)
                except Exception:
                    packed = None
                if packed is None:
                    packed = {
                        "symbol": target,
                        "expiry": expiry,
                        "source": "missing",
                        "loaded": False,
                        "note": packed_note,
                    }
            else:
                packed = {"loaded": False, "source": "missing", "symbol": target, "expiry": ""}
        desk["chain"] = packed
    elif focus == "journal":
        if user_id is None:
            desk["journal"] = []
            desk["journal_note"] = "Sign in to read the paper journal."
        else:
            try:
                desk["journal"] = _journal_rows(user_id)
            except Exception:
                desk["journal"] = []
                desk["journal_note"] = "The paper journal could not be read."
    elif focus == "funds":
        try:
            desk["funds"] = _fund_rows(query)
        except Exception:
            desk["funds"] = []
            desk["funds_note"] = "The NAV catalog could not be read."
    elif focus == "ipo":
        try:
            desk["ipos"] = _ipo_rows()
        except Exception:
            desk["ipos"] = []
            desk["ipo_note"] = "The IPO list could not be read."
    elif focus == "earnings":
        try:
            desk["earnings"] = _earnings_row(symbol, screen)
        except Exception:
            desk["earnings"] = None
    elif focus != "level" and profile not in {
        "literacy",
        "small_talk",
        "compare_concepts",
        "session",
    } and str(screen.get("earnings_date") or "").strip():
        chart_symbol = str(screen.get("chart_symbol") or "").upper()
        if not symbol or not chart_symbol or _same_underlying(chart_symbol, symbol):
            desk["earnings"] = {
                "symbol": chart_symbol or symbol,
                "date": str(screen.get("earnings_date"))[:10],
                "illustrative": str(screen.get("earnings_illustrative") or "") in {"1", "true", "True"},
            }
    if focus != "level":
        desk["level"] = ""
    if not focus and not desk.get("earnings"):
        return {}
    if profile:
        desk["profile"] = profile
    return desk


def render_desk_answer(query: str, desk: Mapping[str, Any] | None) -> str | None:
    if not isinstance(desk, Mapping):
        return None
    focus = str(desk.get("focus") or "")
    if focus == "chain":
        return _render_chain(desk.get("chain") if isinstance(desk.get("chain"), Mapping) else {})
    if focus == "journal":
        return _render_journal(desk)
    if focus == "funds":
        return _render_funds(query, desk)
    if focus == "ipo":
        return _render_ipos(desk)
    if focus == "earnings":
        return _render_earnings(desk.get("earnings") if isinstance(desk.get("earnings"), Mapping) else None)
    if focus == "level":
        return _render_level(desk)
    return None


def render_desk_footnote(desk: Mapping[str, Any] | None) -> str:
    if not isinstance(desk, Mapping) or desk.get("focus"):
        return ""
    lines: list[str] = []
    earn = desk.get("earnings") if isinstance(desk.get("earnings"), Mapping) else None
    if earn and earn.get("date"):
        if earn.get("illustrative"):
            lines.append(
                f"Result date {earn.get('date')} on this symbol is illustrative. Not company guidance."
            )
        else:
            lines.append(
                f"Result date {earn.get('date')} is from the earnings calendar."
            )
    level = str(desk.get("level") or "").strip()
    if level:
        window = str(desk.get("timeframe") or "").strip()
        where = f" on the {window} chart" if window else ""
        lines.append(f"Drawn line{where}: {level}. The print is delayed.")
    if not lines:
        return ""
    return "\n".join(lines)


def _render_chain(chain: Mapping[str, Any]) -> str:
    symbol = str(chain.get("symbol") or "").strip() or "This index"
    expiry = str(chain.get("expiry") or "").strip() or "the nearest expiry"
    source = str(chain.get("source") or "")
    if not chain.get("loaded") or source == "missing":
        note = str(chain.get("note") or "")
        extra = f" {note}" if note else ""
        return (
            f"**{symbol} option chain**\n\n"
            f"The exchange chain for {expiry} did not load, so open interest, "
            f"straddle cost, and max pain are not shown.{extra}\n"
            "_Paper study only. This is not a live order book._"
        )
    origin = str(chain.get("origin") or "")
    if origin == "screen" and source in {"nse", "exchange"}:
        label = "Chain on this screen. The print is delayed."
    elif source not in {"nse", "exchange"}:
        label = "Teaching chain on screen, not the exchange board."
    else:
        label = "Exchange chain figures."
    lines = [
        f"**{symbol} option chain · {expiry}**",
        "",
        label,
        f"• Spot: {_num_text(chain.get('spot')) or 'n/a'}",
        f"• PCR (put OI / call OI): {_num_text(chain.get('pcr')) or 'n/a'}",
        f"• Max pain strike: {_num_text(chain.get('max_pain')) or 'n/a'}",
        f"• ATM straddle (call last + put last): {_num_text(chain.get('straddle')) or 'n/a'}"
        + (f" at {_num_text(chain.get('atm'))}" if chain.get("atm") else ""),
        "",
        "These are chain figures, not a traded straddle and not a recommendation.",
        "_Paper study only. The print is delayed._",
    ]
    return "\n".join(lines)


def _render_journal(desk: Mapping[str, Any]) -> str:
    note = str(desk.get("journal_note") or "")
    rows = desk.get("journal") if isinstance(desk.get("journal"), list) else []
    if note and not rows:
        return f"**Paper journal**\n\n{note}\n_Study notes are not paper orders._"
    if not rows:
        return (
            "**Paper journal**\n\n"
            "No journal rows yet. A study note needs a symbol, what you see, "
            "and what would prove it wrong.\n"
            "_Study notes are not paper orders._"
        )
    lines = ["**Paper journal**", "", "Latest notes on this practice book:"]
    for row in rows[:5]:
        if not isinstance(row, Mapping):
            continue
        side = str(row.get("side") or "").upper()
        symbol = str(row.get("symbol") or "")
        text = str(row.get("note") or "").strip()
        if side == "STUDY":
            lines.append(f"• {symbol} · study · {text or 'Study note.'}")
        else:
            lines.append(f"• {symbol} · {side or 'note'} · {text or 'No written note.'}")
    lines.append("")
    lines.append("Study rows are not buy or sell scores.")
    lines.append("_Paper journal — not a recommendation._")
    return "\n".join(lines)


def _render_funds(query: str, desk: Mapping[str, Any]) -> str:
    if desk.get("funds_note"):
        return (
            "**Mutual fund catalog**\n\n"
            f"{desk.get('funds_note')}\n"
            "Exit load and star rating are not in this catalog.\n"
            "_NAV study only — not a scheme recommendation._"
        )
    rows = desk.get("funds") if isinstance(desk.get("funds"), list) else []
    if not rows:
        return (
            "**Mutual fund catalog**\n\n"
            "Name the scheme (for example a fund house or scheme word). "
            "A blank “which fund” ask is not a ranking.\n"
            "1Y, 3Y, and 5Y returns, exit load, expense ratio, and star rating "
            "are not in the NAV row.\n"
            "_NAV study only — not a scheme recommendation._"
        )
    lines = ["**Mutual fund NAV**", "", f"Matches for: {(query or '').strip()[:80]}"]
    for row in rows[:3]:
        if not isinstance(row, Mapping):
            continue
        house = f" · {row.get('house')}" if row.get("house") else ""
        lines.append(
            f"• {row.get('name')} · NAV {_num_text(row.get('nav')) or 'n/a'} "
            f"on {row.get('nav_date') or 'n/a'}{house}"
        )
    lines.extend(
        [
            "",
            "Exit load, expense ratio, star rating, and 1Y/3Y/5Y returns are not in this NAV row.",
            "_NAV study only — not a scheme recommendation._",
        ]
    )
    return "\n".join(lines)


def _render_ipos(desk: Mapping[str, Any]) -> str:
    if desk.get("ipo_note"):
        return (
            "**IPO list**\n\n"
            f"{desk.get('ipo_note')}\n"
            "Grey-market premium is not in this list.\n"
            "_Issue calendar for study — not an application path._"
        )
    rows = desk.get("ipos") if isinstance(desk.get("ipos"), list) else []
    if not rows:
        return (
            "**IPO list**\n\n"
            "No issue is on the list right now.\n"
            "Grey-market premium is not in this list.\n"
            "_Issue calendar for study — not an application path._"
        )
    lines = ["**IPO list**", "", "From the issue calendar:"]
    for row in rows[:5]:
        if not isinstance(row, Mapping):
            continue
        band = f" · band {row.get('band')}" if row.get("band") else ""
        lot = f" · lot {row.get('lot')}" if row.get("lot") else ""
        lines.append(
            f"• {row.get('name')} · {row.get('status') or 'n/a'} · "
            f"{row.get('open') or 'n/a'} to {row.get('close') or 'n/a'}{band}{lot}"
        )
    lines.extend(
        [
            "",
            "Grey-market premium is not in this list.",
            "_Issue calendar for study — not an application or allotment path._",
        ]
    )
    return "\n".join(lines)


def _render_earnings(earn: Mapping[str, Any] | None) -> str:
    if not earn or not earn.get("date"):
        return (
            "**Result date**\n\n"
            "The earnings calendar has no date for this symbol, so none is shown.\n"
            "_Not company guidance._"
        )
    symbol = str(earn.get("symbol") or "This symbol")
    if earn.get("illustrative"):
        quality = "Illustrative result date. Not company guidance."
    else:
        quality = "From the earnings calendar. Not a forecast of the print."
    return (
        f"**{symbol} result date**\n\n"
        f"• {earn.get('date')}\n"
        f"{quality}\n"
        "_A date on the calendar is not a trading call._"
    )


def _render_level(desk: Mapping[str, Any]) -> str:
    level = str(desk.get("level") or "").strip()
    window = str(desk.get("timeframe") or "").strip()
    if not level:
        return (
            "**Drawn line**\n\n"
            "No horizontal line is on the open chart. Draw an H-line, then ask again.\n"
            "_The print is delayed._"
        )
    where = f" on the {window} window" if window else ""
    return (
        f"**Drawn line**\n\n"
        f"• {level}{where}\n"
        "That is the close you marked. It is not a target or a stop unless you say so.\n"
        "_The print is delayed._"
    )
