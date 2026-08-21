"""Long-horizon investor tips by topic (educational — not product recommendations)."""
from __future__ import annotations

from datetime import datetime
from typing import Any, Optional

from .habits import merge_habit_tips
from .market_session import IST

DISCLAIMER = (
    "Educational investor habits only — not stock, fund, or IPO recommendations, "
    "and not SEBI RA advice. Verify factsheets, DRHPs, and exchange rules yourself."
)

TOPICS = ("long_term", "mutual_funds", "ipo", "fno", "sgb")

_TOPIC_LABELS = {
    "long_term": "Long-term",
    "mutual_funds": "Mutual funds",
    "ipo": "IPOs",
    "fno": "F&O",
    "sgb": "SGB",
}

_TIP_BANKS: dict[str, list[dict[str, str]]] = {
    "long_term": [
        {
            "id": "lt_horizon",
            "title": "Define the horizon first",
            "body": "Money needed within 3 years usually shouldn't sit in concentrated equity bets. Match asset risk to time.",
            "category": "process",
        },
        {
            "id": "lt_allocation",
            "title": "Allocation beats stock-picking",
            "body": "Equity/debt/gold mix usually drives multi-year outcomes more than swapping one large-cap for another.",
            "category": "process",
        },
        {
            "id": "lt_costs",
            "title": "Costs compound too",
            "body": "High expense ratios, frequent churn, and taxes quietly erase years of alpha. Prefer simple, low-friction plans.",
            "category": "risk",
        },
        {
            "id": "lt_drawdown",
            "title": "Plan for drawdowns",
            "body": "If a 30–40% equity drawdown would force a sale, your allocation is too aggressive for your nerves or cash needs.",
            "category": "psychology",
        },
        {
            "id": "lt_rebalance",
            "title": "Rebalance on a calendar",
            "body": "Annual or band-based rebalancing sells strength and buys weakness without daily market noise.",
            "category": "process",
        },
        {
            "id": "lt_emergency",
            "title": "Protect the emergency sleeve",
            "body": "Keep 6–12 months expenses in liquid/safe assets. Don't count mutual-fund equity as an emergency fund.",
            "category": "risk",
        },
        {
            "id": "lt_thesis",
            "title": "Write a one-line thesis",
            "body": "For every multi-year holding: why own it, what would invalidate it. No thesis → easy panic selling.",
            "category": "process",
        },
        {
            "id": "lt_news",
            "title": "Ignore most daily noise",
            "body": "Long-term outcomes rarely hinge on one session's headline. Check process monthly; check prices less often.",
            "category": "psychology",
        },
    ],
    "mutual_funds": [
        {
            "id": "mf_pool",
            "title": "MF = pooled access",
            "body": "Units give you a slice of many securities via an AMC — useful when buying 50 index names yourself is impractical.",
            "category": "process",
        },
        {
            "id": "mf_nav",
            "title": "NAV is unit price, not quality",
            "body": "NAV = (assets − liabilities) / units. A ₹10 NAV isn't 'cheaper' than ₹100 — judge returns, risk, and TER.",
            "category": "process",
        },
        {
            "id": "mf_goal",
            "title": "Fund follows the goal",
            "body": "Pick category from goal + horizon (e.g. short debt vs equity SIP), then compare funds inside that box.",
            "category": "process",
        },
        {
            "id": "mf_risk_appetite",
            "title": "Risk = what you won't abandon",
            "body": "Ask how much short-term drawdown you can take without stopping the SIP. That sets equity vs debt share.",
            "category": "psychology",
        },
        {
            "id": "mf_factsheet",
            "title": "Read the factsheet",
            "body": "Check category, AUM, expense ratio, portfolio concentration, and exit load before chasing past returns.",
            "category": "process",
        },
        {
            "id": "mf_sip",
            "title": "SIP is a behaviour tool",
            "body": "SIPs average purchase price over time — they don't remove market risk. Keep SIPs through dull months.",
            "category": "psychology",
        },
        {
            "id": "mf_overlap",
            "title": "Watch portfolio overlap",
            "body": "Three large-cap funds can be the same Nifty names thrice. Diversify across styles/categories, not logos.",
            "category": "risk",
        },
        {
            "id": "mf_benchmark",
            "title": "Benchmark before bragging",
            "body": "Compare rolling returns vs category/index, not last 1-year chart. One hot year ≠ durable process.",
            "category": "process",
        },
        {
            "id": "mf_direct",
            "title": "Know direct vs regular",
            "body": "Direct plans cut distributor commission from TER. Same fund house/strategy — lower cost compounds.",
            "category": "process",
        },
        {
            "id": "mf_fees",
            "title": "TER + exit load matter",
            "body": "Expense ratio is the annual drag; exit load can hit early redemptions. Both reduce net wealth.",
            "category": "risk",
        },
        {
            "id": "mf_exit",
            "title": "Have an exit rule",
            "body": "Change funds for mandate drift, persistent underperformance, or goal change — not one bad quarter.",
            "category": "process",
        },
        {
            "id": "mf_tax",
            "title": "Mind tax & lock-ins",
            "body": "ELSS lock-in (and 80C only under eligible old-regime cases), debt taxation, and equity STCG/LTCG change net returns.",
            "category": "risk",
        },
        {
            "id": "mf_vs_fd",
            "title": "Don't expect FD certainty",
            "body": "Equity/hybrid MFs are market-linked. Use FDs/liquid sleeves for money you can't afford to see swing.",
            "category": "risk",
        },
    ],
    "ipo": [
        {
            "id": "ipo_drhp",
            "title": "Read risk factors first",
            "body": "DRHP/RHP risk factors and related-party deals matter more than grey-market premium chatter.",
            "category": "process",
        },
        {
            "id": "ipo_use",
            "title": "Follow the use of proceeds",
            "body": "Growth capex ≠ promoter selling down. Know whether fresh capital builds the business or exits owners.",
            "category": "process",
        },
        {
            "id": "ipo_valuation",
            "title": "Anchor vs listed peers",
            "body": "Compare P/E, growth, and margins to listed peers. Listing gains are uncertain; overpay is permanent.",
            "category": "risk",
        },
        {
            "id": "ipo_allotment",
            "title": "Allotment is a lottery",
            "body": "Retail quotas are often oversubscribed. Don't lever up or skip diversification hoping for full allotment.",
            "category": "psychology",
        },
        {
            "id": "ipo_listing",
            "title": "Have a listing plan",
            "body": "Decide before listing: hold for thesis, partial book, or exit if price disconnects from fundamentals.",
            "category": "process",
        },
        {
            "id": "ipo_hype",
            "title": "GMP isn't a guarantee",
            "body": "Grey market premium is informal and can vanish. Treat it as rumour, not a fair value.",
            "category": "risk",
        },
        {
            "id": "ipo_size",
            "title": "Size like a new position",
            "body": "IPO shares are concentrated single-stock risk. Cap size like any other new equity buy.",
            "category": "risk",
        },
        {
            "id": "ipo_asba",
            "title": "ASBA cash is blocked",
            "body": "Application money is blocked until allotment/refund. Don't apply amounts you need for other near-term bills.",
            "category": "process",
        },
    ],
    "fno": [
        {
            "id": "fo_vs",
            "title": "Futures vs options",
            "body": "Futures = agreement to buy/sell later (levered). Options = paid right to buy (call) or sell (put). Practice both in the paper gym first.",
            "category": "process",
        },
        {
            "id": "fo_lot",
            "title": "Think in lots, not shares",
            "body": "1 NIFTY lot is typically 50. Notional = lot × price. Margin is only cash blocked — a move can lose more than margin.",
            "category": "risk",
        },
        {
            "id": "fo_edge",
            "title": "Define the edge",
            "body": "F&O without a directional/vol/hedge thesis is leveraged guessing. Write why the contract, not just the ticker.",
            "category": "process",
        },
        {
            "id": "fo_margin",
            "title": "Margin ≠ capital at risk",
            "body": "Posted margin is a deposit. Loss can exceed it on gaps/assignment. Size from max loss, not lot affordability.",
            "category": "risk",
        },
        {
            "id": "fo_expiry",
            "title": "Respect expiry & CAS",
            "body": "Theta accelerates near expiry; stock F&O can involve physical settlement. Know square-off times (CAS/F&O clocks).",
            "category": "session",
        },
        {
            "id": "fo_greeks",
            "title": "Greeks before premiums",
            "body": "Cheap options can still be expensive in risk (delta/gamma). Check IV vs recent history before selling premium.",
            "category": "process",
        },
        {
            "id": "fo_hedge",
            "title": "Hedge or speculate — pick one",
            "body": "Portfolio hedges and naked directional bets need different size rules. Don't mix intents in one ticket.",
            "category": "process",
        },
        {
            "id": "fo_liquidity",
            "title": "Trade liquid strikes",
            "body": "Wide bid-ask and thin OI amplify slippage. Prefer near ATM weeklies/monthlies with real depth.",
            "category": "risk",
        },
        {
            "id": "fo_overnight",
            "title": "Overnight gap risk",
            "body": "Short options into events (results/policy) can gap through stops. Reduce size or stay flat into binary risk.",
            "category": "risk",
        },
        {
            "id": "fo_journal",
            "title": "Journal every F&O day",
            "body": "Record setup, IV, size, and rule breaks. Retail F&O edge is usually process — not a single hot trade.",
            "category": "psychology",
        },
    ],
    "sgb": [
        {
            "id": "sgb_horizon",
            "title": "Treat SGB as multi-year gold",
            "body": "SGBs suit a multi-year gold sleeve. Plan for the tenor and limited early-exit windows — not a swing-trade ticket.",
            "category": "process",
        },
        {
            "id": "sgb_vs_etf",
            "title": "Compare SGB vs gold ETF",
            "body": "SGB may pay periodic interest with sovereign backing; gold ETFs trade freer but charge TER and pay no coupon.",
            "category": "process",
        },
        {
            "id": "sgb_liquidity",
            "title": "Secondary liquidity can be thin",
            "body": "Exchange volumes for older SGB series can be sparse. Size only what you can hold through quiet periods.",
            "category": "risk",
        },
        {
            "id": "sgb_tax",
            "title": "Verify tax before you buy",
            "body": "Interest is usually taxable as income. Maturity capital-gains treatment has often differed from physical gold — confirm current rules yourself.",
            "category": "risk",
        },
        {
            "id": "sgb_allocation",
            "title": "Cap gold as a sleeve",
            "body": "Gold diversifies; it rarely replaces a full equity/debt plan. Keep SGB inside a planned % of net worth.",
            "category": "process",
        },
        {
            "id": "sgb_tranche",
            "title": "Read the live tranche notice",
            "body": "Issue price, dates, and limits change by tranche. Never assume last year's brochure still applies.",
            "category": "process",
        },
        {
            "id": "sgb_vs_futures",
            "title": "Not an MCX substitute",
            "body": "MCX gold futures are leveraged price bets with expiry risk. SGB is a long-hold savings-style gold exposure.",
            "category": "risk",
        },
        {
            "id": "sgb_emergency",
            "title": "Don't fund emergencies with SGB",
            "body": "Lock-in and thin secondary markets make SGB a poor emergency sleeve — keep cash/liquid funds separate.",
            "category": "risk",
        },
    ],
}


def _rotate(tips: list[dict[str, str]], seed: int, count: int) -> list[dict[str, str]]:
    if not tips:
        return []
    n = len(tips)
    start = seed % n
    ordered = tips[start:] + tips[:start]
    return ordered[: max(1, min(count, n))]


def normalize_topic(topic: str) -> str:
    raw = (topic or "long_term").strip().lower().replace("-", "_").replace(" ", "_")
    aliases = {
        "longterm": "long_term",
        "long": "long_term",
        "equity": "long_term",
        "investing": "long_term",
        "mf": "mutual_funds",
        "mutualfund": "mutual_funds",
        "mutual_fund": "mutual_funds",
        "funds": "mutual_funds",
        "ipos": "ipo",
        "listing": "ipo",
        "futures": "fno",
        "options": "fno",
        "derivatives": "fno",
        "f_and_o": "fno",
        "fo": "fno",
        "sgb": "sgb",
        "sovereign_gold": "sgb",
        "sovereign_gold_bond": "sgb",
        "sovereign_gold_bonds": "sgb",
        "gold_bond": "sgb",
        "gold_bonds": "sgb",
    }
    resolved = aliases.get(raw, raw)
    if resolved not in TOPICS:
        return "long_term"
    return resolved


def build_investor_tips(
    topic: str = "long_term",
    *,
    limit: int = 4,
    now: Optional[datetime] = None,
    activity: Optional[dict[str, Any]] = None,
) -> dict[str, Any]:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    key = normalize_topic(topic)
    bank = list(_TIP_BANKS.get(key) or _TIP_BANKS["long_term"])
    seed = now_ist.year * 10_000 + now_ist.timetuple().tm_yday * 10 + (TOPICS.index(key) + 1)
    # Mild hourly rotation so Home feels fresh across the day.
    seed += now_ist.hour
    educational = [dict(tip, source="topic", evidence=None) for tip in _rotate(bank, seed, max(limit, 3))]

    personalized = list((activity or {}).get("habits") or [])
    has_enough = bool((activity or {}).get("hasEnoughData"))
    sample_size = int((activity or {}).get("sampleSize") or 0)
    paper_note = str((activity or {}).get("paperNote") or "")
    tips = merge_habit_tips(
        personalized,
        educational,
        limit=limit,
        has_enough_data=has_enough,
    )
    return {
        "topic": key,
        "topicLabel": _TOPIC_LABELS.get(key, key),
        "tips": tips,
        "topics": [{"id": t, "label": _TOPIC_LABELS[t]} for t in TOPICS],
        "disclaimer": DISCLAIMER,
        "generatedAt": now_ist.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "sampleSize": sample_size,
        "hasEnoughData": has_enough,
        "paperNote": paper_note,
    }


def build_all_investor_tips(
    *,
    limit_per_topic: int = 2,
    now: Optional[datetime] = None,
) -> dict[str, Any]:
    now_ist = now.astimezone(IST) if now is not None else datetime.now(IST)
    by_topic = {
        topic: build_investor_tips(topic, limit=limit_per_topic, now=now_ist)["tips"]
        for topic in TOPICS
    }
    return {
        "topic": "all",
        "topicLabel": "All topics",
        "tips": [],
        "byTopic": by_topic,
        "topics": [{"id": t, "label": _TOPIC_LABELS[t]} for t in TOPICS],
        "disclaimer": DISCLAIMER,
        "generatedAt": now_ist.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
