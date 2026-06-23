<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import Nav from '$lib/components/Nav.svelte';
	import Footer from '$lib/components/Footer.svelte';
	import type { LayoutData } from './$types';

	let { data, children }: { data: LayoutData; children: import('svelte').Snippet } = $props();

	onMount(() => {
		document.documentElement.lang = data.lang;

		const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		const els = Array.from(document.querySelectorAll<HTMLElement>('[data-reveal]'));
		if (reduce || !('IntersectionObserver' in window)) {
			els.forEach((el) => el.classList.add('is-visible'));
			return;
		}
		const io = new IntersectionObserver(
			(entries) => {
				for (const entry of entries) {
					if (entry.isIntersecting) {
						entry.target.classList.add('is-visible');
						io.unobserve(entry.target);
					}
				}
			},
			{ threshold: 0.12, rootMargin: '0px 0px -8% 0px' }
		);
		els.forEach((el) => io.observe(el));
		return () => io.disconnect();
	});
</script>

<svelte:head>
	<title>{data.m.meta.title}</title>
	<meta name="description" content={data.m.meta.description} />
	<meta property="og:title" content={data.m.meta.title} />
	<meta property="og:description" content={data.m.meta.description} />
	<meta property="og:type" content="website" />
</svelte:head>

<Nav m={data.m.nav} lang={data.lang} />
<main>
	{@render children()}
</main>
<Footer m={data.m.footer} lang={data.lang} />
