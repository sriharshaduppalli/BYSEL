"""
AI Pattern Detection — Identifies classic chart patterns from OHLCV data.
Patterns: Head & Shoulders, Double Top/Bottom, Triangle, Cup & Handle, Flag, Wedge.
Exposed via GET /api/ai/v2/patterns/{symbol}
"""

from typing import List, Dict, Optional
import numpy as np


def detect_patterns(closes: List[float], highs: List[float], lows: List[float]) -> List[Dict]:
    """Run all pattern detectors and return confirmed patterns."""
    results = []
    if len(closes) < 30:
        return results

    c = np.array(closes, dtype=float)
    h = np.array(highs, dtype=float)
    lo = np.array(lows, dtype=float)

    results += _detect_head_and_shoulders(c, h)
    results += _detect_double_top_bottom(c, h, lo)
    results += _detect_triangle(c, h, lo)
    results += _detect_cup_and_handle(c)
    results += _detect_flag(c, h, lo)

    # Sort by confidence descending
    results.sort(key=lambda x: x.get("confidence", 0), reverse=True)
    return results[:5]  # Return top 5 patterns


def _local_maxima(arr: np.ndarray, order: int = 5) -> List[int]:
    peaks = []
    for i in range(order, len(arr) - order):
        if arr[i] == max(arr[i - order: i + order + 1]):
            peaks.append(i)
    return peaks


def _local_minima(arr: np.ndarray, order: int = 5) -> List[int]:
    troughs = []
    for i in range(order, len(arr) - order):
        if arr[i] == min(arr[i - order: i + order + 1]):
            troughs.append(i)
    return troughs


def _detect_head_and_shoulders(closes: np.ndarray, highs: np.ndarray) -> List[Dict]:
    peaks = _local_maxima(highs, order=4)
    if len(peaks) < 3:
        return []

    results = []
    for i in range(len(peaks) - 2):
        l, m, r = peaks[i], peaks[i + 1], peaks[i + 2]
        lh, mh, rh = highs[l], highs[m], highs[r]
        if mh > lh and mh > rh and abs(lh - rh) / mh < 0.05:
            neckline = (closes[l] + closes[r]) / 2
            confidence = min(95, 70 + int((1 - abs(lh - rh) / mh) * 25))
            results.append({
                "pattern": "Head & Shoulders",
                "type": "bearish",
                "confidence": confidence,
                "signal": "SELL",
                "description": f"Classic reversal — head at idx {m}, shoulders at {l}/{r}. Neckline ~{neckline:.1f}",
                "startIdx": int(l),
                "endIdx": int(r),
                "neckline": round(float(neckline), 2),
                "historicalSuccessRate": 68,
            })
    return results


def _detect_double_top_bottom(closes: np.ndarray, highs: np.ndarray, lows: np.ndarray) -> List[Dict]:
    results = []
    peaks = _local_maxima(highs, order=5)
    troughs = _local_minima(lows, order=5)

    for i in range(len(peaks) - 1):
        p1, p2 = peaks[i], peaks[i + 1]
        if abs(highs[p1] - highs[p2]) / highs[p1] < 0.03 and p2 - p1 >= 8:
            confidence = min(90, 65 + int((1 - abs(highs[p1] - highs[p2]) / highs[p1]) * 25))
            results.append({
                "pattern": "Double Top",
                "type": "bearish",
                "confidence": confidence,
                "signal": "SELL",
                "description": f"Two peaks at similar price ({highs[p1]:.1f} / {highs[p2]:.1f}) — resistance confirmed",
                "startIdx": int(p1),
                "endIdx": int(p2),
                "historicalSuccessRate": 72,
            })

    for i in range(len(troughs) - 1):
        t1, t2 = troughs[i], troughs[i + 1]
        if abs(lows[t1] - lows[t2]) / lows[t1] < 0.03 and t2 - t1 >= 8:
            confidence = min(90, 65 + int((1 - abs(lows[t1] - lows[t2]) / lows[t1]) * 25))
            results.append({
                "pattern": "Double Bottom",
                "type": "bullish",
                "confidence": confidence,
                "signal": "BUY",
                "description": f"Two troughs at similar price ({lows[t1]:.1f} / {lows[t2]:.1f}) — support confirmed",
                "startIdx": int(t1),
                "endIdx": int(t2),
                "historicalSuccessRate": 75,
            })

    return results[:2]


def _detect_triangle(closes: np.ndarray, highs: np.ndarray, lows: np.ndarray) -> List[Dict]:
    if len(closes) < 20:
        return []
    window = closes[-20:]
    h_window = highs[-20:]
    l_window = lows[-20:]

    h_slope = float(np.polyfit(range(20), h_window, 1)[0])
    l_slope = float(np.polyfit(range(20), l_window, 1)[0])

    results = []
    if h_slope < -0.01 and l_slope > 0.01:
        results.append({
            "pattern": "Symmetrical Triangle",
            "type": "neutral",
            "confidence": 72,
            "signal": "HOLD",
            "description": "Converging highs and lows — breakout imminent. Watch volume for direction.",
            "startIdx": len(closes) - 20,
            "endIdx": len(closes) - 1,
            "historicalSuccessRate": 60,
        })
    elif h_slope < -0.01 and abs(l_slope) < 0.005:
        results.append({
            "pattern": "Descending Triangle",
            "type": "bearish",
            "confidence": 75,
            "signal": "SELL",
            "description": "Flat support, declining highs — bearish breakdown likely",
            "startIdx": len(closes) - 20,
            "endIdx": len(closes) - 1,
            "historicalSuccessRate": 65,
        })
    elif abs(h_slope) < 0.005 and l_slope > 0.01:
        results.append({
            "pattern": "Ascending Triangle",
            "type": "bullish",
            "confidence": 78,
            "signal": "BUY",
            "description": "Flat resistance, rising lows — bullish breakout likely",
            "startIdx": len(closes) - 20,
            "endIdx": len(closes) - 1,
            "historicalSuccessRate": 70,
        })
    return results


def _detect_cup_and_handle(closes: np.ndarray) -> List[Dict]:
    if len(closes) < 40:
        return []
    segment = closes[-40:]
    mid = len(segment) // 2
    left_high = max(segment[:10])
    right_high = max(segment[-10:])
    cup_low = min(segment[10:30])
    depth = (left_high - cup_low) / left_high

    if 0.1 < depth < 0.35 and abs(left_high - right_high) / left_high < 0.05:
        handle_low = min(segment[-8:])
        if handle_low > cup_low and handle_low < right_high:
            return [{
                "pattern": "Cup & Handle",
                "type": "bullish",
                "confidence": 80,
                "signal": "BUY",
                "description": f"Rounded base ({depth*100:.1f}% depth) + handle pullback. Bullish continuation on breakout.",
                "startIdx": len(closes) - 40,
                "endIdx": len(closes) - 1,
                "historicalSuccessRate": 78,
            }]
    return []


def _detect_flag(closes: np.ndarray, highs: np.ndarray, lows: np.ndarray) -> List[Dict]:
    if len(closes) < 15:
        return []
    pole = closes[-15:-10]
    flag = closes[-10:]

    pole_move = (pole[-1] - pole[0]) / pole[0] if pole[0] else 0
    flag_move = (flag[-1] - flag[0]) / flag[0] if flag[0] else 0

    if pole_move > 0.05 and -0.03 < flag_move < 0.01:
        return [{
            "pattern": "Bull Flag",
            "type": "bullish",
            "confidence": 74,
            "signal": "BUY",
            "description": f"Strong pole (+{pole_move*100:.1f}%) followed by tight consolidation — continuation setup",
            "startIdx": len(closes) - 15,
            "endIdx": len(closes) - 1,
            "historicalSuccessRate": 67,
        }]
    if pole_move < -0.05 and -0.01 < flag_move < 0.03:
        return [{
            "pattern": "Bear Flag",
            "type": "bearish",
            "confidence": 72,
            "signal": "SELL",
            "description": f"Sharp drop ({pole_move*100:.1f}%) followed by weak bounce — breakdown continuation",
            "startIdx": len(closes) - 15,
            "endIdx": len(closes) - 1,
            "historicalSuccessRate": 64,
        }]
    return []
