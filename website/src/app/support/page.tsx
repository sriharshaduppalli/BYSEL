export default function Support() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">Support</h1>
        <p className="text-lg mb-8">FAQ, contact form, and help center for BYSEL users.</p>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow mb-8">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">FAQ</h2>
          <div className="h-32 flex items-center justify-center text-[var(--accent)] text-lg">[FAQ Placeholder]</div>
        </div>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Contact Form</h2>
          <div className="h-32 flex items-center justify-center text-[var(--secondary)] text-lg">[Contact Form Placeholder]</div>
        </div>
      </div>
    </main>
  );
}
