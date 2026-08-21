import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.market_education import get_education_answer
from indian_stock_llm.builtin_knowledge import builtin_knowledge_items
from indian_stock_llm.nse_official_literacy import nse_official_literacy_items


def test_nseindia_education_answer_points_to_official_site():
    answer = get_education_answer("what is nseindia.com")
    assert answer
    assert "nseindia.com" in answer.lower()
    assert "do **not** crawl" in answer.lower() or "not** crawl" in answer.lower()
    assert "not a secret system" in answer.lower() or "not** a secret" in answer.lower()


def test_nse_strategies_maps_to_literacy_not_tips():
    answer = get_education_answer("nse strategies")
    assert answer
    assert "process and risk" in answer.lower()
    assert "sebi research" in answer.lower()


def test_nse_literacy_pack_is_rich_and_unique():
    pack = nse_official_literacy_items()
    ids = [item.id for item in pack]
    assert len(pack) >= 20
    assert len(ids) == len(set(ids))
    by_id = {item.id: item for item in pack}
    for required in (
        "nse_official_source",
        "nse_site_map",
        "nse_get_quote",
        "nse_option_chain",
        "nse_fo_contracts",
        "nse_session_clock",
        "nse_learn_ncfm",
    ):
        assert required in by_id
        assert "nseindia.com" in by_id["nse_official_source"].content
    assert "fine-tune" in by_id["nse_official_source"].content
    assert "do not invent" in by_id["nse_option_chain"].content.lower() or "synthetic" in by_id["nse_option_chain"].content.lower()


def test_builtin_pack_includes_nse_official_source():
    items = builtin_knowledge_items()
    by_id = {item.id: item for item in items}
    assert "nse_official_source" in by_id
    assert "nse_get_quote" in by_id
    assert "nse_investor_education_not_tips" in by_id
