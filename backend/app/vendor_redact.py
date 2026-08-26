"""User-visible copy must not name market-data vendors."""

import re


def redact_vendor_names_for_display(text: str) -> str:
    """Keep vendor names out of user-visible copy."""
    if not text:
        return text
    out = re.sub(r"(?i)Yahoo\s+Finance", "market data", text)
    out = re.sub(r"(?i)NSE/Yahoo", "live market", out)
    out = re.sub(r"(?i)\bYahoo\b", "market data", out)
    out = re.sub(r"(?i)from available market data fields", "from available fields", out)
    return out
