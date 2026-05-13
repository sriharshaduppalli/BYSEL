import Link from "next/link";
import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description:
    "BYSEL Trader privacy policy. Learn how we collect, use, and protect your data in our AI-powered stock trading simulator app.",
};

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
            BYSEL Services (&ldquo;BYSEL,&rdquo; &ldquo;we,&rdquo; &ldquo;us,&rdquo; or &ldquo;our&rdquo;)
            is committed to handling user data responsibly, transparently, and only for product operation
            and improvement.
          </p>
          <p className="mini-muted" style={{ marginTop: "0.5rem" }}>
            Last updated: April 4, 2026 &middot; Effective immediately
          </p>

          <div className="legal-stack" style={{ marginTop: "1rem" }}>
            <article className="glass-card legal-card">
              <h2 className="feature-title">1. Information We Collect</h2>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>Account Information</h3>
              <ul className="list-tight">
                <li>Phone number for OTP-based account authentication</li>
                <li>Display name (optional, user-provided)</li>
              </ul>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>Usage Data</h3>
              <ul className="list-tight">
                <li>Simulation activity (paper trades, watchlists, portfolio configurations)</li>
                <li>Feature usage metrics and interaction patterns</li>
                <li>AI assistant conversation history for improving response quality</li>
              </ul>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>Technical Data</h3>
              <ul className="list-tight">
                <li>Device type, operating system version, and app version</li>
                <li>Crash logs and performance diagnostics</li>
                <li>IP address (for security and abuse prevention)</li>
              </ul>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>What We Do NOT Collect</h3>
              <ul className="list-tight">
                <li>Real brokerage account credentials or financial information</li>
                <li>Contacts, photos, files, or other personal content from your device</li>
                <li>Precise location (GPS) data</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">2. How We Use Your Data</h2>
              <ul className="list-tight">
                <li>Deliver core product functionality — simulations, AI analysis, and portfolio tracking</li>
                <li>Authenticate your account via phone number OTP verification</li>
                <li>Provide personalized AI-powered insights and trading education</li>
                <li>Monitor platform reliability and prevent misuse</li>
                <li>Improve app performance, features, and user experience</li>
                <li>Send optional product updates and educational content (you can opt out)</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">3. Data Sharing</h2>
              <p style={{ fontSize: "0.92rem", color: "var(--ink)" }}>
                We do not sell or rent your personal data. We share data only in these limited cases:
              </p>
              <ul className="list-tight">
                <li><strong>SMS delivery providers:</strong> Your phone number is shared with our SMS
                  service provider solely for OTP delivery</li>
                <li><strong>Cloud infrastructure:</strong> Data is stored on secure cloud servers for
                  app operation</li>
                <li><strong>Legal requirements:</strong> If required by Indian law or legal process</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">4. Data Storage and Security</h2>
              <ul className="list-tight">
                <li>Data is stored on encrypted servers with industry-standard security measures</li>
                <li>Authentication uses secure OTP verification — we do not store passwords</li>
                <li>Access to user data is restricted to authorized personnel only</li>
                <li>We conduct regular security reviews of our infrastructure</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">5. Data Retention</h2>
              <ul className="list-tight">
                <li>Account data is retained while your account is active</li>
                <li>Upon account deletion, personal data is removed within 30 days</li>
                <li>Aggregated, anonymized analytics may be retained for product improvement</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">6. Your Rights</h2>
              <ul className="list-tight">
                <li>Request access to your personal data at any time</li>
                <li>Request correction of inaccurate data</li>
                <li>Request deletion of your account and all associated data</li>
                <li>Opt out of optional communications</li>
                <li>Raise concerns regarding privacy handling at any time</li>
              </ul>
              <p style={{ fontSize: "0.92rem", color: "var(--ink)", marginTop: "0.5rem" }}>
                To exercise any of these rights, contact us at{" "}
                <Link href="mailto:support@byseltrader.com" style={{ color: "var(--brand)" }}>
                  support@byseltrader.com
                </Link>
              </p>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">7. Children&apos;s Privacy</h2>
              <p style={{ fontSize: "0.92rem", color: "var(--ink)" }}>
                BYSEL Trader is not intended for users under the age of 18. We do not knowingly collect
                data from children. If you believe a minor has provided us with personal information,
                please contact us and we will promptly delete it.
              </p>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">8. Changes to This Policy</h2>
              <p style={{ fontSize: "0.92rem", color: "var(--ink)" }}>
                We may update this privacy policy from time to time. Changes will be posted on this page
                with an updated effective date. Continued use of BYSEL Trader after changes constitutes
                acceptance of the revised policy.
              </p>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">9. Contact Us</h2>
              <p style={{ fontSize: "0.92rem", color: "var(--ink)" }}>
                If you have questions about this privacy policy or our data practices:
              </p>
              <ul className="list-tight">
                <li>Email: support@byseltrader.com</li>
                <li>Developer: BYSEL Services</li>
                <li>Website: byseltrader.com</li>
              </ul>
              <div className="btn-row" style={{ marginTop: "0.8rem" }}>
                <Link href="mailto:support@byseltrader.com" className="btn-primary">
                  Privacy Requests
                </Link>
                <Link href="/legal/terms" className="btn-neutral">
                  Terms of Service
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
