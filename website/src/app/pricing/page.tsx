import Link from "next/link";

const PLANS = [
  {
    label: "Starter",
    price: "Free",
    note: "For first-time learners",
    items: [
      "Core simulator with delayed review",
      "Basic market pulse and heatmap",
      "Limited AI guidance prompts",
      "Single paper portfolio",
    ],
    cta: "Start Free",
    href: "https://play.google.com/store",
    featured: false,
  },
  {
    label: "Pro",
    price: "INR 499/mo",
    note: "For disciplined solo traders",
    items: [
      "Full AI execution coach",
      "Advanced analytics and trade journal",
      "Priority market event alerts",
      "Multi-portfolio optimization",
    ],
    cta: "Upgrade to Pro",
    href: "/support",
    featured: true,
  },
  {
    label: "Desk",
    price: "Custom",
    note: "For academies and trading teams",
    items: [
      "Team workspaces and role access",
      "Shared performance dashboards",
      "Dedicated onboarding and support",
      "Custom reporting exports",
    ],
    cta: "Talk to Sales",
    href: "/support",
    featured: false,
  },
];

const FAQ = [
  {
    question: "Can I cancel anytime?",
    answer: "Yes. Pro subscriptions can be canceled from your billing panel without lock-in periods.",
  },
  {
    question: "Do you offer student pricing?",
    answer: "Yes. Learners from recognized institutes can request discounted annual access via support.",
  },
  {
    question: "Is this for real trading?",
    answer: "BYSEL is simulation-first and education-focused. It does not place live orders for brokerage accounts.",
  },
];

export default function Pricing() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Pricing</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.2rem)" }}>
            Plans for every stage of your trading journey.
          </h1>
          <p className="lead">
            Start free, then upgrade when you want deeper analytics, stronger coaching workflows, and professional-grade process reviews.
          </p>

          <div className="price-grid" style={{ marginTop: "1.2rem" }}>
            {PLANS.map((plan, index) => (
              <article
                key={plan.label}
                className={`glass-card price-card${plan.featured ? " featured" : ""}`}
                data-animate
                data-delay={String(Math.min(index, 4))}
              >
                <p className="price-label">{plan.label}</p>
                <p className="price-value">{plan.price}</p>
                <p className="mini-muted">{plan.note}</p>

                <ul className="list-tight">
                  {plan.items.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>

                <div className="btn-row" style={{ marginTop: "0.85rem" }}>
                  <Link
                    href={plan.href}
                    className={plan.featured ? "btn-primary" : "btn-neutral"}
                    target={plan.href.startsWith("http") ? "_blank" : undefined}
                    rel={plan.href.startsWith("http") ? "noreferrer" : undefined}
                  >
                    {plan.cta}
                  </Link>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container split-grid">
          <article className="glass-card hero-panel" data-animate>
            <h2 className="panel-title">What is included in Pro</h2>
            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              <div className="info-row">
                <p className="info-title">High-frequency market context</p>
                <p className="info-copy">Heatmap updates, sector strength drift, and event flags built for intraday review.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Execution-quality analytics</p>
                <p className="info-copy">Measure adherence to your trade plan instead of only outcome statistics.</p>
              </div>
              <div className="info-row">
                <p className="info-title">Priority onboarding</p>
                <p className="info-copy">Hands-on setup help so your first week is focused and productive.</p>
              </div>
            </div>
          </article>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <h2 className="panel-title">Pricing FAQs</h2>
            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              {FAQ.map((item) => (
                <div key={item.question} className="info-row">
                  <p className="info-title">{item.question}</p>
                  <p className="info-copy">{item.answer}</p>
                </div>
              ))}
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
