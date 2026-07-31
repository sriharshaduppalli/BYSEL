import Link from "next/link";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const PLANS = [
  {
    label: "Starter",
    price: "Free",
    note: "Closed testing & early access",
    items: [
      "Practice Ideas with Paper Buy & Alert @ SL",
      "Today's Practice habit loop (Idea → Review)",
      "Instant practice credit (no UPI required)",
      "After-hours paper fills at last session prices",
      "Sector heatmap + AI assistant with Buy / Alert",
      "Email/password or phone OTP — stay signed in",
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
    question: "Do I need UPI to add funds?",
    answer:
      "No. Use Add Funds → Practice credit for instant simulation capital. UPI providers are an optional demo path only — paper trading never requires real payment.",
  },
  {
    question: "Can I paper-trade after market close?",
    answer:
      "Yes. BYSEL allows paper fills after 3:30 PM IST and on weekends using last session prices, so practice drills stay available outside NSE hours.",
  },
  {
    question: "Phone OTP or email login — which should I use?",
    answer:
      "Either works. Register with username, email, and password for a reliable path anytime. Phone OTP is available when Firebase SMS delivery works for your number. Both keep you signed in afterward.",
  },
  {
    question: "Do I stay signed in after login?",
    answer:
      "Yes. After email/password or OTP login, your account session persists across app restarts and brief network changes. You should not need to sign in every time you open the app. Optional biometric lock only unlocks the screen — it does not sign you out.",
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
            Core paper trading — including Practice Ideas, practice credit, and after-hours fills — is available in the Android app today. Paid tiers will unlock deeper coaching when we open wider distribution.
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
