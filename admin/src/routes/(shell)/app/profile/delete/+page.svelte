<script lang="ts">
	import { enhance } from '$app/forms';
	import type { PageData, ActionData } from './$types';

	interface Props {
		data: PageData;
		form: ActionData;
	}

	let { data, form }: Props = $props();

	const inputCls =
		'w-full rounded-2xl bg-surface-container-high px-4 py-3 text-[14px] text-on-surface outline-none';
</script>

<svelte:head>
	<title>{data.m.profile.deleteTitle} — TeamOrg</title>
</svelte:head>

<div class="mx-auto flex max-w-[560px] flex-col gap-6">
	<header class="flex flex-col gap-2">
		<h1 class="font-display text-[24px] font-extrabold text-on-surface">
			{data.m.profile.deleteTitle}
		</h1>
		<p class="text-[14px] text-on-surface-variant">{data.m.profile.deleteIntro}</p>
	</header>

	<section class="flex flex-col gap-3 rounded-[28px] bg-surface p-6 text-[14px]">
		<h2 class="text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">
			{data.m.profile.deleteRemovedTitle}
		</h2>
		<p class="text-on-surface">{data.m.profile.deleteRemovedBody}</p>
	</section>

	<section class="flex flex-col gap-3 rounded-[28px] bg-surface p-6 text-[14px]">
		<h2 class="text-[12px] font-semibold uppercase tracking-wide text-on-surface-variant">
			{data.m.profile.deleteKeptTitle}
		</h2>
		<p class="text-on-surface">{data.m.profile.deleteKeptBody}</p>
	</section>

	{#if data.isCoach}
		<p class="rounded-[28px] bg-surface p-6 text-[14px] text-on-surface">
			{data.m.profile.deleteCoachWarning}
		</p>
	{/if}

	<section class="flex flex-col gap-4 rounded-[28px] bg-surface p-6">
		<p class="text-[14px] font-bold text-error">{data.m.profile.deleteIrreversible}</p>
		<form method="POST" use:enhance class="flex flex-col gap-4">
			<label class="flex flex-col gap-1">
				<span class="text-[12px] font-medium text-primary">{data.m.profile.deletePasswordLabel}</span>
				<input name="password" type="password" required class={inputCls} />
			</label>
			{#if form?.error}
				<p class="text-[12px] font-medium text-error">{form.error}</p>
			{/if}
			<button
				type="submit"
				class="rounded-full border-none bg-error py-3 text-[15px] font-bold text-on-error hover:opacity-90"
			>
				{data.m.profile.deleteButton}
			</button>
			<a
				href="/app/profile"
				class="text-center text-[14px] font-medium text-on-surface-variant hover:underline"
			>
				{data.m.profile.deleteCancel}
			</a>
		</form>
	</section>
</div>
