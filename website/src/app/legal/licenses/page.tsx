export default function Licenses() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Open Source Licenses</h1>
        <p className="text-lg mb-8">BYSEL uses open source software. See below for license details.</p>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow text-left">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Licenses</h2>
          <ul className="list-disc ml-6">
            <li>Next.js - MIT License</li>
            <li>Tailwind CSS - MIT License</li>
            <li>Other dependencies - See package documentation</li>
          </ul>
        </div>
      </div>
    </main>
  );
}
