<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/state';
	import type { ActionData, PageData } from './$types';
	import type { Stripe, StripeElements } from '@stripe/stripe-js';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const o = $derived(data.m.onboarding);

	let submitting = $state(false);
	let errorMessage = $state<string | null>(form?.error ?? null);

	let paymentElementDiv: HTMLDivElement;
	let confirmFormEl: HTMLFormElement;
	let confirmSetupIntentId = $state('');

	let stripe: Stripe | null = null;
	let elements: StripeElements | null = null;

	function submitConfirm(setupIntentId: string) {
		confirmSetupIntentId = setupIntentId;
		confirmFormEl.requestSubmit();
	}

	async function handleSubmit() {
		if (!stripe || !elements) return;
		submitting = true;
		errorMessage = null;

		const result = await stripe.confirmSetup({
			elements,
			confirmParams: {
				return_url: window.location.origin + '/create/billing'
			},
			redirect: 'if_required'
		});

		if (result.error) {
			errorMessage = result.error.message ?? o.cardError;
			submitting = false;
			return;
		}

		if (result.setupIntent) {
			submitConfirm(result.setupIntent.id);
		}
	}

	onMount(async () => {
		const existingSetupIntentId = page.url.searchParams.get('setup_intent');
		if (existingSetupIntentId) {
			submitting = true;
			submitConfirm(existingSetupIntentId);
			return;
		}

		if (!data.publishableKey) return;

		const { loadStripe } = await import('@stripe/stripe-js');
		stripe = await loadStripe(data.publishableKey);
		if (!stripe) return;

		elements = stripe.elements({
			clientSecret: data.clientSecret,
			appearance: { theme: 'flat', variables: { colorPrimary: '#0E6577' } }
		});
		const paymentElement = elements.create('payment');
		paymentElement.mount(paymentElementDiv);
	});
</script>

<svelte:head>
	<title>{o.cardTitle} — TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface-container-low px-6 py-16">
	<div
		class="flex w-full max-w-[560px] flex-col gap-6 rounded-[32px] bg-surface px-10 py-12 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.08)]"
	>
		<ol class="flex items-center gap-3 text-[12px]">
			<li class="flex-1 rounded-2xl border border-outline-variant px-3 py-2 text-center text-on-surface-variant">
				1. {o.createAccountTitle}
			</li>
			<li class="flex-1 rounded-2xl border border-outline-variant px-3 py-2 text-center text-on-surface-variant">
				2. {o.createDetailsTitle}
			</li>
			<li class="flex-1 rounded-2xl border border-primary px-3 py-2 text-center font-bold text-on-surface">
				3. {o.cardTitle}
			</li>
		</ol>

		<p class="rounded-2xl border border-outline-variant px-4 py-3 text-[13px] text-on-surface-variant">
			{o.pricingNote}
		</p>

		{#if !data.publishableKey}
			<p class="rounded-2xl border border-outline-variant px-4 py-3 text-[13px] text-on-surface-variant">
				Billing is not configured in this environment. Set PUBLIC_STRIPE_PUBLISHABLE_KEY to enable
				card setup.
			</p>
		{:else}
			<div bind:this={paymentElementDiv}></div>

			{#if errorMessage}
				<p class="rounded-2xl bg-error-container px-4 py-3 text-[13px] font-medium text-error">
					{errorMessage}
				</p>
			{/if}

			{#if submitting}
				<p class="text-center text-[13px] text-on-surface-variant">{o.cardProcessing}</p>
			{/if}

			<button
				type="button"
				disabled={submitting}
				onclick={handleSubmit}
				class="w-full rounded-full border-none bg-primary py-3.5 text-[15px] font-bold text-on-primary hover:opacity-90 disabled:opacity-60"
			>
				{o.cardSubmit}
			</button>
		{/if}

		<form method="POST" action="?/confirm" bind:this={confirmFormEl} class="hidden">
			<input type="hidden" name="setupIntentId" bind:value={confirmSetupIntentId} />
		</form>
	</div>
</div>
