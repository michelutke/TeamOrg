<script lang="ts">
	import { enhance } from '$app/forms';
	import { getMessages } from '$lib/i18n';
	import type { ActionData, PageData } from './$types';

	interface Props {
		form: ActionData;
		data: PageData;
	}

	let { form, data }: Props = $props();
	const m = $derived(data.m);
	const common = $derived(getMessages(data.lang).common);
</script>

<svelte:head>
	<title>{m.joinTitle} — TeamOrg</title>
</svelte:head>

<div class="flex min-h-screen items-center justify-center bg-surface-container-low">
	<div
		class="flex w-full max-w-[440px] flex-col items-center gap-4 rounded-[32px] bg-surface px-10 py-12 shadow-[0px_8px_32px_0px_rgba(0,0,0,0.08)]"
	>
		<div class="flex size-16 items-center justify-center rounded-full bg-primary-container">
			<span class="text-[22px] font-bold text-on-primary-container">TO</span>
		</div>
		<h1 class="text-center font-display text-[26px] font-extrabold text-on-surface">{m.joinTitle}</h1>

		<form method="POST" use:enhance class="flex w-full flex-col gap-4">
			<label class="flex w-full flex-col gap-1 rounded-2xl bg-surface-container-high px-[18px] py-[10px]">
				<span class="text-[11px] font-medium text-primary">{m.joinCodeLabel}</span>
				<input
					id="code"
					name="code"
					type="text"
					maxlength="8"
					autocomplete="off"
					required
					value={form?.code ?? data.code}
					oninput={(e) => (e.currentTarget.value = e.currentTarget.value.toUpperCase())}
					class="w-full border-none bg-transparent font-mono text-[16px] uppercase text-on-surface outline-none"
				/>
			</label>

			{#if form?.error}
				<p class="text-center text-[12px] font-medium text-error">{form.error}</p>
			{/if}

			<button
				type="submit"
				class="w-full cursor-pointer rounded-full border-none bg-primary py-4 text-[15px] font-bold text-on-primary hover:opacity-90"
			>
				{m.joinSubmit}
			</button>
		</form>

		<a href="/start" class="text-[12px] font-medium text-on-surface-variant hover:text-primary">
			{common.back}
		</a>
	</div>
</div>
