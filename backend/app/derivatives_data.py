"""Derivatives market data: NSE live option/futures chains with synthetic fallback.

NSE India often blocks datacenter IPs; callers must tolerate empty responses and
fall back to educational synthetic chains. PCR / IV-skew are computed for both.
"""
from __future__ import annotations

import logging
import re
from datetime import date, datetime, timedelta
from math import exp, log, sqrt
from typing import Any, Optional

import requests

logger = logging.getLogger(__name__)

_INDEX_SYMBOLS = {"NIFTY", "BANKNIFTY", "FINNIFTY", "MIDCPNIFTY", "NIFTYNXT50"}
_NSE_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)


def _normal_cdf(x: float) -> float:
    return 0.5 * (1.0 + _erf(x / sqrt(2.0)))


def _normal_pdf(x: float) -> float:
    return exp(-0.5 * x * x) / sqrt(2.0 * 3.141592653589793)


def _erf(x: float) -> float:
    # Abramowitz & Stegun approximation
    sign = 1.0 if x >= 0 else -1.0
    ax = abs(x)
    t = 1.0 / (1.0 + 0.3275911 * ax)
    a1, a2, a3, a4, a5 = 0.254829592, -0.284496736, 1.421413741, -1.453152027, 1.061405429
    y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-ax * ax)
    return sign * y


def black_scholes_greeks(
    spot: float,
    strike: float,
    time_years: float,
    rate: float,
    iv: float,
) -> dict[str, float]:
    if spot <= 0 or strike <= 0 or time_years <= 0 or iv <= 0:
        return {
            "callPrice": 0.0,
            "putPrice": 0.0,
            "callDelta": 0.0,
            "putDelta": 0.0,
            "gamma": 0.0,
            "theta": 0.0,
            "vega": 0.0,
        }
    sqrt_t = sqrt(time_years)
    d1 = (log(spot / strike) + (rate + 0.5 * iv * iv) * time_years) / (iv * sqrt_t)
    d2 = d1 - iv * sqrt_t
    call_price = spot * _normal_cdf(d1) - strike * exp(-rate * time_years) * _normal_cdf(d2)
    put_price = strike * exp(-rate * time_years) * _normal_cdf(-d2) - spot * _normal_cdf(-d1)
    gamma = _normal_pdf(d1) / (spot * iv * sqrt_t)
    vega = (spot * _normal_pdf(d1) * sqrt_t) / 100.0
    theta = (
        (-spot * _normal_pdf(d1) * iv / (2.0 * sqrt_t))
        - (rate * strike * exp(-rate * time_years) * _normal_cdf(d2))
    ) / 365.0
    return {
        "callPrice": round(max(call_price, 0.01), 2),
        "putPrice": round(max(put_price, 0.01), 2),
        "callDelta": round(_normal_cdf(d1), 4),
        "putDelta": round(_normal_cdf(d1) - 1.0, 4),
        "gamma": round(gamma, 5),
        "theta": round(theta, 4),
        "vega": round(vega, 4),
    }


def _nse_session() -> requests.Session:
    session = requests.Session()
    session.headers.update(
        {
            "User-Agent": _NSE_UA,
            "Accept": "application/json,text/plain,*/*",
            "Accept-Language": "en-US,en;q=0.9",
            "Referer": "https://www.nseindia.com/option-chain",
            "Connection": "keep-alive",
        }
    )
    try:
        session.get("https://www.nseindia.com", timeout=8)
        session.get("https://www.nseindia.com/option-chain", timeout=8)
    except Exception as exc:
        logger.debug("nse cookie warm failed: %s", exc)
    return session


def _normalize_expiry_iso(raw: str) -> str:
    """Convert NSE '27-Mar-2025' / ISO / other → YYYY-MM-DD when possible."""
    text = (raw or "").strip()
    if not text:
        return ""
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
        return text
    for fmt in ("%d-%b-%Y", "%d-%b-%y", "%d %b %Y", "%Y-%m-%d"):
        try:
            return datetime.strptime(text, fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue
    return text


def _pick_expiry(available: list[str], wanted: str) -> Optional[str]:
    if not available:
        return None
    wanted_iso = _normalize_expiry_iso(wanted)
    for exp in available:
        if _normalize_expiry_iso(exp) == wanted_iso or exp == wanted:
            return exp
    # Nearest forward expiry
    today = datetime.utcnow().date()
    dated: list[tuple[date, str]] = []
    for exp in available:
        iso = _normalize_expiry_iso(exp)
        try:
            d = datetime.strptime(iso, "%Y-%m-%d").date()
        except ValueError:
            continue
        if d >= today:
            dated.append((d, exp))
    if dated:
        dated.sort(key=lambda x: x[0])
        return dated[0][1]
    return available[0]


def compute_chain_metrics(contracts: list[dict[str, Any]], spot: float) -> dict[str, Any]:
    """PCR (OI) + IV skew from strike rows."""
    call_oi = sum(int(c.get("callOi") or 0) for c in contracts)
    put_oi = sum(int(c.get("putOi") or 0) for c in contracts)
    pcr = round(put_oi / call_oi, 3) if call_oi > 0 else None

    if not contracts or spot <= 0:
        return {"pcr": pcr, "ivSkew": None, "atmIv": None}

    atm = min(contracts, key=lambda c: abs(float(c.get("strike") or 0.0) - spot))
    atm_iv = float(atm.get("impliedVolatility") or 0.0) or None

    # Prefer OTM put (~spot-1 step) vs OTM call (~spot+1 step) IV when present.
    below = [c for c in contracts if float(c.get("strike") or 0) < spot]
    above = [c for c in contracts if float(c.get("strike") or 0) > spot]
    put_row = max(below, key=lambda c: float(c.get("strike") or 0)) if below else atm
    call_row = min(above, key=lambda c: float(c.get("strike") or 0)) if above else atm
    put_iv = float(put_row.get("putIv") or put_row.get("impliedVolatility") or 0.0)
    call_iv = float(call_row.get("callIv") or call_row.get("impliedVolatility") or 0.0)
    iv_skew = round(put_iv - call_iv, 4) if (put_iv > 0 and call_iv > 0) else None

    return {"pcr": pcr, "ivSkew": iv_skew, "atmIv": round(atm_iv, 4) if atm_iv else None}


def _map_nse_rows(
    rows: list[dict[str, Any]],
    expiry_raw: str,
    spot: float,
) -> list[dict[str, Any]]:
    expiry_iso = _normalize_expiry_iso(expiry_raw)
    out: list[dict[str, Any]] = []
    try:
        exp_dt = datetime.strptime(expiry_iso, "%Y-%m-%d")
        time_years = max(1 / 365.0, (exp_dt - datetime.utcnow()).days / 365.0)
    except ValueError:
        time_years = 21 / 365.0

    for row in rows:
        try:
            strike = float(row.get("strikePrice") or 0.0)
        except (TypeError, ValueError):
            continue
        if strike <= 0:
            continue
        ce = row.get("CE") if isinstance(row.get("CE"), dict) else {}
        pe = row.get("PE") if isinstance(row.get("PE"), dict) else {}
        # Filter to selected expiry when row carries expiryDate.
        row_exp = row.get("expiryDate") or ce.get("expiryDate") or pe.get("expiryDate")
        if row_exp and _normalize_expiry_iso(str(row_exp)) != expiry_iso:
            # Some payloads already filtered; keep if missing.
            if _normalize_expiry_iso(str(row_exp)) not in {"", expiry_iso}:
                continue

        call_iv = float(ce.get("impliedVolatility") or 0.0) / (
            100.0 if float(ce.get("impliedVolatility") or 0.0) > 1.5 else 1.0
        )
        put_iv = float(pe.get("impliedVolatility") or 0.0) / (
            100.0 if float(pe.get("impliedVolatility") or 0.0) > 1.5 else 1.0
        )
        iv = call_iv if call_iv > 0 else put_iv
        if iv <= 0:
            iv = 0.22
        greeks = black_scholes_greeks(spot, strike, time_years, 0.065, iv)
        # Prefer NSE greeks when present.
        call_delta = float(ce.get("delta") or greeks["callDelta"])
        put_delta = float(pe.get("delta") or greeks["putDelta"])
        gamma = float(ce.get("gamma") or pe.get("gamma") or greeks["gamma"])
        theta = float(ce.get("theta") or pe.get("theta") or greeks["theta"])
        vega = float(ce.get("vega") or pe.get("vega") or greeks["vega"])

        out.append(
            {
                "strike": strike,
                "callLtp": float(ce.get("lastPrice") or greeks["callPrice"] or 0.0),
                "putLtp": float(pe.get("lastPrice") or greeks["putPrice"] or 0.0),
                "callOi": int(ce.get("openInterest") or 0),
                "putOi": int(pe.get("openInterest") or 0),
                "callOiChange": int(ce.get("changeinOpenInterest") or 0),
                "putOiChange": int(pe.get("changeinOpenInterest") or 0),
                "impliedVolatility": round(iv, 4),
                "callIv": round(call_iv, 4) if call_iv > 0 else round(iv, 4),
                "putIv": round(put_iv, 4) if put_iv > 0 else round(iv, 4),
                "callDelta": round(call_delta, 4),
                "putDelta": round(put_delta, 4),
                "gamma": round(gamma, 5),
                "theta": round(theta, 4),
                "vega": round(vega, 4),
            }
        )
    out.sort(key=lambda c: c["strike"])
    return out


def fetch_nse_option_chain(symbol: str, expiry: str) -> Optional[dict[str, Any]]:
    """Fetch live NSE option chain. Returns None on failure."""
    sym = (symbol or "").strip().upper()
    if not sym:
        return None

    # 1) nsepython helper
    try:
        import nsepython as nse

        raw = nse.nse_optionchain_scrapper(sym)
        if isinstance(raw, dict) and (raw.get("records") or {}).get("data"):
            mapped = _parse_nse_payload(raw, sym, expiry)
            if mapped and mapped.get("contracts"):
                mapped["source"] = "nse"
                return mapped
    except Exception as exc:
        logger.debug("nsepython option chain failed for %s: %s", sym, exc)

    # 2) Direct NSE REST
    try:
        session = _nse_session()
        if sym in _INDEX_SYMBOLS:
            url = f"https://www.nseindia.com/api/option-chain-indices?symbol={sym}"
        else:
            url = f"https://www.nseindia.com/api/option-chain-equities?symbol={sym}"
        resp = session.get(url, timeout=12)
        if resp.status_code == 200:
            raw = resp.json()
            mapped = _parse_nse_payload(raw, sym, expiry)
            if mapped and mapped.get("contracts"):
                mapped["source"] = "nse"
                return mapped
        logger.debug("NSE OC HTTP %s for %s", resp.status_code, sym)
    except Exception as exc:
        logger.debug("NSE OC direct failed for %s: %s", sym, exc)

    return None


def _parse_nse_payload(raw: dict[str, Any], symbol: str, expiry: str) -> Optional[dict[str, Any]]:
    records = raw.get("records") if isinstance(raw, dict) else None
    if not isinstance(records, dict):
        return None
    expiries = [str(x) for x in (records.get("expiryDates") or []) if x]
    chosen = _pick_expiry(expiries, expiry)
    if not chosen:
        return None
    spot = float((records.get("underlyingValue") or 0.0) or 0.0)
    rows = list(records.get("data") or [])
    # Prefer filtered data for chosen expiry when available under filtered.
    filtered = raw.get("filtered") if isinstance(raw.get("filtered"), dict) else {}
    if isinstance(filtered.get("data"), list) and filtered.get("data"):
        # Only use filtered when expiry matches nearest/default; else filter records.
        pass
    contracts = _map_nse_rows(rows, chosen, spot)
    if not contracts:
        return None
    # Keep strikes near ATM for UI density.
    contracts_sorted = sorted(contracts, key=lambda c: abs(c["strike"] - spot))
    near = sorted(contracts_sorted[:24], key=lambda c: c["strike"])
    metrics = compute_chain_metrics(near, spot)
    return {
        "symbol": symbol,
        "expiry": _normalize_expiry_iso(chosen),
        "spot": round(spot, 2),
        "generatedAt": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "contracts": near,
        "pcr": metrics.get("pcr"),
        "ivSkew": metrics.get("ivSkew"),
        "atmIv": metrics.get("atmIv"),
        "notes": ["Live NSE option chain (best-effort). Validate with your broker before trading."],
    }


def fetch_nse_futures_contracts(symbol: str, spot: float, lot_size: int) -> Optional[dict[str, Any]]:
    """Best-effort NSE equity futures quotes via nse_quote / nse_fno."""
    sym = (symbol or "").strip().upper()
    if not sym:
        return None
    contracts: list[dict[str, Any]] = []
    try:
        import nsepython as nse

        # Equity futures often appear on quote payload under "futures" / "stocks".
        quote = None
        for fn_name in ("nse_quote", "nse_eq", "nse_fno"):
            fn = getattr(nse, fn_name, None)
            if not callable(fn):
                continue
            try:
                quote = fn(sym)
                if quote:
                    break
            except Exception:
                continue
        if not isinstance(quote, dict):
            return None

        # Common shapes: {"futures":[...]} or nested market dept lists.
        fut_rows = []
        for key in ("futures", "fut", "stockFutures", "data"):
            val = quote.get(key)
            if isinstance(val, list) and val:
                fut_rows = val
                break
        if not fut_rows and isinstance(quote.get("underlyingValue"), (int, float)):
            # Single-leg style — synthesize near month from live underlying.
            return None

        for idx, row in enumerate(fut_rows[:6]):
            if not isinstance(row, dict):
                continue
            last = float(row.get("lastPrice") or row.get("ltp") or row.get("last") or 0.0)
            if last <= 0:
                continue
            expiry_raw = str(row.get("expiryDate") or row.get("expiry") or "")
            expiry = _normalize_expiry_iso(expiry_raw)
            if not expiry:
                expiry = (datetime.utcnow().date() + timedelta(days=7 * (idx + 1))).strftime("%Y-%m-%d")
            oi = int(row.get("openInterest") or row.get("oi") or 0)
            oi_chg = int(row.get("changeinOpenInterest") or row.get("oiChange") or 0)
            volume = int(row.get("totalTradedVolume") or row.get("volume") or 0)
            pct = float(row.get("pChange") or row.get("pctChange") or 0.0)
            basis = round(last - spot, 2) if spot > 0 else 0.0
            margin_pct = 0.15
            contracts.append(
                {
                    "contractSymbol": str(row.get("identifier") or f"{sym}-{expiry}-FUT"),
                    "expiry": expiry,
                    "lotSize": int(row.get("marketLot") or lot_size),
                    "last": round(last, 2),
                    "pctChange": round(pct, 2),
                    "oi": oi,
                    "oiChange": oi_chg,
                    "volume": volume,
                    "basis": basis,
                    "marginPct": margin_pct,
                    "marginPerLot": round(last * int(row.get("marketLot") or lot_size) * margin_pct, 2),
                }
            )
    except Exception as exc:
        logger.debug("NSE futures fetch failed for %s: %s", sym, exc)
        return None

    if not contracts:
        return None
    return {
        "symbol": sym,
        "spot": round(spot, 2),
        "generatedAt": datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ"),
        "contracts": contracts,
        "source": "nse",
        "notes": [
            "Live NSE futures snapshot (best-effort).",
            "Margin is indicative — broker SPAN/ELM may differ.",
        ],
    }
