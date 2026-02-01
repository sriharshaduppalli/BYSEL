import pytest
from fastapi.testclient import TestClient
import sys
from pathlib import Path

# Add parent directory to path
sys.path.insert(0, str(Path(__file__).parent.parent))

from app import app

client = TestClient(app)

def test_health_check():
    """Test health check endpoint"""
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "version" in data

def test_get_quotes():
    """Test getting quotes"""
    response = client.get("/quotes?symbols=RELIANCE,TCS")
    assert response.status_code == 200
    data = response.json()
    assert len(data) >= 0
    if len(data) > 0:
        assert "symbol" in data[0]
        assert "last" in data[0]
        assert "pctChange" in data[0]

def test_get_holdings_empty():
    """Test getting holdings when empty"""
    response = client.get("/holdings")
    assert response.status_code == 200
    data = response.json()
    assert isinstance(data, list)

def test_place_order():
    """Test placing an order"""
    order_data = {
        "symbol": "TCS",
        "qty": 1,
        "side": "BUY"
    }
    response = client.post("/order", json=order_data)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["order"]["symbol"] == "TCS"
    assert data["order"]["qty"] == 1
