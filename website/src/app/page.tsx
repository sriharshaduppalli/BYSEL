import Link from "next/link";
import Image from "next/image";
import LiveHeatmap from "../components/LiveHeatmap";

const FEATURE_CARDS = [
  {
    kicker: "AI Execution Coach",
    title: "Trade plans before button taps",
    copy: "Generate entry, risk, and exit templates instantly, then learn from post-trade breakdowns built for discipline.",
  },
  {
    kicker: "India-First Simulation",
    title: "NSE/BSE market behavior emulation",
    copy: "Practice under realistic volatility, liquidity shifts, and sentiment cycles so your routine survives live sessions.",
  },
  {
    kicker: "Portfolio Intelligence",
    title: "Allocation guidance that explains itself",
    copy: "Spot concentration risk, rebalance ideas, and track decisions with context instead of raw numbers only.",
  },
  {
    kicker: "Trade Journal",
    title: "Review every move with structure",
    copy: "Capture bias, setup quality, and adherence to plan so each week compounds into better behavior.",
  },
  {
    kicker: "Market Radar",
    title: "Heatmap, momentum, and sentiment",
    copy: "See where participation is expanding or fading and build watchlists from sectors with real follow-through.",
  },
  {
    kicker: "Learning Mode",
    title: "From beginner to process-driven",
    copy: "Turn strategy videos into executable checklists with quizzes and scenario-based simulation blocks.",
  },
];

const EXECUTION_LOOP = [
  {
    title: "Build your scenario",
    copy: "Choose symbols, define risk per trade, and set the market context before your session opens.",
  },
  {
    title: "Run simulated execution",
    copy: "Take entries in live conditions and adapt to changing breadth, trend quality, and sentiment readings.",
  },
  {
    title: "Review and improve",
    copy: "AI feedback highlights process mistakes, not just PnL, so your next playbook is cleaner.",
  },
];

const MARKET_SIGNALS = [
  {
    title: "Sector Rotation",
    copy: "Banking, IT, and energy flow snapshots update every few seconds to surface trend transfer.",
  },
  {
    title: "Breadth Tracking",
    copy: "See participation depth behind index moves and avoid entering when leadership is too narrow.",
  },
  {
    title: "Risk Events",
    copy: "Macro and earnings windows are flagged early so you can tune position size and exposure.",
  },
];

export default function Home() {
  return (
    <main>
      <section className="hero-wrap">
        <div className="site-container hero-grid">
          <div data-animate>
            <span className="eyebrow">Built for serious practice</span>
            <h1 className="page-title">Train your trading process before risking real capital.</h1>
            <p className="lead">
              BYSEL Trader combines live market context, AI coaching, and structured simulation to help you build a repeatable trading routine.
            </p>

            <div className="btn-row">
              <Link href="https://play.google.com/store" className="btn-primary" target="_blank" rel="noreferrer">
                Download Android App
              </Link>
              <Link href="/features" className="btn-secondary">
                Explore Product
              </Link>
              <Link href="/markets" className="btn-neutral">
                See Live Market View
              </Link>
            </div>

            <div className="stat-grid">
              <div className="stat-item">
                <span className="stat-value">5s</span>
                <span className="stat-label">Market pulse refresh</span>
              </div>
              <div className="stat-item">
                <span className="stat-value">3-step</span>
                <span className="stat-label">Execution review loop</span>
              </div>
              <div className="stat-item">
                <span className="stat-value">100%</span>
                <span className="stat-label">Simulation-first learning</span>
              </div>
            </div>
          </div>

          <aside className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">Session Dashboard</h2>
              <span className="status-chip live">Open</span>
            </div>
            <p className="mini-muted">Setup your plan, then execute with context that changes as fast as the market does.</p>

            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              <div className="info-row">
                <p className="info-title">Watchlist Focus</p>
                <p className="info-copy">NIFTY, BANKNIFTY, and high-participation large caps with trend confirmation.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Risk Discipline</p>
                <p className="info-copy">Predefined loss threshold and position sizing for every simulated setup.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Review Trigger</p>
                <p className="info-copy">Auto-analysis starts when plan adherence drops, not only after losses.</p>
              </div>
            </div>

            <div className="pill-row">
              <span className="tag-pill">AI Mentor</span>
              <span className="tag-pill">Paper Portfolio</span>
              <span className="tag-pill">Behavior Analytics</span>
            </div>
          </aside>
        </div>
      </section>

      <section className="section-wrap" id="features">
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">Everything you need to practice like a pro</h2>
              <p className="section-copy">
                The platform is designed around process quality: setup selection, risk framing, execution timing, and review rigor.
              </p>
            </div>
          </div>

          <div className="feature-grid">
            {FEATURE_CARDS.map((item, index) => (
              <article
                key={item.title}
                className="glass-card feature-card"
                data-animate
                data-delay={String(Math.min(index, 4))}
              >
                <p className="feature-kicker">{item.kicker}</p>
                <h3 className="feature-title">{item.title}</h3>
                <p className="feature-copy">{item.copy}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap">
        <div className="site-container split-grid">
          <div data-animate>
            <LiveHeatmap />
          </div>

          <div className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">Market Signal Stack</h2>
              <span className="status-chip warn">Intraday</span>
            </div>
            <p className="mini-muted">Use these modules to avoid random entries and prioritize high-quality setups.</p>

            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              {MARKET_SIGNALS.map((signal) => (
                <div key={signal.title} className="info-row">
                  <p className="info-title">{signal.title}</p>
                  <p className="info-copy">{signal.copy}</p>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="section-wrap">
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">The BYSEL execution loop</h2>
              <p className="section-copy">Designed to make confidence come from repeatable process quality, not one lucky day.</p>
            </div>
          </div>

          <div className="timeline">
            {EXECUTION_LOOP.map((step, index) => (
              <article key={step.title} className="glass-card timeline-step" data-animate data-delay={String(index + 1)}>
                <span className="timeline-index">{index + 1}</span>
                <div>
                  <h3 className="feature-title">{step.title}</h3>
                  <p className="feature-copy">{step.copy}</p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="site-container">
        <div className="glass-card cta-block" data-animate>
          <div className="split-grid">
            <div>
              <h2 className="section-title" style={{ marginBottom: "0.5rem" }}>
                Ready to build a disciplined trading habit?
              </h2>
              <p className="section-copy">
                Start with the simulator, run your process, and carry a verified routine into real markets when you are ready.
              </p>
            </div>

            <div className="btn-row" style={{ marginTop: 0, alignSelf: "center", justifyContent: "flex-start" }}>
              <Link href="https://play.google.com/store" className="btn-primary" target="_blank" rel="noreferrer">
                Install on Android
              </Link>
              <Link href="/pricing" className="btn-neutral">
                View Plans
              </Link>
              <Link href="/support" className="btn-neutral">
                Talk to Support
              </Link>
            </div>
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.5rem" }}>
        <div className="site-container" style={{ textAlign: "center" }}>
          <Image src="/ic_launcher.png" alt="BYSEL app icon" width={72} height={72} priority />
          <p className="mini-muted" style={{ marginTop: "0.7rem" }}>
            BYSEL Trader. Simulation-first learning for smarter participation in Indian markets.
          </p>
        </div>
      </section>
    </main>
  );
}
