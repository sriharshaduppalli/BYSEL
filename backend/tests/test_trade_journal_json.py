"""Journal rows must serialize even when a saved figure is NaN."""
import json
from datetime import datetime, timezone
from types import SimpleNamespace

from app.routes.trade_journal import _entry_to_dict


def test_journal_entry_drops_non_finite_numbers():
    row = SimpleNamespace(
        id=1,
        user_id=2,
        symbol="RELIANCE",
        side="BUY",
        qty=1,
        price=float("nan"),
        total=float("inf"),
        order_id=None,
        context_json=json.dumps({"trailingPE": float("nan"), "volumeRatio": 1.25}),
        auto_notes_json="[]",
        user_note="",
        outcome=None,
        created_at=datetime.now(timezone.utc),
    )
    payload = _entry_to_dict(row)
    json.dumps({"entries": [payload]}, allow_nan=False)
    assert payload["price"] is None
    assert payload["total"] is None
    assert payload["context"]["trailingPE"] is None
    assert payload["context"]["volumeRatio"] == 1.25
