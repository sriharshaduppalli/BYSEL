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
    stance = _stance_from_answer(text)
    stance_te = _STANCE_TE.get(stance or "", "")
    if stance_te:
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
    if polished == answer:
        return result
    out = dict(result)
    out["answer"] = polished
    return out
