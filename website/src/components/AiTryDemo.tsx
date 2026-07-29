"use client";

import { FormEvent, useRef, useState } from "react";
import Link from "next/link";
import { AI_ASK_URL } from "../lib/api";

const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.bysel.trader";

const SUGGESTIONS = [
  "What is the price of RELIANCE?",
  "Is TCS a buy for swing?",
  "Compare INFY vs TCS",
  "Nifty outlook today",
];

const MAX_TURNS = 6;

type ChatRole = "user" | "assistant";

type ChatMessage = {
  id: string;
  role: ChatRole;
  content: string;
};

type AiAskResponse = {
  answer?: string;
  symbol?: string;
  current_price?: number;
  source?: string;
};

export default function AiTryDemo() {
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      id: "welcome",
      role: "assistant",
      content:
        "Hi — try a quick NSE question here. Full Buy / Set Alert actions and paper trading are in the Android app.",
    },
  ]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [turnsUsed, setTurnsUsed] = useState(0);
  const listRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    window.requestAnimationFrame(() => {
      if (listRef.current) {
        listRef.current.scrollTop = listRef.current.scrollHeight;
      }
    });
  };

  const ask = async (query: string) => {
    const trimmed = query.trim();
    if (!trimmed || busy) return;
    if (turnsUsed >= MAX_TURNS) {
      setError("Demo limit reached on this page. Continue in the Android app for unlimited practice.");
      return;
    }

    setError(null);
    setBusy(true);
    setInput("");
    setMessages((prev) => [
      ...prev,
      { id: `u-${Date.now()}`, role: "user", content: trimmed },
    ]);
    scrollToBottom();

    try {
      const controller = new AbortController();
      const timeout = window.setTimeout(() => controller.abort(), 60000);
      const response = await fetch(AI_ASK_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ query: trimmed, tier: "auto" }),
        signal: controller.signal,
      });
      window.clearTimeout(timeout);

      if (!response.ok) {
        throw new Error(`AI request failed (${response.status})`);
      }

      const data = (await response.json()) as AiAskResponse;
      let answer = (data.answer || "").trim();
      if (!answer) {
        answer = "I could not form an answer right now. Try again in a moment or open the app.";
      }
      if (data.symbol && data.current_price != null) {
        answer = `${answer}\n\n${data.symbol} · ₹${Number(data.current_price).toLocaleString("en-IN")}`;
      }

      setMessages((prev) => [
        ...prev,
        { id: `a-${Date.now()}`, role: "assistant", content: answer },
      ]);
      setTurnsUsed((n) => n + 1);
    } catch {
      setMessages((prev) => [
        ...prev,
        {
          id: `e-${Date.now()}`,
          role: "assistant",
          content:
            "The AI server is waking up or busy. Wait a few seconds and retry — or open the Android app for a more reliable session.",
        },
      ]);
      setError("If this keeps failing, the API may still be cold-starting (about 30–60s).");
    } finally {
      setBusy(false);
      scrollToBottom();
    }
  };

  const onSubmit = (event: FormEvent) => {
    event.preventDefault();
    void ask(input);
  };

  return (
    <section className="glass-card hero-panel ai-demo" aria-label="Try BYSEL AI">
      <div className="panel-head">
        <h2 className="panel-title">Try the AI assistant</h2>
        <span className="status-chip live">Live demo</span>
      </div>
      <p className="mini-muted">
        Web preview of the same backend the app uses. Paper Buy / Alerts require the Android app.
      </p>

      <div className="ai-demo-thread" ref={listRef}>
        {messages.map((message) => (
          <div
            key={message.id}
            className={`ai-demo-bubble ${message.role === "user" ? "user" : "assistant"}`}
          >
            {message.content}
          </div>
        ))}
        {busy ? <div className="ai-demo-bubble assistant muted">Thinking…</div> : null}
      </div>

      <div className="ai-demo-suggestions">
        {SUGGESTIONS.map((item) => (
          <button
            key={item}
            type="button"
            className="heatmap-sector-chip"
            disabled={busy || turnsUsed >= MAX_TURNS}
            onClick={() => void ask(item)}
          >
            {item}
          </button>
        ))}
      </div>

      <form className="ai-demo-form" onSubmit={onSubmit}>
        <input
          className="ai-demo-input"
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="Ask about an NSE stock…"
          maxLength={280}
          disabled={busy || turnsUsed >= MAX_TURNS}
          aria-label="Ask BYSEL AI"
        />
        <button className="btn-primary" type="submit" disabled={busy || !input.trim() || turnsUsed >= MAX_TURNS}>
          Ask
        </button>
      </form>

      {error ? <p className="mini-muted" style={{ marginTop: "0.55rem" }}>{error}</p> : null}

      <div className="btn-row" style={{ marginTop: "0.75rem" }}>
        <Link href={PLAY_STORE_URL} className="btn-secondary" target="_blank" rel="noreferrer">
          Continue in the app
        </Link>
        <span className="mini-muted">{turnsUsed}/{MAX_TURNS} demo asks</span>
      </div>
    </section>
  );
}
