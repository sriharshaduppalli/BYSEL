import Link from "next/link";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const CORE_MODULES = [
  {
    kicker: "Home",
    title: "Practice Ideas",
    copy: "Educational drills with Entry / Stop / Target, coaching notes, Paper Buy, and Alert @ SL. Process practice — not tip-selling.",
  },
  {
    kicker: "Habit",
    title: "Today's Practice strip",
    copy: "Track Idea → Trade → Review for the day so paper practice becomes a repeatable routine instead of random taps.",
  },
  {
    kicker: "Wallet",
    title: "Practice credit",
    copy: "Fund the simulation wallet instantly without UPI. Optional UPI demo remains; real money is never required to practice.",
  },
  {
    kicker: "Trade",
    title: "After-hours paper fills",
    copy: "When NSE is closed, paper orders still execute using last session prices so evenings and weekends stay useful.",
  },
  {
    kicker: "AI",
    title: "Stock Assistant Chat",
    copy: "Ask prices, buy/sell bias, valuation, and comparisons. Replies can include Entry / Target / Stop-Loss with Buy and Set Alert buttons.",
  },
  {
    kicker: "Heatmap",
    title: "Sector Sentiment Map",
    copy: "NSE sector heatmap with mood and breadth. Targets ~1–2s refresh while the market is open; last-session snapshot when closed.",
  },
  {
    kicker: "Portfolio",
    title: "Holdings & educational stance",
    copy: "Track positions, PnL, and portfolio health — plus practice actions like trim, review risk, and journal holds.",
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
    title: "Add practice credit",
    copy: "Open Trade → Add Funds → Practice credit. Instant paper capital — no UPI needed to start drills.",
  },
  {
    title: "Run a Practice Idea",
    copy: "On Home, pick a drill, tap Paper Buy or Alert @ SL, then complete the short practice review.",
  },
  {
    title: "Review discipline",
    copy: "Use Today's Practice, journal, alerts, and portfolio health to tighten process before real capital.",
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
            BYSEL Trader is an Indian-market paper-trading and learning app: Practice Ideas, habit review,
            instant practice credit, after-hours paper fills, AI chat, and live context — not a SEBI-registered brokerage.
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
                <p className="info-title">India-first practice cockpit</p>
                <p className="info-copy">
                  Practice Ideas and habit review for NSE learners — not a tip marketplace and not a live broker clone.
                </p>
              </div>
              <div className="info-row">
                <p className="info-title">Paper anytime</p>
                <p className="info-copy">
                  Instant practice credit and after-hours fills using last session prices keep drills available beyond 3:30 PM IST.
                </p>
              </div>
              <div className="info-row">
                <p className="info-title">AI that can act (in paper)</p>
                <p className="info-copy">Buy / Set Alert from chat responses, with confirmation before paper orders execute.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Educational stance</p>
                <p className="info-copy">
                  Virtual wallet and SEBI-aware disclaimers. No live brokerage order routing in this product.
                </p>
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
