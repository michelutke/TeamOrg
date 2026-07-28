<script lang="ts">
	import { Users, PlusCircle } from 'lucide-svelte';
	import type { PageData } from './$types';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();
	const m = $derived(data.m);
</script>

<svelte:head>
	<title>{m.welcomeTitle} — TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface-container-low">
	<div
		class="flex w-full max-w-[440px] flex-col items-center gap-4 rounded-[32px] bg-surface px-10 py-12 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.08)]"
	>
		<div class="flex size-16 items-center justify-center rounded-full bg-primary-container">
			<span class="text-[22px] font-bold text-on-primary-container">TO</span>
		</div>
		<h1 class="text-center font-display text-[26px] font-extrabold text-on-surface">{m.welcomeTitle}</h1>
		<p class="text-center text-[13px] text-on-surface-variant">{m.welcomeSubtitle}</p>

		<div class="flex w-full flex-col gap-3">
			<a
				href="/join"
				class="flex w-full items-center gap-4 rounded-2xl border border-outline-variant px-[18px] py-4 hover:bg-surface-container-low"
			>
				<Users class="size-6 shrink-0 text-primary" />
				<span class="text-[15px] font-bold text-on-surface">{m.joinCta}</span>
			</a>

			<a
				href="/create"
				class="flex w-full items-center gap-4 rounded-2xl border border-outline-variant px-[18px] py-4 hover:bg-surface-container-low"
			>
				<PlusCircle class="size-6 shrink-0 text-primary" />
				<span class="flex flex-col gap-0.5">
					<span class="text-[15px] font-bold text-on-surface">{m.createCta}</span>
					<span class="text-[12px] text-on-surface-variant">{m.pricingNote}</span>
				</span>
			</a>
		</div>

		<a href="/login" class="text-[12px] font-medium text-on-surface-variant hover:text-primary">
			{m.loginCta}
		</a>
	</div>
</div>
