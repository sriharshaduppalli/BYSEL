import Link from "next/link";

const PRINCIPLES = [
  {
    title: "Process over prediction",
    copy: "We teach routines that survive uncertain markets instead of relying on one-off calls.",
  },
  {
    title: "Simulation before capital",
    copy: "Confidence should come from repeatable behavior in realistic conditions before real money is at risk.",
  },
  {
    title: "Clarity in every metric",
    copy: "Users deserve clear explanations, not black-box numbers that cannot guide better action.",
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
            BYSEL started with one conviction: trading education should be practical, measurable, and accessible to anyone serious about the craft.
          </p>

          <div className="split-grid" style={{ marginTop: "1.15rem" }}>
            <article className="glass-card hero-panel" data-animate>
              <h2 className="panel-title">Our Mission</h2>
              <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
                Help users develop disciplined execution habits with AI-assisted simulation, live market context, and clear feedback loops.
              </p>
            </article>

            <article className="glass-card hero-panel" data-animate data-delay="1">
              <h2 className="panel-title">Our Vision</h2>
              <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
                Become the most trusted simulation-first platform for market learners and trading teams across India.
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
              Sri Harsha Duppalli leads BYSEL with a blend of technology depth and market-learning focus, pushing for a product that teaches execution discipline at scale.
            </p>
          </article>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <h2 className="panel-title">Work with us</h2>
            <p className="feature-copy" style={{ marginTop: "0.55rem" }}>
              We are building a product-led team across engineering, design, and market education.
            </p>
            <div className="btn-row" style={{ marginTop: "0.95rem" }}>
              <Link href="/careers" className="btn-primary">
                View Careers
              </Link>
              <Link href="/support" className="btn-neutral">
                Contact Team
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
