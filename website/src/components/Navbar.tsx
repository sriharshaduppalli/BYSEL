import Link from "next/link";

export default function Navbar() {
  return (
    <nav className="w-full bg-[var(--background)] border-b border-[var(--primary)] py-4 px-6 flex items-center justify-between">
      <div className="flex items-center gap-3">
        <img src="/ic_launcher.png" alt="BYSEL Logo" width={40} height={40} className="rounded" />
        <span className="text-xl font-bold text-[var(--primary)]">BYSEL</span>
      </div>
      <div className="flex gap-4">
        <Link href="/" className="text-[var(--primary)] font-medium hover:underline">Home</Link>
        <Link href="/features" className="text-[var(--primary)] font-medium hover:underline">Features</Link>
        <Link href="/markets" className="text-[var(--primary)] font-medium hover:underline">Markets</Link>
        <Link href="/pricing" className="text-[var(--primary)] font-medium hover:underline">Pricing</Link>
        <Link href="/support" className="text-[var(--primary)] font-medium hover:underline">Support</Link>
        <Link href="/about" className="text-[var(--primary)] font-medium hover:underline">About</Link>
        <Link href="/careers" className="text-[var(--primary)] font-medium hover:underline">Careers</Link>
        <Link href="/blog" className="text-[var(--primary)] font-medium hover:underline">Blog</Link>
      </div>
    </nav>
  );
}
