import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

import pytest

from app.routes.trading import paper_future_symbol, paper_option_symbol


def test_paper_option_symbol_matches_portfolio_fno_pattern():
    assert paper_option_symbol("NIFTY", "2026-10-09", 22500, "CALL") == "NIFTY09OCTCE22500"
    assert paper_option_symbol("banknifty", "2026-10-29", 52000.4, "PUT") == "BANKNIFTY29OCTPE52000"


def test_paper_future_symbol_matches_portfolio_fno_pattern():
    assert paper_future_symbol("NIFTY", "2026-10-09") == "NIFTY09OCTFUT"
    assert paper_future_symbol("banknifty", "2026-10-29") == "BANKNIFTY29OCTFUT"


def test_paper_option_symbol_rejects_bad_inputs():
    with pytest.raises(ValueError):
        paper_option_symbol("NIFTY", "09-10-2026", 22500, "CALL")
    with pytest.raises(ValueError):
        paper_option_symbol("NIFTY", "2026-10-09", 22500, "FUT")
