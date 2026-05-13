import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Complete Beginner's Guide to Indian Stock Market Trading",
  description:
    "New to stock trading? Learn the essentials of NSE, BSE, demat accounts, order types, and how to start your trading journey in India the right way.",
};

export default function BlogPost() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <div className="blog-post-header">
            <Link href="/blog" className="blog-back-link">
              &larr; Back to Blog
            </Link>
            <span className="eyebrow">Beginner Guide</span>
            <h1 className="page-title" style={{ fontSize: "clamp(1.8rem, 4.5vw, 2.8rem)" }}>
              Complete Beginner&apos;s Guide to Indian Stock Market Trading
            </h1>
            <p className="blog-meta">April 3, 2026 &middot; 8 min read</p>
          </div>

          <article className="blog-body glass-card">
            <p>
              India&apos;s stock market is one of the fastest-growing in the world. If you&apos;ve been
              thinking about getting started but feel overwhelmed by the jargon, charts, and complexity —
              this guide breaks it all down into simple steps.
            </p>

            <h2>Understanding the Basics</h2>

            <h3>NSE and BSE: India&apos;s Two Exchanges</h3>
            <p>
              India has two major stock exchanges: the <strong>National Stock Exchange (NSE)</strong> and
              the <strong>Bombay Stock Exchange (BSE)</strong>. NSE is the larger of the two by trading
              volume, and its benchmark index is the NIFTY 50. BSE&apos;s benchmark is the SENSEX, comprising
              30 large-cap companies.
            </p>
            <p>
              Most retail traders use NSE because of higher liquidity and tighter bid-ask spreads. When you
              hear &ldquo;the market is up 300 points,&rdquo; they&apos;re usually referring to NIFTY 50.
            </p>

            <h3>What You Need to Start</h3>
            <ul>
              <li>
                <strong>Demat Account:</strong> This holds your shares electronically. Think of it as a
                bank account for stocks. You can open one with any SEBI-registered broker.
              </li>
              <li>
                <strong>Trading Account:</strong> This is linked to your demat account and lets you buy
                and sell stocks. Most brokers provide both together.
              </li>
              <li>
                <strong>Bank Account:</strong> Linked for fund transfers. UPI-based instant transfers
                have made this seamless.
              </li>
              <li>
                <strong>PAN Card:</strong> Mandatory for all financial transactions in India.
              </li>
            </ul>

            <h2>Types of Trading</h2>

            <h3>Intraday Trading</h3>
            <p>
              Buy and sell within the same day. Positions are squared off before market close (3:30 PM).
              Higher risk, requires constant monitoring. Profits are taxed as business income.
            </p>

            <h3>Swing Trading</h3>
            <p>
              Hold positions for days to weeks, capturing medium-term price movements. Less stressful
              than intraday, suitable for people with full-time jobs.
            </p>

            <h3>Positional / Investment</h3>
            <p>
              Hold for months to years. Focus on fundamentals — earnings growth, market position,
              management quality. Long-term capital gains (over 1 year) above ₹1.25 lakh are taxed at
              12.5%.
            </p>

            <h2>Essential Order Types</h2>
            <ul>
              <li>
                <strong>Market Order:</strong> Buy/sell immediately at the current price. Fast but you
                may get a slightly different price than expected (slippage).
              </li>
              <li>
                <strong>Limit Order:</strong> Set your price. The order executes only if the stock
                reaches your specified price. More control, but may not fill.
              </li>
              <li>
                <strong>Stop Loss:</strong> Automatically sells if the price drops to your set level.
                Essential for risk management.
              </li>
            </ul>

            <h2>Key Concepts Every Beginner Must Know</h2>

            <h3>1. Never Risk More Than 2% Per Trade</h3>
            <p>
              If your trading capital is ₹1,00,000, never risk more than ₹2,000 on a single trade.
              This ensures one bad trade doesn&apos;t wipe out your account.
            </p>

            <h3>2. Understand Market Timing</h3>
            <p>
              Indian markets are open 9:15 AM to 3:30 PM, Monday to Friday. The first 30 minutes
              (9:15-9:45) are the most volatile. Beginners should avoid trading during market open
              until they develop a feel for price action.
            </p>

            <h3>3. Start with Paper Trading</h3>
            <p>
              Before risking real money, practice with a simulator. BYSEL Trader offers AI-powered
              paper trading with real NSE/BSE market data — you learn market behavior without financial
              risk.
            </p>

            <h3>4. Keep a Trading Journal</h3>
            <p>
              Record every trade: why you entered, your target, your stop loss, and the outcome.
              Patterns emerge over time that reveal your strengths and weaknesses.
            </p>

            <h2>Common Beginner Mistakes</h2>
            <ul>
              <li>Trading based on tips from WhatsApp groups or social media</li>
              <li>Averaging down on losing positions (hoping they&apos;ll recover)</li>
              <li>Over-trading — taking too many positions without clear setups</li>
              <li>Ignoring stop losses (&ldquo;it will come back&rdquo; mindset)</li>
              <li>Putting all capital into a single stock</li>
            </ul>

            <h2>Your First Steps</h2>
            <ol>
              <li>Open a demat + trading account with a reputable broker</li>
              <li>Start with paper trading on BYSEL Trader to learn without risk</li>
              <li>Study 2-3 simple chart patterns (support/resistance, moving averages)</li>
              <li>Begin with small positions in liquid, large-cap stocks</li>
              <li>Review every trade and continuously refine your process</li>
            </ol>

            <div className="btn-row" style={{ marginTop: "1.5rem" }}>
              <Link href="https://play.google.com/store" className="btn-primary" target="_blank" rel="noreferrer">
                Start Paper Trading Free
              </Link>
              <Link href="/blog/why-ai-trading-assistant" className="btn-neutral">
                Read: Why You Need AI
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
