"""
Real-time market data provider using Yahoo Finance.
Fetches live NSE/BSE stock prices with caching to avoid rate limits.
Covers ALL major Indian stocks – NIFTY 500 and beyond.
"""

import yfinance as yf
import logging
import time
from datetime import datetime
from typing import Dict, Optional, List

logger = logging.getLogger(__name__)

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

    def __init__(self, ttl_seconds: int = 60):
        self._cache: Dict[str, dict] = {}
        self._timestamps: Dict[str, float] = {}
        self._ttl = ttl_seconds

    def get(self, symbol: str) -> Optional[dict]:
        if symbol in self._cache:
            if time.time() - self._timestamps[symbol] < self._ttl:
                return self._cache[symbol]
            else:
                del self._cache[symbol]
                del self._timestamps[symbol]
        return None

    def put(self, symbol: str, data: dict):
        self._cache[symbol] = data
        self._timestamps[symbol] = time.time()

    def clear(self):
        self._cache.clear()
        self._timestamps.clear()


# Global cache: quotes refresh every 60 seconds
_quote_cache = QuoteCache(ttl_seconds=60)


def _yf_ticker(symbol: str) -> str:
    """Convert our symbol to Yahoo Finance NSE ticker."""
    return NSE_SYMBOLS.get(symbol, f"{symbol}.NS")


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
        except Exception:
            day_high = float(hist['High'].iloc[-1])
            day_low = float(hist['Low'].iloc[-1])
            open_price = float(hist['Open'].iloc[-1])
            volume = int(hist['Volume'].iloc[-1])
            market_cap = 0
            pe_ratio = 0
            dividend_yield = 0
            fifty_two_high = last_price * 1.15
            fifty_two_low = last_price * 0.85

        quote = {
            "symbol": symbol,
            "last": round(last_price, 2),
            "pctChange": pct_change,
            "open": round(open_price, 2),
            "high": round(day_high, 2),
            "low": round(day_low, 2),
            "previousClose": round(prev_close, 2),
            "volume": volume,
            "marketCap": market_cap,
            "pe": round(pe_ratio, 2) if pe_ratio else 0,
            "dividendYield": round((dividend_yield or 0) * 100, 2),
            "fiftyTwoWeekHigh": round(fifty_two_high, 2),
            "fiftyTwoWeekLow": round(fifty_two_low, 2),
            "timestamp": int(datetime.utcnow().timestamp() * 1000),
        }

        _quote_cache.put(symbol, quote)
        logger.info(f"Fetched live quote: {symbol} = ₹{last_price:.2f} ({pct_change:+.2f}%)")
        return quote

    except Exception as e:
        logger.error(f"Error fetching quote for {symbol}: {e}")
        return _empty_quote(symbol)


def fetch_quotes(symbols: List[str]) -> List[dict]:
    """Fetch quotes for multiple symbols. Uses batch download for uncached symbols."""
    results = []
    uncached = []

    for s in symbols:
        cached = _quote_cache.get(s)
        if cached:
            results.append(cached)
        else:
            uncached.append(s)

    if uncached:
        try:
            yf_tickers = " ".join([_yf_ticker(s) for s in uncached])
            data = yf.download(yf_tickers, period="2d", group_by="ticker", progress=False, threads=True)

            for symbol in uncached:
                try:
                    yf_sym = _yf_ticker(symbol)
                    if len(uncached) == 1:
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
                            "marketCap": 0,
                            "pe": 0,
                            "dividendYield": 0,
                            "fiftyTwoWeekHigh": round(last_price * 1.15, 2),
                            "fiftyTwoWeekLow": round(last_price * 0.85, 2),
                            "timestamp": int(datetime.utcnow().timestamp() * 1000),
                        }
                        _quote_cache.put(symbol, quote)
                        results.append(quote)
                    else:
                        # Fallback to individual fetch
                        results.append(fetch_quote(symbol))
                except Exception as e:
                    logger.warning(f"Batch parse failed for {symbol}, falling back: {e}")
                    results.append(fetch_quote(symbol))

        except Exception as e:
            logger.error(f"Batch download failed: {e}, falling back to individual fetches")
            for symbol in uncached:
                results.append(fetch_quote(symbol))

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
        "marketCap": 0,
        "pe": 0,
        "dividendYield": 0,
        "fiftyTwoWeekHigh": 0,
        "fiftyTwoWeekLow": 0,
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
    entry = INDIAN_STOCKS.get(symbol)
    return entry[1] if entry else symbol


def get_symbols_with_names() -> List[dict]:
    """Return all symbols with their company names (for /symbols endpoint)."""
    return [
        {"symbol": sym, "name": info[1]}
        for sym, info in INDIAN_STOCKS.items()
    ]


def search_stocks(query: str, limit: int = 50) -> List[dict]:
    """
    Search stocks by symbol or company name.
    Works for ALL stocks - returns matches from the catalog,
    and also tries Yahoo Finance for unknown symbols.
    """
    query_lower = query.strip().lower()
    if not query_lower:
        return []

    results = []
    seen = set()

    # 1) Exact symbol match
    query_upper = query.strip().upper()
    if query_upper in INDIAN_STOCKS:
        sym = query_upper
        results.append({
            "symbol": sym,
            "name": INDIAN_STOCKS[sym][1],
            "matchType": "exact"
        })
        seen.add(sym)

    # 2) Symbol prefix matches (highest priority)
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym in seen:
            continue
        if sym.lower().startswith(query_lower):
            results.append({"symbol": sym, "name": name, "matchType": "symbol"})
            seen.add(sym)

    # 3) Symbol contains matches
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym in seen:
            continue
        if query_lower in sym.lower():
            results.append({"symbol": sym, "name": name, "matchType": "symbol"})
            seen.add(sym)

    # 4) Company name matches
    for sym, (ticker, name) in INDIAN_STOCKS.items():
        if sym in seen:
            continue
        if query_lower in name.lower():
            results.append({"symbol": sym, "name": name, "matchType": "name"})
            seen.add(sym)

    # 5) If no results from catalog, try as a direct Yahoo Finance ticker
    if not results and len(query_upper) >= 2:
        try:
            yf_sym = f"{query_upper}.NS"
            ticker = yf.Ticker(yf_sym)
            hist = ticker.history(period="1d")
            if not hist.empty:
                try:
                    info = ticker.info
                    name = info.get("shortName", query_upper)
                except Exception:
                    name = query_upper
                results.append({
                    "symbol": query_upper,
                    "name": name,
                    "matchType": "yahoo"
                })
        except Exception:
            pass

    return results[:limit]
