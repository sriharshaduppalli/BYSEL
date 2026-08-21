/** Production FastAPI backend used by the marketing site demos. */
export const API_BASE =
  process.env.NEXT_PUBLIC_BYSEL_API_URL?.replace(/\/$/, "") ||
  "https://bysel-services-746069024823.europe-west1.run.app";

export const HEATMAP_URL = `${API_BASE}/market/heatmap`;
export const AI_ASK_URL = `${API_BASE}/ai/ask`;
