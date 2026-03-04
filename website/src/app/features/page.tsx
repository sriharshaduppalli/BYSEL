export default function Features() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Product & Features</h1>
        <p className="text-lg mb-8">Explore BYSEL's AI Assistant, trading simulator, analytics, alerts, and more.</p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">AI Assistant</h2>
            <p>Get personalized stock recommendations, technical/fundamental analysis, and answers to your trading questions.</p>
          </div>
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Trading Simulator</h2>
            <p>Practice trading with real market data and optimize your portfolio risk-free.</p>
          </div>
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Analytics & Alerts</h2>
            <p>Receive alerts for price movements, earnings, news, and access advanced analytics tools.</p>
          </div>
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Portfolio Optimization</h2>
            <p>Get suggestions for diversification, risk assessment, and portfolio improvement.</p>
          </div>
        </div>
      </div>
    </main>
  );
}
