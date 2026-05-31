#!/usr/bin/env python3
"""Test script to verify Indian Stock LLM metadata is removed from user-facing responses."""

import sys
import json
from pathlib import Path

# Fix encoding for Windows
if sys.stdout.encoding != 'utf-8':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')

# Add backend to path
sys.path.insert(0, str(Path(__file__).parent / "backend"))

from app.llm_integration import ask_llm

def test_clean_answer():
    """Test that the answer is clean without metadata headers."""
    print("=" * 70)
    print("Testing: 'Should I buy RELIANCE'")
    print("=" * 70)

    result = ask_llm("Should I buy RELIANCE")

    if not result:
        print("[ERROR] ask_llm returned None")
        return False

    answer = result.get("answer", "")
    print("\n[ANSWER TEXT]:")
    print("-" * 70)
    try:
        print(answer)
    except UnicodeEncodeError:
        print(answer.encode('utf-8', errors='replace').decode('utf-8', errors='replace'))
    print("-" * 70)

    # Check for metadata markers that should NOT appear
    metadata_markers = [
        "Intent detected:",
        "Category:",
        "Latency mode:",
        "Model backend:",
        "Data refresh timestamp:",
        "Data lineage verified:",
        "Stale feeds:",
        "Partial feeds:",
        "Resolved entity:",
        "Relevant market context:",
        "intent-category mapping:",
        "data readiness snapshot:",
        "prediction guardrails:",
        "valuation interpretation:",
    ]

    found_metadata = []
    for marker in metadata_markers:
        if marker.lower() in answer.lower():
            found_metadata.append(marker)

    print("\n[METADATA CHECK]:")
    if found_metadata:
        print("FAILED: Found {} metadata markers:".format(len(found_metadata)))
        for marker in found_metadata:
            print("  - {}".format(marker))
        return False
    else:
        print("PASSED: No metadata markers found in answer")

    # Check for user-facing content
    print("\n[RESPONSE STRUCTURE]:")
    print("  Intent: {}".format(result.get('intent')))
    print("  Confidence: {}".format(result.get('confidence')))
    print("  Category: {}".format(result.get('category')))
    print("  Has Disclaimer: {}".format('Disclaimer' in answer))
    print("  Citations: {} items".format(len(result.get('citations', []))))

    return True

if __name__ == "__main__":
    success = test_clean_answer()
    print("\n" + "=" * 70)
    if success:
        print("PASSED: Answer is clean and user-focused")
        sys.exit(0)
    else:
        print("FAILED: Metadata still present in answer")
        sys.exit(1)
