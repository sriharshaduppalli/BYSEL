import Link from "next/link";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const CORE_MODULES = [
  {
    kicker: "AI",
    title: "Stock Assistant Chat",
    copy: "Ask prices, buy/sell bias, valuation, and comparisons. Replies can include Entry / Target / Stop-Loss with Buy and Set Alert buttons.",
  },
  {
    kicker: "Home",
    title: "Market Pulse & Watchlist",
    copy: "Live index strip, idea rails, movers, news-aware brief, and a watchlist built for fast scan-to-trade.",
  },
  {
    kicker: "Heatmap",
    title: "Sector Sentiment Map",
    copy: "NSE sector heatmap with mood and breadth. Targets ~1–2s refresh while the market is open; last-session snapshot when closed.",
  },
  {
    kicker: "Trade",
    title: "Paper Order Ticket",
    copy: "Market/limit style paper orders against a virtual wallet, with confirmation for AI-suggested trades.",
  },
  {
    kicker: "Portfolio",
    title: "Holdings & Health",
    copy: "Track positions, PnL, and portfolio health scores so practice stays measurable.",
  },
  {
    kicker: "Labs",
    title: "Signal, Risk, Smart Money",
    copy: "Signal Lab, Risk Lab, investor portfolio changes, earnings calendar, trade journal, SIP / MF / IPO explorers.",
  },
];

const ACCESS_TRACK = [
  {
    title: "Create account",
    copy: "Register with username, email, and password — the most reliable path for testers right now.",
  },
  {
    title: "Optional phone OTP",
    copy: "Firebase phone login is available; live SMS depends on Firebase delivery. Test numbers / password login keep access unblocked.",
  },
  {
    title: "Explore markets",
    copy: "Home → Heatmap → AI → Trade. Everything stays paper until you are ready for real brokerage elsewhere.",
  },
  {
    title: "Review discipline",
    copy: "Use journal, alerts, and portfolio health to tighten process before risking real capital.",
  },
];

export default function Features() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Product Surface</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)" }}>
            Features that match the live Android app.
          </h1>
          <p className="lead">
            BYSEL Trader is an Indian-market paper-trading and learning app: AI chat, live context, simulated execution,
            and review tools — not a SEBI-registered brokerage.
          </p>

          <div className="feature-grid" style={{ marginTop: "1.2rem" }}>
            {CORE_MODULES.map((item, index) => (
              <article key={item.title} className="glass-card feature-card" data-animate data-delay={String(Math.min(index, 4))}>
                <p className="feature-kicker">{item.kicker}</p>
                <h2 className="feature-title">{item.title}</h2>
                <p className="feature-copy">{item.copy}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container split-grid">
          <article className="glass-card hero-panel" data-animate>
            <div className="panel-head">
              <h2 className="panel-title">Getting started</h2>
              <span className="status-chip live">Recommended</span>
            </div>
            <div className="timeline">
              {ACCESS_TRACK.map((stage, index) => (
                <div key={stage.title} className="timeline-step glass-card" style={{ boxShadow: "none", background: "var(--surface-strong)" }}>
                  <span className="timeline-index">{index + 1}</span>
                  <div>
                    <h3 className="feature-title">{stage.title}</h3>
                    <p className="feature-copy">{stage.copy}</p>
                  </div>
                </div>
              ))}
            </div>
          </article>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">Why BYSEL</h2>
            </div>
            <div className="stack-grid">
              <div className="info-row">
                <p className="info-title">India-first data</p>
                <p className="info-copy">NSE symbols, sector heatmap, and Indian-market coaching — not a US-stock template.</p>
              </div>
              <div className="info-row">
                <p className="info-title">AI that can act (in paper)</p>
                <p className="info-copy">Buy / Set Alert from chat responses, with confirmation before paper orders execute.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Safe practice</p>
                <p className="info-copy">Virtual wallet and educational disclaimers. No live brokerage order routing in this product.</p>
              </div>
            </div>

            <div className="btn-row" style={{ marginTop: "1rem" }}>
              <Link href={PLAY_STORE_URL} className="btn-primary" target="_blank" rel="noreferrer">
                Get the App
              </Link>
              <Link href="/support" className="btn-neutral">
                Contact Support
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
