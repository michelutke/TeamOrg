<script lang="ts">
	import { browser } from '$app/environment';
	import { Menu, X, Moon, Sun } from 'lucide-svelte';
	import LogoMark from './LogoMark.svelte';
	import type { Dict, Locale } from '$lib/i18n';

	let { m, lang, appUrl }: { m: Dict['nav']; lang: Locale; appUrl: string } = $props();
	let open = $state(false);
	let theme = $state(browser ? (document.documentElement.dataset.theme ?? 'light') : 'light');

	function toggleTheme() {
		const el = document.documentElement;
		const next = el.dataset.theme === 'dark' ? 'light' : 'dark';
		el.dataset.theme = next;
		try {
			localStorage.setItem('theme', next);
		} catch {
			/* storage unavailable */
		}
		theme = next;
	}

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
			<LogoMark size={28} blocks="var(--color-accent)" stem="var(--color-on-surface)" />
			<span class="font-display text-[22px] font-extrabold tracking-tight"
				><span class="text-on-surface">Team</span><span class="text-primary">Org</span></span
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

			<button
				type="button"
				onclick={toggleTheme}
				aria-label={m.themeToggle}
				class="flex h-9 w-9 items-center justify-center rounded-full border border-outline-variant text-on-surface hover:bg-surface-low"
			>
				{#if theme === 'dark'}
					<Sun class="h-[18px] w-[18px]" strokeWidth={2} />
				{:else}
					<Moon class="h-[18px] w-[18px]" strokeWidth={2} />
				{/if}
			</button>

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
				<div class="flex items-center gap-2">
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
					<button
						type="button"
						onclick={toggleTheme}
						aria-label={m.themeToggle}
						class="flex h-9 w-9 items-center justify-center rounded-full border border-outline-variant text-on-surface hover:bg-surface-low"
					>
						{#if theme === 'dark'}
							<Sun class="h-[18px] w-[18px]" strokeWidth={2} />
						{:else}
							<Moon class="h-[18px] w-[18px]" strokeWidth={2} />
						{/if}
					</button>
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
