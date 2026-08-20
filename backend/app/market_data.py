"""
Real-time market data provider using Yahoo Finance.
Fetches live NSE/BSE stock prices with caching to avoid rate limits.
Covers ALL major Indian stocks – NIFTY 500 and beyond.
"""

import yfinance as yf
import logging
import os
import re
import time
import difflib
import urllib.parse
import urllib.request
import json as _json
from datetime import datetime
from threading import Lock, Thread
from typing import Dict, Optional, List

logger = logging.getLogger(__name__)


class _SuppressYfinanceDelisted(logging.Filter):
    """Yahoo empty charts are rate-limits/blocks, not delistings of RELIANCE/TCS."""

    def filter(self, record: logging.LogRecord) -> bool:
        try:
            msg = record.getMessage().lower()
        except Exception:
            return True
        if "possibly delisted" in msg or "no price data found" in msg:
            return False
        return True


logging.getLogger("yfinance").addFilter(_SuppressYfinanceDelisted())


def _env_int(name: str, default: int, minimum: int = 1) -> int:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    try:
        parsed = int(raw_value)
    except Exception:
        return default
    return max(minimum, parsed)


# Freshness vs storage: keep last prints in memory so UI can paint while Yahoo
# refreshes. Default get() freshness is session-aware (see quote_max_age_seconds).
QUOTE_CACHE_TTL_OPEN = _env_int("QUOTE_CACHE_TTL_OPEN", 5, minimum=3)
QUOTE_CACHE_TTL_CLOSED = _env_int("QUOTE_CACHE_TTL_CLOSED", 180, minimum=30)
QUOTE_CACHE_STORAGE_SECONDS = _env_int("QUOTE_CACHE_STORAGE_SECONDS", 300, minimum=60)
# Backward-compatible alias: default freshness when market is open.
QUOTE_CACHE_TTL_SECONDS = _env_int("QUOTE_CACHE_TTL_SECONDS", QUOTE_CACHE_TTL_OPEN, minimum=3)
QUOTE_CACHE_MAX_ENTRIES = _env_int("QUOTE_CACHE_MAX_ENTRIES", 3000, minimum=50)
QUOTE_BATCH_SIZE = _env_int("QUOTE_BATCH_SIZE", 40, minimum=1)
# Cap per-symbol Ticker.history fallback. A 20–40 name sequential walk is the 40–50s hang.
QUOTE_INDIVIDUAL_FALLBACK_MAX = _env_int("QUOTE_INDIVIDUAL_FALLBACK_MAX", 2, minimum=0)
QUOTE_YF_DOWNLOAD_TIMEOUT = _env_int("QUOTE_YF_DOWNLOAD_TIMEOUT", 8, minimum=3)
QUOTE_V7_TIMEOUT = _env_int("QUOTE_V7_TIMEOUT", 4, minimum=2)
# PE / EPS / yield / avg volume / target change slowly — keep them off the 5s last-price path.
FUNDAMENTALS_CACHE_TTL_SECONDS = _env_int("FUNDAMENTALS_CACHE_TTL_SECONDS", 4 * 3600, minimum=300)

HISTORY_ALLOWED_PERIODS = {
    "1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y", "10y", "ytd", "max"
}
HISTORY_ALLOWED_INTERVALS = {
    "1m", "2m", "5m", "15m", "30m", "60m", "90m", "1h", "1d", "5d", "1wk", "1mo", "3mo"
}

# If Yahoo returns empty for a preferred interval, try these in order.
HISTORY_INTERVAL_FALLBACKS = {
    ("5d", "15m"): [("5d", "15m"), ("5d", "30m"), ("5d", "1h"), ("5d", "5m")],
    ("5d", "30m"): [("5d", "30m"), ("5d", "15m"), ("5d", "1h")],
    ("1mo", "1d"): [("1mo", "1d"), ("1mo", "1h")],
    ("3mo", "1d"): [("3mo", "1d")],
    ("1y", "1d"): [("1y", "1d"), ("1y", "1wk")],
    ("1y", "1wk"): [("1y", "1wk"), ("1y", "1d")],
}

# ─────────────────────────────────────────────────────────────
# Complete Indian stock catalog  –  symbol → (Yahoo ticker, Company name)
# Includes NIFTY 50, NIFTY Next 50, NIFTY Midcap 150, NIFTY Smallcap,
# BSE popular, and other actively traded stocks on NSE.
# Any symbol NOT in this dict still works – we append .NS automatically.
# ─────────────────────────────────────────────────────────────

INDIAN_STOCKS: Dict[str, tuple] = {
    # ── NIFTY 50 ──────────────────────────────────────────────
    "RELIANCE":     ("RELIANCE.NS",     "Reliance Industries Ltd"),
    "TCS":          ("TCS.NS",          "Tata Consultancy Services Ltd"),
    "HDFCBANK":     ("HDFCBANK.NS",     "HDFC Bank Ltd"),
    "INFY":         ("INFY.NS",         "Infosys Ltd"),
    "ICICIBANK":    ("ICICIBANK.NS",    "ICICI Bank Ltd"),
    "HINDUNILVR":   ("HINDUNILVR.NS",   "Hindustan Unilever Ltd"),
    "ITC":          ("ITC.NS",          "ITC Ltd"),
    "SBIN":         ("SBIN.NS",         "State Bank of India"),
    "BHARTIARTL":   ("BHARTIARTL.NS",   "Bharti Airtel Ltd"),
    "KOTAKBANK":    ("KOTAKBANK.NS",    "Kotak Mahindra Bank Ltd"),
    "LT":           ("LT.NS",           "Larsen & Toubro Ltd"),
    "AXISBANK":     ("AXISBANK.NS",     "Axis Bank Ltd"),
    "BAJFINANCE":   ("BAJFINANCE.NS",   "Bajaj Finance Ltd"),
    "ASIANPAINT":   ("ASIANPAINT.NS",   "Asian Paints Ltd"),
    "MARUTI":       ("MARUTI.NS",       "Maruti Suzuki India Ltd"),
    "TITAN":        ("TITAN.NS",        "Titan Company Ltd"),
    "SUNPHARMA":    ("SUNPHARMA.NS",    "Sun Pharmaceutical Industries Ltd"),
    # Tata Motors demerger: TATAMOTORS is no longer a listed NSE symbol.
    # Listed: TMPV (passenger vehicles / JLR) and TMCV (commercial vehicles).
    "TMPV":         ("TMPV.NS",         "Tata Motors Passenger Vehicles Ltd"),
    "TMCV":         ("TMCV.NS",         "Tata Motors Ltd (Commercial Vehicles)"),
    "WIPRO":        ("WIPRO.NS",        "Wipro Ltd"),
    "ULTRACEMCO":   ("ULTRACEMCO.NS",   "UltraTech Cement Ltd"),
    "NESTLEIND":    ("NESTLEIND.NS",    "Nestle India Ltd"),
    "HCLTECH":      ("HCLTECH.NS",      "HCL Technologies Ltd"),
    "TATASTEEL":    ("TATASTEEL.NS",    "Tata Steel Ltd"),
    "NTPC":         ("NTPC.NS",         "NTPC Ltd"),
    "POWERGRID":    ("POWERGRID.NS",    "Power Grid Corporation of India Ltd"),
    "TECHM":        ("TECHM.NS",        "Tech Mahindra Ltd"),
    "BAJAJFINSV":   ("BAJAJFINSV.NS",   "Bajaj Finserv Ltd"),
    "ONGC":         ("ONGC.NS",         "Oil & Natural Gas Corporation Ltd"),
    "JSWSTEEL":     ("JSWSTEEL.NS",     "JSW Steel Ltd"),
    "ADANIENT":     ("ADANIENT.NS",     "Adani Enterprises Ltd"),
    "HDFCLIFE":     ("HDFCLIFE.NS",     "HDFC Life Insurance Company Ltd"),
    "DIVISLAB":     ("DIVISLAB.NS",     "Divi's Laboratories Ltd"),
    "DRREDDY":      ("DRREDDY.NS",      "Dr. Reddy's Laboratories Ltd"),
    "SBILIFE":      ("SBILIFE.NS",      "SBI Life Insurance Company Ltd"),
    "BRITANNIA":    ("BRITANNIA.NS",    "Britannia Industries Ltd"),
    "CIPLA":        ("CIPLA.NS",        "Cipla Ltd"),
    "EICHERMOT":    ("EICHERMOT.NS",    "Eicher Motors Ltd"),
    "HEROMOTOCO":   ("HEROMOTOCO.NS",   "Hero MotoCorp Ltd"),
    "APOLLOHOSP":   ("APOLLOHOSP.NS",   "Apollo Hospitals Enterprise Ltd"),
    "GRASIM":       ("GRASIM.NS",       "Grasim Industries Ltd"),
    "M&M":          ("M&M.NS",          "Mahindra & Mahindra Ltd"),
    "BPCL":         ("BPCL.NS",         "Bharat Petroleum Corporation Ltd"),
    "COALINDIA":    ("COALINDIA.NS",    "Coal India Ltd"),
    "HINDALCO":     ("HINDALCO.NS",     "Hindalco Industries Ltd"),
    "INDUSINDBK":   ("INDUSINDBK.NS",   "IndusInd Bank Ltd"),
    "ADANIPORTS":   ("ADANIPORTS.NS",   "Adani Ports and Special Economic Zone Ltd"),
    "TATACONSUM":   ("TATACONSUM.NS",   "Tata Consumer Products Ltd"),
    "BAJAJ-AUTO":   ("BAJAJ-AUTO.NS",   "Bajaj Auto Ltd"),
    "SHREECEM":     ("SHREECEM.NS",     "Shree Cement Ltd"),
    "WIPRO":        ("WIPRO.NS",        "Wipro Ltd"),

    # ── NIFTY NEXT 50 ────────────────────────────────────────
    "ADANIGREEN":   ("ADANIGREEN.NS",   "Adani Green Energy Ltd"),
    "ADANIENSOL":   ("ADANIENSOL.NS",   "Adani Energy Solutions Ltd"),
    "AMBUJACEM":    ("AMBUJACEM.NS",    "Ambuja Cements Ltd"),
    "BANDHANBNK":   ("BANDHANBNK.NS",   "Bandhan Bank Ltd"),
    "BANKBARODA":   ("BANKBARODA.NS",   "Bank of Baroda"),
    "BERGEPAINT":   ("BERGEPAINT.NS",   "Berger Paints India Ltd"),
    "BIOCON":       ("BIOCON.NS",       "Biocon Ltd"),
    "BOSCHLTD":     ("BOSCHLTD.NS",     "Bosch Ltd"),
    "COLPAL":       ("COLPAL.NS",       "Colgate-Palmolive (India) Ltd"),
    "DABUR":        ("DABUR.NS",        "Dabur India Ltd"),
    "DLF":          ("DLF.NS",          "DLF Ltd"),
    "GAIL":         ("GAIL.NS",         "GAIL (India) Ltd"),
    "GODREJCP":     ("GODREJCP.NS",     "Godrej Consumer Products Ltd"),
    "HAVELLS":      ("HAVELLS.NS",      "Havells India Ltd"),
    "ICICIGI":      ("ICICIGI.NS",      "ICICI Lombard General Insurance Co Ltd"),
    "ICICIPRULI":   ("ICICIPRULI.NS",   "ICICI Prudential Life Insurance Co Ltd"),
    "INDUSTOWER":   ("INDUSTOWER.NS",   "Indus Towers Ltd"),
    "IOC":          ("IOC.NS",          "Indian Oil Corporation Ltd"),
    "IRCTC":        ("IRCTC.NS",        "Indian Railway Catering and Tourism Corp Ltd"),
    "JUBLFOOD":     ("JUBLFOOD.NS",     "Jubilant FoodWorks Ltd"),
    "LICI":         ("LICI.NS",         "Life Insurance Corporation of India"),
    "LUPIN":        ("LUPIN.NS",        "Lupin Ltd"),
    "MARICO":       ("MARICO.NS",       "Marico Ltd"),
    "MCDOWELL-N":   ("MCDOWELL-N.NS",  "United Spirits Ltd"),
    "MUTHOOTFIN":   ("MUTHOOTFIN.NS",   "Muthoot Finance Ltd"),
    "NAUKRI":       ("NAUKRI.NS",       "Info Edge (India) Ltd"),
    "PEL":          ("PEL.NS",          "Piramal Enterprises Ltd"),
    "PGHH":         ("PGHH.NS",         "Procter & Gamble Hygiene & Health Care Ltd"),
    "PIDILITIND":   ("PIDILITIND.NS",   "Pidilite Industries Ltd"),
    "PNB":          ("PNB.NS",          "Punjab National Bank"),
    "SBICARD":      ("SBICARD.NS",      "SBI Cards and Payment Services Ltd"),
    "SIEMENS":      ("SIEMENS.NS",      "Siemens Ltd"),
    "SRF":          ("SRF.NS",          "SRF Ltd"),
    "TORNTPHARM":   ("TORNTPHARM.NS",   "Torrent Pharmaceuticals Ltd"),
    "TRENT":        ("TRENT.NS",        "Trent Ltd"),
    "VEDL":         ("VEDL.NS",         "Vedanta Ltd"),
    "ETERNAL":      ("ETERNAL.NS",      "Eternal Ltd (Zomato)"),

    # ── NIFTY MIDCAP 150 / POPULAR MIDCAPS ──────────────────
    "AARTIIND":     ("AARTIIND.NS",     "Aarti Industries Ltd"),
    "ABB":          ("ABB.NS",          "ABB India Ltd"),
    "ABCAPITAL":    ("ABCAPITAL.NS",    "Aditya Birla Capital Ltd"),
    "ABFRL":        ("ABFRL.NS",        "Aditya Birla Fashion and Retail Ltd"),
    "ACC":          ("ACC.NS",          "ACC Ltd"),
    "AFFLE":        ("AFFLE.NS",        "Affle (India) Ltd"),
    "AJANTPHARM":   ("AJANTPHARM.NS",   "Ajanta Pharma Ltd"),
    "ALKEM":        ("ALKEM.NS",        "Alkem Laboratories Ltd"),
    "ALKYLAMINE":   ("ALKYLAMINE.NS",   "Alkyl Amines Chemicals Ltd"),
    "AMARAJABAT":   ("AMARAJABAT.NS",   "Amara Raja Energy & Mobility Ltd"),
    "ANGELONE":     ("ANGELONE.NS",     "Angel One Ltd"),
    "APLAPOLLO":    ("APLAPOLLO.NS",    "APL Apollo Tubes Ltd"),
    "ASHOKLEY":     ("ASHOKLEY.NS",     "Ashok Leyland Ltd"),
    "ASTRAL":       ("ASTRAL.NS",       "Astral Ltd"),
    "ATUL":         ("ATUL.NS",         "Atul Ltd"),
    "AUBANK":       ("AUBANK.NS",       "AU Small Finance Bank Ltd"),
    "AUROPHARMA":   ("AUROPHARMA.NS",   "Aurobindo Pharma Ltd"),
    "BALKRISIND":   ("BALKRISIND.NS",   "Balkrishna Industries Ltd"),
    "BALRAMCHIN":   ("BALRAMCHIN.NS",   "Balrampur Chini Mills Ltd"),
    "BATAINDIA":    ("BATAINDIA.NS",    "Bata India Ltd"),
    "BEL":          ("BEL.NS",          "Bharat Electronics Ltd"),
    "BHARATFORG":   ("BHARATFORG.NS",   "Bharat Forge Ltd"),
    "BHEL":         ("BHEL.NS",         "Bharat Heavy Electricals Ltd"),
    "BSE":          ("BSE.NS",          "BSE Ltd"),
    "CANBK":        ("CANBK.NS",        "Canara Bank"),
    "CANFINHOME":   ("CANFINHOME.NS",   "Can Fin Homes Ltd"),
    "CDSL":         ("CDSL.NS",         "Central Depository Services (India) Ltd"),
    "CENTRALBK":    ("CENTRALBK.NS",    "Central Bank of India"),
    "CHAMBLFERT":   ("CHAMBLFERT.NS",   "Chambal Fertilisers and Chemicals Ltd"),
    "CHOLAFIN":     ("CHOLAFIN.NS",     "Cholamandalam Investment and Finance Co Ltd"),
    "CLEAN":        ("CLEAN.NS",        "Clean Science and Technology Ltd"),
    "COFORGE":      ("COFORGE.NS",      "Coforge Ltd"),
    "CONCOR":       ("CONCOR.NS",       "Container Corporation of India Ltd"),
    "COROMANDEL":   ("COROMANDEL.NS",   "Coromandel International Ltd"),
    "CROMPTON":     ("CROMPTON.NS",     "Crompton Greaves Consumer Electricals Ltd"),
    "CUMMINSIND":   ("CUMMINSIND.NS",   "Cummins India Ltd"),
    "DEEPAKNTR":    ("DEEPAKNTR.NS",    "Deepak Nitrite Ltd"),
    "DELHIVERY":    ("DELHIVERY.NS",    "Delhivery Ltd"),
    "DEVYANI":      ("DEVYANI.NS",      "Devyani International Ltd"),
    "DIXON":        ("DIXON.NS",        "Dixon Technologies (India) Ltd"),
    "ESCORTS":      ("ESCORTS.NS",      "Escorts Kubota Ltd"),
    "EXIDEIND":     ("EXIDEIND.NS",     "Exide Industries Ltd"),
    "FEDERALBNK":   ("FEDERALBNK.NS",   "Federal Bank Ltd"),
    "FORTIS":       ("FORTIS.NS",       "Fortis Healthcare Ltd"),
    "GLENMARK":     ("GLENMARK.NS",     "Glenmark Pharmaceuticals Ltd"),
    "GMRAIRPORT":   ("GMRAIRPORT.NS",   "GMR Airports Ltd"),
    "GNFC":         ("GNFC.NS",         "Gujarat Narmada Valley Fertilizers & Chemicals Ltd"),
    "GODREJPROP":   ("GODREJPROP.NS",   "Godrej Properties Ltd"),
    "GRANULES":     ("GRANULES.NS",     "Granules India Ltd"),
    "GSPL":         ("GSPL.NS",         "Gujarat State Petronet Ltd"),
    "GUJGASLTD":    ("GUJGASLTD.NS",   "Gujarat Gas Ltd"),
    "HAL":          ("HAL.NS",          "Hindustan Aeronautics Ltd"),
    "HDFCAMC":      ("HDFCAMC.NS",     "HDFC Asset Management Company Ltd"),
    "HINDCOPPER":   ("HINDCOPPER.NS",   "Hindustan Copper Ltd"),
    "HINDPETRO":    ("HINDPETRO.NS",    "Hindustan Petroleum Corporation Ltd"),
    "HONAUT":       ("HONAUT.NS",       "Honeywell Automation India Ltd"),
    "IDFCFIRSTB":   ("IDFCFIRSTB.NS",  "IDFC First Bank Ltd"),
    "IEX":          ("IEX.NS",          "Indian Energy Exchange Ltd"),
    "IIFL":         ("IIFL.NS",         "IIFL Finance Ltd"),
    "INDHOTEL":     ("INDHOTEL.NS",     "Indian Hotels Company Ltd"),
    "INDIACEM":     ("INDIACEM.NS",     "India Cements Ltd"),
    "INDIAMART":    ("INDIAMART.NS",    "IndiaMART InterMESH Ltd"),
    "INDIANB":      ("INDIANB.NS",      "Indian Bank"),
    "IRFC":         ("IRFC.NS",         "Indian Railway Finance Corporation Ltd"),
    "IPCALAB":      ("IPCALAB.NS",      "IPCA Laboratories Ltd"),
    "JINDALSTEL":   ("JINDALSTEL.NS",   "Jindal Steel & Power Ltd"),
    "JKCEMENT":     ("JKCEMENT.NS",     "JK Cement Ltd"),
    "JSWENERGY":    ("JSWENERGY.NS",    "JSW Energy Ltd"),
    "KAJARIACER":   ("KAJARIACER.NS",   "Kajaria Ceramics Ltd"),
    "KEI":          ("KEI.NS",          "KEI Industries Ltd"),
    "KPITTECH":     ("KPITTECH.NS",     "KPIT Technologies Ltd"),
    "LALPATHLAB":   ("LALPATHLAB.NS",   "Dr Lal PathLabs Ltd"),
    "LATENTVIEW":   ("LATENTVIEW.NS",   "Latent View Analytics Ltd"),
    "LAURUSLABS":   ("LAURUSLABS.NS",   "Laurus Labs Ltd"),
    "LICHSGFIN":    ("LICHSGFIN.NS",    "LIC Housing Finance Ltd"),
    "LTIM":         ("LTIM.NS",         "LTIMindtree Ltd"),
    "LTTS":         ("LTTS.NS",         "L&T Technology Services Ltd"),
    "MANAPPURAM":   ("MANAPPURAM.NS",   "Manappuram Finance Ltd"),
    "MAXHEALTH":    ("MAXHEALTH.NS",    "Max Healthcare Institute Ltd"),
    "MFSL":         ("MFSL.NS",         "Max Financial Services Ltd"),
    "MGL":          ("MGL.NS",          "Mahanagar Gas Ltd"),
    "MOTHERSON":    ("MOTHERSON.NS",    "Samvardhana Motherson International Ltd"),
    "MPHASIS":      ("MPHASIS.NS",      "Mphasis Ltd"),
    "MRF":          ("MRF.NS",          "MRF Ltd"),
    "NATIONALUM":   ("NATIONALUM.NS",   "National Aluminium Company Ltd"),
    "NAVINFLUOR":   ("NAVINFLUOR.NS",   "Navin Fluorine International Ltd"),
    "NBCC":         ("NBCC.NS",         "NBCC (India) Ltd"),
    "NCC":          ("NCC.NS",          "NCC Ltd"),
    "NIACL":        ("NIACL.NS",        "New India Assurance Company Ltd"),
    "NMDC":         ("NMDC.NS",         "NMDC Ltd"),
    "OBEROIRLTY":   ("OBEROIRLTY.NS",  "Oberoi Realty Ltd"),
    "OFSS":         ("OFSS.NS",         "Oracle Financial Services Software Ltd"),
    "PAGEIND":      ("PAGEIND.NS",      "Page Industries Ltd"),
    "PATANJALI":    ("PATANJALI.NS",    "Patanjali Foods Ltd"),
    "PERSISTENT":   ("PERSISTENT.NS",   "Persistent Systems Ltd"),
    "PETRONET":     ("PETRONET.NS",     "Petronet LNG Ltd"),
    "PHOENIXLTD":   ("PHOENIXLTD.NS",   "Phoenix Mills Ltd"),
    "PIIND":        ("PIIND.NS",        "PI Industries Ltd"),
    "POLYCAB":      ("POLYCAB.NS",      "Polycab India Ltd"),
    "POONAWALLA":   ("POONAWALLA.NS",   "Poonawalla Fincorp Ltd"),
    "PRESTIGE":     ("PRESTIGE.NS",     "Prestige Estates Projects Ltd"),
    "PVRINOX":      ("PVRINOX.NS",      "PVR INOX Ltd"),
    "RAJESHEXPO":   ("RAJESHEXPO.NS",   "Rajesh Exports Ltd"),
    "RAMCOCEM":     ("RAMCOCEM.NS",     "Ramco Cements Ltd"),
    "RBLBANK":      ("RBLBANK.NS",      "RBL Bank Ltd"),
    "RECLTD":       ("RECLTD.NS",       "REC Ltd"),
    "SAIL":         ("SAIL.NS",         "Steel Authority of India Ltd"),
    "SANOFI":       ("SANOFI.NS",       "Sanofi India Ltd"),
    "SCHAEFFLER":   ("SCHAEFFLER.NS",   "Schaeffler India Ltd"),
    "SHRIRAMFIN":   ("SHRIRAMFIN.NS",   "Shriram Finance Ltd"),
    "SONACOMS":     ("SONACOMS.NS",     "Sona BLW Precision Forgings Ltd"),
    "STARHEALTH":   ("STARHEALTH.NS",   "Star Health and Allied Insurance Co Ltd"),
    "SUNDARMFIN":   ("SUNDARMFIN.NS",   "Sundaram Finance Ltd"),
    "SUPREMEIND":   ("SUPREMEIND.NS",   "Supreme Industries Ltd"),
    "SYNGENE":      ("SYNGENE.NS",      "Syngene International Ltd"),
    "TATACOMM":     ("TATACOMM.NS",     "Tata Communications Ltd"),
    "TATAELXSI":    ("TATAELXSI.NS",    "Tata Elxsi Ltd"),
    "TATAPOWER":    ("TATAPOWER.NS",    "Tata Power Company Ltd"),
    "TATACHEM":     ("TATACHEM.NS",     "Tata Chemicals Ltd"),
    "TORNTPOWER":   ("TORNTPOWER.NS",   "Torrent Power Ltd"),
    "TVSMOTOR":     ("TVSMOTOR.NS",     "TVS Motor Company Ltd"),
    "UBL":          ("UBL.NS",          "United Breweries Ltd"),
    "UNIONBANK":    ("UNIONBANK.NS",    "Union Bank of India"),
    "UPL":          ("UPL.NS",          "UPL Ltd"),
    "VBL":          ("VBL.NS",          "Varun Beverages Ltd"),
    "VOLTAS":       ("VOLTAS.NS",       "Voltas Ltd"),
    "WHIRLPOOL":    ("WHIRLPOOL.NS",    "Whirlpool of India Ltd"),
    "ZEEL":         ("ZEEL.NS",         "Zee Entertainment Enterprises Ltd"),
    "ZYDUSLIFE":    ("ZYDUSLIFE.NS",    "Zydus Lifesciences Ltd"),

    # ── ADDITIONAL POPULAR / SMALL & MICRO CAPS ─────────────
    "ADANIPOWER":   ("ADANIPOWER.NS",   "Adani Power Ltd"),
    "ATGL":         ("ATGL.NS",         "Adani Total Gas Ltd"),
    "AWL":          ("AWL.NS",          "Adani Wilmar Ltd"),
    "BDL":          ("BDL.NS",          "Bharat Dynamics Ltd"),
    "BRIGADE":      ("BRIGADE.NS",      "Brigade Enterprises Ltd"),
    "CAMS":         ("CAMS.NS",         "Computer Age Management Services Ltd"),
    "CAMPUS":       ("CAMPUS.NS",       "Campus Activewear Ltd"),
    "CARBORUNIV":   ("CARBORUNIV.NS",   "Carborundum Universal Ltd"),
    "CASTROLIND":   ("CASTROLIND.NS",   "Castrol India Ltd"),
    "CENTURYPLY":   ("CENTURYPLY.NS",   "Century Plyboards (India) Ltd"),
    "CESC":         ("CESC.NS",         "CESC Ltd"),
    "CUB":          ("CUB.NS",          "City Union Bank Ltd"),
    "CYIENT":       ("CYIENT.NS",       "Cyient Ltd"),
    "DATAPATTNS":   ("DATAPATTNS.NS",   "Data Patterns (India) Ltd"),
    "DCMSHRIRAM":   ("DCMSHRIRAM.NS",   "DCM Shriram Ltd"),
    "DELTACORP":    ("DELTACORP.NS",    "Delta Corp Ltd"),
    "EMAMILTD":     ("EMAMILTD.NS",     "Emami Ltd"),
    "ENDURANCE":    ("ENDURANCE.NS",    "Endurance Technologies Ltd"),
    "ENGINERSIN":   ("ENGINERSIN.NS",   "Engineers India Ltd"),
    "EQUITASBNK":   ("EQUITASBNK.NS",  "Equitas Small Finance Bank Ltd"),
    "FINCABLES":    ("FINCABLES.NS",    "Finolex Cables Ltd"),
    "FINPIPE":      ("FINPIPE.NS",      "Finolex Industries Ltd"),
    "FLUOROCHEM":   ("FLUOROCHEM.NS",   "Gujarat Fluorochemicals Ltd"),
    "FSL":          ("FSL.NS",          "Firstsource Solutions Ltd"),
    "GICRE":        ("GICRE.NS",        "General Insurance Corporation of India"),
    "GILLETTE":     ("GILLETTE.NS",     "Gillette India Ltd"),
    "GLAXO":        ("GLAXO.NS",        "GlaxoSmithKline Pharmaceuticals Ltd"),
    "GRINDWELL":    ("GRINDWELL.NS",    "Grindwell Norton Ltd"),
    "GRSE":         ("GRSE.NS",         "Garden Reach Shipbuilders & Engineers Ltd"),
    "GSFC":         ("GSFC.NS",         "Gujarat State Fertilizers & Chemicals Ltd"),
    "HAPPSTMNDS":   ("HAPPSTMNDS.NS",   "Happiest Minds Technologies Ltd"),
    "HFCL":         ("HFCL.NS",         "HFCL Ltd"),
    "HUDCO":        ("HUDCO.NS",        "Housing & Urban Development Corp Ltd"),
    "SAMMAANCAP":   ("SAMMAANCAP.NS",   "Sammaan Capital Ltd"),
    "IDBI":         ("IDBI.NS",         "IDBI Bank Ltd"),
    "IDEA":         ("IDEA.NS",         "Vodafone Idea Ltd"),
    "360ONE":       ("360ONE.NS",       "360 ONE WAM Ltd"),
    "INDIGO":       ("INDIGO.NS",       "InterGlobe Aviation Ltd"),
    "INDIGOPNTS":   ("INDIGOPNTS.NS",   "Indigo Paints Ltd"),
    "IOB":          ("IOB.NS",          "Indian Overseas Bank"),
    "ISEC":         ("ISEC.NS",         "ICICI Securities Ltd"),
    "ITI":          ("ITI.NS",          "ITI Ltd"),
    "JBCHEPHARM":   ("JBCHEPHARM.NS",   "JB Chemicals & Pharmaceuticals Ltd"),
    "JINDALSAW":    ("JINDALSAW.NS",    "Jindal Saw Ltd"),
    "JKLAKSHMI":    ("JKLAKSHMI.NS",    "JK Lakshmi Cement Ltd"),
    "JMFINANCIL":   ("JMFINANCIL.NS",  "JM Financial Ltd"),
    "JUBLINGREA":   ("JUBLINGREA.NS",   "Jubilant Ingrevia Ltd"),
    "JUSTDIAL":     ("JUSTDIAL.NS",     "Just Dial Ltd"),
    "KALPATPOWR":   ("KALPATPOWR.NS",  "Kalpataru Projects International Ltd"),
    "KANSAINER":    ("KANSAINER.NS",    "Kansai Nerolac Paints Ltd"),
    "KEC":          ("KEC.NS",          "KEC International Ltd"),
    "KIOCL":        ("KIOCL.NS",        "KIOCL Ltd"),
    "KNRCON":       ("KNRCON.NS",       "KNR Constructions Ltd"),
    "KPRMILL":      ("KPRMILL.NS",      "KPR Mill Ltd"),
    "LTF":          ("LTF.NS",          "L&T Finance Ltd"),
    "LAXMIMACH":    ("LAXMIMACH.NS",    "Lakshmi Machine Works Ltd"),
    "LINDEINDIA":   ("LINDEINDIA.NS",   "Linde India Ltd"),
    "M&MFIN":       ("M&MFIN.NS",       "Mahindra & Mahindra Financial Services Ltd"),
    "MAHABANK":     ("MAHABANK.NS",     "Bank of Maharashtra"),
    "MAHLIFE":      ("MAHLIFE.NS",      "Mahindra Lifespace Developers Ltd"),
    "MANYAVAR":     ("MANYAVAR.NS",     "Vedant Fashions Ltd"),
    "MAPMYINDIA":   ("MAPMYINDIA.NS",  "CE Info Systems Ltd"),
    "MASTEK":       ("MASTEK.NS",       "Mastek Ltd"),
    "MCX":          ("MCX.NS",          "Multi Commodity Exchange of India Ltd"),
    "MEDANTA":      ("MEDANTA.NS",      "Global Health Ltd"),
    "METROPOLIS":   ("METROPOLIS.NS",   "Metropolis Healthcare Ltd"),
    "UNOMINDA":     ("UNOMINDA.NS",     "UNO Minda Ltd"),
    "MOTILALOFS":   ("MOTILALOFS.NS",   "Motilal Oswal Financial Services Ltd"),
    "CANBK":        ("CANBK.NS",        "Canara Bank"),
    "DALBHARAT":    ("DALBHARAT.NS",    "Dalmia Bharat Ltd"),
    "EIH":          ("EIH.NS",          "EIH Ltd"),
    "LEMONTREE":    ("LEMONTREE.NS",    "Lemon Tree Hotels Ltd"),
    "NAM-INDIA":    ("NAM-INDIA.NS",    "Nippon Life India Asset Management Ltd"),
    "NATCOPHARM":   ("NATCOPHARM.NS",   "Natco Pharma Ltd"),
    "NAUKRI":       ("NAUKRI.NS",       "Info Edge (India) Ltd"),
    "NHPC":         ("NHPC.NS",         "NHPC Ltd"),
    "NLCINDIA":     ("NLCINDIA.NS",     "NLC India Ltd"),
    "NOCIL":        ("NOCIL.NS",        "NOCIL Ltd"),
    "OLECTRA":      ("OLECTRA.NS",      "Olectra Greentech Ltd"),
    "PAYTM":        ("PAYTM.NS",        "One97 Communications Ltd"),
    "PCBL":         ("PCBL.NS",         "PCBL Ltd"),
    "PFC":          ("PFC.NS",          "Power Finance Corporation Ltd"),
    "PNBHOUSING":   ("PNBHOUSING.NS",  "PNB Housing Finance Ltd"),
    "POLICYBZR":    ("POLICYBZR.NS",    "PB Fintech Ltd"),
    "POLYMED":      ("POLYMED.NS",      "Poly Medicure Ltd"),
    "RADICO":       ("RADICO.NS",       "Radico Khaitan Ltd"),
    "RAIN":         ("RAIN.NS",         "Rain Industries Ltd"),
    "RAJESHEXPO":   ("RAJESHEXPO.NS",   "Rajesh Exports Ltd"),
    "RALLIS":       ("RALLIS.NS",       "Rallis India Ltd"),
    "RATNAMANI":    ("RATNAMANI.NS",    "Ratnamani Metals & Tubes Ltd"),
    "RAYMOND":      ("RAYMOND.NS",      "Raymond Ltd"),
    "RELAXO":       ("RELAXO.NS",       "Relaxo Footwears Ltd"),
    "RITES":        ("RITES.NS",        "RITES Ltd"),
    "ROUTE":        ("ROUTE.NS",        "Route Mobile Ltd"),
    "RVNL":         ("RVNL.NS",         "Rail Vikas Nigam Ltd"),
    "SAPPHIRE":     ("SAPPHIRE.NS",     "Sapphire Foods India Ltd"),
    "SBICARD":      ("SBICARD.NS",      "SBI Cards and Payment Services Ltd"),
    "SJVN":         ("SJVN.NS",         "SJVN Ltd"),
    "SOBHA":        ("SOBHA.NS",        "Sobha Ltd"),
    "SOLARA":       ("SOLARA.NS",       "Solara Active Pharma Sciences Ltd"),
    "SONATSOFTW":   ("SONATSOFTW.NS",   "Sonata Software Ltd"),
    "SPARC":        ("SPARC.NS",        "Sun Pharma Advanced Research Company Ltd"),
    "STARCEMENT":   ("STARCEMENT.NS",   "Star Cement Ltd"),
    "SUMICHEM":     ("SUMICHEM.NS",     "Sumitomo Chemical India Ltd"),
    "SUNDRMFAST":   ("SUNDRMFAST.NS",   "Sundram Fasteners Ltd"),
    "SUNTV":        ("SUNTV.NS",        "Sun TV Network Ltd"),
    "SUZLON":       ("SUZLON.NS",       "Suzlon Energy Ltd"),
    "SWANENERGY":   ("SWANENERGY.NS",   "Swan Energy Ltd"),
    "SYMPHONY":     ("SYMPHONY.NS",     "Symphony Ltd"),
    "TANLA":        ("TANLA.NS",        "Tanla Platforms Ltd"),
    "TATAINVEST":   ("TATAINVEST.NS",   "Tata Investment Corp Ltd"),
    "THERMAX":      ("THERMAX.NS",      "Thermax Ltd"),
    "THYROCARE":    ("THYROCARE.NS",    "Thyrocare Technologies Ltd"),
    "TIINDIA":      ("TIINDIA.NS",      "Tube Investments of India Ltd"),
    "TIMKEN":       ("TIMKEN.NS",       "Timken India Ltd"),
    "TRIDENT":      ("TRIDENT.NS",      "Trident Ltd"),
    "TRIVENI":      ("TRIVENI.NS",      "Triveni Engineering & Industries Ltd"),
    "TTML":         ("TTML.NS",         "Tata Teleservices (Maharashtra) Ltd"),
    "TV18BRDCST":   ("TV18BRDCST.NS",  "TV18 Broadcast Ltd"),
    "UCOBANK":      ("UCOBANK.NS",      "UCO Bank"),
    "UJJIVANSFB":   ("UJJIVANSFB.NS",  "Ujjivan Small Finance Bank Ltd"),
    "VAIBHAVGBL":   ("VAIBHAVGBL.NS",  "Vaibhav Global Ltd"),
    "VINATIORGA":   ("VINATIORGA.NS",   "Vinati Organics Ltd"),
    "VGUARD":       ("VGUARD.NS",       "V-Guard Industries Ltd"),
    "WELCORP":      ("WELCORP.NS",      "Welspun Corp Ltd"),
    "WELSPUNLIV":   ("WELSPUNLIV.NS",  "Welspun Living Ltd"),
    "YESBANK":      ("YESBANK.NS",      "Yes Bank Ltd"),
    "ZENSARTECH":   ("ZENSARTECH.NS",   "Zensar Technologies Ltd"),

    # ── INDICES (for reference/watchlist) ────────────────────
    "NIFTY50":      ("^NSEI",           "NIFTY 50 Index"),
    "SENSEX":       ("^BSESN",          "BSE SENSEX Index"),
    "BANKNIFTY":    ("^NSEBANK",        "NIFTY Bank Index"),
    "NIFTYIT":      ("^CNXIT",          "NIFTY IT Index"),

    # ── ADDITIONAL POPULAR (mostly dual-listed NSE) ──────────
    "DMART":        ("DMART.NS",        "Avenue Supermarts Ltd"),
    "NYKAA":        ("NYKAA.NS",        "FSN E-Commerce Ventures Ltd"),
    "POLICYBZR":    ("POLICYBZR.NS",    "PB Fintech Ltd"),
    "CARTRADE":     ("CARTRADE.NS",     "CarTrade Tech Ltd"),
    "EASEMYTRIP":   ("EASEMYTRIP.NS",   "Easy Trip Planners Ltd"),
    "STARHEALTH":   ("STARHEALTH.NS",   "Star Health and Allied Insurance Co Ltd"),
    "LODHA":        ("LODHA.NS",        "Macrotech Developers Ltd"),
    "JIOFIN":       ("JIOFIN.NS",       "Jio Financial Services Ltd"),
    "MANKIND":      ("MANKIND.NS",      "Mankind Pharma Ltd"),
    "SOLARINDS":    ("SOLARINDS.NS",    "Solar Industries India Ltd"),
    "CELLO":        ("CELLO.NS",        "Cello World Ltd"),
    "KAYNES":       ("KAYNES.NS",       "Kaynes Technology India Ltd"),
    "MOSCHIP":      ("MOSCHIP.NS",      "Moschip Technologies Ltd"),
    "SYRMA":        ("SYRMA.NS",        "Syrma SGS Technology Ltd"),
    "AVALON":       ("AVALON.NS",       "Avalon Technologies Ltd"),
    "CYIENTDLM":    ("CYIENTDLM.NS",    "Cyient DLM Ltd"),
    "CGPOWER":      ("CGPOWER.NS",      "CG Power and Industrial Solutions Ltd"),
    "RIR":          ("RIR.NS",          "RIR Power Electronics Ltd"),
    "PGEL":         ("PGEL.NS",         "PG Electroplast Ltd"),
    "CENTUM":       ("CENTUM.NS",       "Centum Electronics Ltd"),
    "SPELS":        ("517166.BO",       "SPEL Semiconductor Ltd"),
    "COCHINSHIP":   ("COCHINSHIP.NS",   "Cochin Shipyard Ltd"),
    "MAZAGON":      ("MAZDOCK.NS",     "Mazagon Dock Shipbuilders Ltd"),
    "IREDA":        ("IREDA.NS",        "Indian Renewable Energy Development Agency Ltd"),
    "JSWINFRA":     ("JSWINFRA.NS",     "JSW Infrastructure Ltd"),
    "TATATECH":     ("TATATECH.NS",     "Tata Technologies Ltd"),
    "RVNL":         ("RVNL.NS",         "Rail Vikas Nigam Ltd"),
    "NHPC":         ("NHPC.NS",         "NHPC Ltd"),
    "PFC":          ("PFC.NS",          "Power Finance Corporation Ltd"),
    "RECLTD":       ("RECLTD.NS",       "REC Ltd"),
    "HUDCO":        ("HUDCO.NS",        "Housing & Urban Development Corp Ltd"),
    "SJVN":         ("SJVN.NS",         "SJVN Ltd"),
    "POWERGRID":    ("POWERGRID.NS",    "Power Grid Corporation of India Ltd"),
    "IRCON":        ("IRCON.NS",        "Ircon International Ltd"),
    "RVNL":         ("RVNL.NS",         "Rail Vikas Nigam Ltd"),
    "BEL":          ("BEL.NS",          "Bharat Electronics Ltd"),
    "HAL":          ("HAL.NS",          "Hindustan Aeronautics Ltd"),
    "BDL":          ("BDL.NS",          "Bharat Dynamics Ltd"),
    "GRSE":         ("GRSE.NS",         "Garden Reach Shipbuilders & Engineers Ltd"),
    "MAZDOCK":      ("MAZDOCK.NS",      "Mazagon Dock Shipbuilders Ltd"),
    "COCHINSHIP":   ("COCHINSHIP.NS",   "Cochin Shipyard Ltd"),
    "CDSL":         ("CDSL.NS",         "Central Depository Services (India) Ltd"),
    "BSE":          ("BSE.NS",          "BSE Ltd"),
    "MCX":          ("MCX.NS",          "Multi Commodity Exchange of India Ltd"),
    "CAMS":         ("CAMS.NS",         "Computer Age Management Services Ltd"),

    # ── MISSING BANKS / FINANCE (frequently searched) ────────
    "SOUTHBANK":    ("SOUTHBANK.NS",    "South Indian Bank Ltd"),
    "KARURVYSYA":   ("KARURVYSYA.NS",   "Karur Vysya Bank Ltd"),
    "KTKBANK":      ("KTKBANK.NS",      "Karnataka Bank Ltd"),
    "TMBANK":       ("TAMILNADMER.NS",  "Tamilnad Mercantile Bank Ltd"),
    "TAMILNADMER":  ("TAMILNADMER.NS",  "Tamilnad Mercantile Bank Ltd"),
    "DCBBANK":      ("DCBBANK.NS",      "DCB Bank Ltd"),
    "CSBBANK":      ("CSBBANK.NS",      "CSB Bank Ltd"),
    "DHANLAXMI":    ("DHANLAXMI.NS",    "Dhanlaxmi Bank Ltd"),
    "J&KBANK":      ("J&KBANK.NS",      "Jammu & Kashmir Bank Ltd"),
    "JKBANK":       ("J&KBANK.NS",      "Jammu & Kashmir Bank Ltd"),
    "ESAFSFB":      ("ESAFSFB.NS",      "ESAF Small Finance Bank Ltd"),
    "SURYODAY":     ("SURYODAY.NS",     "Suryoday Small Finance Bank Ltd"),
    "UTKARSHBNK":   ("UTKARSHBNK.NS",   "Utkarsh Small Finance Bank Ltd"),
    "NSDL":         ("NSDL.NS",         "NSDL Ltd"),
    "CENTRALBK":    ("CENTRALBK.NS",    "Central Bank of India"),
    "BANKBEES":     ("BANKBEES.NS",     "Nippon India ETF Bank BeES"),
    "INDIANB":      ("INDIANB.NS",      "Indian Bank"),

    # ── MISSING POPULAR MID/SMALL CAPS ───────────────────────
    "ALOKINDS":     ("ALOKINDS.NS",     "Alok Industries Ltd"),
    "APLAPOLLO":    ("APLAPOLLO.NS",    "APL Apollo Tubes Ltd"),
    "ASTRAL":       ("ASTRAL.NS",       "Astral Ltd"),
    "BHARATFORG":   ("BHARATFORG.NS",   "Bharat Forge Ltd"),
    "BIKAJI":       ("BIKAJI.NS",       "Bikaji Foods International Ltd"),
    "BLS":          ("BLS.NS",          "BLS International Services Ltd"),
    "BSOFT":        ("BSOFT.NS",        "Birlasoft Ltd"),
    "CLEDUCATE":    ("CLEDUCATE.NS",    "CL Educate Ltd"),
    "COALINDIA":    ("COALINDIA.NS",    "Coal India Ltd"),
    "CONCORDBIO":   ("CONCORDBIO.NS",   "Concord Biotech Ltd"),
    "CROMPTON":     ("CROMPTON.NS",     "Crompton Greaves Consumer Electricals Ltd"),
    "CUMMINSIND":   ("CUMMINSIND.NS",   "Cummins India Ltd"),
    "DEEPAKFERT":   ("DEEPAKFERT.NS",   "Deepak Fertilisers & Petrochemicals Corp Ltd"),
    "DEEPAKNTR":    ("DEEPAKNTR.NS",    "Deepak Nitrite Ltd"),
    "DEVYANI":      ("DEVYANI.NS",      "Devyani International Ltd"),
    "DELHIVERY":    ("DELHIVERY.NS",    "Delhivery Ltd"),
    "ELECON":       ("ELECON.NS",       "Elecon Engineering Company Ltd"),
    "ELGIEQUIP":    ("ELGIEQUIP.NS",    "Elgi Equipments Ltd"),
    "EXIDEIND":     ("EXIDEIND.NS",     "Exide Industries Ltd"),
    "FACT":         ("FACT.NS",         "Fertilisers and Chemicals Travancore Ltd"),
    "FIVESTAR":     ("FIVESTAR.NS",     "Five-Star Business Finance Ltd"),
    "GANESHHOUC":   ("GANESHHOUC.NS",   "Ganesh Housing Corporation Ltd"),
    "GESHIP":       ("GESHIP.NS",       "Great Eastern Shipping Company Ltd"),
    "GNFC":         ("GNFC.NS",         "Gujarat Narmada Valley Fertilizers & Chemicals Ltd"),
    "GODFRYPHLP":   ("GODFRYPHLP.NS",   "Godfrey Phillips India Ltd"),
    "GPPL":         ("GPPL.NS",         "Gujarat Pipavav Port Ltd"),
    "GRAPHITE":     ("GRAPHITE.NS",     "Graphite India Ltd"),
    "GUJGASLTD":    ("GUJGASLTD.NS",   "Gujarat Gas Ltd"),
    "HEID":         ("HEID.NS",         "Heidelberg Cement India Ltd"),
    "HLEGLAS":      ("HLEGLAS.NS",      "HLE Glascoat Ltd"),
    "HONAUT":       ("HONAUT.NS",        "Honeywell Automation India Ltd"),
    "IBREALEST":    ("IBREALEST.NS",    "Indiabulls Real Estate Ltd"),
    "IEX":          ("IEX.NS",          "Indian Energy Exchange Ltd"),
    "INDHOTEL":     ("INDHOTEL.NS",     "Indian Hotels Company Ltd"),
    "INTELLECT":    ("INTELLECT.NS",    "Intellect Design Arena Ltd"),
    "IRCTC":        ("IRCTC.NS",        "Indian Railway Catering & Tourism Corp Ltd"),
    "IRFC":         ("IRFC.NS",         "Indian Railway Finance Corporation Ltd"),
    "JSWENERGY":    ("JSWENERGY.NS",    "JSW Energy Ltd"),
    "JTEKTINDIA":   ("JTEKTINDIA.NS",   "JTEKT India Ltd"),
    "JUBLFOOD":     ("JUBLFOOD.NS",     "Jubilant FoodWorks Ltd"),
    "KALYANKJIL":   ("KALYANKJIL.NS",   "Kalyan Jewellers India Ltd"),
    "KEI":          ("KEI.NS",          "KEI Industries Ltd"),
    "KSB":          ("KSB.NS",          "KSB Ltd"),
    "LATENTVIEW":   ("LATENTVIEW.NS",   "Latent View Analytics Ltd"),
    "LXCHEM":       ("LXCHEM.NS",       "Laxmi Organic Industries Ltd"),
    "MAPMYINDIA":   ("MAPMYINDIA.NS",   "CE Info Systems Ltd"),
    "MAXHEALTH":    ("MAXHEALTH.NS",    "Max Healthcare Institute Ltd"),
    "MRPL":         ("MRPL.NS",         "Mangalore Refinery & Petrochemicals Ltd"),
    "MUTHOOTFIN":   ("MUTHOOTFIN.NS",   "Muthoot Finance Ltd"),
    "NATIONALUM":   ("NATIONALUM.NS",   "National Aluminium Company Ltd"),
    "NBCC":         ("NBCC.NS",         "NBCC (India) Ltd"),
    "NCC":          ("NCC.NS",          "NCC Ltd"),
    "NEWGEN":       ("NEWGEN.NS",       "Newgen Software Technologies Ltd"),
    "NUVAMA":       ("NUVAMA.NS",       "Nuvama Wealth Management Ltd"),
    "PGHH":         ("PGHH.NS",         "Procter & Gamble Hygiene & Health Care Ltd"),
    "PRSMJOHNSN":   ("PRSMJOHNSN.NS",   "Prism Johnson Ltd"),
    "QUESS":        ("QUESS.NS",        "Quess Corp Ltd"),
    "REDINGTON":    ("REDINGTON.NS",    "Redington Ltd"),
    "RENUKA":       ("RENUKA.NS",       "Shree Renuka Sugars Ltd"),
    "RCF":          ("RCF.NS",          "Rashtriya Chemicals & Fertilizers Ltd"),
    "RVNL":         ("RVNL.NS",         "Rail Vikas Nigam Ltd"),
    "SAPPHIRE":     ("SAPPHIRE.NS",     "Sapphire Foods India Ltd"),
    "SCHAEFFLER":   ("SCHAEFFLER.NS",   "Schaeffler India Ltd"),
    "SHOPERSTOP":   ("SHOPERSTOP.NS",   "Shoppers Stop Ltd"),
    "SOLARINDS":    ("SOLARINDS.NS",    "Solar Industries India Ltd"),
    "SRF":          ("SRF.NS",          "SRF Ltd"),
    "SUMICHEM":     ("SUMICHEM.NS",     "Sumitomo Chemical India Ltd"),
    "TATVA":        ("TATVA.NS",        "Tatva Chintan Pharma Chem Ltd"),
    "TTKPRESTIG":   ("TTKPRESTIG.NS",   "TTK Prestige Ltd"),
    "VARUNBEV":     ("VBL.NS",          "Varun Beverages Ltd"),
    "VEDL":         ("VEDL.NS",         "Vedanta Ltd"),
    "ZFCVINDIA":    ("ZFCVINDIA.NS",    "ZF Commercial Vehicle Control Systems India Ltd"),

    # ── NEWLY LISTED / IPO POPULAR ───────────────────────────
    "SWIGGY":       ("SWIGGY.NS",       "Swiggy Ltd"),
    "OLA":          ("OLAELEC.NS",      "Ola Electric Mobility Ltd"),
    "FIRSTCRY":     ("FIRSTCRY.NS",     "Brainbees Solutions Ltd"),
    "BAJAJHOUS":    ("BAJAJHFL.NS",     "Bajaj Housing Finance Ltd"),
}

# Build legacy NSE_SYMBOLS dict (symbol → Yahoo ticker) for backward compat
NSE_SYMBOLS: Dict[str, str] = {k: v[0] for k, v in INDIAN_STOCKS.items()}

# Retired / renamed tickers → current listed NSE symbols.
# Keep aliases for user queries; do not treat keys as live listings.
LEGACY_SYMBOL_ALIASES: Dict[str, str] = {
    "TATAMOTORS": "TMPV",       # demerger → passenger vehicles
    "ADANITRANS": "ADANIENSOL",  # Adani Energy Solutions
    "GMRINFRA": "GMRAIRPORT",
    "MINDAIND": "UNOMINDA",
    "MOTHERSUMI": "MOTHERSON",
    "L&TFH": "LTF",
    "LTFH": "LTF",
    "IIFLWAM": "360ONE",
    "IBULHSGFIN": "SAMMAANCAP",
    "ADANIWILMAR": "AWL",
    "HPCL": "HINDPETRO",
    "ZOMATO": "ETERNAL",
    "CANARABANK": "CANBK",
    "DALMIACEM": "DALBHARAT",
    "AARTI": "AARTIIND",
    "ELIH": "EIH",
    "LEMONTR": "LEMONTREE",
}

# Build name lookup (lowercase company name → symbol) for search
_NAME_INDEX: Dict[str, str] = {}

# Enhanced indices for better company name matching
_COMPANY_NAME_EXACT: Dict[str, str] = {}  # Full company name → symbol
_COMPANY_NAME_PARTIAL: Dict[str, list] = {}  # Word → list of symbols
_SYMBOL_BY_WORD: Dict[str, list] = {}  # Multi-word normalized → symbol

for _sym, (_ticker, _name) in INDIAN_STOCKS.items():
    _name_lower = _name.lower()
    _NAME_INDEX[_name_lower] = _sym
    _COMPANY_NAME_EXACT[_name_lower] = _sym

    # Strip " Ltd" suffix and add variant
    _name_no_ltd = _name_lower.replace(" ltd", "").replace(" limited", "").strip()
    if _name_no_ltd and _name_no_ltd != _name_lower:
        _COMPANY_NAME_EXACT[_name_no_ltd] = _sym

    # Add multi-word normalized key (handles "South Indian Bank" → "SOUTHBANK")
    _words = _name_no_ltd.split()
    if len(_words) >= 2:
        _multi_key = " ".join(_words[:-1]).strip()  # Drop last word typically (Ltd, Inc, etc)
        if _multi_key and len(_multi_key) >= 3:
            _SYMBOL_BY_WORD[_multi_key] = _sym

    # Index individual words for partial matching
    for _word in _name_lower.split():
        _word = _word.strip()
        if len(_word) >= 3:  # Only meaningful words
            if _word not in _COMPANY_NAME_PARTIAL:
                _COMPANY_NAME_PARTIAL[_word] = []
            if _sym not in _COMPANY_NAME_PARTIAL[_word]:
                _COMPANY_NAME_PARTIAL[_word].append(_sym)

# Default symbols shown on app home / dashboard
DEFAULT_SYMBOLS = [
    "RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN",
    "WIPRO", "ICICIBANK", "KOTAKBANK", "HINDUNILVR", "ITC",
    "BHARTIARTL", "LT", "AXISBANK", "BAJFINANCE", "TMPV",
    "SUNPHARMA", "TITAN", "MARUTI", "HCLTECH", "TATASTEEL",
]


def quote_max_age_seconds() -> float:
    """How old a cached last-price may be before we hit Yahoo again.

    Market open: ~5s so Home / stream / heatmap track the tape.
    After hours: ~3m — the close print does not move.
    """
    try:
        from .market_session import is_within_equity_session

        if is_within_equity_session():
            return float(QUOTE_CACHE_TTL_OPEN)
    except Exception:
        pass
    return float(QUOTE_CACHE_TTL_CLOSED)


class QuoteCache:
    """In-memory cache for stock quotes with TTL."""

    def __init__(
        self,
        ttl_seconds: int = 60,
        max_entries: int = 350,
        storage_seconds: Optional[int] = None,
    ):
        self._cache: Dict[str, dict] = {}
        self._timestamps: Dict[str, float] = {}
        self._ttl = max(1, int(ttl_seconds))
        # Keep stale prints around for paint-while-refresh unless a test sets a short TTL.
        self._storage_ttl = max(self._ttl, int(storage_seconds)) if storage_seconds else self._ttl
        self._max_entries = max(1, int(max_entries))
        self._lock = Lock()

    def _evict_expired_locked(self, now: float) -> None:
        expired = [
            symbol
            for symbol, timestamp in self._timestamps.items()
            if (now - timestamp) >= self._storage_ttl
        ]
        for symbol in expired:
            self._cache.pop(symbol, None)
            self._timestamps.pop(symbol, None)

    def _evict_oversized_locked(self) -> None:
        overflow = len(self._cache) - self._max_entries
        if overflow <= 0:
            return

        oldest_symbols = sorted(
            self._timestamps.items(),
            key=lambda item: item[1],
        )[:overflow]
        for symbol, _ in oldest_symbols:
            self._cache.pop(symbol, None)
            self._timestamps.pop(symbol, None)

    def get(self, symbol: str, max_age_seconds: Optional[float] = None) -> Optional[dict]:
        now = time.time()
        with self._lock:
            self._evict_expired_locked(now)
            timestamp = self._timestamps.get(symbol)
            if timestamp is None:
                return None
            age_limit = float(self._ttl if max_age_seconds is None else max_age_seconds)
            if age_limit >= 0 and (now - timestamp) >= age_limit:
                return None
            return self._cache.get(symbol)

    def get_allow_stale(self, symbol: str, max_age_seconds: float) -> Optional[dict]:
        """Return a cached quote even if past the default TTL, up to max_age_seconds."""
        now = time.time()
        with self._lock:
            timestamp = self._timestamps.get(symbol)
            payload = self._cache.get(symbol)
            if timestamp is None or payload is None:
                return None
            if (now - timestamp) >= float(max_age_seconds):
                return None
            return payload

    def put(self, symbol: str, data: dict):
        now = time.time()
        with self._lock:
            self._evict_expired_locked(now)
            self._cache[symbol] = data
            self._timestamps[symbol] = now
            self._evict_oversized_locked()

    def patch(self, symbol: str, updates: dict) -> Optional[dict]:
        """Merge snapshot fields into a cached quote without resetting last-price freshness."""
        if not updates:
            return self._cache.get(symbol)
        with self._lock:
            payload = self._cache.get(symbol)
            if payload is None:
                return None
            merged = dict(payload)
            for key, incoming in updates.items():
                if incoming in (None, 0, 0.0):
                    continue
                existing = merged.get(key)
                if existing in (None, 0, 0.0):
                    merged[key] = incoming
            self._cache[symbol] = merged
            return merged

    def clear(self):
        with self._lock:
            self._cache.clear()
            self._timestamps.clear()

    def size(self) -> int:
        with self._lock:
            return len(self._cache)


# Keep last prints up to STORAGE so heatmap/stream can show stale-while-revalidate.
_quote_cache = QuoteCache(
    ttl_seconds=QUOTE_CACHE_TTL_SECONDS,
    max_entries=QUOTE_CACHE_MAX_ENTRIES,
    storage_seconds=QUOTE_CACHE_STORAGE_SECONDS,
)


def normalize_listed_symbol(symbol: str) -> str:
    """Map retired tickers to the current listed NSE symbol (e.g. TATAMOTORS→TMPV)."""
    raw = (symbol or "").strip().upper()
    if raw.endswith(".NS") or raw.endswith(".BO"):
        raw = raw.rsplit(".", 1)[0]
    if raw.startswith("NSE:") or raw.startswith("BSE:"):
        raw = raw.split(":", 1)[1].strip()
    return LEGACY_SYMBOL_ALIASES.get(raw, raw)


def _yf_ticker(symbol: str) -> str:
    """Convert symbol input into a Yahoo Finance ticker with NSE/BSE support."""
    raw_symbol = (symbol or "").strip().upper()
    if not raw_symbol:
        return ""

    force_bse = False
    if raw_symbol.startswith("NSE:"):
        raw_symbol = raw_symbol.split(":", 1)[1].strip()
    elif raw_symbol.startswith("BSE:"):
        raw_symbol = raw_symbol.split(":", 1)[1].strip()
        force_bse = True

    if raw_symbol.endswith(".NS") or raw_symbol.endswith(".BO"):
        base = raw_symbol.rsplit(".", 1)[0]
        mapped = LEGACY_SYMBOL_ALIASES.get(base)
        if mapped:
            suffix = raw_symbol.rsplit(".", 1)[1]
            return f"{mapped}.{suffix}"
        return raw_symbol

    raw_symbol = LEGACY_SYMBOL_ALIASES.get(raw_symbol, raw_symbol)

    # Explicit BSE prefix / numeric scrip code → .BO (resolve alpha via BSE master).
    if force_bse or (len(raw_symbol) == 6 and raw_symbol.isdigit()):
        try:
            from .stock_enricher import lookup_bse_listing

            rec = lookup_bse_listing(raw_symbol)
            if rec and rec.get("code"):
                return f"{rec['code']}.BO"
        except Exception:
            pass
        if len(raw_symbol) == 6 and raw_symbol.isdigit():
            return f"{raw_symbol}.BO"
        return f"{raw_symbol}.BO"

    if raw_symbol in NSE_SYMBOLS:
        return NSE_SYMBOLS[raw_symbol]

    # Live catalog may already know the Yahoo ticker (NSE or BSE-only).
    try:
        catalog = get_stock_catalog()
        if raw_symbol in catalog:
            yahoo = catalog[raw_symbol][0]
            if yahoo:
                return str(yahoo)
    except Exception:
        pass

    # BSE-only active listings (not on NSE equity master) → numeric .BO.
    try:
        from .stock_enricher import is_bse_only_symbol, lookup_bse_listing

        if is_bse_only_symbol(raw_symbol):
            rec = lookup_bse_listing(raw_symbol)
            if rec and rec.get("code"):
                return f"{rec['code']}.BO"
    except Exception:
        pass

    return f"{raw_symbol}.NS"


def _safe_number(value: object, default: float = 0.0) -> float:
    try:
        number = float(value)
    except Exception:
        return default
    if number != number:  # NaN
        return default
    return number


def _parse_yahoo_v8_chart(data: Optional[dict]) -> List[dict]:
    """Map Yahoo v8 chart JSON onto BYSEL candle dicts."""
    results = ((data or {}).get("chart") or {}).get("result") or []
    if not results or not isinstance(results[0], dict):
        return []
    node = results[0]
    timestamps = node.get("timestamp") or []
    quotes = ((node.get("indicators") or {}).get("quote") or [{}])
    if not quotes or not isinstance(quotes[0], dict):
        return []
    quote = quotes[0]
    opens = quote.get("open") or []
    highs = quote.get("high") or []
    lows = quote.get("low") or []
    closes = quote.get("close") or []
    volumes = quote.get("volume") or []

    candles: List[dict] = []
    seen_ts: set[int] = set()
    for index, raw_ts in enumerate(timestamps):
        try:
            timestamp_ms = int(raw_ts) * 1000
            close_p = float(closes[index])
        except (TypeError, ValueError, IndexError):
            continue
        if close_p <= 0 or timestamp_ms in seen_ts:
            continue

        def _bar(values: list, fallback: float) -> float:
            try:
                number = float(values[index])
            except (TypeError, ValueError, IndexError):
                return fallback
            return number if number == number and number > 0 else fallback

        open_p = _bar(opens, close_p)
        high_p = _bar(highs, max(open_p, close_p))
        low_p = _bar(lows, min(open_p, close_p))
        if high_p < low_p:
            continue
        try:
            volume = int(float(volumes[index] or 0))
        except (TypeError, ValueError, IndexError):
            volume = 0

        seen_ts.add(timestamp_ms)
        candles.append(
            {
                "timestamp": timestamp_ms,
                "open": round(open_p, 4),
                "high": round(high_p, 4),
                "low": round(low_p, 4),
                "close": round(close_p, 4),
                "volume": volume,
            }
        )
    candles.sort(key=lambda candle: candle["timestamp"])
    return candles


def _fetch_yahoo_v8_chart(yahoo_symbol: str, period: str, interval: str, timeout: float = 6.0) -> List[dict]:
    """Daily/intraday OHLCV via Yahoo v8 chart REST (crumb-authed, then bare GET)."""
    token = (yahoo_symbol or "").strip()
    if not token:
        return []
    encoded = urllib.parse.quote(token, safe=".")
    query = urllib.parse.urlencode({"range": period, "interval": interval, "events": "div,split"})
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/120.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json",
    }
    for host in ("query1", "query2"):
        url = f"https://{host}.finance.yahoo.com/v8/finance/chart/{encoded}?{query}"
        candles = _parse_yahoo_v8_chart(_yahoo_authed_json(url, timeout=timeout))
        if candles:
            return candles
        try:
            req = urllib.request.Request(url, headers=headers, method="GET")
            with urllib.request.urlopen(req, timeout=max(1.5, float(timeout))) as resp:
                data = _json.loads(resp.read().decode("utf-8"))
            candles = _parse_yahoo_v8_chart(data)
            if candles:
                return candles
        except Exception as exc:
            logger.debug("yahoo v8 chart failed via %s %s/%s: %s", host, period, interval, exc)
    return []


def fetch_quote_history(symbol: str, period: str = "1mo", interval: str = "1d") -> List[dict]:
    """Fetch OHLCV candles from Yahoo Finance for a symbol and timeframe."""
    normalized_symbol = (symbol or "").strip().upper()
    normalized_period = (period or "1mo").strip().lower()
    normalized_interval = (interval or "1d").strip().lower()

    if not normalized_symbol:
        raise ValueError("Symbol is required")
    if normalized_period not in HISTORY_ALLOWED_PERIODS:
        raise ValueError(f"Unsupported history period: {period}")
    if normalized_interval not in HISTORY_ALLOWED_INTERVALS:
        raise ValueError(f"Unsupported history interval: {interval}")

    candidates: List[str] = []
    primary = _yf_ticker(normalized_symbol)
    if primary:
        candidates.append(primary)
    # Dual-listed BSE codes: also try NSE twin (Yahoo .BO history can be flaky).
    try:
        from .stock_enricher import get_nse_equity_map, lookup_bse_listing

        rec = lookup_bse_listing(normalized_symbol)
        sid = str((rec or {}).get("scrip_id") or "").upper()
        if sid and sid in get_nse_equity_map():
            nse_yahoo = _yf_ticker(sid)
            if nse_yahoo and nse_yahoo not in candidates:
                # Prefer NSE history first for dual-listed names.
                candidates.insert(0, nse_yahoo)
    except Exception:
        pass
    if normalized_symbol not in candidates:
        # Last-resort suffixes.
        if len(normalized_symbol) == 6 and normalized_symbol.isdigit():
            candidates.append(f"{normalized_symbol}.BO")
        else:
            candidates.extend([f"{normalized_symbol}.NS", f"{normalized_symbol}.BO"])

    combo_attempts = HISTORY_INTERVAL_FALLBACKS.get(
        (normalized_period, normalized_interval),
        [(normalized_period, normalized_interval)],
    )

    # Prefer Yahoo v8 chart REST (same crumb path as live v7 quotes). yfinance
    # ticker.history() often returns empty from Cloud Run / europe-west1.
    for try_period, try_interval in combo_attempts:
        for yahoo in list(dict.fromkeys(candidates)):
            v8_candles = _fetch_yahoo_v8_chart(yahoo, try_period, try_interval)
            if v8_candles:
                if (try_period, try_interval) != (normalized_period, normalized_interval):
                    logger.info(
                        "history v8 fallback %s: requested %s/%s → served %s/%s (%d bars)",
                        normalized_symbol,
                        normalized_period,
                        normalized_interval,
                        try_period,
                        try_interval,
                        len(v8_candles),
                    )
                return v8_candles

    hist = None
    used_period = normalized_period
    used_interval = normalized_interval
    for try_period, try_interval in combo_attempts:
        for yahoo in list(dict.fromkeys(candidates)):
            try:
                hist = yf.Ticker(yahoo).history(
                    period=try_period,
                    interval=try_interval,
                    auto_adjust=False,
                )
                if hist is not None and not hist.empty:
                    used_period = try_period
                    used_interval = try_interval
                    break
            except Exception as exc:
                logger.debug("history failed for %s %s/%s: %s", yahoo, try_period, try_interval, exc)
                hist = None
        if hist is not None and not hist.empty:
            break

    if hist is None or hist.empty:
        return []

    if (used_period, used_interval) != (normalized_period, normalized_interval):
        logger.info(
            "history fallback %s: requested %s/%s → served %s/%s (%d bars)",
            normalized_symbol,
            normalized_period,
            normalized_interval,
            used_period,
            used_interval,
            len(hist),
        )

    candles: List[dict] = []
    seen_ts: set[int] = set()
    for index, row in hist.iterrows():
        try:
            timestamp_ms = int(index.timestamp() * 1000)
        except Exception:
            timestamp_ms = int(datetime.utcnow().timestamp() * 1000)

        open_p = round(_safe_number(row.get("Open")), 4)
        high_p = round(_safe_number(row.get("High")), 4)
        low_p = round(_safe_number(row.get("Low")), 4)
        close_p = round(_safe_number(row.get("Close")), 4)
        if open_p <= 0 or close_p <= 0 or high_p < low_p:
            continue
        if timestamp_ms in seen_ts:
            continue
        seen_ts.add(timestamp_ms)

        candles.append(
            {
                "timestamp": timestamp_ms,
                "open": open_p,
                "high": high_p,
                "low": low_p,
                "close": close_p,
                "volume": int(_safe_number(row.get("Volume"), default=0.0)),
            }
        )

    candles.sort(key=lambda c: c["timestamp"])
    return candles


def _normalize_dividend_yield_pct(
    raw_yield: object,
    dividend_rate: object,
    last_price: float,
) -> float | None:
    """Return dividend yield in percent for UI (e.g. 0.45 → 0.45%)."""
    rate = _safe_number(dividend_rate, 0.0)
    if rate > 0 and last_price > 0:
        return round((rate / last_price) * 100.0, 2)

    raw = _safe_number(raw_yield, 0.0)
    if raw <= 0:
        return None
    # Fractions are typically ≤ ~0.20 (20%); Yahoo India often already sends percent.
    if raw <= 0.20:
        return round(raw * 100.0, 2)
    if raw <= 20:
        return round(raw, 2)
    return None


def _yahoo_raw_number(value: object) -> Optional[float]:
    """Unwrap Yahoo `{raw: x}` wrappers or parse a scalar."""
    if value is None:
        return None
    if isinstance(value, dict):
        if "raw" in value:
            return _yahoo_raw_number(value.get("raw"))
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    if number != number:  # NaN
        return None
    return number


def _first_yahoo_number(raw: dict, *keys: str) -> Optional[float]:
    for key in keys:
        number = _yahoo_raw_number(raw.get(key))
        if number is not None:
            return number
    return None


def _flatten_yahoo_quote(raw: dict) -> dict:
    """Merge quoteSummary modules / v7 quote / ticker.info into one keyspace."""
    if not isinstance(raw, dict):
        return {}
    flat: dict = {}
    # quoteSummary envelope
    summary = raw.get("quoteSummary")
    if isinstance(summary, dict):
        results = summary.get("result") or []
        if results and isinstance(results[0], dict):
            raw = results[0]
    for module in ("summaryDetail", "defaultKeyStatistics", "financialData", "price"):
        nested = raw.get(module)
        if isinstance(nested, dict):
            for key, value in nested.items():
                flat.setdefault(key, value)
    for key, value in raw.items():
        if key in {"summaryDetail", "defaultKeyStatistics", "financialData", "price", "quoteSummary"}:
            continue
        flat.setdefault(key, value)
    return flat


_FUNDAMENTAL_QUOTE_KEYS = (
    "pe",
    "trailingPE",
    "eps",
    "dividendYield",
    "bid",
    "ask",
    "avgVolume",
    "marketCap",
    "targetMeanPrice",
    "fiftyTwoWeekHigh",
    "fiftyTwoWeekLow",
    "fiftyDayAverage",
    "twoHundredDayAverage",
)
# avgVolume / 52w can arrive from fast_info; valuation still needs a Yahoo crumb call.
_VALUATION_QUOTE_KEYS = (
    "trailingPE",
    "pe",
    "eps",
    "dividendYield",
    "targetMeanPrice",
)


def fundamentals_from_yahoo_quote(raw: dict, last_price: float = 0.0) -> dict:
    """Map a Yahoo v7 quote, quoteSummary, or ticker.info payload to API snapshot fields."""
    flat = _flatten_yahoo_quote(raw)
    if not flat:
        return {}

    last = last_price or _first_yahoo_number(flat, "regularMarketPrice", "currentPrice", "last") or 0.0
    pe = _first_yahoo_number(flat, "trailingPE", "pe")
    if pe is None or pe <= 0:
        pe = _first_yahoo_number(flat, "forwardPE")
    eps = _first_yahoo_number(
        flat,
        "epsTrailingTwelveMonths",
        "trailingEps",
        "epsCurrentYear",
        "eps",
    )
    bid = _first_yahoo_number(flat, "bid")
    ask = _first_yahoo_number(flat, "ask")
    avg_volume = _first_yahoo_number(
        flat,
        "averageDailyVolume3Month",
        "averageVolume",
        "averageDailyVolume10Day",
        "averageVolume10days",
        "avgVolume",
    )
    volume = _first_yahoo_number(flat, "regularMarketVolume", "volume")
    market_cap = _first_yahoo_number(flat, "marketCap")
    target = _first_yahoo_number(flat, "targetMeanPrice")
    week52_high = _first_yahoo_number(flat, "fiftyTwoWeekHigh")
    week52_low = _first_yahoo_number(flat, "fiftyTwoWeekLow")
    fifty_day = _first_yahoo_number(flat, "fiftyDayAverage")
    two_hundred = _first_yahoo_number(flat, "twoHundredDayAverage")
    dividend = _normalize_dividend_yield_pct(
        raw_yield=_first_yahoo_number(flat, "dividendYield", "trailingAnnualDividendYield"),
        dividend_rate=_first_yahoo_number(flat, "dividendRate", "trailingAnnualDividendRate"),
        last_price=last,
    )

    out: dict = {}
    if pe is not None and pe > 0:
        pe_out = round(float(pe), 2)
        out["pe"] = pe_out
        out["trailingPE"] = pe_out
    if eps is not None:
        out["eps"] = round(float(eps), 2)
    if dividend is not None:
        out["dividendYield"] = dividend
    if bid is not None and bid > 0:
        out["bid"] = round(float(bid), 2)
    if ask is not None and ask > 0:
        out["ask"] = round(float(ask), 2)
    if avg_volume is not None and avg_volume > 0:
        out["avgVolume"] = int(avg_volume)
    if volume is not None and volume > 0:
        out["volume"] = int(volume)
    if market_cap is not None and market_cap > 0:
        out["marketCap"] = int(market_cap)
    if target is not None and target > 0:
        out["targetMeanPrice"] = round(float(target), 2)
    if week52_high is not None and week52_high > 0:
        out["fiftyTwoWeekHigh"] = round(float(week52_high), 2)
    if week52_low is not None and week52_low > 0:
        out["fiftyTwoWeekLow"] = round(float(week52_low), 2)
    if fifty_day is not None and fifty_day > 0:
        out["fiftyDayAverage"] = round(float(fifty_day), 2)
    if two_hundred is not None and two_hundred > 0:
        out["twoHundredDayAverage"] = round(float(two_hundred), 2)
    return out


def _overlay_fundamentals(quote: dict, fund: Optional[dict]) -> dict:
    if not quote or not fund:
        return quote
    out = dict(quote)
    for key in _FUNDAMENTAL_QUOTE_KEYS:
        incoming = fund.get(key)
        if incoming in (None, 0, 0.0):
            continue
        if out.get(key) in (None, 0, 0.0):
            out[key] = incoming
    return out


def _merge_fundamentals(*parts: Optional[dict]) -> dict:
    """Combine Yahoo v7 / quoteSummary / fast_info / cache without wiping filled keys."""
    out: dict = {}
    for part in parts:
        if not part:
            continue
        cleaned = {key: value for key, value in part.items() if value not in (None, 0, 0.0)}
        if not cleaned:
            continue
        out = _overlay_fundamentals(out, cleaned) if out else dict(cleaned)
    return out


def _needs_fundamentals(quote: Optional[dict]) -> bool:
    if not quote:
        return True
    return not any(
        quote.get(key) not in (None, 0, 0.0)
        for key in _VALUATION_QUOTE_KEYS
    )


def fundamentals_from_fast_info(info: object, last_price: float = 0.0) -> dict:
    """Map yfinance fast_info (volume / 52w / mcap) — no PE/EPS/yield on this object."""
    if info is None:
        return {}

    def _attr(name: str):
        try:
            if hasattr(info, "get"):
                return info.get(name)
            return getattr(info, name, None)
        except Exception:
            return None

    raw = {
        "regularMarketPrice": last_price or _attr("last_price"),
        "marketCap": _attr("market_cap"),
        "averageDailyVolume3Month": _attr("three_month_average_volume"),
        "regularMarketVolume": _attr("last_volume"),
        "fiftyTwoWeekHigh": _attr("year_high"),
        "fiftyTwoWeekLow": _attr("year_low"),
        "fiftyDayAverage": _attr("fifty_day_average"),
        "twoHundredDayAverage": _attr("two_hundred_day_average"),
    }
    return fundamentals_from_yahoo_quote(raw, last_price=last_price)


_fundamentals_cache: Dict[str, tuple] = {}
_fundamentals_lock = Lock()
_fundamentals_inflight: set = set()


def _get_cached_fundamentals(symbol: str) -> Optional[dict]:
    key = (symbol or "").strip().upper()
    if not key:
        return None
    now = time.time()
    with _fundamentals_lock:
        cached = _fundamentals_cache.get(key)
        if not cached:
            return None
        stamp, payload = cached
        if (now - stamp) >= float(FUNDAMENTALS_CACHE_TTL_SECONDS):
            return None
        return dict(payload) if payload else None


def _put_fundamentals(symbol: str, fund: dict) -> None:
    key = (symbol or "").strip().upper()
    if not key or not fund:
        return
    with _fundamentals_lock:
        _fundamentals_cache[key] = (time.time(), dict(fund))


def clear_fundamentals_cache() -> None:
    with _fundamentals_lock:
        _fundamentals_cache.clear()
        _fundamentals_inflight.clear()


_yf_data = None
_yf_data_lock = Lock()


def _yahoo_data():
    """Reuse one crumb+cookie client. A new YfData() on every call re-fetches the crumb."""
    global _yf_data
    with _yf_data_lock:
        if _yf_data is None:
            from yfinance.data import YfData

            _yf_data = YfData(session=None)
        return _yf_data


def _yahoo_authed_json(url: str, timeout: float = 6.0) -> dict:
    """Yahoo quote/quoteSummary JSON via yfinance crumb+cookie (bare urllib gets 401)."""
    try:
        payload = _yahoo_data().get_raw_json(url, timeout=max(1.5, float(timeout)))
        return payload if isinstance(payload, dict) else {}
    except Exception as exc:
        logger.debug("yahoo authed json failed: %s", exc)
        return {}


def _parse_yahoo_v7_rows(data: Optional[dict]) -> Dict[str, dict]:
    rows = ((data or {}).get("quoteResponse") or {}).get("result") or []
    out: Dict[str, dict] = {}
    for row in rows:
        if not isinstance(row, dict):
            continue
        yf_sym = str(row.get("symbol") or "").strip()
        if yf_sym:
            out[yf_sym] = row
    return out


def _fetch_yahoo_v7_quotes_once(yf_symbols: List[str], timeout: float = 4.0) -> Dict[str, dict]:
    """One Yahoo v7 HTTP call for a small symbol list."""
    encoded = urllib.parse.quote(",".join(yf_symbols), safe=",.")
    for host in ("query1", "query2"):
        url = f"https://{host}.finance.yahoo.com/v7/finance/quote?symbols={encoded}"
        out = _parse_yahoo_v7_rows(_yahoo_authed_json(url, timeout=timeout))
        if out:
            return out

    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/120.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json",
    }
    for host in ("query1", "query2"):
        url = f"https://{host}.finance.yahoo.com/v7/finance/quote?symbols={encoded}"
        try:
            req = urllib.request.Request(url, headers=headers, method="GET")
            with urllib.request.urlopen(req, timeout=max(1.5, float(timeout))) as resp:
                data = _json.loads(resp.read().decode("utf-8"))
            out = _parse_yahoo_v7_rows(data)
            if out:
                return out
        except Exception as exc:
            logger.debug("yahoo v7 quote failed via %s: %s", host, exc)
    return {}


def _fetch_yahoo_v7_quotes(yf_symbols: List[str], timeout: float = 4.0) -> Dict[str, dict]:
    """Yahoo v7 quote endpoint — last price plus PE/EPS/yield/bid/ask/avg volume."""
    uniq: List[str] = []
    seen: set = set()
    for symbol in yf_symbols:
        token = (symbol or "").strip()
        if token and token not in seen:
            seen.add(token)
            uniq.append(token)
    if not uniq:
        return {}

    chunk_size = max(1, min(int(QUOTE_BATCH_SIZE), 40))
    if len(uniq) <= chunk_size:
        return _fetch_yahoo_v7_quotes_once(uniq, timeout=timeout)

    merged: Dict[str, dict] = {}
    for start in range(0, len(uniq), chunk_size):
        merged.update(_fetch_yahoo_v7_quotes_once(uniq[start:start + chunk_size], timeout=timeout))
    return merged


def _quote_from_yahoo_v7(symbol: str, v7: dict, stale: Optional[dict] = None) -> Optional[dict]:
    """Map a Yahoo v7 row onto BYSEL's last-price quote shape. None if no usable last."""
    if not isinstance(v7, dict) or not v7:
        return None
    last_price = _safe_number(
        v7.get("regularMarketPrice") or v7.get("regularMarketPreviousClose"),
        0.0,
    )
    if last_price <= 0:
        return None
    prev_close = _safe_number(v7.get("regularMarketPreviousClose"), last_price)
    pct_raw = _first_yahoo_number(v7, "regularMarketChangePercent")
    if pct_raw is None:
        pct_change = round(((last_price - prev_close) / prev_close) * 100, 2) if prev_close > 0 else 0.0
    else:
        pct_change = round(float(pct_raw), 2)
    fund = fundamentals_from_yahoo_quote(v7, last_price=last_price)
    if fund:
        _put_fundamentals(symbol, fund)
    quote = dict(stale) if stale else {}
    quote.update(
        {
            "symbol": symbol,
            "last": round(last_price, 2),
            "pctChange": pct_change,
            "open": round(_safe_number(v7.get("regularMarketOpen"), last_price), 2),
            "high": round(_safe_number(v7.get("regularMarketDayHigh"), last_price), 2),
            "low": round(_safe_number(v7.get("regularMarketDayLow"), last_price), 2),
            "previousClose": round(prev_close, 2),
            "prevClose": round(prev_close, 2),
            "volume": int(_safe_number(v7.get("regularMarketVolume"), 0.0)),
            "timestamp": int(datetime.utcnow().timestamp() * 1000),
        }
    )
    return _overlay_fundamentals(quote, fund or _get_cached_fundamentals(symbol))


def _fetch_yahoo_quote_summary(yf_symbol: str, timeout: float = 4.0) -> dict:
    """quoteSummary for analyst target (and PE/EPS/yield if v7 omitted them)."""
    token = (yf_symbol or "").strip()
    if not token:
        return {}
    encoded = urllib.parse.quote(token, safe=".")
    for host in ("query1", "query2"):
        url = (
            f"https://{host}.finance.yahoo.com/v10/finance/quoteSummary/{encoded}"
            "?modules=summaryDetail,defaultKeyStatistics,financialData"
        )
        data = _yahoo_authed_json(url, timeout=timeout)
        if data.get("quoteSummary"):
            mapped = fundamentals_from_yahoo_quote(data)
            if mapped:
                return mapped
    return {}


def _fundamentals_from_yfinance_info(symbol: str, last_price: float = 0.0) -> dict:
    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        info = ticker.info or {}
        return fundamentals_from_yahoo_quote(info, last_price=last_price)
    except Exception as exc:
        logger.debug("yfinance info fundamentals failed for %s: %s", symbol, exc)
        return {}


def _fill_fundamentals_batch(symbols: List[str]) -> None:
    try:
        yf_map = {symbol: _yf_ticker(symbol) for symbol in symbols if symbol}
        reverse: Dict[str, List[str]] = {}
        for symbol, yf_sym in yf_map.items():
            if yf_sym:
                reverse.setdefault(yf_sym, []).append(symbol)
        rows = _fetch_yahoo_v7_quotes(list(reverse.keys()), timeout=6.0)
        for yf_sym, raw in rows.items():
            last = _safe_number(raw.get("regularMarketPrice") or raw.get("regularMarketPreviousClose"))
            fund = fundamentals_from_yahoo_quote(raw, last_price=last)
            if not fund.get("targetMeanPrice"):
                extra = _fetch_yahoo_quote_summary(yf_sym, timeout=4.0)
                fund = _merge_fundamentals(fund, extra)
            if not fund:
                continue
            for symbol in reverse.get(yf_sym, []):
                _put_fundamentals(symbol, fund)
                _quote_cache.patch(symbol, fund)
        leftover = [
            symbol
            for symbol in symbols
            if _needs_fundamentals(_get_cached_fundamentals(symbol))
        ]
        for symbol in leftover:
            yf_sym = yf_map.get(symbol)
            extra = _merge_fundamentals(
                _fetch_yahoo_quote_summary(yf_sym, timeout=4.0) if yf_sym else {},
                _fundamentals_from_yfinance_info(symbol),
            )
            if not extra:
                continue
            _put_fundamentals(symbol, extra)
            _quote_cache.patch(symbol, extra)
    except Exception as exc:
        logger.debug("fundamentals fill failed: %s", exc)
    finally:
        with _fundamentals_lock:
            for symbol in symbols:
                _fundamentals_inflight.discard(symbol)


def _schedule_fundamentals_fill(symbols: List[str]) -> None:
    """Refresh PE/EPS/yield/avg volume in the background — never blocks last-price."""
    pending: List[str] = []
    now = time.time()
    with _fundamentals_lock:
        for raw_symbol in symbols:
            key = (raw_symbol or "").strip().upper()
            if not key or key in _fundamentals_inflight:
                continue
            cached = _fundamentals_cache.get(key)
            if cached:
                stamp, payload = cached
                if (now - stamp) < float(FUNDAMENTALS_CACHE_TTL_SECONDS) and not _needs_fundamentals(payload):
                    continue
            _fundamentals_inflight.add(key)
            pending.append(key)
    if not pending:
        return
    Thread(
        target=_fill_fundamentals_batch,
        args=(pending,),
        daemon=True,
        name="quote-fundamentals-fill",
    ).start()


def fetch_quote(symbol: str) -> dict:
    """
    Fetch a single real-time quote for an NSE stock.
    Returns dict with: symbol, last, pctChange, open, high, low,
    volume, marketCap, previousClose, fiftyTwoWeekHigh, fiftyTwoWeekLow, pe, dividendYield
    """
    cached = _quote_cache.get(symbol, max_age_seconds=quote_max_age_seconds())
    if cached:
        filled = _overlay_fundamentals(cached, _get_cached_fundamentals(symbol))
        if filled is not cached and filled != cached:
            _quote_cache.patch(symbol, filled)
        if _needs_fundamentals(filled):
            _schedule_fundamentals_fill([symbol])
        return filled
    stale = _quote_cache.get_allow_stale(symbol, float(QUOTE_CACHE_STORAGE_SECONDS))

    try:
        # Near-live last price: one v7 call. Never Ticker.history / yf.download(period=2d)
        # — empty Yahoo charts look like "possibly delisted" for live NSE names.
        yf_sym = _yf_ticker(symbol)
        v7 = (_fetch_yahoo_v7_quotes([yf_sym], timeout=float(QUOTE_V7_TIMEOUT)) or {}).get(yf_sym) or {}
        live = _quote_from_yahoo_v7(symbol, v7, stale=stale)
        if live:
            _quote_cache.put(symbol, live)
            if _needs_fundamentals(live):
                _schedule_fundamentals_fill([symbol])
            return live
        if stale:
            return stale
        return _empty_quote(symbol)

    except Exception as e:
        logger.error(f"Error fetching quote for {symbol}: {e}")
        if stale:
            return stale
        return _empty_quote(symbol)


def fetch_quotes(
    symbols: List[str],
    max_age_seconds: Optional[float] = None,
    *,
    batch_size: Optional[int] = None,
    yf_threads: bool = False,
    individual_fallback: bool = True,
) -> List[dict]:
    """Fetch quotes for multiple symbols with optimized batching and caching.
    
    Optimization: 
    - Returns cached results first (faster)
    - Batches uncached requests to minimize API calls
    - Falls back to individual fetches on batch errors

    max_age_seconds: if set, treat cache entries older than this as misses
    (heatmap / stream pass a short age while the market is open).
    If omitted, uses quote_max_age_seconds() (~5s open, ~3m closed).
    batch_size / yf_threads: heatmap still requests a larger batch; last prices
    come from Yahoo v7 (one HTTP per ~40 tickers). yf.download is never used here.
    individual_fallback: at most QUOTE_INDIVIDUAL_FALLBACK_MAX per-symbol v7 calls.
    """
    if not symbols:
        return []
    
    results = []
    uncached = []
    symbol_map = {}  # Track position for results ordering
    size = max(1, int(batch_size or QUOTE_BATCH_SIZE))
    age_limit = quote_max_age_seconds() if max_age_seconds is None else max_age_seconds

    # Separate cached from uncached, preserving order
    for idx, s in enumerate(symbols):
        cached = _quote_cache.get(s, max_age_seconds=age_limit)
        if cached:
            results.append((idx, cached))
        else:
            uncached.append(s)
            symbol_map[s] = idx

    # Fetch uncached symbols in batches
    fallback_left = int(QUOTE_INDIVIDUAL_FALLBACK_MAX) if individual_fallback else 0
    if uncached:
        for start in range(0, len(uncached), size):
            batch_symbols = uncached[start:start + size]
            fetched = _fetch_batch_quotes(batch_symbols, yf_threads=yf_threads)
            for symbol in batch_symbols:
                quote = fetched.get(symbol)
                if quote is None and fallback_left > 0:
                    quote = fetch_quote(symbol)
                    fallback_left -= 1
                last_px = _safe_number((quote or {}).get("last"), 0.0) if quote else 0.0
                if quote and last_px > 0:
                    results.append((symbol_map[symbol], quote))
                else:
                    stale = _quote_cache.get_allow_stale(symbol, float(QUOTE_CACHE_STORAGE_SECONDS))
                    if stale and _safe_number(stale.get("last"), 0.0) > 0:
                        results.append((symbol_map[symbol], stale))

    # Sort by original order and extract quotes
    results.sort(key=lambda x: x[0])
    return [q for _, q in results]


def _quotes_from_v7_batch(batch_symbols: List[str], timeout: Optional[float] = None) -> dict:
    """Last-price batch via Yahoo v7 (one HTTP per ~40 tickers)."""
    results = {}
    yf_map = {symbol: _yf_ticker(symbol) for symbol in batch_symbols if symbol}
    reverse: Dict[str, List[str]] = {}
    for symbol, yf_sym in yf_map.items():
        if yf_sym:
            reverse.setdefault(yf_sym, []).append(symbol)
    if not reverse:
        return results
    rows = _fetch_yahoo_v7_quotes(
        list(reverse.keys()),
        timeout=float(timeout if timeout is not None else QUOTE_V7_TIMEOUT),
    )
    for yf_sym, raw in rows.items():
        for symbol in reverse.get(yf_sym, []):
            stale = _quote_cache.get_allow_stale(symbol, float(QUOTE_CACHE_STORAGE_SECONDS))
            quote = _quote_from_yahoo_v7(symbol, raw, stale=stale)
            if quote:
                _quote_cache.put(symbol, quote)
                results[symbol] = quote
    return results


def _fetch_batch_quotes(batch_symbols: List[str], *, yf_threads: bool = False) -> dict:
    """Fetch a batch of last prices via Yahoo v7 only. Never yf.download(period=2d)."""
    results = _quotes_from_v7_batch(batch_symbols)
    need_fund = [symbol for symbol, quote in results.items() if _needs_fundamentals(quote)]
    if need_fund:
        _schedule_fundamentals_fill(need_fund)
    return results


def _fetch_batch_quotes_download(
    batch_symbols: List[str],
    *,
    yf_threads: bool = False,
    timeout: float = 8.0,
) -> dict:
    """Unused on quote/stream/movers/heatmap. Kept for tests; do not call from hot paths."""
    results = {}
    if not batch_symbols:
        return results
    # Cap leftover history to 0–2 symbols so a Yahoo block cannot walk 32 names.
    batch_symbols = list(batch_symbols)[: max(0, min(2, int(QUOTE_INDIVIDUAL_FALLBACK_MAX)))]
    if not batch_symbols:
        return results

    try:
        yf_tickers = " ".join([_yf_ticker(s) for s in batch_symbols])
        data = yf.download(
            yf_tickers,
            period="2d",
            group_by="ticker",
            progress=False,
            threads=bool(yf_threads),
            timeout=max(3.0, float(timeout)),
        )

        for symbol in batch_symbols:
            try:
                yf_sym = _yf_ticker(symbol)
                if len(batch_symbols) == 1:
                    ticker_data = data
                else:
                    ticker_data = data[yf_sym] if yf_sym in data.columns.get_level_values(0) else None

                if ticker_data is not None and not ticker_data.empty:
                    last_price = _safe_number(ticker_data['Close'].iloc[-1], 0.0)
                    prev_close = _safe_number(
                        ticker_data['Close'].iloc[-2] if len(ticker_data) > 1 else last_price,
                        last_price,
                    )
                    if last_price <= 0:
                        continue
                    pct_change = round(((last_price - prev_close) / prev_close) * 100, 2) if prev_close > 0 else 0.0

                    quote = {
                        "symbol": symbol,
                        "last": round(last_price, 2),
                        "pctChange": pct_change,
                        "open": round(_safe_number(ticker_data['Open'].iloc[-1], last_price), 2),
                        "high": round(_safe_number(ticker_data['High'].iloc[-1], last_price), 2),
                        "low": round(_safe_number(ticker_data['Low'].iloc[-1], last_price), 2),
                        "previousClose": round(prev_close, 2),
                        "volume": int(_safe_number(ticker_data['Volume'].iloc[-1], 0.0)),
                        "avgVolume": None,
                        "marketCap": None,
                        "pe": None,
                        "trailingPE": None,
                        "eps": None,
                        "dividendYield": None,
                        "fiftyTwoWeekHigh": round(last_price * 1.15, 2),
                        "fiftyTwoWeekLow": round(last_price * 0.85, 2),
                        "targetMeanPrice": None,
                        "fiftyDayAverage": None,
                        "twoHundredDayAverage": None,
                        "bid": None,
                        "ask": None,
                        "timestamp": int(datetime.utcnow().timestamp() * 1000),
                    }
                    quote = _overlay_fundamentals(quote, _get_cached_fundamentals(symbol))
                    _quote_cache.put(symbol, quote)
                    results[symbol] = quote
            except Exception as e:
                logger.warning(f"Parse failed for {symbol} in batch: {e}")
        
        del data
    except Exception as e:
        logger.error(f"Batch download failed for {len(batch_symbols)} symbols: {e}")

    missing = [symbol for symbol, quote in results.items() if _needs_fundamentals(quote)]
    if missing:
        _schedule_fundamentals_fill(missing)
    return results


def _empty_quote(symbol: str) -> dict:
    """Return a zero-value quote when data is unavailable."""
    return {
        "symbol": symbol,
        "last": 0.0,
        "pctChange": 0.0,
        "open": 0.0,
        "high": 0.0,
        "low": 0.0,
        "previousClose": 0.0,
        "prevClose": 0.0,
        "volume": 0,
        "avgVolume": None,
        "marketCap": None,
        "pe": None,
        "trailingPE": None,
        "eps": None,
        "dividendYield": None,
        "fiftyTwoWeekHigh": None,
        "fiftyTwoWeekLow": None,
        "targetMeanPrice": None,
        "fiftyDayAverage": None,
        "twoHundredDayAverage": None,
        "bid": None,
        "ask": None,
        "timestamp": int(datetime.utcnow().timestamp() * 1000),
    }


def get_all_symbols() -> List[str]:
    """Return curated quote-universe symbols (used by /quotes/all)."""
    return list(INDIAN_STOCKS.keys())


def get_default_symbols() -> List[str]:
    """Return default symbols shown to users."""
    return DEFAULT_SYMBOLS


def get_stock_name(symbol: str) -> str:
    """Return company name for a symbol, or the symbol itself if unknown."""
    normalized = _strip_exchange_suffix(symbol or "")
    entry = get_stock_catalog().get(normalized) or INDIAN_STOCKS.get(normalized)
    return entry[1] if entry else (symbol or normalized)


def get_symbols_with_names() -> List[dict]:
    """Return searchable symbols with company names (curated + NSE equity master)."""
    return [
        {"symbol": sym, "name": info[1], "matchType": "catalog"}
        for sym, info in get_stock_catalog().items()
    ]


_SEARCH_CATALOG: Optional[Dict[str, tuple]] = None
_SEARCH_CATALOG_DIRTY = False
_SEARCH_CATALOG_LOCK = Lock()
_INDEX_SYMBOLS = {
    "NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT", "NIFTYBANK",
}
_MOVERS_CACHE: Dict[str, object] = {"fetched_at": 0.0, "payload": None, "refreshing": False}
_MOVERS_CACHE_TTL_OPEN = 20
_MOVERS_CACHE_TTL_CLOSED = 60
_MOVERS_CACHE_TTL_SECONDS = _MOVERS_CACHE_TTL_CLOSED
_MOVERS_STALE_TTL_SECONDS = 15 * 60
_MOVERS_CACHE_LOCK = Lock()
_MOVERS_REFRESH_LOCK = Lock()


def mark_stock_catalog_dirty() -> None:
    """Signal that NSE/BSE masters changed — next get_stock_catalog() rebuilds."""
    global _SEARCH_CATALOG_DIRTY
    _SEARCH_CATALOG_DIRTY = True


def invalidate_stock_catalog() -> None:
    """Clear process catalog cache (called when NSE/BSE listing masters refresh)."""
    global _SEARCH_CATALOG, _SEARCH_CATALOG_DIRTY
    _SEARCH_CATALOG_DIRTY = True
    with _SEARCH_CATALOG_LOCK:
        _SEARCH_CATALOG = None


def get_stock_catalog() -> Dict[str, tuple]:
    """
    Search/browse catalog = curated INDIAN_STOCKS (overrides win)
    + NSE EQUITY_L (SYMBOL.NS) + BSE active equities (BSE-only SYMBOL.BO + 6-digit codes).
    Dual-listed names stay on NSE; BSE scrip codes remain searchable.
    """
    global _SEARCH_CATALOG, _SEARCH_CATALOG_DIRTY
    with _SEARCH_CATALOG_LOCK:
        listings_stale = False
        try:
            from .stock_enricher import listings_are_stale

            listings_stale = listings_are_stale()
        except Exception:
            listings_stale = False
        if (
            _SEARCH_CATALOG is not None
            and not _SEARCH_CATALOG_DIRTY
            and not listings_stale
        ):
            return _SEARCH_CATALOG

        catalog: Dict[str, tuple] = dict(INDIAN_STOCKS)
        nse_count = 0
        bse_only_count = 0
        bse_code_count = 0
        try:
            from .stock_enricher import get_bse_equity_records, get_nse_equity_map

            nse_map = get_nse_equity_map()
            for sym, name in nse_map.items():
                key = (sym or "").strip().upper()
                if not key or key in catalog:
                    continue
                # Drop retired tickers that may still appear in stale equity masters.
                if key in LEGACY_SYMBOL_ALIASES:
                    continue
                catalog[key] = (f"{key}.NS", name)
                nse_count += 1

            for rec in get_bse_equity_records():
                code = str(rec.get("code") or "").strip()
                sid = str(rec.get("scrip_id") or "").strip().upper()
                name = str(rec.get("name") or sid or code).strip()
                yahoo_bo = str(rec.get("yahoo") or (f"{code}.BO" if code else ""))
                if not code or not yahoo_bo:
                    continue

                on_nse = bool(sid and (sid in catalog or sid in nse_map))
                if on_nse:
                    # Dual-listed: keep NSE analysis path; expose BSE code for search/quotes.
                    if code not in catalog:
                        # Prefer NSE yahoo for the shared name; code maps to .BO for BSE asks.
                        catalog[code] = (yahoo_bo, catalog.get(sid, (None, name))[1] or name)
                        bse_code_count += 1
                    continue

                # BSE-only equity — list under scrip_id and numeric code.
                if sid and sid not in catalog and sid not in LEGACY_SYMBOL_ALIASES:
                    catalog[sid] = (yahoo_bo, name)
                    bse_only_count += 1
                if code not in catalog:
                    catalog[code] = (yahoo_bo, name)
                    bse_code_count += 1
        except Exception as exc:
            logger.warning("NSE/BSE equity master merge skipped: %s", exc)

        # Never expose delisted aliases as searchable listed symbols.
        for legacy in LEGACY_SYMBOL_ALIASES:
            catalog.pop(legacy, None)

        _SEARCH_CATALOG = catalog
        _SEARCH_CATALOG_DIRTY = False  # clear dirty set during master load
        logger.info(
            "Stock catalog ready: %d symbols (curated=%d, nse_added=%d, bse_only=%d, bse_codes=%d)",
            len(catalog),
            len(INDIAN_STOCKS),
            nse_count,
            bse_only_count,
            bse_code_count,
        )
        return catalog


def get_stock_catalog_if_ready() -> Optional[Dict[str, tuple]]:
    """Return the in-memory catalog only — never downloads NSE/BSE masters."""
    with _SEARCH_CATALOG_LOCK:
        if _SEARCH_CATALOG is not None and not _SEARCH_CATALOG_DIRTY:
            return _SEARCH_CATALOG
        return None


def _movers_slice(payload: Dict[str, object], limit: int, *, cached: bool) -> Dict[str, object]:
    gainers = [
        row for row in (payload.get("gainers") or [])
        if isinstance(row, dict) and _safe_number(row.get("pctChange"), 0.0) > 0
    ][:limit]
    losers = [
        row for row in (payload.get("losers") or [])
        if isinstance(row, dict) and _safe_number(row.get("pctChange"), 0.0) < 0
    ][:limit]
    return {
        "gainers": gainers,
        "losers": losers,
        "mostActive": list(payload.get("mostActive") or [])[:limit],
        "universeSize": payload.get("universeSize", 0),
        "generatedAt": payload.get("generatedAt", ""),
        "cached": cached,
    }


def _compute_market_movers_payload() -> Dict[str, object]:
    """Heavy Yahoo path — keep the universe small for Home cold starts."""
    # Cap tightly: Home refresh competes with /quotes on Render free-tier.
    liquid = list(dict.fromkeys(
        list(DEFAULT_SYMBOLS)
        + [
            "ADANIENT", "ADANIPORTS", "JSWSTEEL", "ONGC", "COALINDIA",
            "BPCL", "DRREDDY", "CIPLA", "TECHM", "BAJAJFINSV",
            "ASIANPAINT", "ZOMATO", "HAL", "BEL", "NTPC",
        ]
    ))
    universe = [
        sym for sym in liquid
        if sym in INDIAN_STOCKS and sym not in _INDEX_SYMBOLS and not str(sym).startswith("^")
    ][:36]

    quotes = fetch_quotes(universe, individual_fallback=False)
    usable = []
    for q in quotes or []:
        try:
            last = _safe_number(q.get("last"), 0.0)
            pct = _safe_number(q.get("pctChange"), 0.0)
            vol = int(_safe_number(q.get("volume"), 0.0))
            sym = str(q.get("symbol") or "").upper()
            if not sym or last <= 0:
                continue
            name = INDIAN_STOCKS.get(sym, (None, sym))[1]
            usable.append({
                "symbol": sym,
                "name": name,
                "last": round(last, 2),
                "pctChange": round(pct, 2),
                "volume": vol,
            })
        except Exception:
            continue

    gainers = sorted(
        [row for row in usable if row["pctChange"] > 0],
        key=lambda x: x["pctChange"],
        reverse=True,
    )
    losers = sorted(
        [row for row in usable if row["pctChange"] < 0],
        key=lambda x: x["pctChange"],
    )
    most_active = sorted(usable, key=lambda x: x.get("volume", 0), reverse=True)
    generated_at = datetime.utcnow().isoformat() + "Z"
    return {
        "gainers": gainers[:25],
        "losers": losers[:25],
        "mostActive": most_active[:25],
        "universeSize": len(usable),
        "generatedAt": generated_at,
        "cached": False,
    }


def _refresh_movers_cache_async() -> None:
    """Background refresh so Home can keep serving a stale snapshot."""
    if not _MOVERS_REFRESH_LOCK.acquire(blocking=False):
        return
    try:
        with _MOVERS_CACHE_LOCK:
            if _MOVERS_CACHE.get("refreshing"):
                return
            _MOVERS_CACHE["refreshing"] = True
        try:
            payload = _compute_market_movers_payload()
            with _MOVERS_CACHE_LOCK:
                _MOVERS_CACHE["fetched_at"] = time.time()
                _MOVERS_CACHE["payload"] = payload
        except Exception as exc:
            logger.warning("movers_background_refresh_failed reason=%s", exc)
        finally:
            with _MOVERS_CACHE_LOCK:
                _MOVERS_CACHE["refreshing"] = False
    finally:
        _MOVERS_REFRESH_LOCK.release()


def fetch_market_movers(limit: int = 10) -> Dict[str, object]:
    """
    Rank a liquid Indian stock universe by day % change / volume.

    Stale-while-revalidate:
      - fresh cache (<60s): return immediately
      - stale but usable (<15m): return stale + kick background refresh
      - no cache: compute synchronously (smaller universe)
    """
    limit = max(1, min(int(limit or 10), 25))
    now = time.time()
    with _MOVERS_CACHE_LOCK:
        cached = _MOVERS_CACHE.get("payload")
        fetched_at = float(_MOVERS_CACHE.get("fetched_at") or 0.0)
        age = now - fetched_at if fetched_at else 1e9
        refreshing = bool(_MOVERS_CACHE.get("refreshing"))

    ttl = _MOVERS_CACHE_TTL_OPEN if quote_max_age_seconds() <= float(QUOTE_CACHE_TTL_OPEN) else _MOVERS_CACHE_TTL_CLOSED
    if cached and age < ttl:
        return _movers_slice(cached, limit, cached=True)

    if cached and age < _MOVERS_STALE_TTL_SECONDS:
        if not refreshing:
            Thread(target=_refresh_movers_cache_async, name="movers-refresh", daemon=True).start()
        return _movers_slice(cached, limit, cached=True)

    try:
        full_payload = _compute_market_movers_payload()
    except Exception as exc:
        logger.error("fetch_market_movers quote fetch failed: %s", exc)
        if cached:
            return _movers_slice(cached, limit, cached=True)
        return {
            "gainers": [],
            "losers": [],
            "mostActive": [],
            "universeSize": 0,
            "generatedAt": datetime.utcnow().isoformat() + "Z",
            "cached": False,
        }

    with _MOVERS_CACHE_LOCK:
        _MOVERS_CACHE["fetched_at"] = time.time()
        _MOVERS_CACHE["payload"] = full_payload
        _MOVERS_CACHE["refreshing"] = False

    return _movers_slice(full_payload, limit, cached=False)


_SEARCH_NOISE_WORDS = {
    "a",
    "an",
    "and",
    "cmp",
    "company",
    "for",
    "in",
    "is",
    "latest",
    "me",
    "my",
    "of",
    "price",
    "quote",
    "share",
    "shares",
    "stock",
    "stocks",
    "the",
    "today",
    "what",
}


def _strip_exchange_suffix(token: str) -> str:
    value = token.strip().upper()
    if value.endswith(".NS") or value.endswith(".BO"):
        return value.rsplit(".", 1)[0]
    return value


def _build_search_terms(raw_query: str) -> List[str]:
    normalized = re.sub(r"[^A-Za-z0-9]+", " ", (raw_query or "").lower()).strip()
    if not normalized:
        return []

    tokens = [token for token in normalized.split() if token]
    cleaned_tokens = [token for token in tokens if token not in _SEARCH_NOISE_WORDS]

    if cleaned_tokens:
        return cleaned_tokens
    return tokens


def _dedupe_keep_order(values: List[str]) -> List[str]:
    seen = set()
    deduped: List[str] = []
    for value in values:
        cleaned = value.strip()
        if not cleaned:
            continue
        lowered = cleaned.lower()
        if lowered in seen:
            continue
        seen.add(lowered)
        deduped.append(cleaned)
    return deduped


def search_stocks(query: str, limit: int = 50) -> List[dict]:
    """
    Search stocks by symbol or company name.
    Pipeline: exact → prefix → contains → name phrase → fuzzy → Yahoo search API.
    Catalog = curated INDIAN_STOCKS + NSE EQUITY_L + BSE active equities.
    """
    catalog = get_stock_catalog()
    terms = _build_search_terms(query)
    if not terms:
        return []

    # Never map greetings / chitchat into tickers (hi → HINDUNILVR).
    blocked = {
        "hi", "hii", "hiii", "hello", "hey", "yo", "sup", "thanks", "thank",
        "ty", "thx", "bye", "ok", "okay", "gm", "gn", "namaste", "namaskar",
    }
    if all(term.lower() in blocked for term in terms) and len(terms) <= 3:
        return []

    phrase = " ".join(terms)
    search_units = _dedupe_keep_order([phrase] + terms)
    search_units_lower = [unit.lower() for unit in search_units]
    search_units_upper = [_strip_exchange_suffix(unit) for unit in search_units]
    # Drop blocked tokens from fuzzy units so "hi there" still won't invent HINDUNILVR.
    search_units_lower = [u for u in search_units_lower if u not in blocked]
    search_units_upper = [u for u in search_units_upper if u.lower() not in blocked]
    if not search_units_lower and not search_units_upper:
        return []

    results = []
    seen = set()

    def _row(sym: str, name: str, match_type: str) -> dict:
        yahoo = catalog.get(sym, ("",))[0] if sym in catalog else ""
        return {
            "symbol": sym,
            "name": name,
            "matchType": match_type,
            "exchange": "BSE" if str(yahoo).endswith(".BO") else "NSE",
            "yahooTicker": yahoo or None,
        }

    # 1) Exact symbol match from any meaningful unit.
    for query_upper in search_units_upper:
        if query_upper in catalog and query_upper not in seen:
            results.append(_row(query_upper, catalog[query_upper][1], "exact"))
            seen.add(query_upper)

    # 2) Symbol prefix matches (highest priority after exact)
    # Require >= 3 chars so "hi" / "it" do not invent HINDUNILVR / ITC.
    for sym, (ticker, name) in catalog.items():
        if sym in seen:
            continue
        symbol_lower = sym.lower()
        if any(symbol_lower.startswith(unit) for unit in search_units_lower if len(unit) >= 3):
            results.append(_row(sym, name, "symbol"))
            seen.add(sym)

    # 3) Symbol contains matches — require >= 3 chars (was 2 → "hi" ⊂ HINDUNILVR).
    for sym, (ticker, name) in catalog.items():
        if sym in seen:
            continue
        symbol_lower = sym.lower()
        if any(unit in symbol_lower for unit in search_units_lower if len(unit) >= 3):
            results.append(_row(sym, name, "symbol"))
            seen.add(sym)

    # 4) Company name matches — phrase match or all-tokens match
    for sym, (ticker, name) in catalog.items():
        if sym in seen:
            continue
        name_lower = name.lower()
        phrase_match = phrase in name_lower
        token_match = all(term in name_lower for term in terms if len(term) >= 2)
        if phrase_match or token_match:
            results.append(_row(sym, name, "name"))
            seen.add(sym)

    # 4b) Partial token match — any search term appears in name (relaxed)
    if not results:
        for sym, (ticker, name) in catalog.items():
            if sym in seen:
                continue
            name_lower = name.lower()
            meaningful_terms = [t for t in terms if len(t) >= 3]
            if meaningful_terms and any(t in name_lower for t in meaningful_terms):
                results.append(_row(sym, name, "partial"))
                seen.add(sym)

    # 5) Fuzzy matching on company names (handles typos, "india" vs "indian")
    if not results and len(phrase) >= 3:
        _all_names = {sym: name.lower() for sym, (_, name) in catalog.items()}
        close_matches = difflib.get_close_matches(
            phrase, list(_all_names.values()), n=5, cutoff=0.45
        )
        for matched_name in close_matches:
            for sym, name_lower in _all_names.items():
                if name_lower == matched_name and sym not in seen:
                    results.append(_row(sym, catalog[sym][1], "fuzzy"))
                    seen.add(sym)
                    break

    # 6) Yahoo Finance search API — proper search, not just ticker guess
    if not results:
        yahoo_results = _yahoo_search_query(phrase, limit=5)
        for item in yahoo_results:
            sym = item.get("symbol", "")
            name = item.get("name", sym)
            if sym and sym not in seen:
                results.append({"symbol": sym, "name": name, "matchType": "yahoo"})
                seen.add(sym)

    # 7) Legacy fallback: try raw tokens as NSE then BSE Yahoo tickers
    if not results:
        yahoo_candidates = _dedupe_keep_order(search_units_upper + [term.upper() for term in terms])
        for candidate in yahoo_candidates:
            if len(candidate) < 2:
                continue
            suffixes = (".BO",) if len(candidate) == 6 and candidate.isdigit() else (".NS", ".BO")
            found = False
            for suffix in suffixes:
                try:
                    yf_name = f"{candidate}{suffix}"
                    ticker = yf.Ticker(yf_name)
                    hist = ticker.history(period="1d")
                    if hist.empty:
                        continue
                    try:
                        info = ticker.info
                        name = info.get("shortName", candidate)
                    except Exception:
                        name = candidate

                    results.append({
                        "symbol": candidate,
                        "name": name,
                        "matchType": "yahoo",
                        "exchange": "BSE" if yf_name.endswith(".BO") else "NSE",
                    })
                    found = True
                    break
                except Exception:
                    continue
            if found:
                break

    return results[:limit]


def _yahoo_search_query(query: str, limit: int = 5) -> List[dict]:
    """Search Yahoo Finance for Indian stocks using their search API."""
    try:
        encoded_q = urllib.parse.quote(query)
        url = (
            f"https://query2.finance.yahoo.com/v1/finance/search"
            f"?q={encoded_q}&quotesCount={limit}&newsCount=0"
            f"&listsCount=0&enableFuzzyQuery=true&quotesQueryId=tss_match_phrase_query"
        )
        req = urllib.request.Request(
            url,
            headers={"User-Agent": "Mozilla/5.0"},
            method="GET",
        )
        with urllib.request.urlopen(req, timeout=8) as resp:
            data = _json.loads(resp.read().decode("utf-8"))

        results = []
        for quote in data.get("quotes", []):
            exchange = quote.get("exchange", "")
            symbol = quote.get("symbol", "")
            short_name = quote.get("shortname", symbol)

            # Only accept NSE/BSE Indian stocks
            if exchange not in ("NSI", "BSE", "NSE", "BOM"):
                continue

            # Normalize: strip .NS/.BO suffix for our internal symbol
            clean_symbol = symbol.replace(".NS", "").replace(".BO", "")
            results.append({
                "symbol": clean_symbol,
                "name": short_name,
                "matchType": "yahoo",
            })

        return results
    except Exception as exc:
        logger.debug("yahoo_search_api_error query=%s reason=%s", query, str(exc))
        return []
