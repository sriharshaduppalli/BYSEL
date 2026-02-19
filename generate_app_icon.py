"""Generate BYSEL app launcher icons for all Android mipmap densities."""
from PIL import Image, ImageDraw, ImageFont
import os

def create_bysel_icon(size):
    """Create a professional BYSEL stock trading app icon."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Background - deep purple gradient effect (solid for simplicity)
    # Rounded-rect background
    margin = int(size * 0.05)
    radius = int(size * 0.2)
    
    # Draw rounded rectangle background
    draw.rounded_rectangle(
        [margin, margin, size - margin, size - margin],
        radius=radius,
        fill=(13, 13, 30, 255)  # Dark navy background
    )
    
    # Draw accent border glow
    draw.rounded_rectangle(
        [margin + 2, margin + 2, size - margin - 2, size - margin - 2],
        radius=radius - 2,
        outline=(124, 77, 255, 200),  # Purple glow
        width=max(2, int(size * 0.015))
    )
    
    # Draw a mini stock chart line (uptrend)
    chart_points = [
        (size * 0.15, size * 0.65),
        (size * 0.25, size * 0.60),
        (size * 0.35, size * 0.68),
        (size * 0.45, size * 0.55),
        (size * 0.55, size * 0.58),
        (size * 0.65, size * 0.45),
        (size * 0.75, size * 0.48),
        (size * 0.85, size * 0.35),
    ]
    
    # Draw chart line
    for i in range(len(chart_points) - 1):
        draw.line(
            [chart_points[i], chart_points[i + 1]],
            fill=(0, 230, 118, 255),  # Green line
            width=max(2, int(size * 0.025))
        )
    
    # Draw upward arrow at the end of the chart
    arrow_x = size * 0.85
    arrow_y = size * 0.35
    arrow_size = size * 0.06
    draw.polygon([
        (arrow_x, arrow_y - arrow_size),
        (arrow_x - arrow_size * 0.7, arrow_y + arrow_size * 0.3),
        (arrow_x + arrow_size * 0.7, arrow_y + arrow_size * 0.3),
    ], fill=(0, 230, 118, 255))
    
    # Draw "B" letter - bold, centered upper area
    font_size = int(size * 0.38)
    try:
        font = ImageFont.truetype("arialbd.ttf", font_size)
    except:
        try:
            font = ImageFont.truetype("arial.ttf", font_size)
        except:
            font = ImageFont.load_default()
    
    text = "B"
    bbox = draw.textbbox((0, 0), text, font=font)
    text_width = bbox[2] - bbox[0]
    text_height = bbox[3] - bbox[1]
    text_x = (size - text_width) // 2
    text_y = int(size * 0.12)
    
    # Text shadow
    draw.text((text_x + 2, text_y + 2), text, fill=(0, 0, 0, 150), font=font)
    # Main text - white with slight purple tint
    draw.text((text_x, text_y), text, fill=(255, 255, 255, 255), font=font)
    
    # Draw "YSEL" smaller below the B  
    small_font_size = int(size * 0.13)
    try:
        small_font = ImageFont.truetype("arialbd.ttf", small_font_size)
    except:
        try:
            small_font = ImageFont.truetype("arial.ttf", small_font_size)
        except:
            small_font = ImageFont.load_default()
    
    small_text = "YSEL"
    bbox2 = draw.textbbox((0, 0), small_text, font=small_font)
    st_width = bbox2[2] - bbox2[0]
    st_x = (size - st_width) // 2
    st_y = text_y + text_height + int(size * 0.02)
    
    draw.text((st_x, st_y), small_text, fill=(124, 77, 255, 255), font=small_font)
    
    # Small purple dots for decoration
    dot_y = size * 0.82
    for i in range(3):
        dot_x = size * 0.4 + i * size * 0.1
        dot_r = max(2, int(size * 0.015))
        draw.ellipse([dot_x - dot_r, dot_y - dot_r, dot_x + dot_r, dot_y + dot_r],
                     fill=(124, 77, 255, 180))
    
    return img


def create_adaptive_foreground(size):
    """Create foreground for adaptive icon (108dp safe zone)."""
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    center = size // 2
    inner_size = int(size * 0.55)  # Content in the safe zone (66/108)
    
    # Stock chart line
    chart_points = [
        (center - inner_size * 0.4, center + inner_size * 0.15),
        (center - inner_size * 0.25, center + inner_size * 0.08),
        (center - inner_size * 0.1, center + inner_size * 0.18),
        (center + inner_size * 0.05, center + inner_size * 0.02),
        (center + inner_size * 0.15, center + inner_size * 0.06),
        (center + inner_size * 0.3, center - inner_size * 0.1),
        (center + inner_size * 0.4, center - inner_size * 0.05),
    ]
    
    for i in range(len(chart_points) - 1):
        draw.line(
            [chart_points[i], chart_points[i + 1]],
            fill=(0, 230, 118, 255),
            width=max(3, int(size * 0.02))
        )
    
    # "B" letter
    font_size = int(inner_size * 0.65)
    try:
        font = ImageFont.truetype("arialbd.ttf", font_size)
    except:
        try:
            font = ImageFont.truetype("arial.ttf", font_size)
        except:
            font = ImageFont.load_default()
    
    text = "B"
    bbox = draw.textbbox((0, 0), text, font=font)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    tx = center - tw // 2
    ty = center - th // 2 - int(inner_size * 0.12)
    
    draw.text((tx + 2, ty + 2), text, fill=(0, 0, 0, 100), font=font)
    draw.text((tx, ty), text, fill=(255, 255, 255, 255), font=font)
    
    # "YSEL" below
    sf_size = int(inner_size * 0.18)
    try:
        small_font = ImageFont.truetype("arialbd.ttf", sf_size)
    except:
        try:
            small_font = ImageFont.truetype("arial.ttf", sf_size)
        except:
            small_font = ImageFont.load_default()
    
    st = "YSEL"
    bbox2 = draw.textbbox((0, 0), st, font=small_font)
    stw = bbox2[2] - bbox2[0]
    stx = center - stw // 2
    sty = ty + th + int(inner_size * 0.02)
    draw.text((stx, sty), st, fill=(124, 77, 255, 255), font=small_font)
    
    return img


# Mipmap density sizes
DENSITIES = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

ADAPTIVE_DENSITIES = {
    'mipmap-mdpi': 108,
    'mipmap-hdpi': 162,
    'mipmap-xhdpi': 216,
    'mipmap-xxhdpi': 324,
    'mipmap-xxxhdpi': 432,
}

base_path = r"c:\Users\sriha\Desktop\Applications\BYSEL\BYSEL\android\app\src\main\res"

print("Generating BYSEL launcher icons...")

# Generate regular launcher icons
for density, size in DENSITIES.items():
    folder = os.path.join(base_path, density)
    os.makedirs(folder, exist_ok=True)
    
    icon = create_bysel_icon(size)
    icon_path = os.path.join(folder, "ic_launcher.png")
    icon.save(icon_path, "PNG")
    
    # Also save round version
    round_icon = icon.copy()
    mask = Image.new('L', (size, size), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.ellipse([0, 0, size, size], fill=255)
    round_icon.putalpha(mask)
    round_path = os.path.join(folder, "ic_launcher_round.png")
    round_icon.save(round_path, "PNG")
    
    print(f"  {density}: {size}x{size}px -> {icon_path}")

# Generate adaptive icon foregrounds
for density, size in ADAPTIVE_DENSITIES.items():
    folder = os.path.join(base_path, density)
    os.makedirs(folder, exist_ok=True)
    
    fg = create_adaptive_foreground(size)
    fg_path = os.path.join(folder, "ic_launcher_foreground.png")
    fg.save(fg_path, "PNG")
    print(f"  {density} foreground: {size}x{size}px")

# Generate Play Store icon (512x512)
store_icon = create_bysel_icon(512)
store_path = os.path.join(base_path, "..", "..", "..", "..", "playstore_icon.png")
store_icon.save(os.path.abspath(store_path), "PNG")
print(f"\nPlay Store icon: 512x512px -> playstore_icon.png")

print("\nDone! All icons generated.")
