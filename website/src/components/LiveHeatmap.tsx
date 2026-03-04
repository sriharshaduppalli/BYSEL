"use client";
import React, { useEffect, useState } from "react";

// Use local backend endpoint for real market data
const API_URL = "https://www.byseltrader.com/api/heatmap";

export default function LiveHeatmap() {
  const [data, setData] = useState<any[] | null>(null);
  const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
  const [initialLoad, setInitialLoad] = useState(true);

  useEffect(() => {
    let intervalId;
    const fetchData = async () => {
      try {
        const res = await fetch(API_URL);
        if (!res.ok) throw new Error("Failed to fetch heatmap");
        const json = await res.json();
        setData(json);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : String(err));
      } finally {
        if (initialLoad) {
          setLoading(false);
          setInitialLoad(false);
        }
      }
    };
    fetchData();
    intervalId = setInterval(fetchData, 2000); // Refresh every 2s
    return () => clearInterval(intervalId);
  }, [initialLoad]);

  if (loading) return <div>Loading live heatmap...</div>;
  if (error) return <div className="text-red-500">{error}</div>;
  if (!data || !Array.isArray(data)) return <div>No heatmap data available.</div>;

  // Render heatmap (simple grid, customize as needed)
  return (
    <div className="grid grid-cols-5 gap-2">
      {data.map((stock) => (
        <div
          key={stock.symbol}
          className="p-2 rounded text-xs font-bold text-white"
          style={{ background: stock.color || "#333" }}
        >
          {stock.symbol}
          <br />
          {stock.pctChange}%
        </div>
      ))}
    </div>
  );
}
