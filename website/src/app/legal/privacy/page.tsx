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
            Last updated: August 15, 2026 &middot; Effective immediately
          </p>

          <div className="legal-stack" style={{ marginTop: "1rem" }}>
            <article className="glass-card legal-card">
              <h2 className="feature-title">1. Information We Collect</h2>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>Account Information</h3>
              <ul className="list-tight">
                <li>Username and email for password-based account authentication</li>
                <li>Phone number when you choose OTP-based sign-in</li>
                <li>Display name (optional, user-provided)</li>
              </ul>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>Usage Data</h3>
              <ul className="list-tight">
                <li>Simulation activity (paper trades, watchlists, alerts, portfolio configurations)</li>
                <li>Private stock notes you save on a symbol</li>
                <li>Firebase Analytics usage events (app opens / basic diagnostics — not advertising)</li>
                <li>AI assistant conversation history for improving response quality</li>
              </ul>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>Technical Data</h3>
              <ul className="list-tight">
                <li>Device type, operating system version, and app version</li>
                <li>Firebase Cloud Messaging device token (price-alert notifications you create)</li>
                <li>IP address (for security and abuse prevention)</li>
              </ul>

              <h3 style={{ fontSize: "0.95rem", marginTop: "0.7rem" }}>What We Do NOT Collect</h3>
              <ul className="list-tight">
                <li>Real brokerage account credentials or live bank account details</li>
                <li>Contacts, photos, files, or other personal content from your device</li>
                <li>Precise location (GPS) data</li>
                <li>Advertising ID (the Android app disables Google advertising ID collection)</li>
                <li>Biometric images or templates — optional fingerprint/face unlock uses your device OS APIs only</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">2. How We Use Your Data</h2>
              <ul className="list-tight">
                <li>Deliver core product functionality — simulations, AI analysis, and portfolio tracking</li>
                <li>Authenticate your account via email/password and/or phone OTP, and maintain a signed-in session until you Sign out</li>
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
                <li><strong>SMS delivery providers and Firebase Authentication:</strong> When you use phone OTP, your phone number is shared solely for login codes</li>
                <li><strong>Firebase Cloud Messaging:</strong> Device tokens are used to deliver price-alert notifications</li>
                <li><strong>Firebase Analytics:</strong> Basic app-usage diagnostics (not advertising; no Advertising ID)</li>
                <li><strong>AI providers</strong> (for example Groq / Gemini / our Indian Stock LLM stack): when you use AI chat; queries are stock-related prompts processed to return answers. We remain responsible for this processing under Play User Data requirements (limited use; not sold as marketing data)</li>
                <li><strong>Cloud infrastructure:</strong> Data is stored on secure cloud servers for
                  app operation</li>
                <li><strong>Legal requirements:</strong> If required by Indian law or legal process</li>
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">4. Data Storage and Security</h2>
              <ul className="list-tight">
                <li>Data is stored on encrypted servers with industry-standard security measures</li>
                <li>Passwords are stored only as secure one-way hashes — never in plain text</li>
                <li>Phone OTP verification is available as an alternate sign-in method</li>
                <li>Session tokens are used to keep you signed in; they can be revoked when you Sign out or on security events</li>
                <li>Optional biometric lock stays on-device and does not send biometric data to BYSEL</li>
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
