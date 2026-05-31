#!/usr/bin/env python3
"""
BYSEL Tier Verification Tool - Simple All-In-One Diagnostic
Run this to test all tiers and generate a report
"""

import requests
import json
from datetime import datetime

class Colors:
    GREEN = '\033[92m'
    RED = '\033[91m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    END = '\033[0m'
    BOLD = '\033[1m'

def print_success(msg):
    print(f"{Colors.GREEN}✅ {msg}{Colors.END}")

def print_error(msg):
    print(f"{Colors.RED}❌ {msg}{Colors.END}")

def print_warning(msg):
    print(f"{Colors.YELLOW}⚠️  {msg}{Colors.END}")

def print_info(msg):
    print(f"{Colors.BLUE}ℹ️  {msg}{Colors.END}")

def print_header(msg):
    print(f"\n{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.END}")
    print(f"{Colors.BOLD}{msg}{Colors.END}")
    print(f"{Colors.BOLD}{Colors.BLUE}{'='*60}{Colors.END}\n")

def test_tier(query: str, tier: str, base_url: str) -> dict:
    """Test a single tier and return response details."""
    payload = {
        "query": query,
        "tier": tier,
        "conversation_history": []
    }

    try:
        response = requests.post(
            f"{base_url}/api/ai/ask",
            json=payload,
            timeout=15
        )

        if response.status_code == 200:
            result = response.json()
            return {
                "success": True,
                "source": result.get('source'),
                "tier_requested": result.get('tier_requested'),
                "answer": result.get('answer', ''),
                "answer_length": len(result.get('answer', '')),
            }
        else:
            return {
                "success": False,
                "error": f"HTTP {response.status_code}",
                "details": response.text[:200]
            }
    except requests.Timeout:
        return {"success": False, "error": "Request timeout (>15s)"}
    except Exception as e:
        return {"success": False, "error": str(e)[:100]}

def main():
    # Configuration
    BASE_URL = "https://bysel-backend.onrender.com"
    QUERY = "Analyze WIPRO"
    TIERS = ["groq", "gemini", "indian-stock-llm", "rule-engine"]

    print_header(f"BYSEL AI Tier Verification Report")
    print_info(f"Time: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print_info(f"Base URL: {BASE_URL}")
    print_info(f"Test Query: '{QUERY}'")

    # Store results
    results = {}

    # Test each tier
    for tier in TIERS:
        print_info(f"Testing tier: {tier}...")
        result = test_tier(QUERY, tier, BASE_URL)
        results[tier] = result

        if result["success"]:
            print_success(f"{tier}: Got response from {result['source']} ({result['answer_length']} chars)")
        else:
            print_error(f"{tier}: {result.get('error', 'Unknown error')}")

    # Test auto mode
    print_info("Testing auto mode (no tier specified)...")
    result = test_tier(QUERY, None, BASE_URL)
    if result["success"]:
        source_used = result.get('source', 'unknown')
        print_success(f"Auto mode: Fell back to {source_used}")
    else:
        print_error(f"Auto mode: {result.get('error', 'Unknown error')}")

    # Summary
    print_header("SUMMARY")

    working_tiers = [t for t, r in results.items() if r["success"]]
    failed_tiers = [t for t, r in results.items() if not r["success"]]

    print_info(f"Working tiers: {len(working_tiers)}/{len(TIERS)}")
    for tier in working_tiers:
        source = results[tier]['source']
        length = results[tier]['answer_length']
        print_success(f"  {tier:20} → {source:20} ({length} chars)")

    if failed_tiers:
        print_warning(f"Failed tiers: {len(failed_tiers)}/{len(TIERS)}")
        for tier in failed_tiers:
            error = results[tier].get('error', 'Unknown')
            print_error(f"  {tier:20} → {error}")

    # Diagnosis
    print_header("DIAGNOSIS")

    if results["gemini"]["success"]:
        print_success("Gemini is working! The async/sync bug appears to be FIXED.")
        print_info("Gemini should now respond in auto mode if Groq is unavailable.")
    elif "not available" in str(results["gemini"]).lower():
        print_warning("Gemini reports 'not available' - API key might be missing")
        print_info("Action: Add GEMINI_API_KEY to Render environment variables")
    else:
        print_error(f"Gemini failed: {results['gemini'].get('error')}")
        print_info("Action: Check Render logs for error details")

    if results["groq"]["success"]:
        print_success("Groq is working as Tier 1 (primary)")

    if results["rule-engine"]["success"]:
        print_success("Rule Engine is working as fallback (always available)")

    # Final recommendation
    print_header("NEXT STEPS")

    if all(results[t]["success"] for t in TIERS):
        print_success("✨ All tiers working! System is configured correctly.")
        print_info("The 'random information' issue should now be resolved.")
        print_info("Test in the mobile app to confirm responses are clean.")
    else:
        print_warning("Some tiers are not working. See above for details.")
        print_info("Check Render environment variables and logs:")
        print_info(f"  https://dashboard.render.com/bysel-backend")

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print_error("\nTest interrupted by user")
    except Exception as e:
        print_error(f"Unexpected error: {str(e)}")
