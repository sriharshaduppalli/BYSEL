"""Generate Play Store graphics for BYSEL app."""
from PIL import Image, ImageDraw, ImageFont
import math
import os

OUTPUT_DIR = r"c:\Users\sriha\Desktop\Applications\BYSEL\BYSEL\playstore-graphics"
os.makedirs(OUTPUT_DIR, exist_ok=True)


def create_app_icon():
    """Create 512x512 app icon with gradient background and BYSEL text."""
    size = 512
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Draw gradient background (dark blue to purple)
    for y in range(size):
        r = int(26 + (50 - 26) * y / size)
        g = int(35 + (20 - 35) * y / size)
        b = int(126 + (200 - 126) * y / size)
        draw.line([(0, y), (size, y)], fill=(r, g, b, 255))

    # Add rounded rectangle mask
    mask = Image.new("L", (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    radius = 80
    mask_draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    
    # Apply mask
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(img, mask=mask)
    draw = ImageDraw.Draw(result)

    # Draw chart line (stylized stock chart going up)
    chart_points = [
        (80, 340), (120, 310), (160, 330), (200, 280),
        (240, 300), (280, 250), (320, 220), (360, 240),
        (400, 180), (432, 160)
    ]
    # Draw glow effect
    for offset in range(6, 0, -1):
        alpha = int(40 - offset * 6)
        for i in range(len(chart_points) - 1):
            draw.line([chart_points[i], chart_points[i + 1]],
                      fill=(124, 77, 255, alpha), width=offset * 3)

    # Draw main chart line
    for i in range(len(chart_points) - 1):
        draw.line([chart_points[i], chart_points[i + 1]],
                  fill=(124, 255, 180, 255), width=4)

    # Draw small dots at chart points
    for point in chart_points:
        x, y = point
        draw.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(124, 255, 180, 255))

    # Draw upward arrow at the end
    arrow_tip = chart_points[-1]
    ax, ay = arrow_tip
    draw.polygon([(ax, ay - 20), (ax - 12, ay), (ax + 12, ay)],
                 fill=(124, 255, 180, 255))

    # Draw "B" letter large and centered-ish
    try:
        font_large = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 160)
    except:
        font_large = ImageFont.load_default()

    # Draw "B" with slight shadow
    draw.text((165, 50), "B", fill=(0, 0, 0, 80), font=font_large)
    draw.text((162, 47), "B", fill=(255, 255, 255, 255), font=font_large)

    # Draw "BYSEL" text at bottom
    try:
        font_small = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 52)
    except:
        font_small = ImageFont.load_default()

    text = "BYSEL"
    bbox = draw.textbbox((0, 0), text, font=font_small)
    tw = bbox[2] - bbox[0]
    tx = (size - tw) // 2
    draw.text((tx + 2, 392), text, fill=(0, 0, 0, 80), font=font_small)
    draw.text((tx, 390), text, fill=(255, 255, 255, 255), font=font_small)

    # Save
    output_path = os.path.join(OUTPUT_DIR, "app_icon_512.png")
    result.save(output_path, "PNG")
    print(f"App icon saved: {output_path}")
    return output_path


def create_feature_graphic():
    """Create 1024x500 feature graphic."""
    w, h = 1024, 500
    img = Image.new("RGB", (w, h))
    draw = ImageDraw.Draw(img)

    # Draw gradient background (dark indigo to purple)
    for y in range(h):
        r = int(26 + (80 - 26) * y / h)
        g = int(35 + (20 - 35) * y / h)
        b = int(126 + (220 - 126) * y / h)
        draw.line([(0, y), (w, y)], fill=(r, g, b))

    # Draw decorative circles (subtle)
    for cx, cy, cr in [(800, 100, 120), (900, 400, 80), (150, 420, 60), (50, 100, 40)]:
        for offset in range(cr, 0, -1):
            alpha_val = max(0, int(15 * offset / cr))
            color = (124, 77, 255)
            draw.ellipse([cx - offset, cy - offset, cx + offset, cy + offset],
                         outline=(*color,), width=1)

    # Draw stock chart line across the graphic
    chart_points = [
        (50, 380), (100, 350), (150, 370), (220, 300),
        (280, 320), (350, 260), (420, 280), (500, 220),
        (580, 240), (650, 180), (720, 200), (790, 150),
        (860, 170), (920, 120), (970, 100)
    ]

    # Draw glow
    for offset in range(8, 0, -1):
        alpha = int(30 - offset * 3)
        glow_color = (124, 255, 180)
        for i in range(len(chart_points) - 1):
            draw.line([chart_points[i], chart_points[i + 1]],
                      fill=glow_color, width=offset * 2)

    # Draw main line
    for i in range(len(chart_points) - 1):
        draw.line([chart_points[i], chart_points[i + 1]],
                  fill=(124, 255, 180), width=3)

    # Fill area under chart with gradient
    for i in range(len(chart_points) - 1):
        x1, y1 = chart_points[i]
        x2, y2 = chart_points[i + 1]
        for x in range(int(x1), int(x2)):
            t = (x - x1) / (x2 - x1) if x2 != x1 else 0
            y_line = y1 + t * (y2 - y1)
            for y in range(int(y_line), h):
                alpha = max(0, int(25 * (1 - (y - y_line) / (h - y_line))))
                if alpha > 0:
                    orig = img.getpixel((x, y))
                    blended = tuple(min(255, c + alpha) for c in orig[:3])
                    draw.point((x, y), fill=blended)

    # Draw "BYSEL" title
    try:
        font_title = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 90)
        font_sub = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 28)
        font_tag = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 22)
    except:
        font_title = ImageFont.load_default()
        font_sub = ImageFont.load_default()
        font_tag = ImageFont.load_default()

    # Title with shadow
    title = "BYSEL"
    bbox = draw.textbbox((0, 0), title, font=font_title)
    tw = bbox[2] - bbox[0]
    tx = (w - tw) // 2
    draw.text((tx + 3, 123), title, fill=(0, 0, 0, ), font=font_title)
    draw.text((tx, 120), title, fill=(255, 255, 255), font=font_title)

    # Subtitle
    subtitle = "AI-Powered Stock Trading & Analysis"
    bbox2 = draw.textbbox((0, 0), subtitle, font=font_sub)
    tw2 = bbox2[2] - bbox2[0]
    tx2 = (w - tw2) // 2
    draw.text((tx2, 225), subtitle, fill=(200, 200, 255), font=font_sub)

    # Feature tags
    tags = ["🤖 AI Assistant", "📊 Predictions", "💯 Health Score", "🌡️ Heatmap"]
    tag_y = 290
    total_width = sum(draw.textbbox((0, 0), t, font=font_tag)[2] - draw.textbbox((0, 0), t, font=font_tag)[0] for t in tags) + 30 * (len(tags) - 1)
    start_x = (w - total_width) // 2

    for tag in tags:
        bbox_t = draw.textbbox((0, 0), tag, font=font_tag)
        tag_w = bbox_t[2] - bbox_t[0]
        tag_h = bbox_t[3] - bbox_t[1]
        # Draw tag pill background
        pill_padding = 12
        draw.rounded_rectangle(
            [start_x - pill_padding, tag_y - 6,
             start_x + tag_w + pill_padding, tag_y + tag_h + 10],
            radius=16,
            fill=(124, 77, 255)
        )
        draw.text((start_x, tag_y), tag, fill=(255, 255, 255), font=font_tag)
        start_x += tag_w + 30 + pill_padding * 2

    # Bottom text
    try:
        font_bottom = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 18)
    except:
        font_bottom = ImageFont.load_default()
    bottom_text = "363+ Indian Stocks • Live Prices • Paper Trading • 5 Themes"
    bbox3 = draw.textbbox((0, 0), bottom_text, font=font_bottom)
    tw3 = bbox3[2] - bbox3[0]
    tx3 = (w - tw3) // 2
    draw.text((tx3, 460), bottom_text, fill=(180, 180, 220), font=font_bottom)

    output_path = os.path.join(OUTPUT_DIR, "feature_graphic_1024x500.png")
    img.save(output_path, "PNG")
    print(f"Feature graphic saved: {output_path}")
    return output_path


if __name__ == "__main__":
    create_app_icon()
    create_feature_graphic()
    print(f"\nGraphics saved to: {OUTPUT_DIR}")
    print("Upload these to Google Play Console Store Listing")
