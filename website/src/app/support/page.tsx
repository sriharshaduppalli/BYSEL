import Link from "next/link";

const SUPPORT_CHANNELS = [
  {
    title: "General Support",
    copy: "Questions about onboarding, accounts, and simulator setup.",
    detail: "support@byseltrader.com",
  },
  {
    title: "Billing Help",
    copy: "Subscription changes, invoices, and plan upgrades.",
    detail: "billing@byseltrader.com",
  },
  {
    title: "Partnerships",
    copy: "Trading academies, institutions, and desk-level deployments.",
    detail: "partners@byseltrader.com",
  },
];

const FAQ = [
  {
    question: "How quickly does support respond?",
    answer: "Most requests receive a first response within one business day. Pro and Desk plans receive priority handling.",
  },
  {
    question: "Can I recover my simulation history?",
    answer: "Yes. If you changed devices or accounts, contact support and include your registered email address.",
  },
  {
    question: "Do you provide strategy advice?",
    answer: "We provide educational guidance and process feedback. BYSEL does not offer personalized investment advice.",
  },
  {
    question: "Where can I report bugs?",
    answer: "Use the in-app feedback option or email support with screenshots and the app version number.",
  },
];

export default function Support() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Support Center</span>
          <h1 className="page-title" style={{ fontSize: "clamp(2rem, 5vw, 3.1rem)" }}>
            Need help? We are here to keep your learning flow smooth.
          </h1>
          <p className="lead">
            Reach out for onboarding, billing, technical issues, or product guidance. We prioritize clarity and fast follow-through.
          </p>

          <div className="feature-grid" style={{ marginTop: "1.15rem" }}>
            {SUPPORT_CHANNELS.map((channel, index) => (
              <article key={channel.title} className="glass-card feature-card" data-animate data-delay={String(Math.min(index, 4))}>
                <h2 className="feature-title">{channel.title}</h2>
                <p className="feature-copy">{channel.copy}</p>
                <p className="info-title" style={{ marginTop: "0.55rem" }}>
                  {channel.detail}
                </p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="section-wrap" style={{ paddingTop: "0.2rem" }}>
        <div className="site-container split-grid">
          <article className="glass-card hero-panel" data-animate>
            <h2 className="panel-title">Frequently Asked Questions</h2>
            <div className="stack-grid" style={{ marginTop: "0.7rem" }}>
              {FAQ.map((item) => (
                <div key={item.question} className="info-row">
                  <p className="info-title">{item.question}</p>
                  <p className="info-copy">{item.answer}</p>
                </div>
              ))}
            </div>
          </article>

          <article className="glass-card hero-panel" data-animate data-delay="1">
            <div className="panel-head">
              <h2 className="panel-title">Before you contact us</h2>
              <span className="status-chip warn">Checklist</span>
            </div>
            <ul className="list-tight">
              <li>Share your app version and device model.</li>
              <li>Include screenshots for UI or login issues.</li>
              <li>Mention the exact time and action that failed.</li>
              <li>Tell us if the issue is reproducible or intermittent.</li>
            </ul>

            <div className="btn-row" style={{ marginTop: "1rem" }}>
              <Link href="https://play.google.com/store" className="btn-primary" target="_blank" rel="noreferrer">
                Install Latest Build
              </Link>
              <Link href="/legal/terms" className="btn-neutral">
                Read Terms
              </Link>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
