<script lang="ts">
	import { onMount } from 'svelte';
	import { enhance } from '$app/forms';
	import { page } from '$app/state';
	import type { Stripe, StripeElements } from '@stripe/stripe-js';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const b = $derived(data.m.billing);
	const o = $derived(data.m.onboarding);
	const converted = $derived(page.url.searchParams.get('converted'));
	const targetKind = $derived(data.billing.kind === 'team' ? 'club' : 'team');
	const convertLabel = $derived(data.billing.kind === 'team' ? b.convertToClub : b.convertToTeam);
	const clubId = page.params.clubId;

	let showCardForm = $state(false);
	let clientSecret = $state<string | null>(null);
	let submitting = $state(false);
	let cardErrorMessage = $state<string | null>(null);

	let paymentElementDiv: HTMLDivElement;
	let confirmFormEl: HTMLFormElement;
	let confirmInputEl: HTMLInputElement;

	let stripe: Stripe | null = null;
	let elements: StripeElements | null = null;

	const statusStyles: Record<string, string> = {
		active: 'bg-success-container text-success',
		past_due: 'bg-[#FFF3CD] text-[#7A5B00]',
		frozen: 'bg-error-container text-error'
	};
	const statusLabels = $derived<Record<string, string>>({
		active: b.statusActive,
		past_due: b.statusPastDue,
		frozen: b.statusFrozen
	});

	function submitConfirm(setupIntentId: string) {
		// Set the DOM value directly: a bind:value flushes asynchronously, so the
		// synchronous requestSubmit() would serialize the form with an empty id.
		confirmInputEl.value = setupIntentId;
		confirmFormEl.requestSubmit();
	}

	async function mountPaymentElement(secret: string) {
		if (!data.publishableKey) return;
		const { loadStripe } = await import('@stripe/stripe-js');
		stripe = await loadStripe(data.publishableKey);
		if (!stripe) return;

		elements = stripe.elements({
			clientSecret: secret,
			appearance: { theme: 'flat', variables: { colorPrimary: '#0E6577' } }
		});
		const paymentElement = elements.create('payment');
		paymentElement.mount(paymentElementDiv);
	}

	async function handleSubmit() {
		if (!stripe || !elements) return;
		submitting = true;
		cardErrorMessage = null;

		const result = await stripe.confirmSetup({
			elements,
			confirmParams: {
				return_url: window.location.origin + `/manage/${clubId}/billing`
			},
			redirect: 'if_required'
		});

		if (result.error) {
			cardErrorMessage = result.error.message ?? b.cardUpdateError;
			submitting = false;
			return;
		}

		if (result.setupIntent) {
			submitConfirm(result.setupIntent.id);
		}
	}

	onMount(() => {
		const existingSetupIntentId = page.url.searchParams.get('setup_intent');
		if (existingSetupIntentId) {
			submitting = true;
			showCardForm = true;
			submitConfirm(existingSetupIntentId);
		}
	});

	$effect(() => {
		if (clientSecret && showCardForm) mountPaymentElement(clientSecret);
	});

	$effect(() => {
		if (form?.clientSecret) {
			clientSecret = form.clientSecret;
			showCardForm = true;
			cardErrorMessage = null;
		}
	});
</script>

<svelte:head>
	<title>{b.title} — TeamOrg</title>
</svelte:head>

<div class="flex flex-col gap-6">
	<div class="flex items-center gap-3">
		<h1 class="font-display text-[30px] font-extrabold text-on-surface">{b.title}</h1>
		<span
			class="rounded-full px-3 py-1 text-[12px] font-semibold {statusStyles[data.billing.billingStatus] ??
				statusStyles.active}"
		>
			{statusLabels[data.billing.billingStatus] ?? b.statusActive}
		</span>
	</div>

	{#if converted === 'club'}
		<div class="rounded-2xl bg-success-container px-4 py-3 text-[13px] font-medium text-success">
			{b.convertedToClubNote}
		</div>
	{/if}

	<!-- Member counts -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
			<div class="rounded-2xl bg-surface-container-high px-5 py-4">
				<p class="text-[12px] font-medium text-on-surface-variant">{b.memberCount}</p>
				<p class="text-[24px] font-bold text-on-surface">{data.billing.currentMemberCount}</p>
			</div>
			<div class="rounded-2xl bg-surface-container-high px-5 py-4">
				<p class="text-[12px] font-medium text-on-surface-variant">{b.projectedCount}</p>
				<p class="text-[24px] font-bold text-on-surface">{data.billing.projectedBilledCount}</p>
			</div>
		</div>
		<p class="mt-3 text-[13px] text-on-surface-variant">{b.countBasisNote}</p>
	</div>

	<!-- Card on file / manual billing -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-4 font-display text-[20px] font-bold text-on-surface">{b.cardOnFile}</h2>

		{#if data.billing.billingMode !== 'stripe'}
			<p class="text-[14px] text-on-surface-variant">{b.manualNote}</p>
		{:else if showCardForm}
			{#if !data.publishableKey}
				<p class="rounded-2xl border border-outline-variant px-4 py-3 text-[13px] text-on-surface-variant">
					Billing is not configured in this environment. Set PUBLIC_STRIPE_PUBLISHABLE_KEY to enable
					card setup.
				</p>
			{:else}
				<div bind:this={paymentElementDiv}></div>

				{#if cardErrorMessage}
					<p class="mt-3 rounded-2xl bg-error-container px-4 py-3 text-[13px] font-medium text-error">
						{cardErrorMessage}
					</p>
				{/if}

				{#if submitting}
					<p class="mt-3 text-[13px] text-on-surface-variant">{b.cardProcessing}</p>
				{/if}

				<button
					type="button"
					disabled={submitting}
					onclick={handleSubmit}
					class="mt-4 cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90 disabled:opacity-60"
				>
					{b.cardSubmit}
				</button>
			{/if}
		{:else}
			{#if data.billing.cardBrand && data.billing.cardLast4}
				<p class="text-[14px] text-on-surface">
					{data.billing.cardBrand} •••• {data.billing.cardLast4} — {data.billing.cardExpMonth}/{data
						.billing.cardExpYear}
				</p>
			{:else}
				<p class="text-[14px] text-on-surface-variant">{b.noCard}</p>
			{/if}

			{#if form?.cardError}
				<p class="mt-3 text-[13px] font-medium text-error">{b.cardUpdateError}</p>
			{/if}

			<form method="POST" action="?/updateCard" use:enhance class="mt-4">
				<button
					type="submit"
					class="cursor-pointer rounded-full border-none bg-primary px-6 py-3 text-[14px] font-bold text-on-primary hover:opacity-90"
				>
					{b.updateCard}
				</button>
			</form>
		{/if}
	</div>

	<!-- Convert club/team -->
	<div class="rounded-3xl bg-surface-container-low p-6">
		<h2 class="mb-2 font-display text-[20px] font-bold text-on-surface">{convertLabel}</h2>
		<p class="mb-4 text-[13px] text-on-surface-variant">
			{b.currentKind}: {data.billing.kind === 'team' ? o.kindTeam : o.kindClub}
		</p>

		{#if form?.convertError}
			<p class="mb-3 text-[13px] font-medium text-error">{b.convertBlocked}</p>
		{/if}

		<form method="POST" action="?/convert">
			<input type="hidden" name="targetKind" value={targetKind} />
			<button
				type="submit"
				class="cursor-pointer rounded-full border border-outline-variant bg-transparent px-6 py-3 text-[14px] font-medium text-on-surface-variant hover:bg-surface-container-high"
			>{convertLabel}</button>
		</form>
	</div>

	<form method="POST" action="?/confirmCard" bind:this={confirmFormEl} class="hidden">
		<input type="hidden" name="setupIntentId" bind:this={confirmInputEl} />
	</form>
</div>
