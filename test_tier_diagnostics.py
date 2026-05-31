#!/usr/bin/env python3
"""
Diagnostic script to test each LLM tier independently.
Tests with explicit tier selection to isolate which one is problematic.
"""

import requests
import json
from typing import Dict

BASE_URL = "https://bysel-backend.onrender.com"  # Change to localhost:8000 for local testing

def test_tier(query: str, tier: str) -> Dict:
    """Test a specific tier with explicit tier selection."""
    print(f"\n{'='*60}")
    print(f"Testing: Query='{query}', Tier='{tier}'")
    print(f"{'='*60}")

    payload = {
        "query": query,
        "tier": tier,
        "conversation_history": []
    }

    try:
        response = requests.post(
            f"{BASE_URL}/api/ai/ask",
            json=payload,
            timeout=30
        )

        if response.status_code == 200:
            result = response.json()
            print(f"Status: ✅ Success (200)")
            print(f"Source: {result.get('source', 'N/A')}")
            print(f"Tier Requested: {result.get('tier_requested', 'N/A')}")
            print(f"\nAnswer Preview (first 300 chars):")
            answer = result.get('answer', 'NO ANSWER')
            print(f"{answer[:300]}")
            if len(answer) > 300:
                print(f"... [truncated, {len(answer)} total chars]")
            return result
        else:
            print(f"Status: ❌ Error ({response.status_code})")
            print(f"Response: {response.text}")
            return {"error": response.text}
    except Exception as e:
        print(f"Status: ❌ Exception: {str(e)}")
        return {"error": str(e)}

def main():
    # Test queries
    test_queries = [
        "Analyze WIPRO",
        "Should I buy RELIANCE",
        "What's the P/E ratio of TCS",
    ]

    # Test tiers
    test_tiers = [
        "groq",
        "gemini",
        "indian-stock-llm",
        "rule-engine",
        "auto"  # Default fallback
    ]

    results = {}

    for query in test_queries:
        results[query] = {}

        for tier in test_tiers:
            tier_result = test_tier(query, tier)
            results[query][tier] = {
                "source": tier_result.get("source"),
                "answer_length": len(tier_result.get("answer", "")),
                "has_error": "error" in tier_result or tier_result.get("source") == "none"
            }

    # Summary Report
    print(f"\n\n{'='*60}")
    print("DIAGNOSTIC SUMMARY")
    print(f"{'='*60}")

    for query in test_queries:
        print(f"\nQuery: '{query}'")
        for tier in test_tiers:
            result = results[query][tier]
            status = "❌ ERROR" if result["has_error"] else "✅ OK"
            print(f"  {tier:20} {status:10} Source: {result['source']:15} Length: {result['answer_length']:5}")

if __name__ == "__main__":
    print("BYSEL LLM Tier Diagnostic Tool")
    print("Testing each tier independently to identify which is returning poor responses\n")
    main()
