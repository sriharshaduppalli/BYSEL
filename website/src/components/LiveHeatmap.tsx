"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { HEATMAP_URL } from "../lib/api";

const REFRESH_INTERVAL_MS = 5000;

type HeatmapStock = {
  symbol: string;
  pctChange: number;
  color: string;
};

type HeatmapSector = {
  name: string;
  avgChange: number;
  advances: number;
  declines: number;
  stocks: HeatmapStock[];
};

type HeatmapPayload = {
  sectors: HeatmapSector[];
  mood: string;
  moodDescription: string;
  marketOpen: boolean;
  isStale?: boolean;
  marketBreadth?: {
    advances: number;
    declines: number;
    unchanged: number;
    total: number;
  };
};

const FALLBACK: HeatmapPayload = {
  sectors: [
    {
      name: "Sample",
      avgChange: 0.4,
      advances: 5,
      declines: 3,
      stocks: [
        { symbol: "RELIANCE", pctChange: 1.23, color: "" },
        { symbol: "TCS", pctChange: 0.74, color: "" },
        { symbol: "INFY", pctChange: -0.41, color: "" },
        { symbol: "HDFCBANK", pctChange: 0.34, color: "" },
        { symbol: "SBIN", pctChange: -1.08, color: "" },
        { symbol: "ICICIBANK", pctChange: 0.68, color: "" },
        { symbol: "LT", pctChange: 0.52, color: "" },
        { symbol: "BHARTIARTL", pctChange: -0.27, color: "" },
      ],
    },
  ],
  mood: "NEUTRAL",
  moodDescription: "Showing a sample until the live feed connects.",
  marketOpen: false,
  isStale: true,
};

const clamp = (value: number, min: number, max: number): number =>
  Math.min(max, Math.max(min, value));

const dynamicColor = (pctChange: number): string => {
  const scaled = clamp(Math.abs(pctChange) / 3.5, 0.18, 1);
  if (pctChange >= 0) {
    const opacity = 0.52 + scaled * 0.4;
    return `linear-gradient(145deg, rgba(43, 138, 87, ${opacity}), rgba(22, 102, 67, 0.96))`;
  }
  const opacity = 0.48 + scaled * 0.43;
  return `linear-gradient(145deg, rgba(206, 90, 67, ${opacity}), rgba(145, 54, 40, 0.96))`;
};

const parsePercent = (value: unknown): number | null => {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const numeric = Number(value.replace(/%/g, "").trim());
    if (Number.isFinite(numeric)) return numeric;
  }
  return null;
};

const parsePayload = (raw: unknown): HeatmapPayload | null => {
  if (!raw || typeof raw !== "object") return null;
  const data = raw as Record<string, unknown>;
  const sectorsRaw = Array.isArray(data.sectors) ? data.sectors : [];
  const sectors: HeatmapSector[] = [];

  for (const sector of sectorsRaw) {
    if (!sector || typeof sector !== "object") continue;
    const s = sector as Record<string, unknown>;
    const name = typeof s.name === "string" ? s.name : "";
    if (!name) continue;
    const avgChange = parsePercent(s.avgChange) ?? 0;
    const stocksRaw = Array.isArray(s.stocks) ? s.stocks : [];
    const stocks: HeatmapStock[] = [];
    for (const row of stocksRaw) {
      if (!row || typeof row !== "object") continue;
      const r = row as Record<string, unknown>;
      const symbol = typeof r.symbol === "string" ? r.symbol.trim().toUpperCase() : "";
      const pct = parsePercent(r.pctChange) ?? parsePercent(r.changePercent);
      if (!symbol || pct === null) continue;
      stocks.push({
        symbol: symbol.slice(0, 12),
        pctChange: pct,
        color: typeof r.color === "string" && r.color.trim() ? r.color : dynamicColor(pct),
      });
    }
    sectors.push({
      name,
      avgChange,
      advances: typeof s.advances === "number" ? s.advances : 0,
      declines: typeof s.declines === "number" ? s.declines : 0,
      stocks,
    });
  }

  if (sectors.length === 0) return null;

  const breadth =
    data.marketBreadth && typeof data.marketBreadth === "object"
      ? (data.marketBreadth as HeatmapPayload["marketBreadth"])
      : undefined;

  return {
    sectors,
    mood: typeof data.mood === "string" ? data.mood : "NEUTRAL",
    moodDescription:
      typeof data.moodDescription === "string"
        ? data.moodDescription
        : typeof data.staleReason === "string"
          ? data.staleReason
          : "Market breadth snapshot",
    marketOpen: Boolean(data.marketOpen),
    isStale: Boolean(data.isStale),
    marketBreadth: breadth,
  };
};

const percentLabel = (value: number): string => {
  const formatted = value.toFixed(2);
  return value >= 0 ? `+${formatted}%` : `${formatted}%`;
};

export default function LiveHeatmap() {
  const [payload, setPayload] = useState<HeatmapPayload | null>(null);
  const [selectedSector, setSelectedSector] = useState<string>("All");
  const [loading, setLoading] = useState(true);
  const [warning, setWarning] = useState<string | null>(null);
  const [updatedAt, setUpdatedAt] = useState("-");

  const refresh = useCallback(async () => {
    try {
      const controller = new AbortController();
      const timeout = window.setTimeout(() => controller.abort(), 25000);
      const response = await fetch(HEATMAP_URL, {
        cache: "no-store",
        signal: controller.signal,
      });
      window.clearTimeout(timeout);
      if (!response.ok) {
        throw new Error(`Heatmap request failed (${response.status})`);
      }
      const parsed = parsePayload(await response.json());
      if (!parsed) {
        throw new Error("Heatmap payload was empty");
      }
      setPayload(parsed);
      setWarning(
        parsed.isStale || !parsed.marketOpen
          ? parsed.moodDescription || "Showing last session / cached breadth."
          : null,
      );
      setUpdatedAt(
        new Intl.DateTimeFormat("en-IN", {
          hour: "2-digit",
          minute: "2-digit",
          second: "2-digit",
          hour12: false,
        }).format(new Date()),
      );
    } catch {
      setWarning("Live feed delayed (server waking up). Showing a sample until data arrives.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timerId = window.setInterval(() => {
      void refresh();
    }, REFRESH_INTERVAL_MS);
    return () => window.clearInterval(timerId);
  }, [refresh]);

  const view = payload ?? FALLBACK;

  const tiles = useMemo(() => {
    if (selectedSector === "All") {
      return view.sectors
        .flatMap((s) => s.stocks)
        .sort((a, b) => Math.abs(b.pctChange) - Math.abs(a.pctChange))
        .slice(0, 16)
        .map((s) => ({ ...s, color: s.color || dynamicColor(s.pctChange) }));
    }
    const sector = view.sectors.find((s) => s.name === selectedSector);
    return (sector?.stocks ?? [])
      .slice(0, 16)
      .map((s) => ({ ...s, color: s.color || dynamicColor(s.pctChange) }));
  }, [selectedSector, view]);

  const statusLabel = !payload
    ? "Sample"
    : warning
      ? view.marketOpen
        ? "Catching up"
        : "Session closed"
      : "Live";

  return (
    <section className="glass-card hero-panel" aria-live="polite">
      <div className="panel-head">
        <h3 className="panel-title">Live Market Heatmap</h3>
        <span className={`status-chip ${warning || !payload ? "warn" : "live"}`}>{statusLabel}</span>
      </div>

      <p className="mini-muted">
        {loading
          ? "Connecting to BYSEL market API…"
          : `${view.mood} · ${view.moodDescription} · Updated ${updatedAt} IST`}
      </p>

      {view.marketBreadth ? (
        <p className="mini-muted" style={{ marginTop: "0.25rem" }}>
          Breadth {view.marketBreadth.advances} adv / {view.marketBreadth.declines} dec
          {view.marketBreadth.total ? ` · ${view.marketBreadth.total} names` : ""}
        </p>
      ) : null}

      <div className="heatmap-sector-row">
        <button
          type="button"
          className={`heatmap-sector-chip${selectedSector === "All" ? " active" : ""}`}
          onClick={() => setSelectedSector("All")}
        >
          All movers
        </button>
        {view.sectors.slice(0, 8).map((sector) => (
          <button
            key={sector.name}
            type="button"
            className={`heatmap-sector-chip${selectedSector === sector.name ? " active" : ""}`}
            onClick={() => setSelectedSector(sector.name)}
          >
            {sector.name} {percentLabel(sector.avgChange)}
          </button>
        ))}
      </div>

      <div className="heatmap-grid" style={{ marginTop: "0.65rem" }}>
        {tiles.map((stock) => (
          <div key={`${selectedSector}-${stock.symbol}`} className="heatmap-tile" style={{ background: stock.color }}>
            <span className="heatmap-symbol">{stock.symbol}</span>
            <p className="heatmap-move">{percentLabel(stock.pctChange)}</p>
          </div>
        ))}
      </div>

      {warning ? <p className="mini-muted" style={{ marginTop: "0.7rem" }}>{warning}</p> : null}
      <p className="mini-muted" style={{ marginTop: "0.45rem" }}>
        Full interactive heatmap with 1–2s refresh lives in the Android app.
      </p>
    </section>
  );
}
