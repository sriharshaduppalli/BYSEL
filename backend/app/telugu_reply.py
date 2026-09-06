"""Polish ISM answers so Telugu / Tenglish users get a usable Telugu card.

Composer still writes English. The ISM phrase table translates headings. This
layer fixes the leftover problems users actually see: a BUY lead on a HOLD
plan, the English rewrite leaked as "Your ask", and common leftover sentences.
"""
from __future__ import annotations

import re

_LEAD_RE = re.compile(
    r"^\*\*\u0c24\u0c46\u0c32\u0c41\u0c17\u0c41 \u0c38\u0c3e\u0c30\u0c3e\u0c02\u0c36\u0c02:\*\*[^\n]*\n+",
)

_SUGGEST_TE: tuple[tuple[str, str], ...] = (
    (r"(?i)^Technical analysis of ([A-Z0-9.&-]+)$", r"\1 టెక్నికల్ అనాలిసిస్"),
    (r"(?i)^Latest news on ([A-Z0-9.&-]+)$", r"\1 వార్తలు"),
    (r"(?i)^Support and resistance for ([A-Z0-9.&-]+)$", r"\1 సపోర్ట్ / రెసిస్టెన్స్"),
    (r"(?i)^What are risks in ([A-Z0-9.&-]+) right now\??$", r"\1 లో ఇప్పుడు రిస్క్ ఏమిటి?"),
    (r"(?i)^Should I buy (NIFTY50|NIFTY|SENSEX|BANKNIFTY|NIFTYIT|NIFTYBANK)\??$", r"\1 ఎలా ఉంది?"),
    (r"(?i)^Should I buy ([A-Z0-9.&-]+)\??$", r"\1 కొనాలా?"),
    (r"(?i)^How is ([A-Z0-9.&-]+) now\??$", r"\1 ఎలా ఉంది?"),
    (r"(?i)^What is the price of ([A-Z0-9.&-]+)\??$", r"\1 ధర ఎంత?"),
    (r"(?i)^What is fair value for ([A-Z0-9.&-]+)\??$", r"\1 fair value ఎంత?"),
    (r"(?i)^What is ([A-Z0-9.&-]+) PE\??$", r"\1 P/E ఎంత?"),
    (r"(?i)^Predict (NIFTY50|NIFTY|SENSEX|BANKNIFTY|NIFTYIT|NIFTYBANK) price$", r"\1 ఎలా ఉంటుంది?"),
    (r"(?i)^Predict ([A-Z0-9.&-]+) price$", r"\1 ధర ఎలా ఉంటుంది?"),
    (r"(?i)^Analyze ([A-Z0-9.&-]+)$", r"\1 విశ్లేషణ"),
    (r"(?i)^Compare ([A-Z0-9.&-]+) and ([A-Z0-9.&-]+)$", r"\1 మరియు \2 పోల్చండి"),
    (r"(?i)^Compare ([A-Z0-9.&-]+) with ([A-Z0-9.&-]+)$", r"\1 ను \2 తో పోల్చండి"),
    (r"(?i)^Should I wait for a dip in (NIFTY50|NIFTY|SENSEX|BANKNIFTY)\??$", r"\1 dip వస్తుందా?"),
    (r"(?i)^Should I wait for a dip in ([A-Z0-9.&-]+)\??$", r"\1 లో dip కోసం వేచి ఉండాలా?"),
    (r"(?i)^Is (NIFTY50|NIFTY|SENSEX|BANKNIFTY) overvalued\??$", r"\1 valuation ఎలా ఉంది?"),
    (r"(?i)^Is ([A-Z0-9.&-]+) overvalued\??$", r"\1 overvalued ఆ?"),
    (r"(?i)^([A-Z0-9.&-]+) market sentiment$", r"\1 సెంటిమెంట్"),
)


def localize_suggestions(query: str, suggestions: list | None) -> list:
    if not suggestions:
        return []
    try:
        from indian_stock_llm.query_language import is_telugu_query
    except Exception:
        return list(suggestions)
    if not is_telugu_query(query):
        return list(suggestions)
    out: list[str] = []
    for raw in suggestions:
        text = str(raw or "").strip()
        if not text:
            continue
        localized = text
        for pat, repl in _SUGGEST_TE:
            localized = re.sub(pat, repl, localized)
        out.append(localized)
    return out


def polish_telugu_answer(query: str, answer: str) -> str:
    if not answer:
        return answer
    try:
        from indian_stock_llm.query_language import is_telugu_query, localize_assistant_answer
    except Exception:
        return answer
    if not is_telugu_query(query):
        return answer
    localized = localize_assistant_answer(query, answer)
    try:
        from indian_stock_llm.telugu_response import _drop_internal_ask, _stance_from_answer, _STANCE_TE, _LOCALIZED_MARK
    except Exception:
        return localized
    text = _drop_internal_ask(localized)
    try:
        from indian_stock_llm.telugu_response import apply_telugu_leftovers

        text = apply_telugu_leftovers(text)
    except Exception:
        pass
    stance = _stance_from_answer(text)
    stance_te = _STANCE_TE.get(stance or "", "")
    sell_ask = bool(re.search(
        r"(?i)should i sell|\bammala\b|\bammali\b|\u0c05\u0c2e\u0c4d\u0c2e\u0c3e\u0c32\u0c3e",
        query or "",
    ))
    if sell_ask and stance in {"BUY", "ACCUMULATE", "HOLD", "WAIT"}:
        lead = (
            f"**{_LOCALIZED_MARK}:** "
            "\u0c05\u0c2e\u0c4d\u0c2e\u0c15\u0c02 \u0c35\u0c26\u0c4d\u0c26\u0c41 \u2014 "
            f"{stance_te}. "
            "\u0c35\u0c3f\u0c35\u0c30\u0c3e\u0c32\u0c41 \u0c15\u0c3f\u0c02\u0c26 \u0c09\u0c28\u0c4d\u0c28\u0c3e\u0c2f\u0c3f.\n\n"
        )
    elif stance_te:
        lead = (
            f"**{_LOCALIZED_MARK}:** {stance_te} \u2014 "
            "\u0c35\u0c3f\u0c35\u0c30\u0c3e\u0c32\u0c41 \u0c15\u0c3f\u0c02\u0c26 \u0c09\u0c28\u0c4d\u0c28\u0c3e\u0c2f\u0c3f.\n\n"
        )
    else:
        lead = (
            f"**{_LOCALIZED_MARK}:** "
            "\u0c2e\u0c40 \u0c2a\u0c4d\u0c30\u0c36\u0c4d\u0c28\u0c15\u0c41 \u0c38\u0c2e\u0c3e\u0c27\u0c3e\u0c28\u0c02 \u0c15\u0c3f\u0c02\u0c26 \u0c09\u0c02\u0c26\u0c3f.\n\n"
        )
    if _LEAD_RE.search(text):
        text = _LEAD_RE.sub(lead, text, count=1)
    elif not text.startswith(f"**{_LOCALIZED_MARK}:**"):
        text = lead + text
    return re.sub(r"\n{3,}", "\n\n", text).strip() + "\n"


def polish_telugu_result(query: str, result: dict | None) -> dict | None:
    if not result:
        return result
    answer = result.get("answer")
    if not answer:
        return result
    polished = polish_telugu_answer(query, str(answer))
    tips = localize_suggestions(query, result.get("suggestions"))
    if polished == answer and tips == list(result.get("suggestions") or []):
        return result
    out = dict(result)
    if polished != answer:
        out["answer"] = polished
    if tips:
        out["suggestions"] = tips
    return out
