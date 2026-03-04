export default function Markets() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Markets</h1>
        <p className="text-lg mb-8">Live market widgets, news, sentiment, and heatmap preview.</p>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow mb-8">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Live Market Heatmap</h2>
          <div className="h-32 flex items-center justify-center text-[var(--accent)] text-lg">[Heatmap Widget Placeholder]</div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Trending Stocks</h2>
            <div className="h-16 flex items-center justify-center text-[var(--secondary)]">[Trending Stocks Placeholder]</div>
          </div>
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Market News & Sentiment</h2>
            <div className="h-16 flex items-center justify-center text-[var(--accent)]">[News & Sentiment Placeholder]</div>
          </div>
        </div>
      </div>
    </main>
  );
}
