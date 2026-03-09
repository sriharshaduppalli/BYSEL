import Link from "next/link";

const OPEN_ROLES = [
  {
    title: "Frontend Engineer (React/Next.js)",
    type: "Full-time",
    copy: "Build high-clarity interfaces for traders and learning workflows that scale across device sizes.",
  },
  {
    title: "Mobile Engineer (Kotlin/Compose)",
    type: "Full-time",
    copy: "Advance the Android app experience with robust state handling and simulation-driven UX.",
  },
  {
    title: "Market Education Specialist",
    type: "Contract",
    copy: "Translate market concepts into structured learning tracks, checklists, and simulation exercises.",
  },
];

const HIRING_STEPS = [
  "Intro chat to understand your background",
  "Focused task aligned with the role",
  "Team interview and collaboration discussion",
  "Final conversation with product leadership",
];

export default function Careers() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Careers</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.1rem)" }}>
            Build products that make market learning practical.
          </h1>
          <p className="lead">
            Join a team focused on simulation-first learning, strong product craft, and outcomes users can trust.
          </p>

          <div className="feature-grid" style={{ marginTop: "1.2rem" }}>
            {OPEN_ROLES.map((role, index) => (
              <article key={role.title} className="glass-card feature-card" data-animate data-delay={String(Math.min(index, 4))}>
                <p className="feature-kicker">{role.type}</p>
                <h2 className="feature-title">{role.title}</h2>
                <p className="feature-copy">{role.copy}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container split-grid">
          <article className="glass-card hero-panel" data-animate>
            <h2 className="panel-title">Hiring Process</h2>
            <ul className="list-tight">
              {HIRING_STEPS.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ul>
          </article>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <h2 className="panel-title">What we value</h2>
            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              <div className="info-row">
                <p className="info-title">Ownership mindset</p>
                <p className="info-copy">You define problems clearly and finish what you start.</p>
              </div>
              <div className="info-row">
                <p className="info-title">User-first execution</p>
                <p className="info-copy">Every feature should improve a trader workflow, not just add complexity.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Fast, thoughtful iteration</p>
                <p className="info-copy">We move quickly and keep quality high through focused, measurable improvements.</p>
              </div>
            </div>

            <div className="btn-row" style={{ marginTop: "1rem" }}>
              <Link href="mailto:careers@byseltrader.com" className="btn-primary">
                Apply via Email
              </Link>
              <Link href="/about" className="btn-neutral">
                Learn About BYSEL
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
