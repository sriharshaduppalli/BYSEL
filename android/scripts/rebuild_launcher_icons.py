"""Rebuild adaptive launcher layers from the Play Store 512 icon."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

ANDROID = Path(__file__).resolve().parents[1]
SOURCE = ANDROID.parent / "playstore-graphics" / "app_icon_512.png"
RES = ANDROID / "app" / "src" / "main" / "res"

# 108dp adaptive canvas per density. Logo fill is large enough that a
# circular launcher mask still shows the original B / chart / BYSEL mark.
DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}
LEGACY_DP = 48
ADAPTIVE_DP = 108
LOGO_FILL = 0.90


def _fit(src: Image.Image, box: int) -> Image.Image:
    fitted = src.copy()
    fitted.thumbnail((box, box), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (box, box), (0, 0, 0, 0))
    out.paste(fitted, ((box - fitted.width) // 2, (box - fitted.height) // 2), fitted)
    return out


def _circle(src: Image.Image) -> Image.Image:
    size = src.size[0]
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(src, (0, 0), mask)
    return out


def main() -> None:
    src = Image.open(SOURCE).convert("RGBA")
    for name, scale in DENSITIES.items():
        folder = RES / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)

        legacy = _fit(src, int(LEGACY_DP * scale))
        legacy.save(folder / "ic_launcher.png", "PNG")
        _circle(legacy).save(folder / "ic_launcher_round.png", "PNG")

        canvas = int(ADAPTIVE_DP * scale)
        logo = _fit(src, max(1, int(canvas * LOGO_FILL)))
        adaptive = Image.new("RGBA", (canvas, canvas), (0, 0, 0, 0))
        adaptive.paste(logo, ((canvas - logo.width) // 2, (canvas - logo.height) // 2), logo)
        adaptive.save(folder / "ic_launcher_foreground.png", "PNG")
        print(f"{name}: launcher {legacy.size[0]} foreground {canvas}")


if __name__ == "__main__":
    main()
