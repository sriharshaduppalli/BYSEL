export default function PrivacyPolicy() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Privacy Policy</h1>
        <p className="text-lg mb-8">Your privacy is important to us. Read our policy below.</p>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow text-left">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Data Collection</h2>
          <p>We collect only necessary information for app functionality and analytics. No personal data is shared with third parties.</p>
          <h2 className="text-xl font-semibold mt-6 mb-2 text-[var(--primary)]">User Rights</h2>
          <p>You can request deletion of your data at any time. Contact support@www.byseltrader.com for privacy requests.</p>
        </div>
      </div>
    </main>
  );
}
