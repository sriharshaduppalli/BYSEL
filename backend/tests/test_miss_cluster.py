"""Nightly miss clustering stays off the request path and never invents knowledge."""
from __future__ import annotations

import json

from app.miss_cluster import cluster_and_write, cluster_feedback_lines, normalize_miss_phrase


def test_cluster_groups_unhelpful_and_frequent_queries(tmp_path):
    lines = [
        json.dumps({"kind": "thumbs_v1", "query": "Dividend date of RELIANCE", "helpful": False, "intent": "events_news"}),
        json.dumps({"kind": "thumbs_v1", "query": "Dividend date of RELIANCE", "helpful": False, "intent": "events_news"}),
        json.dumps({"kind": "interaction_v1", "query": "Is the market open?", "grounded": True, "confidence": 0.9, "intent": "general_query"}),
        "2026-08-28T00:00:00\tgeneral_query\tis the market open?\n",
        json.dumps({"kind": "thumbs_v1", "query": "What's on my watchlist?", "helpful": True, "intent": "portfolio"}),
        json.dumps({"kind": "thumbs_v1", "query": "What's on my watchlist?", "helpful": True, "intent": "portfolio"}),
        json.dumps({"kind": "thumbs_v1", "query": "What's on my watchlist?", "helpful": True, "intent": "portfolio"}),
        json.dumps({"kind": "thumbs_v1", "query": "What's on my watchlist?", "helpful": True, "intent": "portfolio"}),
    ]
    report = cluster_feedback_lines(lines)
    assert report["miss_count"] >= 2
    assert report["note"].startswith("Human review only")
    phrases = {row["phrase"] for row in report["clusters"]}
    assert normalize_miss_phrase("Dividend date of RELIANCE") in phrases
    review = [row for row in report["clusters"] if row["needs_review"]]
    assert review

    log = tmp_path / "daily_feedback.log"
    log.write_text("\n".join(lines), encoding="utf-8")
    out = tmp_path / "miss_clusters.json"
    written = cluster_and_write(log_path=log, out_path=out)
    assert out.exists()
    payload = json.loads(out.read_text(encoding="utf-8"))
    assert payload["note"].startswith("Human review only")
    assert payload.get("learned_items") is None
    assert written["written_to"] == str(out)
    assert not (tmp_path / "learned_knowledge.json").exists()
