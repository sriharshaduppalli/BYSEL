import Link from "next/link";
import LiveHeatmap from "../../components/LiveHeatmap";

const MARKET_MODULES = [
  {
    title: "Top Movers",
    copy: "Rank symbols by momentum and participation to avoid chasing thin, low-conviction moves.",
  },
  {
    title: "Breadth Snapshot",
    copy: "Track advance/decline trends and detect when index strength is diverging from broader participation.",
  },
  {
    title: "Event Watch",
    copy: "Earnings windows, policy updates, and macro events are surfaced so risk can be adjusted in time.",
  },
  {
    title: "Sector Momentum",
    copy: "Identify leadership rotation across financials, IT, autos, pharma, and energy in near real-time.",
  },
];

const COVERAGE_TAGS = ["NSE Equities", "Index Derivatives", "Sector Heatmap", "Sentiment Layer", "Intraday Alerts"];

export default function Markets() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Market Center</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.15rem)" }}>
            Real-time context for better simulated decisions.
          </h1>
          <p className="lead">
            BYSEL surfaces market structure, sector participation, and event risk in one view so you can practice with intent.
          </p>

          <div className="pill-row">
            {COVERAGE_TAGS.map((tag) => (
              <span key={tag} className="tag-pill">
                {tag}
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container split-grid">
          <div data-animate>
            <LiveHeatmap />
          </div>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">Signal Overview</h2>
              <span className="status-chip live">Realtime</span>
            </div>
            <div className="stack-grid">
              <div className="info-row">
                <p className="info-title">Index Pulse</p>
                <p className="info-copy">Track NIFTY and BANKNIFTY behavior with breadth and volatility overlays.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Liquidity Lens</p>
                <p className="info-copy">Spot where participation is strong enough to support clean entries and exits.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Bias Guardrails</p>
                <p className="info-copy">Get prompts when market internals disagree with your directional thesis.</p>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.3rem" }}>
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">Modules inside Markets</h2>
              <p className="section-copy">Every panel is tuned to help you build better trade selection and timing decisions.</p>
            </div>
          </div>

          <div className="feature-grid">
            {MARKET_MODULES.map((item, index) => (
              <article key={item.title} className="glass-card feature-card" data-animate data-delay={String(Math.min(index + 1, 4))}>
                <h3 className="feature-title">{item.title}</h3>
                <p className="feature-copy">{item.copy}</p>
              </article>
            ))}
          </div>

          <div className="btn-row" style={{ marginTop: "1rem" }}>
            <Link href="/features" className="btn-neutral">
              Explore Product Features
            </Link>
            <Link href="/pricing" className="btn-primary">
              Unlock Full Access
            </Link>
          </div>
        </div>
      </section>
    </main>
  );
}
