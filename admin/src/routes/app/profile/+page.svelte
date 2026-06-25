<script lang="ts">
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();

	const initials = $derived(
		data.user.displayName
			.split(/\s+/)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase() ?? '')
			.join('')
	);
</script>

<svelte:head>
	<title>{data.m.profile.title} — TeamOrg</title>
</svelte:head>

<div class="mx-auto flex max-w-[560px] flex-col gap-6">
	<header class="flex items-center gap-4">
		<span
			class="flex size-16 items-center justify-center rounded-full bg-primary-container text-[20px] font-bold text-on-primary-container"
		>
			{initials}
		</span>
		<h1 class="font-display text-[24px] font-extrabold text-on-surface">{data.user.displayName}</h1>
	</header>

	<section class="flex flex-col gap-3 rounded-[28px] bg-surface p-6 text-[14px]">
		<h2 class="text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">
			{data.m.profile.account}
		</h2>
		<div class="flex justify-between">
			<dt class="text-on-surface-variant">{data.m.profile.name}</dt>
			<dd class="font-semibold text-on-surface">{data.user.displayName}</dd>
		</div>
		<div class="flex justify-between">
			<dt class="text-on-surface-variant">{data.m.profile.email}</dt>
			<dd class="font-semibold text-on-surface">{data.user.email}</dd>
		</div>
	</section>

	<section class="flex items-center justify-between rounded-[28px] bg-surface p-6">
		<span class="text-[14px] text-on-surface">{data.m.profile.language}</span>
		<div class="flex items-center gap-1 text-[12px] font-medium">
			<a
				href="?lang=de"
				data-sveltekit-reload
				class="rounded-full px-3 py-1 transition-colors {data.lang === 'de'
					? 'bg-secondary-container text-on-secondary-container'
					: 'text-on-surface-variant hover:bg-surface-container-high'}">DE</a
			>
			<a
				href="?lang=en"
				data-sveltekit-reload
				class="rounded-full px-3 py-1 transition-colors {data.lang === 'en'
					? 'bg-secondary-container text-on-secondary-container'
					: 'text-on-surface-variant hover:bg-surface-container-high'}">EN</a
			>
		</div>
	</section>
</div>
