export default function Pricing() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Pricing</h1>
        <p className="text-lg mb-8">Transparent pricing for BYSEL. Free and premium tiers.</p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Free</h2>
            <ul className="text-left list-disc ml-6">
              <li>Basic trading simulator</li>
              <li>AI Assistant (limited)</li>
              <li>Market heatmap preview</li>
              <li>Portfolio analytics</li>
            </ul>
          </div>
          <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
            <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Premium</h2>
            <ul className="text-left list-disc ml-6">
              <li>Full AI Assistant access</li>
              <li>Advanced analytics & alerts</li>
              <li>Portfolio optimization tools</li>
              <li>Priority support</li>
            </ul>
          </div>
        </div>
      </div>
    </main>
  );
}
