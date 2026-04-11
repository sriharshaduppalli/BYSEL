#!/usr/bin/env python3
"""
Test script for BYSEL AI Engine - Indian Finance Queries
"""

def test_indian_finance_handler():
    """Test the Indian finance query handler directly."""

    # Mock the handler function (simplified version)
    def _handle_indian_finance_query(query: str):
        query_lower = query.lower().strip()

        # IPO related queries
        if "ipo" in query_lower:
            if "what is" in query_lower or "explain" in query_lower:
                return {
                    "type": "education",
                    "answer": (
                        "📈 **What is an IPO (Initial Public Offering)?**\n\n"
                        "An IPO is when a private company offers its shares to the public for the first time. "
                        "In India, IPOs are regulated by SEBI and happen on NSE/BSE.\n\n"
                        "**Key Points:**\n"
                        "• Companies raise capital for expansion\n"
                        "• Retail investors can apply through UPI/Demat\n"
                        "• Lot size determines minimum investment\n"
                        "• Allotment is done via lottery system\n"
                        "• Listing happens 3-4 days after closure\n\n"
                        "**Risks:** High volatility, lock-in period, no guaranteed returns"
                    ),
                    "suggestions": ["How to apply for IPO?", "Best upcoming IPOs", "IPO allotment status"]
                }

        # SIP related queries
        elif "sip" in query_lower:
            if "what is" in query_lower or "explain" in query_lower or "how" in query_lower:
                return {
                    "type": "education",
                    "answer": (
                        "💰 **What is SIP (Systematic Investment Plan)?**\n\n"
                        "SIP is a smart way to invest regularly in mutual funds. Instead of investing a large sum at once, "
                        "you invest a fixed amount every month/quarter.\n\n"
                        "**Benefits:**\n"
                        "• **Rupee Cost Averaging**: Buy more units when prices are low\n"
                        "• **Discipline**: Regular investing habit\n"
                        "• **Power of Compounding**: Long-term wealth creation\n"
                        "• **Low Minimum**: Start with ₹100-500 per month\n\n"
                        "**Types:** Daily, Weekly, Monthly, Quarterly SIP\n"
                        "**Best for:** Long-term wealth creation (5+ years)"
                    ),
                    "suggestions": ["Best SIP funds", "How to start SIP", "SIP calculator"]
                }

        # Default fallback
        else:
            return {
                "type": "education",
                "answer": (
                    "🤔 I can help you understand various Indian financial concepts!\n\n"
                    "Try asking about:\n"
                    "• IPO (Initial Public Offering)\n"
                    "• SIP (Systematic Investment Plan)\n"
                    "• Mutual Funds & ELSS\n"
                    "• F&O (Futures & Options)\n"
                    "• Demat Account\n"
                    "• Nifty 50 & Sensex\n"
                    "• Dividends & Taxation\n\n"
                    "What specific topic would you like to learn about?"
                ),
                "suggestions": ["What is IPO?", "How does SIP work?", "Mutual funds basics"]
            }

    test_queries = [
        "What is IPO?",
        "Explain SIP",
        "What is F&O?",
        "What is Demat account?",
        "What are mutual funds?",
        "What is Nifty?",
        "What is Sensex?",
        "What are dividends?",
    ]

    print("🧪 Testing BYSEL AI Engine - Indian Finance Queries\n")
    print("=" * 60)

    for query in test_queries:
        print(f"\n🔍 Query: {query}")
        print("-" * 40)

        try:
            response = _handle_indian_finance_query(query)
            response_type = response.get("type", "unknown")
            answer = response.get("answer", "No answer")

            print(f"📋 Type: {response_type}")
            print(f"💬 Answer: {answer[:200]}..." if len(answer) > 200 else f"💬 Answer: {answer}")

            if "suggestions" in response:
                suggestions = response.get("suggestions", [])
                if suggestions:
                    print(f"💡 Suggestions: {', '.join(suggestions[:3])}")

        except Exception as e:
            print(f"❌ Error: {e}")

        print()

if __name__ == "__main__":
    test_indian_finance_handler()