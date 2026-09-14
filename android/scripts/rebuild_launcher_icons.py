"""Rebuild launcher, round, adaptive, and splash icons from the BYSEL mark.

Android 8+ clips adaptive icons to a circle/squircle using only the inner
~66% (72/108dp) safe zone. A full-bleed rounded logo at 90% of the canvas
is cropped after install — users see a fragment of B/chart/BYSEL.

Foreground artwork is therefore centered inside that safe zone on a
transparent canvas. The adaptive background is the brand indigo so the
mask shows one complete logo.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ANDROID = Path(__file__).resolve().parents[1]
RES = ANDROID / "app" / "src" / "main" / "res"
PLAYSTORE = ANDROID.parent / "playstore-graphics"
PLAYSTORE.mkdir(parents=True, exist_ok=True)

BRAND = (38, 27, 163, 255)  # #261BA3
CHART = (180, 255, 220, 255)
WHITE = (255, 255, 255, 255)
SHADOW = (0, 0, 0, 70)

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}
LEGACY_DP = 48
ADAPTIVE_DP = 108
# 72/108 = 0.667 official safe zone. Stay inside it so circular launchers
# still show B, the chart, and the BYSEL wordmark.
SAFE_ZONE = 0.62

FONT_CANDIDATES = [
    Path(r"C:\Windows\Fonts\arialbd.ttf"),
    Path(r"C:\Windows\Fonts\ARIALBD.TTF"),
    Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"),
]


def _font(size: int) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    for path in FONT_CANDIDATES:
        if path.exists():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def _draw_mark(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    """B + uptrend + BYSEL inside [left, top, right, bottom]."""
    left, top, right, bottom = box
    width = right - left
    height = bottom - top
    cx = (left + right) / 2

    font_b = _font(max(12, int(height * 0.36)))
    font_word = _font(max(8, int(height * 0.14)))

    bbox_b = draw.textbbox((0, 0), "B", font=font_b)
    bw, bh = bbox_b[2] - bbox_b[0], bbox_b[3] - bbox_b[1]
    bx = cx - bw / 2 - bbox_b[0]
    by = top + height * 0.08 - bbox_b[1]
    draw.text((bx + 2, by + 2), "B", font=font_b, fill=SHADOW)
    draw.text((bx, by), "B", font=font_b, fill=WHITE)

    chart = [
        (left + width * 0.12, top + height * 0.68),
        (left + width * 0.28, top + height * 0.60),
        (left + width * 0.42, top + height * 0.64),
        (left + width * 0.56, top + height * 0.50),
        (left + width * 0.70, top + height * 0.46),
        (left + width * 0.84, top + height * 0.32),
    ]
    width_px = max(3, int(width * 0.035))
    draw.line(chart, fill=CHART, width=width_px, joint="curve")
    ax, ay = chart[-1]
    arrow = max(6, int(width * 0.055))
    draw.polygon(
        [(ax, ay - arrow), (ax - arrow * 0.7, ay + arrow * 0.25), (ax + arrow * 0.7, ay + arrow * 0.25)],
        fill=CHART,
    )

    bbox_w = draw.textbbox((0, 0), "BYSEL", font=font_word)
    ww = bbox_w[2] - bbox_w[0]
    wx = cx - ww / 2 - bbox_w[0]
    wy = top + height * 0.78 - bbox_w[1]
    draw.text((wx + 1, wy + 1), "BYSEL", font=font_word, fill=SHADOW)
    draw.text((wx, wy), "BYSEL", font=font_word, fill=WHITE)


def _branded_tile(size: int, circular: bool = False) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    inset = max(1, int(size * 0.02))
    if circular:
        draw.ellipse([inset, inset, size - 1 - inset, size - 1 - inset], fill=BRAND)
    else:
        radius = int(size * 0.22)
        draw.rounded_rectangle(
            [inset, inset, size - 1 - inset, size - 1 - inset],
            radius=radius,
            fill=BRAND,
        )
    pad = int(size * 0.10)
    _draw_mark(draw, (pad, pad, size - pad, size - pad))
    return img


def _adaptive_foreground(size: int) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    inner = max(8, int(size * SAFE_ZONE))
    origin = (size - inner) // 2
    _draw_mark(draw, (origin, origin, origin + inner, origin + inner))
    return img


def main() -> None:
    playstore = _branded_tile(512)
    playstore_path = PLAYSTORE / "app_icon_512.png"
    playstore.save(playstore_path, "PNG")
    print(f"playstore: {playstore_path}")

    for name, scale in DENSITIES.items():
        folder = RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)

        legacy = int(LEGACY_DP * scale)
        _branded_tile(legacy).save(folder / "ic_launcher.png", "PNG")
        _branded_tile(legacy, circular=True).save(folder / "ic_launcher_round.png", "PNG")

        canvas = int(ADAPTIVE_DP * scale)
        _adaptive_foreground(canvas).save(folder / "ic_launcher_foreground.png", "PNG")
        print(f"{name}: launcher {legacy} foreground {canvas}")


if __name__ == "__main__":
    main()
