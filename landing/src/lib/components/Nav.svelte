<script lang="ts">
	import { Check, Menu, X } from 'lucide-svelte';
	import type { Dict, Locale } from '$lib/i18n';

	let { m, lang, appUrl }: { m: Dict['nav']; lang: Locale; appUrl: string } = $props();
	let open = $state(false);

	const loginUrl = $derived(`${appUrl}/login`);

	const links = $derived([
		{ href: '/#funktionen', label: m.features },
		{ href: '/#preise', label: m.pricing },
		{ href: '/#kontakt', label: m.contact }
	]);
</script>

<header
	class="sticky top-0 z-50 border-b border-outline-variant/70 bg-surface/85 backdrop-blur-md"
>
	<nav class="mx-auto flex h-[68px] max-w-content items-center justify-between px-5 md:px-10">
		<!-- Logo -->
		<a href="/" class="flex items-center gap-2.5" aria-label="teamorg">
			<span class="flex h-9 w-9 items-center justify-center rounded-[10px] bg-primary">
				<Check class="h-5 w-5 text-on-primary" strokeWidth={3} />
			</span>
			<span class="font-display text-[22px] font-extrabold tracking-tight text-on-surface"
				>teamorg</span
			>
		</a>

		<!-- Desktop links + actions -->
		<div class="hidden items-center gap-8 md:flex">
			<ul class="flex items-center gap-7">
				{#each links as link (link.href)}
					<li>
						<a
							href={link.href}
							class="text-[15px] font-medium text-on-surface-variant transition-colors hover:text-on-surface"
							>{link.label}</a
						>
					</li>
				{/each}
			</ul>

			<!-- DE / EN toggle -->
			<div
				class="flex items-center rounded-full border border-outline-variant p-0.5 text-[13px] font-medium"
			>
				<a
					href="?lang=de"
				data-sveltekit-reload
				data-sveltekit-preload-data="off"
					class="rounded-full px-3 py-1.5 transition-colors {lang === 'de'
						? 'bg-primary font-bold text-on-primary'
						: 'text-on-surface-variant hover:text-on-surface'}">DE</a
				>
				<a
					href="?lang=en"
				data-sveltekit-reload
				data-sveltekit-preload-data="off"
					class="rounded-full px-3 py-1.5 transition-colors {lang === 'en'
						? 'bg-primary font-bold text-on-primary'
						: 'text-on-surface-variant hover:text-on-surface'}">EN</a
				>
			</div>

			<a
				href={loginUrl}
				class="text-[15px] font-medium text-on-surface-variant transition-colors hover:text-on-surface"
				>{m.login}</a
			>

			<a
				href="/#kontakt"
				class="rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary shadow-sm transition-transform duration-150 hover:scale-[1.03] active:scale-[0.98]"
				>{m.cta}</a
			>
		</div>

		<!-- Mobile menu button -->
		<button
			class="flex h-10 w-10 items-center justify-center rounded-lg text-on-surface md:hidden"
			aria-label="Menu"
			aria-expanded={open}
			onclick={() => (open = !open)}
		>
			{#if open}<X class="h-6 w-6" />{:else}<Menu class="h-6 w-6" />{/if}
		</button>
	</nav>

	<!-- Mobile menu -->
	{#if open}
		<div class="border-t border-outline-variant/70 bg-surface px-5 pb-6 pt-2 md:hidden">
			<ul class="flex flex-col">
				{#each links as link (link.href)}
					<li>
						<a
							href={link.href}
							class="block py-3 text-[16px] font-medium text-on-surface"
							onclick={() => (open = false)}>{link.label}</a
						>
					</li>
				{/each}
				<li>
					<a
						href={loginUrl}
						class="block py-3 text-[16px] font-medium text-on-surface"
						onclick={() => (open = false)}>{m.login}</a
					>
				</li>
			</ul>
			<div class="mt-3 flex items-center justify-between">
				<div
					class="flex items-center rounded-full border border-outline-variant p-0.5 text-[13px] font-medium"
				>
					<a
						href="?lang=de"
				data-sveltekit-reload
				data-sveltekit-preload-data="off"
						class="rounded-full px-3 py-1.5 {lang === 'de'
							? 'bg-primary font-bold text-on-primary'
							: 'text-on-surface-variant'}">DE</a
					>
					<a
						href="?lang=en"
				data-sveltekit-reload
				data-sveltekit-preload-data="off"
						class="rounded-full px-3 py-1.5 {lang === 'en'
							? 'bg-primary font-bold text-on-primary'
							: 'text-on-surface-variant'}">EN</a
					>
				</div>
				<a
					href="/#kontakt"
					class="rounded-full bg-primary px-5 py-3 text-[14px] font-bold text-on-primary"
					onclick={() => (open = false)}>{m.cta}</a
				>
			</div>
		</div>
	{/if}
</header>
