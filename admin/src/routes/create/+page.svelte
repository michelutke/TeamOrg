<script lang="ts">
	import { enhance } from '$app/forms';
	import type { ActionData, PageData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const o = $derived(data.m.onboarding);

	const inputCls =
		'w-full rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none';

	const formName = $derived(form && 'name' in form ? ((form.name as string) ?? '') : '');
	const formEmail = $derived(form && 'email' in form ? ((form.email as string) ?? '') : '');

	let kind = $state((form && 'kind' in form ? (form.kind as string) : null) ?? 'team');
	const formSportType = $derived(
		form && 'sportType' in form ? ((form.sportType as string) ?? '') : ''
	);
	const formLocation = $derived(form && 'location' in form ? ((form.location as string) ?? '') : '');
	const formBillingEmail = $derived(
		form && 'billingEmail' in form ? (form.billingEmail as string) : ''
	);
</script>

<svelte:head>
	<title>{data.step === 'account' ? o.createAccountTitle : o.createDetailsTitle} — TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface-container-low px-6 py-16">
	<div
		class="flex w-full max-w-[560px] flex-col gap-6 rounded-[32px] bg-surface px-10 py-12 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.08)]"
	>
		<ol class="flex items-center gap-3 text-[12px]">
			<li
				class="flex-1 rounded-2xl border px-3 py-2 text-center {data.step === 'account'
					? 'border-primary font-bold text-on-surface'
					: 'border-outline-variant text-on-surface-variant'}"
			>
				1. {o.createAccountTitle}
			</li>
			<li
				class="flex-1 rounded-2xl border px-3 py-2 text-center {data.step === 'details'
					? 'border-primary font-bold text-on-surface'
					: 'border-outline-variant text-on-surface-variant'}"
			>
				2. {o.createDetailsTitle}
			</li>
			<li class="flex-1 rounded-2xl border border-outline-variant px-3 py-2 text-center text-on-surface-variant">
				3. {o.cardTitle}
			</li>
		</ol>

		{#if data.step === 'account'}
			<form method="POST" action="?/register" use:enhance class="flex flex-col gap-4">
				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.nameLabel}</span>
					<input name="name" required value={formName} class={inputCls} />
				</label>
				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.emailLabel}</span>
					<input name="email" type="email" required value={formEmail} class={inputCls} />
				</label>
				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.passwordLabel}</span>
					<input name="password" type="password" required minlength="8" class={inputCls} />
				</label>

				{#if form?.error}
					<p class="rounded-2xl bg-error-container px-4 py-3 text-[13px] font-medium text-error">
						{form.error}
					</p>
				{/if}

				<button
					type="submit"
					class="mt-1 w-full rounded-full border-none bg-primary py-3.5 text-[15px] font-bold text-on-primary hover:opacity-90"
				>
					{o.registerSubmit}
				</button>
			</form>

			<p class="text-center text-[13px] text-on-surface-variant">
				{o.haveAccount}
				<a href="/login?redirectTo=/create" class="font-semibold text-primary hover:underline">
					{o.toLogin}
				</a>
			</p>
		{:else}
			<form method="POST" action="?/create" use:enhance class="flex flex-col gap-4">
				<div class="flex gap-3">
					<label
						class="flex-1 cursor-pointer rounded-2xl border px-4 py-3 {kind === 'team'
							? 'border-primary bg-surface-container-high'
							: 'border-outline-variant'}"
					>
						<input
							type="radio"
							name="kind"
							value="team"
							bind:group={kind}
							class="sr-only"
						/>
						<span class="block text-[14px] font-bold text-on-surface">{o.kindTeam}</span>
						<span class="mt-1 block text-[12px] text-on-surface-variant">{o.kindTeamHint}</span>
					</label>
					<label
						class="flex-1 cursor-pointer rounded-2xl border px-4 py-3 {kind === 'club'
							? 'border-primary bg-surface-container-high'
							: 'border-outline-variant'}"
					>
						<input
							type="radio"
							name="kind"
							value="club"
							bind:group={kind}
							class="sr-only"
						/>
						<span class="block text-[14px] font-bold text-on-surface">{o.kindClub}</span>
						<span class="mt-1 block text-[12px] text-on-surface-variant">{o.kindClubHint}</span>
					</label>
				</div>
				<p class="text-[12px] text-on-surface-variant">{o.kindSwitchNote}</p>

				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.nameLabel}</span>
					<input name="name" required value={formName} class={inputCls} />
				</label>
				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.sportLabel}</span>
					<input name="sportType" value={formSportType || 'volleyball'} class={inputCls} />
				</label>
				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.locationLabel}</span>
					<input name="location" value={formLocation} class={inputCls} />
				</label>
				<label class="flex flex-col gap-1">
					<span class="text-[11px] font-medium text-primary">{o.billingEmailLabel}</span>
					<input
						name="billingEmail"
						type="email"
						required
						value={formBillingEmail || data.billingEmail}
						class={inputCls}
					/>
				</label>

				<p class="rounded-2xl border border-outline-variant px-4 py-3 text-[13px] text-on-surface-variant">
					{o.pricingNote}
				</p>

				{#if form?.error}
					<p class="rounded-2xl bg-error-container px-4 py-3 text-[13px] font-medium text-error">
						{form.error}
					</p>
				{/if}

				<button
					type="submit"
					class="w-full rounded-full border-none bg-primary py-3.5 text-[15px] font-bold text-on-primary hover:opacity-90"
				>
					{o.detailsSubmit}
				</button>
			</form>
		{/if}
	</div>
</div>
