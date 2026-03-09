import Link from "next/link";

const POSTS = [
  {
    title: "How to build an intraday checklist that actually improves results",
    excerpt: "A practical framework to reduce impulsive entries and improve execution consistency.",
    tag: "Execution",
  },
  {
    title: "Reading market breadth before committing to directional trades",
    excerpt: "Use participation depth to validate your thesis before taking size.",
    tag: "Market Context",
  },
  {
    title: "Post-trade reviews: what top learners track every day",
    excerpt: "Move beyond PnL and evaluate behavior, setup quality, and adherence metrics.",
    tag: "Performance",
  },
];

const TOPICS = ["Risk Management", "Trade Psychology", "System Design", "Portfolio Structure", "Volatility Playbooks"];

export default function Blog() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">BYSEL Journal</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.1rem)" }}>
            Insights for disciplined market learners.
          </h1>
          <p className="lead">
            Practical frameworks, trade-behavior lessons, and product updates designed to sharpen your simulation workflow.
          </p>

          <div className="feature-grid" style={{ marginTop: "1.2rem" }}>
            {POSTS.map((post, index) => (
              <article key={post.title} className="glass-card feature-card" data-animate data-delay={String(Math.min(index, 4))}>
                <p className="feature-kicker">{post.tag}</p>
                <h2 className="feature-title">{post.title}</h2>
                <p className="feature-copy">{post.excerpt}</p>
                <div className="btn-row" style={{ marginTop: "0.8rem" }}>
                  <Link href="/support" className="btn-neutral">
                    Request Full Article
                  </Link>
                </div>
              </article>
            ))}
          </div>

          <div className="glass-card cta-block" style={{ marginTop: "1rem" }}>
            <h2 className="panel-title">Explore topics</h2>
            <div className="pill-row">
              {TOPICS.map((topic) => (
                <span key={topic} className="tag-pill">
                  {topic}
                </span>
              ))}
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
