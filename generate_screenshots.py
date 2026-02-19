"""Generate phone screenshot mockups for Play Store listing."""
from PIL import Image, ImageDraw, ImageFont
import os

OUTPUT_DIR = r"c:\Users\sriha\Desktop\Applications\BYSEL\BYSEL\playstore-graphics"
os.makedirs(OUTPUT_DIR, exist_ok=True)

# Phone screenshot dimensions (9:16 aspect ratio)
W, H = 1080, 1920


def get_fonts():
    try:
        title = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 64)
        subtitle = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 36)
        body = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 30)
        small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 24)
        big = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 80)
        emoji_font = ImageFont.truetype("C:/Windows/Fonts/seguiemj.ttf", 48)
    except:
        title = subtitle = body = small = big = emoji_font = ImageFont.load_default()
    return title, subtitle, body, small, big, emoji_font


def draw_gradient(draw, w, h, color1, color2):
    for y in range(h):
        r = int(color1[0] + (color2[0] - color1[0]) * y / h)
        g = int(color1[1] + (color2[1] - color1[1]) * y / h)
        b = int(color1[2] + (color2[2] - color1[2]) * y / h)
        draw.line([(0, y), (w, y)], fill=(r, g, b))


def draw_card(draw, x, y, w, h, radius=20):
    draw.rounded_rectangle([x, y, x + w, y + h], radius=radius,
                           fill=(30, 30, 60), outline=(60, 60, 100), width=2)


def screenshot_dashboard():
    """Screenshot 1: Dashboard with stock prices."""
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    title_f, sub_f, body_f, small_f, big_f, _ = get_fonts()

    draw_gradient(draw, W, H, (15, 15, 35), (26, 35, 80))

    # Status bar area
    draw.rectangle([0, 0, W, 80], fill=(10, 10, 30))
    draw.text((40, 25), "9:41", fill=(255, 255, 255), font=small_f)

    # Header
    draw.text((40, 120), "BYSEL", fill=(255, 255, 255), font=big_f)
    draw.text((40, 210), "AI-Powered Stock Trading", fill=(180, 180, 220), font=sub_f)

    # Market summary card
    draw_card(draw, 40, 300, W - 80, 160)
    draw.text((70, 320), "Market Overview", fill=(200, 200, 240), font=sub_f)
    draw.text((70, 370), "NIFTY 50", fill=(255, 255, 255), font=body_f)
    draw.text((350, 370), "22,147.50", fill=(124, 255, 180), font=body_f)
    draw.text((620, 370), "+1.23%", fill=(124, 255, 180), font=body_f)
    draw.text((70, 410), "SENSEX", fill=(255, 255, 255), font=body_f)
    draw.text((350, 410), "73,245.80", fill=(124, 255, 180), font=body_f)
    draw.text((620, 410), "+0.98%", fill=(124, 255, 180), font=body_f)

    # Stock cards
    stocks = [
        ("RELIANCE", "Reliance Industries", "2,419.60", "+1.45%", True),
        ("TCS", "Tata Consultancy", "3,892.15", "-0.32%", False),
        ("HDFCBANK", "HDFC Bank Ltd", "1,645.30", "+0.87%", True),
        ("INFY", "Infosys Ltd", "1,523.40", "+2.15%", True),
        ("ICICIBANK", "ICICI Bank Ltd", "1,087.90", "-0.54%", False),
        ("WIPRO", "Wipro Ltd", "485.60", "+1.12%", True),
        ("BHARTIARTL", "Bharti Airtel", "1,234.50", "+0.76%", True),
    ]

    y = 500
    for symbol, name, price, change, positive in stocks:
        draw_card(draw, 40, y, W - 80, 130)
        draw.text((70, y + 20), symbol, fill=(255, 255, 255), font=body_f)
        draw.text((70, y + 60), name, fill=(150, 150, 180), font=small_f)
        
        color = (124, 255, 180) if positive else (255, 100, 100)
        draw.text((700, y + 20), price, fill=(255, 255, 255), font=body_f)
        draw.text((700, y + 60), change, fill=color, font=small_f)
        
        # Mini chart line
        if positive:
            points = [(850, y+80), (870, y+60), (900, y+70), (930, y+40), (960, y+50), (990, y+30)]
        else:
            points = [(850, y+30), (870, y+50), (900, y+40), (930, y+70), (960, y+60), (990, y+80)]
        for i in range(len(points) - 1):
            draw.line([points[i], points[i+1]], fill=color, width=3)
        
        y += 150

    # Bottom nav bar
    draw.rectangle([0, H - 140, W, H], fill=(15, 15, 40))
    nav_items = ["Home", "AI", "Trade", "Portfolio", "Heatmap", "More"]
    nav_x = 40
    for item in nav_items:
        color = (124, 77, 255) if item == "Home" else (120, 120, 150)
        # Draw dot for active
        if item == "Home":
            draw.ellipse([nav_x + 35, H - 130, nav_x + 55, H - 110], fill=(124, 77, 255))
        draw.text((nav_x + 15, H - 80), item, fill=color, font=small_f)
        nav_x += W // 6

    img.save(os.path.join(OUTPUT_DIR, "screenshot_1_dashboard.png"), "PNG")
    print("Screenshot 1 (Dashboard) saved")


def screenshot_ai_assistant():
    """Screenshot 2: AI Assistant chat."""
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    title_f, sub_f, body_f, small_f, big_f, _ = get_fonts()

    draw_gradient(draw, W, H, (15, 15, 35), (26, 35, 80))

    # Header
    draw.rounded_rectangle([0, 0, W, 200], radius=0, fill=(26, 35, 126))
    draw_gradient_rect(draw, 0, 0, W, 200, (26, 35, 126), (124, 77, 255))
    draw.text((40, 100), "AI Stock Assistant", fill=(255, 255, 255), font=title_f)
    draw.text((40, 165), "Ask me anything about stocks", fill=(200, 200, 240), font=small_f)

    # User message bubble
    y = 260
    draw.rounded_rectangle([300, y, W - 40, y + 80], radius=20, fill=(124, 77, 255))
    draw.text((330, y + 20), "Should I buy RELIANCE?", fill=(255, 255, 255), font=body_f)
    
    # AI response bubble
    y = 380
    draw.rounded_rectangle([40, y, W - 100, y + 380], radius=20, fill=(30, 30, 60))
    
    response_lines = [
        "RELIANCE INDUSTRIES LTD",
        "looks excellent right now!",
        "",
        "Current Price: Rs 2,419.60",
        "RSI: 58.49 (healthy range)",
        "P/E Ratio: 23.1 (fair value)",
        "",
        "AI Prediction: +2.6% in 1 month",
        "Target: Rs 2,482.50",
        "",
        "BYSEL Score: 75/100",
        "Strong pick!",
    ]
    
    ry = y + 20
    for line in response_lines:
        if "excellent" in line or "Strong" in line:
            draw.text((70, ry), line, fill=(124, 255, 180), font=small_f)
        elif "Score" in line:
            draw.text((70, ry), line, fill=(255, 215, 0), font=body_f)
        elif line == "":
            ry -= 10
        else:
            draw.text((70, ry), line, fill=(220, 220, 240), font=small_f)
        ry += 32

    # Suggestion chips
    y = 800
    chips = ["Compare with TCS", "Predict price", "Sector analysis"]
    cx = 40
    for chip in chips:
        bbox = draw.textbbox((0, 0), chip, font=small_f)
        cw = bbox[2] - bbox[0] + 40
        draw.rounded_rectangle([cx, y, cx + cw, y + 50], radius=25,
                               outline=(124, 77, 255), width=2)
        draw.text((cx + 20, y + 10), chip, fill=(124, 77, 255), font=small_f)
        cx += cw + 20

    # Another user message
    y = 900
    draw.rounded_rectangle([200, y, W - 40, y + 80], radius=20, fill=(124, 77, 255))
    draw.text((230, y + 20), "Compare TCS vs INFOSYS", fill=(255, 255, 255), font=body_f)

    # AI comparison response
    y = 1020
    draw_card(draw, 40, y, W - 80, 500)
    draw.text((70, y + 20), "Stock Comparison", fill=(200, 200, 240), font=sub_f)
    
    # TCS column
    draw.text((70, y + 80), "TCS", fill=(255, 255, 255), font=body_f)
    draw.text((70, y + 120), "Price: Rs 3,892.15", fill=(200, 200, 220), font=small_f)
    draw.text((70, y + 155), "P/E: 28.5", fill=(200, 200, 220), font=small_f)
    draw.text((70, y + 190), "RSI: 62.3", fill=(200, 200, 220), font=small_f)
    draw.text((70, y + 225), "Score: 72/100", fill=(255, 215, 0), font=small_f)
    draw.text((70, y + 265), "Prediction: +1.8%", fill=(124, 255, 180), font=small_f)

    # INFY column
    draw.text((550, y + 80), "INFOSYS", fill=(255, 255, 255), font=body_f)
    draw.text((550, y + 120), "Price: Rs 1,523.40", fill=(200, 200, 220), font=small_f)
    draw.text((550, y + 155), "P/E: 25.1", fill=(200, 200, 220), font=small_f)
    draw.text((550, y + 190), "RSI: 55.7", fill=(200, 200, 220), font=small_f)
    draw.text((550, y + 225), "Score: 68/100", fill=(255, 215, 0), font=small_f)
    draw.text((550, y + 265), "Prediction: +2.3%", fill=(124, 255, 180), font=small_f)

    draw.text((70, y + 340), "Winner: TCS (higher score)", fill=(124, 255, 180), font=body_f)
    draw.text((70, y + 390), "But INFOSYS has better value (lower P/E)", fill=(200, 200, 220), font=small_f)
    draw.text((70, y + 430), "and stronger predicted growth.", fill=(200, 200, 220), font=small_f)

    # Input bar at bottom
    draw.rectangle([0, H - 200, W, H], fill=(15, 15, 40))
    draw.rounded_rectangle([40, H - 180, W - 140, H - 120], radius=25,
                           outline=(60, 60, 100), width=2)
    draw.text((70, H - 170), "Ask about any stock...", fill=(100, 100, 130), font=body_f)
    draw.ellipse([W - 120, H - 180, W - 40, H - 120], fill=(124, 77, 255))
    # Arrow in send button
    draw.polygon([(W - 95, H - 140), (W - 60, H - 155), (W - 60, H - 125)],
                 fill=(255, 255, 255))

    # Bottom nav
    draw.rectangle([0, H - 100, W, H], fill=(15, 15, 40))

    img.save(os.path.join(OUTPUT_DIR, "screenshot_2_ai_assistant.png"), "PNG")
    print("Screenshot 2 (AI Assistant) saved")


def screenshot_heatmap():
    """Screenshot 3: Market Heatmap."""
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    title_f, sub_f, body_f, small_f, big_f, _ = get_fonts()

    draw_gradient(draw, W, H, (15, 15, 35), (26, 35, 80))

    # Header with mood
    draw.rounded_rectangle([0, 0, W, 220], radius=0, fill=None)
    draw_gradient_rect(draw, 0, 0, W, 220, (20, 80, 40), (40, 120, 60))
    draw.text((40, 80), "Market Heatmap", fill=(255, 255, 255), font=title_f)
    draw.text((40, 155), "BULLISH    Advances: 67  Declines: 43", fill=(180, 255, 200), font=small_f)

    # Market breadth bar
    bar_y = 260
    draw_card(draw, 40, bar_y, W - 80, 100)
    draw.text((70, bar_y + 10), "Market Breadth", fill=(200, 200, 240), font=small_f)
    # Green bar (67%)
    bar_w = W - 140
    green_w = int(bar_w * 0.6)
    draw.rounded_rectangle([70, bar_y + 50, 70 + green_w, bar_y + 80], radius=10,
                           fill=(76, 175, 80))
    draw.rounded_rectangle([70 + green_w, bar_y + 50, 70 + bar_w, bar_y + 80], radius=10,
                           fill=(244, 67, 54))
    draw.text((80, bar_y + 52), "67 Advances", fill=(255, 255, 255), font=small_f)
    draw.text((70 + green_w + 20, bar_y + 52), "43 Declines", fill=(255, 255, 255), font=small_f)

    # Sector heatmaps
    sectors = [
        ("Banking", "+1.23%", [
            ("HDFCBANK", "+1.8%", True), ("ICICIBANK", "+0.5%", True),
            ("SBIN", "+2.1%", True), ("KOTAKBANK", "-0.3%", False),
            ("AXISBANK", "+1.5%", True), ("INDUSINDBK", "+0.8%", True),
        ]),
        ("IT", "-0.45%", [
            ("TCS", "-0.3%", False), ("INFY", "+0.5%", True),
            ("WIPRO", "-1.2%", False), ("HCLTECH", "+0.2%", True),
            ("TECHM", "-0.8%", False), ("LTIM", "-0.5%", False),
        ]),
        ("Pharma", "+0.87%", [
            ("SUNPHARMA", "+1.5%", True), ("DRREDDY", "+0.8%", True),
            ("CIPLA", "-0.2%", False), ("DIVISLAB", "+2.3%", True),
            ("APOLLOHOSP", "+0.5%", True), ("BIOCON", "+1.1%", True),
        ]),
        ("Auto", "+1.56%", [
            ("TATAMOTORS", "+2.5%", True), ("M&M", "+1.8%", True),
            ("MARUTI", "+0.9%", True), ("BAJAJ-AUTO", "+1.2%", True),
            ("HEROMOTOCO", "-0.3%", False), ("EICHERMOT", "+0.5%", True),
        ]),
    ]

    y = 400
    for sector_name, sector_change, stocks_list in sectors:
        draw_card(draw, 40, y, W - 80, 240)
        
        positive = not sector_change.startswith("-")
        color = (124, 255, 180) if positive else (255, 100, 100)
        draw.text((70, y + 15), sector_name, fill=(255, 255, 255), font=body_f)
        draw.text((400, y + 15), sector_change, fill=color, font=body_f)
        
        # Stock tiles in grid
        tile_w = (W - 160) // 3
        tile_h = 70
        tx, ty = 70, y + 60
        for i, (sym, chg, pos) in enumerate(stocks_list):
            if i > 0 and i % 3 == 0:
                tx = 70
                ty += tile_h + 10
            
            tile_color = (46, 125, 50) if pos else (198, 40, 40)
            draw.rounded_rectangle([tx, ty, tx + tile_w - 10, ty + tile_h],
                                   radius=8, fill=tile_color)
            draw.text((tx + 10, ty + 8), sym[:8], fill=(255, 255, 255), font=small_f)
            chg_color = (200, 255, 200) if pos else (255, 200, 200)
            draw.text((tx + 10, ty + 38), chg, fill=chg_color, font=small_f)
            
            tx += tile_w

        y += 260

    img.save(os.path.join(OUTPUT_DIR, "screenshot_3_heatmap.png"), "PNG")
    print("Screenshot 3 (Heatmap) saved")


def screenshot_portfolio():
    """Screenshot 4: Portfolio with Health Score."""
    img = Image.new("RGB", (W, H))
    draw = ImageDraw.Draw(img)
    title_f, sub_f, body_f, small_f, big_f, _ = get_fonts()

    draw_gradient(draw, W, H, (15, 15, 35), (26, 35, 80))

    # Header
    draw.text((40, 80), "Portfolio", fill=(255, 255, 255), font=title_f)

    # Health Score Card
    draw_card(draw, 40, 200, W - 80, 400)
    
    # Grade circle
    draw.ellipse([80, 230, 220, 370], fill=(124, 77, 255))
    draw.text((115, 255), "A", fill=(255, 255, 255), font=big_f)
    
    # Score info
    draw.text((260, 240), "Portfolio Health Score", fill=(200, 200, 240), font=sub_f)
    draw.text((260, 290), "78 / 100", fill=(255, 255, 255), font=title_f)
    
    # Score bar
    bar_w = W - 340
    draw.rounded_rectangle([260, 370, 260 + bar_w, 395], radius=12, fill=(40, 40, 70))
    fill_w = int(bar_w * 0.78)
    draw.rounded_rectangle([260, 370, 260 + fill_w, 395], radius=12, fill=(124, 77, 255))
    
    # Risk level
    draw.text((260, 410), "Risk Level: Moderate", fill=(255, 200, 100), font=small_f)
    draw.text((600, 410), "5 stocks, 4 sectors", fill=(180, 180, 200), font=small_f)

    # Breakdown scores
    y = 440
    draw.text((80, y + 20), "Breakdown", fill=(200, 200, 240), font=sub_f)
    
    breakdowns = [
        ("Diversification", 72, (100, 200, 255)),
        ("Risk Management", 85, (124, 255, 180)),
        ("Quality", 80, (255, 215, 0)),
        ("Balance", 75, (255, 150, 100)),
    ]
    
    by = y + 70
    for label, score, color in breakdowns:
        draw.text((80, by), label, fill=(200, 200, 220), font=small_f)
        draw.text((900, by), f"{score}", fill=(255, 255, 255), font=small_f)
        bar_full = 600
        draw.rounded_rectangle([350, by + 5, 350 + bar_full, by + 22], radius=8, fill=(40, 40, 70))
        draw.rounded_rectangle([350, by + 5, 350 + int(bar_full * score / 100), by + 22],
                               radius=8, fill=color)
        by += 45

    # Suggestions
    y = by + 20
    draw_card(draw, 40, y, W - 80, 220)
    draw.text((70, y + 15), "Suggestions", fill=(200, 200, 240), font=sub_f)
    suggestions = [
        "Add more sectors for better diversification",
        "Consider adding defensive stocks (FMCG, Pharma)",
        "Portfolio is well balanced overall",
    ]
    sy = y + 60
    for sug in suggestions:
        draw.ellipse([80, sy + 5, 92, sy + 17], fill=(124, 77, 255))
        draw.text((105, sy), sug, fill=(200, 200, 220), font=small_f)
        sy += 40

    # Holdings section
    y = sy + 40
    draw.text((40, y), "Holdings", fill=(200, 200, 240), font=sub_f)
    y += 50
    
    holdings = [
        ("RELIANCE", "10 shares", "24,196.00", "+14.5%", True),
        ("TCS", "5 shares", "19,460.75", "+8.2%", True),
        ("HDFCBANK", "15 shares", "24,679.50", "-2.1%", False),
    ]
    
    for sym, qty, value, pnl, pos in holdings:
        draw_card(draw, 40, y, W - 80, 110)
        draw.text((70, y + 15), sym, fill=(255, 255, 255), font=body_f)
        draw.text((70, y + 55), qty, fill=(150, 150, 180), font=small_f)
        draw.text((650, y + 15), value, fill=(255, 255, 255), font=body_f)
        color = (124, 255, 180) if pos else (255, 100, 100)
        draw.text((650, y + 55), pnl, fill=color, font=small_f)
        y += 130

    img.save(os.path.join(OUTPUT_DIR, "screenshot_4_portfolio.png"), "PNG")
    print("Screenshot 4 (Portfolio) saved")


def draw_gradient_rect(draw, x, y, w, h, color1, color2):
    for row in range(h):
        r = int(color1[0] + (color2[0] - color1[0]) * row / h)
        g = int(color1[1] + (color2[1] - color1[1]) * row / h)
        b = int(color1[2] + (color2[2] - color1[2]) * row / h)
        draw.line([(x, y + row), (x + w, y + row)], fill=(r, g, b))


if __name__ == "__main__":
    screenshot_dashboard()
    screenshot_ai_assistant()
    screenshot_heatmap()
    screenshot_portfolio()
    print(f"\nAll screenshots saved to: {OUTPUT_DIR}")
