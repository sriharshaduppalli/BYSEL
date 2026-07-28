import Link from "next/link";
import Image from "next/image";
import LiveHeatmap from "../components/LiveHeatmap";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const FEATURE_CARDS = [
  {
    kicker: "AI Stock Assistant",
    title: "Ask, analyze, then act",
    copy: "Chat about NSE stocks with grounded answers. Buy and Set Alert actions sit right on AI replies so you can paper-trade without leaving the conversation.",
  },
  {
    kicker: "Home Market Pulse",
    title: "Indices, ideas, and watchlist",
    copy: "NIFTY / SENSEX / BANK NIFTY strip, idea rails, movers, and a denser watchlist with sort chips and quick Trade CTAs.",
  },
  {
    kicker: "Live Heatmap",
    title: "Sector heat in 1–2 seconds",
    copy: "While the market is open, the sentiment heatmap refreshes about every 1–2 seconds so breadth and sector leadership stay current.",
  },
  {
    kicker: "Paper Trading",
    title: "Practice with a virtual wallet",
    copy: "Place simulated BUY/SELL orders, track holdings and PnL, and review portfolio health without risking real capital.",
  },
  {
    kicker: "Signal Lab & Smart Money",
    title: "Momentum, pressure, and investor flows",
    copy: "Surface high-participation names, track smart-money style portfolio changes, and route straight into stock detail or trade.",
  },
  {
    kicker: "Discipline Tools",
    title: "Alerts, journal, risk & SIP labs",
    copy: "Price alerts, trade journal, risk lab, earnings calendar, mutual funds / SIP / IPO explorers — built for Indian-market learning.",
  },
];

const EXECUTION_LOOP = [
  {
    title: "Scan the tape",
    copy: "Use Home pulse, heatmap, and Signal Lab to shortlist symbols with real participation — not random tips.",
  },
  {
    title: "Ask AI, then confirm",
    copy: "Get a trade decision with levels. Tap Buy or Set Alert in chat, confirm the order, and keep risk explicit.",
  },
  {
    title: "Review and improve",
    copy: "Journal, portfolio health, and post-trade coaching help you tighten process week over week.",
  },
];

const MARKET_SIGNALS = [
  {
    title: "Sector Heatmap",
    copy: "Banking, IT, Pharma, Auto, and more — with advances/declines and mood when the session is live.",
  },
  {
    title: "Movers & Momentum",
    copy: "Gainers, losers, and most-active names with stale-while-revalidate so Home stays fast on cold starts.",
  },
  {
    title: "Price Alerts",
    copy: "Create ABOVE/BELOW alerts from AI cards or the Alerts screen and get notified as levels approach.",
  },
];

export default function Home() {
  return (
    <main>
      <section className="hero-wrap">
        <div className="site-container hero-grid">
          <div data-animate>
            <span className="eyebrow">Android app for Indian markets</span>
            <h1 className="page-title">Paper-trade NSE stocks with AI coaching and live market context.</h1>
            <p className="lead">
              BYSEL Trader is a simulation-first Android app: live quotes, sector heatmap, AI assistant,
              paper portfolio, and structured practice tools — built for India, not generic US-market demos.
            </p>

            <div className="btn-row">
              <Link href={PLAY_STORE_URL} className="btn-primary" target="_blank" rel="noreferrer">
                Get on Google Play
              </Link>
              <Link href="/features" className="btn-secondary">
                Explore Features
              </Link>
              <Link href="/markets" className="btn-neutral">
                Live Market View
              </Link>
            </div>

            <div className="stat-grid">
              <div className="stat-item">
                <span className="stat-value">1–2s</span>
                <span className="stat-label">Heatmap refresh (market open)</span>
              </div>
              <div className="stat-item">
                <span className="stat-value">AI+</span>
                <span className="stat-label">Chat with Buy / Alert actions</span>
              </div>
              <div className="stat-item">
                <span className="stat-value">100%</span>
                <span className="stat-label">Paper trading / educational</span>
              </div>
            </div>
          </div>

          <aside className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">Inside the app</h2>
              <span className="status-chip live">Android</span>
            </div>
            <p className="mini-muted">What users open every session — Home, AI, Trade, Portfolio, Heatmap.</p>

            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              <div className="info-row">
                <p className="info-title">Home</p>
                <p className="info-copy">Index strip, ideas, movers, watchlist, and quick trade entry.</p>
              </div>
              <div className="info-row">
                <p className="info-title">AI Assistant</p>
                <p className="info-copy">Stock Q&amp;A, trade decisions with Entry / Target / Stop-Loss, confirmable orders.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Auth that works offline SMS</p>
                <p className="info-copy">Register with username + email + password, or use phone OTP when SMS delivery is available.</p>
              </div>
            </div>

            <div className="pill-row">
              <span className="tag-pill">NSE / BSE context</span>
              <span className="tag-pill">Paper wallet</span>
              <span className="tag-pill">Not a SEBI broker</span>
            </div>
          </aside>
        </div>
      </section>

      <section className="section-wrap" id="features">
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">What ships in the latest BYSEL Android app</h2>
              <p className="section-copy">
                Product surfaces that match the live app — not a generic financial-planning brochure.
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
              <h2 className="section-title">The BYSEL practice loop</h2>
              <p className="section-copy">Scan → decide with AI → paper-execute → review. Built for habit, not hype.</p>
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
                Ready to practice Indian markets on Android?
              </h2>
              <p className="section-copy">
                Install BYSEL Trader, create a username/password account (recommended while OTP SMS is being stabilized),
                and start with paper capital. Educational use only — not investment advice and not a SEBI-registered broker.
              </p>
            </div>

            <div className="btn-row" style={{ marginTop: 0, alignSelf: "center", justifyContent: "flex-start" }}>
              <Link href={PLAY_STORE_URL} className="btn-primary" target="_blank" rel="noreferrer">
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
            BYSEL Trader · Simulation-first learning for smarter participation in Indian markets.
          </p>
        </div>
      </section>
    </main>
  );
}
