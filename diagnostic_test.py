#!/usr/bin/env python3
"""Diagnostic test to check which LLM is being used and why."""

import sys
import asyncio
from pathlib import Path

# Fix encoding
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

sys.path.insert(0, str(Path(__file__).parent / "backend"))

async def test_all_tiers():
    """Test each tier independently to diagnose the flow."""

    print("=" * 70)
    print("TIER DIAGNOSTIC TEST")
    print("=" * 70)

    # Tier 3: Rule Engine
    print("\n[TIER 3] Rule Engine:")
    print("-" * 70)
    from app.ai_engine import ai_assistant
    rule_result = ai_assistant("Analyze WIPRO")
    print(f"Rule Engine Answer: {rule_result.get('answer', 'N/A')[:200]}...")
    print(f"Rule Engine has symbol: {rule_result.get('symbol')}")
    print(f"Rule Engine has data: {bool(rule_result.get('data'))}")

    # Tier 2: Indian Stock LLM
    print("\n[TIER 2] Indian Stock LLM:")
    print("-" * 70)
    from app.llm_integration import llm_available, ask_llm
    if llm_available():
        llm_result = ask_llm("Analyze WIPRO")
        if llm_result:
            print(f"LLM available: True")
            print(f"LLM Answer: {llm_result.get('answer', 'N/A')[:200]}...")
            print(f"LLM Confidence: {llm_result.get('confidence')}")
            print(f"LLM Intent: {llm_result.get('intent')}")
        else:
            print(f"LLM returned None")
    else:
        print(f"LLM not available")

    # Tier 1: Groq
    print("\n[TIER 1] Groq:")
    print("-" * 70)
    from app.groq_llm import groq_available, ask_groq, classify_intent, expand_acronyms_in_query
    from app.stock_enricher import normalize_hinglish

    groq_avail = groq_available()
    print(f"Groq available: {groq_avail}")

    if groq_avail:
        try:
            # Simulate the call flow
            expanded_query = expand_acronyms_in_query("Analyze WIPRO")
            normalized_query = normalize_hinglish(expanded_query)
            intent_result = classify_intent(normalized_query)

            print(f"Query after expansion: {expanded_query}")
            print(f"Query after normalization: {normalized_query}")
            print(f"Intent detected: {intent_result.get('intent')}")
            print(f"Intent confidence: {intent_result.get('confidence')}")
            print(f"Intent reasoning: {intent_result.get('reasoning')}")

            # Minimal context for Groq
            minimal_context = {
                "symbol": "WIPRO",
                "current_price": 0,  # Will be empty
                "technical": {},
                "fundamental": {},
                "sentiment": {},
            }

            print("\nCalling Groq...")
            groq_result = await ask_groq(
                normalized_query,
                context=minimal_context,
                conversation_history=None,
                intent_result=intent_result,
            )

            if groq_result.get("answer"):
                print(f"Groq returned answer ({len(groq_result['answer'])} chars)")
                print(f"Answer preview: {groq_result['answer'][:300]}...")
            else:
                print(f"Groq returned empty or no answer field")
                print(f"Full Groq response: {groq_result}")

        except Exception as e:
            print(f"Error calling Groq: {e}")
            import traceback
            traceback.print_exc()
    else:
        print("Groq not available - check GROQ_API_KEY environment variable")

    print("\n" + "=" * 70)
    print("SUMMARY")
    print("=" * 70)
    print(f"Groq Available: {groq_avail}")
    print(f"Indian Stock LLM Available: {llm_available()}")
    print(f"Rule Engine Available: True")

if __name__ == "__main__":
    asyncio.run(test_all_tiers())
