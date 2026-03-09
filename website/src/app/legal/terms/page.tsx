import Link from "next/link";

export default function TermsOfService() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Legal</span>
          <h1 className="page-title" style={{ fontSize: "clamp(1.9rem, 5vw, 3rem)" }}>
            Terms of Service
          </h1>
          <p className="lead">These terms explain how BYSEL services can be used and what responsibilities apply to all users.</p>

          <div className="legal-stack" style={{ marginTop: "1rem" }}>
            <article className="glass-card legal-card">
              <h2 className="feature-title">Educational use only</h2>
              <p className="feature-copy">
                BYSEL is a simulation and education platform. It does not execute live brokerage orders and does not provide personalized financial advice.
              </p>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">Account responsibilities</h2>
              <ul className="list-tight">
                <li>Provide accurate registration details.</li>
                <li>Protect your account credentials and access devices.</li>
                <li>Report suspicious activity immediately to support.</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">Acceptable conduct</h2>
              <ul className="list-tight">
                <li>No abuse, reverse engineering, or unauthorized access attempts.</li>
                <li>No content that violates applicable laws or third-party rights.</li>
                <li>Violations can lead to temporary or permanent account suspension.</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">Changes and contact</h2>
              <p className="feature-copy">
                Terms may be updated as features evolve. Continued use after updates means acceptance of the revised terms.
              </p>
              <div className="btn-row" style={{ marginTop: "0.8rem" }}>
                <Link href="/legal/privacy" className="btn-neutral">
                  View Privacy Policy
                </Link>
                <Link href="/support" className="btn-primary">
                  Contact Support
                </Link>
              </div>
            </article>
          </div>
        </div>
      </section>
    </main>
  );
}
