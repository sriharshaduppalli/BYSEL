"""When /ai/ask may attach a ticker or buy/sell chips.

Buy/sell CTAs are only valid when the user's own text named a stock (or is a
clear pronoun follow-up). Literacy, greetings, and general how-to asks must
not inherit the last ticker or the open quote.
"""
from __future__ import annotations

import re
from typing import Any, Mapping

_NON_STOCK_SOURCES = frozenset({"small-talk", "clarifier", "education"})
_NON_STOCK_PROFILES = frozenset(
    {"literacy", "small_talk", "compare_concepts", "session"}
)
_NON_STOCK_INTENTS = frozenset(
    {"EDUCATIONAL", "SMALL_TALK", "COMPARE_CONCEPTS", "GENERAL"}
)
_INDEX_SYMBOLS = frozenset(
    {
        "NIFTY",
        "NIFTY50",
        "SENSEX",
        "BANKNIFTY",
        "NIFTYBANK",
        "NIFTYIT",
        "INDIAVIX",
    }
)

_TE_ANTE = "\u0c05\u0c02\u0c1f\u0c47"  # అంటే
_TE_EMITI = "\u0c0f\u0c2e\u0c3f\u0c1f\u0c3f"  # ఏమిటి
_TE_KONALA = "\u0c15\u0c4a\u0c28\u0c3e\u0c32\u0c3e"  # కొనాలా
_TE_AMMALA = "\u0c05\u0c2e\u0c4d\u0c2e\u0c3e\u0c32\u0c3e"  # అమ్మాలా

_DEFINITION_RE = re.compile(
    r"\b(what is|what are|define|definition|meaning of|explain|how to|"
    r"how does|how do i|difference between|formula|equation|teach)\b|"
    + _TE_ANTE
    + r"|"
    + _TE_EMITI,
    flags=re.IGNORECASE,
)
_FOLLOWUP_RE = re.compile(
    r"^(what about|how about|and |also |same |yes\b|ok\b|why\?|simplify|"
    r"tell me more|what next|both\b)|"
    r"\b(it|that stock|this stock|the same stock|its|both)\b",
    flags=re.IGNORECASE,
)
_TRADE_ASK_RE = re.compile(
    r"\b(should i (buy|sell)|buy or sell|hold or (exit|sell)|kharid|bech|"
    r"entry zone|trade plan|accumulate|trim)\b|"
    + _TE_KONALA
    + r"|"
    + _TE_AMMALA,
    flags=re.IGNORECASE,
)
_TRADE_CHIP_RE = re.compile(
    r"(should i (buy|sell)|wait for a dip|"
    r"good (buy|time to (buy|sell))|is it a good (buy|investment)|"
    r"practice (buy|sell)|risk vs reward for buying|best entry price)|"
    + _TE_KONALA
    + r"|"
    + _TE_AMMALA,
    flags=re.IGNORECASE,
)
_TRADE_PLAN_ANSWER_RE = re.compile(
    r"(paper (buy|sell) plan|\*\*action:\*\*\s*(buy|sell|hold|trim|wait)|"
    r"\*\*direct answer:\*\*\s*(buy|sell|hold|trim|accumulate))",
    flags=re.IGNORECASE,
)


def _query(query: str) -> str:
    return (query or "").strip()


def is_definitional_topic(query: str) -> bool:
    return bool(_DEFINITION_RE.search(_query(query)))


def is_trade_followup(query: str) -> bool:
    return bool(_FOLLOWUP_RE.search(_query(query)))


def is_trade_ask(query: str) -> bool:
    return bool(_TRADE_ASK_RE.search(_query(query)))


def user_named_symbol(query: str) -> str | None:
    """Symbol only if the user's own words named a stock (not RSI/SIP/etc.)."""
    text = _query(query)
    if not text:
        return None
    try:
        from indian_stock_llm.query_contract import _symbol_candidates

        found = _symbol_candidates(text)
        if found:
            return str(found[0]).upper()
    except Exception:
        pass
    try:
        from .stock_enricher import extract_symbol_from_query

        one = extract_symbol_from_query(text)
        if one:
            return str(one).upper()
    except Exception:
        pass
    return None


def is_index_symbol(symbol: str | None) -> bool:
    return str(symbol or "").upper().strip() in _INDEX_SYMBOLS


def is_non_stock_topic(
    query: str,
    *,
    source: str = "",
    profile: str = "",
    intent: str = "",
) -> bool:
    if (source or "") in _NON_STOCK_SOURCES:
        return True
    if (profile or "") in _NON_STOCK_PROFILES:
        return True
    named = user_named_symbol(query)
    if (intent or "").upper() in _NON_STOCK_INTENTS and not named:
        return True
    if is_definitional_topic(query) and not named:
        return True
    return False


def allow_response_symbol(
    query: str,
    *,
    source: str = "",
    profile: str = "",
    intent: str = "",
    follow_up: bool = False,
) -> bool:
    if is_non_stock_topic(query, source=source, profile=profile, intent=intent):
        return False
    if user_named_symbol(query):
        return True
    return bool(follow_up and is_trade_followup(query))


def allow_buy_sell_chips(
    query: str,
    *,
    source: str = "",
    profile: str = "",
    intent: str = "",
    follow_up: bool = False,
    symbol: str | None = None,
) -> bool:
    if is_index_symbol(symbol):
        return False
    return allow_response_symbol(
        query,
        source=source,
        profile=profile,
        intent=intent,
        follow_up=follow_up,
    )


def is_buy_sell_suggestion(text: str) -> bool:
    return bool(_TRADE_CHIP_RE.search(text or ""))


def strip_buy_sell_suggestions(suggestions: list[Any] | None) -> list[str]:
    out: list[str] = []
    for tip in suggestions or []:
        text = str(tip or "").strip()
        if not text or is_buy_sell_suggestion(text):
            continue
        out.append(text)
    return out


def sanitize_ask_payload(
    payload: Mapping[str, Any],
    query: str,
    *,
    source: str = "",
    profile: str = "",
    intent: str = "",
    follow_up: bool = False,
) -> dict[str, Any]:
    """Drop leaked tickers / buy-sell chips from a user-facing /ai/ask body."""
    out = dict(payload or {})
    named = user_named_symbol(query)
    allow_symbol = allow_response_symbol(
        query,
        source=source,
        profile=profile,
        intent=intent,
        follow_up=follow_up,
    )
    attached = str(out.get("symbol") or "").strip().upper() or None
    if not allow_symbol:
        out.pop("symbol", None)
        out.pop("signal", None)
        out.pop("current_price", None)
        data = dict(out.get("data") or {})
        data.pop("currentPrice", None)
        data.pop("price", None)
        if data:
            out["data"] = data
        else:
            out.pop("data", None)
        out["suggestions"] = strip_buy_sell_suggestions(out.get("suggestions"))
        return out

    if attached and named and attached != named and not follow_up:
        # Do not advertise a different ticker than the one the user named.
        out["symbol"] = named
    if not allow_buy_sell_chips(
        query,
        source=source,
        profile=profile,
        intent=intent,
        follow_up=follow_up,
        symbol=out.get("symbol"),
    ):
        out["suggestions"] = strip_buy_sell_suggestions(out.get("suggestions"))
        out.pop("signal", None)
    return out
