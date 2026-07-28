"""
Real-time stock context enricher.
Sources (all free, no API key):
  1. NSE equity CSV   — company names for ALL 2000+ NSE-listed stocks
  2. NSE India API    — P/E, sector P/E avg, 52-week range, live price
  3. Yahoo Finance API— P/E, sector, dividend yield (direct HTTP, not yfinance lib)
  4. Google News RSS  — headlines from ET, MoneyControl, Business Standard
  5. yfinance fast_info — market cap, fallback price
  6. yf.download()    — OHLCV history for RSI, Bollinger, SMAs, S/R levels
  7. Hardcoded maps   — sector + company name fallback for top 200 stocks
"""
from __future__ import annotations

import asyncio
import csv
import io
import logging
import re
import threading
from time import time
from typing import Optional
from urllib.parse import quote_plus
from xml.etree import ElementTree as ET

logger = logging.getLogger(__name__)

_cache: dict[str, tuple[float, dict]] = {}
_CACHE_TTL = 60  # seconds

# Loaded from NSE archives CSV — covers ALL ~2000+ NSE-listed stocks
_NSE_EQUITY_MAP: dict[str, str] = {}   # symbol -> full company name
_NSE_EQUITY_LOADED = False
_NSE_EQUITY_LOCK = threading.Lock()


def _load_nse_equity_map() -> None:
    """
    Downloads NSE EQUITY_L.csv once and builds symbol→company name map.
    Called lazily on first enrich() call. Cached for the process lifetime.
    """
    global _NSE_EQUITY_MAP, _NSE_EQUITY_LOADED
    with _NSE_EQUITY_LOCK:
        if _NSE_EQUITY_LOADED:
            return
        try:
            import requests
            resp = requests.get(
                "https://archives.nseindia.com/content/equities/EQUITY_L.csv",
                headers={
                    "User-Agent": "Mozilla/5.0",
                    "Referer": "https://www.nseindia.com/",
                },
                timeout=15,
            )
            if resp.status_code == 200:
                reader = csv.DictReader(io.StringIO(resp.text))
                for row in reader:
                    sym = row.get("SYMBOL", "").strip()
                    name = row.get("NAME OF COMPANY", "").strip()
                    if sym and name:
                        _NSE_EQUITY_MAP[sym] = name
                logger.info("NSE equity list loaded: %d symbols", len(_NSE_EQUITY_MAP))
            else:
                logger.warning("NSE equity CSV returned %s", resp.status_code)
        except Exception as e:
            logger.warning("NSE equity list load failed: %s", e)
        finally:
            _NSE_EQUITY_LOADED = True  # don't retry even on failure


def get_nse_equity_map() -> dict[str, str]:
    """Public accessor for the NSE EQUITY_L symbol→name map (~2000+ equities)."""
    _load_nse_equity_map()
    return dict(_NSE_EQUITY_MAP)
_NAME_TO_SYMBOL: dict[str, str] = {
    "reliance": "RELIANCE", "ril": "RELIANCE",
    "tcs": "TCS", "tata consultancy": "TCS",
    "infosys": "INFY", "infy": "INFY",
    "hdfc bank": "HDFCBANK", "hdfc": "HDFCBANK",
    "icici bank": "ICICIBANK", "icici": "ICICIBANK",
    "wipro": "WIPRO",
    "hcl": "HCLTECH", "hcltech": "HCLTECH",
    "sbi": "SBIN", "state bank": "SBIN",
    "bajaj finance": "BAJFINANCE", "bajaj fin": "BAJFINANCE",
    "kotak": "KOTAKBANK", "kotak bank": "KOTAKBANK",
    "axis bank": "AXISBANK", "axis": "AXISBANK",
    "maruti": "MARUTI", "maruti suzuki": "MARUTI",
    "adani enterprises": "ADANIENT", "adanient": "ADANIENT",
    "adani ports": "ADANIPORTS",
    "titan": "TITAN",
    "ultratech": "ULTRACEMCO", "ultratech cement": "ULTRACEMCO",
    "nestle": "NESTLEIND",
    "ltimindtree": "LTIM", "lti": "LTIM",
    "tech mahindra": "TECHM",
    "sun pharma": "SUNPHARMA", "sun pharmaceutical": "SUNPHARMA",
    "asian paints": "ASIANPAINT",
    "bharti airtel": "BHARTIARTL", "airtel": "BHARTIARTL",
    "ongc": "ONGC",
    "power grid": "POWERGRID",
    "ntpc": "NTPC",
    "coal india": "COALINDIA",
    "jio financial": "JIOFIN", "jio fin": "JIOFIN",
    "tata motors": "TATAMOTORS",
    "tata steel": "TATASTEEL",
    "hindalco": "HINDALCO",
    "jsw steel": "JSWSTEEL",
    "grasim": "GRASIM",
    "bpcl": "BPCL", "bharat petroleum": "BPCL",
    "hero motocorp": "HEROMOTOCO", "hero moto": "HEROMOTOCO",
    "bajaj auto": "BAJAJ-AUTO",
    "divis": "DIVISLAB", "divis lab": "DIVISLAB",
    "cipla": "CIPLA",
    "dr reddy": "DRREDDY", "dr reddys": "DRREDDY",
    "eicher": "EICHERMOT",
    "shriram": "SHRIRAMFIN", "shriram finance": "SHRIRAMFIN",
    "upl": "UPL",
    "britannia": "BRITANNIA",
    "indusind": "INDUSINDBK", "indusind bank": "INDUSINDBK",
    # Extended coverage
    "lupin": "LUPIN",
    "zomato": "ZOMATO",
    "irctc": "IRCTC",
    "havells": "HAVELLS",
    "pidilite": "PIDILITIND",
    "apollo hospital": "APOLLOHOSP", "apollo hospitals": "APOLLOHOSP",
    "sbi life": "SBILIFE", "sbi life insurance": "SBILIFE",
    "hdfc life": "HDFCLIFE", "hdfc life insurance": "HDFCLIFE",
    "bajaj finserv": "BAJAJFINSV",
    "chola": "CHOLAFIN", "cholamandalam": "CHOLAFIN",
    "trent": "TRENT",
    "dmart": "DMART", "avenue supermarts": "DMART",
    "muthoot": "MUTHOOTFIN", "muthoot finance": "MUTHOOTFIN",
    "bandhan": "BANDHANBNK", "bandhan bank": "BANDHANBNK",
    "biocon": "BIOCON",
    "auro pharma": "AUROPHARMA", "aurobindo": "AUROPHARMA",
    "torrent pharma": "TORNTPHARM", "torrent": "TORNTPHARM",
    "godrej": "GODREJCP", "godrej consumer": "GODREJCP",
    "polycab": "POLYCAB",
    "dixon": "DIXON",
    "vedanta": "VEDL",
    "sail": "SAIL",
    "nmdc": "NMDC",
    "rec": "RECLTD", "rec limited": "RECLTD",
    "pfc": "PFC", "power finance": "PFC",
    "irfc": "IRFC",
    "lici": "LICI", "lic": "LICI", "life insurance corporation": "LICI",
    "icici prudential": "ICICIPRULI",
    "icici lombard": "ICICIGI",
    "max healthcare": "MAXHEALTH",
    "fortis": "FORTIS",
    "page industries": "PAGEIND",
    "varun beverages": "VBL",
    "voltas": "VOLTAS",
    "abbott": "ABBOTINDIA",
    "zydus": "ZYDUSLIFE",
    "alkem": "ALKEM",
    "motherson": "MOTHERSUMI",
    "tata communication": "TATACOMM", "tata comm": "TATACOMM",
    "tata elxsi": "TATAELXSI",
    "tata chemicals": "TATACHEM",
    "jubilant food": "JUBLFOOD", "dominos": "JUBLFOOD",
    "star health": "STARHEALTH",
    "naukri": "NAUKRI", "info edge": "NAUKRI",
    "indigo": "INDIGO", "interglobe": "INDIGO",
    "spicejet": "SPICEJET",
    "sula": "SULA",
    "emami": "EMAMILTD",
    "colgate": "COLPAL",
    "marico": "MARICO",
    "dabur": "DABUR",
    "hul": "HINDUNILVR", "hindustan unilever": "HINDUNILVR",
    "ioc": "IOC", "indian oil": "IOC",
    "hpcl": "HPCL", "hindustan petroleum": "HPCL",
    "concor": "CONCOR", "container corporation": "CONCOR",
    "bhel": "BHEL",
    "abb": "ABB",
    "siemens": "SIEMENS",
    "l&t": "LT", "larsen": "LT", "larsen and toubro": "LT",
    "ltts": "LTTS", "l&t technology": "LTTS",
    "persistent": "PERSISTENT",
    "coforge": "COFORGE",
    "mphasis": "MPHASIS",
    "ofss": "OFSS", "oracle financial": "OFSS",
    "delhivery": "DELHIVERY",
    "paytm": "PAYTM", "one97": "PAYTM",
    "nykaa": "NYKAA", "fsn": "NYKAA",
    "policybazaar": "POLICYBZR",
    "cartrade": "CARTRADE",
    "anand rathi": "ANANDRATHI",
    "5paisa": "5PAISA",
    "angel": "ANGELONE", "angel one": "ANGELONE",
    "central bank": "CENTRALBK",
    "canara bank": "CANBK",
    "bank of baroda": "BANKBARODA", "bob": "BANKBARODA",
    "punjab national bank": "PNB", "pnb": "PNB",
    "union bank": "UNIONBANK",
    "federal bank": "FEDERALBNK",
    "idfc first": "IDFCFIRSTB", "idfc": "IDFCFIRSTB",
    "au small finance": "AUBANK", "au bank": "AUBANK",
    "equitas": "EQUITASBNK",
    "ujjivan": "UJJIVANSFB",
    "karnataka bank": "KTKBANK",
    "south indian bank": "SOUTHBANK",
    "city union bank": "CUB",
    "lakshmi vilas": "LAKSHVILAS",
}

_POS_WORDS = {
    "surge", "rally", "growth", "profit", "strong", "beat", "record",
    "gain", "rise", "bull", "upgrade", "positive", "high", "wins",
    "boost", "outperform", "buy", "overweight", "robust", "better",
    "recovery", "rebound", "momentum", "expansion", "milestone",
}
_NEG_WORDS = {
    "fall", "decline", "loss", "weak", "miss", "cut", "drop", "bear",
    "downgrade", "risk", "concern", "warning", "down", "low", "crash",
    "slump", "worry", "underperform", "sell", "underweight", "poor",
    "disappointing", "slowdown", "contraction", "pressure", "debt",
}

_NSE_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "*/*",
    "Accept-Language": "en-US,en;q=0.9",
    "Referer": "https://www.nseindia.com/",
}

# Hardcoded fallback for sector (never shows "N/A" for major stocks)
_SYMBOL_SECTOR: dict[str, str] = {
    "RELIANCE": "Oil & Gas / Refineries",
    "TCS": "Information Technology",
    "INFY": "Information Technology",
    "HDFCBANK": "Banking & Financial Services",
    "ICICIBANK": "Banking & Financial Services",
    "WIPRO": "Information Technology",
    "HCLTECH": "Information Technology",
    "SBIN": "Banking & Financial Services",
    "BAJFINANCE": "Financial Services / NBFC",
    "KOTAKBANK": "Banking & Financial Services",
    "AXISBANK": "Banking & Financial Services",
    "MARUTI": "Automobiles",
    "ADANIENT": "Infrastructure / Diversified",
    "ADANIPORTS": "Infrastructure / Ports",
    "TITAN": "Consumer Goods / Jewellery",
    "ULTRACEMCO": "Cement",
    "NESTLEIND": "FMCG",
    "LTIM": "Information Technology",
    "TECHM": "Information Technology",
    "SUNPHARMA": "Pharmaceuticals",
    "ASIANPAINT": "Paints / Consumer Goods",
    "BHARTIARTL": "Telecom",
    "ONGC": "Oil & Gas",
    "POWERGRID": "Power / Utilities",
    "NTPC": "Power / Utilities",
    "COALINDIA": "Mining / Energy",
    "JIOFIN": "Financial Services",
    "TATAMOTORS": "Automobiles",
    "TATASTEEL": "Steel / Metals",
    "HINDALCO": "Metals & Mining",
    "JSWSTEEL": "Steel / Metals",
    "GRASIM": "Diversified / Chemicals",
    "BPCL": "Oil & Gas",
    "HEROMOTOCO": "Automobiles",
    "BAJAJ-AUTO": "Automobiles",
    "DIVISLAB": "Pharmaceuticals",
    "CIPLA": "Pharmaceuticals",
    "DRREDDY": "Pharmaceuticals",
    "EICHERMOT": "Automobiles",
    "SHRIRAMFIN": "Financial Services / NBFC",
    "UPL": "Agrochemicals",
    "BRITANNIA": "FMCG",
    "INDUSINDBK": "Banking & Financial Services",
    # Extended
    "LUPIN": "Pharmaceuticals",
    "ZOMATO": "Consumer Internet / Food Tech",
    "IRCTC": "Tourism / Railways",
    "HAVELLS": "Consumer Electricals",
    "PIDILITIND": "Adhesives / Specialty Chemicals",
    "APOLLOHOSP": "Healthcare / Hospitals",
    "SBILIFE": "Life Insurance",
    "HDFCLIFE": "Life Insurance",
    "BAJAJFINSV": "Financial Services / Diversified",
    "CHOLAFIN": "Financial Services / NBFC",
    "TRENT": "Retail",
    "DMART": "Retail / Supermarkets",
    "MUTHOOTFIN": "Financial Services / Gold NBFC",
    "BANDHANBNK": "Banking & Financial Services",
    "BIOCON": "Pharmaceuticals / Biotechnology",
    "AUROPHARMA": "Pharmaceuticals",
    "TORNTPHARM": "Pharmaceuticals",
    "GODREJCP": "FMCG",
    "POLYCAB": "Cables & Wiring",
    "DIXON": "Electronics Manufacturing",
    "VEDL": "Metals & Mining",
    "SAIL": "Steel",
    "NMDC": "Mining / Iron Ore",
    "RECLTD": "Power Finance / NBFC",
    "PFC": "Power Finance / NBFC",
    "IRFC": "Infrastructure Finance",
    "LICI": "Life Insurance",
    "ICICIPRULI": "Life Insurance",
    "ICICIGI": "General Insurance",
    "MAXHEALTH": "Healthcare / Hospitals",
    "FORTIS": "Healthcare / Hospitals",
    "PAGEIND": "Apparel / Textiles",
    "VBL": "Beverages / FMCG",
    "VOLTAS": "Consumer Appliances",
    "ABBOTINDIA": "Pharmaceuticals",
    "ZYDUSLIFE": "Pharmaceuticals",
    "ALKEM": "Pharmaceuticals",
    "MOTHERSUMI": "Auto Ancillaries",
    "TATACOMM": "Telecom / IT Services",
    "TATAELXSI": "Information Technology",
    "TATACHEM": "Chemicals",
    "JUBLFOOD": "Quick Service Restaurants / Food",
    "STARHEALTH": "General Insurance",
    "NAUKRI": "Online Classifieds / Internet",
    "INDIGO": "Aviation",
    "HINDUNILVR": "FMCG",
    "IOC": "Oil & Gas",
    "HPCL": "Oil & Gas",
    "CONCOR": "Logistics / Railways",
    "BHEL": "Capital Goods / Engineering",
    "ABB": "Capital Goods / Engineering",
    "SIEMENS": "Capital Goods / Engineering",
    "LT": "Infrastructure / Engineering",
    "LTTS": "Information Technology / Engineering",
    "PERSISTENT": "Information Technology",
    "COFORGE": "Information Technology",
    "MPHASIS": "Information Technology",
    "OFSS": "Information Technology / BFSI",
    "DELHIVERY": "Logistics",
    "PAYTM": "Fintech / Digital Payments",
    "NYKAA": "Beauty / E-commerce",
    "POLICYBZR": "Insurance Aggregator / Fintech",
    "ANGELONE": "Broking / Financial Services",
    "CANBK": "Banking & Financial Services",
    "BANKBARODA": "Banking & Financial Services",
    "PNB": "Banking & Financial Services",
    "UNIONBANK": "Banking & Financial Services",
    "FEDERALBNK": "Banking & Financial Services",
    "IDFCFIRSTB": "Banking & Financial Services",
    "AUBANK": "Banking & Financial Services",
    "EMAMILTD": "FMCG",
    "COLPAL": "FMCG",
    "MARICO": "FMCG",
    "DABUR": "FMCG",
}

# Hardcoded full company names (fallback when all APIs return nothing)
_SYMBOL_COMPANY: dict[str, str] = {
    "RELIANCE": "Reliance Industries Ltd",
    "TCS": "Tata Consultancy Services Ltd",
    "INFY": "Infosys Ltd",
    "HDFCBANK": "HDFC Bank Ltd",
    "ICICIBANK": "ICICI Bank Ltd",
    "WIPRO": "Wipro Ltd",
    "HCLTECH": "HCL Technologies Ltd",
    "SBIN": "State Bank of India",
    "BAJFINANCE": "Bajaj Finance Ltd",
    "KOTAKBANK": "Kotak Mahindra Bank Ltd",
    "AXISBANK": "Axis Bank Ltd",
    "MARUTI": "Maruti Suzuki India Ltd",
    "ADANIENT": "Adani Enterprises Ltd",
    "ADANIPORTS": "Adani Ports and SEZ Ltd",
    "TITAN": "Titan Company Ltd",
    "ULTRACEMCO": "UltraTech Cement Ltd",
    "NESTLEIND": "Nestle India Ltd",
    "LTIM": "LTIMindtree Ltd",
    "TECHM": "Tech Mahindra Ltd",
    "SUNPHARMA": "Sun Pharmaceutical Industries Ltd",
    "ASIANPAINT": "Asian Paints Ltd",
    "BHARTIARTL": "Bharti Airtel Ltd",
    "ONGC": "Oil and Natural Gas Corporation Ltd",
    "POWERGRID": "Power Grid Corporation of India Ltd",
    "NTPC": "NTPC Ltd",
    "COALINDIA": "Coal India Ltd",
    "JIOFIN": "Jio Financial Services Ltd",
    "TATAMOTORS": "Tata Motors Ltd",
    "TATASTEEL": "Tata Steel Ltd",
    "HINDALCO": "Hindalco Industries Ltd",
    "JSWSTEEL": "JSW Steel Ltd",
    "GRASIM": "Grasim Industries Ltd",
    "BPCL": "Bharat Petroleum Corporation Ltd",
    "HEROMOTOCO": "Hero MotoCorp Ltd",
    "BAJAJ-AUTO": "Bajaj Auto Ltd",
    "DIVISLAB": "Divi's Laboratories Ltd",
    "CIPLA": "Cipla Ltd",
    "DRREDDY": "Dr. Reddy's Laboratories Ltd",
    "EICHERMOT": "Eicher Motors Ltd",
    "SHRIRAMFIN": "Shriram Finance Ltd",
    "UPL": "UPL Ltd",
    "BRITANNIA": "Britannia Industries Ltd",
    "INDUSINDBK": "IndusInd Bank Ltd",
    # Extended
    "LUPIN": "Lupin Ltd",
    "ZOMATO": "Zomato Ltd",
    "IRCTC": "Indian Railway Catering and Tourism Corporation Ltd",
    "HAVELLS": "Havells India Ltd",
    "PIDILITIND": "Pidilite Industries Ltd",
    "APOLLOHOSP": "Apollo Hospitals Enterprise Ltd",
    "SBILIFE": "SBI Life Insurance Company Ltd",
    "HDFCLIFE": "HDFC Life Insurance Company Ltd",
    "BAJAJFINSV": "Bajaj Finserv Ltd",
    "CHOLAFIN": "Cholamandalam Investment and Finance Company Ltd",
    "TRENT": "Trent Ltd",
    "DMART": "Avenue Supermarts Ltd",
    "MUTHOOTFIN": "Muthoot Finance Ltd",
    "BANDHANBNK": "Bandhan Bank Ltd",
    "BIOCON": "Biocon Ltd",
    "AUROPHARMA": "Aurobindo Pharma Ltd",
    "TORNTPHARM": "Torrent Pharmaceuticals Ltd",
    "GODREJCP": "Godrej Consumer Products Ltd",
    "POLYCAB": "Polycab India Ltd",
    "DIXON": "Dixon Technologies (India) Ltd",
    "VEDL": "Vedanta Ltd",
    "SAIL": "Steel Authority of India Ltd",
    "NMDC": "NMDC Ltd",
    "RECLTD": "REC Ltd",
    "PFC": "Power Finance Corporation Ltd",
    "IRFC": "Indian Railway Finance Corporation Ltd",
    "LICI": "Life Insurance Corporation of India",
    "ICICIPRULI": "ICICI Prudential Life Insurance Company Ltd",
    "ICICIGI": "ICICI Lombard General Insurance Company Ltd",
    "MAXHEALTH": "Max Healthcare Institute Ltd",
    "FORTIS": "Fortis Healthcare Ltd",
    "PAGEIND": "Page Industries Ltd",
    "VBL": "Varun Beverages Ltd",
    "VOLTAS": "Voltas Ltd",
    "ABBOTINDIA": "Abbott India Ltd",
    "ZYDUSLIFE": "Zydus Lifesciences Ltd",
    "ALKEM": "Alkem Laboratories Ltd",
    "MOTHERSUMI": "Samvardhana Motherson International Ltd",
    "TATACOMM": "Tata Communications Ltd",
    "TATAELXSI": "Tata Elxsi Ltd",
    "TATACHEM": "Tata Chemicals Ltd",
    "JUBLFOOD": "Jubilant FoodWorks Ltd",
    "STARHEALTH": "Star Health and Allied Insurance Company Ltd",
    "NAUKRI": "Info Edge (India) Ltd",
    "INDIGO": "InterGlobe Aviation Ltd",
    "HINDUNILVR": "Hindustan Unilever Ltd",
    "IOC": "Indian Oil Corporation Ltd",
    "HPCL": "Hindustan Petroleum Corporation Ltd",
    "CONCOR": "Container Corporation of India Ltd",
    "BHEL": "Bharat Heavy Electricals Ltd",
    "ABB": "ABB India Ltd",
    "SIEMENS": "Siemens Ltd",
    "LT": "Larsen & Toubro Ltd",
    "LTTS": "L&T Technology Services Ltd",
    "PERSISTENT": "Persistent Systems Ltd",
    "COFORGE": "Coforge Ltd",
    "MPHASIS": "Mphasis Ltd",
    "OFSS": "Oracle Financial Services Software Ltd",
    "DELHIVERY": "Delhivery Ltd",
    "PAYTM": "One 97 Communications Ltd",
    "NYKAA": "FSN E-Commerce Ventures Ltd",
    "POLICYBZR": "PB Fintech Ltd",
    "ANGELONE": "Angel One Ltd",
    "CANBK": "Canara Bank",
    "BANKBARODA": "Bank of Baroda",
    "PNB": "Punjab National Bank",
    "UNIONBANK": "Union Bank of India",
    "FEDERALBNK": "The Federal Bank Ltd",
    "IDFCFIRSTB": "IDFC First Bank Ltd",
    "AUBANK": "AU Small Finance Bank Ltd",
    "EMAMILTD": "Emami Ltd",
    "COLPAL": "Colgate-Palmolive (India) Ltd",
    "MARICO": "Marico Ltd",
    "DABUR": "Dabur India Ltd",
}


def normalize_hinglish(query: str) -> str:
    """
    Normalize Hinglish (Hindi-English mix) queries to English for NLP processing.
    Preserves stock symbols and financial terms.
    Examples:
    • "RELIANCE ka P/E kya hai?" → "RELIANCE of P/E what is?"
    • "₹1500 par buy karna chahiye?" → "₹1500 at buy should?"
    • "NIFTY me RELIANCE best hai?" → "NIFTY in RELIANCE best is?"
    """
    q = query

    # Hindi question words (placed at start for clarity)
    hinglish_words = {
        # Question words
        r'\bkya\b': 'what',
        r'\bkaun\b': 'who',
        r'\bkaise\b': 'how',
        r'\bkyun\b': 'why',
        r'\bkab\b': 'when',
        r'\bkahan\b': 'where',
        # Common postpositions / suffixes
        r'\bka\b': 'of',  # possession
        r'\bke\b': 'of',
        r'\bki\b': 'of',
        r'\bpar\b': 'at',  # location/price
        r'\bpe\b': 'at',
        r'\bme\b': 'in',  # location
        r'\bko\b': 'to',  # object marker
        r'\bse\b': 'from',  # ablative
        r'\btak\b': 'till',
        # Common verbs
        r'\bhona\b': 'is',
        r'\bhai\b': 'is',
        r'\bhain\b': 'are',
        r'\btha\b': 'was',
        r'\bthe\b': 'were',
        r'\bchahunga\b': 'want',
        r'\bchaiye\b': 'should',
        r'\bchahiye\b': 'should',
        r'\bsakte\b': 'can',
        r'\bsakte\s+ho\b': 'can',
        r'\bbata\b': 'tell',
        r'\bdo\b': 'give',
        r'\blena\b': 'take',
        r'\bkarna\b': 'do',
        # Interjections / common words
        r'\bji\b': '',  # respectful marker, remove
        r'\bna\b': 'not',  # negation
        r'\bnahi\b': 'no',
        r'\baur\b': 'and',
        r'\bya\b': 'or',
        r'\bto\b': 'then',
    }

    # Apply case-insensitive replacements
    for hindi, english in hinglish_words.items():
        q = re.sub(hindi, english, q, flags=re.IGNORECASE)

    # Clean up extra whitespace
    q = re.sub(r'\s+', ' ', q).strip()
    return q


def extract_all_symbols_from_query(query: str) -> list[str]:
    """
    Extract ALL symbols/stock names from query (not just first one).
    Returns list of symbols, empty list if none found.
    Used for multi-stock comparisons: "RELIANCE vs TCS vs INFY" → ["RELIANCE", "TCS", "INFY"]
    """
    q_lower = query.lower()
    symbols = []

    # Step 1: Extract via name lookups (most reliable — runs on longest matches first)
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        if name in q_lower and sym not in symbols:
            symbols.append(sym)

    # Step 2: Extract via regex (uppercase tokens not in skip list)
    _SKIP = {
        "A", "AN", "BY", "TO", "OF", "ON", "AS", "IS", "IN", "AT", "OR", "AND", "THE", "FOR",
        "HOW", "WHY", "WHAT", "WHEN", "WHERE", "WHICH", "WHO",
        "IT", "ITS", "THIS", "THAT", "THEY", "THEM",
        "BUY", "SELL", "GET", "CAN", "WILL", "HAS", "HIT", "ARE", "WAS", "GIVE", "SHOW", "TELL", "LIST", "FIND",
        "UP", "DOWN", "NOW", "NEW", "TOP", "BEST", "HIGH", "LOW", "BIG", "MID", "MOST", "LESS", "MORE", "ALL", "ANY",
        "STOCK", "STOCKS", "SHARE", "SHARES", "MARKET", "SECTOR", "SECTORS", "BANK", "BANKS", "FUND", "FUNDS",
        "INDEX", "NIFTY", "NSE", "BSE", "SENSEX", "IPO", "FII", "DII", "NIFTY50", "NIFTY100",
        "MOMENTUM", "SWING", "TRADING", "TRADE", "TRADER", "INTRADAY", "SCALP", "SCALPING", "BREAKOUT", "PULLBACK",
        "RALLY", "TREND", "TRENDING", "BULLISH", "BEARISH", "NEUTRAL", "TECHNICAL", "ANALYSIS", "SCREENER",
        "GROWTH", "INCOME", "DIVIDEND", "DIVIDENDS", "YIELD", "VALUE", "QUALITY", "RETURNS", "PROFIT", "LOSS",
        "SUPPORT", "RESIST", "VOLATILE", "VOLATILITY", "OVERVALUE", "UNDERVALUE",
        "SMALL", "LARGE", "MIDCAP", "LARGECAP", "SMALLCAP", "CAP", "MICRO", "NANO",
        "GOOD", "SAFE", "RISK", "RISKY", "INDIA", "INDIAN", "OPTION", "OPTIONS", "FUTURE", "FUTURES",
        "PORTFOLIO", "DIVERSIFY", "ANALYZE", "ANALYSE", "COMPARE", "PREDICT", "FORECAST", "ACCUMULATE", "PARK",
        "REBALANCE", "HOLD", "WATCH", "EXIT", "ENTER", "INVEST", "RECOMMEND", "NEAR", "STRONG", "WEAK", "QUICK",
        "FAIR", "FAIRLY", "BLUE", "CHIP", "CHIPS", "ENTRY", "SETUP", "WITH", "FROM", "ABOUT", "AFTER", "BEFORE",
        "OUTLOOK", "IMPACT", "EFFECT", "POINT", "POINTS", "MONEY", "GAINS", "PAYING", "QUARTER", "QUARTERLY",
        "ANNUAL", "MONTHLY", "PEERS", "YEAR", "MONTH", "WEEK", "TERM", "LONG", "SHORT", "NEXT", "LAST",
        "TODAY", "DAILY", "WEEKLY", "MONTHLY", "VS", "VERSUS",
    }
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', query.upper())
    for tok in tokens:
        if tok not in _SKIP and len(tok) >= 3 and tok not in symbols:
            symbols.append(tok)

    return symbols


def extract_entities_from_query(query: str) -> dict:
    """
    Extract financial entities: price targets, time horizons, quantities.
    Returns dict with extracted parameters for contextualizing response.
    """
    entities = {}
    q_lower = query.lower()

    # Price levels (₹XXXX, reach 1500, above 2000, target 3500)
    price_patterns = [
        (r'₹\s*([\d,]+(?:\.\d+)?)', 'price_inr'),
        (r'\$\s*([\d,]+(?:\.\d+)?)', 'price_usd'),
        (r'reach\s+([\d,]+(?:\.\d+)?)', 'price_target'),
        (r'target\s+([\d,]+(?:\.\d+)?)', 'price_target'),
        (r'(above|below)\s+([\d,]+(?:\.\d+)?)', 'price_level'),
        (r'at\s+([\d,]+(?:\.\d+)?)', 'price_level'),
    ]
    for pattern, key in price_patterns:
        match = re.search(pattern, q_lower)
        if match:
            price_str = match.group(1 if 'price_target' not in pattern else 1).replace(',', '')
            try:
                entities[key] = float(price_str)
                break  # Use first found price
            except ValueError:
                pass

    # Time horizons (3 months, next quarter, 1 year, by year-end)
    time_patterns = [
        (r'(\d+)\s*(month|week|day|year)', 'duration'),
        (r'next\s+(quarter|month|week|year)', 'next_period'),
        (r'(q[1-4]|quarter\s*[1-4]|h[1-2])', 'fiscal_period'),
        (r'year.?end', 'by_year_end'),
    ]
    for pattern, key in time_patterns:
        match = re.search(pattern, q_lower)
        if match:
            entities[key] = match.group(0)
            break

    # Quantity/percentage (10% gain, doubled, 2x return)
    quantity_patterns = [
        (r'(\d+)\s*%\s*(gain|return|rise|fall|drop)', 'percentage_target'),
        (r'(\d+)x\s*(return|gain|multiple)', 'multiple_target'),
        (r'double[d]?', 'multiple_target'),
    ]
    for pattern, key in quantity_patterns:
        match = re.search(pattern, q_lower)
        if match:
            entities[key] = match.group(0)
            break

    return entities


def extract_time_window_from_query(query: str) -> Optional[dict]:
    """
    Extract temporal window from query: '6 months ago' → {'period': '6m', 'lookback_days': 180}
    Returns None if no time reference found.
    Supported periods: 1d/5d/1mo/3mo/6mo/1y/2y/5y/10y/max
    """
    q_lower = query.lower()

    # Map time patterns to yfinance periods and lookback days
    time_patterns = [
        (r'(\d+)\s+days?\s+ago', lambda m: {
            'period': f"{int(m.group(1))}d" if int(m.group(1)) <= 60 else '3mo',
            'lookback_days': int(m.group(1))
        }),
        (r'(\d+)\s+weeks?\s+ago', lambda m: {
            'period': '1mo' if int(m.group(1)) <= 4 else '3mo' if int(m.group(1)) <= 13 else '1y',
            'lookback_days': int(m.group(1)) * 7
        }),
        (r'(\d+)\s+months?\s+ago', lambda m: {
            'period': f"{int(m.group(1))}mo" if int(m.group(1)) <= 6 else 'max',
            'lookback_days': int(m.group(1)) * 30
        }),
        (r'(\d+)\s+years?\s+ago', lambda m: {
            'period': f"{int(m.group(1))}y" if int(m.group(1)) <= 10 else 'max',
            'lookback_days': int(m.group(1)) * 365
        }),
        (r'last\s+(week|month|quarter|year)', lambda m: {
            'week': {'period': '5d', 'lookback_days': 7},
            'month': {'period': '1mo', 'lookback_days': 30},
            'quarter': {'period': '3mo', 'lookback_days': 90},
            'year': {'period': '1y', 'lookback_days': 365},
        }.get(m.group(1), {'period': '1mo', 'lookback_days': 30})),
        (r'past\s+(week|month|quarter|year)', lambda m: {
            'week': {'period': '5d', 'lookback_days': 7},
            'month': {'period': '1mo', 'lookback_days': 30},
            'quarter': {'period': '3mo', 'lookback_days': 90},
            'year': {'period': '1y', 'lookback_days': 365},
        }.get(m.group(1), {'period': '1mo', 'lookback_days': 30})),
        (r'in\s+the\s+past\s+(\d+)\s+(days?|weeks?|months?|years?)', lambda m: {
            'day': {'period': f"{int(m.group(1))}d", 'lookback_days': int(m.group(1))},
            'days': {'period': f"{int(m.group(1))}d", 'lookback_days': int(m.group(1))},
            'week': {'period': f"{int(m.group(1)) * 7}d", 'lookback_days': int(m.group(1)) * 7},
            'weeks': {'period': f"{int(m.group(1)) * 7}d", 'lookback_days': int(m.group(1)) * 7},
            'month': {'period': f"{int(m.group(1))}mo", 'lookback_days': int(m.group(1)) * 30},
            'months': {'period': f"{int(m.group(1))}mo", 'lookback_days': int(m.group(1)) * 30},
            'year': {'period': f"{int(m.group(1))}y", 'lookback_days': int(m.group(1)) * 365},
            'years': {'period': f"{int(m.group(1))}y", 'lookback_days': int(m.group(1)) * 365},
        }.get(m.group(2), {'period': '1mo', 'lookback_days': 30})),
    ]

    for pattern, handler in time_patterns:
        match = re.search(pattern, q_lower, re.IGNORECASE)
        if match:
            try:
                return handler(match)
            except Exception as e:
                logger.debug(f"Error parsing time window: {e}")
                continue

    return None


def link_news_to_price_moves(symbol: str, headlines: Optional[List[Dict]] = None, pct_change: float = 0) -> Optional[Dict]:
    """
    Link recent news events to price movements.
    Returns: {
        "likely_catalyst": headline text,
        "event_type": "earnings" | "dividend" | "regulation" | "acquisition" | "news",
        "sentiment": "positive" | "negative" | "neutral",
        "confidence": "high" | "medium" | "low"
    }
    """
    if not headlines or abs(pct_change) < 1.5:  # Ignore small moves
        return None

    # Event type keywords
    event_keywords = {
        "earnings": ["earn", "result", "q1", "q2", "q3", "q4", "quarterly", "fy", "ebitda", "profit"],
        "dividend": ["dividend", "bonus", "split", "rights", "distribution", "payout"],
        "regulation": ["sebi", "rbi", "regulation", "penalty", "ban", "fine", "court", "legal"],
        "acquisition": ["acqui", "merger", "deal", "stake", "buyout", "joint venture"],
        "management": ["ceo", "md", "chairman", "board", "resign", "appoint", "leadership"],
    }

    best_match = None
    best_confidence = 0

    for headline_obj in headlines[:5]:  # Check first 5 headlines
        if not headline_obj:
            continue

        title = headline_obj.get("title", "").lower()
        source = headline_obj.get("source", "").lower()

        # Keyword matching for event type
        for event_type, keywords in event_keywords.items():
            keyword_matches = sum(1 for kw in keywords if kw in title)
            if keyword_matches > 0:
                # Confidence calculation
                base_confidence = min(100, 30 + keyword_matches * 20)

                # Boost confidence if move is large (>3%)
                if abs(pct_change) > 3:
                    base_confidence = min(100, base_confidence + 20)

                # Check sentiment from headline keywords
                positive_words = ["bullish", "gain", "beat", "upgrade", "partnership", "growth", "record", "profit"]
                negative_words = ["bearish", "loss", "miss", "downgrade", "warning", "decline", "fraud", "weak"]

                sentiment = "neutral"
                if any(w in title for w in positive_words):
                    sentiment = "positive"
                elif any(w in title for w in negative_words):
                    sentiment = "negative"

                # Return if this is the best match so far
                if base_confidence > best_confidence:
                    best_confidence = base_confidence
                    best_match = {
                        "likely_catalyst": headline_obj.get("title", ""),
                        "event_type": event_type,
                        "sentiment": sentiment,
                        "confidence": "high" if base_confidence >= 70 else "medium" if base_confidence >= 50 else "low",
                        "source": source,
                    }

    return best_match


def screen_stocks(criteria: Optional[Dict] = None) -> List[Dict]:
    """
    Screen stocks by sector and financial criteria.
    criteria = {
        "sector": "IT" | "Banking" | "Pharma" | etc.,
        "pe_max": 25,          # P/E ratio upper limit
        "dividend_min": 2,     # Minimum dividend yield %
        "market_cap": "large" | "mid" | "small"  # Market cap category
    }
    Returns: List of matching stocks with quote data (top 10, sorted by P/E)
    """
    if not criteria:
        criteria = {}

    # Sector keywords mapping to stock symbols (common Indian stocks)
    SECTOR_STOCKS = {
        "IT": ["TCS", "INFY", "WIPRO", "HCLTECH", "TECHM", "MPHASIS", "LTTS"],
        "BANKING": ["SBIN", "ICICIBANK", "HDFCBANK", "AXISBANK", "KOTAKBANK", "INDUSINDBK"],
        "PHARMA": ["SUNPHARMA", "CIPLA", "LUPIN", "DIVISLAB", "BIOCON", "DRREDDY"],
        "AUTO": ["MARUTI", "TATAMOTORS", "M&M", "BAJAJ-AUTO", "HEROMOTOCO", "EICHERMOT"],
        "ENERGY": ["RELIANCE", "NTPC", "POWERGRID", "ONGC", "IOC", "BPCL"],
        "INFRA": ["LT", "ADANIPORTS", "ADANIENT", "IRCON", "RVNL"],
        "FMCG": ["ITC", "HINDUNILVR", "NESTLEIND", "BRITANNIA", "DABUR", "MARICO"],
        "CEMENT": ["ULTRACEMCO", "SHREECEM", "AMBUJACEM", "ACC", "DALMIACEM"],
        "METAL": ["TATASTEEL", "HINDALCO", "JSWSTEEL", "VEDL", "SAIL"],
        "DEFENCE": ["HAL", "BEL", "BDL", "MAZDOCK", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "DEFENSE": ["HAL", "BEL", "BDL", "MAZDOCK", "COCHINSHIP", "GRSE", "DATAPATTNS"],
        "PSU": ["SBIN", "NTPC", "ONGC", "BPCL", "IOC", "COALINDIA", "BEL", "HAL"],
        "REALTY": ["DLF", "GODREJPROP", "OBEROIRLTY", "PRESTIGE", "LODHA"],
        "RAILWAY": ["IRCTC", "IRFC", "RVNL", "IRCON", "RAILTEL"],
    }

    sector = criteria.get("sector", "").upper()
    pe_max = criteria.get("pe_max")
    dividend_min = criteria.get("dividend_min")

    # Get stocks for sector
    sector_key = None
    for key in SECTOR_STOCKS:
        if sector in key or key in sector:
            sector_key = key
            break

    stocks_to_check = SECTOR_STOCKS.get(sector_key, []) if sector_key else []

    if not stocks_to_check:
        return []

    # Fetch quotes for sector stocks
    try:
        from ..market_data import fetch_quotes
        quotes = fetch_quotes(stocks_to_check)
    except Exception as e:
        logger.debug(f"Could not fetch quotes: {e}")
        return []

    # Apply filters
    filtered = []
    for quote in quotes:
        if not quote:
            continue

        # P/E filter
        if pe_max and quote.get("pe", 999) > pe_max:
            continue

        # Dividend filter
        if dividend_min and quote.get("dividendYield", 0) < dividend_min:
            continue

        filtered.append(quote)

    # Sort by P/E (ascending) and return top 10
    filtered.sort(key=lambda x: x.get("pe", 999))
    return filtered[:10]


def extract_symbol_from_query(query: str) -> Optional[str]:
    # Strip Android prompt wrapper first so tokens like CONTEXT / WALLET aren't tickers.
    raw = (query or "").strip()
    if raw.lower().startswith("user_query:"):
        raw = raw.split(":", 1)[1].strip()
    if " | context:" in raw:
        raw = raw.split(" | context:", 1)[0].strip()
    query = raw
    q_lower = query.lower()

    # Step 1: reject general market / list queries — no specific stock being asked
    _GENERAL_PATTERNS = [
        r'\btop\b.{0,30}\bstocks?\b',
        r'\bbest\b.{0,30}\bstocks?\b',
        r'\bwhich\b.{0,30}\bstocks?\b',
        r'\blist\b.{0,30}\bstocks?\b',
        r'\bstocks?\b.{0,20}\bto\s+(buy|sell|watch|invest|trade|avoid|accumulate)\b',
        r'\bstocks?\b.{0,25}\bfor\s+(swing|day|long|short|momentum|growth|value|intraday|quick|medium)\b',
        r'\bstocks?\b.{0,20}\s*(near|around|below|above|under|paying|close)\b',
        r'\bundervalued\b.{0,30}\bstocks?\b',
        r'\bovervalued\b.{0,30}\bstocks?\b',
        r'\bgood\b.{0,20}\bstocks?\b',
        r'\bsafest?\b.{0,20}\bstocks?\b',
        r'\bdividend\b.{0,20}\bstocks?\b',
        r'\bblue.?chip\b',
        r'\b(optimize|rebalance|diversify)\b.{0,30}\bportfolio\b',
        r'\bmy\s+(portfolio|holdings?)\b',
        r'\bwhich\b.{0,30}\bholdings?\b',
        r'\bbest\b.{0,20}\bentry\b',
        r'\bentry\s+(point|price|level)s?\b',
        r'\b(bank|pharma|auto|energy|fmcg|defence|defense|infra|it|psu|realty|railway)\s+stocks?\b',
        r'\b(defence|defense|psu|realty)\b',
        r'\baccumulate\b',
        r'\bbreakout\b.{0,20}\bstocks?\b',
        r'\bstocks?\b.{0,20}\bbreakout\b',
        r'\bstocks?\b.{0,20}\bsetup\b',
        r'\brecommend\b',
        r'\bsuggestion[s]?\b',
        r'\bwhat\s+(are|is)\s+.{0,20}\bstocks?\b',
        r'\bhow\s+to\s+(pick|choose|select|find)\b',
        r'\bmarket\s+outlook\b',
        r'\bnifty\s+outlook\b',
    ]
    for pattern in _GENERAL_PATTERNS:
        if re.search(pattern, q_lower):
            return None

    # Step 2: name-based lookup — most reliable for natural language
    for name, sym in sorted(_NAME_TO_SYMBOL.items(), key=lambda x: -len(x[0])):
        if name in q_lower:
            return sym

    # Step 3: regex for explicit uppercase tickers (e.g. "Is LUPIN a buy?")
    _SKIP = {
        # articles / prepositions / conjunctions
        "A", "AN", "BY", "TO", "OF", "ON", "AS",
        "IS", "IN", "AT", "OR", "AND", "THE", "FOR",
        # question words
        "HOW", "WHY", "WHAT", "WHEN", "WHERE", "WHICH", "WHO",
        # pronouns
        "IT", "ITS", "THIS", "THAT", "THEY", "THEM",
        # common verbs
        "BUY", "SELL", "GET", "CAN", "WILL", "HAS", "HIT", "ARE", "WAS",
        "GIVE", "SHOW", "TELL", "LIST", "FIND", "SHOULD", "WANT",
        # directional / status
        "UP", "DOWN", "NOW", "NEW", "TOP", "BEST", "HIGH", "LOW", "BIG",
        "MID", "MOST", "LESS", "MORE", "ALL", "ANY",
        # market / finance terms that look like tickers
        "STOCK", "STOCKS", "SHARE", "SHARES", "MARKET", "SECTOR", "SECTORS",
        "BANK", "BANKS", "FUND", "FUNDS", "INDEX", "NIFTY", "NSE", "BSE",
        "SENSEX", "IPO", "FII", "DII", "NIFTY50", "NIFTY100",
        # trading / strategy terms
        "MOMENTUM", "SWING", "TRADING", "TRADE", "TRADER", "INTRADAY",
        "SCALP", "SCALPING", "BREAKOUT", "PULLBACK", "REBOUND",
        "RALLY", "RALLY", "TREND", "TRENDING",
        "BULLISH", "BEARISH", "NEUTRAL",
        "TECHNICAL", "ANALYSIS", "SCREENER",
        # financial metrics / concepts
        "GROWTH", "INCOME", "DIVIDEND", "DIVIDENDS", "YIELD",
        "VALUE", "QUALITY", "RETURNS", "PROFIT", "LOSS",
        "SUPPORT", "RESIST", "VOLATILE", "VOLATILITY",
        "OVERVALUE", "UNDERVALUE",
        # size / category
        "SMALL", "LARGE", "MIDCAP", "LARGECAP", "SMALLCAP", "CAP",
        "MICRO", "NANO",
        # descriptive / general
        "GOOD", "SAFE", "RISK", "RISKY", "INDIA", "INDIAN",
        "OPTION", "OPTIONS", "FUTURE", "FUTURES",
        "PORTFOLIO", "DIVERSIFY",
        # common verbs used in prompts
        "ANALYZE", "ANALYSE", "COMPARE", "PREDICT", "FORECAST",
        "ACCUMULATE", "PARK", "REBALANCE", "HOLD", "WATCH", "EXIT",
        "ENTER", "INVEST", "RECOMMEND",
        # adjectives / descriptors
        "NEAR", "STRONG", "WEAK", "QUICK", "FAIR", "FAIRLY",
        "BLUE", "CHIP", "CHIPS", "ENTRY", "SETUP", "WITH", "FROM",
        "ABOUT", "AFTER", "BEFORE",
        # non-stock nouns
        "OUTLOOK", "IMPACT", "EFFECT", "POINT", "POINTS",
        "MONEY", "GAINS", "PAYING", "QUARTER", "QUARTERLY",
        "ANNUAL", "MONTHLY", "PEERS", "SECTOR",
        # Android prompt-wrapper tokens
        "CONTEXT", "WALLET", "HOLDINGS", "HISTORY", "SYMBOL", "USER",
        "QUERY", "PORTFOLIOSCORE", "PCTCHANGE", "MARKETCAP",
        # time
        "YEAR", "MONTH", "WEEK", "TERM", "LONG", "SHORT",
        "NEXT", "LAST", "TODAY", "DAILY", "WEEKLY", "MONTHLY",
        # sector themes that look like tickers
        "DEFENCE", "DEFENSE", "PHARMA", "FMCG", "INFRA", "REALTY",
    }
    q_upper = query.upper().strip()
    tokens = re.findall(r'\b[A-Z][A-Z0-9\-]{1,9}\b', q_upper)
    for tok in tokens:
        if tok not in _SKIP and len(tok) >= 3:
            return tok

    # Step 4: fuzzy matching fallback for typos (e.g., "RELIANGE" → "RELIANCE")
    from difflib import get_close_matches
    extracted_tokens = re.findall(r'\b[A-Za-z0-9\-]{3,10}\b', query.upper())
    if extracted_tokens:
        for token in extracted_tokens:
            if token not in _SKIP:
                candidates = get_close_matches(token, list(_NAME_TO_SYMBOL.values()), n=1, cutoff=0.75)
                if candidates:
                    return candidates[0]

    return None



def _fmt_inr_cr(val) -> Optional[str]:
    try:
        v = float(val)
        cr = v / 1e7
        if cr >= 1_00_000:
            return f"₹{cr/1_00_000:.1f} lakh crore"
        if cr >= 1_000:
            return f"₹{cr:,.0f} crore"
        return f"₹{cr:.1f} crore"
    except (TypeError, ValueError):
        return None


def _rolling_mean(arr, n):
    if len(arr) < n:
        return None
    return sum(arr[-n:]) / n


def _rolling_std(arr, n):
    if len(arr) < n:
        return None
    mean = _rolling_mean(arr, n)
    variance = sum((x - mean) ** 2 for x in arr[-n:]) / n
    return variance ** 0.5


def _calc_rsi(closes, period=14):
    if len(closes) < period + 1:
        return None
    gains, losses = [], []
    for i in range(1, len(closes)):
        diff = closes[i] - closes[i - 1]
        gains.append(max(diff, 0))
        losses.append(max(-diff, 0))
    avg_gain = sum(gains[-period:]) / period
    avg_loss = sum(losses[-period:]) / period
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return round(100 - (100 / (1 + rs)), 1)


def _news_sentiment(headlines: list[str]) -> dict:
    pos, neg, neu = 0, 0, 0
    for h in headlines:
        words = set(h.lower().split())
        p = len(words & _POS_WORDS)
        n = len(words & _NEG_WORDS)
        if p > n:
            pos += 1
        elif n > p:
            neg += 1
        else:
            neu += 1
    total = pos + neg + neu or 1
    if pos > neg:
        overall = "Positive"
    elif neg > pos:
        overall = "Negative"
    else:
        overall = "Neutral"
    return {
        "overall": overall,
        "breakdown": f"Positive {pos*100//total}% / Neutral {neu*100//total}% / Negative {neg*100//total}%",
    }


# ---------------------------------------------------------------------------
# Source 1: NSE India API — P/E, sector, company name, 52-week, live price
# ---------------------------------------------------------------------------
def _fetch_nse_fundamentals(symbol: str) -> dict:
    """
    NSE India official API. Returns P/E, sector P/E avg, company name,
    industry/sector, 52-week high/low, and live price. No API key needed.
    """
    try:
        import requests
        session = requests.Session()
        session.headers.update(_NSE_HEADERS)
        # Step 1: visit home page to get cookies (NSE checks for session)
        session.get("https://www.nseindia.com/", timeout=8)
        resp = session.get(
            f"https://www.nseindia.com/api/quote-equity?symbol={symbol}",
            timeout=8,
        )
        if resp.status_code != 200:
            logger.warning("NSE API returned %s for %s", resp.status_code, symbol)
            return {}
        d = resp.json()
        meta = d.get("metadata", {})
        price_info = d.get("priceInfo", {})
        info = d.get("info", {})
        whl = price_info.get("weekHighLow", {})
        pe_val = meta.get("pdSymbolPe")
        pe_sector_val = meta.get("pdSectorPe")
        return {
            "company_name": info.get("companyName"),
            "sector": info.get("industry") or meta.get("industry"),
            "price": price_info.get("lastPrice") or price_info.get("close"),
            "pe": float(pe_val) if pe_val else None,
            "pe_sector": float(pe_sector_val) if pe_sector_val else None,
            "week52_high": float(whl["max"]) if whl.get("max") else None,
            "week52_low": float(whl["min"]) if whl.get("min") else None,
        }
    except Exception as e:
        logger.warning("NSE fetch failed for %s: %s", symbol, e)
        return {}


# ---------------------------------------------------------------------------
# Source 1b: Yahoo Finance direct API — P/E, sector, dividend (cloud-reliable)
# ---------------------------------------------------------------------------
_YF_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/120.0.0.0 Safari/537.36"
    ),
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
}


def _fetch_yahoo_fundamentals(symbol: str) -> dict:
    """
    Direct Yahoo Finance quoteSummary API call using requests (not yfinance lib).
    More reliable on cloud/Docker than ticker.info because we control the session.
    Returns P/E, dividend yield, sector, company name.
    """
    try:
        import requests
        yf_sym = f"{symbol}.NS"
        url = (
            "https://query1.finance.yahoo.com/v10/finance/quoteSummary/"
            f"{yf_sym}?modules=summaryDetail,summaryProfile,defaultKeyStatistics"
        )
        resp = requests.get(url, headers=_YF_HEADERS, timeout=10)
        if resp.status_code != 200:
            # try query2 mirror
            url2 = url.replace("query1", "query2")
            resp = requests.get(url2, headers=_YF_HEADERS, timeout=10)
        if resp.status_code != 200:
            logger.warning("Yahoo Finance direct API %s for %s", resp.status_code, symbol)
            return {}
        result = (resp.json()
                  .get("quoteSummary", {})
                  .get("result") or [{}])[0]

        def _raw(d: dict, key: str):
            v = d.get(key, {})
            return v.get("raw") if isinstance(v, dict) else v

        summary = result.get("summaryDetail", {})
        profile = result.get("summaryProfile", {})
        stats = result.get("defaultKeyStatistics", {})

        pe = _raw(summary, "trailingPE") or _raw(stats, "forwardPE")
        div = _raw(summary, "dividendYield") or _raw(summary, "trailingAnnualDividendYield")
        sector = profile.get("sector") or profile.get("industry")
        company_name = profile.get("longName") or profile.get("shortName")

        return {
            "pe": float(pe) if pe else None,
            "div_yield": float(div) if div else None,
            "sector": sector,
            "company_name": company_name,
        }
    except Exception as e:
        logger.warning("Yahoo Finance direct API failed for %s: %s", symbol, e)
        return {}


# ---------------------------------------------------------------------------
# Source 2: Google News RSS — aggregates ET, MoneyControl, Business Standard
# ---------------------------------------------------------------------------
def _fetch_google_news_headlines(symbol: str, company_name: str = "") -> list[str]:
    """
    Google News RSS feed for the stock. Aggregates headlines from
    Economic Times, MoneyControl, Business Standard etc. — no scraping needed.
    """
    try:
        import requests
        query = quote_plus(f"{company_name or symbol} NSE stock India")
        url = (
            f"https://news.google.com/rss/search"
            f"?q={query}&hl=en-IN&gl=IN&ceid=IN:en"
        )
        resp = requests.get(url, timeout=8, headers={"User-Agent": "Mozilla/5.0"})
        if resp.status_code != 200:
            return []
        root = ET.fromstring(resp.content)
        headlines: list[str] = []
        for item in root.findall(".//item")[:10]:
            title = item.findtext("title", "").strip()
            if title:
                # Google News appends " - Source Name"; strip it
                title = title.rsplit(" - ", 1)[0].strip()
                headlines.append(title)
        return headlines
    except Exception as e:
        logger.warning("Google News RSS failed for %s: %s", symbol, e)
        return []


# ---------------------------------------------------------------------------
# Main enricher: merges all four sources
# ---------------------------------------------------------------------------
def _fetch_yfinance(symbol: str) -> dict:
    try:
        import yfinance as yf

        # Ensure NSE equity list is loaded (covers all 2000+ NSE stocks)
        if not _NSE_EQUITY_LOADED:
            _load_nse_equity_map()

        nse_sym = symbol if symbol.endswith(".NS") else f"{symbol}.NS"
        ticker = yf.Ticker(nse_sym)

        # --- Source 1: NSE India API (primary for fundamentals) ---
        nse = _fetch_nse_fundamentals(symbol)
        company_name: str = nse.get("company_name") or ""
        sector: str = nse.get("sector") or ""
        price = nse.get("price")
        pe = nse.get("pe")
        pe_sector_avg = nse.get("pe_sector")
        week52_high = nse.get("week52_high")
        week52_low = nse.get("week52_low")

        # --- Source 1b: Yahoo Finance direct API (fills gaps when NSE is blocked) ---
        yf_fund = _fetch_yahoo_fundamentals(symbol)
        if not pe:
            pe = yf_fund.get("pe")
        div_yield = yf_fund.get("div_yield")
        if not sector:
            sector = yf_fund.get("sector") or ""
        if not company_name:
            company_name = yf_fund.get("company_name") or ""

        # --- NSE equity list (covers ALL NSE-listed stocks) ---
        if not company_name:
            company_name = _NSE_EQUITY_MAP.get(symbol, "")

        # --- Hardcoded maps (top 200 stocks, 100% reliable) ---
        if not sector:
            sector = _SYMBOL_SECTOR.get(symbol, "N/A")
        if not company_name:
            company_name = _SYMBOL_COMPANY.get(symbol, symbol)

        # --- Source 3: yfinance fast_info (market cap + fallback price) ---
        market_cap = None
        try:
            fi = ticker.fast_info
            market_cap = getattr(fi, "market_cap", None) or getattr(fi, "marketCap", None)
            if not price:
                price = getattr(fi, "last_price", None)
            if not week52_high:
                week52_high = getattr(fi, "fifty_two_week_high", None)
            if not week52_low:
                week52_low = getattr(fi, "fifty_two_week_low", None)
        except Exception as e:
            logger.warning("fast_info failed for %s: %s", symbol, e)

        # yfinance ticker.info: last-resort fill for anything still missing
        try:
            info = ticker.info or {}
            if not div_yield:
                div_yield = info.get("dividendYield")
            if not pe:
                pe = info.get("trailingPE") or info.get("forwardPE")
            if not sector or sector == "N/A":
                sector = info.get("sector") or info.get("industry") or _SYMBOL_SECTOR.get(symbol, "N/A")
            if not company_name or company_name == symbol:
                company_name = (info.get("longName") or info.get("shortName")
                                or _NSE_EQUITY_MAP.get(symbol)
                                or _SYMBOL_COMPANY.get(symbol, symbol))
            if not market_cap:
                market_cap = info.get("marketCap")
            if not week52_high:
                week52_high = info.get("fiftyTwoWeekHigh")
            if not week52_low:
                week52_low = info.get("fiftyTwoWeekLow")
            if not price:
                price = (info.get("currentPrice")
                         or info.get("regularMarketPrice")
                         or info.get("previousClose"))
        except Exception as e:
            logger.warning("ticker.info failed for %s: %s", symbol, e)

        # 52-week position string
        pos_52w = None
        if price and week52_high and week52_low and week52_high > week52_low:
            pct = (price - week52_low) / (week52_high - week52_low) * 100
            if pct >= 80:
                position = "near 52-week high"
            elif pct <= 20:
                position = "near 52-week low"
            else:
                position = "middle of range"
            pos_52w = f"₹{week52_low:,.2f} – ₹{week52_high:,.2f} | Current at {pct:.0f}% ({position})"

        # --- Source 4: yf.download() for OHLCV (technicals) ---
        closes, highs, lows = [], [], []
        try:
            hist = yf.download(nse_sym, period="1y", progress=False, auto_adjust=True)
            if not hist.empty:
                # Newer yfinance returns MultiIndex columns (field, ticker) — flatten
                if hasattr(hist.columns, "levels"):
                    hist.columns = hist.columns.get_level_values(0)
                if "Close" in hist.columns:
                    closes = [float(v) for v in hist["Close"].dropna()]
                if "High" in hist.columns:
                    highs = [float(v) for v in hist["High"].dropna()]
                if "Low" in hist.columns:
                    lows = [float(v) for v in hist["Low"].dropna()]
        except Exception as e:
            logger.warning("yf.download failed for %s: %s", symbol, e)

        current = closes[-1] if closes else price

        # Moving Averages
        sma5   = _rolling_mean(closes, 5)
        sma20  = _rolling_mean(closes, 20)
        sma50  = _rolling_mean(closes, 50)
        sma200 = _rolling_mean(closes, 200)

        ma_lines = []
        if sma5:   ma_lines.append(f"5-SMA ₹{sma5:,.2f}")
        if sma20:  ma_lines.append(f"20-SMA ₹{sma20:,.2f}")
        if sma50:  ma_lines.append(f"50-SMA ₹{sma50:,.2f}")
        if sma200: ma_lines.append(f"200-SMA ₹{sma200:,.2f}")

        if current and sma50 and sma200:
            if current > sma50 > sma200:
                ma_trend = "Bullish — price above 50 & 200 SMA. " + ", ".join(ma_lines)
            elif current < sma50 < sma200:
                ma_trend = "Bearish — price below 50 & 200 SMA. " + ", ".join(ma_lines)
            else:
                ma_trend = "Mixed. " + ", ".join(ma_lines)
        elif ma_lines:
            ma_trend = ", ".join(ma_lines)
        else:
            ma_trend = None

        # Bollinger Bands (20-day)
        bb_band = None
        if len(closes) >= 20:
            bb_mid = _rolling_mean(closes, 20)
            bb_std = _rolling_std(closes, 20)
            if bb_mid and bb_std:
                bb_upper = bb_mid + 2 * bb_std
                bb_lower = bb_mid - 2 * bb_std
                if current:
                    if current > bb_upper:
                        bb_pos = "above upper band (overbought signal)"
                    elif current < bb_lower:
                        bb_pos = "below lower band (oversold signal)"
                    else:
                        pct_in = (current - bb_lower) / (bb_upper - bb_lower) * 100
                        bb_pos = f"{pct_in:.0f}% within bands"
                    bb_band = (f"Upper ₹{bb_upper:,.2f} | Mid ₹{bb_mid:,.2f} | "
                               f"Lower ₹{bb_lower:,.2f} — price {bb_pos}")

        # RSI
        rsi_val = _calc_rsi(closes)
        rsi_interp = None
        rsi_str = None
        if rsi_val is not None:
            if rsi_val > 70:
                rsi_interp = "overbought"
            elif rsi_val < 30:
                rsi_interp = "oversold"
            else:
                rsi_interp = "neutral"
            rsi_str = f"{rsi_val} ({rsi_interp})"

        # Support & Resistance
        support1 = round(min(lows[-20:]), 2) if len(lows) >= 20 else None
        resistance1 = round(max(highs[-20:]), 2) if len(highs) >= 20 else None
        support2 = round(min(lows[-52:]), 2) if len(lows) >= 52 else None
        resistance2 = round(max(highs[-52:]), 2) if len(highs) >= 52 else None

        # Stop Loss & Take Profit
        stop_loss = round(support1 * 0.98, 2) if support1 else None
        take_profit = resistance1
        rr_ratio = None
        if current and stop_loss and take_profit and current > stop_loss:
            risk = current - stop_loss
            reward = take_profit - current
            if risk > 0:
                rr_ratio = f"1 : {reward/risk:.1f}"

        # Sector trend from 1-month price momentum
        sector_trend = "Neutral"
        if len(closes) >= 21:
            ret_1m = (closes[-1] - closes[-21]) / closes[-21] * 100
            if ret_1m > 3:
                sector_trend = f"Bullish (stock +{ret_1m:.1f}% in 1 month)"
            elif ret_1m < -3:
                sector_trend = f"Bearish (stock {ret_1m:.1f}% in 1 month)"
            else:
                sector_trend = f"Neutral (stock {ret_1m:+.1f}% in 1 month)"

        # --- Source 2: Google News RSS (primary for headlines) ---
        headlines = _fetch_google_news_headlines(symbol, company_name)

        # Fallback: yfinance news if Google News returned nothing
        if not headlines:
            try:
                raw_news = ticker.news or []
                for item in raw_news[:8]:
                    title = (item.get("title")
                             or (item.get("content") or {}).get("title", ""))
                    if title:
                        headlines.append(title)
            except Exception:
                pass

        sentiment = _news_sentiment(headlines) if headlines else {}

        fundamental_data = {
            "pe_ratio": f"{pe:.1f}" if pe else None,
            "pe_sector_avg": f"{pe_sector_avg:.1f}" if pe_sector_avg else "~25 (market avg)",
            "market_cap": _fmt_inr_cr(market_cap),
            "dividend_yield": f"{div_yield*100:.2f}%" if div_yield else "Not declared",
            "week_52": pos_52w,
        }
        trading_levels_data = {
            "support_1": f"{support1:,.2f}" if support1 else None,
            "support_2": f"{support2:,.2f}" if support2 else None,
            "resistance_1": f"{resistance1:,.2f}" if resistance1 else None,
            "resistance_2": f"{resistance2:,.2f}" if resistance2 else None,
            "stop_loss": f"{stop_loss:,.2f}" if stop_loss else None,
            "take_profit": f"{take_profit:,.2f}" if take_profit else None,
            "risk_reward": rr_ratio,
        }
        technical_data = {
            "rsi": rsi_str,
            "rsi_interpretation": rsi_interp,
            "bollinger_bands": bb_band,
            "moving_averages": ma_trend,
            "trend": ("strong_bullish" if current and sma50 and sma200 and current > sma50 > sma200
                      else "bearish" if current and sma50 and sma200 and current < sma50 < sma200
                      else "neutral"),
        }

        # Pre-compute signal conclusions for each indicator
        pre_signals = compute_pre_signals(technical_data, fundamental_data, trading_levels_data, current)

        return {
            "symbol": symbol,
            "company_name": company_name,
            "sector": sector,
            "current_price": f"{current:,.2f}" if current else None,
            "fundamental": fundamental_data,
            "technical": technical_data,
            "trading_levels": trading_levels_data,
            "sentiment": {
                "overall": sentiment.get("overall"),
                "breakdown": sentiment.get("breakdown"),
                "recent_events": headlines[:5],
                "sector_trend": sector_trend,
            },
            "news_headlines": headlines,
            "pre_signals": pre_signals,
        }

    except Exception as exc:
        logger.error("Stock enricher failed for %s: %s", symbol, exc, exc_info=True)
        raise


async def enrich(symbol: str) -> dict:
    now = time()
    cached = _cache.get(symbol)
    if cached and (now - cached[0]) < _CACHE_TTL:
        return cached[1]
    loop = asyncio.get_event_loop()
    data = await loop.run_in_executor(None, _fetch_yfinance, symbol)
    if data:
        _cache[symbol] = (now, data)
    return data


def format_news_for_prompt(headlines: list[str]) -> str:
    if not headlines:
        return ""
    lines = "\n".join(f"  • {h}" for h in headlines)
    return f"RECENT NEWS HEADLINES:\n{lines}"


def compute_pre_signals(
    tech: dict, fund: dict, tl: dict, current_price: Optional[float]
) -> dict:
    """
    Pre-compute human-readable signal conclusions from raw data so the LLM
    receives analysis results rather than raw numbers.
    """
    signals: dict = {}

    # RSI signal
    try:
        rsi_val = float(str(tech.get("rsi", "")).split()[0].replace(",", ""))
        if rsi_val >= 75:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Strongly overbought, high reversal risk, avoid chasing"
        elif rsi_val >= 70:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Overbought zone, potential short-term pullback"
        elif rsi_val <= 25:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Strongly oversold, watch for reversal/bounce"
        elif rsi_val <= 30:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Oversold, potential accumulation zone"
        elif 48 <= rsi_val <= 55:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Neutral momentum, no directional signal"
        elif rsi_val > 55:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Bullish momentum building, trend intact"
        else:
            signals["rsi_signal"] = f"RSI {rsi_val:.0f} — Bearish momentum, wait for stabilization"
    except (ValueError, TypeError, IndexError):
        pass

    # MA trend signal
    ma_str = str(tech.get("moving_averages", "") or tech.get("trend", "")).lower()
    if "strong_bullish" in ma_str or "strongly bullish" in ma_str:
        signals["ma_signal"] = "All major MAs aligned bullish — strong uptrend, dips are buying opportunities"
    elif "bullish" in ma_str and "above" in ma_str:
        signals["ma_signal"] = "Price above key MAs — uptrend intact, pullbacks to 50-SMA are entry zones"
    elif "strong_bearish" in ma_str or "strongly bearish" in ma_str:
        signals["ma_signal"] = "Price below all major MAs — strong downtrend, avoid buying until MA reclaim"
    elif "bearish" in ma_str and "below" in ma_str:
        signals["ma_signal"] = "Price below key MAs — downtrend, wait for 20-SMA reclaim before buying"
    elif ma_str:
        signals["ma_signal"] = "Mixed MA signals — sideways consolidation, wait for directional breakout"

    # 52-week position
    week52_str = str(fund.get("week_52", "") or "")
    if current_price and week52_str:
        try:
            nums = re.findall(r"[\d]+\.?\d*", week52_str.replace(",", ""))
            if len(nums) >= 2:
                low52, high52 = float(nums[0]), float(nums[-1])
                rng = high52 - low52
                if rng > 0:
                    pos_pct = (current_price - low52) / rng * 100
                    if pos_pct >= 90:
                        signals["week52_signal"] = (
                            f"Near 52-week HIGH ({pos_pct:.0f}% of range) — "
                            f"resistance at ₹{high52:,.0f}, profit booking risk"
                        )
                    elif pos_pct >= 70:
                        signals["week52_signal"] = (
                            f"Upper half of 52-week range ({pos_pct:.0f}%) — "
                            f"momentum favors bulls but watch ₹{high52:,.0f} resistance"
                        )
                    elif pos_pct <= 10:
                        signals["week52_signal"] = (
                            f"Near 52-week LOW ({pos_pct:.0f}% of range) — "
                            f"potential value zone, but confirm before buying"
                        )
                    elif pos_pct <= 30:
                        signals["week52_signal"] = (
                            f"Lower 52-week range ({pos_pct:.0f}%) — "
                            f"contrarian opportunity, but sentiment still negative"
                        )
                    else:
                        signals["week52_signal"] = (
                            f"Mid 52-week range ({pos_pct:.0f}%) — "
                            f"neutral zone, ₹{high52:,.0f} target | ₹{low52:,.0f} floor"
                        )
        except (ValueError, IndexError):
            pass

    # Support/Resistance proximity
    if current_price and tl.get("support_1") and tl.get("resistance_1"):
        try:
            s1 = float(str(tl["support_1"]).replace(",", "").replace("₹", ""))
            r1 = float(str(tl["resistance_1"]).replace(",", "").replace("₹", ""))
            dist_sup = (current_price - s1) / current_price * 100
            dist_res = (r1 - current_price) / current_price * 100
            if dist_sup < 2:
                signals["level_signal"] = (
                    f"Price ≈ support ₹{s1:,.0f} ({dist_sup:.1f}% away) — "
                    f"low-risk entry zone, stop below ₹{s1:,.0f}"
                )
            elif dist_res < 2:
                signals["level_signal"] = (
                    f"Price ≈ resistance ₹{r1:,.0f} ({dist_res:.1f}% away) — "
                    f"breakout or rejection imminent, wait for confirmation"
                )
            elif dist_res < dist_sup:
                signals["level_signal"] = (
                    f"Limited upside to resistance ₹{r1:,.0f} ({dist_res:.1f}%) "
                    f"vs downside to support ₹{s1:,.0f} ({dist_sup:.1f}%) — "
                    f"unfavorable risk/reward until breakout"
                )
            else:
                signals["level_signal"] = (
                    f"Good risk/reward — ₹{dist_res:.1f}% upside to ₹{r1:,.0f} "
                    f"vs ₹{dist_sup:.1f}% risk to support ₹{s1:,.0f}"
                )
        except (ValueError, TypeError):
            pass

    # P/E valuation signal
    try:
        pe = float(str(fund.get("pe_ratio", "") or "").replace(",", "").split()[0])
        if pe < 10:
            signals["pe_signal"] = f"P/E {pe:.1f} — Very cheap, potential value trap or turnaround; check earnings trend"
        elif pe < 18:
            signals["pe_signal"] = f"P/E {pe:.1f} — Reasonably valued, room for re-rating if earnings grow"
        elif pe < 30:
            signals["pe_signal"] = f"P/E {pe:.1f} — Fairly to slightly premium, earnings growth must sustain"
        elif pe < 50:
            signals["pe_signal"] = f"P/E {pe:.1f} — Premium valuation, growth expectations already priced in"
        else:
            signals["pe_signal"] = f"P/E {pe:.1f} — Expensive, high risk if earnings disappoint"
    except (ValueError, TypeError, IndexError):
        pass

    return signals
