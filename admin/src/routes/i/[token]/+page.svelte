<script lang="ts">
	import { enhance } from '$app/forms';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const inv = $derived(data.invite);
	const isClub = $derived(inv?.scope === 'club');
	const roleLabel = (r: string) => data.m.roles[r as keyof typeof data.m.roles] ?? r;
	const i = $derived(data.m.invite);

	const expiry = $derived(
		inv
			? new Intl.DateTimeFormat(data.lang === 'de' ? 'de-CH' : 'en-GB', {
					day: '2-digit',
					month: '2-digit',
					year: 'numeric'
				}).format(new Date(inv.expiresAt))
			: ''
	);

	const inputCls =
		'w-full rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none';
	const prefill = $derived(data.state === 'anonymous' ? data.prefillEmail : null);
	const formName = $derived(form && 'name' in form ? (form.name ?? '') : '');
</script>

<svelte:head>
	<title>{i.eyebrow} · TeamOrg</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<section class="mx-auto flex min-h-screen max-w-[520px] flex-col justify-center px-6 py-16">
	{#if !inv}
		<div class="rounded-[24px] bg-surface p-8 text-center">
			<h1 class="font-display text-[24px] font-extrabold text-on-surface">{i.invalidTitle}</h1>
			<p class="mt-4 text-[15px] text-on-surface-variant">{i.invalidBody}</p>
			<a href="/login" class="mt-7 inline-block text-[14px] font-medium text-primary hover:underline">
				{data.m.login.submit}
			</a>
		</div>
	{:else}
		<div class="rounded-[24px] bg-surface p-8">
			<p class="text-[12px] font-bold tracking-[0.12em] text-primary">{i.eyebrow}</p>
			<h1 class="mt-3 font-display text-[26px] font-extrabold leading-tight text-on-surface">
				{isClub ? i.clubTitle : i.teamTitle}
			</h1>

			<dl class="mt-6 flex flex-col gap-3 text-[15px]">
				<div class="flex justify-between gap-4">
					<dt class="text-on-surface-variant">Verein</dt>
					<dd class="font-semibold text-on-surface">{inv.clubName}</dd>
				</div>
				{#if inv.teamName}
					<div class="flex justify-between gap-4">
						<dt class="text-on-surface-variant">{i.teamLabel}</dt>
						<dd class="font-semibold text-on-surface">{inv.teamName}</dd>
					</div>
				{/if}
				<div class="flex justify-between gap-4">
					<dt class="text-on-surface-variant">{i.roleLabel}</dt>
					<dd class="font-semibold text-on-surface">{roleLabel(inv.role)}</dd>
				</div>
				<div class="flex justify-between gap-4">
					<dt class="text-on-surface-variant">{i.invitedByLabel}</dt>
					<dd class="font-semibold text-on-surface">{inv.invitedBy}</dd>
				</div>
				<div class="flex justify-between gap-4">
					<dt class="text-on-surface-variant">{i.expiresLabel}</dt>
					<dd class="font-semibold text-on-surface">{expiry}</dd>
				</div>
			</dl>

			{#if form?.error}
				<p class="mt-5 rounded-2xl bg-error-container px-4 py-3 text-[13px] font-medium text-error">
					{form.error}
				</p>
			{/if}

			<!-- State-specific action -->
			{#if data.state === 'redeemable'}
				<form method="POST" action="?/redeem" use:enhance class="mt-7">
					<button
						type="submit"
						class="w-full rounded-full border-none bg-primary py-3.5 text-[15px] font-bold text-on-primary hover:opacity-90"
					>
						{i.join}
					</button>
				</form>
			{:else if data.state === 'mismatch'}
				<div class="mt-7 flex flex-col gap-3">
					<p class="text-[14px] text-on-surface-variant">{i.mismatchBody}</p>
					<form method="POST" action="/logout">
						<button
							type="submit"
							class="w-full rounded-full bg-surface-container-high py-3 text-[14px] font-semibold text-on-surface"
						>
							{i.signOut}
						</button>
					</form>
				</div>
			{:else}
				<!-- anonymous: register (invite-gated) + link to login -->
				<form method="POST" action="?/register" use:enhance class="mt-7 flex flex-col gap-3">
					<label class="flex flex-col gap-1">
						<span class="text-[11px] font-medium text-primary">{i.name}</span>
						<input name="name" required value={formName} class={inputCls} />
					</label>
					<label class="flex flex-col gap-1">
						<span class="text-[11px] font-medium text-primary">{i.email}</span>
						<input
							name="email"
							type="email"
							required
							value={prefill ?? ''}
							readonly={!!prefill}
							class="{inputCls} {prefill ? 'opacity-70' : ''}"
						/>
					</label>
					<label class="flex flex-col gap-1">
						<span class="text-[11px] font-medium text-primary">{i.password}</span>
						<input name="password" type="password" required minlength="8" class={inputCls} />
					</label>
					<button
						type="submit"
						class="mt-1 w-full rounded-full border-none bg-primary py-3.5 text-[15px] font-bold text-on-primary hover:opacity-90"
					>
						{i.createAndJoin}
					</button>
				</form>

				<p class="mt-5 text-center text-[13px] text-on-surface-variant">
					{i.haveAccount}
					<a
						href="/login?redirectTo=/i/{data.token}"
						class="font-semibold text-primary hover:underline">{i.toLogin}</a
					>
				</p>
			{/if}
		</div>
	{/if}
</section>
