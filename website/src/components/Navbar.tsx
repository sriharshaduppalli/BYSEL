"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";

const NAV_ITEMS = [
  { href: "/", label: "Home" },
  { href: "/features", label: "Features" },
  { href: "/markets", label: "Markets" },
  { href: "/pricing", label: "Pricing" },
  { href: "/support", label: "Support" },
  { href: "/about", label: "About" },
  { href: "/blog", label: "Blog" },
];

export default function Navbar() {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  const isActive = (href: string): boolean => {
    if (href === "/") {
      return pathname === "/";
    }
    return pathname.startsWith(href);
  };

  const closeMenu = (): void => setMenuOpen(false);

  return (
    <header className="top-nav">
      <nav className="site-container">
        <div className="top-nav-row">
          <Link href="/" className="brand-mark" onClick={closeMenu}>
            <Image src="/ic_launcher.png" alt="BYSEL logo" width={42} height={42} priority />
            <span>
              <span className="brand-title">BYSEL Trader</span>
              <span className="brand-tag">AI-first market simulator</span>
            </span>
          </Link>

          <div className="nav-desktop" aria-label="Primary navigation">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={`nav-link${isActive(item.href) ? " active" : ""}`}
              >
                {item.label}
              </Link>
            ))}
          </div>

          <Link
            href="https://play.google.com/store"
            className="nav-pill"
            target="_blank"
            rel="noreferrer"
          >
            Get Android App
          </Link>

          <button
            className="mobile-toggle"
            type="button"
            onClick={() => setMenuOpen((previous) => !previous)}
            aria-label="Toggle menu"
            aria-expanded={menuOpen}
          >
            {menuOpen ? "Close" : "Menu"}
          </button>
        </div>

        {menuOpen ? (
          <div className="mobile-menu">
            {NAV_ITEMS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                onClick={closeMenu}
                className={`nav-link${isActive(item.href) ? " active" : ""}`}
              >
                {item.label}
              </Link>
            ))}

            <Link
              href="https://play.google.com/store"
              className="btn-primary"
              target="_blank"
              rel="noreferrer"
            >
              Get Android App
            </Link>
          </div>
        ) : null}
      </nav>
    </header>
  );
}
