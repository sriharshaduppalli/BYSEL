"""
Per-user stock notes — private text keyed by normalized symbol (e.g. RELIANCE.NS).
Not investment advice; visible only to the authenticated owner.

  GET    /stock-notes
  GET    /stock-notes/{symbol}
  PUT    /stock-notes
  DELETE /stock-notes/{symbol}
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from ..database.db import StockNoteModel
from .dependencies import get_current_user, get_db

router = APIRouter(tags=["Stock Notes"])

MAX_NOTE_CHARS = 4000


def normalize_stock_note_symbol(raw: str) -> str:
    cleaned = (raw or "").strip().upper().replace(" ", "")
    if not cleaned:
        return ""
    if cleaned.startswith("NSE:"):
        cleaned = cleaned[4:]
    elif cleaned.startswith("BSE:"):
        base = cleaned[4:]
        if base.endswith(".BO"):
            base = base[:-3]
        return f"{base}.BO" if base else ""
    if cleaned.endswith(".BO"):
        base = cleaned[:-3]
        return f"{base}.BO" if base else ""
    if cleaned.endswith(".NS"):
        return cleaned
    if len(cleaned) == 6 and cleaned.isdigit():
        return f"{cleaned}.BO"
    return f"{cleaned}.NS"


def _updated_at_ms(value: Optional[datetime]) -> int:
    if value is None:
        return int(datetime.now(timezone.utc).timestamp() * 1000)
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return int(value.timestamp() * 1000)


def _to_schema(row: StockNoteModel) -> "StockNote":
    return StockNote(
        symbol=row.symbol,
        text=row.text or "",
        updatedAt=_updated_at_ms(row.updated_at),
    )


class StockNote(BaseModel):
    symbol: str
    text: str = ""
    updatedAt: int = 0


class StockNoteUpsert(BaseModel):
    symbol: str
    text: str = Field(default="", max_length=MAX_NOTE_CHARS)


class StockNotesListResponse(BaseModel):
    notes: List[StockNote] = []


class StockNoteDeleteResponse(BaseModel):
    status: str
    symbol: str


@router.get("/stock-notes", response_model=StockNotesListResponse)
async def list_stock_notes(
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    rows = (
        db.query(StockNoteModel)
        .filter(StockNoteModel.user_id == user.id)
        .order_by(StockNoteModel.updated_at.desc())
        .all()
    )
    return StockNotesListResponse(notes=[_to_schema(row) for row in rows if (row.text or "").strip()])


@router.get("/stock-notes/{symbol:path}", response_model=StockNote)
async def get_stock_note(
    symbol: str,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    normalized = normalize_stock_note_symbol(symbol)
    if not normalized:
        raise HTTPException(status_code=400, detail="symbol is required")
    row = (
        db.query(StockNoteModel)
        .filter(StockNoteModel.user_id == user.id, StockNoteModel.symbol == normalized)
        .first()
    )
    if not row or not (row.text or "").strip():
        return StockNote(symbol=normalized, text="", updatedAt=0)
    return _to_schema(row)


@router.put("/stock-notes", response_model=StockNote)
async def upsert_stock_note(
    body: StockNoteUpsert,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    normalized = normalize_stock_note_symbol(body.symbol)
    if not normalized:
        raise HTTPException(status_code=400, detail="symbol is required")
    text = (body.text or "").strip()
    if len(text) > MAX_NOTE_CHARS:
        raise HTTPException(status_code=400, detail=f"note exceeds {MAX_NOTE_CHARS} characters")

    row = (
        db.query(StockNoteModel)
        .filter(StockNoteModel.user_id == user.id, StockNoteModel.symbol == normalized)
        .first()
    )
    now = datetime.now(timezone.utc)
    if row is None:
        row = StockNoteModel(user_id=user.id, symbol=normalized, text=text, updated_at=now)
        db.add(row)
    else:
        row.text = text
        row.updated_at = now
    db.commit()
    db.refresh(row)
    return _to_schema(row)


@router.delete("/stock-notes/{symbol:path}", response_model=StockNoteDeleteResponse)
async def delete_stock_note(
    symbol: str,
    db: Session = Depends(get_db),
    user=Depends(get_current_user),
):
    normalized = normalize_stock_note_symbol(symbol)
    if not normalized:
        raise HTTPException(status_code=400, detail="symbol is required")
    row = (
        db.query(StockNoteModel)
        .filter(StockNoteModel.user_id == user.id, StockNoteModel.symbol == normalized)
        .first()
    )
    if row is not None:
        db.delete(row)
        db.commit()
    return StockNoteDeleteResponse(status="deleted", symbol=normalized)
