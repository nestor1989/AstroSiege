#!/usr/bin/env python3
"""Generates all launcher/notification icon assets for the Astro Siege Android project.
Run once from the project root: `python3 gen_icons.py`. Not needed again unless the
icon design changes — Android Studio's Image Asset tool can also regenerate these from
play_store_icon_512.png if you want to redesign it later."""
import math
import os

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(ROOT, "app", "src", "main", "res")

VOID_DEEP = (5, 6, 17, 255)
LASER = (79, 227, 255, 255)
SCORE_GREEN = (198, 255, 107, 255)
MARQUEE_ORANGE = (255, 107, 74, 255)


def draw_ship(size, pad_ratio=0.5):
    """Draws the game's interceptor-ship silhouette (same triangular shape used in the
    game's own canvas rendering) centered on a transparent square of `size`x`size`,
    occupying pad_ratio of the canvas, with a soft cyan glow behind it."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    w = size * pad_ratio
    h = w * 0.78
    cx, cy = size / 2, size / 2 + h * 0.04

    def pt(x, y):
        return (cx + x, cy + y)

    hull = [
        pt(0, -h / 2),
        pt(w * 0.18, -h * 0.05),
        pt(w / 2, h / 2),
        pt(w * 0.12, h * 0.28),
        pt(0, h / 2 - h * 0.05),
        pt(-w * 0.12, h * 0.28),
        pt(-w / 2, h / 2),
        pt(-w * 0.18, -h * 0.05),
    ]

    # Glow: draw a blurred, larger, softly-colored copy behind the hull.
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.polygon(hull, fill=(LASER[0], LASER[1], LASER[2], 140))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=size * 0.05))
    img = Image.alpha_composite(img, glow)
    draw = ImageDraw.Draw(img)

    draw.polygon(hull, fill=LASER)

    # cockpit highlight
    ellipse_w, ellipse_h = w * 0.18, h * 0.32
    ex, ey = pt(0, -h * 0.08)
    draw.ellipse(
        [ex - ellipse_w / 2, ey - ellipse_h / 2, ex + ellipse_w / 2, ey + ellipse_h / 2],
        fill=(255, 255, 255, 140),
    )

    # accent stripe
    stripe_y = cy + h * 0.08
    draw.line(
        [(cx - w * 0.3, stripe_y), (cx + w * 0.3, stripe_y)],
        fill=SCORE_GREEN,
        width=max(1, int(size * 0.012)),
    )

    return img


def flat_square_icon(size):
    """Full legacy launcher icon: void-colored rounded square background + ship."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = Image.new("RGBA", (size, size), VOID_DEEP)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=size * 0.18, fill=255
    )
    img.paste(bg, (0, 0), mask)
    ship = draw_ship(size, pad_ratio=0.62)
    img = Image.alpha_composite(img, ship)
    return img


def round_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bg = Image.new("RGBA", (size, size), VOID_DEEP)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    img.paste(bg, (0, 0), mask)
    ship = draw_ship(size, pad_ratio=0.56)
    img = Image.alpha_composite(img, ship)
    return img


def foreground_layer(size):
    """Adaptive icon foreground: transparent background, ship kept inside the ~66%
    safe zone so it survives circle/square/squircle masking on different launchers."""
    return draw_ship(size, pad_ratio=0.40)


def notification_icon(size):
    """Flat white silhouette on transparent, per Android status-bar icon guidelines."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    w = size * 0.66
    h = w * 0.9
    cx, cy = size / 2, size / 2
    hull = [
        (cx, cy - h / 2),
        (cx + w * 0.18, cy - h * 0.05),
        (cx + w / 2, cy + h / 2),
        (cx, cy + h * 0.32),
        (cx - w / 2, cy + h / 2),
        (cx - w * 0.18, cy - h * 0.05),
    ]
    draw.polygon(hull, fill=(255, 255, 255, 255))
    return img


LAUNCHER_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
FOREGROUND_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
NOTIF_SIZES = {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}

for density, size in LAUNCHER_SIZES.items():
    d = os.path.join(RES, f"mipmap-{density}")
    os.makedirs(d, exist_ok=True)
    flat_square_icon(size).save(os.path.join(d, "ic_launcher.png"))
    round_icon(size).save(os.path.join(d, "ic_launcher_round.png"))

for density, size in FOREGROUND_SIZES.items():
    d = os.path.join(RES, f"mipmap-{density}")
    os.makedirs(d, exist_ok=True)
    foreground_layer(size).save(os.path.join(d, "ic_launcher_foreground.png"))

for density, size in NOTIF_SIZES.items():
    d = os.path.join(RES, f"drawable-{density}")
    os.makedirs(d, exist_ok=True)
    notification_icon(size).save(os.path.join(d, "ic_notification.png"))

# Play Store listing icon (512x512, not referenced by the app itself).
flat_square_icon(512).resize((512, 512), Image.LANCZOS).save(
    os.path.join(ROOT, "play_store_icon_512.png")
)

print("Icons generated.")
