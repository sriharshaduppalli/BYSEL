import time

from fastapi.testclient import TestClient

from app import app

client = TestClient(app)


def _register(prefix: str) -> dict:
    suffix = str(int(time.time() * 1000))
    username = f"{prefix}_{suffix}"
    response = client.post(
        "/auth/register",
        json={"username": username, "email": f"{username}@example.com", "password": "demo1234"},
    )
    assert response.status_code == 200
    return {"Authorization": f"Bearer {response.json()['access_token']}"}


def test_stock_notes_require_auth():
    assert client.get("/stock-notes").status_code == 401
    assert client.put("/stock-notes", json={"symbol": "RELIANCE", "text": "hi"}).status_code == 401


def test_stock_notes_crud_and_normalization():
    headers = _register("notes_crud")

    put = client.put(
        "/stock-notes",
        headers=headers,
        json={"symbol": "reliance", "text": "  watch 1400 support  "},
    )
    assert put.status_code == 200
    body = put.json()
    assert body["symbol"] == "RELIANCE.NS"
    assert body["text"] == "watch 1400 support"
    assert body["updatedAt"] > 0

    listed = client.get("/stock-notes", headers=headers).json()
    assert len(listed["notes"]) == 1
    assert listed["notes"][0]["symbol"] == "RELIANCE.NS"

    fetched = client.get("/stock-notes/RELIANCE", headers=headers).json()
    assert fetched["text"] == "watch 1400 support"

    other = _register("notes_other")
    isolated = client.get("/stock-notes", headers=other).json()
    assert isolated["notes"] == []

    deleted = client.delete("/stock-notes/RELIANCE.NS", headers=headers)
    assert deleted.status_code == 200
    assert deleted.json()["symbol"] == "RELIANCE.NS"
    assert client.get("/stock-notes", headers=headers).json()["notes"] == []


def test_stock_notes_empty_text_clears_from_list():
    headers = _register("notes_clear")
    client.put("/stock-notes", headers=headers, json={"symbol": "TCS.NS", "text": "results week"})
    client.put("/stock-notes", headers=headers, json={"symbol": "TCS.NS", "text": "   "})
    assert client.get("/stock-notes", headers=headers).json()["notes"] == []
    assert client.get("/stock-notes/TCS.NS", headers=headers).json()["text"] == ""
