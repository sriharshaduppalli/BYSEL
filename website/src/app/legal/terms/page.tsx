export default function TermsOfService() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Terms of Service</h1>
        <p className="text-lg mb-8">Please read our terms before using BYSEL.</p>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow text-left">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Usage</h2>
          <p>BYSEL is for educational and simulation purposes only. No real trading or financial advice is provided.</p>
          <h2 className="text-xl font-semibold mt-6 mb-2 text-[var(--primary)]">User Conduct</h2>
          <p>Users must not misuse the platform or attempt unauthorized access. Violations may result in account suspension.</p>
        </div>
      </div>
    </main>
  );
}
