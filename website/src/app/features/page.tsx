import Link from "next/link";

const CORE_MODULES = [
  {
    kicker: "Execution Prep",
    title: "Scenario Builder",
    copy: "Define market context, entry logic, and risk envelope before every session starts.",
  },
  {
    kicker: "Guidance",
    title: "AI Trade Coach",
    copy: "Get setup-specific prompts, confidence checks, and reasoning support while you simulate.",
  },
  {
    kicker: "Review",
    title: "Behavior Analytics",
    copy: "Track discipline drift, late exits, overtrading, and emotional bias across sessions.",
  },
  {
    kicker: "Risk",
    title: "Portfolio Lens",
    copy: "See concentration and sector clustering instantly with rebalance suggestions that are easy to act on.",
  },
  {
    kicker: "Context",
    title: "Live Market Pulse",
    copy: "Heatmap, breadth, and momentum layers reveal where participation is actually moving.",
  },
  {
    kicker: "Learning",
    title: "Structured Growth Paths",
    copy: "Progress from beginner to process-driven trader through guided, simulation-first milestones.",
  },
];

const LEARNING_TRACK = [
  {
    title: "Foundation",
    copy: "Learn setup types, market structure, and risk basics without exposure to real capital.",
  },
  {
    title: "Execution",
    copy: "Apply rules in live market conditions and discover where discipline breaks under pressure.",
  },
  {
    title: "Refinement",
    copy: "Use session analytics to reduce impulsive actions and tighten your process week by week.",
  },
  {
    title: "Scale",
    copy: "Build consistency with repeatable routines before transitioning to live trading environments.",
  },
];

export default function Features() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Product Surface</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)" }}>
            Features built for process quality, not hype.
          </h1>
          <p className="lead">
            BYSEL focuses on the full execution cycle: pre-trade planning, realistic simulation, and post-trade coaching that makes your next session better.
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
              <h2 className="panel-title">Learning Track</h2>
              <span className="status-chip live">Always-on</span>
            </div>
            <div className="timeline">
              {LEARNING_TRACK.map((stage, index) => (
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
              <h2 className="panel-title">Why teams choose BYSEL</h2>
            </div>
            <div className="stack-grid">
              <div className="info-row">
                <p className="info-title">Faster onboarding</p>
                <p className="info-copy">New learners adopt a structured routine from day one with guided checklists.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Clear risk behavior</p>
                <p className="info-copy">Position sizing and loss controls are visible in every simulation, not hidden in reports.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Actionable reviews</p>
                <p className="info-copy">Session summaries link outcomes to behavior so improvements are concrete and measurable.</p>
              </div>
            </div>

            <div className="btn-row" style={{ marginTop: "1rem" }}>
              <Link href="/pricing" className="btn-primary">
                Compare Plans
              </Link>
              <Link href="/support" className="btn-neutral">
                Request Demo
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
