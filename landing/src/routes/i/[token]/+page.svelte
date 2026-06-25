<script lang="ts">
	import { env } from '$env/dynamic/public';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();

	const inv = $derived(data.invite);
	const isClub = $derived(inv?.scope === 'club');
	const androidUrl = env.PUBLIC_ANDROID_DOWNLOAD_URL ?? '';
	const appLink = $derived(`teamorg://invite/team/${data.token}`);

	const expiry = $derived(
		inv
			? new Intl.DateTimeFormat(data.lang === 'de' ? 'de-CH' : 'en-GB', {
					day: '2-digit',
					month: '2-digit',
					year: 'numeric'
				}).format(new Date(inv.expiresAt))
			: ''
	);
</script>

<svelte:head>
	<title>{data.m.invite.eyebrow} · teamorg</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<section class="mx-auto flex max-w-[560px] flex-col px-6 py-16 md:py-24">
	{#if inv}
		<div
			class="rounded-[24px] border border-outline-variant/70 bg-surface p-7 shadow-sm md:p-9"
			data-reveal
		>
			<p class="text-[12px] font-bold tracking-[0.12em] text-primary">{data.m.invite.eyebrow}</p>
			<h1
				class="font-display mt-3 text-[26px] font-extrabold leading-tight tracking-tight text-on-surface md:text-[30px]"
			>
				{isClub ? data.m.invite.clubTitle : data.m.invite.teamTitle}
			</h1>

			<dl class="mt-7 flex flex-col gap-4 text-[15px]">
				<div class="flex items-baseline justify-between gap-4">
					<dt class="text-on-surface-variant">Verein</dt>
					<dd class="font-semibold text-on-surface">{inv.clubName}</dd>
				</div>
				{#if inv.teamName}
					<div class="flex items-baseline justify-between gap-4">
						<dt class="text-on-surface-variant">{data.m.invite.teamLabel}</dt>
						<dd class="font-semibold text-on-surface">{inv.teamName}</dd>
					</div>
				{/if}
				<div class="flex items-baseline justify-between gap-4">
					<dt class="text-on-surface-variant">{data.m.invite.roleLabel}</dt>
					<dd class="font-semibold text-on-surface">{data.m.invite.roles[inv.role]}</dd>
				</div>
				<div class="flex items-baseline justify-between gap-4">
					<dt class="text-on-surface-variant">{data.m.invite.invitedByLabel}</dt>
					<dd class="font-semibold text-on-surface">{inv.invitedBy}</dd>
				</div>
				<div class="flex items-baseline justify-between gap-4">
					<dt class="text-on-surface-variant">{data.m.invite.expiresLabel}</dt>
					<dd class="font-semibold text-on-surface">{expiry}</dd>
				</div>
			</dl>

			<div class="mt-8 flex flex-col gap-3">
				<a
					href={appLink}
					class="rounded-full bg-primary px-5 py-3.5 text-center text-[15px] font-bold text-on-primary shadow-sm transition-transform duration-150 hover:scale-[1.02] active:scale-[0.98]"
					>{data.m.invite.openApp}</a
				>
				<a
					href="{data.appUrl}/i/{data.token}"
					class="rounded-full border border-outline-variant px-5 py-3.5 text-center text-[15px] font-bold text-on-surface transition-colors hover:bg-surface-variant/40"
					>{data.m.invite.joinWeb}</a
				>
				{#if androidUrl}
					<a
						href={androidUrl}
						rel="noopener"
						class="rounded-full border border-outline-variant px-5 py-3.5 text-center text-[15px] font-bold text-on-surface transition-colors hover:bg-surface-variant/40"
						>{data.m.invite.download}</a
					>
				{/if}
			</div>

			<p class="mt-5 text-center text-[13px] text-on-surface-variant">{data.m.invite.iosSoon}</p>
			<p class="mt-2 text-center text-[13px] text-on-surface-variant">{data.m.invite.hint}</p>
		</div>
	{:else}
		<div
			class="rounded-[24px] border border-outline-variant/70 bg-surface p-7 text-center shadow-sm md:p-9"
			data-reveal
		>
			<h1 class="font-display text-[24px] font-extrabold tracking-tight text-on-surface md:text-[28px]">
				{data.m.invite.invalidTitle}
			</h1>
			<p class="mt-4 text-[15px] leading-[1.6] text-on-surface-variant">
				{data.m.invite.invalidBody}
			</p>
			<a href="/" class="mt-7 inline-block text-[14px] font-medium text-primary hover:underline"
				>{data.m.invite.backHome}</a
			>
		</div>
	{/if}
</section>
