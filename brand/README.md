# TeamOrg Brand Assets

Graphite Cyan identity: graphite `#22262E` ground, cyan `#64D8E8` mark (dark surfaces), teal `#0E6577` on light surfaces — cyan never sits on white.

## The mark

"T" whose crossbar is the roster: three member blocks + stem. Unit system on a 560×560 box — block `u` = 160, gap = ¼u, corner radius = 0.3u, stem = 2u+¼u. Rects (x, y, w, h): (0,0,160,160), (200,0,160,160), (400,0,160,160), (200,200,160,360).

## Files

| Path | Purpose |
|---|---|
| `icon-master-1024.svg` | icon master (gradient graphite ground, cyan mark) |
| `store/play-icon-512.png` | Play Store listing icon |
| `store/feature-graphic{,.svg,-1024x500.png}` | Play feature graphic (P1 pattern + lockup) |
| `merch/badge-*` | ring badge, single-color separations (cyan/graphite, ink, white) |
| `tools/gen_brand_assets.py` | regenerates icon master, iOS 1024 icon, Play assets, Android legacy mipmaps, adaptive-icon vectors |
| `tools/gen_badge.py` | regenerates the merch badge variants |

Regenerate (requires `pip install cairosvg`):

```bash
python3 brand/tools/gen_brand_assets.py
python3 brand/tools/gen_badge.py
```

## Where the brand lives in code

- App theme: `composeApp/src/commonMain/kotlin/ch/teamorg/ui/theme/Color.kt` (light+dark schemes, Material You opt-in via `platformDynamicColorScheme`)
- Brand components: `TeamorgMark`, `TeamorgLoader` (roster-wave spinner), `TeamorgSplash` (assemble animation) in `ui/components/`
- Android icons: `composeApp/src/androidMain/res/` (adaptive vectors incl. monochrome layer + legacy mipmaps)
- iOS icon: `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/`
- Web: `landing/src/lib/components/LogoMark.svelte`, favicons in `landing/static/` + `admin/static/`, palettes in both `tailwind.config.ts`
- Figma: "Logo Concept — Graphite Cyan" page in the TeamOrg file (variables rethemed for Light + Dark modes)
