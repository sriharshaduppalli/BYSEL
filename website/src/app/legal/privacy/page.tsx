import Link from "next/link";

export default function PrivacyPolicy() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Legal</span>
          <h1 className="page-title" style={{ fontSize: "clamp(1.9rem, 5vw, 3rem)" }}>
            Privacy Policy
          </h1>
          <p className="lead">
            BYSEL is committed to handling user data responsibly, transparently, and only for product operation and improvement.
          </p>

          <div className="legal-stack" style={{ marginTop: "1rem" }}>
            <article className="glass-card legal-card">
              <h2 className="feature-title">What we collect</h2>
              <ul className="list-tight">
                <li>Account information required for login and support communication.</li>
                <li>Simulation activity and product interaction metrics for learning analytics.</li>
                <li>Technical diagnostics used to improve app stability and performance.</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">How data is used</h2>
              <ul className="list-tight">
                <li>Deliver core product functionality and personalized guidance.</li>
                <li>Monitor reliability and prevent misuse of the platform.</li>
                <li>Improve educational workflows and user experience quality.</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">Your rights</h2>
              <ul className="list-tight">
                <li>Request account data access, correction, or deletion.</li>
                <li>Contact us to manage notification and communication preferences.</li>
                <li>Raise concerns regarding privacy handling at any time.</li>
              </ul>
              <div className="btn-row" style={{ marginTop: "0.8rem" }}>
                <Link href="mailto:support@byseltrader.com" className="btn-primary">
                  Privacy Requests
                </Link>
                <Link href="/legal/licenses" className="btn-neutral">
                  Open Source Licenses
                </Link>
              </div>
            </article>
          </div>
        </div>
      </section>
    </main>
  );
}
