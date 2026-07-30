<script lang="ts">
	import Hero from '$lib/components/Hero.svelte';
	import Features from '$lib/components/Features.svelte';
	import HowItWorks from '$lib/components/HowItWorks.svelte';
	import Pricing from '$lib/components/Pricing.svelte';
	import Contact from '$lib/components/Contact.svelte';
	import Footer from '$lib/components/Footer.svelte';
	import RosterPattern from '$lib/components/RosterPattern.svelte';
	import type { PageData, ActionData } from './$types';

	let { data, form }: { data: PageData; form: ActionData } = $props();
</script>

<Hero m={data.m.hero} />
<Features m={data.m.features} />
<HowItWorks m={data.m.how} />
<Pricing m={data.m.pricing} />
<div class="relative overflow-hidden bg-surface">
	<RosterPattern outline uniformOpacity={0.18} rows={24} />
	<!-- fade-IN: solid at top, pattern emerges downward (78% of contact height ≈ fixed 520px works at all breakpoints) -->
	<div
		class="pointer-events-none absolute inset-x-0 top-0 h-[520px]"
		style="background: linear-gradient(to bottom, var(--color-surface), transparent)"
	></div>
	<Contact m={data.m.contact} siteKey={data.turnstileSiteKey} {form} />
	<Footer m={data.m.footer} lang={data.lang} />
</div>
