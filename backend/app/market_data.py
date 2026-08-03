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
from threading import Lock, Thread
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


QUOTE_CACHE_TTL_SECONDS = _env_int("QUOTE_CACHE_TTL_SECONDS", 180, minimum=5)
QUOTE_CACHE_MAX_ENTRIES = _env_int("QUOTE_CACHE_MAX_ENTRIES", 3000, minimum=50)
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

    hist = None
    for yahoo in list(dict.fromkeys(candidates)):
        try:
            hist = yf.Ticker(yahoo).history(
                period=normalized_period,
                interval=normalized_interval,
                auto_adjust=False,
            )
            if hist is not None and not hist.empty:
                break
        except Exception as exc:
            logger.debug("history failed for %s: %s", yahoo, exc)
            hist = None
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


def fetch_quotes(symbols: List[str], max_age_seconds: Optional[float] = None) -> List[dict]:
    """Fetch quotes for multiple symbols with optimized batching and caching.
    
    Optimization: 
    - Returns cached results first (faster)
    - Batches uncached requests to minimize API calls
    - Falls back to individual fetches on batch errors

    max_age_seconds: if set, treat cache entries older than this as misses
    (used by live heatmap to refresh every 1–2s while market is open).
    """
    if not symbols:
        return []
    
    results = []
    uncached = []
    symbol_map = {}  # Track position for results ordering

    # Separate cached from uncached, preserving order
    for idx, s in enumerate(symbols):
        cached = _quote_cache.get(s, max_age_seconds=max_age_seconds)
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
        {"symbol": sym, "name": info[1]}
        for sym, info in get_stock_catalog().items()
    ]


_SEARCH_CATALOG: Optional[Dict[str, tuple]] = None
_SEARCH_CATALOG_DIRTY = False
_SEARCH_CATALOG_LOCK = Lock()
_INDEX_SYMBOLS = {
    "NIFTY50", "SENSEX", "BANKNIFTY", "NIFTYIT", "NIFTYBANK",
}
_MOVERS_CACHE: Dict[str, object] = {"fetched_at": 0.0, "payload": None, "refreshing": False}
_MOVERS_CACHE_TTL_SECONDS = 60
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


def _movers_slice(payload: Dict[str, object], limit: int, *, cached: bool) -> Dict[str, object]:
    return {
        "gainers": list(payload.get("gainers") or [])[:limit],
        "losers": list(payload.get("losers") or [])[:limit],
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

    quotes = fetch_quotes(universe)
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

    gainers = sorted(usable, key=lambda x: x["pctChange"], reverse=True)
    losers = sorted(usable, key=lambda x: x["pctChange"])
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

    if cached and age < _MOVERS_CACHE_TTL_SECONDS:
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
            results.append({"symbol": clean_symbol, "name": short_name})

        return results
    except Exception as exc:
        logger.debug("yahoo_search_api_error query=%s reason=%s", query, str(exc))
        return []
