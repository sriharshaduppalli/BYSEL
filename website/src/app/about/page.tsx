import Link from "next/link";

const PRINCIPLES = [
  {
    title: "Process over prediction",
    copy: "We teach routines that survive uncertain markets instead of relying on one-off calls.",
  },
  {
    title: "Simulation before capital",
    copy: "Confidence should come from repeatable behavior in realistic Indian-market conditions before real money is at risk.",
  },
  {
    title: "Clarity in every metric",
    copy: "Users deserve clear explanations — AI levels, portfolio health, heatmap mood — not black-box hype.",
  },
];

export default function About() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">About BYSEL</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)" }}>
            Building confident traders through structured practice.
          </h1>
          <p className="lead">
            BYSEL Trader is an Android paper-trading and market-learning product for Indian equities.
            We are not a SEBI-registered broker or investment adviser — the app is educational and simulation-first.
          </p>

          <div className="split-grid" style={{ marginTop: "1.15rem" }}>
            <article className="glass-card hero-panel" data-animate>
              <h2 className="panel-title">Our Mission</h2>
              <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
                Help users develop disciplined execution habits with Practice Ideas, Idea → Trade → Review loops,
                AI-assisted analysis, live NSE context, and clear paper-trading feedback — even after market close.
              </p>
            </article>

            <article className="glass-card hero-panel" data-animate data-delay="1">
              <h2 className="panel-title">Our Vision</h2>
              <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
                Become the most trusted simulation-first platform for market learners across India —
                then expand carefully into regulated live trading only when licensing is in place.
              </p>
            </article>
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container">
          <div className="section-head">
            <div>
              <h2 className="section-title">Principles that shape the product</h2>
            </div>
          </div>

          <div className="feature-grid">
            {PRINCIPLES.map((item, index) => (
              <article key={item.title} className="glass-card feature-card" data-animate data-delay={String(Math.min(index + 1, 4))}>
                <h3 className="feature-title">{item.title}</h3>
                <p className="feature-copy">{item.copy}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container split-grid">
          <article className="glass-card hero-panel" data-animate>
            <h2 className="panel-title">Leadership</h2>
            <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
              Sri Harsha Duppalli leads BYSEL with a blend of technology depth and market-learning focus,
              pushing for a product that teaches execution discipline at scale.
            </p>
          </article>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <h2 className="panel-title">Based in Hyderabad</h2>
            <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
              BYSEL Services · Kukatpally, Hyderabad, Telangana, India.
              Reach us at{" "}
              <Link href="mailto:support@byseltrader.com" style={{ color: "var(--brand)" }}>
                support@byseltrader.com
              </Link>
              .
            </p>
            <div className="btn-row" style={{ marginTop: "0.9rem" }}>
              <Link href="/careers" className="btn-primary">
                Careers
              </Link>
              <Link href="/support" className="btn-neutral">
                Support
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
