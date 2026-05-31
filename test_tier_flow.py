#!/usr/bin/env python3
"""Test the full tier fallback flow."""

import sys
import asyncio
from pathlib import Path

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

sys.path.insert(0, str(Path(__file__).parent / "backend"))

async def test_full_flow():
    """Test the complete Tier 1/2/3 fallback flow."""

    print("=" * 70)
    print("FULL TIER FALLBACK TEST: 'Analyze WIPRO'")
    print("=" * 70)

    # Simulate what routes/__init__.py does
    from app.ai_engine import ai_assistant
    from app.llm_integration import llm_available, ask_llm
    from app.groq_llm import groq_available

    query = "Analyze WIPRO"

    # Tier 3: Always run first (baseline)
    print("\n[TIER 3] Rule-Engine Baseline:")
    print("-" * 70)
    rule_result = ai_assistant(query)
    print(f"Answer: {rule_result.get('answer', 'N/A')[:150]}...")
    print(f"Source: rule-engine")

    # Tier 1: Groq (would be used in Render with API key)
    print("\n[TIER 1] Groq Status:")
    print(f"Available: {groq_available()}")
    if not groq_available():
        print("(Not available locally, but IS available in Render with GROQ_API_KEY)")

    # Tier 2: Indian Stock LLM
    print("\n[TIER 2] Indian Stock LLM:")
    print("-" * 70)
    if llm_available():
        llm_result = ask_llm(query)
        confidence = llm_result.get("confidence", 0) if llm_result else 0
        print(f"Confidence: {confidence}")
        print(f"Answer: {llm_result.get('answer', 'N/A')[:150]}...")

        if confidence >= 0.4:
            print("\nRESULT: Will use Indian Stock LLM response")
            print(f"Source: indian-stock-llm")
        else:
            print(f"\nRESULT: Confidence {confidence} < 0.4 threshold")
            print("Falling through to Rule-Engine (Tier 3)")
            print(f"Answer: {rule_result.get('answer', 'N/A')[:150]}...")
            print(f"Source: rule-engine")

    print("\n" + "=" * 70)
    print("FINAL RESPONSE TO USER:")
    print("=" * 70)
    print(rule_result.get('answer', 'N/A')[:500])

if __name__ == "__main__":
    asyncio.run(test_full_flow())
