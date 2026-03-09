import Link from "next/link";

const LICENSES = [
  "Next.js - MIT License",
  "React - MIT License",
  "Tailwind CSS - MIT License",
  "TypeScript - Apache License 2.0",
  "Other dependencies - See package-level license references",
];

export default function Licenses() {
  return (
    <main>
      <section className="section-wrap">
        <div className="site-container">
          <span className="eyebrow">Legal</span>
          <h1 className="page-title" style={{ fontSize: "clamp(1.9rem, 5vw, 3rem)" }}>
            Open Source Licenses
          </h1>
          <p className="lead">
            BYSEL is built with open source technologies. We acknowledge and respect the licensing terms of these components.
          </p>

          <div className="legal-stack" style={{ marginTop: "1rem" }}>
            <article className="glass-card legal-card">
              <h2 className="feature-title">Primary dependencies</h2>
              <ul className="list-tight">
                {LICENSES.map((license) => (
                  <li key={license}>{license}</li>
                ))}
              </ul>
            </article>

            <article className="glass-card legal-card">
              <h2 className="feature-title">Additional attribution</h2>
              <p className="feature-copy">
                For complete third-party notices, review the dependency manifests distributed with each release.
              </p>
              <div className="btn-row" style={{ marginTop: "0.8rem" }}>
                <Link href="/legal/terms" className="btn-neutral">
                  Terms of Service
                </Link>
                <Link href="/legal/privacy" className="btn-primary">
                  Privacy Policy
                </Link>
              </div>
            </article>
          </div>
        </div>
      </section>
    </main>
  );
}
