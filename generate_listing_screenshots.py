"""Play listing phone screenshots for BYSEL 4.0.12 — honest paper-practice copy."""
from __future__ import annotations

import os

from PIL import Image, ImageDraw, ImageFont

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "playstore-graphics",
    "listing-4.0.12",
)
os.makedirs(OUTPUT_DIR, exist_ok=True)

W, H = 1080, 1920
SURFACE = (11, 12, 14)
CARD = (42, 45, 51)
PRIMARY = (66, 165, 245)
TEXT = (255, 255, 255)
MUTED = (158, 172, 180)
POS = (0, 230, 118)
NEG = (255, 82, 82)
BAR = (18, 19, 22)


def fonts():
    try:
        return {
            "hero": ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 72),
            "title": ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 52),
            "sub": ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 30),
            "body": ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 28),
            "reg": ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 26),
            "small": ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 22),
        }
    except OSError:
        fallback = ImageFont.load_default()
        return {key: fallback for key in ("hero", "title", "sub", "body", "reg", "small")}


def card(draw: ImageDraw.ImageDraw, box, radius=18):
    draw.rounded_rectangle(box, radius=radius, fill=CARD)


def status_bar(draw, f):
    draw.rectangle([0, 0, W, 72], fill=BAR)
    draw.text((40, 22), "9:41", fill=TEXT, font=f["small"])
    draw.text((880, 22), "BYSEL", fill=MUTED, font=f["small"])


def footer(draw, f, active: str):
    draw.rectangle([0, H - 132, W, H], fill=BAR)
    items = ["Home", "AI", "Trade", "Portfolio", "Heatmap", "More"]
    slot = W // len(items)
    for i, name in enumerate(items):
        x = slot * i + 8
        color = PRIMARY if name == active else MUTED
        draw.text((x, H - 78), name, fill=color, font=f["small"])


def banner(draw, f, y, text):
    card(draw, [40, y, W - 40, y + 88], radius=14)
    draw.text((64, y + 26), text, fill=PRIMARY, font=f["small"])


def save(img: Image.Image, name: str):
    path = os.path.join(OUTPUT_DIR, name)
    img.save(path, "PNG", optimize=True)
    print(path)


def shot_auth(f):
    img = Image.new("RGB", (W, H), SURFACE)
    draw = ImageDraw.Draw(img)
    status_bar(draw, f)
    draw.text((64, 160), "BYSEL", fill=TEXT, font=f["hero"])
    draw.text((64, 250), "Paper practice. Account required.", fill=MUTED, font=f["sub"])
    banner(draw, f, 340, "Not live brokerage. Not investment advice.")
    card(draw, [64, 470, W - 64, 620])
    draw.text((96, 500), "Email and password", fill=TEXT, font=f["body"])
    draw.text((96, 550), "or phone OTP to sign in", fill=MUTED, font=f["reg"])
    card(draw, [64, 660, W - 64, 810])
    draw.text((96, 690), "Orders, wallet, and P&L", fill=TEXT, font=f["body"])
    draw.text((96, 740), "are simulated for education", fill=MUTED, font=f["reg"])
    draw.rounded_rectangle([64, 880, W - 64, 990], radius=16, fill=PRIMARY)
    draw.text((250, 910), "Create account / Sign in", fill=SURFACE, font=f["body"])
    draw.text((64, 1060), "Open testing 4.0.12", fill=MUTED, font=f["small"])
    draw.text((64, 1110), "Prices can lag the exchange tape.", fill=MUTED, font=f["small"])
    save(img, "01-sign-in.png")


def shot_quotes(f):
    img = Image.new("RGB", (W, H), SURFACE)
    draw = ImageDraw.Draw(img)
    status_bar(draw, f)
    draw.text((48, 110), "Watchlist", fill=TEXT, font=f["title"])
    draw.text((48, 180), "Sample quotes · paper practice", fill=MUTED, font=f["sub"])
    banner(draw, f, 240, "Education only. Not a buy or sell call.")
    rows = [
        ("RELIANCE", "Energy", "2,419.60", "+1.4%", True),
        ("TCS", "IT", "3,892.15", "-0.3%", False),
        ("HDFCBANK", "Banking", "1,645.30", "+0.8%", True),
        ("INFY", "IT", "1,523.40", "+2.1%", True),
        ("ITC", "FMCG", "418.20", "+0.4%", True),
    ]
    y = 360
    for symbol, sector, price, change, up in rows:
        card(draw, [40, y, W - 40, y + 150])
        draw.text((64, y + 28), symbol, fill=TEXT, font=f["body"])
        draw.text((64, y + 80), sector, fill=MUTED, font=f["small"])
        draw.text((680, y + 28), price, fill=TEXT, font=f["body"])
        draw.text((680, y + 80), change, fill=POS if up else NEG, font=f["reg"])
        y += 168
    footer(draw, f, "Home")
    save(img, "02-watchlist.png")


def shot_scanner(f):
    img = Image.new("RGB", (W, H), SURFACE)
    draw = ImageDraw.Draw(img)
    status_bar(draw, f)
    draw.text((48, 110), "Scanner", fill=TEXT, font=f["title"])
    draw.text((48, 180), "BYSEL Score · education, not a broker", fill=MUTED, font=f["sub"])
    banner(draw, f, 240, "Compare names. Education — not a buy list.")
    chips = ["Long-term", "Swing", "High Quality"]
    x = 48
    for i, chip in enumerate(chips):
        fill = PRIMARY if i == 0 else CARD
        fg = SURFACE if i == 0 else TEXT
        w = 300 if i == 0 else 250
        draw.rounded_rectangle([x, 360, x + w, 430], radius=20, fill=fill)
        draw.text((x + 28, 378), chip, fill=fg, font=f["small"])
        x += w + 20
    rows = [
        ("INFY", "72", "BYSEL Score from this snapshot"),
        ("TCS", "68", "Missing fields stay as —"),
        ("HCLTECH", "64", "Not a recommendation to buy or sell"),
    ]
    y = 470
    for symbol, score, note in rows:
        card(draw, [40, y, W - 40, y + 200])
        draw.text((64, y + 28), symbol, fill=TEXT, font=f["body"])
        draw.text((64, y + 90), note, fill=MUTED, font=f["small"])
        draw.text((860, y + 40), score, fill=PRIMARY, font=f["hero"])
        draw.text((820, y + 130), "Score", fill=MUTED, font=f["small"])
        y += 220
    footer(draw, f, "More")
    save(img, "03-scanner.png")


def shot_paper(f):
    img = Image.new("RGB", (W, H), SURFACE)
    draw = ImageDraw.Draw(img)
    status_bar(draw, f)
    draw.text((48, 110), "Paper book", fill=TEXT, font=f["title"])
    draw.text((48, 180), "Simulated wallet · not live orders", fill=MUTED, font=f["sub"])
    banner(draw, f, 240, "Import CSV/CAS is read-only. No broker routing.")
    card(draw, [40, 360, W - 40, 620])
    draw.text((64, 390), "Practice cash", fill=MUTED, font=f["small"])
    draw.text((64, 440), "₹ 2,50,000", fill=TEXT, font=f["hero"])
    draw.text((64, 540), "Day P&L uses live marks when possible", fill=MUTED, font=f["reg"])
    holdings = [
        ("RELIANCE", "8 qty · paper", "+₹1,240"),
        ("ITC", "40 qty · paper", "-₹180"),
        ("INFY", "Imported book", "Mark only"),
    ]
    y = 660
    for symbol, meta, pnl in holdings:
        card(draw, [40, y, W - 40, y + 150])
        draw.text((64, y + 28), symbol, fill=TEXT, font=f["body"])
        draw.text((64, y + 84), meta, fill=MUTED, font=f["small"])
        color = POS if pnl.startswith("+") else (MUTED if "Mark" in pnl else NEG)
        draw.text((740, y + 50), pnl, fill=color, font=f["reg"])
        y += 168
    footer(draw, f, "Portfolio")
    save(img, "04-paper-book.png")


def shot_heatmap(f):
    img = Image.new("RGB", (W, H), SURFACE)
    draw = ImageDraw.Draw(img)
    status_bar(draw, f)
    draw.text((48, 110), "Heatmap", fill=TEXT, font=f["title"])
    draw.text((48, 180), "Session snapshot · tap a sector for Scanner", fill=MUTED, font=f["sub"])
    banner(draw, f, 240, "After hours the tape stays frozen by design.")
    sectors = [
        ("IT", "+0.8%", True, [("TCS", True), ("INFY", True), ("WIPRO", False)]),
        ("Banking", "-0.4%", False, [("HDFCBANK", False), ("SBIN", True), ("ICICIBANK", False)]),
        ("Energy", "+1.1%", True, [("RELIANCE", True), ("ONGC", True), ("NTPC", False)]),
        ("FMCG", "+0.2%", True, [("HINDUNILVR", True), ("ITC", True), ("NESTLEIND", False)]),
    ]
    y = 360
    for name, chg, up, names in sectors:
        card(draw, [40, y, W - 40, y + 230])
        draw.text((64, y + 24), name, fill=TEXT, font=f["body"])
        draw.text((820, y + 24), chg, fill=POS if up else NEG, font=f["body"])
        tx = 64
        for i, (sym, green) in enumerate(names):
            tw = 300
            fill = (20, 70, 48) if green else (80, 32, 32)
            draw.rounded_rectangle([tx, y + 100, tx + tw, y + 190], radius=12, fill=fill)
            draw.text((tx + 20, y + 128), sym[:10], fill=TEXT, font=f["small"])
            tx += tw + 16
        y += 250
    footer(draw, f, "Heatmap")
    save(img, "05-heatmap.png")


def shot_ai(f):
    img = Image.new("RGB", (W, H), SURFACE)
    draw = ImageDraw.Draw(img)
    status_bar(draw, f)
    draw.text((48, 110), "AI assistant", fill=TEXT, font=f["title"])
    draw.text((48, 180), "Ask about a snapshot in plain language", fill=MUTED, font=f["sub"])
    banner(draw, f, 240, "Educational. Not a recommendation to buy or sell.")
    draw.rounded_rectangle([220, 370, W - 40, 500], radius=20, fill=PRIMARY)
    draw.text((250, 400), "What does this RELIANCE", fill=SURFACE, font=f["reg"])
    draw.text((250, 440), "snapshot show?", fill=SURFACE, font=f["reg"])
    card(draw, [40, 540, W - 80, 1080])
    lines = [
        "This is a paper-practice snapshot,",
        "not a live order ticket.",
        "",
        "Price and change come from public",
        "market data and can lag the tape.",
        "",
        "BYSEL Score is a comparison helper.",
        "It is not a Strong Buy or a target.",
        "",
        "Weekend / after-hours tape is frozen",
        "by design.",
    ]
    y = 580
    for line in lines:
        draw.text((72, y), line, fill=TEXT if line else MUTED, font=f["reg"])
        y += 40
    draw.rounded_rectangle([40, 1600, W - 140, 1710], radius=22, outline=MUTED, width=2)
    draw.text((70, 1634), "Ask about a snapshot...", fill=MUTED, font=f["reg"])
    draw.ellipse([W - 120, 1600, W - 40, 1680], fill=PRIMARY)
    footer(draw, f, "AI")
    save(img, "06-ai-assistant.png")


def feature_graphic():
    """Play feature graphic: 1024x500, not a phone screenshot."""
    w, h = 1024, 500
    img = Image.new("RGB", (w, h), SURFACE)
    draw = ImageDraw.Draw(img)
    try:
        title = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 92)
        sub = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 28)
        tag = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 22)
    except OSError:
        title = sub = tag = ImageFont.load_default()

    for y in range(h):
        t = y / h
        r = int(11 + (20 - 11) * t)
        g = int(12 + (28 - 12) * t)
        b = int(14 + (42 - 14) * t)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    points = [
        (560, 360), (620, 330), (680, 345), (740, 280),
        (800, 300), (860, 230), (920, 250), (980, 190),
    ]
    for i in range(len(points) - 1):
        draw.line([points[i], points[i + 1]], fill=PRIMARY, width=4)

    draw.text((72, 118), "BYSEL", fill=TEXT, font=title)
    draw.text((76, 230), "Indian market education and paper practice", fill=MUTED, font=sub)

    pills = ["Account required", "Not live trading", "NSE / BSE"]
    x = 76
    for pill in pills:
        box = draw.textbbox((0, 0), pill, font=tag)
        pw = box[2] - box[0]
        draw.rounded_rectangle([x, 300, x + pw + 36, 352], radius=16, fill=CARD)
        draw.text((x + 18, 312), pill, fill=PRIMARY, font=tag)
        x += pw + 52

    path = os.path.join(OUTPUT_DIR, "feature-graphic-1024x500.png")
    img.save(path, "PNG", optimize=True)
    print(path)


if __name__ == "__main__":
    f = fonts()
    shot_auth(f)
    shot_quotes(f)
    shot_scanner(f)
    shot_paper(f)
    shot_heatmap(f)
    shot_ai(f)
    feature_graphic()
    print("done")
