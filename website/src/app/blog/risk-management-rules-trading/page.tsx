import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "5 Risk Management Rules That Protect Your Trading Capital",
  description:
    "Learn the essential risk management strategies used by professional traders. Position sizing, stop losses, and portfolio rules to keep your capital safe.",
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
            <span className="eyebrow">Risk Management</span>
            <h1 className="page-title" style={{ fontSize: "clamp(1.8rem, 4.5vw, 2.8rem)" }}>
              5 Risk Management Rules That Protect Your Trading Capital
            </h1>
            <p className="blog-meta">April 2, 2026 &middot; 5 min read</p>
          </div>

          <article className="blog-body glass-card">
            <p>
              The most successful traders aren&apos;t the ones who pick the best stocks — they&apos;re the
              ones who manage risk the best. In Indian markets, where circuit limits, operator-driven
              moves, and sudden news events can create wild swings, risk management isn&apos;t just
              important — it&apos;s survival.
            </p>

            <h2>Rule 1: The 2% Rule</h2>
            <p>
              Never risk more than 2% of your total trading capital on any single trade. If your account
              has ₹5,00,000, your maximum loss per trade should be ₹10,000.
            </p>
            <p>
              This means your position size depends on where your stop loss is. If you buy a stock at
              ₹500 with a stop loss at ₹480, your risk per share is ₹20. With a ₹10,000 maximum risk,
              you can buy 500 shares (₹10,000 ÷ ₹20).
            </p>
            <p>
              BYSEL Trader&apos;s AI automatically calculates position sizes based on your risk parameters,
              so you never have to do this math under pressure.
            </p>

            <h2>Rule 2: Always Use Stop Losses</h2>
            <p>
              A trade without a stop loss is a gamble. Before you enter any position, define your exit
              point for loss. This should be based on technical levels (support, moving average) — not
              an arbitrary percentage.
            </p>
            <p>
              Common stop loss approaches for Indian markets:
            </p>
            <ul>
              <li><strong>Technical stop:</strong> Below the most recent swing low or support level</li>
              <li><strong>ATR-based stop:</strong> 1.5x the Average True Range below entry</li>
              <li><strong>Time stop:</strong> Exit if the trade doesn&apos;t move in your favor within a set
                number of candles</li>
            </ul>

            <h2>Rule 3: Maximum Daily Loss Limit</h2>
            <p>
              Set a hard limit on how much you can lose in a single day. Professional traders typically
              use 4-6% of capital. Once you hit this limit, stop trading for the day.
            </p>
            <p>
              This prevents revenge trading — the destructive cycle where you try to recover losses by
              taking increasingly risky trades. Most blow-ups happen not from one bad trade, but from a
              series of emotional trades after the first loss.
            </p>

            <h2>Rule 4: Diversify Across Sectors</h2>
            <p>
              Never have more than 25% of your portfolio in a single sector. Indian markets have strong
              sectoral correlations — when banking falls, HDFC Bank, ICICI, Kotak, and SBI all drop
              together. If you&apos;re overexposed to one sector, a single piece of negative news can
              devastate your portfolio.
            </p>
            <p>
              BYSEL Trader&apos;s portfolio intelligence feature monitors your sector exposure and alerts
              you when concentration risk is building up.
            </p>

            <h2>Rule 5: Scale In, Not All At Once</h2>
            <p>
              Instead of buying your full position at once, enter in 2-3 tranches. Buy the first third
              at your entry point. Add more only if the trade moves in your favor. This way, your
              average price improves on winning trades, and your losses are smaller on losing ones.
            </p>
            <p>
              For example, if you want to buy 300 shares of Reliance:
            </p>
            <ul>
              <li>Buy 100 shares at your target entry</li>
              <li>Add 100 more if it moves 1% in your direction</li>
              <li>Add the final 100 after it confirms the breakout</li>
            </ul>

            <h2>Putting It All Together</h2>
            <p>
              Risk management is a skill that improves with practice. The best way to internalize these
              rules is to practice them in a simulator before using real money. BYSEL Trader&apos;s AI
              coaching tracks your adherence to these principles and gives specific feedback on where
              you can improve.
            </p>

            <div className="btn-row" style={{ marginTop: "1.5rem" }}>
              <Link href="https://play.google.com/store" className="btn-primary" target="_blank" rel="noreferrer">
                Practice Risk Management
              </Link>
              <Link href="/blog/beginners-guide-indian-stock-market" className="btn-neutral">
                Read: Beginner&apos;s Guide
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
