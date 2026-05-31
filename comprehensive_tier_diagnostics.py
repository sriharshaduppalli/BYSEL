#!/usr/bin/env python3
"""
Comprehensive diagnostic tool for BYSEL LLM tiers.
Outputs a detailed analysis of which tier is responding and quality assessment.
"""

import requests
import json
from datetime import datetime

BASE_URL = "https://bysel-backend.onrender.com"
# For local testing use: BASE_URL = "http://localhost:8000"

class TierDiagnostics:
    def __init__(self):
        self.results = {}
        self.start_time = datetime.now()

    def test_tier(self, query: str, tier: str) -> dict:
        """Test a single tier and return detailed results."""
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
                return {
                    "status": "success",
                    "http_code": 200,
                    "source": result.get('source'),
                    "tier_requested": result.get('tier_requested'),
                    "answer": result.get('answer', ''),
                    "answer_length": len(result.get('answer', '')),
                    "has_error": False,
                    "symbol": result.get('symbol'),
                    "price": result.get('current_price'),
                }
            else:
                return {
                    "status": "http_error",
                    "http_code": response.status_code,
                    "error": response.text[:200],
                    "has_error": True,
                }
        except Exception as e:
            return {
                "status": "exception",
                "http_code": None,
                "error": str(e)[:200],
                "has_error": True,
            }

    def assess_quality(self, answer: str) -> dict:
        """Assess response quality on multiple dimensions."""
        assessment = {
            "total_length": len(answer),
            "is_empty": len(answer.strip()) == 0,
            "looks_like_metadata": False,
            "looks_like_rule_engine": False,
            "looks_like_llm_analysis": False,
            "quality_flags": [],
        }

        lower_answer = answer.lower()

        # Check for metadata indicators
        metadata_patterns = [
            "intent detected",
            "category:",
            "latency mode",
            "model backend",
            "data refresh",
            "data lineage",
            "stale feeds",
            "partial feeds",
            "resolved entity",
            "relevant market context",
            "valuation interpretation",
            "prediction guardrails",
            "signal strength",
        ]

        for pattern in metadata_patterns:
            if pattern in lower_answer:
                assessment["looks_like_metadata"] = True
                assessment["quality_flags"].append(f"Found metadata pattern: {pattern}")
                break

        # Check for rule engine patterns
        rule_engine_patterns = [
            "technical analysis:",
            "rsi =",
            "support 1:",
            "resistance 1:",
            "technical score:",
            "fundamental score:",
            "strong buy",
            "strong sell",
            "score: ",
        ]

        for pattern in rule_engine_patterns:
            if pattern in lower_answer:
                assessment["looks_like_rule_engine"] = True
                break

        # Check for LLM analysis patterns
        llm_patterns = [
            "based on",
            "analysis shows",
            "strong fundamentals",
            "trading at a",
            "p/e ratio",
            "recomm",
            "suggest",
            "appears to be",
            "looking at the",
        ]

        for pattern in llm_patterns:
            if pattern in lower_answer:
                assessment["looks_like_llm_analysis"] = True
                break

        # Red flags
        if "no analysis available" in lower_answer:
            assessment["quality_flags"].append("No analysis available - response failed")
        if "error" in lower_answer and len(answer) < 200:
            assessment["quality_flags"].append("Error message in response")
        if len(answer) < 50:
            assessment["quality_flags"].append("Response too short (< 50 chars)")

        return assessment

    def run_comprehensive_test(self):
        """Run full diagnostic suite."""
        test_cases = [
            ("Analyze WIPRO", "Test basic analysis"),
            ("Should I buy RELIANCE", "Test buy/sell decision"),
            ("What is P/E ratio of TCS", "Test knowledge question"),
            ("Compare INFY vs Wipro", "Test comparison"),
        ]

        tiers = ["groq", "gemini", "indian-stock-llm", "rule-engine"]

        print("\n" + "="*80)
        print("BYSEL LLM TIER DIAGNOSTIC REPORT")
        print("="*80)
        print(f"Test started: {self.start_time.strftime('%Y-%m-%d %H:%M:%S')}")
        print(f"Base URL: {BASE_URL}")
        print("="*80)

        for query, description in test_cases:
            print(f"\n{'='*80}")
            print(f"TEST CASE: {description}")
            print(f"Query: '{query}'")
            print("="*80)

            query_results = {}

            for tier in tiers:
                result = self.test_tier(query, tier)
                query_results[tier] = result

                print(f"\n▶ Tier: {tier.upper()}")
                print(f"  Status: {result.get('status')}")
                print(f"  Source in Response: {result.get('source')}")
                print(f"  Answer Length: {result.get('answer_length', 0)} chars")

                if not result.get('has_error'):
                    assessment = self.assess_quality(result.get('answer', ''))

                    print(f"  Quality Assessment:")
                    print(f"    ├─ Looks like Metadata: {assessment['looks_like_metadata']}")
                    print(f"    ├─ Looks like Rule Engine: {assessment['looks_like_rule_engine']}")
                    print(f"    ├─ Looks like LLM Analysis: {assessment['looks_like_llm_analysis']}")

                    if assessment['quality_flags']:
                        print(f"    └─ ⚠️ Flags:")
                        for flag in assessment['quality_flags']:
                            print(f"       • {flag}")

                    # Show first 200 chars of answer
                    answer_preview = result.get('answer', '')[:200]
                    print(f"\n  Answer Preview:")
                    for line in answer_preview.split('\n')[:3]:
                        print(f"    {line}")
                    if len(result.get('answer', '')) > 200:
                        print(f"    ... [truncated]")

                else:
                    print(f"  ❌ Error: {result.get('error')}")

            self.results[query] = query_results

        # Summary
        print("\n\n" + "="*80)
        print("SUMMARY & RECOMMENDATIONS")
        print("="*80)

        tier_issues = {tier: 0 for tier in tiers}

        for query, results in self.results.items():
            for tier, result in results.items():
                if result.get('has_error'):
                    tier_issues[tier] += 1

        print("\nTier Reliability:")
        for tier, error_count in tier_issues.items():
            success = len(self.results) - error_count
            print(f"  {tier:20} {success}/{len(self.results)} successful")

        print("\nWhich tier might be returning 'random information':")
        for tier in tiers:
            print(f"  - Check Render logs for 'DEBUG: Calling {tier.capitalize()}' messages")
            print(f"    to see which tier is being called for each query")

        print("\n✅ NEXT STEPS:")
        print("  1. Review the Render logs at: https://dashboard.render.com/bysel-backend")
        print("  2. Search for 'DEBUG:' messages to see the fallback chain")
        print("  3. Test with explicit tier parameters:")
        print("     - tier='groq' to force Groq only")
        print("     - tier='gemini' to force Gemini only")
        print("     - tier='rule-engine' to see baseline signal-only responses")
        print("  4. If Gemini responses look poor, may need to:")
        print("     - Verify GEMINI_API_KEY is set in Render environment")
        print("     - Check if Gemini is properly initializing (gemini_available() = True)")
        print("     - Review Gemini response format and system prompt")

def main():
    diagnostics = TierDiagnostics()
    diagnostics.run_comprehensive_test()

if __name__ == "__main__":
    main()
