"""Deterministic paper-habit lessons for Home / F&O Learn chips.

These answers must stay concrete (clock, one rule, one mistake) and must not
show equations, vendor names, or buy/sell calls.
"""
from __future__ import annotations

import re
from typing import Optional

_HABIT_MARK = re.compile(r"\[BYSEL_HABIT:([a-z0-9_]+)\]", re.I)

# Longer / more specific patterns first. Keep F&O scanner chips distinct from
# the Home F&O habit (which also says "futures vs options").
_HABIT_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("fno_expiry", re.compile(r"lose value as expiry|time decay|expiry week", re.I)),
    ("fno_lot", re.compile(r"lot size and margin in nse|what a lot size means", re.I)),
    (
        "fno",
        re.compile(
            r"futures vs options.{0,80}(lot size|margin).{0,60}(paper habit|educational paper)",
            re.I,
        ),
    ),
    (
        "fno_vs",
        re.compile(
            r"difference between.{0,40}futures and options|futures versus options for beginners",
            re.I,
        ),
    ),
    ("open", re.compile(r"opening range|first[- ]hour volatil|first hour of (the )?nse", re.I)),
    (
        "risk",
        re.compile(
            r"stop[- ]loss.{0,20}size.{0,20}(avoid )?fomo|avoid fomo on nse paper",
            re.I,
        ),
    ),
    ("chop", re.compile(r"midday chop|revenge trad", re.I)),
    ("close", re.compile(r"closing auction|\bcas\b.{0,40}square|square[- ]off.{0,20}intraday", re.I)),
    (
        "weekend",
        re.compile(
            r"weekend(s)? and holiday|review paper trades on weekend|holiday prep",
            re.I,
        ),
    ),
    (
        "mutual_funds",
        re.compile(
            r"what are mutual funds\b|mutual funds?.{0,40}(nav|sip|ter)|nav, sip and ter",
            re.I,
        ),
    ),
    ("ipo", re.compile(r"\bipo\b.{0,40}(drhp|allotment)|read an? ipo drhp", re.I)),
    ("sgb", re.compile(r"sovereign gold bond|\bsgb\b", re.I)),
    (
        "long_term",
        re.compile(
            r"long[- ]term investing in indian stocks|beginners do long[- ]term",
            re.I,
        ),
    ),
]

_LESSONS: dict[str, str] = {
    "open": (
        "Opening range — paper habit (not a buy or sell call)\n\n"
        "What it is\n"
        "After 9:15 IST the first few minutes often print a high and a low. That band is the "
        "opening range. It is a map of early interest, not a signal to trade.\n\n"
        "Paper habit\n"
        "1. Mark the first 15-minute high and low on your paper sheet. Do not place a paper "
        "order in those first minutes unless the drill is specifically 'observe only'.\n"
        "2. Write one sentence: did price stay inside the band, break above, or break below?\n"
        "3. Wait for a close outside the band on the next 15-minute bar before you even "
        "consider a paper entry. If it snaps back in, treat that as noise.\n\n"
        "Why the first hour feels wild\n"
        "Overnight news, pending orders, and index futures all hit at once. Spreads can be "
        "wider. A big candle is not proof you 'missed' something.\n\n"
        "Common mistake\n"
        "Chasing the first green candle because others are talking about it. That is FOMO, "
        "not a plan.\n\n"
        "Use this only on paper. Live orders are not part of this lesson."
    ),
    "risk": (
        "Stops, size, and FOMO — paper habit (not a buy or sell call)\n\n"
        "What to write before any paper trade\n"
        "1. Invalidation: the price where your reason is wrong. That is the stop level you "
        "will honour on paper.\n"
        "2. Size: how many shares or lots so that a full stop is a small, pre-accepted "
        "practice loss — not a number that makes you freeze.\n"
        "3. Time stop: if nothing happens by your clock (for example lunch), you flatten the "
        "paper trade. No 'just five more minutes' without a new reason.\n\n"
        "FOMO check\n"
        "If you want to enter because the candle already ran, skip the paper trade and write "
        "'late' in the journal. Late entries are a separate lesson, not a bigger size.\n\n"
        "Common mistake\n"
        "Moving the paper stop further away after the trade is on, or adding size to 'get "
        "back' a red trade. That is revenge, not risk control.\n\n"
        "Use this only on paper. Live orders are not part of this lesson."
    ),
    "chop": (
        "Midday chop — paper habit (not a buy or sell call)\n\n"
        "What usually happens\n"
        "From late morning into the lunch window, many cash stocks drift in a tight range. "
        "Small candles both ways. That is chop: lots of motion, little follow-through.\n\n"
        "Paper habit\n"
        "1. If your opening-range plan did not trigger by ~11:30 IST, default is stand aside.\n"
        "2. Do not open a new paper trade just because you are bored or a chat group is active.\n"
        "3. If you already have a paper trade and it is going nowhere, use your time stop. "
        "Do not 'fix' it with a second paper trade in the opposite direction.\n\n"
        "Common mistake\n"
        "Revenge trading: the morning paper trade was red, so you force a midday trade to "
        "feel even. That usually adds a second red row to the journal.\n\n"
        "Use this only on paper. Live orders are not part of this lesson."
    ),
    "close": (
        "Close, CAS, and square-off — paper habit (not a buy or sell call)\n\n"
        "What the close is\n"
        "Cash stocks have a closing session (often called CAS) after the continuous market. "
        "The closing print can differ from the last continuous tick. Intraday / MIS-style "
        "paper positions are meant to be flat before that window, not 'hoped through' it.\n\n"
        "Paper habit\n"
        "1. Set a clock alarm ~15 minutes before the cash close. That is your flatten window.\n"
        "2. Square off the paper trade in that window even if you are a little green or red. "
        "The lesson is finishing the day, not squeezing the last tick.\n"
        "3. Write the continuous last vs the official close if they differ. That gap is "
        "normal; it is not a broker error.\n\n"
        "Common mistake\n"
        "Leaving an intraday paper trade open 'just this once' because you are travelling "
        "or the P&L is almost even. Overnight gap risk is a different product and a "
        "different plan.\n\n"
        "Use this only on paper. Live orders are not part of this lesson."
    ),
    "weekend": (
        "Weekend and holiday prep — paper habit (not a buy or sell call)\n\n"
        "What to do when the cash market is shut\n"
        "1. Journal: three lines on last week's paper trades — plan followed, plan broken, "
        "one rule to keep.\n"
        "2. Calendar: next week's holidays, special sessions, and any result dates you "
        "already track. Do not invent news.\n"
        "3. Watchlist hygiene: drop names you cannot explain in one sentence. Add nothing "
        "from a random tip without a written reason.\n"
        "4. One learning task: one annual report page, one fund factsheet, or one F&O "
        "contract spec — not ten new charts.\n\n"
        "Common mistake\n"
        "Building a Monday hit-list from weekend social posts. Monday open is often noisy. "
        "Prep is for rules, not for a shopping list.\n\n"
        "Use this only on paper. Live orders are not part of this lesson."
    ),
    "long_term": (
        "Long-term investing habits — paper / journal only (not a stock pick)\n\n"
        "Start with time, not a ticker\n"
        "Write when you will need the money. Money needed inside about three years usually "
        "does not belong in a concentrated equity story. Match the vehicle to the clock "
        "before you open a chart.\n\n"
        "Paper habit\n"
        "1. Write a simple mix: equity / debt / gold (or cash) in percentages that you can "
        "live with in a bad year. Revisit once a year, not every week.\n"
        "2. Prefer a short list you can explain (index fund, a few businesses you understand) "
        "over a long list of 'hot' names.\n"
        "3. Costs and taxes are part of the journal: expense ratio, brokerage, and when "
        "gains become long-term for your holding. Read the factsheet or broker note; do "
        "not guess.\n"
        "4. SIP or lump-sum is a cash-flow choice. The habit is paying yourself on a "
        "calendar, not timing the perfect dip.\n\n"
        "Common mistake\n"
        "Swapping one large-cap for another every month and calling it investing. That is "
        "trading with a longer label.\n\n"
        "Not SEBI RA advice. Verify filings and factsheets yourself."
    ),
    "mutual_funds": (
        "Mutual funds — paper habit (not a fund recommendation)\n\n"
        "Three words, in plain language\n"
        "NAV is the unit price of the fund for that day — it moves with the holdings, not "
        "because someone 'set' a good price for you.\n"
        "SIP is a standing instruction to buy units on a calendar. It is a habit, not a "
        "guarantee of profit.\n"
        "TER / expense ratio is the annual cost the fund takes from the scheme. Lower is "
        "not automatically better, but ignoring cost is a mistake.\n\n"
        "Paper habit\n"
        "1. Open one factsheet. Write: category, benchmark, TER, and exit load.\n"
        "2. Direct vs regular: regular includes distributor commission in the cost. Same "
        "portfolio, different TER. Pick with eyes open; we do not recommend either.\n"
        "3. Do not chase last year's star category. Write why this category fits your "
        "horizon before you paper-allocate.\n\n"
        "Common mistake\n"
        "Buying a fund because NAV looks 'cheap' (₹10 vs ₹200). Unit price is not a value "
        "score.\n\n"
        "Not SEBI RA advice. Read the scheme document yourself."
    ),
    "ipo": (
        "IPOs — paper habit (not an apply / skip call)\n\n"
        "What an IPO is\n"
        "A company offers shares to the public at a stated price band. Listing day can gap "
        "up or down. Allotment is not guaranteed in a popular issue.\n\n"
        "Paper habit\n"
        "1. Read the DRHP / RHP summary: what the company does, why it wants money "
        "(fresh issue vs offer for sale), and major risks in the risk factors chapter.\n"
        "2. Write one sentence on valuation vs listed peers only if you can name the peers. "
        "If you cannot, you do not have a thesis — skip the paper apply.\n"
        "3. Grey-market talk is gossip. Do not treat it as a number in your journal.\n"
        "4. After listing, a paper 'hold forever' still needs a business reason. Listing "
        "pop is not a research report.\n\n"
        "Common mistake\n"
        "Applying because the issue is 'oversubscribed' or a celebrity is advertising it. "
        "Demand is not the same as a good business at a fair price.\n\n"
        "Not SEBI RA advice. Read the prospectus on the official filing site yourself."
    ),
    "fno": (
        "F&O for beginners — paper habit (not a strategy call)\n\n"
        "Futures vs options, in one breath\n"
        "A future is a contract to buy or sell the index or stock later at a fixed price. "
        "Both sides have symmetric obligation.\n"
        "An option is a right for the buyer and an obligation for the seller. The buyer "
        "pays a premium; the seller collects it and carries the duty.\n\n"
        "Lot size and margin\n"
        "You cannot trade one share in F&O. The exchange sets a lot. Margin is the deposit "
        "blocked to hold the position — it is not your maximum loss on shorts or sold "
        "options.\n\n"
        "Paper habit\n"
        "1. Before any paper F&O line, write: index or stock, expiry date, lot size, and "
        "whether you are the buyer or the seller.\n"
        "2. Intraday-style paper F&O is flattened the same day unless the drill is "
        "explicitly overnight (and you accept gap risk in the journal).\n"
        "3. Sold options and futures can lose more than the premium or the initial margin "
        "you first wrote down.\n\n"
        "Common mistake\n"
        "Treating F&O like a cheaper stock. Leverage makes small index moves large in rupees.\n\n"
        "Use this only on paper. Live F&O is not part of this lesson."
    ),
    "sgb": (
        "Sovereign Gold Bonds — paper habit (not a subscribe call)\n\n"
        "What an SGB is\n"
        "A government bond linked to gold price, issued in tranches. You may get a small "
        "interest credit on the bond, and the principal tracks gold as per the issue terms. "
        "This is not the same as a gold ETF or physical jewellery.\n\n"
        "Paper habit\n"
        "1. Read the current issue circular: tenor, interest, issue price vs advertised "
        "discount, and how you exit (secondary market vs maturity).\n"
        "2. Write why you want gold exposure (diversification vs trading a metal chart). "
        "If the reason is 'gold always goes up', that is not a plan.\n"
        "3. Compare SGB, gold ETF, and digital gold only on cost, tax treatment you verify "
        "yourself, and how easily you can sell. We do not pick a winner.\n\n"
        "Common mistake\n"
        "Buying an SGB tranche because a message said 'last day, guaranteed profit'. Issue "
        "windows close; returns do not come with a guarantee.\n\n"
        "Not SEBI RA advice. Read the official issue terms yourself."
    ),
    "fno_vs": (
        "Futures vs options — paper habit (not a buy or sell call)\n\n"
        "Futures\n"
        "Both sides must perform. Profit and loss move with the index or stock. Margin is "
        "blocked; a fast move against you can demand more margin the same day.\n\n"
        "Options\n"
        "The buyer can let the right expire. The most the buyer can lose is the premium "
        "paid (plus costs). The seller keeps the premium and must perform if assigned — "
        "loss can be much larger than that premium.\n\n"
        "Paper habit\n"
        "Write 'buyer' or 'seller' before you log a paper option. If you cannot explain "
        "assignment in one line, do not paper-sell.\n\n"
        "Common mistake\n"
        "Selling options because 'premium income looks easy' without a max-pain journal "
        "line for a gap opening.\n\n"
        "Use this only on paper."
    ),
    "fno_lot": (
        "Lot size and margin — paper habit (not a size recommendation)\n\n"
        "Lot size\n"
        "F&O trades in fixed bundles set by the exchange. One lot of an index is many units. "
        "Write the rupee move for one lot in words on the sheet before you paper-enter.\n\n"
        "Margin\n"
        "The blocked deposit you see on a broker screen is not your maximum loss. A sold "
        "option or a future can need more money the same day if the index runs.\n\n"
        "Paper habit\n"
        "1. Look up today's lot size for the contract you are studying. Write it down.\n"
        "2. Write a 'what if the index moves about one percent' line in rupees per lot "
        "before you paper-enter.\n"
        "3. If that rupee number is uncomfortable, the lesson is 'too big' — reduce lots "
        "or skip. Do not hide it in 'I will exit fast'.\n\n"
        "Common mistake\n"
        "Using the same lot count you saw in a social video. Their capital is not yours.\n\n"
        "Use this only on paper. Exchange lot sizes change; verify on the official circular."
    ),
    "fno_expiry": (
        "Expiry week — paper habit (not a trade call)\n\n"
        "What changes\n"
        "As expiry gets close, an option's time value usually shrinks if other things stay "
        "similar. That is why a cheap-looking option can still decay. Futures also pin "
        "toward a settlement process; the last session can be jumpy.\n\n"
        "Paper habit\n"
        "1. Always write the expiry date on the paper ticket. Same-week vs next-week is a "
        "different drill.\n"
        "2. Long options: if your reason is 'it is cheap this week', add a line that cheap "
        "can become cheaper by Friday.\n"
        "3. Avoid opening a new paper short-option book on expiry day unless the lesson is "
        "specifically 'watch settlement', not 'harvest premium'.\n\n"
        "Common mistake\n"
        "Holding a long call or put into the last hour because it is 'almost there'. Almost "
        "is not ITM at settlement.\n\n"
        "Use this only on paper. Settlement rules are on the exchange; read them yourself."
    ),
}


def resolve_habit_lesson_id(query: str) -> Optional[str]:
    text = (query or "").strip()
    if not text:
        return None
    marked = _HABIT_MARK.search(text)
    if marked:
        key = marked.group(1).lower()
        if key in _LESSONS:
            return key
    for lesson_id, pattern in _HABIT_PATTERNS:
        if pattern.search(text):
            return lesson_id
    return None


_TIP_FALLBACK = re.compile(
    r"teach this paper habit:\s*(.+?)(?:\.?\s*context:|\s*$)",
    re.I | re.S,
)


def _tip_fallback_lesson(query: str) -> Optional[str]:
    match = _TIP_FALLBACK.search(query or "")
    if not match:
        return None
    title = re.sub(r"\s+", " ", match.group(1)).strip(" .")
    if not title or len(title) > 80:
        return None
    context = ""
    parts = re.split(r"context:\s*", query, maxsplit=1, flags=re.I)
    if len(parts) == 2:
        context = re.split(r"educational|\bnot a\b", parts[1], maxsplit=1, flags=re.I)[0]
        context = re.sub(r"\s+", " ", context).strip(" .")
    lines = [
        f"{title} — paper habit (not a buy or sell call)",
        "",
    ]
    if context:
        lines.extend([context, ""])
    lines.extend(
        [
            "Paper habit",
            "1. Rewrite this tip as one rule you can follow without a stock name.",
            "2. Add a clock or a review date — a habit without a time usually gets skipped.",
            "3. Journal one time you broke the rule and what you will do instead next week.",
            "",
            "Common mistake",
            "Collecting tips and never writing the rule. The card is a reminder, not a trade.",
            "",
            "Use this only on paper. Not SEBI RA advice.",
        ]
    )
    return "\n".join(lines)


def get_habit_lesson(query: str) -> Optional[str]:
    lesson_id = resolve_habit_lesson_id(query)
    if lesson_id:
        return _LESSONS[lesson_id]
    return _tip_fallback_lesson(query)
