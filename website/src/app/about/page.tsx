export default function About() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[var(--background)] text-[var(--text)]">
      <div className="w-full max-w-3xl px-6 py-16 text-center">
        <h1 className="text-3xl md:text-4xl font-bold mb-4 text-[var(--primary)]">About BYSEL</h1>
        <p className="text-lg mb-8">
          BYSEL is an innovative platform dedicated to democratizing access to financial markets. Founded with the vision to empower individuals, BYSEL leverages cutting-edge AI-driven tools to help users learn, practice, and excel in trading. Our story began with a mission to bridge the gap between technology and finance, making trading education and market insights accessible to everyone.
        </p>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow mb-8">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Our Mission</h2>
          <div className="min-h-16 flex items-center justify-center text-[var(--accent)] text-lg">
            Empowering users to learn and practice trading with AI-driven tools, fostering financial literacy and confidence for all.
          </div>
        </div>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow mb-8">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Our Vision</h2>
          <div className="min-h-16 flex items-center justify-center text-[var(--accent)] text-lg">
            To become the leading platform for AI-powered trading education and market analysis, enabling users to make informed decisions and achieve their financial goals.
          </div>
        </div>
        <div className="bg-[var(--background)] border border-[var(--primary)] rounded-xl p-6 shadow">
          <h2 className="text-xl font-semibold mb-2 text-[var(--primary)]">Our Team</h2>
          <div className="min-h-16 flex flex-col items-center justify-center text-[var(--secondary)] text-lg">
            <div className="mb-4">
              <span className="font-bold">CEO: SRI HARSHA DUPPALLI</span><br />
              SRI HARSHA DUPPALLI is the visionary founder and CEO of BYSEL. With a strong background in technology and finance, Harsha leads the team with a passion for innovation and user empowerment. His mission is to make trading education and market intelligence accessible to everyone.
            </div>
            <div>
              <span className="font-bold">Team Members:</span> [Team Members Placeholder]
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
