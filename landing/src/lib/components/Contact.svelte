<script lang="ts">
	import { enhance } from '$app/forms';
	import { Mail, Clock, Building2, CheckCircle2 } from 'lucide-svelte';
	import type { Dict } from '$lib/i18n';

	type ContactForm = {
		success?: boolean;
		error?: string;
		values?: Record<string, string>;
	} | null;

	let {
		m,
		siteKey,
		form
	}: { m: Dict['contact']; siteKey: string; form: ContactForm } = $props();

	let submitting = $state(false);
	const v = $derived(form?.values ?? {});

	const errorText = $derived(
		form?.error === 'captcha'
			? m.errorCaptcha
			: form?.error === 'server'
				? m.errorServer
				: form?.error === 'validation'
					? m.errorValidation
					: ''
	);
</script>

<svelte:head>
	{#if siteKey}
		<script src="https://challenges.cloudflare.com/turnstile/v0/api.js" async defer></script>
	{/if}
</svelte:head>

<section
	id="kontakt"
	class="scroll-mt-20 bg-gradient-to-br from-[#c9760e] to-[#a85e08]"
>
	<div
		class="mx-auto flex max-w-content flex-col gap-12 px-6 py-20 md:flex-row md:items-center md:gap-16 md:px-10 md:py-24"
	>
		<!-- Info -->
		<div data-reveal class="md:flex-1">
			<span
				class="inline-block rounded-full bg-white/[0.16] px-3.5 py-1.5 text-[12px] font-bold tracking-[0.16em] text-white"
				>{m.eyebrow}</span
			>
			<h2
				class="font-display mt-4 max-w-[480px] text-[28px] font-extrabold leading-[1.1] tracking-tight text-white md:text-[40px]"
			>
				{m.title}
			</h2>
			<p class="mt-4 max-w-[440px] text-[15px] leading-[1.52] text-white/90 md:text-[17px]">
				{m.sub}
			</p>
			<ul class="mt-7 flex flex-col gap-3.5">
				<li class="flex items-center gap-3 text-[16px] font-medium text-white">
					<span class="flex h-9 w-9 items-center justify-center rounded-full bg-white/[0.16]">
						<Mail class="h-4 w-4" strokeWidth={2} />
					</span>
					<a href="mailto:info@teamorg.ch" class="hover:underline">{m.infoEmail}</a>
				</li>
				<li class="flex items-center gap-3 text-[16px] font-medium text-white/95">
					<span class="flex h-9 w-9 items-center justify-center rounded-full bg-white/[0.16]">
						<Clock class="h-4 w-4" strokeWidth={2} />
					</span>
					{m.infoReply}
				</li>
				<li class="flex items-center gap-3 text-[16px] font-medium text-white/95">
					<span class="flex h-9 w-9 items-center justify-center rounded-full bg-white/[0.16]">
						<Building2 class="h-4 w-4" strokeWidth={2} />
					</span>
					{m.infoFor}
				</li>
			</ul>
		</div>

		<!-- Form card -->
		<div
			data-reveal
			style="--reveal-delay:80ms"
			class="w-full rounded-[1.75rem] bg-surface p-7 shadow-[0_24px_60px_-10px_rgba(20,10,40,0.3)] md:max-w-[500px] md:p-9"
		>
			{#if form?.success}
				<div class="flex flex-col items-center py-8 text-center">
					<span class="flex h-16 w-16 items-center justify-center rounded-full bg-accent-green">
						<CheckCircle2 class="h-9 w-9 text-accent-green-on" strokeWidth={2} />
					</span>
					<h3 class="font-display mt-5 text-[22px] font-extrabold text-on-surface">
						{m.successTitle}
					</h3>
					<p class="mt-2 max-w-[360px] text-[15px] leading-[1.5] text-on-surface-variant">
						{m.successBody}
					</p>
				</div>
			{:else}
				<h3 class="font-display text-[22px] font-extrabold tracking-tight text-on-surface md:text-[24px]">
					{m.formTitle}
				</h3>

				{#if errorText}
					<p
						class="mt-4 rounded-xl border border-accent-red-on/20 bg-accent-red px-4 py-3 text-[14px] font-medium text-accent-red-on"
						role="alert"
					>
						{errorText}
					</p>
				{/if}

				<form
					method="POST"
					action="/?/contact"
					class="mt-5 flex flex-col gap-4"
					use:enhance={() => {
						submitting = true;
						return async ({ update }) => {
							try {
								// On success this resets the form fields (SvelteKit default),
								// so no entered data lingers; on error it keeps the values.
								await update();
							} finally {
								submitting = false;
								try {
									window.turnstile?.reset();
								} catch {
									/* widget not rendered */
								}
							}
						};
					}}
				>
					<!-- Honeypot (hidden from humans) -->
					<input
						type="text"
						name="company"
						tabindex="-1"
						autocomplete="off"
						class="absolute left-[-9999px] h-0 w-0 opacity-0"
						aria-hidden="true"
					/>

					<label class="flex flex-col gap-2">
						<span class="text-[13px] font-medium text-on-surface-variant">{m.fields.club}</span>
						<input
							name="club"
							required
							value={v.club ?? ''}
							placeholder={m.fields.clubPh}
							class="rounded-xl border border-outline-variant bg-surface-low px-4 py-3.5 text-[15px] text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus:border-primary"
						/>
					</label>

					<label class="flex flex-col gap-2">
						<span class="text-[13px] font-medium text-on-surface-variant">{m.fields.name}</span>
						<input
							name="name"
							required
							value={v.name ?? ''}
							placeholder={m.fields.namePh}
							class="rounded-xl border border-outline-variant bg-surface-low px-4 py-3.5 text-[15px] text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus:border-primary"
						/>
					</label>

					<label class="flex flex-col gap-2">
						<span class="text-[13px] font-medium text-on-surface-variant">{m.fields.email}</span>
						<input
							name="email"
							type="email"
							required
							value={v.email ?? ''}
							placeholder={m.fields.emailPh}
							class="rounded-xl border border-outline-variant bg-surface-low px-4 py-3.5 text-[15px] text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus:border-primary"
						/>
					</label>

					<label class="flex flex-col gap-2">
						<span class="text-[13px] font-medium text-on-surface-variant">{m.fields.members}</span>
						<input
							name="members"
							inputmode="numeric"
							value={v.members ?? ''}
							placeholder={m.fields.membersPh}
							class="rounded-xl border border-outline-variant bg-surface-low px-4 py-3.5 text-[15px] text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus:border-primary"
						/>
					</label>

					<label class="flex flex-col gap-2">
						<span class="text-[13px] font-medium text-on-surface-variant">{m.fields.message}</span>
						<textarea
							name="message"
							required
							rows="4"
							placeholder={m.fields.messagePh}
							class="resize-none rounded-xl border border-outline-variant bg-surface-low px-4 py-3.5 text-[15px] text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus:border-primary"
							>{v.message ?? ''}</textarea
						>
					</label>

					{#if siteKey}
						<div class="cf-turnstile" data-sitekey={siteKey} data-theme="light"></div>
					{/if}

					<p class="text-[12px] leading-[1.4] text-on-surface-variant">{m.consent}</p>

					<button
						type="submit"
						disabled={submitting}
						class="flex items-center justify-center rounded-full bg-primary py-4 text-[16px] font-bold text-on-primary transition-transform duration-150 hover:scale-[1.01] active:scale-[0.99] disabled:cursor-not-allowed disabled:opacity-70"
					>
						{submitting ? m.sending : m.submit}
					</button>
					<p class="text-center text-[13px] font-medium text-on-surface-variant">{m.direct}</p>
				</form>
			{/if}
		</div>
	</div>
</section>
