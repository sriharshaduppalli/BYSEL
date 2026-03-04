import Image from "next/image";
import LiveHeatmap from "../components/LiveHeatmap";

export default function Home() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        {/* Try ic_launcher.png, bysel-logo.svg, or fallback */}
        <Image src="/ic_launcher.png" alt="BYSEL Logo" width={120} height={120} className="mx-auto mb-6" />
        <h1 className="text-4xl md:text-5xl font-bold mb-4 text-[var(--primary)]">BYSEL - Stock Trading Simulator</h1>
        <p className="text-lg md:text-xl mb-8 text-[var(--text)]">
          Experience modern trading with AI Assistant, portfolio optimization, live market heatmap, and advanced analytics.
        </p>
        <div className="flex flex-col md:flex-row gap-4 justify-center mb-8">
          <a href="#download" className="bg-[var(--primary)] text-white font-semibold px-6 py-3 rounded-lg shadow hover:bg-[var(--secondary)] transition">Download App</a>
          <a href="#features" className="bg-[var(--accent)] text-white font-semibold px-6 py-3 rounded-lg shadow hover:bg-[var(--primary)] transition">Explore Features</a>
        </div>
        {/* Live Heatmap Preview */}
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow mb-8">
          <h2 className="text-2xl font-semibold mb-2 text-[var(--primary)]">Live Market Heatmap</h2>
          <LiveHeatmap />
        </div>
        <div className="flex flex-wrap gap-4 justify-center mt-8">
          <a href="/features" className="underline text-[var(--primary)] font-medium">Product/Features</a>
          <a href="/markets" className="underline text-[var(--primary)] font-medium">Markets</a>
          <a href="/pricing" className="underline text-[var(--primary)] font-medium">Pricing</a>
          <a href="/legal/privacy" className="underline text-[var(--primary)] font-medium">Privacy Policy</a>
          <a href="/support" className="underline text-[var(--primary)] font-medium">Support</a>
          <a href="/about" className="underline text-[var(--primary)] font-medium">About</a>
          <a href="/careers" className="underline text-[var(--primary)] font-medium">Careers</a>
          <a href="/blog" className="underline text-[var(--primary)] font-medium">Blog</a>
        </div>
      </div>
    </main>
  );
}
// (Removed duplicate Home component)
