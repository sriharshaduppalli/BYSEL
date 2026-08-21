import Link from "next/link";
import Image from "next/image";
import LiveHeatmap from "../components/LiveHeatmap";
import AiTryDemo from "../components/AiTryDemo";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const FEATURE_CARDS = [
  {
    kicker: "Practice Ideas",
    title: "Entry, stop, and target drills",
    copy: "Home Practice Ideas show educational levels with Paper Buy and Alert @ SL — drills for process, not tip-selling.",
  },
  {
    kicker: "Today's Practice",
    title: "Idea → Trade → Review",
    copy: "A daily habit strip tracks idea seen, paper trade taken, and review logged so practice compounds into discipline.",
  },
  {
    kicker: "Practice credit",
    title: "Instant paper wallet funding",
    copy: "Add simulation capital in one tap — no UPI required. Optional UPI demo stays available; paper trading never needs real money.",
  },
  {
    kicker: "After-hours paper",
    title: "Practice when NSE is closed",
    copy: "Paper fills still work after 3:30 PM IST using last session prices, so evenings and weekends stay useful for drills.",
  },
  {
    kicker: "AI assistant",
    title: "Ask about a snapshot",
    copy: "Plain-language help on a quote snapshot. Answers are educational — not a recommendation to buy or sell.",
  },
  {
    kicker: "Live Heatmap",
    title: "Sector heat in 1–2 seconds",
    copy: "While the market is open, the sentiment heatmap refreshes about every 1–2 seconds; closed sessions show last-session context.",
  },
];

const EXECUTION_LOOP = [
  {
    title: "Pick a Practice Idea",
    copy: "See Entry / SL / Target with coaching notes. These are educational drills — not SEBI tips or buy recommendations.",
  },
  {
    title: "Paper Buy or Alert @ SL",
    copy: "Execute a simulated buy with practice credit, or set an alert at the stop so you rehearse risk levels.",
  },
  {
    title: "Review the trade",
    copy: "Log whether you set a stop, followed the plan, and what you learned — then check portfolio health and journal.",
  },
];

const MARKET_SIGNALS = [
  {
    title: "Practice Ideas rail",
    copy: "Educational drills with levels, Paper Buy, and Alert @ SL on Home — process practice, not tip feed.",
  },
  {
    title: "Sector Heatmap",
    copy: "Banking, IT, Pharma, Auto, and more — with advances/declines and mood when the session is live.",
  },
  {
    title: "After-hours paper fills",
    copy: "NSE closed? Keep practicing with last session prices and instant practice credit.",
  },
];

export default function Home() {
  return (
    <main>
      <section className="hero-wrap">
        <div className="site-container hero-grid">
          <div data-animate>
            <span className="eyebrow">www.byseltrader.com</span>
            <h1 className="page-title">Indian market education and paper practice. Account required. Not live trading.</h1>
            <p className="lead">
              BYSEL is the official paper-practice app for NSE / BSE learners. Try the snapshot assistant and
              sector heatmap here, then open Android for watchlists, Scanner / BYSEL Score, and a simulated
              wallet. This is not a broker and not investment advice.
            </p>

            <div className="btn-row">
              <Link href={PLAY_STORE_URL} className="btn-primary" target="_blank" rel="noreferrer">
                Get on Google Play
              </Link>
              <a href="#try-ai" className="btn-secondary">
                Try AI on the web
              </a>
              <a href="#live-heatmap" className="btn-neutral">
                View heatmap
              </a>
            </div>

            <div className="stat-grid">
              <div className="stat-item">
                <span className="stat-value">Idea→Review</span>
                <span className="stat-label">Daily practice habit loop</span>
              </div>
              <div className="stat-item">
                <span className="stat-value">24×7</span>
                <span className="stat-label">Paper fills after NSE close</span>
              </div>
              <div className="stat-item">
                <span className="stat-value">100%</span>
                <span className="stat-label">Paper trading / educational</span>
              </div>
            </div>
          </div>

          <aside id="try-ai" data-animate data-delay="1">
            <AiTryDemo />
          </aside>
        </div>
      </section>

      <section className="section-wrap" id="features">
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">What ships in the latest BYSEL Android app</h2>
              <p className="section-copy">
                Practice Ideas, habit tracking, practice credit, and after-hours paper — matching the live app, not a brochure.
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

      <section className="section-wrap" id="live-heatmap">
        <div className="site-container split-grid">
          <div data-animate>
            <LiveHeatmap />
          </div>

          <div className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">In the Android app</h2>
              <span className="status-chip live">Full product</span>
            </div>
            <p className="mini-muted">Web demos are a preview. The app unlocks the full practice loop.</p>

            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              {MARKET_SIGNALS.map((signal) => (
                <div key={signal.title} className="info-row">
                  <p className="info-title">{signal.title}</p>
                  <p className="info-copy">{signal.copy}</p>
                </div>
              ))}
              <div className="info-row">
                <p className="info-title">Scanner / BYSEL Score</p>
                <p className="info-copy">
                  Compare names for education. Scores are not a buy list and missing fields stay as —.
                </p>
              </div>
            </div>

            <div className="btn-row" style={{ marginTop: "0.9rem", alignItems: "center" }}>
              <Image
                src="/bysel-logo.svg"
                alt="BYSEL app icon"
                width={48}
                height={48}
                style={{ borderRadius: 12 }}
              />
              <Link href={PLAY_STORE_URL} className="btn-primary" target="_blank" rel="noreferrer">
                Get the app
              </Link>
            </div>
          </div>
        </div>
      </section>

      <section className="section-wrap">
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">The BYSEL practice loop</h2>
              <p className="section-copy">
                Idea → Paper Buy / Alert @ SL → Review. Built for habit, not hype — educational drills only.
              </p>
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
                Install BYSEL Trader, add practice credit in seconds, and run today&apos;s Practice Ideas — even after
                market close. Educational use only — not investment advice and not a SEBI-registered broker.
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
          <Image src="/bysel-logo.svg" alt="BYSEL app icon" width={72} height={72} priority />
          <p className="mini-muted" style={{ marginTop: "0.7rem" }}>
            www.byseltrader.com · Paper practice only. Past market data does not predict future results.
          </p>
        </div>
      </section>
    </main>
  );
}
