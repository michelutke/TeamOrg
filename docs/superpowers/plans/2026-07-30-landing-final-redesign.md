# Landing Final Redesign (Figma "Landing FINAL — Dark/Light") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild teamorg.ch (landing/) to match the approved Figma frames "Landing FINAL — Dark" + "Landing FINAL — Light" (file iKcGJfgxUxMi2AnE9o4BAL, section 60876:173): unified per-scheme background, light/dark theme, exact P2 T-tessellation in the hero with smooth fade-out, trimmed open pricing (no card), Tribüne outline pattern fading into contact and continuing seamlessly through the footer.

**Architecture:** Tailwind color tokens become CSS custom properties defined per scheme in `app.css` (`:root` = light, `[data-theme='dark']` + `prefers-color-scheme` fallback = dark). Patterns are two Svelte SVG components (`TessellationPattern` = exact P2 lattice, `RosterPattern` gains an outline/uniform mode). Contact + Footer share one wrapper with a single pattern layer so the pattern runs seamlessly across both.

**Tech Stack:** SvelteKit (Svelte 5 runes), Tailwind 4, no test framework in `landing/` — verification is `npm run check` + `playwright-cli` visual checks against `vite dev`.

## Global Constraints

- All copy stays bilingual: every string change edits BOTH `de` and `en` in `landing/src/lib/i18n/index.ts` and the `Dict` type.
- Scheme values (verbatim from Figma finals):
  - Light: bg `#F7F9FA`, panel `#FFFFFF`, on-surface `#181C1F`, body `#40484C`, accent `#0E6577`, on-accent `#FFFFFF`, pattern-a `#0E6577`, pattern-b `#9DB4BA`, placeholder `#5A646C`.
  - Dark: bg `#101417`, panel `#1B2124`, on-surface `#F4F7F9`, body `#BEC8CF`, accent `#64D8E8`, on-accent `#001F26`, pattern-a `#64D8E8`, pattern-b `#E2E4E8`, placeholder `#96A0A5`.
- Tribüne pattern (contact/footer): 20px squares, rx 6, x-pitch 48, y-pitch 52, odd rows offset x+24, **outline only** stroke-width 2.5, **uniform opacity 0.18**, row colors alternate pattern-a/pattern-b.
- Tessellation (hero): T-unit 64×64 = three 18×18 squares at x 0/23/46 y 0 + stem 18×41 at (23,23), stroke 2.5, rx 5.5; lattice band A `up(6+144k, 6+144r)` `down(142+144k, 70+144r)`, band B `up(78+144k, 78+144r)` `down(70+144k, 142+144r)`; opacity cycle `[0.55,0.3,0.4,0.5,0.35,0.45]` × 0.35, color pattern-a.
- Patterns never sit un-faded behind text: hero fades out bottom 55%; contact fades in top 78%; footer content sits on a 62% scrim.
- `cd landing && npm run check` must pass before every commit. Never push without it (repo rule).
- No `Co-Authored-By`/AI hints in commits. Frequent commits, one per task.

---

### Task 1: Theme tokens + dark scheme plumbing

**Files:**
- Modify: `landing/tailwind.config.ts` (colors → `var(...)` refs)
- Modify: `landing/src/app.css` (define both schemes)
- Modify: `landing/src/routes/+layout.svelte` (theme init script)
- Modify: `landing/src/lib/components/Nav.svelte` (toggle button)

**Interfaces:**
- Produces: Tailwind classes `bg-surface`, `bg-panel`, `text-on-surface`, `text-body`, `bg-accent`, `text-on-accent`, `text-accent`, `text-placeholder`, plus CSS vars `--pattern-a`, `--pattern-b` used by Tasks 2/4/5.

- [ ] **Step 1: Replace hex tokens with CSS vars in `tailwind.config.ts`**

```ts
colors: {
	primary: 'var(--color-accent)',            // legacy alias, keeps old classes working
	'on-primary': 'var(--color-on-accent)',
	'primary-container': '#BFEAF4',            // pastel chips identical in both schemes
	'on-primary-container': '#001F26',
	accent: 'var(--color-accent)',
	'on-accent': 'var(--color-on-accent)',
	surface: 'var(--color-surface)',
	'surface-low': 'var(--color-surface-low)',
	panel: 'var(--color-panel)',
	'on-surface': 'var(--color-on-surface)',
	'on-surface-variant': 'var(--color-body)',
	body: 'var(--color-body)',
	placeholder: 'var(--color-placeholder)',
	outline: 'var(--color-outline)',
	'outline-variant': 'var(--color-outline-variant)',
	'accent-green': '#C8EBD5', 'accent-green-on': '#0D4020',
	'accent-yellow': '#F7E7B7', 'accent-yellow-on': '#7A5C00',
	'accent-red': '#F9D7D3', 'accent-red-on': '#701C1A',
	'accent-blue': '#BFEAF4', 'accent-blue-on': '#00363F',
	'accent-lavender': '#C7D2FE', 'accent-lavender-on': '#243178',
	'accent-purple': '#D6C4F4', 'accent-purple-on': '#4A2C6E'
}
```

- [ ] **Step 2: Define schemes in `app.css`** (after the `@font-face` blocks)

```css
:root {
	--color-surface: #f7f9fa;
	--color-surface-low: #f1f4f6;
	--color-panel: #ffffff;
	--color-on-surface: #181c1f;
	--color-body: #40484c;
	--color-accent: #0e6577;
	--color-on-accent: #ffffff;
	--color-placeholder: #5a646c;
	--color-outline: #70787c;
	--color-outline-variant: #c3cbd1;
	--pattern-a: #0e6577;
	--pattern-b: #9db4ba;
	color-scheme: light;
}
:root[data-theme='dark'] {
	--color-surface: #101417;
	--color-surface-low: #0d1013;
	--color-panel: #1b2124;
	--color-on-surface: #f4f7f9;
	--color-body: #bec8cf;
	--color-accent: #64d8e8;
	--color-on-accent: #001f26;
	--color-placeholder: #96a0a5;
	--color-outline: #8a949a;
	--color-outline-variant: #39424a;
	--pattern-a: #64d8e8;
	--pattern-b: #e2e4e8;
	color-scheme: dark;
}
body { background-color: var(--color-surface); color: var(--color-on-surface); }
```

- [ ] **Step 3: Theme init without FOUC in `+layout.svelte`** (inline `<script>` in `<svelte:head>` before CSS applies)

```svelte
<svelte:head>
	{@html `<script>(function(){var t=localStorage.getItem('theme');if(!t)t=matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';document.documentElement.dataset.theme=t;})()</script>`}
</svelte:head>
```

- [ ] **Step 4: Toggle in `Nav.svelte`** — round icon button next to lang toggle

```svelte
<script lang="ts">
	// …existing imports…
	function toggleTheme() {
		const el = document.documentElement;
		const next = el.dataset.theme === 'dark' ? 'light' : 'dark';
		el.dataset.theme = next;
		localStorage.setItem('theme', next);
	}
</script>
<button
	type="button"
	onclick={toggleTheme}
	aria-label="Theme wechseln"
	class="flex h-9 w-9 items-center justify-center rounded-full border border-outline-variant text-on-surface hover:bg-surface-low"
>◐</button>
```

- [ ] **Step 5: Sweep hardcoded hexes** — replace in all components: `#fbfaf7`→`bg-surface`, `#181C23`/`#2C313B` section fills→`bg-surface`, `#64D8E8` CTAs→`bg-accent text-on-accent`, `#E2E4E8`/`#9AA3AD` text→`text-on-surface`/`text-body`. (`grep -rn '#' landing/src/lib/components` lists every offender; each must resolve to a token class.)

- [ ] **Step 6: Verify** — `cd landing && npm run check` → 0 errors; `npm run dev -- --port 5173`, `playwright-cli open http://localhost:5173`, screenshot; `playwright-cli eval "document.documentElement.dataset.theme='dark'"`, screenshot; both render with unified bg, no white flash.

- [ ] **Step 7: Commit** — `git add landing && git commit -m "feat(landing): CSS-var theme tokens with light/dark scheme + toggle"`

---

### Task 2: TessellationPattern component (exact P2 lattice)

**Files:**
- Create: `landing/src/lib/components/TessellationPattern.svelte`

**Interfaces:**
- Produces: `<TessellationPattern rows={6} opacityScale={0.35} class="…" />` — absolute-positioned SVG, color from `var(--pattern-a)`.

- [ ] **Step 1: Implement component**

```svelte
<script lang="ts">
	// Exact P2 "T-Tessellation" lattice from the brand board:
	// band A up(6+144k, 6+144r) / down(142+144k, 70+144r),
	// band B up(78+144k, 78+144r) / down(70+144k, 142+144r),
	// opacity cycle [0.55,0.3,0.4,0.5,0.35,0.45].
	const OPS = [0.55, 0.3, 0.4, 0.5, 0.35, 0.45];
	const WIDTH = 1728; // 12 cols × 144, clipped by parent

	let { rows = 6, opacityScale = 0.35, class: klass = '' }: {
		rows?: number; opacityScale?: number; class?: string;
	} = $props();

	type T = { x: number; y: number; rot: 0 | 180; opacity: number };
	const tiles: T[] = $derived.by(() => {
		const out: T[] = [];
		let i = 0;
		const cols = Math.ceil(WIDTH / 144);
		for (let r = 0; r < rows; r++) {
			const y = 6 + 144 * r;
			for (let k = 0; k < cols; k++) {
				out.push({ x: 6 + 144 * k, y, rot: 0, opacity: OPS[i++ % 6] * opacityScale });
				out.push({ x: 142 + 144 * k, y: y + 64, rot: 180, opacity: OPS[i++ % 6] * opacityScale });
			}
			for (let k = 0; k < cols; k++) {
				out.push({ x: 70 + 144 * k, y: y + 136, rot: 180, opacity: OPS[i++ % 6] * opacityScale });
				out.push({ x: 78 + 144 * k, y: y + 72, rot: 0, opacity: OPS[i++ % 6] * opacityScale });
			}
		}
		return out;
	});
</script>

<svg
	class="pointer-events-none absolute inset-0 h-full w-full {klass}"
	viewBox="0 0 {WIDTH} {rows * 144 + 12}"
	preserveAspectRatio="xMinYMin slice"
	aria-hidden="true"
	style="color: var(--pattern-a)"
>
	{#each tiles as t (t.x + ',' + t.y + ',' + t.rot)}
		<g transform="translate({t.x},{t.y}) rotate({t.rot},32,32)" opacity={t.opacity}>
			<rect x="0" y="0" width="18" height="18" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
			<rect x="23" y="0" width="18" height="18" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
			<rect x="46" y="0" width="18" height="18" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
			<rect x="23" y="23" width="18" height="41" rx="5.5" fill="none" stroke="currentColor" stroke-width="2.5" />
		</g>
	{/each}
</svg>
```

Note: rotation pivot `(32,32)` = unit center; matches Figma instance placement because a 180° rotation about the center maps the 64×64 unit onto the recorded bounding position.

- [ ] **Step 2: Verify vs. Figma reference** — mount temporarily on the page, `playwright-cli screenshot` the hero region and compare side-by-side with `scratchpad/p2-orig.png` (same interlock: down-T stems slot between up-T caps). Then remove temp mount.

- [ ] **Step 3: Commit** — `git commit -m "feat(landing): exact P2 T-tessellation pattern component"`

---

### Task 3: Hero rework (panel card, tessellation, fade-out)

**Files:**
- Modify: `landing/src/lib/components/Hero.svelte`

**Interfaces:**
- Consumes: `TessellationPattern` (Task 2), theme tokens (Task 1).

- [ ] **Step 1: Rebuild hero markup**

```svelte
<section class="relative overflow-hidden bg-surface">
	<TessellationPattern rows={6} opacityScale={0.35} />
	<!-- smooth fade into next section -->
	<div
		class="pointer-events-none absolute inset-x-0 bottom-0 h-[55%]"
		style="background: linear-gradient(to bottom, transparent, var(--color-surface))"
	></div>

	<div class="relative mx-auto flex max-w-content flex-col items-center gap-10 px-6 pb-16 pt-12 md:flex-row md:px-10 md:pb-20 md:pt-16">
		<div
			class="w-full rounded-[32px] bg-panel p-8 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.18)] md:max-w-[560px] md:p-11"
		>
			<!-- keep existing eyebrow chip / h1 / sub / CTA / trust markup, but: -->
			<!-- h1: text-on-surface, headlineB span: text-accent -->
			<!-- sub: text-body -->
			<!-- primary CTA: bg-accent text-on-accent rounded-full -->
			<!-- secondary CTA: border-outline-variant text-on-surface -->
			<!-- trust dots: bg-accent, trust text: text-body -->
		</div>
		<!-- PhoneMockup block unchanged -->
	</div>
</section>
```

The existing Hero children (eyebrow/h1/sub/buttons/trust/PhoneMockups) are kept — only the wrapper (`bg-[#181C23]`, gradient style) is replaced by the panel structure above and each hardcoded color class is swapped to its token per Task 1 Step 5 table. The old `<RosterPattern />` import/usage in Hero is deleted.

- [ ] **Step 2: Verify** — `npm run check`; dev-server screenshots light + dark: pattern visible at top, melts into `bg-surface` before the Funktionen headline; panel text ≥ 7:1.

- [ ] **Step 3: Commit** — `git commit -m "feat(landing): hero with panel card, P2 tessellation and fade-out"`

---

### Task 4: RosterPattern outline mode

**Files:**
- Modify: `landing/src/lib/components/RosterPattern.svelte`

**Interfaces:**
- Produces: `<RosterPattern outline uniformOpacity={0.18} rows={14} class="…" />`; colors from `var(--pattern-a)`/`var(--pattern-b)`; default (no `outline`) keeps legacy filled/sinus rendering for any remaining callers.

- [ ] **Step 1: Extend component**

```svelte
<script lang="ts">
	const PATTERN_COLS = 34;
	const X_PITCH = 48; const Y_PITCH = 52; const SQUARE = 20; const OFFSET = 24;

	let { intensity = 1, rows = 14, outline = false, uniformOpacity = 0.18, class: klass = '' }: {
		intensity?: number; rows?: number; outline?: boolean; uniformOpacity?: number; class?: string;
	} = $props();

	type Rect = { x: number; y: number; a: boolean; opacity: number };
	const rects: Rect[] = $derived(
		Array.from({ length: rows }, (_, row) => {
			const even = row % 2 === 0;
			const opacity = outline
				? uniformOpacity
				: +((0.08 + 0.42 * (0.5 + 0.5 * Math.sin(row * 0.7))) * intensity).toFixed(3);
			return Array.from({ length: PATTERN_COLS }, (_, col) => ({
				x: (even ? 0 : OFFSET) + col * X_PITCH,
				y: row * Y_PITCH,
				a: even,
				opacity
			}));
		}).flat()
	);
</script>

<svg
	class="pointer-events-none absolute inset-0 h-full w-full {klass}"
	viewBox="0 0 1632 {rows * Y_PITCH}"
	preserveAspectRatio="xMinYMin slice"
	aria-hidden="true"
>
	{#each rects as r (r.x + ',' + r.y)}
		<rect
			x={r.x} y={r.y} width={SQUARE} height={SQUARE} rx="6"
			fill={outline ? 'none' : `var(${r.a ? '--pattern-a' : '--pattern-b'})`}
			stroke={outline ? `var(${r.a ? '--pattern-a' : '--pattern-b'})` : 'none'}
			stroke-width={outline ? 2.5 : 0}
			opacity={r.opacity}
		/>
	{/each}
</svg>
```

- [ ] **Step 2: Verify** — `npm run check`; component renders outline squares, uniform alpha.

- [ ] **Step 3: Commit** — `git commit -m "feat(landing): outline/uniform mode for roster pattern"`

---

### Task 5: Contact + Footer shared pattern wrapper (fade-in, seamless continuation, scrim)

**Files:**
- Modify: `landing/src/routes/+page.svelte` (wrap Contact + Footer)
- Modify: `landing/src/lib/components/Contact.svelte` (drop own pattern + bg)
- Modify: `landing/src/lib/components/Footer.svelte` (drop own pattern + bg, add scrim)

**Interfaces:**
- Consumes: `RosterPattern` outline mode (Task 4).

- [ ] **Step 1: One pattern layer across both sections in `+page.svelte`**

```svelte
<div class="relative overflow-hidden bg-surface">
	<RosterPattern outline uniformOpacity={0.18} rows={24} />
	<!-- fade-IN: solid at top, pattern emerges downward (78% of contact height ≈ fixed 520px works at all breakpoints) -->
	<div
		class="pointer-events-none absolute inset-x-0 top-0 h-[520px]"
		style="background: linear-gradient(to bottom, var(--color-surface), transparent)"
	></div>
	<Contact m={m.contact} />
	<Footer m={m.footer} nav={m.nav} />
</div>
```

Because both sections live under ONE absolutely-positioned pattern, the grid, row alternation and phase continue seamlessly across the contact/footer boundary by construction — no offset math needed.

- [ ] **Step 2: `Contact.svelte`** — remove `<RosterPattern intensity={0.22} />` and the section's own graphite `bg-*`; section becomes `class="relative scroll-mt-20"` (transparent). Form panel stays `bg-panel rounded-[24px]`; left column text `text-on-surface`/`text-body`; info-row dots `bg-accent`.

- [ ] **Step 3: `Footer.svelte`** — remove `<RosterPattern intensity={0.12} />` and own bg; add scrim as first child:

```svelte
<footer class="relative">
	<div class="absolute inset-0" style="background: var(--color-surface); opacity: 0.62"></div>
	<div class="relative mx-auto max-w-content …existing content…">…</div>
</footer>
```

Footer text colors: headings `text-accent`, links `text-on-surface`, muted `text-body`.

- [ ] **Step 4: Verify** — screenshots light+dark of the page bottom: contact headline zone clean, dots emerge below, rows run visibly through the footer without a seam, footer links clearly readable.

- [ ] **Step 5: Commit** — `git commit -m "feat(landing): shared tribune outline pattern across contact and footer"`

---

### Task 6: Pricing — open layout + trimmed copy

**Files:**
- Modify: `landing/src/lib/i18n/index.ts` (Dict type + de + en)
- Modify: `landing/src/lib/components/Pricing.svelte` (full rewrite)

**Interfaces:**
- Produces: `Dict['pricing']` = `{ eyebrow, title, price, per, example, fine, includesTitle, includes: string[4], cta }` (drops `sub`, `planLabel`, `footnote`).

- [ ] **Step 1: i18n**

```ts
// Dict type
pricing: {
	eyebrow: string; title: string; price: string; per: string;
	example: string; fine: string; includesTitle: string; includes: string[]; cta: string;
};
// de
pricing: {
	eyebrow: 'PREISE',
	title: 'Ein Preis. Keine Überraschungen.',
	price: '2 CHF',
	per: '/ Mitglied / Jahr',
	example: 'Beispiel: 40 Mitglieder = 40 CHF pro Jahr',
	fine: 'Jährliche Abrechnung. Nur aktive Mitglieder zählen.',
	includesTitle: 'ALLES INKLUSIVE',
	includes: [
		'Unbegrenzte Teams & Mitglieder',
		'Anwesenheit in Echtzeit & Statistiken',
		'Offline-Modus mit Auto-Sync',
		'J+S-Anwesenheitskontrolle'
	],
	cta: 'Jetzt starten'
},
// en
pricing: {
	eyebrow: 'PRICING',
	title: 'One price. No surprises.',
	price: 'CHF 2',
	per: '/ member / year',
	example: 'Example: 40 members = CHF 40 per year',
	fine: 'Billed yearly. Only active members count.',
	includesTitle: 'EVERYTHING INCLUDED',
	includes: [
		'Unlimited teams & members',
		'Real-time attendance & stats',
		'Offline mode with auto-sync',
		'J+S attendance reports'
	],
	cta: 'Get started'
},
```

- [ ] **Step 2: Rewrite `Pricing.svelte`** (open split, hairlines, no card)

```svelte
<script lang="ts">
	import type { Dict } from '$lib/i18n';
	let { m }: { m: Dict['pricing'] } = $props();
</script>

<section id="preise" class="scroll-mt-20 bg-surface">
	<div class="mx-auto max-w-content px-6 py-20 md:px-10 md:py-24">
		<hr class="border-accent/25" />
		<div class="mt-10 grid gap-12 md:grid-cols-[1fr_480px] md:gap-20">
			<div>
				<span class="inline-block rounded-full bg-primary-container px-3.5 py-1.5 text-[12px] font-bold tracking-[0.16em] text-on-primary-container">{m.eyebrow}</span>
				<h2 class="font-display mt-5 text-[32px] font-extrabold leading-[1.1] tracking-tight text-on-surface md:text-[40px]">{m.title}</h2>
				<div class="mt-6 flex items-baseline gap-3">
					<span class="font-display text-[72px] font-extrabold tracking-tight text-accent md:text-[96px]">{m.price}</span>
					<span class="text-[17px] font-medium text-on-surface">{m.per}</span>
				</div>
				<p class="mt-3 text-[15px] font-bold text-on-surface">{m.example}</p>
				<p class="mt-2 text-[13px] text-body">{m.fine}</p>
				<a href="https://app.teamorg.ch/start"
					class="mt-7 inline-block rounded-full bg-accent px-8 py-4 text-[15px] font-bold text-on-accent transition-transform duration-150 hover:scale-[1.02]">{m.cta}</a>
			</div>
			<ul>
				<li class="pb-3 text-[12px] font-bold tracking-[0.18em] text-accent">{m.includesTitle}</li>
				{#each m.includes as item (item)}
					<li class="flex items-center gap-3.5 border-b border-on-surface/15 py-4">
						<span class="h-2.5 w-2.5 shrink-0 rounded-[3px] bg-accent"></span>
						<span class="text-[15px] font-medium text-on-surface">{item}</span>
					</li>
				{/each}
			</ul>
		</div>
	</div>
</section>
```

- [ ] **Step 3: Verify** — `npm run check` (Dict change surfaces stale key usages — fix all); screenshots light+dark.

- [ ] **Step 4: Commit** — `git commit -m "feat(landing): open trimmed pricing section"`

---

### Task 7: Features + HowItWorks token pass

**Files:**
- Modify: `landing/src/lib/components/Features.svelte`
- Modify: `landing/src/lib/components/HowItWorks.svelte`

Both keep prod structure and the `Shape.svelte` M3-expressive icons (clover/flower/sunny/squircle) and pastel accents — only surfaces/text move to tokens so dark mode works:
- section bg → `bg-surface`; cards → `bg-panel border border-outline-variant` (dark: panel #1B2124 + subtle border out of the box).
- headings `text-on-surface`, body `text-body`, eyebrow chip stays `bg-primary-container text-on-primary-container`.
- Step number badges: keep pastel `accent-green/-blue/-red` fills with their `-on` text (identical in both schemes, per Figma finals).

- [ ] **Step 1: Apply swaps in both files** (mechanical, per the table above)
- [ ] **Step 2: Verify** — dark screenshots: cards clearly separated from bg, Shape icons pastel-on-panel.
- [ ] **Step 3: Commit** — `git commit -m "feat(landing): token-driven features and steps for dark scheme"`

---

### Task 8: Full QA + PR

- [ ] **Step 1:** `cd landing && npm run check` → 0 errors.
- [ ] **Step 2:** Full-page screenshots light + dark (`playwright-cli`), desktop 1440 + mobile 412: compare against Figma finals — fades smooth, patterns exact, no text over un-faded pattern, all copy present in de + en (`?lang=en`).
- [ ] **Step 3:** Legal pages + `/i/[token]` unaffected (they inherit tokens; spot-check).
- [ ] **Step 4:** Push branch `feat/landing-final-redesign`, PR to `main`, checks → merge, then promote `main → production` (Coolify auto-deploys landing).

## Unresolved Questions

1. Default scheme: OS-preference (geplant) oder fix Light?
2. Theme-Toggle im Nav gewünscht oder nur OS-Preference?
3. EN-Preistexte oben ok?
4. Mobile: Hero-Panel volle Breite unter Pattern ok (Figma hat nur Desktop)?
