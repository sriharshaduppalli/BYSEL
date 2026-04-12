#!/usr/bin/env python3
"""
BYSEL Release Gate
==================
Runs pre-release SLO checks against the /metrics/slo endpoint.

Usage (against a running local server):
    python backend/scripts/release_gate.py

Usage (against a custom URL):
    python backend/scripts/release_gate.py --base-url http://staging.bysel.internal:8000

Usage (skip if minimum sample count not reached, useful for short soak tests):
    python backend/scripts/release_gate.py --min-samples 50

Exit codes:
    0  All checks PASSED (or skipped due to insufficient data)
    1  One or more checks FAILED
    2  Could not reach the backend
"""

import argparse
import json
import sys
import urllib.request
import urllib.error

# ---------------------------------------------------------------------------
# SLO thresholds (mirrors targets block in /metrics/slo response)
# ---------------------------------------------------------------------------
ORDER_SUCCESS_RATE_MIN_PCT: float = 99.5   # order error rate must be <= 0.5 %
HTTP_P95_LATENCY_MAX_MS: float = 300.0     # p95 HTTP latency must be <= 300 ms
STREAM_ERROR_RATE_MAX_PCT: float = 1.0     # quote stream send-error rate <= 1 %


# ---------------------------------------------------------------------------
# Core evaluation logic (pure – no I/O so it can be unit-tested directly)
# ---------------------------------------------------------------------------

def evaluate_slo_response(payload: dict, min_samples: int = 10) -> tuple[bool, list[dict]]:
    """Evaluate a /metrics/slo response dict against hardcoded thresholds.

    Returns:
        (all_critical_passed, results)

    where *results* is a list of dicts:
        {
            "check":   str,          # human label
            "status":  "PASS"|"FAIL"|"SKIP",
            "detail":  str,          # one-line explanation
        }
    """
    results: list[dict] = []
    all_passed = True

    slo = payload.get("slo", {})
    order_req = slo.get("orderRequests", {})
    http = slo.get("http", {})
    stream = slo.get("quotesStream", {})

    # ------------------------------------------------------------------
    # 1. Order success rate
    # ------------------------------------------------------------------
    order_total = int(order_req.get("totalRequests", 0))
    order_error_pct = float(order_req.get("errorRatePct") or 0.0)
    order_success_pct = 100.0 - order_error_pct
    threshold_error_pct = 100.0 - ORDER_SUCCESS_RATE_MIN_PCT

    if order_total < min_samples:
        results.append({
            "check": "Order success rate",
            "status": "SKIP",
            "detail": (
                f"Only {order_total} order samples (need >= {min_samples}); "
                "metric not meaningful yet"
            ),
        })
    elif order_error_pct > threshold_error_pct:
        all_passed = False
        results.append({
            "check": "Order success rate",
            "status": "FAIL",
            "detail": (
                f"Success rate {order_success_pct:.2f}% < required "
                f"{ORDER_SUCCESS_RATE_MIN_PCT}% "
                f"(error rate {order_error_pct:.2f}% over {order_total} requests)"
            ),
        })
    else:
        results.append({
            "check": "Order success rate",
            "status": "PASS",
            "detail": (
                f"Success rate {order_success_pct:.2f}% >= {ORDER_SUCCESS_RATE_MIN_PCT}% "
                f"over {order_total} requests"
            ),
        })

    # ------------------------------------------------------------------
    # 2. HTTP p95 latency
    # ------------------------------------------------------------------
    http_total = int(http.get("totalRequests", 0))
    latency_ms = http.get("latencyMs", {})
    p95 = float(latency_ms.get("p95") or 0.0) if isinstance(latency_ms, dict) else 0.0

    if http_total < min_samples:
        results.append({
            "check": "HTTP p95 latency",
            "status": "SKIP",
            "detail": (
                f"Only {http_total} HTTP samples (need >= {min_samples}); "
                "metric not meaningful yet"
            ),
        })
    elif p95 > HTTP_P95_LATENCY_MAX_MS:
        all_passed = False
        results.append({
            "check": "HTTP p95 latency",
            "status": "FAIL",
            "detail": (
                f"p95 {p95:.1f} ms > threshold {HTTP_P95_LATENCY_MAX_MS:.0f} ms "
                f"over {http_total} samples"
            ),
        })
    else:
        results.append({
            "check": "HTTP p95 latency",
            "status": "PASS",
            "detail": (
                f"p95 {p95:.1f} ms <= {HTTP_P95_LATENCY_MAX_MS:.0f} ms "
                f"over {http_total} samples"
            ),
        })

    # ------------------------------------------------------------------
    # 3. Quote stream send-error rate
    # ------------------------------------------------------------------
    stream_total = int(stream.get("messagesSent", 0)) + int(stream.get("sendErrors", 0))
    stream_error_pct = float(stream.get("errorRatePct") or 0.0)

    if stream_total < min_samples:
        results.append({
            "check": "Quote stream error rate",
            "status": "SKIP",
            "detail": (
                f"Only {stream_total} stream samples (need >= {min_samples}); "
                "metric not meaningful yet"
            ),
        })
    elif stream_error_pct > STREAM_ERROR_RATE_MAX_PCT:
        all_passed = False
        results.append({
            "check": "Quote stream error rate",
            "status": "FAIL",
            "detail": (
                f"Stream error rate {stream_error_pct:.2f}% > threshold "
                f"{STREAM_ERROR_RATE_MAX_PCT:.1f}% "
                f"over {stream_total} samples"
            ),
        })
    else:
        results.append({
            "check": "Quote stream error rate",
            "status": "PASS",
            "detail": (
                f"Stream error rate {stream_error_pct:.2f}% <= "
                f"{STREAM_ERROR_RATE_MAX_PCT:.1f}% "
                f"over {stream_total} samples"
            ),
        })

    # ------------------------------------------------------------------
    # 4. Crash-free sessions (requires Crashlytics; always SKIP from backend)
    # ------------------------------------------------------------------
    results.append({
        "check": "Crash-free sessions",
        "status": "SKIP",
        "detail": (
            "Requires Firebase Crashlytics integration; "
            f"target >= {slo.get('targets', {}).get('crashFreeSessionsMinPct', 99.8)}%. "
            "Verify manually in Firebase console before release."
        ),
    })

    return all_passed, results


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def _fetch_slo(base_url: str, timeout: int) -> dict:
    url = base_url.rstrip("/") + "/metrics/slo"
    req = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def _print_report(results: list[dict], all_passed: bool) -> None:
    width = 60
    print()
    print("=" * width)
    print("  BYSEL Release Gate Report")
    print("=" * width)
    for r in results:
        icon = {"PASS": "✓", "FAIL": "✗", "SKIP": "~"}.get(r["status"], "?")
        print(f"  {icon} [{r['status']:4s}] {r['check']}")
        print(f"         {r['detail']}")
    print("-" * width)
    verdict = "GATE PASSED — safe to release" if all_passed else "GATE FAILED — do not release"
    print(f"  {verdict}")
    print("=" * width)
    print()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="BYSEL release gate — checks SLO thresholds before releasing."
    )
    parser.add_argument(
        "--base-url",
        default="http://localhost:8000",
        help="Base URL of the BYSEL backend (default: http://localhost:8000)",
    )
    parser.add_argument(
        "--min-samples",
        type=int,
        default=10,
        help="Minimum sample count for a check to be considered (default: 10)",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=10,
        help="HTTP request timeout in seconds (default: 10)",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        dest="output_json",
        help="Output the results as JSON instead of a human-readable report",
    )
    args = parser.parse_args(argv)

    try:
        payload = _fetch_slo(args.base_url, args.timeout)
    except urllib.error.URLError as exc:
        print(f"ERROR: Could not reach backend at {args.base_url}: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:  # noqa: BLE001
        print(f"ERROR: Unexpected error fetching SLO metrics: {exc}", file=sys.stderr)
        return 2

    all_passed, results = evaluate_slo_response(payload, min_samples=args.min_samples)

    if args.output_json:
        print(json.dumps({"passed": all_passed, "checks": results}, indent=2))
    else:
        _print_report(results, all_passed)

    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
