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
import urllib.request
import json as _json
from datetime import datetime
from threading import Lock
from typing import Dict, Optional, List

logger = logging.getLogger(__name__)


def _env_int(name: str, default: int, minimum: int = 1) -> int:
    raw_value = os.getenv(name)
    if raw_value is None:
        return default
    try:
        parsed = int(raw_value)
    except Exception:
        return default
    return max(minimum, parsed)


QUOTE_CACHE_TTL_SECONDS = _env_int("QUOTE_CACHE_TTL_SECONDS", 60, minimum=5)
QUOTE_CACHE_MAX_ENTRIES = _env_int("QUOTE_CACHE_MAX_ENTRIES", 350, minimum=50)
QUOTE_BATCH_SIZE = _env_int("QUOTE_BATCH_SIZE", 40, minimum=1)

HISTORY_ALLOWED_PERIODS = {
    "1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y", "10y", "ytd", "max"
}
HISTORY_ALLOWED_INTERVALS = {
    "1m", "2m", "5m", "15m", "30m", "60m", "90m", "1h", "1d", "5d", "1wk", "1mo", "3mo"
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
    "TATAMOTORS":   ("TATAMOTORS.NS",   "Tata Motors Ltd"),
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
    "ADANITRANS":   ("ADANITRANS.NS",   "Adani Transmission Ltd"),
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
    "ZOMATO":       ("ETERNAL.NS",      "Zomato Ltd (Eternal Ltd)"),

    # ── NIFTY MIDCAP 150 / POPULAR MIDCAPS ──────────────────
    "AAPL":         ("AAPL.NS",         "Not applicable - use international"),
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
    "GMRINFRA":     ("GMRINFRA.NS",     "GMR Airports Infrastructure Ltd"),
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
    "ADANIWILMAR":  ("ADANIWILMAR.NS",  "Adani Wilmar Ltd"),
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
    "IBULHSGFIN":   ("IBULHSGFIN.NS",  "Indiabulls Housing Finance Ltd"),
    "IDBI":         ("IDBI.NS",         "IDBI Bank Ltd"),
    "IDEA":         ("IDEA.NS",         "Vodafone Idea Ltd"),
    "IIFLWAM":      ("IIFLWAM.NS",      "360 ONE WAM Ltd"),
    "INDIGO":       ("INDIGO.NS",       "InterGlobe Aviation Ltd"),
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
    "L&TFH":        ("L&TFH.NS",        "L&T Finance Ltd"),
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
    "MINDAIND":     ("MINDAIND.NS",     "Minda Industries Ltd"),
    "MOTILALOFS":   ("MOTILALOFS.NS",   "Motilal Oswal Financial Services Ltd"),
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
    "VALUEIND":     ("VALUEIND.NS",     "Value Industries Ltd"),
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

    # ── BSE-ONLY / ADDITIONAL POPULAR ────────────────────────
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

# Build name lookup (lowercase company name → symbol) for search
_NAME_INDEX: Dict[str, str] = {}
for _sym, (_ticker, _name) in INDIAN_STOCKS.items():
    _NAME_INDEX[_name.lower()] = _sym

# Default symbols shown on app home / dashboard
DEFAULT_SYMBOLS = [
    "RELIANCE", "TCS", "INFY", "HDFCBANK", "SBIN",
    "WIPRO", "ICICIBANK", "KOTAKBANK", "HINDUNILVR", "ITC",
    "BHARTIARTL", "LT", "AXISBANK", "BAJFINANCE", "TATAMOTORS",
    "SUNPHARMA", "TITAN", "MARUTI", "HCLTECH", "TATASTEEL",
]


class QuoteCache:
    """In-memory cache for stock quotes with TTL."""

    def __init__(self, ttl_seconds: int = 60, max_entries: int = 350):
        self._cache: Dict[str, dict] = {}
        self._timestamps: Dict[str, float] = {}
        self._ttl = max(1, int(ttl_seconds))
        self._max_entries = max(1, int(max_entries))
        self._lock = Lock()

    def _evict_expired_locked(self, now: float) -> None:
        expired = [
            symbol
            for symbol, timestamp in self._timestamps.items()
            if (now - timestamp) >= self._ttl
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

    def get(self, symbol: str) -> Optional[dict]:
        now = time.time()
        with self._lock:
            self._evict_expired_locked(now)
            return self._cache.get(symbol)

    def put(self, symbol: str, data: dict):
        now = time.time()
        with self._lock:
            self._evict_expired_locked(now)
            self._cache[symbol] = data
            self._timestamps[symbol] = now
            self._evict_oversized_locked()

    def clear(self):
        with self._lock:
            self._cache.clear()
            self._timestamps.clear()

    def size(self) -> int:
        with self._lock:
            return len(self._cache)


# Global cache: quotes refresh every 60 seconds and stay memory bounded.
_quote_cache = QuoteCache(
    ttl_seconds=QUOTE_CACHE_TTL_SECONDS,
    max_entries=QUOTE_CACHE_MAX_ENTRIES,
)


def _yf_ticker(symbol: str) -> str:
    """Convert symbol input into a Yahoo Finance ticker with NSE/BSE support."""
    raw_symbol = (symbol or "").strip().upper()
    if not raw_symbol:
        return ""

    if raw_symbol.startswith("NSE:"):
        raw_symbol = raw_symbol.split(":", 1)[1].strip()
    elif raw_symbol.startswith("BSE:"):
        raw_symbol = raw_symbol.split(":", 1)[1].strip()
        if raw_symbol and not raw_symbol.endswith(".BO"):
            return f"{raw_symbol}.BO"

    if raw_symbol.endswith(".NS") or raw_symbol.endswith(".BO"):
        return raw_symbol

    if raw_symbol in NSE_SYMBOLS:
        return NSE_SYMBOLS[raw_symbol]

    if len(raw_symbol) == 6 and raw_symbol.isdigit():
        return f"{raw_symbol}.BO"

    return f"{raw_symbol}.NS"


def _safe_number(value: object, default: float = 0.0) -> float:
    try:
        number = float(value)
    except Exception:
        return default
    if number != number:  # NaN
        return default
    return number


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

    ticker = yf.Ticker(_yf_ticker(normalized_symbol))
    hist = ticker.history(period=normalized_period, interval=normalized_interval, auto_adjust=False)
    if hist is None or hist.empty:
        return []

    candles: List[dict] = []
    for index, row in hist.iterrows():
        try:
            timestamp_ms = int(index.timestamp() * 1000)
        except Exception:
            timestamp_ms = int(datetime.utcnow().timestamp() * 1000)

        candles.append(
            {
                "timestamp": timestamp_ms,
                "open": round(_safe_number(row.get("Open")), 4),
                "high": round(_safe_number(row.get("High")), 4),
                "low": round(_safe_number(row.get("Low")), 4),
                "close": round(_safe_number(row.get("Close")), 4),
                "volume": int(_safe_number(row.get("Volume"), default=0.0)),
            }
        )

    return candles


def fetch_quote(symbol: str) -> dict:
    """
    Fetch a single real-time quote for an NSE stock.
    Returns dict with: symbol, last, pctChange, open, high, low,
    volume, marketCap, previousClose, fiftyTwoWeekHigh, fiftyTwoWeekLow, pe, dividendYield
    """
    cached = _quote_cache.get(symbol)
    if cached:
        return cached

    try:
        ticker = yf.Ticker(_yf_ticker(symbol))
        info = ticker.fast_info
        hist = ticker.history(period="2d")

        if hist.empty:
            logger.warning(f"No history data for {symbol}")
            return _empty_quote(symbol)

        last_price = float(info.last_price) if hasattr(info, 'last_price') and info.last_price else float(hist['Close'].iloc[-1])
        prev_close = float(info.previous_close) if hasattr(info, 'previous_close') and info.previous_close else (
            float(hist['Close'].iloc[-2]) if len(hist) > 1 else last_price
        )

        pct_change = round(((last_price - prev_close) / prev_close) * 100, 2) if prev_close > 0 else 0.0

        # Get extended info (may fail for some stocks)
        try:
            full_info = ticker.info
            market_cap = full_info.get('marketCap', 0)
            pe_ratio = full_info.get('trailingPE', 0)
            dividend_yield = full_info.get('dividendYield', 0)
            fifty_two_high = full_info.get('fiftyTwoWeekHigh', last_price * 1.15)
            fifty_two_low = full_info.get('fiftyTwoWeekLow', last_price * 0.85)
            day_high = full_info.get('dayHigh', float(hist['High'].iloc[-1]))
            day_low = full_info.get('dayLow', float(hist['Low'].iloc[-1]))
            open_price = full_info.get('open', float(hist['Open'].iloc[-1]))
            volume = full_info.get('volume', int(hist['Volume'].iloc[-1]))
            avg_volume = full_info.get('averageVolume', full_info.get('averageVolume10days', 0))
            target_mean = full_info.get('targetMeanPrice', None)
            fifty_day_avg = full_info.get('fiftyDayAverage', None)
            two_hundred_day_avg = full_info.get('twoHundredDayAverage', None)
        except Exception:
            day_high = float(hist['High'].iloc[-1])
            day_low = float(hist['Low'].iloc[-1])
            open_price = float(hist['Open'].iloc[-1])
            volume = int(hist['Volume'].iloc[-1])
            avg_volume = 0
            market_cap = 0
            pe_ratio = 0
            dividend_yield = 0
            fifty_two_high = last_price * 1.15
            fifty_two_low = last_price * 0.85
            target_mean = None
            fifty_day_avg = None
            two_hundred_day_avg = None

        quote = {
            "symbol": symbol,
            "last": round(last_price, 2),
            "pctChange": pct_change,
            "open": round(open_price, 2),
            "high": round(day_high, 2),
            "low": round(day_low, 2),
            "previousClose": round(prev_close, 2),
            "volume": volume,
            "avgVolume": avg_volume,
            "marketCap": market_cap,
            "pe": round(pe_ratio, 2) if pe_ratio else 0,
            "dividendYield": round((dividend_yield or 0) * 100, 2),
            "fiftyTwoWeekHigh": round(fifty_two_high, 2),
            "fiftyTwoWeekLow": round(fifty_two_low, 2),
            "targetMeanPrice": round(target_mean, 2) if target_mean else None,
            "fiftyDayAverage": round(fifty_day_avg, 2) if fifty_day_avg else None,
            "twoHundredDayAverage": round(two_hundred_day_avg, 2) if two_hundred_day_avg else None,
            "timestamp": int(datetime.utcnow().timestamp() * 1000),
        }

        _quote_cache.put(symbol, quote)
        logger.info(f"Fetched live quote: {symbol} = ₹{last_price:.2f} ({pct_change:+.2f}%)")
        return quote

    except Exception as e:
        logger.error(f"Error fetching quote for {symbol}: {e}")
        return _empty_quote(symbol)


def fetch_quotes(symbols: List[str]) -> List[dict]:
    """Fetch quotes for multiple symbols with optimized batching and caching.
    
    Optimization: 
    - Returns cached results first (faster)
    - Batches uncached requests to minimize API calls
    - Falls back to individual fetches on batch errors
    """
    if not symbols:
        return []
    
    results = []
    uncached = []
    symbol_map = {}  # Track position for results ordering

    # Separate cached from uncached, preserving order
    for idx, s in enumerate(symbols):
        cached = _quote_cache.get(s)
        if cached:
            results.append((idx, cached))
        else:
            uncached.append(s)
            symbol_map[s] = idx

    # Fetch uncached symbols in batches
    if uncached:
        for start in range(0, len(uncached), QUOTE_BATCH_SIZE):
            batch_symbols = uncached[start:start + QUOTE_BATCH_SIZE]
            fetched = _fetch_batch_quotes(batch_symbols)
            for idx, symbol in enumerate(batch_symbols):
                quote = fetched.get(symbol) or fetch_quote(symbol)
                results.append((symbol_map[symbol], quote))

    # Sort by original order and extract quotes
    results.sort(key=lambda x: x[0])
    return [q for _, q in results]


def _fetch_batch_quotes(batch_symbols: List[str]) -> dict:
    """Fetch a batch of quotes (max QUOTE_BATCH_SIZE) efficiently."""
    results = {}
    
    try:
        yf_tickers = " ".join([_yf_ticker(s) for s in batch_symbols])
        data = yf.download(
            yf_tickers,
            period="2d",
            group_by="ticker",
            progress=False,
            threads=False,
            timeout=15,  # Add timeout to prevent hanging
        )

        for symbol in batch_symbols:
            try:
                yf_sym = _yf_ticker(symbol)
                if len(batch_symbols) == 1:
                    ticker_data = data
                else:
                    ticker_data = data[yf_sym] if yf_sym in data.columns.get_level_values(0) else None

                if ticker_data is not None and not ticker_data.empty:
                    last_price = float(ticker_data['Close'].iloc[-1])
                    prev_close = float(ticker_data['Close'].iloc[-2]) if len(ticker_data) > 1 else last_price
                    pct_change = round(((last_price - prev_close) / prev_close) * 100, 2) if prev_close > 0 else 0.0

                    quote = {
                        "symbol": symbol,
                        "last": round(last_price, 2),
                        "pctChange": pct_change,
                        "open": round(float(ticker_data['Open'].iloc[-1]), 2),
                        "high": round(float(ticker_data['High'].iloc[-1]), 2),
                        "low": round(float(ticker_data['Low'].iloc[-1]), 2),
                        "previousClose": round(prev_close, 2),
                        "volume": int(ticker_data['Volume'].iloc[-1]),
                        "avgVolume": 0,
                        "marketCap": 0,
                        "pe": 0,
                        "dividendYield": 0,
                        "fiftyTwoWeekHigh": round(last_price * 1.15, 2),
                        "fiftyTwoWeekLow": round(last_price * 0.85, 2),
                        "targetMeanPrice": None,
                        "fiftyDayAverage": None,
                        "twoHundredDayAverage": None,
                        "timestamp": int(datetime.utcnow().timestamp() * 1000),
                    }
                    _quote_cache.put(symbol, quote)
                    results[symbol] = quote
            except Exception as e:
                logger.warning(f"Parse failed for {symbol} in batch: {e}")
        
        del data
    except Exception as e:
        logger.error(f"Batch download failed for {len(batch_symbols)} symbols: {e}")
    
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
        "volume": 0,
        "avgVolume": 0,
        "marketCap": 0,
        "pe": 0,
        "dividendYield": 0,
        "fiftyTwoWeekHigh": 0,
        "fiftyTwoWeekLow": 0,
        "targetMeanPrice": None,
        "fiftyDayAverage": None,
        "twoHundredDayAverage": None,
        "timestamp": int(datetime.utcnow().timestamp() * 1000),
    }


def get_all_symbols() -> List[str]:
    """Return all supported symbols."""
    return list(INDIAN_STOCKS.keys())


def get_default_symbols() -> List[str]:
    """Return default symbols shown to users."""
    return DEFAULT_SYMBOLS


def get_stock_name(symbol: str) -> str:
    """Return company name for a symbol, or the symbol itself if unknown."""
    normalized = _strip_exchange_suffix(symbol or "")
    entry = INDIAN_STOCKS.get(normalized)
    return entry[1] if entry else (symbol or normalized)


def get_symbols_with_names() -> List[dict]:
    """Return all symbols with their company names (for /symbols endpoint)."""
    return [
        {"symbol": sym, "name": info[1]}
        for sym, info in INDIAN_STOCKS.items()
    ]


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
    """
    terms = _build_search_terms(query)
    if not terms:
        return []

    phrase = " ".join(terms)
    search_units = _dedupe_keep_order([phrase] + terms)
    search_units_lower = [unit.lower() for unit in search_units]
    search_units_upper = [_strip_exchange_suffix(unit) for unit in search_units]

    results = []
    seen = set()

    # 1) Exact symbol match from any meaningful unit.
    for query_upper in search_units_upper:
        if query_upper in INDIAN_STOCKS and query_upper not in seen:
            results.append({
                "symbol": query_upper,
                "name": INDIAN_STOCKS[query_upper][1],
                "matchType": "exact"
            })
            seen.add(query_upper)

    # 2) Symbol prefix matches (highest priority after exact)
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym in seen:
            continue
        symbol_lower = sym.lower()
        if any(symbol_lower.startswith(unit) for unit in search_units_lower if len(unit) >= 2):
            results.append({"symbol": sym, "name": name, "matchType": "symbol"})
            seen.add(sym)

    # 3) Symbol contains matches
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym in seen:
            continue
        symbol_lower = sym.lower()
        if any(unit in symbol_lower for unit in search_units_lower if len(unit) >= 2):
            results.append({"symbol": sym, "name": name, "matchType": "symbol"})
            seen.add(sym)

    # 4) Company name matches — phrase match or all-tokens match
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym in seen:
            continue
        name_lower = name.lower()
        phrase_match = phrase in name_lower
        token_match = all(term in name_lower for term in terms if len(term) >= 2)
        if phrase_match or token_match:
            results.append({"symbol": sym, "name": name, "matchType": "name"})
            seen.add(sym)

    # 4b) Partial token match — any search term appears in name (relaxed)
    if not results:
        for sym, (ticker, name) in INDIAN_STOCKS.items():
            if sym in seen:
                continue
            name_lower = name.lower()
            meaningful_terms = [t for t in terms if len(t) >= 3]
            if meaningful_terms and any(t in name_lower for t in meaningful_terms):
                results.append({"symbol": sym, "name": name, "matchType": "partial"})
                seen.add(sym)

    # 5) Fuzzy matching on company names (handles typos, "india" vs "indian")
    if not results and len(phrase) >= 3:
        _all_names = {sym: name.lower() for sym, (_, name) in INDIAN_STOCKS.items()}
        close_matches = difflib.get_close_matches(
            phrase, list(_all_names.values()), n=5, cutoff=0.45
        )
        for matched_name in close_matches:
            for sym, name_lower in _all_names.items():
                if name_lower == matched_name and sym not in seen:
                    results.append({
                        "symbol": sym,
                        "name": INDIAN_STOCKS[sym][1],
                        "matchType": "fuzzy"
                    })
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

    # 7) Legacy fallback: try raw tokens as NSE tickers
    if not results:
        yahoo_candidates = _dedupe_keep_order(search_units_upper + [term.upper() for term in terms])
        for candidate in yahoo_candidates:
            if len(candidate) < 2:
                continue
            try:
                ticker = yf.Ticker(f"{candidate}.NS")
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
                    "matchType": "yahoo"
                })
                break
            except Exception:
                continue

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
            results.append({"symbol": clean_symbol, "name": short_name})

        return results
    except Exception as exc:
        logger.debug("yahoo_search_api_error query=%s reason=%s", query, str(exc))
        return []
