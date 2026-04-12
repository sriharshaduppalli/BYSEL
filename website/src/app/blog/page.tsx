import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Blog — Trading Insights & Market Education",
  description:
    "Learn stock trading strategies, risk management, market analysis, and trading psychology. Free educational content for Indian market traders.",
};

const POSTS = [
  {
    slug: "why-ai-trading-assistant",
    title: "Why Every Indian Trader Needs an AI Trading Assistant in 2026",
    excerpt:
      "Discover how AI-powered trading assistants are transforming stock trading in India with real-time analysis and behavioral coaching.",
    tag: "AI & Technology",
    date: "April 4, 2026",
    readTime: "6 min read",
  },
  {
    slug: "beginners-guide-indian-stock-market",
    title: "Complete Beginner's Guide to Indian Stock Market Trading",
    excerpt:
      "New to trading? Learn the essentials of NSE, BSE, demat accounts, order types, and how to start your journey the right way.",
    tag: "Beginner Guide",
    date: "April 3, 2026",
    readTime: "8 min read",
  },
  {
    slug: "risk-management-rules-trading",
    title: "5 Risk Management Rules That Protect Your Trading Capital",
    excerpt:
      "The essential risk management strategies used by professional traders — position sizing, stop losses, and portfolio rules.",
    tag: "Risk Management",
    date: "April 2, 2026",
    readTime: "5 min read",
  },
  {
    slug: "intraday-trading-routine",
    title: "How to Build a Winning Intraday Trading Routine for Indian Markets",
    excerpt:
      "A step-by-step framework for structuring your trading day — pre-market prep, execution rules, and post-market review.",
    tag: "Execution",
    date: "April 1, 2026",
    readTime: "7 min read",
  },
  {
    slug: "sector-rotation-indian-markets",
    title: "Understanding Sector Rotation in Indian Markets",
    excerpt:
      "Learn how to identify and profit from sector rotation in NSE/BSE. Understand money flow between banking, IT, pharma, and more.",
    tag: "Market Context",
    date: "March 30, 2026",
    readTime: "6 min read",
  },
  {
    slug: "trading-psychology-discipline",
    title: "Trading Psychology: How to Control Emotions and Trade with Discipline",
    excerpt:
      "Master the mental game of trading. Overcome fear, greed, FOMO, and revenge trading to become consistently profitable.",
    tag: "Performance",
    date: "March 28, 2026",
    readTime: "7 min read",
  },
];

const TOPICS = [
  "Risk Management",
  "Trade Psychology",
  "Sector Analysis",
  "Intraday Strategies",
  "AI in Trading",
  "Beginner Guides",
  "Portfolio Structure",
];

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
            Practical frameworks, trade-behavior lessons, and market education designed to sharpen your
            trading process and build lasting discipline.
          </p>

          <div className="blog-grid" style={{ marginTop: "1.2rem" }}>
            {POSTS.map((post, index) => (
              <Link
                key={post.slug}
                href={`/blog/${post.slug}`}
                className="blog-card-link"
              >
                <article
                  className="glass-card blog-card"
                  data-animate
                  data-delay={String(Math.min(index, 4))}
                >
                  <p className="feature-kicker">{post.tag}</p>
                  <h2 className="feature-title">{post.title}</h2>
                  <p className="feature-copy">{post.excerpt}</p>
                  <p className="blog-card-meta">
                    {post.date} &middot; {post.readTime}
                  </p>
                </article>
              </Link>
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

          <div className="glass-card cta-block">
            <h2 className="panel-title">Start practicing what you learn</h2>
            <p className="feature-copy">
              Theory without practice is useless. Download BYSEL Trader and apply these concepts in a
              real market simulator with AI coaching.
            </p>
            <div className="btn-row" style={{ marginTop: "0.8rem" }}>
              <Link
                href="https://play.google.com/store"
                className="btn-primary"
                target="_blank"
                rel="noreferrer"
              >
                Download BYSEL Trader
              </Link>
            </div>
          </div>
        </div>
      </section>
    </main>
  );
}
