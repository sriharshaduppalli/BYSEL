"""Paper-trade habit scoring for session + investor cards.

Educational process cues only — never buy/sell advice.
All timestamps are interpreted as UTC when naive, then bucketed in IST.
Insights stay silent or say "not enough data" until the sample is large enough.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from datetime import datetime, time, timedelta, timezone
from typing import Any, Iterable, Optional

from .market_session import CASH_OPEN, IST, session_close_for_status

MIN_PATTERN_TRADES = 5
SOFT_PATTERN_TRADES = 3
LOOKBACK_DAYS = 30
CONCENTRATION_SHARE = 0.40
OPEN_CLUSTER_SHARE = 0.50
CLOSING_CLUSTER_SHARE = 0.35
ONE_WAY_BUY_SHARE = 0.80
BUSY_DAY_TRADES = 8
ROUND_TRIP_MIN = 3

_SESSION_BUCKETS = (
    "first_hour",
    "mid_morning",
    "lunch_lull",
    "afternoon",
    "closing_window",
    "after_hours",
    "weekend",
)


def _as_ist(value: Any) -> Optional[datetime]:
    if value is None:
        return None
    if isinstance(value, datetime):
        dt = value
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(IST)
    if isinstance(value, (int, float)):
        ts = float(value)
        if ts > 1e12:
            ts /= 1000.0
        if ts <= 0:
            return None
        return datetime.fromtimestamp(ts, tz=timezone.utc).astimezone(IST)
    if isinstance(value, str):
        raw = value.strip()
        if not raw:
            return None
        try:
            if raw.endswith("Z"):
                raw = raw[:-1] + "+00:00"
            dt = datetime.fromisoformat(raw)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt.astimezone(IST)
        except ValueError:
            return None
    return None


def session_bucket(now: datetime) -> str:
    """IST session window for a timestamp (holiday not applied — caller filters)."""
    now_ist = now.astimezone(IST) if now.tzinfo else now.replace(tzinfo=IST)
    if now_ist.weekday() >= 5:
        return "weekend"
    t = now_ist.time()
    close = session_close_for_status(now_ist.date())
    if t < CASH_OPEN:
        return "after_hours"
    if t > close:
        return "after_hours"
    if t < time(10, 15):
        return "first_hour"
    if t < time(12, 0):
        return "mid_morning"
    if t < time(13, 30):
        return "lunch_lull"
    if t < time(14, 45):
        return "afternoon"
    return "closing_window"


def _habit(
    habit_id: str,
    title: str,
    body: str,
    *,
    category: str,
    evidence: str,
    source: str = "paper",
) -> dict[str, str]:
    return {
        "id": habit_id,
        "title": title,
        "body": body,
        "category": category,
        "source": source,
        "evidence": evidence,
    }


def _norm_symbol(value: Any) -> str:
    return str(value or "").strip().upper().replace(".NS", "").replace(".BO", "")


def _norm_side(value: Any) -> str:
    return str(value or "").strip().upper()


def _is_stop_order(order: dict[str, Any]) -> bool:
    otype = str(order.get("orderType") or order.get("order_type") or "").strip().upper()
    if otype in {"SL", "SLM"}:
        return True
    trigger = order.get("triggerPrice")
    if trigger is None:
        trigger = order.get("trigger_price")
    try:
        return trigger is not None and float(trigger) > 0
    except (TypeError, ValueError):
        return False


def _trade_dicts(trades: Iterable[Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for raw in trades or []:
        if hasattr(raw, "symbol"):
            item = {
                "symbol": getattr(raw, "symbol", ""),
                "side": getattr(raw, "side", ""),
                "qty": getattr(raw, "quantity", None) if getattr(raw, "quantity", None) is not None else getattr(raw, "qty", 0),
                "price": getattr(raw, "price", 0) or 0,
                "total": getattr(raw, "total", 0) or 0,
                "orderType": getattr(raw, "order_type", None) or getattr(raw, "orderType", ""),
                "triggerPrice": getattr(raw, "trigger_price", None) or getattr(raw, "triggerPrice", None),
                "createdAt": getattr(raw, "created_at", None) or getattr(raw, "createdAt", None),
            }
        elif isinstance(raw, dict):
            item = dict(raw)
        else:
            continue
        symbol = _norm_symbol(item.get("symbol"))
        if not symbol:
            continue
        item["symbol"] = symbol
        item["side"] = _norm_side(item.get("side"))
        item["_ist"] = _as_ist(item.get("createdAt") or item.get("created_at") or item.get("timestamp"))
        out.append(item)
    return out


def _in_lookback(trades: list[dict[str, Any]], now_ist: datetime) -> list[dict[str, Any]]:
    cutoff = now_ist - timedelta(days=LOOKBACK_DAYS)
    kept: list[dict[str, Any]] = []
    for trade in trades:
        when = trade.get("_ist")
        if when is None:
            kept.append(trade)
            continue
        if when >= cutoff:
            kept.append(trade)
    return kept


def _holding_dicts(holdings: Iterable[Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for raw in holdings or []:
        if hasattr(raw, "symbol"):
            qty = getattr(raw, "quantity", None)
            if qty is None:
                qty = getattr(raw, "qty", 0)
            last = getattr(raw, "last_price", None)
            if last is None:
                last = getattr(raw, "last", 0)
            avg = getattr(raw, "avg_price", None)
            if avg is None:
                avg = getattr(raw, "avgPrice", 0)
            item = {"symbol": raw.symbol, "qty": qty, "last": last, "avgPrice": avg}
        elif isinstance(raw, dict):
            item = dict(raw)
        else:
            continue
        symbol = _norm_symbol(item.get("symbol"))
        if not symbol:
            continue
        try:
            qty = int(item.get("qty") or item.get("quantity") or 0)
        except (TypeError, ValueError):
            qty = 0
        if qty <= 0:
            continue
        try:
            last = float(item.get("last") or item.get("last_price") or item.get("avgPrice") or item.get("avg_price") or 0)
        except (TypeError, ValueError):
            last = 0.0
        try:
            avg = float(item.get("avgPrice") or item.get("avg_price") or last or 0)
        except (TypeError, ValueError):
            avg = last
        value = qty * (last if last > 0 else avg)
        out.append({"symbol": symbol, "qty": qty, "value": value})
    return out


def _not_enough_session_habit(sample: int) -> dict[str, str]:
    need = MIN_PATTERN_TRADES
    return _habit(
        "need_more_paper",
        "Not enough paper trades yet",
        (
            f"Session patterns need at least {need} paper fills in the last {LOOKBACK_DAYS} days. "
            f"You have {sample}. Use Today's Practice (Idea → paper trade → Review) so the next cards "
            "can describe your IST timing — not a generic checklist."
        ),
        category="process",
        evidence=f"{sample} paper trade{'s' if sample != 1 else ''} in {LOOKBACK_DAYS}d",
        source="paper",
    )


def _not_enough_investor_habit(sample: int, holdings_n: int) -> dict[str, str]:
    return _habit(
        "need_more_book",
        "Practice book still thin",
        (
            "Investor habits here are built from your paper holdings and fills — not live demat. "
            f"Logged paper trades: {sample}; open paper names: {holdings_n}. "
            "A few reviewed practice loops beat a long unused watchlist."
        ),
        category="process",
        evidence=f"{sample} paper trades · {holdings_n} paper holdings",
        source="paper",
    )


def score_session_habits(
    trades: Iterable[Any],
    *,
    trigger_count: int = 0,
    journal_entries: Optional[Iterable[Any]] = None,
    now: Optional[datetime] = None,
) -> dict[str, Any]:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    rows = _in_lookback(_trade_dicts(trades), now_ist)
    sample = len(rows)
    habits: list[dict[str, str]] = []

    if sample < SOFT_PATTERN_TRADES:
        return {
            "habits": [_not_enough_session_habit(sample)],
            "sampleSize": sample,
            "hasEnoughData": False,
            "paperNote": (
                f"Paper-trade sample: {sample} in {LOOKBACK_DAYS} days (IST). "
                f"Need {MIN_PATTERN_TRADES} fills before timing patterns are shown."
            ),
            "stats": {"sample": sample, "buckets": {}},
        }

    buckets: Counter[str] = Counter()
    by_symbol: Counter[str] = Counter()
    by_day: Counter[str] = Counter()
    buys = 0
    sells = 0
    stop_orders = 0
    for row in rows:
        when = row.get("_ist")
        if when is not None:
            buckets[session_bucket(when)] += 1
            by_day[when.strftime("%Y-%m-%d")] += 1
        by_symbol[row["symbol"]] += 1
        if row["side"] == "BUY":
            buys += 1
        elif row["side"] == "SELL":
            sells += 1
        if _is_stop_order(row):
            stop_orders += 1

    session_fills = sum(buckets[b] for b in _SESSION_BUCKETS if b not in {"after_hours", "weekend"})
    first_hour = buckets["first_hour"]
    closing = buckets["closing_window"]
    lunch = buckets["lunch_lull"]
    after_hours = buckets["after_hours"]

    if sample >= MIN_PATTERN_TRADES and session_fills >= MIN_PATTERN_TRADES:
        if first_hour / session_fills >= OPEN_CLUSTER_SHARE:
            habits.append(
                _habit(
                    "open_cluster",
                    "Paper fills clustered at the open",
                    (
                        f"{first_hour} of {session_fills} in-session paper fills landed in 09:15–10:15 IST. "
                        "The opening range is often the widest — a written invalidation before 9:15 is the "
                        "usual process check, not chasing the first print."
                    ),
                    category="session",
                    evidence=f"{first_hour}/{session_fills} fills in first hour (IST)",
                )
            )
        if closing / session_fills >= CLOSING_CLUSTER_SHARE:
            habits.append(
                _habit(
                    "close_cluster",
                    "Late-session paper activity",
                    (
                        f"{closing} of {session_fills} in-session paper fills were after 14:45 IST. "
                        "From 3 Aug 2026, F&O cash continuous ends ~15:15, CAS ~15:35, derivatives ~15:40 — "
                        "and broker MIS square-off can be earlier. Late size has less time to work."
                    ),
                    category="session",
                    evidence=f"{closing}/{session_fills} fills in the closing window (IST)",
                )
            )
        if lunch >= max(3, int(0.30 * session_fills)):
            habits.append(
                _habit(
                    "lunch_chop",
                    "Fills in the midday lull",
                    (
                        f"{lunch} paper fills hit 12:00–13:30 IST, when cash volume often thins. "
                        "Fake breaks are common — many desks cut size or wait rather than force a ticket."
                    ),
                    category="session",
                    evidence=f"{lunch} fills in 12:00–13:30 IST",
                )
            )

    if after_hours >= 2 and sample >= SOFT_PATTERN_TRADES:
        habits.append(
            _habit(
                "after_hours_fills",
                "Fills outside the cash tape",
                (
                    f"{after_hours} paper fills were logged outside 09:15–close IST (or on a weekend). "
                    "BYSEL can accept practice tickets anytime — treat those as prep, not as live-session "
                    "execution practice."
                ),
                category="session",
                evidence=f"{after_hours} off-session paper fills",
            )
        )

    if by_symbol and sample >= MIN_PATTERN_TRADES:
        top_symbol, top_n = by_symbol.most_common(1)[0]
        if top_n / sample >= CONCENTRATION_SHARE and top_n >= 3:
            habits.append(
                _habit(
                    "single_name",
                    f"Over-trading {top_symbol}",
                    (
                        f"{top_n} of {sample} recent paper fills were {top_symbol}. "
                        "Repeating one name can be a thesis — or revenge sizing. "
                        "A one-line invalidation per fill keeps the loop honest."
                    ),
                    category="risk",
                    evidence=f"{top_n}/{sample} paper fills in {top_symbol}",
                )
            )

    busiest = by_day.most_common(1)
    if busiest and busiest[0][1] >= BUSY_DAY_TRADES and sample >= MIN_PATTERN_TRADES:
        day_key, day_n = busiest[0]
        habits.append(
            _habit(
                "busy_session",
                "High ticket count on one IST day",
                (
                    f"{day_n} paper fills on {day_key} (IST calendar). "
                    "A busy practice day is useful only if each ticket had a stop and a reason — "
                    "count of trades is not a skill score."
                ),
                category="psychology",
                evidence=f"{day_n} paper fills on {day_key}",
            )
        )

    if sample >= MIN_PATTERN_TRADES and buys >= 4 and buys / max(buys + sells, 1) >= ONE_WAY_BUY_SHARE:
        habits.append(
            _habit(
                "one_way_book",
                "Mostly buys, few exits",
                (
                    f"{buys} paper buys vs {sells} sells in the last {LOOKBACK_DAYS} days. "
                    "Practice includes exiting. A time-stop or thesis-fail sell is part of the loop — "
                    "not a live recommendation."
                ),
                category="process",
                evidence=f"{buys} buys · {sells} sells",
            )
        )

    sl_like = stop_orders + max(0, int(trigger_count))
    if sample >= MIN_PATTERN_TRADES and sl_like == 0:
        habits.append(
            _habit(
                "no_stop",
                "No stop-style paper tickets",
                (
                    f"None of the last {sample} paper fills used SL/SLM or a trigger price, "
                    "and no trigger orders are on the book. "
                    "A stop is where the thesis dies — write it before the entry, even in practice."
                ),
                category="risk",
                evidence=f"0 SL/trigger tickets across {sample} fills",
            )
        )
    elif sample >= MIN_PATTERN_TRADES and sl_like > 0:
        habits.append(
            _habit(
                "stops_in_use",
                "Stop practice is on the book",
                (
                    f"{sl_like} stop-style tickets (SL/SLM, trigger price, or working triggers) "
                    f"alongside {sample} paper fills. Keep reviewing whether the stop was respected — "
                    "that is the habit, not the P&L."
                ),
                category="process",
                evidence=f"{sl_like} stop-style tickets · {sample} fills",
            )
        )

    chase = _journal_chase_habit(journal_entries, buys_fallback=buys)
    if chase:
        habits.append(chase)

    if sample >= MIN_PATTERN_TRADES and not habits:
        spread = ", ".join(
            f"{label.replace('_', ' ')} {buckets[label]}"
            for label in ("first_hour", "mid_morning", "lunch_lull", "afternoon", "closing_window")
            if buckets[label]
        )
        habits.append(
            _habit(
                "session_mix",
                "Timing is spread across the IST day",
                (
                    f"{sample} paper fills in {LOOKBACK_DAYS} days are not bunched in one window"
                    + (f" ({spread})." if spread else ".")
                    + " Keep tagging plan-followed vs impulse so the mix stays a process, not luck."
                ),
                category="process",
                evidence=f"{sample} paper fills · mixed IST windows",
            )
        )

    has_enough = sample >= MIN_PATTERN_TRADES
    return {
        "habits": habits[:4],
        "sampleSize": sample,
        "hasEnoughData": has_enough,
        "paperNote": (
            f"Based on {sample} paper fills in the last {LOOKBACK_DAYS} days (IST session windows). "
            "Educational — not a live-brokerage report."
        ),
        "stats": {
            "sample": sample,
            "buys": buys,
            "sells": sells,
            "buckets": dict(buckets),
            "stopLike": sl_like,
        },
    }


def _journal_chase_habit(journal_entries: Optional[Iterable[Any]], *, buys_fallback: int) -> Optional[dict[str, str]]:
    rows: list[dict[str, Any]] = []
    for raw in journal_entries or []:
        if hasattr(raw, "side"):
            ctx = {}
            try:
                import json

                raw_ctx = getattr(raw, "context_json", None)
                if raw_ctx:
                    ctx = json.loads(raw_ctx) if isinstance(raw_ctx, str) else dict(raw_ctx)
            except Exception:
                ctx = {}
            rows.append({"side": _norm_side(getattr(raw, "side", "")), "context": ctx})
        elif isinstance(raw, dict):
            rows.append(
                {
                    "side": _norm_side(raw.get("side")),
                    "context": raw.get("context") or {},
                }
            )
    buys = [r for r in rows if r["side"] == "BUY"]
    if len(buys) < SOFT_PATTERN_TRADES:
        return None
    overbought = 0
    near_high = 0
    for row in buys:
        ctx = row.get("context") or {}
        rsi = ctx.get("rsiAtTrade")
        try:
            if rsi is not None and float(rsi) >= 70:
                overbought += 1
        except (TypeError, ValueError):
            pass
        if ctx.get("near52wHigh") is True:
            near_high += 1
    n = len(buys)
    if overbought / n >= 0.30 and overbought >= 2:
        return _habit(
            "chase_rsi",
            "Buys tagged overbought on the journal",
            (
                f"{overbought} of {n} journaled paper buys had RSI ≥ 70 at the fill. "
                "That is a chase-risk flag from your own log — wait for a pullback or skip, "
                "rather than treating momentum as a thesis."
            ),
            category="psychology",
            evidence=f"{overbought}/{n} journal buys with RSI≥70",
        )
    if near_high / n >= 0.40 and near_high >= 2:
        return _habit(
            "chase_high",
            "Buys near the 52-week high",
            (
                f"{near_high} of {n} journaled paper buys were near the 52-week high. "
                "Strength can continue — but R:R is often thinner at resistance. "
                "Write why this level, not only that it is making highs."
            ),
            category="risk",
            evidence=f"{near_high}/{n} journal buys near 52W high",
        )
    return None


def score_investor_habits(
    trades: Iterable[Any],
    *,
    holdings: Optional[Iterable[Any]] = None,
    goals: Optional[Iterable[Any]] = None,
    alert_count: int = 0,
    wallet_balance: Optional[float] = None,
    topic: str = "long_term",
    now: Optional[datetime] = None,
) -> dict[str, Any]:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    rows = _in_lookback(_trade_dicts(trades), now_ist)
    book = _holding_dicts(holdings)
    sample = len(rows)
    habits: list[dict[str, str]] = []

    book_value = sum(h["value"] for h in book)
    if book and book_value > 0:
        top = max(book, key=lambda h: h["value"])
        share = top["value"] / book_value
        if share >= CONCENTRATION_SHARE and len(book) >= 2:
            habits.append(
                _habit(
                    "book_concentration",
                    f"Paper book heavy in {top['symbol']}",
                    (
                        f"{top['symbol']} is about {int(share * 100)}% of your paper book "
                        f"({len(book)} names). Concentration is a choice — match it to a written "
                        "horizon and an invalidation, not to the last mover on the heatmap."
                    ),
                    category="risk",
                    evidence=f"{top['symbol']} ≈ {int(share * 100)}% of paper book",
                )
            )
        elif len(book) == 1:
            habits.append(
                _habit(
                    "single_holding",
                    "One name is the whole paper book",
                    (
                        f"Only {book[0]['symbol']} is open in paper holdings. "
                        "That is fine for a drill — it is not a diversified plan. "
                        "Size the next practice name from a goal, not from a tip card."
                    ),
                    category="risk",
                    evidence=f"1 paper holding · {book[0]['symbol']}",
                )
            )

    if sample == 0 and not book:
        unused_bits = []
        if wallet_balance is not None and wallet_balance > 0:
            unused_bits.append(f"practice wallet is funded (₹{int(wallet_balance):,})")
        if alert_count > 0:
            unused_bits.append(f"{alert_count} price alert{'s' if alert_count != 1 else ''} set")
        extra = (" " + "; ".join(unused_bits) + ".") if unused_bits else ""
        habits.append(
            _habit(
                "unused_practice",
                "Paper trading is unused",
                (
                    "No paper fills or open paper holdings in the lookback."
                    + extra
                    + " Today's Practice on Home is the loop: pick an idea, paper-buy or set Alert @ SL, then review. "
                    "Cards stay educational until that sample exists."
                ),
                category="process",
                evidence="0 paper fills · 0 paper holdings",
            )
        )
    elif sample < SOFT_PATTERN_TRADES and not habits:
        habits.append(_not_enough_investor_habit(sample, len(book)))

    if sample >= MIN_PATTERN_TRADES:
        round_trips = _same_day_round_trips(rows)
        topic_key = (topic or "long_term").strip().lower()
        if topic_key in {"long_term", "mutual_funds", "sgb"} and round_trips >= ROUND_TRIP_MIN:
            habits.append(
                _habit(
                    "horizon_mismatch",
                    "Same-day round trips vs a long horizon",
                    (
                        f"{round_trips} same-IST-day buy+sell pairs show up in your paper log, "
                        f"while this card is on “{_topic_label(topic_key)}”. "
                        "Intraday practice and multi-year allocation are different sports — "
                        "keep the journals separate so risk rules do not blur."
                    ),
                    category="process",
                    evidence=f"{round_trips} same-day paper round trips",
                )
            )
        if topic_key == "fno" and sample >= MIN_PATTERN_TRADES:
            habits.append(
                _habit(
                    "fno_size_check",
                    "F&O practice is not lot-affordability",
                    (
                        f"{sample} paper fills in the lookback. For F&O drills, size from max loss "
                        "and expiry/CAS clocks — posted margin is a deposit, not the risk. "
                        "This is a process reminder from your activity, not a contract pick."
                    ),
                    category="risk",
                    evidence=f"{sample} paper fills · F&O topic selected",
                )
            )

    risk_habit = _risk_profile_habit(goals, book, sample)
    if risk_habit:
        habits.append(risk_habit)

    if sample >= MIN_PATTERN_TRADES and book and not any(h["id"] == "unused_practice" for h in habits):
        if not habits:
            habits.append(
                _habit(
                    "book_in_use",
                    "Practice book is being used",
                    (
                        f"{sample} paper fills and {len(book)} open paper names in {LOOKBACK_DAYS} days. "
                        "Next accuracy step: tag each review with stop used / plan followed "
                        "so these cards can talk about discipline, not only count."
                    ),
                    category="process",
                    evidence=f"{sample} fills · {len(book)} holdings",
                )
            )

    has_enough = sample >= MIN_PATTERN_TRADES or len(book) >= 2
    if not habits:
        habits.append(_not_enough_investor_habit(sample, len(book)))
        has_enough = False

    return {
        "habits": habits[:4],
        "sampleSize": sample,
        "hasEnoughData": has_enough,
        "paperNote": (
            f"Based on {sample} paper fills and {len(book)} paper holdings "
            f"(last {LOOKBACK_DAYS} days, IST). Not your live demat."
        ),
        "stats": {
            "sample": sample,
            "holdings": len(book),
            "roundTrips": _same_day_round_trips(rows) if rows else 0,
        },
    }


def _topic_label(topic: str) -> str:
    return {
        "long_term": "Long-term",
        "mutual_funds": "Mutual funds",
        "ipo": "IPOs",
        "fno": "F&O",
        "sgb": "SGB",
    }.get(topic, topic)


def _same_day_round_trips(rows: list[dict[str, Any]]) -> int:
    by_day_symbol: dict[tuple[str, str], list[str]] = defaultdict(list)
    for row in rows:
        when = row.get("_ist")
        if when is None or row["side"] not in {"BUY", "SELL"}:
            continue
        by_day_symbol[(when.strftime("%Y-%m-%d"), row["symbol"])].append(row["side"])
    trips = 0
    for sides in by_day_symbol.values():
        buys = sides.count("BUY")
        sells = sides.count("SELL")
        trips += min(buys, sells)
    return trips


def _risk_profile_habit(
    goals: Optional[Iterable[Any]],
    book: list[dict[str, Any]],
    sample: int,
) -> Optional[dict[str, str]]:
    profiles: list[str] = []
    for raw in goals or []:
        if hasattr(raw, "risk_profile"):
            profiles.append(str(getattr(raw, "risk_profile") or "").upper())
        elif isinstance(raw, dict):
            profiles.append(str(raw.get("riskProfile") or raw.get("risk_profile") or "").upper())
    if not profiles:
        return None
    conservative = any(p in {"CONSERVATIVE", "LOW", "SAFE"} for p in profiles)
    aggressive = any(p in {"AGGRESSIVE", "HIGH"} for p in profiles)
    if conservative and book:
        top = max(book, key=lambda h: h["value"])
        book_value = sum(h["value"] for h in book) or 1.0
        share = top["value"] / book_value
        if share >= CONCENTRATION_SHARE or sample >= BUSY_DAY_TRADES:
            return _habit(
                "risk_mismatch",
                "Conservative goal vs concentrated paper book",
                (
                    f"A goal is tagged conservative/low, but {top['symbol']} is "
                    f"~{int(share * 100)}% of the paper book"
                    + (f" and you logged {sample} fills in {LOOKBACK_DAYS} days." if sample else ".")
                    + " That mix can be a drill — just notice the mismatch before treating "
                    "paper P&L as a plan for money you cannot see swing."
                ),
                category="risk",
                evidence=f"goal risk={profiles[0]} · top name {int(share * 100)}%",
            )
    if aggressive and sample == 0 and not book:
        return _habit(
            "risk_unused",
            "Aggressive goal, unused practice book",
            (
                "A goal is tagged aggressive, but there are no paper fills or holdings to test "
                "drawdown tolerance. Practice the size and stop rules first — a label is not a habit."
            ),
            category="process",
            evidence=f"goal risk={profiles[0]} · empty paper book",
        )
    return None


def merge_habit_tips(
    personalized: list[dict[str, str]],
    educational: list[dict[str, str]],
    *,
    limit: int,
    has_enough_data: bool,
    not_enough: Optional[dict[str, str]] = None,
) -> list[dict[str, str]]:
    """Prefer 1–2 paper-backed habits, then session/topic education. Dedup by id."""
    limit = max(1, min(int(limit), 8))
    out: list[dict[str, str]] = []
    seen: set[str] = set()

    def _add(tip: dict[str, str]) -> None:
        tip_id = str(tip.get("id") or "")
        if not tip_id or tip_id in seen:
            return
        if len(out) >= limit:
            return
        seen.add(tip_id)
        out.append(tip)

    paper_slots = 2 if limit >= 3 else 1
    paper = list(personalized or [])
    if paper:
        for tip in paper[:paper_slots]:
            _add(tip)
    elif not has_enough_data and not_enough:
        _add(not_enough)

    for tip in educational or []:
        _add(tip)
        if len(out) >= limit:
            break
    return out


def activity_from_db(db: Any, user_id: int) -> dict[str, Any]:
    """Load paper-trade context for habit scoring. No live quote fetches."""
    from .database.db import (
        AlertModel,
        GoalPlanModel,
        HoldingModel,
        OrderModel,
        TradeJournalModel,
        TriggerOrderModel,
        WalletModel,
    )

    orders = (
        db.query(OrderModel)
        .filter(OrderModel.user_id == user_id)
        .order_by(OrderModel.created_at.desc())
        .limit(80)
        .all()
    )
    holdings = (
        db.query(HoldingModel)
        .filter(HoldingModel.user_id == user_id)
        .all()
    )
    goals = (
        db.query(GoalPlanModel)
        .filter(GoalPlanModel.user_id == user_id)
        .all()
    )
    journal = (
        db.query(TradeJournalModel)
        .filter(TradeJournalModel.user_id == user_id)
        .order_by(TradeJournalModel.created_at.desc())
        .limit(50)
        .all()
    )
    trigger_count = (
        db.query(TriggerOrderModel)
        .filter(TriggerOrderModel.user_id == user_id)
        .count()
    )
    alert_count = (
        db.query(AlertModel)
        .filter(AlertModel.user_id == user_id, AlertModel.is_active.is_(True))
        .count()
    )
    wallet = db.query(WalletModel).filter(WalletModel.user_id == user_id).first()
    return {
        "orders": orders,
        "holdings": holdings,
        "goals": goals,
        "journal": journal,
        "trigger_count": int(trigger_count or 0),
        "alert_count": int(alert_count or 0),
        "wallet_balance": float(wallet.balance) if wallet is not None else None,
    }


def wilder_rsi(prices: Iterable[float], period: int = 14) -> Optional[float]:
    """Wilder RSI. Returns None when the series is too short — never a fake 50."""
    series = [float(p) for p in prices if p is not None]
    if len(series) < period + 1:
        return None
    deltas = [series[i] - series[i - 1] for i in range(1, len(series))]
    avg_gain = sum(max(d, 0.0) for d in deltas[:period]) / period
    avg_loss = sum(max(-d, 0.0) for d in deltas[:period]) / period
    for delta in deltas[period:]:
        avg_gain = (avg_gain * (period - 1) + max(delta, 0.0)) / period
        avg_loss = (avg_loss * (period - 1) + max(-delta, 0.0)) / period
    if avg_loss == 0:
        return 100.0 if avg_gain > 0 else None
    rs = avg_gain / avg_loss
    return round(100.0 - (100.0 / (1.0 + rs)), 1)
