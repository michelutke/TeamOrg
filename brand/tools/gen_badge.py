import cairosvg, os
CY, GR = "#64D8E8", "#181C23"
def badge(fg, bg, transparent=False):
    bgel = "" if transparent else f'<circle cx="512" cy="512" r="512" fill="{bg}"/>'
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs><path id="top" d="M512 512 m -336 0 a 336 336 0 1 1 672 0 a 336 336 0 1 1 -672 0"/>
<path id="bot" d="M512 512 m -336 0 a 336 336 0 1 0 672 0 a 336 336 0 1 0 -672 0"/></defs>
{bgel}
<circle cx="512" cy="512" r="466" fill="none" stroke="{fg}" stroke-width="17"/>
<circle cx="512" cy="512" r="404" fill="none" stroke="{fg}" stroke-width="6"/>
<g fill="{fg}">
<rect x="376" y="356" width="78" height="78" rx="23.4"/>
<rect x="474" y="356" width="78" height="78" rx="23.4"/>
<rect x="572" y="356" width="78" height="78" rx="23.4"/>
<rect x="474" y="454" width="78" height="176" rx="23.4"/>
</g>
<text font-family="Helvetica, Arial, sans-serif" font-weight="bold" font-size="72" letter-spacing="14" fill="{fg}">
<textPath href="#top" startOffset="21.8%" text-anchor="middle">TEAMORG</textPath></text>
<text font-family="Helvetica, Arial, sans-serif" font-weight="bold" font-size="66" letter-spacing="12" fill="{fg}">
<textPath href="#bot" startOffset="23%" text-anchor="middle">EST. 2026</textPath></text>
<circle cx="176" cy="512" r="14" fill="{fg}"/><circle cx="848" cy="512" r="14" fill="{fg}"/>
</svg>'''
os.makedirs("/Users/miggi/miggisrc/teamorg/brand/merch", exist_ok=True)
for name, fg, bg, tr in [("badge-cyan-on-graphite", CY, GR, False), ("badge-ink-on-transparent", "#181C1F", "", True), ("badge-white-on-transparent", "#FFFFFF", "", True)]:
    svg = badge(fg, bg, tr)
    p = f"/Users/miggi/miggisrc/teamorg/brand/merch/{name}"
    open(p + ".svg", "w").write(svg)
    cairosvg.svg2png(bytestring=svg.encode(), write_to=p + ".png", output_width=1024, output_height=1024)
    print(name)
