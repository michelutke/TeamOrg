#!/usr/bin/env python3
"""Generate TeamOrg Graphite-Cyan brand assets: Android adaptive vectors,
legacy mipmap PNGs, iOS 1024 icon, Play 512 + feature graphic."""
import io, math, os, subprocess, sys

REPO = "/Users/miggi/miggisrc/teamorg"
BRAND = os.path.join(REPO, "brand")
RES = os.path.join(REPO, "composeApp/src/androidMain/res")
IOS_ICON = os.path.join(REPO, "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png")

CY = "#64D8E8"
MINT = "#8CDCA0"
WHITE = "#E2E4E8"
GR_A, GR_B = "#2C313B", "#181C23"

MARK_RECTS = [(232, 232, 160, 160), (432, 232, 160, 160), (632, 232, 160, 160), (432, 432, 160, 360)]

def mark_svg_rects(scale=1.0, dx=0.0, dy=0.0, fill=CY):
    out = []
    for (x, y, w, h) in MARK_RECTS:
        out.append(f'<rect x="{x*scale+dx:.2f}" y="{y*scale+dy:.2f}" width="{w*scale:.2f}" '
                   f'height="{h*scale:.2f}" rx="{48*scale:.2f}" fill="{fill}"/>')
    return "\n".join(out)

def hero_icon_svg(size):
    s = size / 1024
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="{GR_A}"/><stop offset="1" stop-color="{GR_B}"/></linearGradient></defs>
<rect width="{size}" height="{size}" fill="url(#bg)"/>
{mark_svg_rects(scale=s)}
</svg>'''

def round_icon_svg(size):
    s = size / 1024
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">
<defs><linearGradient id="bg" x1="0" y1="0" x2="0" y2="1">
<stop offset="0" stop-color="{GR_A}"/><stop offset="1" stop-color="{GR_B}"/></linearGradient>
<clipPath id="c"><circle cx="{size/2}" cy="{size/2}" r="{size/2}"/></clipPath></defs>
<g clip-path="url(#c)"><rect width="{size}" height="{size}" fill="url(#bg)"/>
{mark_svg_rects(scale=s)}</g>
</svg>'''

def render(svg_text, out_path, w=None, h=None):
    import cairosvg
    kwargs = {}
    if w: kwargs["output_width"] = w
    if h: kwargs["output_height"] = h
    cairosvg.svg2png(bytestring=svg_text.encode(), write_to=out_path, **kwargs)
    print("wrote", out_path)

def rounded_rect_path(x, y, w, h, r):
    return (f"M{x+r:.3f},{y:.3f} L{x+w-r:.3f},{y:.3f} "
            f"A{r:.3f},{r:.3f} 0 0 1 {x+w:.3f},{y+r:.3f} L{x+w:.3f},{y+h-r:.3f} "
            f"A{r:.3f},{r:.3f} 0 0 1 {x+w-r:.3f},{y+h:.3f} L{x+r:.3f},{y+h:.3f} "
            f"A{r:.3f},{r:.3f} 0 0 1 {x:.3f},{y+h-r:.3f} L{x:.3f},{y+r:.3f} "
            f"A{r:.3f},{r:.3f} 0 0 1 {x+r:.3f},{y:.3f} Z")

def android_vector(fill_color, name):
    s = 108 / 1024
    paths = []
    for (x, y, w, h) in MARK_RECTS:
        d = rounded_rect_path(x*s, y*s, w*s, h*s, 48*s)
        paths.append(f'    <path android:fillColor="{fill_color}" android:pathData="{d}"/>')
    body = "\n".join(paths)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
{body}
</vector>
'''

def feature_graphic_svg():
    # 1024x500: P1 falling-blocks field (56px cells, deterministic) + lockup
    blocks = []
    def prand(a, b):
        n = math.sin(a * 127.1 + b * 311.7) * 43758.5453
        return n - math.floor(n)
    for iy in range(-1, 11):
        for ix in range(-1, 20):
            p = prand(ix, iy)
            if p < 0.18 or (300 < ix*56 < 900 and 120 < iy*56 < 360):
                continue  # keep center clear for lockup
            stagger = (iy % 2) * 28
            hh = 52 if p > 0.86 else 24
            c = MINT if p > 0.94 else (CY if p > 0.5 else WHITE)
            o = 0.10 + p*0.12 if c == WHITE else 0.15 + prand(iy, ix) * 0.45
            blocks.append(f'<rect x="{ix*56+16+stagger}" y="{iy*56+14}" width="24" height="{hh}" rx="8" '
                          f'fill="{c}" opacity="{o:.2f}"/>')
    blocksv = "\n".join(blocks)
    glyph = mark_svg_rects(scale=120/1024*1.0, dx=330-232*120/1024, dy=190-232*120/1024)
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500">
<defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="{GR_A}"/><stop offset="1" stop-color="#14171D"/></linearGradient></defs>
<rect width="1024" height="500" fill="url(#bg)"/>
<g transform="rotate(-8 512 250)">{blocksv}</g>
<g>{glyph}</g>
<text x="470" y="252" font-family="Helvetica, Arial, sans-serif" font-weight="bold" font-size="72"
 letter-spacing="-2"><tspan fill="{WHITE}">Team</tspan><tspan fill="{CY}">Org</tspan></text>
<text x="474" y="300" font-family="Helvetica, Arial, sans-serif" font-size="26" fill="#9AA3AD">Dein Team. Ein Plan.</text>
</svg>'''

os.makedirs(BRAND, exist_ok=True)
os.makedirs(os.path.join(BRAND, "store"), exist_ok=True)

# masters
with open(os.path.join(BRAND, "icon-master-1024.svg"), "w") as f:
    f.write(hero_icon_svg(1024))
with open(os.path.join(BRAND, "store", "feature-graphic.svg"), "w") as f:
    f.write(feature_graphic_svg())

# iOS 1024 (no alpha)
render(hero_icon_svg(1024), IOS_ICON, 1024, 1024)

# Play 512
render(hero_icon_svg(1024), os.path.join(BRAND, "store", "play-icon-512.png"), 512, 512)
# Feature graphic 1024x500
render(feature_graphic_svg(), os.path.join(BRAND, "store", "feature-graphic-1024x500.png"), 1024, 500)

# Legacy mipmaps
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for d, px in DENSITIES.items():
    render(hero_icon_svg(1024), os.path.join(RES, f"mipmap-{d}", "ic_launcher.png"), px, px)
    render(round_icon_svg(1024), os.path.join(RES, f"mipmap-{d}", "ic_launcher_round.png"), px, px)

# Android adaptive vectors
with open(os.path.join(RES, "drawable-v24", "ic_launcher_foreground.xml"), "w") as f:
    f.write(android_vector(CY, "fg"))
with open(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), "w") as f:
    f.write(android_vector("#FFFFFFFF", "mono"))
with open(os.path.join(RES, "drawable", "ic_launcher_background.xml"), "w") as f:
    f.write('''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr xmlns:aapt="http://schemas.android.com/aapt" name="android:fillColor">
            <gradient
                android:startX="54" android:startY="0"
                android:endX="54" android:endY="108"
                android:type="linear">
                <item android:offset="0.0" android:color="#FF2C313B"/>
                <item android:offset="1.0" android:color="#FF181C23"/>
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')

for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
    p = os.path.join(RES, "mipmap-anydpi-v26", name)
    with open(p, "w") as f:
        f.write('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
''')
    print("wrote", p)

print("DONE")
