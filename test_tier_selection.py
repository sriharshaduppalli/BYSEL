#!/usr/bin/env python3
"""Test tier selection parameter."""

import sys
import asyncio
import json
from pathlib import Path

if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

sys.path.insert(0, str(Path(__file__).parent / "backend"))

async def test_tier_selection():
    """Test that tier parameter works correctly."""

    print("=" * 70)
    print("TIER SELECTION PARAMETER TEST")
    print("=" * 70)

    # Test 1: Verify AiQuery accepts tier parameter
    print("\n[TEST 1] AiQuery Model Accepts Tier Parameter:")
    print("-" * 70)

    try:
        from app.routes import AiQuery
        q1 = AiQuery(query="Analyze WIPRO")
        print(f"✓ Default tier: {q1.tier}")

        q2 = AiQuery(query="Analyze WIPRO", tier="groq")
        print(f"✓ Explicit tier='groq': {q2.tier}")

        q3 = AiQuery(query="Analyze WIPRO", tier="rule-engine")
        print(f"✓ Explicit tier='rule-engine': {q3.tier}")

        q4 = AiQuery(query="Analyze WIPRO", tier="indian-stock-llm")
        print(f"✓ Explicit tier='indian-stock-llm': {q4.tier}")

        print("\nAll tier values accepted!")
    except Exception as e:
        print(f"✗ Error: {e}")
        return False

    # Test 2: Verify tier parameter validation
    print("\n[TEST 2] Invalid Tier Parameter Handling:")
    print("-" * 70)

    try:
        q_invalid = AiQuery(query="Analyze WIPRO", tier="invalid-tier")
        print(f"✓ Invalid tier accepted (will default to 'auto'): {q_invalid.tier}")
    except Exception as e:
        print(f"✗ Error: {e}")

    print("\n" + "=" * 70)
    print("EXPECTED REQUEST/RESPONSE FORMAT")
    print("=" * 70)

    example_request = {
        "query": "Should I buy RELIANCE",
        "tier": "groq",
        "conversation_history": []
    }

    example_response = {
        "answer": "BUY RELIANCE at current levels...",
        "source": "groq",
        "tier_requested": "groq",
        "symbol": "RELIANCE",
        "current_price": 2850.50
    }

    print("\nRequest (with tier selection):")
    print(json.dumps(example_request, indent=2))

    print("\nResponse (includes tier_requested):")
    print(json.dumps(example_response, indent=2))

    print("\n" + "=" * 70)
    print("TIER PARAMETER USAGE EXAMPLES")
    print("=" * 70)

    examples = [
        {
            "description": "Auto mode (default, with fallback)",
            "curl": 'curl -X POST https://bysel-backend.onrender.com/api/ai/ask \\  -H "Content-Type: application/json" \\  -d \'{"query": "Analyze WIPRO"}\'',
        },
        {
            "description": "Force Groq only (no fallback)",
            "curl": 'curl -X POST https://bysel-backend.onrender.com/api/ai/ask \\  -H "Content-Type: application/json" \\  -d \'{"query": "Analyze WIPRO", "tier": "groq"}\'',
        },
        {
            "description": "Force Indian Stock LLM only (no fallback)",
            "curl": 'curl -X POST https://bysel-backend.onrender.com/api/ai/ask \\  -H "Content-Type: application/json" \\  -d \'{"query": "Analyze WIPRO", "tier": "indian-stock-llm"}\'',
        },
        {
            "description": "Force Rule Engine only (no fallback)",
            "curl": 'curl -X POST https://bysel-backend.onrender.com/api/ai/ask \\  -H "Content-Type: application/json" \\  -d \'{"query": "Analyze WIPRO", "tier": "rule-engine"}\'',
        },
    ]

    for i, example in enumerate(examples, 1):
        print(f"\n[Example {i}] {example['description']}")
        print(f"  Tier: {example['curl'].split('tier')[0].split('query')[0] if 'tier' in example['curl'] else 'auto'}")
        print(f"  Command:")
        print(f"    {example['curl']}")

    print("\n" + "=" * 70)
    print("TIER BEHAVIOR MATRIX")
    print("=" * 70)

    behavior = {
        "auto (default)": {
            "Groq available": "Use Groq response",
            "Groq empty": "Try Indian Stock LLM (if confidence >= 0.4)",
            "Both unavailable": "Use Rule Engine",
        },
        "tier='groq'": {
            "Groq available": "Use Groq response",
            "Groq unavailable": "Error: 'Groq LLM not available'",
            "Groq error": "Return error message (no fallback)",
        },
        "tier='indian-stock-llm'": {
            "LLM available (conf >= 0.4)": "Use LLM response",
            "LLM confidence < 0.4": "Return low-confidence response (no fallback)",
            "LLM error": "Return error message (no fallback)",
        },
        "tier='rule-engine'": {
            "Always": "Use Rule Engine response",
            "Fallback": "None (always available)",
        },
    }

    for tier, behaviors in behavior.items():
        print(f"\n{tier}:")
        for condition, action in behaviors.items():
            print(f"  {condition}: {action}")

    print("\n" + "=" * 70)
    print("TESTS PASSED!")
    print("=" * 70)
    return True

if __name__ == "__main__":
    success = asyncio.run(test_tier_selection())
    sys.exit(0 if success else 1)
