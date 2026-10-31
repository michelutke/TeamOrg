<script lang="ts">
	import { enhance } from '$app/forms';
	import type { ActionData, PageData } from './$types';

	interface Props {
		form: ActionData;
		data: PageData;
	}

	let { form, data }: Props = $props();
	const m = $derived(data.m);
</script>

<svelte:head>
	<title>{m.resetTitle} | TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface-container-low">
	<div
		class="flex w-full max-w-[440px] flex-col items-center gap-4 rounded-[32px] bg-surface px-10 py-12 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.08)]"
	>
		<h1 class="font-display text-[26px] font-extrabold text-on-surface">{m.resetTitle}</h1>

		{#if form?.success}
			<p class="text-center text-[14px] text-on-surface-variant" data-testid="reset-success">
				{m.success}
			</p>
			<a
				href="/login"
				class="w-full rounded-full bg-primary py-4 text-center text-[15px] font-bold text-on-primary hover:opacity-90"
			>
				{m.backToLogin}
			</a>
		{:else if form?.invalidLink}
			<p class="text-center text-[14px] font-medium text-error" data-testid="reset-invalid">
				{form.error}
			</p>
			<a href="/forgot-password" class="text-[13px] font-bold text-primary hover:underline">
				{m.requestNewLink}
			</a>
		{:else}
			<form method="POST" use:enhance class="flex w-full flex-col gap-4">
				<label class="flex w-full flex-col gap-1 rounded-2xl bg-surface-container-high px-[18px] py-[10px]">
					<span class="text-[11px] font-medium text-primary">{m.newPassword}</span>
					<input
						id="password"
						name="password"
						type="password"
						autocomplete="new-password"
						required
						minlength="8"
						maxlength="72"
						class="w-full border-none bg-transparent text-[16px] text-on-surface outline-none"
					/>
				</label>

				<label class="flex w-full flex-col gap-1 rounded-2xl bg-surface-container-high px-[18px] py-[10px]">
					<span class="text-[11px] font-medium text-primary">{m.confirmPassword}</span>
					<input
						id="confirm"
						name="confirm"
						type="password"
						autocomplete="new-password"
						required
						minlength="8"
						maxlength="72"
						class="w-full border-none bg-transparent text-[16px] text-on-surface outline-none"
					/>
				</label>

				{#if form?.error}
					<p class="text-center text-[12px] font-medium text-error">{form.error}</p>
				{/if}

				<button
					type="submit"
					class="w-full cursor-pointer rounded-full border-none bg-primary py-4 text-[15px] font-bold text-on-primary hover:opacity-90"
				>
					{m.resetSubmit}
				</button>
			</form>
		{/if}
	</div>
</div>
