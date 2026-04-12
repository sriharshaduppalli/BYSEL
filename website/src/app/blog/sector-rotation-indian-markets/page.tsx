import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Understanding Sector Rotation in Indian Markets: A Practical Guide",
  description:
    "Learn how to identify and profit from sector rotation in NSE/BSE. Understand money flow between banking, IT, pharma, and other Indian market sectors.",
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
            <span className="eyebrow">Market Context</span>
            <h1 className="page-title" style={{ fontSize: "clamp(1.8rem, 4.5vw, 2.8rem)" }}>
              Understanding Sector Rotation in Indian Markets
            </h1>
            <p className="blog-meta">March 30, 2026 &middot; 6 min read</p>
          </div>

          <article className="blog-body glass-card">
            <p>
              One of the most powerful edges in Indian market trading is understanding sector rotation —
              the periodic shift of money from one sector to another. When you see NIFTY going nowhere
              but Bank Nifty rallying 2%, that&apos;s sector rotation at work. Identifying these shifts
              early gives you a significant advantage.
            </p>

            <h2>What Is Sector Rotation?</h2>
            <p>
              Sector rotation is the movement of investment capital from one industry sector to another.
              Smart money — institutional investors, mutual funds, and FIIs — constantly reallocate
              based on economic cycles, policy changes, and earnings expectations.
            </p>
            <p>
              In India, the primary sectors to watch are:
            </p>
            <ul>
              <li><strong>NIFTY Bank / Financial Services:</strong> Most impacted by RBI policy, credit
                growth, and NPA data</li>
              <li><strong>NIFTY IT:</strong> Driven by US tech spending, dollar strength, and deal wins</li>
              <li><strong>NIFTY Pharma / Healthcare:</strong> Defensive sector, often rallies when market
                is uncertain</li>
              <li><strong>NIFTY Auto:</strong> Tied to domestic consumption, rural demand, and commodity
                prices</li>
              <li><strong>NIFTY Metal:</strong> Highly cyclical, follows global commodity prices and China
                demand</li>
              <li><strong>NIFTY Energy:</strong> Influenced by crude oil, government policy on renewables</li>
              <li><strong>NIFTY FMCG:</strong> Defensive, steady compounder. Money flows here during
                market stress</li>
            </ul>

            <h2>The Indian Market Rotation Cycle</h2>

            <h3>Phase 1: Risk-On Beginning</h3>
            <p>
              When the market starts a new uptrend after a correction, money flows first into
              <strong> banking and financial stocks</strong>. Bank Nifty leads NIFTY 50. FIIs start
              net buying. You&apos;ll see HDFC Bank, ICICI Bank, and SBI leading the charge.
            </p>

            <h3>Phase 2: Broadening Rally</h3>
            <p>
              If the rally has legs, it broadens to <strong>auto, real estate, and capital goods</strong>.
              Mid-cap stocks start outperforming large-caps. The advance-decline ratio stays positive
              day after day.
            </p>

            <h3>Phase 3: Late Cycle</h3>
            <p>
              In the later stages, <strong>metals and energy</strong> catch up. Speculative names and
              small-caps see massive moves. This is the most dangerous phase — the market looks
              euphoric but smart money is already rotating out.
            </p>

            <h3>Phase 4: Defensive Shift</h3>
            <p>
              When the market tops out, money flows into <strong>pharma, FMCG, and IT</strong>. These
              sectors provide earnings visibility and act as safe havens. If you see Bank Nifty falling
              while NIFTY Pharma rises, defensive rotation has begun.
            </p>

            <h2>How to Spot Rotation in Real Time</h2>
            <ul>
              <li>
                <strong>Compare sectoral index performance:</strong> Look at 5-day returns across all
                NIFTY sectoral indices. The sector gaining while others fade is receiving fresh capital.
              </li>
              <li>
                <strong>Watch relative strength:</strong> If IT stocks are rising on a flat market day,
                they have relative strength. This precedes bigger moves.
              </li>
              <li>
                <strong>Volume confirmation:</strong> Sector rotation backed by volume is more reliable
                than price moves on thin volume.
              </li>
              <li>
                <strong>FII/DII sectoral flows:</strong> Monthly data from NSE shows which sectors are
                receiving institutional flows.
              </li>
            </ul>
            <p>
              BYSEL Trader&apos;s heatmap and sector rotation tracking gives you real-time visibility into
              these flow shifts, so you can align your trades with where the money is moving.
            </p>

            <h2>Trading the Rotation</h2>
            <ol>
              <li>Identify the current rotation phase using the framework above</li>
              <li>Focus your trades on the <strong>incoming sector</strong>, not the one that already moved</li>
              <li>Use individual stock relative strength within the favored sector to pick the best names</li>
              <li>Set tight stops — if the rotation thesis is wrong, exit quickly</li>
              <li>Don&apos;t fight the rotation — if banking is dying and pharma is rising, go where the
                money is flowing</li>
            </ol>

            <div className="btn-row" style={{ marginTop: "1.5rem" }}>
              <Link href="https://play.google.com/store" className="btn-primary" target="_blank" rel="noreferrer">
                Track Sector Rotation Live
              </Link>
              <Link href="/blog/intraday-trading-routine" className="btn-neutral">
                Read: Intraday Routine Guide
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
