<script lang="ts">
	import { enhance } from '$app/forms';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const initials = $derived(
		data.user.displayName
			.split(/\s+/)
			.slice(0, 2)
			.map((w) => w[0]?.toUpperCase() ?? '')
			.join('')
	);

	const inputCls =
		'w-full rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none';
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

	<section class="flex flex-col gap-4 rounded-[28px] bg-surface p-6">
		<h2 class="text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">
			{data.m.profile.changePasswordTitle}
		</h2>
		<form method="POST" action="?/changePassword" use:enhance class="flex flex-col gap-4">
			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{data.m.profile.currentPasswordLabel}</span>
				<input name="currentPassword" type="password" required class={inputCls} />
			</label>
			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{data.m.profile.newPasswordLabel}</span>
				<input name="newPassword" type="password" required minlength="8" class={inputCls} />
			</label>
			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{data.m.profile.confirmPasswordLabel}</span>
				<input name="confirmPassword" type="password" required minlength="8" class={inputCls} />
			</label>
			{#if form?.error}
				<p class="text-[12px] font-medium text-error">{form.error}</p>
			{:else if form?.success}
				<p class="text-[12px] font-medium text-success">{data.m.profile.passwordChanged}</p>
			{/if}
			<button
				type="submit"
				class="rounded-full border-none bg-primary py-3 text-[15px] font-bold text-on-primary hover:opacity-90"
			>
				{data.m.profile.changePasswordButton}
			</button>
		</form>
	</section>

	<section class="flex flex-col gap-3 rounded-[28px] border border-error/30 bg-surface p-6">
		<h2 class="text-[12px] font-semibold uppercase tracking-wide text-error">
			{data.m.profile.deleteSectionTitle}
		</h2>
		<p class="text-[14px] text-on-surface-variant">{data.m.profile.deleteSectionBody}</p>
		<a
			href="/app/profile/delete"
			class="self-start text-[14px] font-bold text-error hover:underline"
		>
			{data.m.profile.deleteSectionLink}
		</a>
	</section>
</div>
