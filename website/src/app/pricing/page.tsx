import Link from "next/link";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const PLANS = [
  {
    label: "Starter",
    price: "Free",
    note: "Closed testing & early access",
    items: [
      "Paper trading wallet and portfolio",
      "Home pulse, watchlist, movers",
      "Sector heatmap (live when market open)",
      "AI assistant with Buy / Set Alert actions",
      "Username + email password login",
    ],
    cta: "Get the App",
    href: PLAY_STORE_URL,
    featured: false,
  },
  {
    label: "Pro",
    price: "Coming soon",
    note: "For serious solo practice",
    items: [
      "Deeper AI coaching workflows",
      "Advanced journal & analytics",
      "Priority alerts and event windows",
      "Portfolio optimization tools",
    ],
    cta: "Join Waitlist",
    href: "/support",
    featured: true,
  },
  {
    label: "Desk",
    price: "Custom",
    note: "Academies and trading teams",
    items: [
      "Team workspaces and role access",
      "Shared performance dashboards",
      "Dedicated onboarding",
      "Custom reporting",
    ],
    cta: "Talk to Us",
    href: "/support",
    featured: false,
  },
];

const FAQ = [
  {
    question: "Is BYSEL a SEBI-registered broker?",
    answer:
      "No. BYSEL Trader is an educational paper-trading app. It does not place live brokerage orders or provide registered investment advice.",
  },
  {
    question: "How should I sign in while OTP SMS is unreliable?",
    answer:
      "Use Register with username, email, and password. Phone OTP remains available when Firebase SMS delivery works for your number.",
  },
  {
    question: "Where do I download the app?",
    answer:
      "Android builds are distributed via Google Play for package com.bysel.trader. Public listing depends on Play Console production approval / closed testing access.",
  },
];

export default function Pricing() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Pricing</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)" }}>
            Start free while we grow with testers.
          </h1>
          <p className="lead">
            Core paper trading and AI practice are available in the Android app today. Paid tiers will unlock deeper coaching when we open wider distribution.
          </p>

          <div className="price-grid" style={{ marginTop: "1.2rem" }}>
            {PLANS.map((plan, index) => (
              <article
                key={plan.label}
                className={`glass-card price-card${plan.featured ? " featured" : ""}`}
                data-animate
                data-delay={String(Math.min(index, 4))}
              >
                <p className="feature-kicker">{plan.note}</p>
                <h2 className="feature-title">{plan.label}</h2>
                <p className="stat-value" style={{ margin: "0.4rem 0" }}>
                  {plan.price}
                </p>
                <ul className="price-list">
                  {plan.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
                <Link
                  href={plan.href}
                  className={plan.featured ? "btn-primary" : "btn-neutral"}
                  target={plan.href.startsWith("http") ? "_blank" : undefined}
                  rel={plan.href.startsWith("http") ? "noreferrer" : undefined}
                  style={{ marginTop: "1rem", display: "inline-flex" }}
                >
                  {plan.cta}
                </Link>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container">
          <h2 className="section-title">FAQ</h2>
          <div className="feature-grid" style={{ marginTop: "1rem" }}>
            {FAQ.map((item) => (
              <article key={item.question} className="glass-card feature-card">
                <h3 className="feature-title">{item.question}</h3>
                <p className="feature-copy">{item.answer}</p>
              </article>
            ))}
          </div>
        </div>
      </section>
    </main>
  );
}
