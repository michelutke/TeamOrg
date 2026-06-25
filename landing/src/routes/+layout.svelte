<script lang="ts">
	import '../app.css';
	import { onMount } from 'svelte';
	import { afterNavigate } from '$app/navigation';
	import Nav from '$lib/components/Nav.svelte';
	import Footer from '$lib/components/Footer.svelte';
	import type { LayoutData } from './$types';

	let { data, children }: { data: LayoutData; children: import('svelte').Snippet } = $props();

	// The layout persists across client-side navigations, so onMount only fires once.
	// We re-scan for [data-reveal] elements after every navigation (afterNavigate) so
	// pages rendered after a back/forward navigation also get revealed - otherwise their
	// text stays stuck at opacity:0.
	let io: IntersectionObserver | null = null;
	let reduceMotion = false;

	function observeReveals() {
		if (typeof document === 'undefined') return;
		if (reduceMotion) {
			document.querySelectorAll('[data-reveal]').forEach((el) => el.classList.add('is-visible'));
			return;
		}
		if (!io) return; // observer not ready yet - onMount will call this again
		document
			.querySelectorAll('[data-reveal]:not(.is-visible)')
			.forEach((el) => io!.observe(el));
	}

	onMount(() => {
		const supported = 'IntersectionObserver' in window;
		reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches || !supported;
		if (!reduceMotion) {
			io = new IntersectionObserver(
				(entries) => {
					for (const entry of entries) {
						if (entry.isIntersecting) {
							entry.target.classList.add('is-visible');
							io!.unobserve(entry.target);
						}
					}
				},
				{ threshold: 0.12, rootMargin: '0px 0px -8% 0px' }
			);
		}
		document.documentElement.lang = data.lang;
		observeReveals();
		return () => io?.disconnect();
	});

	afterNavigate(() => {
		document.documentElement.lang = data.lang;
		observeReveals();
	});
</script>

<svelte:head>
	<title>{data.m.meta.title}</title>
	<meta name="description" content={data.m.meta.description} />
	<meta property="og:title" content={data.m.meta.title} />
	<meta property="og:description" content={data.m.meta.description} />
	<meta property="og:type" content="website" />
</svelte:head>

<Nav m={data.m.nav} lang={data.lang} appUrl={data.appUrl} />
<main>
	{@render children()}
</main>
<Footer m={data.m.footer} lang={data.lang} />
