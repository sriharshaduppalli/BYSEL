import type { Metadata, Viewport } from "next";
import { IBM_Plex_Mono, Manrope, Syne } from "next/font/google";
import "./globals.css";
import Navbar from "../components/Navbar";
import Link from "next/link";

const headlineFont = Syne({
  variable: "--font-syne",
  subsets: ["latin"],
  weight: ["600", "700", "800"],
});

const bodyFont = Manrope({
  variable: "--font-manrope",
  subsets: ["latin"],
  weight: ["400", "500", "600", "700", "800"],
});

const monoFont = IBM_Plex_Mono({
  variable: "--font-ibm-plex-mono",
  subsets: ["latin"],
  weight: ["400", "500"],
});

export const metadata: Metadata = {
  metadataBase: new URL("https://www.byseltrader.com"),
  title: {
    default: "BYSEL Trader | AI-First Trading Simulator",
    template: "%s | BYSEL Trader",
  },
  description:
    "Train in realistic Indian markets with BYSEL's AI guidance, live market context, and skill-first execution workflows.",
  keywords: [
    "BYSEL",
    "paper trading",
    "stock simulator",
    "indian stock market app",
    "ai trading assistant",
  ],
  icons: {
    icon: "/ic_launcher.png",
  },
};

export const viewport: Viewport = {
  themeColor: "#0e636f",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const year = new Date().getFullYear();

  return (
    <html lang="en">
      <body className={`${headlineFont.variable} ${bodyFont.variable} ${monoFont.variable} antialiased`}>
        <div className="site-shell">
          <Navbar />
          <div className="site-content">{children}</div>

          <footer className="site-footer">
            <div className="site-container footer-grid">
              <div>
                <p className="footer-title">BYSEL Trader</p>
                <p className="mini-muted">
                  Build execution discipline with live context, structured AI feedback, and a simulator designed for Indian market behavior.
                </p>
              </div>

              <div>
                <p className="footer-title">Product</p>
                <Link className="footer-link" href="/features">Features</Link>
                <Link className="footer-link" href="/markets">Markets</Link>
                <Link className="footer-link" href="/pricing">Pricing</Link>
              </div>

              <div>
                <p className="footer-title">Company</p>
                <Link className="footer-link" href="/about">About</Link>
                <Link className="footer-link" href="/careers">Careers</Link>
                <Link className="footer-link" href="/blog">Blog</Link>
                <Link className="footer-link" href="/support">Support</Link>
                <Link className="footer-link" href="/legal/privacy">Privacy</Link>
                <Link className="footer-link" href="/legal/terms">Terms</Link>
                <p className="mini-muted">Copyright {year} BYSEL Trader</p>
              </div>
            </div>
          </footer>
        </div>
      </body>
    </html>
  );
}
