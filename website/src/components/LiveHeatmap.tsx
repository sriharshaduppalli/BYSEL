"use client";
import { useEffect, useMemo, useState } from "react";

const API_URL = "https://www.byseltrader.com/api/heatmap";
const REFRESH_INTERVAL_MS = 4000;

type HeatmapEntry = {
  symbol: string;
  pctChange: number;
  color: string;
};

type RawHeatmapEntry = {
  symbol?: unknown;
  pctChange?: unknown;
  changePercent?: unknown;
  color?: unknown;
};

const FALLBACK_HEATMAP: HeatmapEntry[] = [
  { symbol: "RELIANCE", pctChange: 1.23, color: "linear-gradient(145deg, #2b8a57, #1f6f45)" },
  { symbol: "TCS", pctChange: 0.74, color: "linear-gradient(145deg, #2f955f, #216f45)" },
  { symbol: "INFY", pctChange: -0.41, color: "linear-gradient(145deg, #c85d4a, #9f4031)" },
  { symbol: "HDFCBANK", pctChange: 0.34, color: "linear-gradient(145deg, #3a9871, #236f4f)" },
  { symbol: "SBIN", pctChange: -1.08, color: "linear-gradient(145deg, #d96a51, #a84a37)" },
  { symbol: "ICICIBANK", pctChange: 0.68, color: "linear-gradient(145deg, #2d8e68, #23654c)" },
  { symbol: "LT", pctChange: 0.52, color: "linear-gradient(145deg, #369472, #245d49)" },
  { symbol: "BHARTIARTL", pctChange: -0.27, color: "linear-gradient(145deg, #bd5a48, #8d3e2f)" },
];

const clamp = (value: number, min: number, max: number): number => {
  return Math.min(max, Math.max(min, value));
};

const parsePercent = (value: unknown): number | null => {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const cleaned = value.replace(/%/g, "").trim();
    const numeric = Number(cleaned);
    if (Number.isFinite(numeric)) {
      return numeric;
    }
  }

  return null;
};

const dynamicColor = (pctChange: number): string => {
  const scaled = clamp(Math.abs(pctChange) / 3.5, 0.18, 1);
  if (pctChange >= 0) {
    const opacity = 0.52 + scaled * 0.4;
    return `linear-gradient(145deg, rgba(43, 138, 87, ${opacity}), rgba(22, 102, 67, 0.96))`;
  }

  const opacity = 0.48 + scaled * 0.43;
  return `linear-gradient(145deg, rgba(206, 90, 67, ${opacity}), rgba(145, 54, 40, 0.96))`;
};

const toHeatmapEntry = (row: RawHeatmapEntry): HeatmapEntry | null => {
  const symbolRaw = typeof row.symbol === "string" ? row.symbol.trim() : "";
  if (!symbolRaw) {
    return null;
  }

  const pct = parsePercent(row.pctChange) ?? parsePercent(row.changePercent);
  if (pct === null) {
    return null;
  }

  const symbol = symbolRaw.toUpperCase().slice(0, 10);
  const color = typeof row.color === "string" && row.color.trim() ? row.color : dynamicColor(pct);

  return { symbol, pctChange: pct, color };
};

const parseHeatmapResponse = (payload: unknown): HeatmapEntry[] => {
  if (!Array.isArray(payload)) {
    return [];
  }

  return payload
    .map((row) => toHeatmapEntry((row as RawHeatmapEntry) ?? {}))
    .filter((item): item is HeatmapEntry => item !== null)
    .sort((a, b) => Math.abs(b.pctChange) - Math.abs(a.pctChange));
};

const percentLabel = (value: number): string => {
  const formatted = value.toFixed(2);
  return value >= 0 ? `+${formatted}%` : `${formatted}%`;
};

export default function LiveHeatmap() {
  const [entries, setEntries] = useState<HeatmapEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [warning, setWarning] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState<string>("-");

  useEffect(() => {
    let isMounted = true;

    const refreshHeatmap = async (): Promise<void> => {
      try {
        const response = await fetch(API_URL, { cache: "no-store" });
        if (!response.ok) {
          throw new Error(`Heatmap request failed (${response.status})`);
        }

        const payload: unknown = await response.json();
        const parsed = parseHeatmapResponse(payload);
        if (parsed.length === 0) {
          throw new Error("Heatmap payload was empty");
        }

        if (!isMounted) {
          return;
        }

        setEntries(parsed);
        setWarning(null);
        setUpdatedAt(
          new Intl.DateTimeFormat("en-IN", {
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false,
          }).format(new Date()),
        );
      } catch {
        if (!isMounted) {
          return;
        }

        setWarning("Live feed delayed. Showing a representative market sample.");
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    void refreshHeatmap();
    const timerId = window.setInterval(() => {
      void refreshHeatmap();
    }, REFRESH_INTERVAL_MS);

    return () => {
      isMounted = false;
      window.clearInterval(timerId);
    };
  }, []);

  const tiles = useMemo(() => {
    if (entries.length > 0) {
      return entries.slice(0, 16);
    }
    return FALLBACK_HEATMAP;
  }, [entries]);

  return (
    <section className="glass-card hero-panel" aria-live="polite">
      <div className="panel-head">
        <h3 className="panel-title">Live Market Heatmap</h3>
        <span className={`status-chip ${warning ? "warn" : "live"}`}>{warning ? "Fallback" : "Live"}</span>
      </div>

      <p className="mini-muted">
        {loading ? "Loading market breadth..." : `Last refresh: ${updatedAt} IST`}
      </p>

      <div className="heatmap-grid" style={{ marginTop: "0.65rem" }}>
        {tiles.map((stock) => (
          <div key={stock.symbol} className="heatmap-tile" style={{ background: stock.color }}>
            <span className="heatmap-symbol">{stock.symbol}</span>
            <p className="heatmap-move">{percentLabel(stock.pctChange)}</p>
          </div>
        ))}
      </div>

      {warning ? <p className="mini-muted" style={{ marginTop: "0.7rem" }}>{warning}</p> : null}
    </section>
  );
}
